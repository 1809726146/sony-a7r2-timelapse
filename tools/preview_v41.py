# -*- coding: utf-8 -*-
"""v4.1 版式离线预演（PIL），用于在没有相机时检查字号/间距是否溢出。

坐标和 Java 端（HudView / MenuView）保持同一套数字：
预览 顶 11/34px、参数 12/30px、信息 12/17px、提示 11px；
菜单 行高 38、项目 17px、选项卡 16px、提示 12px。
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "A7R2App", "res", "raw", "cjk.ttf")
OUT = os.path.join(ROOT, "dist", "_design")

BG = (10, 10, 10)
INK = (240, 239, 234)
MID = (154, 154, 147)
DIM = (92, 92, 86)
BRASS = (196, 154, 108)

_cache = {}


def a(color, alpha):
    return tuple(int(c * alpha) for c in color)


def font(size):
    key = int(size)
    if key not in _cache:
        _cache[key] = ImageFont.truetype(FONT, key)
    return _cache[key]


def text(d, s, x, y, size, color):
    d.text((x, y), s, font=font(size), fill=color)
    return d.textlength(s, font=font(size))


def text_right(d, s, right, y, size, color):
    d.text((right - d.textlength(s, font=font(size)), y), s, font=font(size), fill=color)


def text_center(d, s, cx, y, size, color):
    d.text((cx - d.textlength(s, font=font(size)) / 2, y), s, font=font(size), fill=color)


def rect(d, x, y, w, h, color):
    if w > 0 and h > 0:
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)


def frame(d, x, y, w, h, color):
    d.rectangle([x, y, x + w - 1, y + h - 1], outline=color)


def corners(d):
    c = a(INK, 0.55)
    for x, y in ((20, 88), (602, 88), (20, 296), (602, 296)):
        frame(d, x, y, 18, 18, c)


def crosshair(d):
    c = a(INK, 0.5)
    rect(d, 306, 200, 28, 1, c)
    rect(d, 320, 186, 1, 28, c)


def bottom_block(d, rule_y, label_y, value_y, iso, shutter, aperture):
    rect(d, 20, rule_y, 600, 1, a((255, 255, 255), 0.12))
    rect(d, 216, label_y - 6, 1, 66, a((255, 255, 255), 0.10))
    rect(d, 416, label_y - 6, 1, 66, a((255, 255, 255), 0.10))
    lc = a(INK, 0.56)
    text(d, "ISO", 20, label_y, 12, lc)
    text(d, "快门", 240, label_y, 12, lc)
    text(d, "光圈", 440, label_y, 12, lc)
    text(d, iso, 20, value_y, 30, INK)
    text(d, shutter, 240, value_y, 30, INK)
    text(d, aperture, 440, value_y, 30, INK)


def scale(d, badge_y, base_y, meter):
    text(d, "A", 20, badge_y, 12, BRASS)
    rect(d, 44, base_y, 210, 1, a((255, 255, 255), 0.32))
    for i in range(13):
        major = i % 3 == 0
        hh = 11 if major else 7
        rect(d, 44 + i * 17, base_y - hh, 1, hh, a((255, 255, 255), 0.62 if major else 0.38))
    nx = max(46, min(252, 44 + 105 + meter * 105 / 9.0))
    rect(d, nx, base_y - 10, 2, 16, BRASS)


def idle():
    img = Image.new("RGB", (640, 480), BG)
    d = ImageDraw.Draw(img)
    text(d, "剩余", 20, 2, 11, DIM)
    w = text(d, "8804", 20, 20, 34, INK)
    text(d, "张", 20 + w + 6, 38, 14, MID)
    text_right(d, "RAW · 4300K · 多重测光", 596, 20, 13, MID)
    corners(d)
    crosshair(d)
    bottom_block(d, 326, 336, 350, "80", "30.0s", "F4.0")
    scale(d, 402, 416, 0)
    x = 330
    y = 444
    lc = a(INK, 0.56)
    x += text(d, "间隔", x, y + 5, 12, lc) + 7
    x += text(d, "0:10", x, y, 17, INK) + 16
    x += text(d, "张数", x, y + 5, 12, lc) + 7
    x += text(d, "250", x, y, 17, INK) + 16
    x += text(d, "已拍", x, y + 5, 12, lc) + 7
    text(d, "2", x, y, 17, a(INK, 0.9))
    text(d, "FN 菜单    中央 开始", 20, 462, 11, DIM)
    return img


def running():
    img = Image.new("RGB", (640, 480), BG)
    d = ImageDraw.Draw(img)
    text(d, "剩余", 20, 2, 11, DIM)
    w = text(d, "198", 20, 20, 34, INK)
    text(d, "张", 20 + w + 6, 38, 14, MID)
    text_right(d, "RAW · 4300K · 多重测光", 540, 20, 13, MID)
    rect(d, 572, 22, 7, 7, BRASS)
    text(d, "RUN", 586, 18, 12, BRASS)
    text_center(d, "下一张", 320, 148, 12, MID)
    bw = d.textlength("7.4", font=font(96))
    bx = 320 - bw / 2
    text(d, "7.4", bx, 172, 96, INK)
    text(d, "秒", bx + bw + 10, 240, 20, a(INK, 0.62))
    rect(d, 0, 326, 640, 1, a((255, 255, 255), 0.14))
    rect(d, 0, 326, 600 * 2 / 250.0, 2, BRASS)
    text(d, "2 / 250", 20, 332, 11, a(INK, 0.66))
    text_right(d, "已存 2 · 卡速 2.6s", 620, 332, 11, a(INK, 0.66))
    bottom_block(d, 350, 356, 372, "4000", "1/80", "F4.0")
    text(d, "快门 暂停    上键 调光    FN 退出", 20, 462, 11, DIM)
    return img


TABS = ["拍摄", "设置", "延时设置", "预设"]
ITEMS = [("模式", "A 光圈优先"), ("ISO", "AUTO 1600 (100-1600)"), ("ISO自动下限", "100"),
         ("ISO自动上限", "1600"), ("快门", "1/60"), ("光圈", "F4.0"), ("照片格式", "RAW"),
         ("压缩", "压缩"), ("白平衡", "4300K")]


def menu(sel=0):
    img = Image.new("RGB", (640, 480), BG)
    d = ImageDraw.Draw(img)
    x = 20
    for i, t in enumerate(TABS):
        active = i == 0
        w = text(d, t, x, 8, 16, INK if active else MID)
        if active:
            rect(d, x, 36, w, 2, a(INK, 0.75))
        x += w + 26
    text_right(d, "1/4", 620, 10, 12, DIM)
    text(d, "拍摄  ·  1. 拍摄参数  ·  模式", 20, 44, 13, DIM)
    rect(d, 20, 70, 600, 1, a((255, 255, 255), 0.10))
    for i, (label, value) in enumerate(ITEMS):
        y = 80 + i * 38
        active = i == sel
        if active:
            rect(d, 16, y + 2, 3, 34, BRASS)
        text(d, label, 26, y + 10, 17, INK if active else a(INK, 0.80))
        text_right(d, value, 620, y + 11, 17, BRASS if active else MID)
        if i < len(ITEMS) - 1:
            rect(d, 24, y + 37, 592, 1, a((255, 255, 255), 0.06))
    text(d, "左右 选项卡    上下 选项    转盘 改值    中央 执行    FN 退出", 20, 446, 12, DIM)
    return img


def main():
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    shots = [("v41-idle.png", idle()), ("v41-run.png", running()), ("v41-menu.png", menu(0))]
    for name, img in shots:
        p = os.path.join(OUT, name)
        img.save(p)
        print("saved", p)
    sheet = Image.new("RGB", (640 * 2 + 30, 480 * 2 + 30), (24, 24, 24))
    for i, (_, img) in enumerate(shots):
        sheet.paste(img, (10 + (i % 2) * 650, 10 + (i // 2) * 490))
    p = os.path.join(OUT, "v41-sheet.png")
    sheet.save(p)
    print("saved", p)


if __name__ == "__main__":
    main()
