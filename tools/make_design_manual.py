"""生成《延时摄影 v3.9 设计手册》DOCX"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn

ROOT = r"D:\大\sony-cam-dev"
DESIGN = os.path.join(ROOT, "dist", "_design")
OUT = os.path.join(ROOT, "dist", "延时摄影-v3.9-设计手册.docx")

AMBER = RGBColor(0xC9, 0xA0, 0x30)
DARK = RGBColor(0x22, 0x22, 0x22)
GREY = RGBColor(0x66, 0x66, 0x66)
EAST = "微软雅黑"


def setup(doc):
    st = doc.styles["Normal"]
    st.font.name = "微软雅黑"
    st.font.size = Pt(10.5)
    st.element.rPr.rFonts.set(qn("w:eastAsia"), EAST)
    for s, sz in (("Heading 1", 16), ("Heading 2", 13), ("Heading 3", 11.5)):
        h = doc.styles[s]
        h.font.name = "微软雅黑"
        h.font.size = Pt(sz)
        h.font.color.rgb = DARK
        h.element.rPr.rFonts.set(qn("w:eastAsia"), EAST)
    sec = doc.sections[0]
    sec.top_margin = Cm(2.2)
    sec.bottom_margin = Cm(2.0)
    sec.left_margin = Cm(2.4)
    sec.right_margin = Cm(2.4)


def h(doc, text, level=1):
    p = doc.add_heading(text, level=level)
    return p


def para(doc, text, size=10.5, color=None, bold=False, space_after=6):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.bold = bold
    if color is not None:
        r.font.color.rgb = color
    p.paragraph_format.space_after = Pt(space_after)
    return p


def bullet(doc, text):
    p = doc.add_paragraph(text, style="List Bullet")
    p.paragraph_format.space_after = Pt(2)
    for r in p.runs:
        r.font.size = Pt(10.5)
    return p


def table(doc, headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Light Grid Accent 1"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, text in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        r = p.add_run(text)
        r.bold = True
        r.font.size = Pt(10)
        r.font.color.rgb = DARK
    for row in rows:
        cells = t.add_row().cells
        for i, text in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            r = p.add_run(str(text))
            r.font.size = Pt(9.5)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    return t


def image(doc, name, width_cm=15.6, caption=None):
    path = os.path.join(DESIGN, name)
    if not os.path.exists(path):
        return
    doc.add_picture(path, width=Cm(width_cm))
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    if caption:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(caption)
        r.font.size = Pt(9)
        r.font.color.rgb = GREY


def main():
    doc = Document()
    setup(doc)

    # ---------- 封面 ----------
    doc.add_paragraph()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("延时摄影")
    r.font.size = Pt(34)
    r.bold = True
    r.font.color.rgb = AMBER
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("设 计 手 册")
    r.font.size = Pt(18)
    r.font.color.rgb = DARK

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("索尼 ILCE-7RM2 机内应用  ·  v3.9")
    r.font.size = Pt(12)
    r.font.color.rgb = GREY

    doc.add_paragraph()
    table(doc, ["项目", "内容"], [
        ["应用名称", "延时摄影"],
        ["包名 / 版本", "com.shenwenjun.a7r2app  ·  v3.9（versionCode 79）"],
        ["运行环境", "ILCE-7RM2（A7R II），固件 4.01，Android 4.1.2 / SDK 16"],
        ["屏幕", "640×480 px，density 1.5 → 427×320 dp"],
        ["开发者", "Shenwenjun"],
        ["文档版本", "v3.9 · 2026-11"],
    ], widths=[4.0, 11.0])

    doc.add_paragraph()
    image(doc, "ui-v39-1.png", 15.8, "图 0  界面总览（一）：预览与参数")

    # ---------- 1 设计目标 ----------
    h(doc, "1  设计目标与约束", 1)
    para(doc, "本应用在相机内部运行，替代「手机遥控 + 定时器」的传统延时拍摄方式，"
              "目标是在 640×480 的小屏、有限的按键和相机固件限制下，完成一整套延时摄影的参数设置与自动拍摄。")
    h(doc, "1.1  设计目标", 2)
    for t in [
        "一眼看清：拍摄界面不遮挡取景，关键参数（模式、曝光、格式、色温、间隔、张数）常驻可见。",
        "少走层级：常用设置不超过两次按键；改值只用一个转盘动作。",
        "状态可信：所有写入相机的参数都经过校验，相机重开后自动重写，防止被固件改回。",
        "可复现：常用场景可存为预设文件，一键切换，避免每次重设。",
    ]:
        bullet(doc, t)

    h(doc, "1.2  硬约束", 2)
    table(doc, ["约束", "来源", "设计应对"], [
        ["屏幕仅 640×480（427dp 宽）", "相机 LCD", "字号 12–13sp、行高 24dp，单行显示「名称 + 值」"],
        ["系统字体为索尼子集，缺常用汉字", "系统", "优先加载相机自带 GB18030 字体，失败则用内置 Noto 子集"],
        ["按键有限（方向 / 中央 / FN / C1 / C2 / 转盘）", "机身", "方向键只做选择，转盘只做改值，分工不重叠"],
        ["每张必须重开相机（HAL 限制）", "固件", "间隔下限设 5 秒，界面不承诺更短"],
        ["预览帧不下发给 App", "固件", "放弃取景冻结、缩放等依赖帧的功能"],
        ["无输入法", "系统", "使用场景改为固定列表循环选择，不做自由输入"],
    ], widths=[5.2, 2.4, 8.4])

    # ---------- 2 信息架构 ----------
    doc.add_page_break()
    h(doc, "2  信息架构", 1)
    para(doc, "菜单为「选项卡 → 项目列表」两级，预设页额外有一层「文件 → 文件内参数」。")
    table(doc, ["层级", "内容", "说明"], [
        ["选项卡", "拍摄 / 设置 / 延时设置 / 预设", "←→ 切换，切换即显示该项目列表"],
        ["项目", "各选项卡下的具体设置项", "↑↓ 选择，转盘改值，确认键执行动作"],
        ["预设文件", "预设文件 1–4", "确认键进入，左键返回"],
        ["文件内参数", "间隔 时/分/秒、拍摄张数、使用场景", "编辑后可保存或载入"],
    ], widths=[3.0, 6.4, 6.6])

    h(doc, "2.1  各页项目", 2)
    table(doc, ["选项卡", "项目（按显示顺序）"], [
        ["拍摄", "模式 / ISO /（ISO 为 AUTO 时插入：ISO自动下限、ISO自动上限）/ 快门 / 光圈 / 照片格式 / 压缩 / 白平衡"],
        ["设置", "对焦 / 测光 / 静音 / 防抖"],
        ["延时设置", "间隔 时 / 间隔 分 / 间隔 秒 / 拍摄张数"],
        ["预设", "预设文件 1 / 预设文件 2 / 预设文件 3 / 预设文件 4"],
        ["预设 › 文件内", "间隔 时 / 间隔 分 / 间隔 秒 / 拍摄张数 / 使用场景 / 保存当前参数 / 载入该预设"],
    ], widths=[3.4, 12.6])

    image(doc, "ui-v39-2.png", 15.8, "图 1  延时设置与预设文件")

    # ---------- 3 交互设计 ----------
    h(doc, "3  交互设计", 1)
    h(doc, "3.1  按键分工", 2)
    para(doc, "核心原则：方向键只负责「选择」，转盘只负责「改值」，确认键只负责「执行」。三者不交叉，"
              "避免在相机上误触改参数。")
    table(doc, ["按键", "菜单内", "预览界面"], [
        ["↑ / ↓", "在当前列表内移动选择", "间隔 −1 / +1 秒"],
        ["← / →", "切换选项卡（仅选择）", "张数 −10 / +10"],
        ["转盘（任一）", "修改当前项目参数，立即生效", "光圈 / 快门 / 曝光补偿"],
        ["确认键", "执行当前项目（预设保存/载入）", "开始 / 停止间隔拍摄"],
        ["FN", "打开 / 关闭菜单", "打开 / 关闭菜单"],
        ["C1", "—", "保存日志快照（SNAP-时间戳.TXT）"],
        ["C2", "—", "循环切换到下一个非空预设文件"],
    ], widths=[3.2, 6.6, 6.2])

    h(doc, "3.2  菜单状态", 2)
    para(doc, "菜单只有「列表」一个状态，打开即列表，无中间页：")
    for t in [
        "打开 FN → 停在「拍摄」第 1 项。",
        "←→ 切换选项卡，索引回到第 1 项，值不变。",
        "转盘 → 当前项的值立即变化，同时写入相机（去抖 300ms）。",
        "确认键 → 预设项执行保存/载入；普通参数提示「转盘调整」。",
        "预设页按确认键 → 进入文件；文件内按 ← → 返回列表。",
    ]:
        bullet(doc, t)

    h(doc, "3.3  拍摄流程", 2)
    table(doc, ["步骤", "用户动作", "系统反馈"], [
        ["1", "FN 打开菜单，设模式 / ISO / 格式", "列表高亮跟随选择，值即时更新"],
        ["2", "切到延时设置，定间隔与张数", "间隔徽章显示 0:05 / H:MM:SS"],
        ["3", "确认键开始", "3 秒倒计时后开始，进度条与剩余张数显示"],
        ["4", "拍摄中", "上键调暗屏幕 4 档；快门/删除键可停"],
        ["5", "结束", "自动恢复亮度，日志记录张数与耗时"],
    ], widths=[1.6, 7.0, 7.4])

    # ---------- 4 视觉规范 ----------
    h(doc, "4  视觉规范", 1)
    h(doc, "4.1  色彩", 2)
    table(doc, ["用途", "色值", "说明"], [
        ["背景", "#0A0B0E", "近黑，避免夜间拍摄干扰"],
        ["面板", "#14161B", "底部信息区"],
        ["主强调（选中 / 值）", "#FFB300", "琥珀色，与索尼 OSD 呼应"],
        ["选中行底", "#FFB300 24%", "叠加在背景上的半透明"],
        ["主文字", "#FFFFFF / #E8EAF0", "选中项用纯白"],
        ["次文字", "#9AA3B2", "未选中项的值"],
        ["弱文字", "#6C727E", "面包屑、提示"],
        ["分隔线", "#FFFFFF 12%", "行分隔、区块分隔"],
    ], widths=[5.0, 4.0, 6.6])

    h(doc, "4.2  字号与间距", 2)
    table(doc, ["元素", "字号", "行高 / 间距"], [
        ["选项卡", "13sp 粗体", "顶部 10dp，下划色条 2dp"],
        ["面包屑", "10sp", "距选项卡 5dp"],
        ["项目名", "13sp（选中加粗）", "行高 24dp"],
        ["项目值", "12sp 粗体", "右对齐，距边 11dp"],
        ["底部提示", "10sp", "距底 8dp"],
        ["预览参数框", "12sp 粗体", "高 26dp，间距 5dp"],
    ], widths=[4.4, 5.0, 6.2])

    h(doc, "4.3  选中态", 2)
    para(doc, "选中行由三层构成：整行 24% 琥珀底色 + 左侧 3dp 实心琥珀竖条 + 文字加粗转白。"
              "这种组合在低亮度屏幕和强光下都能辨认，且不依赖颜色单一通道。")

    h(doc, "4.4  预览界面栅格", 2)
    table(doc, ["区域", "位置", "内容"], [
        ["左上", "8dp, 6dp", "卡 N 张（剩余可拍张数）"],
        ["右上", "右对齐", "ISO 实时值（AUTO 时随相机变化）"],
        ["参数行", "底部上区", "模式徽章 / 曝光标尺 / 五个参数框"],
        ["操作行", "参数行下", "间隔、张数、总时长、ENTER、单张"],
        ["运行行", "最底", "进度条 + 已拍/总数 + 倒计时 + 已存"],
    ], widths=[3.2, 3.6, 9.2])

    image(doc, "screen-01.png", 9.6, "图 2  预览界面")

    # ---------- 5 数据 ----------
    h(doc, "5  数据与持久化", 1)
    table(doc, ["数据", "存储位置", "内容"], [
        ["间隔预设", "SharedPreferences（App 私有）", "槽位 1–4：间隔秒数、张数、使用场景"],
        ["运行日志", "SD 卡 /A7R2APP/LOG.TXT", "每行「时间 级别 内容」，追加写"],
        ["日志快照", "SD 卡 /A7R2APP/SNAP-时间戳.TXT", "内存缓冲导出，不读原日志"],
        ["相机参数", "相机固件", "通过 createParametersModifier 写入，每次重开重写"],
    ], widths=[3.2, 6.0, 6.8])

    para(doc, "说明：预设存于 App 私有目录，卸载即失效；日志与快照存于 SD 卡，卸载后仍在，便于事后排查。")

    h(doc, "6  技术取舍", 1)
    table(doc, ["问题", "尝试过的方案", "最终选择"], [
        ["快门只能触发一次", "CameraEx.reopen()", "release + open（0.83 秒，比 reopen 快且稳定）"],
        ["中文显示方块", "内置字体 / 系统字体", "优先相机自带 GB18030 字体，内置 Noto 子集兜底"],
        ["预览帧拿不到", "setPreviewCallback 两种", "放弃依赖帧的功能（冻结、缩放）"],
        ["日志快照 EBUSY", "读原日志文件", "内存缓冲直接写出"],
        ["新建文件 ENOENT", "LOG-时间戳.TXT", "SNAP-时间戳.TXT（实测该命名可建）"],
        ["预设误覆盖", "转一格即保存", "转盘只选槽位，确认键才执行"],
        ["菜单层级过深", "三级（选项卡→组→项目）", "两级（选项卡→项目），预设内部再分层"],
    ], widths=[4.2, 5.4, 6.4])

    # ---------- 7 版本 ----------
    h(doc, "7  版本演进", 1)
    table(doc, ["版本", "关键变化"], [
        ["v1.x", "间隔拍摄、参数面板、设置锁定、对焦同步"],
        ["v2.x", "移除软件回放；C1 日志快照；OFL 字体；包体降到 121 KB"],
        ["v3.0–3.4", "FN 菜单改为选项卡结构，反复调整层级与进入方式"],
        ["v3.5", "定稿交互：方向键只选择、转盘改值、确认键执行"],
        ["v3.6–3.8", "ISO AUTO 范围、张数 ±1/快转 +10、预设文件化、修复参数不可改"],
        ["v3.9", "C2 循环修复；代码清理与热路径优化；发布包"],
    ], widths=[3.0, 13.0])

    h(doc, "8  验收清单（节选）", 1)
    table(doc, ["编号", "检查项", "判定"], [
        ["A1", "四个选项卡可切换，列表随选项卡变化", "通过"],
        ["A2", "ISO 为 AUTO 时显示上下限两行，手动时隐藏", "通过"],
        ["A3", "转盘改值即时写入相机（日志 applySettings OK）", "通过"],
        ["A4", "预设文件内间隔/张数/场景均可改", "通过"],
        ["A5", "C2 在有非空预设时可循环切换", "通过"],
        ["A6", "张数慢转 ±1、快转 ±10，范围 1–9999", "通过"],
        ["A7", "自动构建 + 42 项检查全部通过", "通过"],
    ], widths=[1.6, 11.4, 3.0])

    doc.add_paragraph()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("— 文档结束 —")
    r.font.size = Pt(9)
    r.font.color.rgb = GREY

    doc.save(OUT)
    print("saved:", OUT, os.path.getsize(OUT))


if __name__ == "__main__":
    main()
