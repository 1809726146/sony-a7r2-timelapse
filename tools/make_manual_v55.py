# -*- coding: utf-8 -*-
"""生成《延时摄影 v5.5 设计手册》DOCX。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn

ROOT = r"D:\大\sony-cam-dev"
DESIGN = os.path.join(ROOT, "dist", "_design")
V55 = os.path.join(DESIGN, "v55")
OUT = os.path.join(ROOT, "dist", "延时摄影-V5.5-设计手册.docx")

BRASS = RGBColor(0xC4, 0x9A, 0x6C)
DARK = RGBColor(0x1A, 0x1A, 0x1A)
GREY = RGBColor(0x6C, 0x72, 0x7E)
EAST = "微软雅黑"

VER = "v5.5"
CODE = "95"


def setup(doc):
    st = doc.styles["Normal"]
    st.font.name = EAST
    st.font.size = Pt(10.5)
    st.element.rPr.rFonts.set(qn("w:eastAsia"), EAST)
    for s, sz in (("Heading 1", 16), ("Heading 2", 13), ("Heading 3", 11.5)):
        hd = doc.styles[s]
        hd.font.name = EAST
        hd.font.size = Pt(sz)
        hd.font.color.rgb = DARK
        hd.element.rPr.rFonts.set(qn("w:eastAsia"), EAST)
    sec = doc.sections[0]
    sec.top_margin = Cm(2.2)
    sec.bottom_margin = Cm(2.0)
    sec.left_margin = Cm(2.4)
    sec.right_margin = Cm(2.4)


def h(doc, text, level=1):
    return doc.add_heading(text, level=level)


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
        r = hdr[i].paragraphs[0].add_run(text)
        r.bold = True
        r.font.size = Pt(10)
        r.font.color.rgb = DARK
    for row in rows:
        cells = t.add_row().cells
        for i, text in enumerate(row):
            cells[i].text = ""
            r = cells[i].paragraphs[0].add_run(str(text))
            r.font.size = Pt(9.5)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    return t


def image(doc, path, width_cm=13.6, caption=None):
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
    r.font.color.rgb = BRASS

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("设 计 手 册")
    r.font.size = Pt(18)
    r.font.color.rgb = DARK

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("索尼 ILCE-7RM2 机内应用  ·  " + VER)
    r.font.size = Pt(12)
    r.font.color.rgb = GREY

    doc.add_paragraph()
    table(doc, ["项目", "内容"], [
        ["应用名称", "延时摄影"],
        ["包名 / 版本", "com.shenwenjun.a7r2app  ·  " + VER + "（versionCode " + CODE + "）"],
        ["运行环境", "ILCE-7RM2（A7R II），固件 4.01，Android 4.1.2 / SDK 16"],
        ["屏幕", "640×480 px，density 1.5（约 427×320 dp），61.2 mm 宽"],
        ["像素密度", "约 10.46 px/mm —— 1 px ≈ 0.096 mm，24 px ≈ 2.3 mm"],
        ["开发者", "Shenwenjun"],
        ["文档版本", VER + " · 2026-11"],
    ], widths=[4.0, 11.0])

    doc.add_paragraph()
    image(doc, os.path.join(V55, "v55-idle.png"), 13.6, "图 0  预览界面（待机）：剩余张数、四角刻线、三列参数、曝光标尺与读数")

    # ---------- 1 ----------
    doc.add_page_break()
    h(doc, "1  设计目标与约束", 1)
    para(doc, "本应用在相机内部运行，替代「手机遥控 + 定时器」的传统延时拍摄方式。"
              "目标是在 640×480 的小屏、有限的按键和相机固件限制下，完成一整套延时摄影的参数设置与自动拍摄。")
    h(doc, "1.1  设计目标", 2)
    for t in [
        "一眼看清：拍摄界面不遮挡取景，剩余张数、曝光三要素、间隔与张数常驻可见。",
        "字号优先：机身屏只有 61 mm 宽，所有正文 ≥ 17 px（约 1.6 mm），关键数值 30–32 px。",
        "单一强调色：整屏只有一种黄铜色承担「当前项 / 指针 / 强调」，其余全部是黑白层级。",
        "状态可信：写进相机的参数逐项校验，相机重开后自动重写；曝光补偿带数值读数，不靠猜。",
        "可复现：常用场景存为预设文件，一键切换。",
    ]:
        bullet(doc, t)

    h(doc, "1.2  硬约束与应对", 2)
    table(doc, ["约束", "来源", "设计应对"], [
        ["屏幕 640×480，观看距离约 30 cm", "相机 LCD", "正文 17–20 px，数值 30–32 px，行高 44 px"],
        ["系统字体是索尼子集，缺常用汉字", "系统", "优先加载相机自带 GB18030 字体，失败用内置 Noto 子集兜底"],
        ["按键有限（方向 / 中央 / FN / C1 / C2 / 转盘）", "机身", "方向键只选择、转盘只改值、确认键只执行"],
        ["每张必须重开相机（HAL 限制）", "固件", "间隔下限 5 秒；循环实测约 3.5 秒"],
        ["预览帧不下发给 App", "固件", "放弃冻结、放大等依赖帧的功能；对焦交给相机"],
        ["无输入法", "系统", "使用场景改为固定列表循环，不做自由输入"],
        ["曝光补偿只能 ±5 EV，但小屏难读", "固件 + 屏幕", "标尺收敛到 ±3 EV，并直接显示 +0.7 这类读数"],
    ], widths=[5.4, 2.4, 8.2])

    # ---------- 2 ----------
    doc.add_page_break()
    h(doc, "2  信息架构", 1)
    para(doc, "菜单为「选项卡 → 项目列表」两级；预设页额外有一层「文件 → 文件内参数」。"
              "首版曾做成 α6700 式三级（选项卡 → 组 → 项目），在 2.7 英寸屏上多点一次都要低头找，最终压成两级。")
    table(doc, ["层级", "内容", "说明"], [
        ["选项卡", "拍摄 / 设置 / 延时设置 / 预设", "←→ 切换，切换即显示该项目列表"],
        ["项目", "各选项卡下的具体设置项", "↑↓ 选择，转盘改值，确认键执行"],
        ["预设文件", "预设文件 1–4", "确认键进入，← 返回"],
        ["文件内参数", "间隔 时/分/秒、拍摄张数、使用场景、保存、载入", "可编辑，确认键执行保存 / 载入"],
    ], widths=[3.0, 6.8, 6.2])

    h(doc, "2.1  各页项目", 2)
    table(doc, ["选项卡", "项目（按显示顺序）"], [
        ["拍摄", "模式 / ISO /（ISO 为 AUTO 时插入 ISO自动下限、ISO自动上限）/ 快门 / 光圈 / 照片格式 / 压缩 / 白平衡 / 静音快门 / RAW 压缩 / 预设 / 日志快照"],
        ["设置", "对焦 / 测光 / 静音 / 防抖"],
        ["延时设置", "间隔 时 / 间隔 分 / 间隔 秒 / 拍摄张数"],
        ["预设", "预设文件 1 / 2 / 3 / 4"],
        ["预设 › 文件内", "间隔 时 / 间隔 分 / 间隔 秒 / 拍摄张数 / 使用场景 / 保存当前参数 / 载入该预设"],
    ], widths=[3.4, 12.6])

    image(doc, os.path.join(V55, "v55-menu.png"), 13.6, "图 1  菜单：选项卡 18 px、行高 44 px、项目名与值 20 px、当前项 3 px 黄铜竖线")

    # ---------- 3 ----------
    doc.add_page_break()
    h(doc, "3  交互设计", 1)
    h(doc, "3.1  按键分工", 2)
    para(doc, "核心原则：方向键只负责「选择」，转盘只负责「改值」，确认键只负责「执行」，三者不交叉。")
    table(doc, ["按键", "菜单内", "预览界面"], [
        ["↑ / ↓", "在当前列表内移动选择", "间隔 −1 / +1 秒"],
        ["← / →", "切换选项卡（仅选择）", "张数 −10 / +10"],
        ["转盘（任一）", "修改当前项目参数，立即写入相机", "光圈 / 快门 / 曝光补偿（±3 EV）"],
        ["确认键", "执行当前项目（预设保存 / 载入）", "开始 / 停止间隔拍摄"],
        ["FN", "打开 / 关闭菜单", "打开 / 关闭菜单"],
        ["C1", "—", "保存日志快照（SNAP-时间戳.TXT）"],
        ["C2", "—", "循环切换到下一个非空预设文件"],
        ["上键（拍摄中）", "—", "屏幕调暗 4 档 / 恢复"],
    ], widths=[3.4, 6.4, 6.2])

    h(doc, "3.2  菜单状态", 2)
    for t in [
        "打开 FN → 停在「拍摄」第 1 项，不经过中间页。",
        "←→ 切换选项卡，索引回到第 1 项，值不变。",
        "转盘 → 当前项的值立即变化并写入相机（去抖 300 ms，减少预览闪黑）。",
        "确认键 → 预设项执行保存 / 载入；普通参数不弹窗、不跳转。",
        "预设页确认键 → 进入该文件；文件内按 ← 返回列表。",
    ]:
        bullet(doc, t)

    h(doc, "3.3  拍摄流程", 2)
    table(doc, ["步骤", "用户动作", "系统反馈"], [
        ["1", "FN 打开菜单，设模式 / ISO / 格式", "列表高亮跟随选择，值即时更新"],
        ["2", "切到延时设置，定间隔与张数", "间隔按 时:分:秒 显示（最长 24:59:59）"],
        ["3", "确认键开始", "3 秒倒计时后开始；中间只显示整秒倒计时"],
        ["4", "拍摄中", "上键调暗屏幕；快门 / 删除键可停；右上角 RUN 指示灯"],
        ["5", "结束", "恢复亮度，显示「已完成 n / N、已存、跳过」并写日志"],
    ], widths=[1.6, 7.0, 7.4])

    h(doc, "3.4  曝光标尺", 2)
    para(doc, "标尺是这一版返工最多的地方。旧版只有一根指针压在刻度上，用户无法判断「现在补偿了多少」，"
              "且基线宽度（210 px）、19 个刻度（13 × 17 px）与指针换算基准（span/2/9）三者不一致，指针永远对不齐。")
    for t in [
        "几何：x0 = 44，step = 15，共 18 格（= ±3 EV），span = 270，中心 = 179。",
        "刻度：每 3 格一个主刻度（高 11 px），其余次刻度高 7 px；基线 1 px。",
        "数字：每个主刻度标 |k|（k 从 −3 到 +3），0 加粗；左端 −、右端 +。",
        "指针：2 × 17 px 黄铜竖条，位置 = 中心 + 补偿格数 × 15，夹在两端内。",
        "读数：标尺右侧 22 px 显示「0.0 / +0.7 / −1.0」，0 为白、其余为黄铜。",
        "范围：相机本身给 ±5 EV（15 格），界面收敛到 ±3 EV，转盘到这里停住。",
    ]:
        bullet(doc, t)
    image(doc, os.path.join(V55, "v55-ev07.png"), 13.6, "图 2  曝光补偿 +0.7：指针右移 7 格，右侧读数同步")

    # ---------- 4 ----------
    doc.add_page_break()
    h(doc, "4  视觉规范", 1)
    para(doc, "v4.0 起整体重做，目标是「去 AI 味」：不要渐变卡片、不要圆角、不要投影、不要彩色图标块。"
              "整屏只有黑、白、黄铜三种颜色团，层级靠字重、字号和间距表达。")
    h(doc, "4.1  色彩", 2)
    table(doc, ["用途", "色值", "说明"], [
        ["背景", "#0A0A0A", "近黑，夜间不干扰取景"],
        ["主文字", "#F0EFEA", "暖白，比纯白柔和"],
        ["次文字", "#C6C6BE", "标签、面包屑"],
        ["弱文字", "#93938B", "提示、单位"],
        ["强调", "#C49A6C", "黄铜色，只用于当前项、指针、读数"],
        ["分隔线", "#FFFFFF 10%", "行间细线"],
        ["压暗层", "顶部 72%→0、底部 96%", "取景画面上的渐变，保证文字可读"],
    ], widths=[4.2, 4.6, 6.6])

    h(doc, "4.2  字阶（按 640 px ≈ 61.2 mm 折算）", 2)
    table(doc, ["元素", "字号", "实际高度", "说明"], [
        ["剩余张数", "34 px", "≈3.3 mm", "数值用窄字面，衬在左上角"],
        ["参数标签", "14 px", "≈1.34 mm", "ISO / 快门 / 光圈"],
        ["参数值", "30–32 px", "≈3.0 mm", "按列宽自动缩到 20 px 起"],
        ["标尺数字", "13 px", "≈1.24 mm", "−3…+3"],
        ["标尺读数", "22 px", "≈2.1 mm", "0.0 / +0.7"],
        ["倒计时", "96 px", "≈9.2 mm", "拍摄中，仅整秒"],
        ["菜单选项卡", "18 px", "≈1.72 mm", "活动项加 2 px 下划线"],
        ["菜单项目名 / 值", "20 px", "≈1.9 mm", "行高 44 px"],
        ["提示行", "14 px", "≈1.34 mm", "页面底部"],
    ], widths=[4.2, 3.4, 3.6, 6.2])

    h(doc, "4.3  选中态与结构线", 2)
    for t in [
        "列表当前项：左侧 3 px 黄铜竖线 + 文字加粗，不加底色块。",
        "选项卡：活动项文字加粗 + 下方 2 px 白线，其余 72% 白。",
        "预览取景：四角 L 形刻线（18 px 臂长）围出安全区，不画整框。",
        "底部信息区：一条 10% 白分隔线 + 三列参数 + 竖向细线分开。",
    ]:
        bullet(doc, t)

    # ---------- 5 ----------
    h(doc, "5  预览界面栅格", 1)
    table(doc, ["区域", "坐标（px）", "内容"], [
        ["左上", "20, 4 / 20, 20", "「剩余」13 px + 张数 34 px + 「张」15 px"],
        ["右上", "右对齐至 596（拍摄中 540）", "格式 · 色温 · 测光（17 px），拍摄中右侧接 RUN 灯"],
        ["四角刻线", "(20,88) (602,88) (20,296) (602,296)", "L 形，臂长 18 px，72% 白"],
        ["参数列", "x = 20 / 240 / 440", "标签 14 px（y=330）、值 30–32 px（y=344）"],
        ["竖向细线", "x = 216 / 416，高 66", "9% 白，分隔三列"],
        ["曝光标尺", "x0 = 44，基线 y = 420，宽 270", "18 格，指针黄铜，右侧读数"],
        ["信息行", "右起 345，y = 430 / 436", "间隔 / 张数 / 已拍"],
        ["提示", "20, 446", "按键提示 14 px"],
    ], widths=[3.2, 5.4, 7.4])

    image(doc, os.path.join(V55, "v55-run.png"), 13.6, "图 3  拍摄中：整秒倒计时、RUN 指示灯、黄铜进度线、剩余与已存")

    # ---------- 6 ----------
    h(doc, "6  数据与持久化", 1)
    table(doc, ["数据", "存储位置", "内容"], [
        ["预设", "SharedPreferences（App 私有）", "槽位 1–4：间隔、张数、使用场景"],
        ["运行日志", "SD 卡 /A7R2APP/LOG.TXT", "每行「时间 级别 内容」，追加写"],
        ["日志快照", "SD 卡 /A7R2APP/SNAP-时间戳.TXT", "从内存缓冲写出，不读原日志"],
        ["相机参数", "相机固件", "经 createParametersModifier 写入，重开后重写"],
    ], widths=[3.2, 6.2, 6.6])

    # ---------- 7 ----------
    doc.add_page_break()
    h(doc, "7  技术取舍", 1)
    table(doc, ["问题", "尝试过的方案", "最终选择"], [
        ["快门每次开机只能触发一次", "CameraEx.reopen()", "release + open（0.83 s，稳定且更快）"],
        ["中文显示方块", "内置字体 / 系统字体", "优先相机自带 GB18030 字体，内置 Noto 子集兜底"],
        ["预览帧拿不到", "setPreviewCallback 两种", "放弃依赖帧的功能（冻结、放大）"],
        ["日志快照 EBUSY", "读原日志文件", "内存缓冲直接写出"],
        ["新建文件 ENOENT", "LOG-时间戳.TXT / A7R2APP-SNAP.TXT", "SNAP-时间戳.TXT（实测该命名可建）"],
        ["设置项太多看不完", "三级菜单 / 长列表", "四个选项卡 + 行高 44 px，一屏 8 行"],
        ["字号小看不清", "12–13 px", "正文 17–20 px、数值 30–32 px（机身屏 61 mm 宽）"],
        ["标尺对不齐", "三个独立系数", "统一 x0 / step / span，指针由格数直接换算"],
        ["曝光补偿读不出数", "只画指针", "右侧读数 + 每 EV 数字标注"],
        ["诊断截图污染启动", "启动自绘 PNG", "v5.5 起彻底移除"],
    ], widths=[4.2, 5.2, 6.6])

    # ---------- 8 ----------
    h(doc, "8  版本演进", 1)
    table(doc, ["版本", "关键变化"], [
        ["v1.x", "间隔拍摄、参数面板、设置锁定、对焦方式同步"],
        ["v2.x", "移除软件回放；C1 日志快照；OFL 字体；包体降到 121 KB"],
        ["v3.x", "FN 菜单改为选项卡结构；ISO AUTO 范围；预设文件化；交互定稿"],
        ["v4.0", "整体重做视觉：单一黄铜强调、无圆角投影、细体大字；两级菜单"],
        ["v4.1–4.3", "首次上机后全面放大字号；修复缺字与光圈写法"],
        ["v4.5", "按机身屏物理尺寸重算字阶（17–20 px 正文）"],
        ["v4.7–4.9", "提亮配色（次/弱文字）、四角刻线改 L 形、数值自动缩排"],
        ["v5.0–5.3", "曝光标尺重做：±3 EV、数字刻度、黄铜读数；倒计时整秒；去掉「下一张」"],
        ["v5.4", "新应用图标（城市延时：相机 + 钟面 + 快进）"],
        ["v5.5", "移除启动自检截图；发布包"],
    ], widths=[3.0, 13.0])

    h(doc, "9  验收", 1)
    table(doc, ["类别", "范围", "结果"], [
        ["构建检查", "APK 元信息、dex 字符串、源码不变量、id 交叉、字体覆盖、文档同步，共 42 项", "42 / 42 通过"],
        ["菜单分支审计", "4 个选项卡 × 全部可见项目，名称 / 取值 / 变值三分支", "35 / 35 齐全"],
        ["真机测试", "启动、菜单、四选项卡逐项、硬件键、拍摄中、结束、边界", "通过（用户实测）"],
    ], widths=[3.0, 10.4, 3.2])

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
