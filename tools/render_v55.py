# -*- coding: utf-8 -*-
"""按 v5.5 的实现数值离线渲染界面图（640×480），用于设计手册与发布包。

这些图不是相机截图，而是用同一套坐标重绘的「设计稿」：
预览·待机 / 预览·曝光补偿 +0.7 / 预览·拍摄中 / 菜单。
"""
import os
from PIL import Image, ImageDraw, ImageFont

W, H = 640, 480

BG = (0x0A, 0x0A, 0x0A)
INK = (0xF0, 0xEF, 0xEA)
MID = (0xC6, 0xC6, 0xBE)
DIM = (0x93, 0x93, 0x8B)
BRASS = (0xC4, 0x9A, 0x6C)

FONT_R = r"C:\Windows\Fonts\msyh.ttc"
FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"

OUT = r"D:\大\sony-cam-dev\dist\_design\v55"


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT_R, size)


def alpha(color, a):
    if isinstance(color, int):
        color = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    return (color[0], color[1], color[2], int(round(255 * a)))


def text(d, xy, s, size, color, bold=False, align="left"):
    f = font(size, bold)
    x, y = xy
    if align == "right":
        w = d.textlength(s, font=f)
        x -= w
    elif align == "center":
        w = d.textlength(s, font=f)
        x -= w / 2.0
    d.text((x, y + size * 0.12), s, font=f, fill=color)


def rect(d, box, color):
    d.rectangle(box, fill=color)


def corners(d):
    a = alpha(INK, 0.72)
    L = 18
    for (x, y, sx, sy) in ((20, 88, 1, 1), (602, 88, -1, 1), (20, 296, 1, -1), (602, 296, -1, -1)):
        d.line([(x, y), (x + sx * L, y)], fill=a, width=2)
        d.line([(x, y), (x, y + sy * L)], fill=a, width=2)


def scale(d, meter, ev_step=1.0 / 3.0):
    x0, step, slots = 44, 15, 18
    span = step * slots
    base = 420
    rect(d, (x0, base, x0 + span, base + 1), alpha(INK, 0.45))
    for i in range(slots + 1):
        x = x0 + i * step
        major = (i % 3 == 0)
        h = 11 if major else 7
        rect(d, (x, base - h, x + 1, base), alpha(INK, 0.82 if major else 0.50))
        if major:
            k = int(i / 3) - 3
            label = str(abs(k))
            text(d, (x, base - 28), label, 13, alpha(INK, 0.92 if k == 0 else 0.62), bold=(k == 0), align="center")
    text(d, (x0, base + 4), "-", 12, alpha(INK, 0.6))
    text(d, (x0 + span, base + 4), "+", 12, alpha(INK, 0.6), align="right")
    px = x0 + span / 2.0 + meter * step
    px = max(x0 + 2, min(x0 + span - 2, px))
    rect(d, (px - 1, base - 13, px + 1, base + 4), BRASS)
    ev = meter * ev_step
    ev_text = "0.0" if abs(ev) < 0.05 else ("%+.1f" % ev)
    ev_color = alpha(INK, 0.72) if abs(ev) < 0.05 else BRASS
    text(d, (x0 + span + 16, base - 16), ev_text, 22, ev_color)


def params(d, iso, shutter, aperture, top_label=330, top_value=344):
    cols = ((20, "ISO", iso), (240, "快门", shutter), (440, "光圈", aperture))
    for x, k, v in cols:
        text(d, (x, top_label), k, 14, alpha(INK, 0.75))
        text(d, (x, top_value), v, 32, INK)
    for x in (216, 416):
        rect(d, (x, top_label, x + 1, top_label + 66), alpha(0xFFFFFF, 0.09))


def info_row(d, items, x0=345, y_label=436, y_value=430):
    x = x0
    for k, v in items:
        text(d, (x, y_label), k, 13, alpha(INK, 0.75))
        kw = d.textlength(k, font=font(13))
        text(d, (x + kw + 8, y_value), v, 19, INK)
        x += kw + 8 + d.textlength(v, font=font(19)) + 24


def idle(meter=0):
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im, "RGBA")
    text(d, (20, 4), "剩余", 13, MID)
    text(d, (20, 20), "5900", 34, INK)
    text(d, (20 + d.textlength("5900", font=font(34)) + 7, 34), "张", 15, MID)
    text(d, (596, 18), "RAW · 4500K · 多重测光", 17, alpha(INK, 0.92), align="right")
    corners(d)
    params(d, "4000", "1/60", "F4.0")
    text(d, (20, 400), "A", 14, BRASS, bold=True)
    scale(d, meter)
    info_row(d, [("间隔", "0:05"), ("张数", "250"), ("已拍", "0")])
    text(d, (20, 446), "FN 菜单    中央 开始", 14, MID)
    return im


def running():
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im, "RGBA")
    text(d, (20, 4), "剩余", 13, MID)
    text(d, (20, 20), "198", 34, INK)
    text(d, (20 + d.textlength("198", font=font(34)) + 7, 34), "张", 15, MID)
    text(d, (540, 18), "RAW · 4500K · 多重测光", 17, alpha(INK, 0.92), align="right")
    rect(d, (572, 22, 579, 29), BRASS)
    text(d, (588, 18), "RUN", 14, BRASS, bold=True)
    corners(d)
    big = "3"
    bw = d.textlength(big, font=font(96))
    text(d, (320 - bw / 2.0 - 12, 150), big, 96, INK)
    text(d, (320 + bw / 2.0 - 12 + 14, 208), "秒", 22, alpha(INK, 0.88))
    rect(d, (20, 300, 620, 302), alpha(0xFFFFFF, 0.12))
    rect(d, (20, 300, 20 + 125, 302), BRASS)
    text(d, (20, 306), "52 / 250", 13, alpha(INK, 0.88))
    text(d, (620, 306), "已存 52 · 卡速 2.6s", 13, alpha(INK, 0.88), align="right")
    params(d, "4000", "1/80", "F4.0", 330, 344)
    text(d, (20, 446), "快门 暂停    上键 调光    FN 退出", 14, MID)
    return im


MENU = {
    "tabs": ["拍摄", "设置", "延时设置", "预设"],
    "crumb": "拍摄 · 拍摄参数 · 模式",
    "rows": [("模式", "A 光圈优先"), ("ISO", "4000"), ("快门", "1/60"),
             ("光圈", "F4.0"), ("照片格式", "RAW"), ("压缩", "压缩"), ("白平衡", "AUTO")],
}


def menu(sel=0, tab=0):
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im, "RGBA")
    x = 20
    for i, t in enumerate(MENU["tabs"]):
        active = (i == tab)
        text(d, (x, 8), t, 18, INK if active else alpha(INK, 0.72), bold=active)
        if active:
            rect(d, (x, 36, x + d.textlength(t, font=font(18, True)), 38), alpha(INK, 0.75))
        x += int(d.textlength(t, font=font(18))) + 26
    text(d, (620, 10), "1/4", 14, MID, align="right")
    text(d, (20, 46), MENU["crumb"], 16, MID)
    rect(d, (20, 70, 620, 71), alpha(0xFFFFFF, 0.10))
    y = 80
    for i, (k, v) in enumerate(MENU["rows"]):
        if i == sel:
            rect(d, (18, y + 2, 21, y + 42), BRASS)
            text(d, (26, y + 9), k, 20, INK, bold=True)
            text(d, (620, y + 9), v, 20, alpha(INK, 0.95), align="right")
        else:
            text(d, (26, y + 9), k, 20, alpha(INK, 0.90))
            text(d, (620, y + 9), v, 20, alpha(INK, 0.88), align="right")
        if i < len(MENU["rows"]) - 1:
            rect(d, (20, y + 44, 620, y + 45), alpha(0xFFFFFF, 0.10))
        y += 44
    text(d, (20, 446), "左右 选项卡    上下 选项    转盘 改值    中央 执行    FN 退出", 14, MID)
    return im


def main():
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    idle(0).save(os.path.join(OUT, "v55-idle.png"))
    idle(7).save(os.path.join(OUT, "v55-ev07.png"))
    idle(-3).save(os.path.join(OUT, "v55-ev-1.png"))
    running().save(os.path.join(OUT, "v55-run.png"))
    menu(0, 0).save(os.path.join(OUT, "v55-menu.png"))
    menu(1, 3).save(os.path.join(OUT, "v55-menu-preset.png"))
    print("saved to", OUT)


if __name__ == "__main__":
    main()
