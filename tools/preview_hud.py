# -*- coding: utf-8 -*-
"""把 Java 里的 HUD / 菜单几何用 PIL 复现一遍，用于离线检查版式是否会撞车。

只验证「位置和尺寸」，字体用的是内置子集，跟机上 GB18030 略有差别，
所以这脚本不追求像素级一致，只用来抓重叠、越界、错行。
坐标必须与 HudView.java / MenuView.java 保持一致。
"""
import io, os, sys
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "A7R2App")
FONT = os.path.join(APP, "res", "raw", "cjk.ttf")
OUT = os.path.join(ROOT, "dist", "_design")

W, H = 640, 480
BG = (10, 10, 10)
INK = (240, 239, 234)
MID = (154, 154, 147)
DIM = (92, 92, 86)
BRASS = (196, 154, 108)


def a(color, alpha):
    return (color[0], color[1], color[2], int(255 * alpha))


def font(size):
    return ImageFont.truetype(FONT, size)


def text(d, s, x, y, size, color):
    f = font(size)
    d.text((x, y + size * 0.78 - size), s, font=f, fill=color)  # PIL 的 y 是顶部，减掉
    return d.textlength(s, font=f)


def text_right(d, s, right, y, size, color):
    w = d.textlength(s, font=font(size))
    return text(d, s, right - w, y, size, color)


def text_center(d, s, cx, y, size, color):
    w = d.textlength(s, font=font(size))
    return text(d, s, cx - w / 2, y, size, color)


def rect(d, x, y, w, h, color):
    d.rectangle([x, y, x + w, y + h], fill=color)


def frame(d, x, y, w, h, color):
    d.rectangle([x, y, x + w, y + h], outline=color)


def corners(d):
    for x, y in ((20, 76), (606, 76), (20, 327), (606, 327)):
        frame(d, x, y, 14, 14, a(INK, 0.55))


def bottom_block(d, rule_y, label_y, value_y, iso, shutter, aperture):
    rect(d, 20, rule_y, 600, 1, a((255, 255, 255), 0.10))
    rect(d, 220, label_y - 4, 1, 44, a((255, 255, 255), 0.09))
    rect(d, 420, label_y - 4, 1, 44, a((255, 255, 255), 0.09))
    for label, x in (("ISO", 20), ("快门", 242), ("光圈", 442)):
        text(d, label, x, label_y, 9, a(INK, 0.52))
    text(d, iso, 20, value_y, 20, INK)
    text(d, shutter, 242, value_y, 20, INK)
    text(d, aperture, 442, value_y, 20, INK)


def scale(d, badge_y, base_y, meter):
    text(d, "A", 20, badge_y, 9, BRASS)
    rect(d, 44, base_y, 200, 1, a((255, 255, 255), 0.30))
    for i in range(13):
        major = i % 3 == 0
        rect(d, 44 + i * 16, base_y - (10 if major else 6), 1, 10 if major else 6,
             a((255, 255, 255), 0.62 if major else 0.38))
    nx = min(242, max(46, 44 + 100 + meter * 100 / 9))
    rect(d, nx, base_y - 9, 1, 14, BRASS)


def idle():
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img, "RGBA")
    text(d, "剩余", 20, 4, 8, DIM)
    w = text(d, "320", 20, 20, 22, INK)
    text(d, "张", 20 + w + 7, 20 + 22 - 9 - 1, 9, MID)
    text_right(d, "RAW · 4300K · 多重测光", 620, 18, 9, MID)
    corners(d)
    bottom_block(d, 330, 342, 352, "1600", "1/60", "F4.0")
    scale(d, 388, 400, 0)
    x, y = 345, 428
    x += text(d, "间隔", x, y + 6, 9, a(INK, 0.52)) + 6
    x += text(d, "0:05", x, y, 14, INK) + 20
    x += text(d, "张数", x, y + 6, 9, a(INK, 0.52)) + 6
    x += text(d, "250", x, y, 14, INK) + 20
    x += text(d, "已拍", x, y + 6, 9, a(INK, 0.52)) + 6
    text(d, "0", x, y, 14, a(INK, 0.9))
    text(d, "FN 菜单    中央 开始", 20, 470, 8, DIM)
    return img


def running():
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img, "RGBA")
    text(d, "剩余", 20, 4, 8, DIM)
    w = text(d, "198", 20, 20, 22, INK)
    text(d, "张", 20 + w + 7, 20 + 22 - 9 - 1, 9, MID)
    text_right(d, "RAW · 4300K", 560, 18, 9, MID)
    rect(d, 574, 26, 5, 5, BRASS)
    text(d, "RUN", 586, 22, 9, BRASS)
    text_center(d, "下一张", W / 2, 160, 9, MID)
    bw = d.textlength("3.2", font=font(78))
    bx = W / 2 - bw / 2
    text(d, "3.2", bx, 178, 78, INK)
    text(d, "秒", bx + bw + 8, 232, 13, a(INK, 0.62))
    rect(d, 0, 330, W, 1, a((255, 255, 255), 0.12))
    rect(d, 0, 330, 125 * 52 / 250, 1, BRASS)
    text(d, "52 / 250", 20, 338, 8, a(INK, 0.62))
    text_right(d, "已存 52", 620, 338, 8, a(INK, 0.62))
    bottom_block(d, 348, 356, 366, "100", "1/30", "F5.6")
    text(d, "快门 暂停    ↑ 调光    FN 退出", 20, 470, 8, DIM)
    return img


TABS = ["拍摄", "设置", "延时设置", "预设"]
ITEMS = [("模式", "A 光圈优先"), ("ISO", "AUTO 1600 (100-1600)"), ("ISO自动下限", "100"),
         ("ISO自动上限", "1600"), ("快门", "1/60"), ("光圈", "F4.0"), ("照片格式", "RAW"),
         ("压缩", "压缩"), ("白平衡", "4300K"), ("静音快门", "关"), ("RAW 压缩", "压缩"),
         ("预设", "1  0:05 × 250"), ("日志快照", "按 C1 保存")]


def menu(sel=0):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img, "RGBA")
    x = 20
    for i, t in enumerate(TABS):
        active = i == 0
        tw = text(d, t, x, 14, 9, INK if active else MID)
        if active:
            rect(d, x, 34, tw, 1, a((255, 255, 255), 0.55))
        x += tw + 24
    text_right(d, "1/4", 620, 14, 8, DIM)
    crumb = "拍摄  ›  拍摄  ›  " + ITEMS[sel][0]
    text(d, crumb, 20, 44, 9, DIM)
    rect(d, 20, 58, 600, 1, a((255, 255, 255), 0.09))
    for i, (label, value) in enumerate(ITEMS):
        y = 66 + i * 26
        active = i == sel
        if active:
            rect(d, 18, y, 2, 26, BRASS)
        text(d, label, 26, y + 6, 13, INK if active else a(INK, 0.78))
        text_right(d, value, 620, y + 7, 12, BRASS if active else MID)
        if i < len(ITEMS) - 1:
            rect(d, 24, y + 25, 592, 1, a((255, 255, 255), 0.05))
    text(d, "←→ 选项卡    ↑↓ 选项    转盘 改值    中央 执行    FN 退出", 20, 450, 8, DIM)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    shots = [("v40-app-idle", idle()), ("v40-app-run", running()),
             ("v40-app-menu", menu(0)), ("v40-app-menu2", menu(10))]
    sheet = Image.new("RGB", (W * 2 + 30, H * 2 + 60), (24, 24, 24))
    for i, (name, im) in enumerate(shots):
        im.save(os.path.join(OUT, name + ".png"))
        sheet.paste(im, (10 + (i % 2) * (W + 10), 10 + (i // 2) * (H + 25)))
    sheet.save(os.path.join(OUT, "v40-app-sheet.png"))
    print("saved 4 screens + sheet")


if __name__ == "__main__":
    main()
