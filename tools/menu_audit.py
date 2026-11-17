# -*- coding: utf-8 -*-
"""菜单可交互项审计（v4.0）。

静态分析 CameraSettings.java，列出「每个选项卡、每个可见项目」是否有：
  - 名称（itemName 的 switch 分支）
  - 取值文本（label 的 switch 分支）
  - 变值/执行（change 的 switch 分支）
只要有一项缺失，按键就会「按下去没反应」，所以这是交互测试的第一道关。

用法：python tools/menu_audit.py [-v]
"""
from __future__ import print_function

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "A7R2App", "src", "com", "shenwenjun", "a7r2app", "CameraSettings.java")

TABS = ["\u62cd\u6444", "\u8bbe\u7f6e", "\u5ef6\u65f6\u8bbe\u7f6e", "\u9884\u8bbe"]
PRESET_FILE_ITEMS_HINT = ["间隔-时", "间隔-分", "间隔-秒", "拍摄张数", "使用场景", "保存当前参数", "载入该预设"]


def read(path):
    return io.open(path, encoding="utf-8").read()


def bucket_cases(text):
    """把 case ITEM_X: 按所在方法名分组"""
    buckets = {}
    cur = "?"
    for line in text.splitlines():
        # 允许任意修饰符组合（public static String itemName(...) 这种两个修饰符的要能吃下）
        m = re.match(r"\s{4}(?:(?:public|private|protected|static|final|synchronized|abstract)\s+)*"
                     r"[\w\[\]<>.]+\s+(\w+)\s*\(", line)
        if m:
            cur = m.group(1)
        for c in re.findall(r"case\s+(ITEM_\w+)\s*:", line):
            buckets.setdefault(cur, set()).add(c)
    return buckets


def consts(text):
    out = {}
    for name, val in re.findall(r"static final int (ITEM_\w+)\s*=\s*(\d+);", text):
        out[name] = int(val)
    return out


def tree(text):
    m = re.search(r"private static final int\[\]\[\]\[\] TREE = \{(.*?)\n    \};", text, re.S)
    if not m:
        return []
    body = m.group(1)
    tabs = []
    for group in re.findall(r"\{([^{}]*)\}", body):
        items = re.findall(r"ITEM_\w+", group)
        if items:
            tabs.append(items)
    return tabs


def preset_file_items(text):
    m = re.search(r"PRESET_FILE_ITEMS\s*=\s*\{(.*?)\};", text, re.S)
    if not m:
        return []
    return re.findall(r"ITEM_\w+", m.group(1))


def main():
    text = read(SRC)
    c = consts(text)
    buckets = bucket_cases(text)
    names = buckets.get("itemName", set())
    labels = buckets.get("label", set())
    changes = set()
    for k, v in buckets.items():
        if k in ("itemName", "label", "summary", "itemAt", "visibleItemAt", "shotItemAt"):
            continue
        changes |= v
    # change() 里常用 case A: case B: 连写，保险起见把 change/confirmItem 合并
    for k in ("change", "confirmItem"):
        changes |= buckets.get(k, set())

    tab_items = tree(text)
    pfile = preset_file_items(text) or []
    iso_dyn = ["ITEM_ISO_MIN", "ITEM_ISO_MAX"]

    rows = []
    for t, items in enumerate(tab_items):
        if t == 0:
            variants = [("iso-man", items), ("iso-auto", items[:2] + iso_dyn + items[2:])]
        elif t == 3:
            variants = [("files", items), ("file-in", pfile)]
        else:
            variants = [("", items)]
        for tag, its in variants:
            for it in its:
                rows.append((TABS[t], tag, it))

    def out(line):
        sys.stdout.write(line + "\n")

    out("%-6s %-9s %-18s %-6s %-6s %-6s" % ("TAB", "STATE", "ITEM", "NAME", "LABEL", "CHANGE"))
    bad = []
    for tab, tag, it in rows:
        n = it in names
        l = it in labels
        ch = it in changes
        out("%-6s %-9s %-18s %-6s %-6s %-6s" % ("T%d" % (TABS.index(tab) + 1), tag, it,
                                                 "OK" if n else "--", "OK" if l else "--",
                                                 "OK" if ch else "--"))
        if not (n and l and ch):
            bad.append((tab, tag, it, n, l, ch))

    total = len(rows)
    out("")
    out("total=%d complete=%d missing=%d" % (total, total - len(bad), len(bad)))
    if bad:
        out("problems:")
        for b in bad:
            out("  %s/%s %s -> name %s label %s change %s" % (b[0], b[1], b[2], b[3], b[4], b[5]))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
