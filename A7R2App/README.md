# 延时摄影 —— 索尼 A7R II（ILCE-7RM2）机内 App

> 开发者：Shenwenjun

基于 [PMCADemo](https://github.com/ma1co/PMCADemo) 的思路重写的精简骨架，**不依赖 Gradle、不依赖
OpenMemories-Framework**，直接对着索尼私有 API（`com.sony.scalar.*`）写，编译只需要：

- JDK 8
- Android SDK：`platforms;android-10` + `build-tools;25.0.2`
- `libs/sony-stubs.jar`（索尼 API 的编译桩，只在编译期使用，不会打进 APK）

已经实现：

| 功能 | 说明 |
|---|---|
| 实时取景 | `CameraEx.open()` → `getNormalCamera().setPreviewDisplay()/startPreview()` |
| 半按对焦 | 快门 S1 扫描码 → `autoFocus()` / `cancelAutoFocus()` |
| 全按拍照 | 快门 S2 扫描码 → `takePicture()`，照片由相机按当前设置写卡 |
| 按键日志 | 所有按键的扫描码翻译成名字并记录（含拨轮/模式盘） |
| 文件日志 | `/sdcard/A7R2APP/LOG.TXT`，同时输出到 logcat |
| 相机握手 | 广播 `AppInfoReceive`（必须）、`apo`（禁止自动关机） |
| 生命周期 | 接收 `ExitCompleted` / `BootCompleted` |

---

## 1. 编译

```cmd
build.cmd
```

输出：`build\A7R2App-debug.apk`（已用 `debug.keystore` 做 v1 签名，Android 2.3/4.x 必需）。

### 变更记录

- **3.9**：修复 **C2 循环切换预设卡住**——原循环每次都用当前槽位重算，遇到空槽会反复试同一槽；
  改为从当前槽位的下一个开始、最多遍历 4 个槽位，每个只试一次。

- **3.8**：
  ① **ISO 上下限只在 AUTO 时出现**——手动 ISO 时拍摄页不再显示这两行；
  ② 修复**预设文件内「间隔 时/分/秒」「使用场景」无法修改**（`change()` 缺少对应分支，只有张数能改）。

- **3.7**：
  ① 拍摄页新增 **ISO自动下限 / ISO自动上限** 两项（AUTO 时可调范围）；
  ② **拍摄张数**：转盘 ±1，**快速连续旋转每格 +10**；
  ③ 预设改为 **文件 → 文件内参数** 两级：预设页只列 **预设文件 1–4**，
     确认进入文件后可编辑 间隔 时/分/秒、拍摄张数、使用场景，并有「保存当前参数 / 载入该预设」；
     左键返回文件列表。

- **3.6**：
  ① **ISO AUTO 显示上下限**（此前 `label()` 只返回 `AUTO`）→ `AUTO 1600 (100-1600)`；
  ② **拍摄张数改为 1–9999、每次 ±1**（原来按 10/50/100… 档位跳）；
  ③ **预设页可直接编辑**：预设槽位 / 间隔 时·分·秒 / 拍摄张数 / **使用场景**（日转夜、流云、星空…），
     确认键执行「保存当前参数」「载入该预设」。

- **3.5（定稿交互）**：
  ① 取消「主」选项卡；选项卡为 **拍摄 / 设置 / 延时设置 / 预设**，选中即显示项目列表；
  ② **上下左右只做选择**（↑↓ 选项、←→ 切选项卡），**转盘修改参数**，**确认键执行项目动作**（预设保存/载入）；
  ③ ISO 为 AUTO 时显示 **`AUTO 当前值 (下限-上限)`**，例如 `AUTO 1600 (100-1600)`。

- **3.0-restored（回退版）**：按用户要求退回 v3.0 的三级菜单——
  选项卡 `主 / 拍摄 / 画质 / 对焦 / 预设`（「主」为总览，导航时跳过），
  左栏菜单组（带编号）、右栏菜单项目；
  按键：选项卡层 `←→` 切换、`→`/中央 进组；组层 `↑↓` 选组、`→`/中央 进项目、`←` 返回；
  项目层 `↑↓` 选项、`←→`/转盘 改值、**中央返回组并执行确认动作**（预设保存/载入、日志快照）。
  versionCode 74（高于 v3.4 的 73，可直接覆盖安装）。

- **3.4**：
  ① **预设不再"转一格就写一次"**——切槽位只改选择，**中央键确认时才保存/载入**（此前一转盘把 4 个槽全覆盖）；
  ② 「日志快照」条目在中央确认时执行；
  ③ FN 打开固定停在「拍摄」第 1 项（不再沿用上次的选项卡）。

- **3.3**：FN 改成**列表 + 编辑**两态——
  打开即列表（↑↓ 选项、←→ 切选项卡），按**中央进入**编辑（值两侧出现琥珀三角，←→/转盘改值），
  再按**中央确认**退出编辑。解决"按中央没反应/不显示列表"的问题。

- **3.2**：打开 FN **直接显示项目列表**（不再需要"进入"）——
  左右键切换选项卡，上下键选项目，转盘改值；去掉组层与"进入"动作。

- **3.1（FN 菜单重排）**：按需求重划选项卡——
  **拍摄**（模式 / ISO / 快门 / 光圈 / 照片格式 / 压缩 / 白平衡）、
  **设置**（对焦 / 测光 / 静音 / 防抖）、
  **延时设置**（间隔-时 / 间隔-分 / 间隔-秒 / 张数）、
  **预设**（保存的拍摄参数 / 可编辑的拍摄参数）。
  每个选项卡只有一个组，UI 自动隐藏左栏变成**两级操作**（选项卡 ⇄ 项目列表），
  取消原「主」总览页。

- **3.0（FN 菜单重构）**：参照 α6700 MENU 改成**三级结构**——
  顶部 **选项卡**（主/拍摄/画质/对焦/预设）→ **菜单组**（左栏，带编号）→ **菜单项目**（右栏），
  面包屑显示 `画质 › 1. 图像质量 › 文件格式`；
  按键：`←→` 选项卡/改值、`↑↓` 同层移动、`→`/中央 进入下一级、`←` 返回上一级、中央在项目层返回组。

- **2.7**：修 **FN 文字变方块**——Noto 子集在 Android 4.1 上 `Typeface.createFromFile` 抛
  `native typeface cannot be made`。改为**优先用相机自带完整中文字体**（`MYingHeiC-GB18030-SJ.ttf`），
  内置字体只作兜底，并保留全部 `name` 记录（Android 4.1 需要 family name）。

- **2.6**：修 **字体缓存不更新**——`Fonts` 之前只在文件不存在时解压，升级后仍用旧字体；
  改为按 `versionCode` 命名（`cjk-<ver>.ttf`）并在解压后清理旧文件，升级必定生效。

- **2.5**：
  ① **间隔预设（4 槽）**——FN 菜单新增「载入预设 / 保存预设」，左右切换槽位即执行；
  预览界面按 **C2** 循环切换下一个非空预设（Toast 提示槽位与内容）；
  ② **卡剩余可拍张数**——用 `StatFs` 读剩余空间，按当前格式估算（RAW 41/82MB、JPEG 5MB、RAW+JPEG 46/87MB），
  显示在**左上角**「卡 N 张」；
  ③ FN 菜单 18 项（`MenuView` 行数 9），字体子集扩到 636 字形。

- **2.4c**：**内置字体换成 OFL 授权的 Noto Sans SC**（Subset，78 KB / 625 字形，0 缺字），
  替代原先从系统字体裁剪的子集；`拍摄中…` 的省略号改为三个 ASCII 点（Noto 子集无 U+2026）。

- **2.4b**：`build.cmd` 增加第 8 步自动验证（调用 `tools/verify_v18.py`，失败只告警不阻断）；
  使用手册同步「间隔 时/分/秒」与 16 项菜单。

- **2.4**：**间隔时间支持「时/分/秒」**——
  FN 菜单新增 3 行：`间隔-时`（0–24 h）、`间隔-分`（0–59）、`间隔-秒`（0–59），
  可设范围 **5 秒 – 24:59:59**；HUD 间隔徽章改为 `M:SS` / `H:MM:SS`，
  总时长超过一天时显示 `N天HH:MM:SS`；FN 菜单由 13 项扩到 **16 项**（`MenuView` 行数 7→8）。

- **2.3**：C1 快照命名定为 `SNAP-时间戳.TXT`（实测本机只接受这种命名，`LOG-*` 会 ENOENT），
  保留 `SNAP.TXT` 作为第二候选与「追加到 LOG.TXT」兜底。

- **2.2**：C1 快照增加**多文件名尝试**（`SNAP.TXT` / `LOG-时间戳.TXT` / `A7R2APP-SNAP.TXT`）与**追加兜底**；
  每次尝试的成功/失败都会写进 LOG.TXT，用于定位"新建文件被拒（ENOENT）"的具体范围。
- **2.1**：快照改为「关闭日志写句柄 → 新建文件 → 重新打开」，并把失败原因写入 LOG.TXT。

- **2.0**：
  ① **移除软件回放（检视）功能**——FN 菜单从 14 项减为 **13 项**，删除 JPEG 监听、回放界面与全部相关代码；
  拍完是否显示照片完全由**相机的「自动检视」**决定（App 读不到也改不了）；
  ② **C1 日志快照修复**：根因是 `LOG.TXT` 正被写入流占用，再开读流报
  `EBUSY (Device or resource busy)`；改为**内存缓冲**（最近 1MB）直接写出快照，不再读文件。

- **1.9.1**：C1 日志快照重写（字符流读回 + 复用 `writeText` 写入，失败时把异常写进 LOG.TXT 与 `SNAPSHOT-ERR.TXT`）；
  实测重开耗时由 1.2 秒降到 **0.83 秒**（onOpened 异步 + 防抖合并写入）。

- **1.9**：
  ① **C1 = 保存日志快照**（复制 `LOG.TXT` 为 `LOG-MMdd-HHmmss.TXT`，不覆盖原日志）；
  ② 调参写入防抖 120ms → **300ms**，减少连续转盘时的预览闪烁（每次写参数 HAL 会重置预览流约 0.05–0.15 秒）；
  ③ `onOpened` 改为异步执行，先起预览再写参数，避免重开后短暂黑屏；
  ④ 结论：相机「自动检视」**不在 App 参数表里**，无法读取/同步，只能在相机菜单关闭。

- **1.8**：
  ① **对焦方式同步**——启动时优先读 `focus-mode`（manual/dmf/auto），再回退 `auto-focus-mode`；
  对焦项扩展为 MF / AF-S / AF-C / AF-A / **DMF** 五个选项；
  ② **检视关闭后强制回预览**——改「检视」立即注销 JPEG 回调，写卡完成后 `review off -> stay on preview`，
  渲染时再兜底清理；
  ③ 结论：拍照后仍出现照片是**相机自身的「自动检视」**（App 读不到该设置），
  需在相机菜单 → 播放 → 自动检视 → 关。
- **1.7**：改过的设置**锁定**——每次相机重开无条件重写全部参数并恢复光圈/快门；启动读取后清空定制标记，
  避免用旧值覆盖相机；FN 菜单每行加白色分隔线。

- **1.6**：按实测收敛——
  ① **间隔下限从 1 秒改为 5 秒**（RAW 单张 3.1 秒写卡 + 1.2 秒重开 = 4.3 秒循环，1–4 秒必然跳格）；
  ② 移除 v1.2–v1.5 的"冻结帧 / 拍摄中面板"——实测本机 HAL 不回调预览帧
  （`setPreviewCallback` 与 `setOneShotPreviewCallback` 均不触发），拿不到最后一帧，重开期间回到原生黑屏。

- **1.2**：
  ① **冻结帧**——重开相机期间用最后一帧取景画面盖住黑屏（`setOneShotPreviewCallback` 抓帧 →
  `YuvImage` 转 JPEG → ImageView 显示，重开完成 260ms 后隐藏）；
  ② 打开相机改用 `OpenOptions(preview=true, inheritSetting=true, RECORDING_MODE_STILL)`，失败自动回退；
  ③ 日志新增 `shot stored in Xms (capture+write)` 与 `camera reopened (...) Xms`，用于定位间隔下限；
  ④ 清理：删除无调用的 `intervalLine()` / `seconds()`，精简 `strings.xml`，使用手册补充 ±3EV 说明。

- **1.0（发布版）**：应用名改为 **延时摄影**，开发者 **Shenwenjun**（`res/values/strings.xml`
  的 `app_name` / `developer`，并在 `AndroidManifest.xml` 注册 `developer`/`author`/
  `com.sony.scalar.developer` 三条 meta-data）；**取消 C1 参数导出功能**（按键、界面提示、
  `dumpParameters()` 一并删除）。

- **0.40**（UI 重做）：
  ① 模式字母变成**琥珀色圆角徽章**（adge_bg.xml，黑字 22sp），回到最左；
  ② RAW/色温 改**白色**并右移，与 /4.0 60 [250] **同字号同基线**、右对齐固定列
  （140dp + 18dp 间隔 + 200dp），数值变化只向左长，不推挤邻居；
  ③ 底部整句提示文字换成**图标徽章**（新增 IconView：上下箭头/左右箭头/播放三角/快门环/时钟）：
  ▲▼ 5s、◀▶ ×250、🕐 20:50（总时长）、▶ ENTER、◉ 单张；
  ④ 运行中换成**张数进度条**（新增 MiniBarView）+ 6/250 + 下一张 2s（整秒，<1 秒显示「拍摄中…」）+ 已存 6，倒计时 250ms 刷新。

- **0.39**（方案 A，收敛）：**移除 C2 放大对焦**。实测 getPreviewMagnificationRatio() 在 A7R2 上
  四个档位全部返回 **-10（不支持）**，setPreviewMagnification() 与 setAutoPreviewMagnification()
  调用成功但相机无任何反应（magChanged 不回调）——预览放大没有开放给机内 App。
  C2 键恢复空闲，相关探测日志一并移除。

- **0.36**：**C2 放大对焦**——仅在 MF 模式下生效，按一次 setPreviewMagnification()
  （比例取相机支持的最大档，A7R2 为 11.7x/屏显 12x，中心对准画面中点），再按一次 stopPreviewMagnification()；
  切后台/退出时会自动退出放大。

- **0.35**：新增四项设置——**防抖**（关/仅拍摄/常开，setAntiHandBlurMode）、
  **对焦**（MF/AF-S/AF-C/AF-A，ocus-mode + setAutoFocusMode）、
  **RAW压缩**（压缩/未压缩，setRawRecMode）、**测光**（多重/中央重点/点测，setMeteringMode）。
  pplySettings 改成传 CameraController.Settings 对象，一次写完并在日志里打印全部字段。

- **0.33**（方案 A，收敛）：**放弃物理曝光补偿拨盘**——它的事件编码不稳定（拍完重开相机后
  同一个拨盘从 `code 1` 变成 `code 3`），无法可靠映射成 ±1/3 EV。曝光补偿改由
  **后拨轮**（预览界面，每格 1/3 EV）和 **FN 面板的「曝光补偿」行** 调节；
  物理拨盘在 App 内不再响应，也不再刷日志。
- **0.22–0.32**：底栏提示精简（去掉 FN/C1 提示）；标尺定宽 118dp、缩短、数字与刻度拉开；
  参数读写合并成单次 `getParameters`（单次 apply 从 ~800ms 降到 ~110–180ms）；
  模式/曝光补偿改动不再回读校验；标尺改为本地目标值（即时跟手）；
  标尺显示范围收敛到 ±3EV。
- **0.21**：按标注重排——
  ① 底部整块（参数行 + 两行提示）**横向铺满**，提示行不再缩在中间；
  ② 标尺在 **P/A/S 显示系统曝光补偿**，**M 档显示相机给的曝光偏差**
  （`setProperExposureLevelCallback` → `onControlLevelNotify`）；
  ③ `RAW / 4000K` 从左上**移到标尺与 `f/4.0 60 [250]` 之间**，左上只留机型。
- **0.20**：按原生界面修的四处——
  ① **`+/-`（EV_COMP）键现在能调曝光补偿**（之前完全没接，所以标尺一直是 0.0）：按 +/- 直接打开
  设置面板并停在「曝光补偿」行，左右/转盘按 1/3 EV 步进，写入 `exposure-compensation`；
  ② 标尺改成**固定宽度 118dp**，不再随"快门速度/张数"文字长短被拉长；
  ③ 左上机型/格式/色温 11sp → **15sp**；④ 标尺整体缩短。
- **0.19**：按索尼原生取景界面重排——左下模式字母（26sp）、底部曝光标尺（自定义 `MeterView`）、
  右侧 `f/4.0 60 [250]`（快门按索尼习惯只显示分母）、右上 `AUTO ISO 16000`。
- **0.18**（修死机）：v0.17 在**纯 RAW** 下注册 `JpegListener` 导致整机冻结（开拍即死，日志只写到
  启动那几行）。现在回放监听**只在格式含 JPEG 且检视秒数>0 时**才注册；纯 RAW 自动关闭回放，
  只走"拍完存卡"，不再碰 JPEG 回调。日志会明确打印 `jpeg review ENABLED/DISABLED`。
- **0.17**：检视改成**整张照片回放**（不再是中央参数框）——通过 `CameraEx.setJpegListener`
  拿到相机回传的 JPEG，后台线程解码（长边约 900px）后铺满取景区，显示时长仍由 FN 的「检视」决定；
  夜间调暗的遮罩改为盖在照片**上面**（SurfaceView → 照片 → 遮罩 → 文字），所以调暗对回放也生效。
- **0.16**：
  ① AUTO 档下 HUD 显示相机**实时算出的 ISO**（`AutoISOSensitivityListener`），不再是范围文本；
  ② 间隔结束不再提示"再按 ENTER"，只留完成统计；
  ③ 每张写卡后弹**检视浮层**（序号 + 文件名 + 光圈/快门/ISO），显示时长在 FN 里可调（0/1/2/3/5 秒），
  浮层只是 UI，**不参与计时**，不影响下一张；
  ④ 拍摄中按**上键**循环调暗取景画面（4 档遮罩），不拍摄时上键仍是改间隔。
- **0.15**：间隔拍摄时每张重开相机本会重复写一遍设置，现在先比对相机当前值，
  **已经一致就跳过**（省一次 `setParameters`），只在不一致时才重写。
- **0.14**（关键修复：参数走官方通道）：改用 **`CameraEx.createParametersModifier()`**
  写设置（`setISOSensitivity` / `setISOAutoMin` / `setISOAutoMax` /
  `setColorTemperatureForWhiteBalance` / `setPictureStorageFormat` / `setSilentShutterMode`），
  不再用通用 `Parameters.set("iso-sensitivity", …)`——后者相机直接忽略（这正是
  "面板设 200、预览还是 AUTO"的原因）。写入后**回读校验**，不一致会 `tryIgnoreInhibit()`
  重试一次并把 want/readback 都写进日志。ISO 档位改为解析相机真实
  `iso-sensitivity-values`（含 125/160 等 1/3 档），避免写进不支持的值。
- **0.13**：预览界面恢复转盘调参——**前拨轮调光圈、后拨轮调快门**（不再拦档位，
  相机不接受就无效果，日志记 `preview aperture step`）；控制轮仍只用于设置面板。
  面板/转盘的改动改为 **120ms 防抖合并**，不再每拨一格做一次 `setParameters` + `getParameters`，
  转起来跟手；退出面板、开拍、切后台前会强制 flush。
- **0.12**：间隔改为 **1 秒步进**（1–60s）；ISO **每次 ±100**（AUTO ↔ 100 … 102400）；
  色温 **每次 ±100K**（AUTO ↔ 2500K … 9900K）；ISO 非 AUTO 时隐藏"ISO范围"行，
  切到 AUTO 才出现，一行显示 `[100]-1600`，左右键切换编辑下限/上限、转盘改选中的那一端。
- **0.11**：转盘**只在 FN 设置面板里**改当前选中项（预览界面不再响应转盘）；
  新增 **ISO下限 / ISO上限**（`iso-auto-min` / `iso-auto-max`，AUTO ISO 的可用范围），
  两者自动夹紧（下限超过上限会把上限顶上去）；HUD 的 AUTO 后面直接显示范围，如 `AUTO 100-1600`。
- **0.10**（光圈/快门可验证 + 重开后还原）：每次步进后回读并记录真实值
  （`aperture now f/2.0` / `shutter now 1/50`）；记住用户调过的光圈/快门，
  每次重开相机后自动走回去（A/M 还原光圈、S/M 还原快门，方向自适应、最多 40 步）。
  这样间隔拍摄中途手调的光圈不会因为"每张重开相机"而丢失。
- **0.9**（HUD + 转盘 + 光圈/快门/静音）：
  预览界面重新排版，顶部显示 机型/模式/格式/色温，底部大号 HUD 实时显示
  **光圈 · 快门 · ISO**；转盘直调（前拨轮=光圈、后拨轮=快门、控制轮=ISO）；
  FN 设置面板扩到 7 项（模式/ISO/色温/格式/光圈/快门/静音）。
  HUD 每秒回读一次参数，**拍摄/写卡期间不读**，避免干扰快门时序。
- **0.8**（App 内设置面板）：按 **FN** 进入，上下键选项、左右键改值、ENTER 退出。
  可调 **模式 P/A/S/M**（`scene-mode`）、**ISO**（`iso-sensitivity`，0=AUTO）、
  **色温**（`whitebalance=color-temp` + `color-temperture-white-balance`）、
  **照片格式**（`storage-fmt`：jpeg / raw / rawjpeg）。
  每拍一张会重开相机，用户设置会在重开后自动写回（`onOpened` 钩子）。
- **0.7**（参数导出）：按 **C1** 把相机真实参数表写到 `A7R2APP/PARAMS.TXT`
  （current / supported / initial 三段 + 常用标准字段）。用途：P/A/S/M、ISO、色温、
  RAW/JPG 在索尼 HAL 里都是私有 `sony:*` 键，必须先读出来才能安全地 `setParameters`。
- **0.6**（定版）：实测确认 A7R II 拍完一张后只有 **release + 重新 open** 能复位快门状态
  （重启预览、完整预览周期、JPEG 回调全部无效）。现在把重开相机的动作从"快门时"挪到
  "写卡完成后"，让快门正好落在计划时刻；并加了防打断保护（重开中/拍摄中不重复重开）。
  间隔选项改为 3/5/10/15/30/60 秒（RAW 单张约 3.5 秒，3 秒以下会跳格）。
- **0.5**（连续拍摄策略探测）：一次快门依次尝试 4 种方式并记录哪种有效。实测 50 张全部成功。
- **0.4**（连续拍摄修复）：Android 在 `takePicture()` 后会停掉预览，导致第二张开始
  `RuntimeException: takePicture failed`。现在每次写卡完成（`StoreImageCompleteListener`）
  都在主线程重启预览，`shoot()` 里也会先补一次 `startPreview()` 并做一次重试；
  单张拍完实时取景也会自动恢复（之前会冻结）。
- **0.3**：间隔拍摄/延时。上下键选间隔（1/2/3/5/10/15/30/60s），左右键选张数
  （10/50/100/250/500/1000/2000/5000/9999），ENTER 开始/停止。
  计时用绝对时间基准防漂移；上一张没写完卡时跳过该格并记日志；
  通过 `setStoreImageCompleteListener` 记录每张的真实文件名。
- **0.2**（真机日志驱动）：快门松手不再调 `cancelTakePicture()`（会把没写完的照片取消掉）；
  补全 `DIAL3_CW/CCW`、`EV_COMP`、`SLI_AF_MF_AEL`、`AEL_AFMF`、`CUSTOM`、`AFMF` 等按键名。
- **0.1**：首个版本，取景 / 对焦 / 拍照 / 日志 / 相机握手。

当前这份代码在本机已经编译通过，产物：

```
A7R2App-debug.apk   12,345 字节
classes.dex         13,284 字节，13 个类
package: com.shenwenjun.a7r2app  versionCode=1  versionName=0.1
sdkVersion: 10   targetSdkVersion: 10
launchable-activity: com.shenwenjun.a7r2app.MainActivity  label='A7R2 骨架'
apksigner verify -> Verified using v1 scheme (JAR signing): true
```

构建流程（全部离线，不需要网络）：

```
aapt 生成 R.java → javac（-bootclasspath android.jar + stubs.jar）
→ dx 生成 classes.dex → aapt 打包资源 → aapt add dex
→ zipalign → apksigner 签名
```

`build.cmd` 会先检查 JDK / SDK / stubs 是否存在，缺哪个会直接报出来。

> 注意：`build.cmd` 必须保持**纯 ASCII**。cmd.exe 按 ANSI 读取 .bat，UTF-8 中文注释会把
> 后面的命令行拆坏（这也是为什么脚本里没有中文注释）。

## 2. 装到相机

三种方式，任选：

1. **PMCA-RE（推荐，已配好便携环境）**
   ```cmd
   install-pmca.cmd
   ```
   等价于 `..\pmca.cmd install -f build\A7R2App-debug.apk`。
   相机用 USB 连电脑、开机就行，相机会自己切到 App 安装模式。
2. **SPK + 相机内安装**：`..\pmca.cmd apk2spk build\A7R2App-debug.apk A7R2App.spk`，
   把 spk 拷到 SD 卡的 `PMCADEMO/` 目录，用 PMCADemo 的 *Install* 菜单装。
3. **adb（开发期最快，需要先装 OpenMemories: Tweak 并打开 ADB）**
   ```cmd
   install-adb.cmd 192.168.1.50
   ```
   等价于：
   ```cmd
   adb connect <相机IP>:5555
   adb install -r build\A7R2App-debug.apk
   ```

装好后在相机的 **应用程序列表** 里能看到「A7R2 骨架」。如果没出现，重启相机让列表刷新。

## 3. 按键

| 按键 | 行为 |
|---|---|
| 快门半按（S1） | 对焦 / 松开取消 |
| 快门全按（S2） | 拍照 / 松开取消 |
| 删除键（垃圾箱）、SK2 | 退出 |

真实键值来自 `ScalarInput.ISV_KEY_*`（扫描码，不是 Android keyCode），其余按键会记录到日志里，
方便你扩展。

## 4. 日志

```
/sdcard/A7R2APP/LOG.TXT
```

内容示例：

```
11-11 19:30:01.123 I === app started ===
11-11 19:30:01.124 I model=ILCE-7RM2 release=4.x sdk=...
11-11 19:30:01.300 I CameraEx.open() ok, camera=android.hardware.Camera@...
11-11 19:30:01.400 I preview started
11-11 19:30:05.010 I keyDown S1_1_half_press
```

也可以 `adb logcat -s A7R2App` 实时看。

## 5. 改包名（如果要发布）

1. `AndroidManifest.xml` 里 `package="com.shenwenjun.a7r2app"`
2. `src/com/shenwenjun/a7r2app/` 目录改名，所有 `.java` 的 `package` 行跟着改
3. `build.cmd` 里 `%GEN%\com\shenwenjun\a7r2app\R.java` 不用改（它用 `dir /s` 收集）

包名一旦改了就**不能**和旧版本共存，会当成两个 App。

## 6. 为什么不用 OpenMemories-Framework

框架的 `framework` AAR 在 JitPack 上构建失败（只有 `stubs` 能下载），而且 AAR 里的资源合并需要
Gradle。这里直接用索尼 API + 编译桩，依赖最少、构建最快，代价是框架提供的
`DeviceInfo` / `DateTime` / `DisplayManager` / `MediaManager` 这些便利封装要自己写
（或者把框架源码 `git clone` 下来一起编译）。

## 7. 下一步可以加什么

- **间隔拍摄 / 延时**：`Handler.postDelayed` 循环调用 `takePicture()`
- **改曝光参数**：`CameraEx.getNormalCamera().getParameters()` + `setParameters()`
- **读机型/序列号/固件**：`com.sony.scalar.sysutil.ScalarProperties`
- **屏幕信息叠加**：`DisplayManager`（框架里有封装）
- **Wi-Fi 上传**：`INTERNET` 权限已声明，直接起 HTTP 客户端传照片
- **开机自启**：在 `BootCompletedReceiver` 里 `startActivity()`
- **拍照回调**：`CameraEx.setShutterListener()` / `setJpegListener()`

## 8. 注意

- 这是第三方逆向工具，装 App 会改相机 `packages.xml`，有变砖风险；出问题不保修。
- 相机端是 Android 2.3.7/4.x（API 10 级别）：没有 AndroidX、没有 GMS、没有运行时权限。
- 每次改代码都要重新 `build.cmd` + `adb install -r`。
- 真机调试前务必把相机「节电开始时间」调长，否则 Wi-Fi/ADB 几分钟就断。
