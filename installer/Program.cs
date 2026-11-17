using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace A7R2Installer
{
    /// <summary>
    /// 索尼延时摄影 · 安装程序
    /// 1) 定期用 PMCA 查询 USB 上的索尼相机，判断是否处于 MTP 模式
    /// 2) 就绪后从内嵌资源提取 APK，调用 PMCA 安装到相机
    /// 3) 安装完成弹窗提示
    /// </summary>
    public class MainForm : Form
    {
        // ---- 配色（与 App 界面同源：近黑 + 暖白 + 黄铜） ----
        static readonly Color Bg = Color.FromArgb(10, 10, 10);
        static readonly Color Panel = Color.FromArgb(22, 22, 22);
        static readonly Color Ink = Color.FromArgb(240, 239, 234);
        static readonly Color Mid = Color.FromArgb(198, 198, 190);
        static readonly Color Dim = Color.FromArgb(120, 120, 112);
        static readonly Color Brass = Color.FromArgb(196, 154, 108);
        static readonly Color Ok = Color.FromArgb(126, 200, 140);
        static readonly Color Warn = Color.FromArgb(230, 180, 90);
        static readonly Color Bad = Color.FromArgb(226, 120, 110);

        const string AppTitle = "索尼延时摄影 · 安装程序";
        const string AppVersion = "V5.5";
        const string ApkName = "延时摄影-V5.5.apk";

        public enum State { Unknown, NotFound, WrongMode, Ready, Installing, Done, Failed }

        State state = State.Unknown;
        bool busy;                 // 正在查询或安装，避免重入
        System.Threading.Timer pollTimer;
        static bool headless;      // 自检/离屏渲染时不轮询、不弹窗

        public static bool Headless { get { return headless; } set { headless = value; } }

        // 控件
        Label lblDot, lblState, lblHint, lblRuntime;
        Panel barTrack, barFill;
        System.Windows.Forms.Timer barTimer;
        TextBox log;
        Button btnInstall, btnRecheck, btnExit;

        public MainForm()
        {
            Text = AppTitle + " " + AppVersion;
            ClientSize = new Size(660, 470);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Bg;
            ForeColor = Ink;
            Font = new Font("Microsoft YaHei UI", 9f, FontStyle.Regular, GraphicsUnit.Point);
            DoubleBuffered = true;

            BuildUi();
            WriteLog("安装程序就绪，" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
            WriteLog("退出码 0 = 成功；日志会同时写入同目录 install-log.txt");
            WriteLog("正在检测相机…");

            if (headless) return;   // 离屏渲染模式不启动轮询

            // 首次检测 + 每 2 秒轮询
            ThreadPool.QueueUserWorkItem(delegate { Detect(); });
            pollTimer = new System.Threading.Timer(delegate { Detect(); }, null, 2000, 2000);
        }

        // ------------------------------------------------------------------
        // 界面
        // ------------------------------------------------------------------
        void BuildUi()
        {
            // 顶部黄铜条
            var top = new Panel();
            top.Bounds = new Rectangle(0, 0, 660, 3);
            top.BackColor = Brass;
            Controls.Add(top);

            var lblTitle = new Label();
            lblTitle.Text = AppTitle;
            lblTitle.Font = new Font("Microsoft YaHei UI", 15f, FontStyle.Regular, GraphicsUnit.Point);
            lblTitle.ForeColor = Ink;
            lblTitle.AutoSize = true;
            lblTitle.Location = new Point(28, 26);
            Controls.Add(lblTitle);

            var lblSub = new Label();
            lblSub.Text = "索尼 ILCE-7RM2（A7R II）机内应用  " + AppVersion + "  ·  包名 com.shenwenjun.a7r2app";
            lblSub.Font = new Font("Microsoft YaHei UI", 8.5f);
            lblSub.ForeColor = Dim;
            lblSub.AutoSize = true;
            lblSub.Location = new Point(30, 60);
            Controls.Add(lblSub);

            // 状态卡
            var card = new Panel();
            card.Bounds = new Rectangle(28, 88, 604, 96);
            card.BackColor = Panel;
            Controls.Add(card);

            lblDot = new Label();
            lblDot.Text = "●";
            lblDot.Font = new Font("Microsoft YaHei UI", 13f);
            lblDot.ForeColor = Dim;
            lblDot.AutoSize = true;
            lblDot.Location = new Point(16, 14);
            card.Controls.Add(lblDot);

            lblState = new Label();
            lblState.Text = "正在检测相机…";
            lblState.Font = new Font("Microsoft YaHei UI", 11f);
            lblState.ForeColor = Ink;
            lblState.AutoSize = true;
            lblState.Location = new Point(44, 15);
            card.Controls.Add(lblState);

            lblHint = new Label();
            lblHint.Text = "";
            lblHint.Font = new Font("Microsoft YaHei UI", 8.5f);
            lblHint.ForeColor = Mid;
            lblHint.AutoSize = false;
            lblHint.Bounds = new Rectangle(18, 48, 570, 40);
            card.Controls.Add(lblHint);

            // 进度条（自绘：底轨 + 黄铜滑块，与整体配色一致）
            barTrack = new Panel();
            barTrack.Bounds = new Rectangle(28, 196, 604, 6);
            barTrack.BackColor = Color.FromArgb(35, 35, 33);
            Controls.Add(barTrack);

            barFill = new Panel();
            barFill.Bounds = new Rectangle(0, 0, 0, 6);
            barFill.BackColor = Brass;
            barTrack.Controls.Add(barFill);

            barTimer = new System.Windows.Forms.Timer();
            barTimer.Interval = 16;
            barTimer.Tick += delegate { AnimateBar(); };

            // 日志
            log = new TextBox();
            log.Bounds = new Rectangle(28, 214, 604, 176);
            log.Multiline = true;
            log.ReadOnly = true;
            log.ScrollBars = ScrollBars.Vertical;
            log.BackColor = Color.FromArgb(16, 16, 16);
            log.ForeColor = Mid;
            log.BorderStyle = BorderStyle.FixedSingle;
            log.Font = new Font("Consolas", 8.5f, FontStyle.Regular, GraphicsUnit.Point);
            Controls.Add(log);

            // 按钮
            btnExit = MakeButton("退出", 28, 402, 118, Color.FromArgb(48, 48, 46), Ink);
            btnExit.Click += delegate { Close(); };

            btnRecheck = MakeButton("重新检测", 154, 402, 130, Color.FromArgb(48, 48, 46), Ink);
            btnRecheck.Click += delegate { WriteLog("手动重新检测…"); ThreadPool.QueueUserWorkItem(delegate { Detect(); }); };

            btnInstall = MakeButton("开始安装", 468, 402, 164, Brass, Color.FromArgb(20, 18, 14));
            btnInstall.Enabled = false;
            btnInstall.Click += delegate { BeginInstall(); };

            lblRuntime = new Label();
            lblRuntime.Text = "";
            lblRuntime.Font = new Font("Microsoft YaHei UI", 8f);
            lblRuntime.ForeColor = Dim;
            lblRuntime.AutoSize = false;
            lblRuntime.Bounds = new Rectangle(28, 440, 604, 20);
            Controls.Add(lblRuntime);
        }

        Button MakeButton(string text, int x, int y, int w, Color back, Color fore)
        {
            var b = new Button();
            b.Text = text;
            b.Bounds = new Rectangle(x, y, w, 34);
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderSize = 0;
            b.BackColor = back;
            b.ForeColor = fore;
            b.Font = new Font("Microsoft YaHei UI", 9.5f);
            b.Cursor = Cursors.Hand;
            Controls.Add(b);
            return b;
        }

        int barPos = -140;
        bool barRunning;

        void StartBar()
        {
            if (headless) return;
            barRunning = true;
            barTimer.Start();
        }

        void StopBar()
        {
            barRunning = false;
            barTimer.Stop();
            barFill.Bounds = new Rectangle(0, 0, barTrack.Width, 6);
        }

        void AnimateBar()
        {
            if (!barRunning) return;
            barPos += 7;
            if (barPos > barTrack.Width) barPos = -140;
            barFill.SetBounds(barPos, 0, 140, 6);
        }

        // ------------------------------------------------------------------
        // 路径解析：优先同目录「运行环境」，其次开发机上的工具链
        // ------------------------------------------------------------------
        static string BaseDir { get { return AppDomain.CurrentDomain.BaseDirectory; } }

        static string FindPython()
        {
            string[] cands = {
                Path.Combine(BaseDir, @"运行环境\python\python.exe"),
                Path.Combine(BaseDir, @"ruby\runtime\python\python.exe"),
                @"D:\大\sony-cam-dev\toolchain\python\python.exe"
            };
            foreach (string c in cands) if (File.Exists(c)) return c;
            return null;
        }

        static string FindPmca()
        {
            string[] cands = {
                Path.Combine(BaseDir, @"运行环境\pmca\pmca-console.py"),
                Path.Combine(BaseDir, @"pmca\pmca-console.py"),
                @"D:\大\sony-cam-dev\pmca-re\pmca-console.py"
            };
            foreach (string c in cands) if (File.Exists(c)) return c;
            return null;
        }

        // ------------------------------------------------------------------
        // 调用 PMCA
        // ------------------------------------------------------------------
        string RunPmca(string args, out int exitCode, int timeoutMs)
        {
            string s = RunPmcaStatic(args, out exitCode, timeoutMs);
            if (FindPython() == null || FindPmca() == null)
                WriteLog("找不到运行环境（python 或 pmca-console.py）");
            return s;
        }

        // 与界面无关的 PMCA 调用，供自检模式复用
        static string RunPmcaStatic(string args, out int exitCode, int timeoutMs)
        {
            exitCode = -1;
            string python = FindPython(), pmca = FindPmca();
            if (python == null || pmca == null) return "";

            var psi = new ProcessStartInfo();
            psi.FileName = python;
            psi.Arguments = "\"" + pmca + "\" " + args;
            psi.WorkingDirectory = Path.GetDirectoryName(pmca);
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.StandardOutputEncoding = Encoding.UTF8;
            psi.StandardErrorEncoding = Encoding.UTF8;
            psi.EnvironmentVariables["PYTHONIOENCODING"] = "utf-8";

            var sb = new StringBuilder();
            try
            {
                using (var p = new Process())
                {
                    p.StartInfo = psi;
                    p.OutputDataReceived += delegate (object s, DataReceivedEventArgs e)
                    { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
                    p.ErrorDataReceived += delegate (object s, DataReceivedEventArgs e)
                    { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
                    p.Start();
                    p.BeginOutputReadLine();
                    p.BeginErrorReadLine();
                    if (!p.WaitForExit(timeoutMs))
                    {
                        try { p.Kill(); } catch { }
                        lock (sb) sb.AppendLine("[超时] 命令未在 " + (timeoutMs / 1000) + " 秒内结束");
                    }
                    exitCode = p.HasExited ? p.ExitCode : -1;
                }
            }
            catch (Exception ex)
            {
                lock (sb) sb.AppendLine("启动失败: " + ex.Message);
            }
            lock (sb) return sb.ToString();
        }

        // ------------------------------------------------------------------
        // 检测相机
        // ------------------------------------------------------------------
        void Detect()
        {
            if (busy || state == State.Installing || state == State.Done) return;
            busy = true;
            try
            {
                int code;
                string outp = RunPmca("info", out code, 25000);
                string lower = outp.ToLowerInvariant();

                bool mtp = lower.Contains("mtp mode");
                bool msc = lower.Contains("mass storage") || lower.Contains("msc mode");
                bool found = lower.Contains("ilce") || lower.Contains("sony") && lower.Contains("is a camera");

                if (mtp && found)            SetState(State.Ready, "已连接相机（MTP 模式），可以安装",
                                                         "相机：ILCE-7RM2　|　传输：MTP　|　下一步点「开始安装」");
                else if (msc)                SetState(State.WrongMode, "检测到相机，但当前是大容量存储模式",
                                                         "请在相机上改：MENU → 设置（第 4 页）→ USB 连接 → 选「MTP」，改完会自动继续");
                else if (mtp)                SetState(State.Ready, "已连接相机（MTP 模式），可以安装",
                                                         "下一步点「开始安装」");
                else                         SetState(State.NotFound, "未检测到相机",
                                                         "请用 USB 数据线连接相机并开机；相机需处于 MTP 模式（不是电脑遥控、不是大容量存储）");
            }
            catch (Exception ex)
            {
                SetState(State.NotFound, "检测出错", ex.Message);
            }
            finally { busy = false; }
        }

        // 演示/自检用：直接设定某个状态，不走真实检测
        public void DemoState(State s)
        {
            string text, hint;
            switch (s)
            {
                case State.Ready:
                    text = "已连接相机（MTP 模式），可以安装";
                    hint = "相机：ILCE-7RM2　|　传输：MTP　|　下一步点「开始安装」";
                    break;
                case State.WrongMode:
                    text = "检测到相机，但当前是大容量存储模式";
                    hint = "请在相机上改：MENU → 设置（第 4 页）→ USB 连接 → 选「MTP」，改完会自动继续";
                    break;
                case State.Installing:
                    text = "正在安装，请勿断开 USB…";
                    hint = "上传 APK → 写入相机 → 等待相机完成安装，通常 20–60 秒";
                    break;
                case State.Done:
                    text = "安装完成";
                    hint = "相机上会短暂显示进度，完成后重启相机即可在「应用程序」里看到「延时摄影」";
                    break;
                default:
                    text = "未检测到相机";
                    hint = "请用 USB 数据线连接相机并开机；相机需处于 MTP 模式（不是电脑遥控、不是大容量存储）";
                    break;
            }
            SetState(s, text, hint);
        }
        void SetState(State s, string text, string hint)
        {
            if (IsDisposed) return;
            if (InvokeRequired) { BeginInvoke(new Action<State, string, string>(SetState), s, text, hint); return; }

            bool changed = s != state;
            state = s;
            lblState.Text = text;
            lblHint.Text = hint;

            Color c = Dim;
            switch (s)
            {
                case State.Ready: c = Ok; break;
                case State.WrongMode: c = Warn; break;
                case State.NotFound: c = Bad; break;
                case State.Installing: c = Brass; break;
                case State.Done: c = Ok; break;
                case State.Failed: c = Bad; break;
            }
            lblDot.ForeColor = c;
            lblState.ForeColor = s == State.Ready || s == State.Done ? Ok : Ink;

            btnInstall.Enabled = (s == State.Ready);
            btnInstall.ForeColor = btnInstall.Enabled ? Color.FromArgb(20, 18, 14) : Color.FromArgb(120, 108, 92);
            btnRecheck.Enabled = (s == State.NotFound || s == State.WrongMode || s == State.Failed);

            string py = FindPython(), pm = FindPmca();
            lblRuntime.Text = (py == null || pm == null)
                ? "运行环境缺失：请保持「运行环境」文件夹与安装程序在同一目录"
                : "运行环境：" + Path.GetDirectoryName(Path.GetDirectoryName(py)).Replace(BaseDir, @".\");

            if (changed && s != State.Unknown)
            {
                // 状态变化才写日志，避免每 2 秒刷屏
                if (s == State.Ready) WriteLog("相机就绪（MTP 模式）");
                else if (s == State.WrongMode) WriteLog("相机处于大容量存储模式，等待切换为 MTP…");
                else if (s == State.NotFound) WriteLog("未检测到相机，继续等待…");
            }
        }

        // ------------------------------------------------------------------
        // 安装
        // ------------------------------------------------------------------
        void BeginInstall()
        {
            if (state != State.Ready) return;

            SetState(State.Installing, "正在安装，请勿断开 USB…", "上传 APK → 写入相机 → 等待相机完成安装，通常 20–60 秒");
            StartBar();
            btnInstall.Enabled = false;
            btnRecheck.Enabled = false;

            ThreadPool.QueueUserWorkItem(delegate { Install(); });
        }

        void Install()
        {
            string apkPath = null;
            bool ok = false;
            string failReason = null;

            try
            {
                // 1) 从内嵌资源提取 APK
                apkPath = ExtractApk();
                WriteLog("已提取安装包：" + apkPath + "（" + new FileInfo(apkPath).Length.ToString("N0") + " 字节）");

                // 2) 调用 PMCA 安装
                WriteLog("开始安装到相机…");
                int code;
                string outp = RunPmca("install -d native -f \"" + apkPath + "\"", out code, 300000);
                foreach (string line in outp.Replace("\r", "").Split('\n'))
                    if (line.Trim().Length > 0) WriteLog("  " + line.Trim());

                string low = outp.ToLowerInvariant();
                ok = low.Contains("task completed successfully");
                if (!ok)
                {
                    if (low.Contains("no devices found")) failReason = "安装过程中相机掉线（可能休眠或被切换了 USB 模式）";
                    else if (low.Contains("access denied") || low.Contains("insufficient permissions")) failReason = "权限不足，请以管理员身份运行本程序";
                    else if (low.Contains("claim_interface") || low.Contains("usb.core.usberror")) failReason = "相机处于电脑遥控模式，PMCA 无法访问，请切回 MTP";
                    else failReason = "退出码 " + code + "，请查看上方日志";
                }
            }
            catch (Exception ex)
            {
                failReason = ex.Message;
            }

            if (ok) FinishOk(apkPath);
            else FinishFail(failReason, apkPath);
        }

        string ExtractApk()
        {
            var asm = Assembly.GetExecutingAssembly();
            Stream src = null;
            foreach (string n in asm.GetManifestResourceNames())
                if (n.EndsWith("APK", StringComparison.OrdinalIgnoreCase)) { src = asm.GetManifestResourceStream(n); break; }

            string dir = Path.Combine(Path.GetTempPath(), "a7r2-installer");
            Directory.CreateDirectory(dir);
            string target = Path.Combine(dir, ApkName);

            // 优先内嵌资源；没有则用同目录的 APK
            if (src != null)
            {
                using (src)
                using (var fs = new FileStream(target, FileMode.Create, FileAccess.Write))
                    src.CopyTo(fs);
                return target;
            }

            string beside = Path.Combine(BaseDir, ApkName);
            if (File.Exists(beside))
            {
                File.Copy(beside, target, true);
                return target;
            }
            throw new FileNotFoundException("安装包里没有 APK，也找不到同目录的 " + ApkName);
        }

        void FinishOk(string apkPath)
        {
            if (IsDisposed) return;
            if (InvokeRequired) { BeginInvoke(new Action<string>(FinishOk), apkPath); return; }

            StopBar();
            SetState(State.Done, "安装完成", "相机上会短暂显示进度，完成后重启相机即可在「应用程序」里看到「延时摄影」");
            WriteLog("安装完成。相机重启后打开 应用程序 → 延时摄影。");
            SaveLogFile();

            MessageBox.Show(this,
                "安装完成。\n\n" +
                "1. 相机会自动重启（如未重启请手动关机再开）\n" +
                "2. 菜单 → 应用程序 → 「延时摄影」\n" +
                "3. 建议把相机「自动检视」设为「关」，避免每张之后弹出回放",
                "安装完成 · " + AppVersion,
                MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        void FinishFail(string reason, string apkPath)
        {
            if (IsDisposed) return;
            if (InvokeRequired) { BeginInvoke(new Action<string, string>(FinishFail), reason, apkPath); return; }

            StopBar();
            SetState(State.Failed, "安装失败", reason);
            WriteLog("安装失败：" + reason);
            SaveLogFile();
            MessageBox.Show(this, "安装失败：\n\n" + reason + "\n\n详细日志见程序窗口或同目录 install-log.txt",
                            "安装失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }

        // ------------------------------------------------------------------
        // 日志
        // ------------------------------------------------------------------
        void WriteLog(string line)
        {
            if (IsDisposed) return;
            if (InvokeRequired) { BeginInvoke(new Action<string>(WriteLog), line); return; }
            log.AppendText("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + line + "\r\n");
        }

        void SaveLogFile()
        {
            try
            {
                File.WriteAllText(Path.Combine(BaseDir, "install-log.txt"), log.Text, Encoding.UTF8);
            }
            catch { }
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (state == State.Installing)
            {
                var r = MessageBox.Show(this, "正在安装，确定要中断吗？中断可能导致相机上的安装不完整。",
                                        "确认退出", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
                if (r != DialogResult.Yes) { e.Cancel = true; return; }
            }
            if (pollTimer != null) pollTimer.Dispose();
            base.OnFormClosing(e);
        }

        [STAThread]
        public static void Main(string[] args)
        {
            // 自检模式：无界面跑一次检测并把结果写文件，用于自动化验证
            if (args != null && args.Length > 0 && args[0] == "--selftest")
            {
                SelfTest(args.Length > 1 ? args[1] : null);
                return;
            }

            // 离屏渲染：把窗口画成 PNG，用于无显示器环境下的版式检查
            if (args != null && args.Length > 1 && args[0] == "--ui")
            {
                RenderUi(args[1], args.Length > 2 ? args[2] : null);
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }

        static void RenderUi(string outPath, string variant)
        {
            headless = true;
            Application.EnableVisualStyles();
            var f = new MainForm();
            f.FormBorderStyle = FormBorderStyle.None;   // 只画客户区，避免标题栏偏移
            var bmp = new Bitmap(f.ClientSize.Width, f.ClientSize.Height);
            try
            {
                if (variant == "ready") f.DemoState(State.Ready);
                else if (variant == "msc") f.DemoState(State.WrongMode);
                else if (variant == "notfound") f.DemoState(State.NotFound);
                else if (variant == "installing") f.DemoState(State.Installing);
                else if (variant == "done") f.DemoState(State.Done);

                // 触发生成窗口句柄，再离屏绘制
                typeof(Control).GetMethod("CreateControl",
                        BindingFlags.Instance | BindingFlags.NonPublic)
                    .Invoke(f, new object[] { true });
                for (int i = 0; i < 3; i++) Application.DoEvents();   // 让变更后的布局刷新生效
                f.DrawToBitmap(bmp, new Rectangle(0, 0, bmp.Width, bmp.Height));
                bmp.Save(outPath, System.Drawing.Imaging.ImageFormat.Png);
                Console.WriteLine("rendered " + outPath + " " + bmp.Width + "x" + bmp.Height + " variant=" + (variant ?? "-"));
            }
            catch (Exception ex)
            {
                Console.WriteLine("render failed: " + ex);
            }
            finally { bmp.Dispose(); f.Dispose(); }
        }

        static void SelfTest(string outPath)
        {
            var sb = new StringBuilder();
            sb.AppendLine("python: " + (FindPython() ?? "(未找到)"));
            sb.AppendLine("pmca:   " + (FindPmca() ?? "(未找到)"));

            int code;
            string outp = RunPmcaStatic("info", out code, 60000);
            sb.AppendLine("exit:   " + code);
            sb.AppendLine("--- output ---");
            sb.AppendLine(outp.Trim());

            string low = outp.ToLowerInvariant();
            string verdict;
            if (low.Contains("mtp mode")) verdict = "READY (MTP)";
            else if (low.Contains("mass storage")) verdict = "WRONG_MODE (MSC)";
            else verdict = "NOT_FOUND";
            sb.AppendLine("--- verdict ---");
            sb.AppendLine(verdict);

            // 顺带验证内嵌资源能否取出
            try
            {
                var asm = Assembly.GetExecutingAssembly();
                Stream s = null;
                foreach (string n in asm.GetManifestResourceNames())
                    if (n.EndsWith("APK", StringComparison.OrdinalIgnoreCase)) { s = asm.GetManifestResourceStream(n); break; }
                if (s != null) { sb.AppendLine("embedded APK: " + s.Length.ToString("N0") + " bytes"); s.Dispose(); }
                else sb.AppendLine("embedded APK: (缺失)");
            }
            catch (Exception ex) { sb.AppendLine("embedded APK 读取失败: " + ex.Message); }

            if (!string.IsNullOrEmpty(outPath))
                File.WriteAllText(outPath, sb.ToString(), Encoding.UTF8);
            else
                Console.Write(sb.ToString());
        }
    }
}
