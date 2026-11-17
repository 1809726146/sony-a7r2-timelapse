"""v1.8 自动化验证：APK 元信息、关键代码路径、资源一致性、字体覆盖、文档同步"""
import io
import os
import re
import sys
import zipfile

# 控制台编码（Windows 默认 GBK/cp1252）会导致中文报错，统一切 UTF-8
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "A7R2App")
APK = os.path.join(APP, "build", "A7R2App-debug.apk")
SRC = os.path.join(APP, "src", "com", "shenwenjun", "a7r2app")

results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))


def read(path):
    return io.open(path, encoding="utf-8").read()


# ---------- 1. APK 元信息 ----------
apk_ok = os.path.exists(APK)
check("APK 存在", apk_ok, "%.0f KB" % (os.path.getsize(APK) / 1024) if apk_ok else "缺失")

if apk_ok:
    z = zipfile.ZipFile(APK)
    names = z.namelist()
    dex = z.read("classes.dex")
    check("APK 含 classes.dex", "classes.dex" in names)
    check("APK 含中文字体", "res/raw/cjk.ttf" in names)
    check("APK 含资源", "resources.arsc" in names)
    for probe in ("focus: mode=", "camera reopened", "shot stored in", "DMF", "log snapshot"):
        check("dex 字符串: %s" % probe, probe.encode("utf-8") in dex)
    check("dex 无已删功能(preview callback)", b"setPreviewCallback" not in dex)

# ---------- 2. 源码关键点 ----------
src = {f: read(os.path.join(SRC, f)) for f in os.listdir(SRC) if f.endswith(".java")}

check("间隔下限 = 5 秒", "INTERVAL_MIN = 5" in src["IntervalShooter.java"],
      re.search(r"INTERVAL_MIN = (\d+)", src["IntervalShooter.java"]).group(0))
check("间隔上限 24:59:59", "24 * 3600 + 59 * 60 + 59" in src["IntervalShooter.java"])
check("间隔支持时/分/秒", "adjustHours(" in src["IntervalShooter.java"]
      and "intervalText(" in src["IntervalShooter.java"]
      and "ITEM_INT_H" in src["CameraSettings.java"])
check("预设读/存", "loadPreset(" in src["CameraSettings.java"]
      and "savePreset(" in src["CameraSettings.java"]
      and "class Presets" in src.get("Presets.java", ""))
check("卡余张数", "StatFs" in src.get("Storage.java", "")
      and "estimatedShots(" in src["Storage.java"])
check("C2 切换预设", "cyclePreset()" in src["MainActivity.java"])
check("对焦含 DMF", "DMF" in src["CameraSettings.java"] and "focus-mode" in src["CameraSettings.java"])
check("对焦项 5 个", src["CameraSettings.java"].count('"MF", "AF-S", "AF-C", "AF-A", "DMF"') >= 1)
check("启动读 focus-mode", 'getParameter("focus-mode")' in src["CameraSettings.java"])
check("启动清定制标记", "customized = false;" in src["CameraSettings.java"])
check("重开无条件重写", "dirtyApply = false;\n            apply();" in src["CameraSettings.java"]
      or "apply();" in src["CameraSettings.java"])
check("已移除软件检视", "reviewSeconds" not in src["CameraSettings.java"] and "onShotImage" not in src["MainActivity.java"])
check("菜单行分隔线", "Ui.alpha(0xFFFFFF, 0.10f)" in src["MenuView.java"])
check("三级菜单树", "TREE" in src["CameraSettings.java"]
      and "LEVEL_ITEM" in src["MenuView.java"]
      and "handleSettingsKey" in src["MainActivity.java"])
check("无放大对焦残留",
      "setPreviewMagnification(" not in "".join(src.values())
      and "getPreviewMagnificationRatio(" not in "".join(src.values()))
check("无冻结帧残留", "grabFreezeFrame" not in "".join(src.values())
      and "onReopenStart" not in "".join(src.values()))
check("C1 导出已移除", "dumpParameters" not in "".join(src.values()))

# ---------- 3. 资源与代码一致性 ----------
layout = read(os.path.join(APP, "res", "layout", "main.xml"))
layout_ids = set(re.findall(r'android:id="@\+id/(\w+)"', layout))
code_ids = set()
for text in src.values():
    code_ids |= set(re.findall(r"R\.id\.(\w+)", text))
missing = code_ids - layout_ids
check("代码引用的 id 都在布局里", not missing, "缺失: %s" % missing if missing else "%d 个 id" % len(code_ids))
unused = layout_ids - code_ids
check("布局无未使用 id", not unused, "未用: %s" % unused if unused else "全部被引用")

strings = read(os.path.join(APP, "res", "values", "strings.xml"))
check("strings.xml 含 app_name", 'name="app_name"' in strings)
check("strings.xml 含 developer", 'name="developer"' in strings)
check("应用名 = 延时摄影", "延时摄影" in strings)
check("开发者 = Shenwenjun", "Shenwenjun" in strings)

manifest = read(os.path.join(APP, "AndroidManifest.xml"))
check("versionCode", 'android:versionCode="95"' in manifest)
check("versionName", 'android:versionName="5.5"' in manifest)
check("开发者 meta-data", "com.sony.scalar.developer" in manifest)

# ---------- 4. 字体覆盖 ----------
try:
    sys.path.insert(0, os.path.join(ROOT, "tools", "pylib"))
    from fontTools.ttLib import TTFont
    cmap = TTFont(os.path.join(APP, "res", "raw", "cjk.ttf")).getBestCmap()
    chars = set()
    for base, dirs, files in os.walk(APP):
        if "build" in base.lower() or "obj" in base.lower():
            continue
        for fn in files:
            if fn.endswith((".java", ".xml")):
                try:
                    text = read(os.path.join(base, fn))
                except Exception:
                    continue
                if fn.endswith(".java"):
                    # 只看会显示到屏幕上的字符串字面量：先去掉注释，注释里的中文不需要字形
                    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
                    text = re.sub(r"//[^\n]*", " ", text)
                chars |= set(re.findall(r"[\u3000-\u303f\u4e00-\u9fff\uff00-\uffef]", text))
    miss = sorted(c for c in chars if ord(c) not in cmap)
    check("字体覆盖所有中文", not miss, "缺字: %s" % "".join(miss[:20]) if miss else "%d 字形" % len(chars))
except Exception as e:
    check("字体覆盖检查", False, str(e))

# ---------- 5. 文档同步 ----------
for name, probes in {
    "使用手册.md": ["自动检视", "24:59:59", "DMF"],
    "README.md": ["3.9", "自动检视"],
}.items():
    p = os.path.join(APP, name)
    if os.path.exists(p):
        text = read(p)
        for probe in probes:
            check("%s 含「%s」" % (name, probe), probe in text)
    else:
        check("%s 存在" % name, False, "缺失")

# ---------- 输出 ----------
width = max(len(n) for n, _, _ in results) + 2
print("=" * (width + 46))
print("延时摄影 v3.9 自动验证")
print("=" * (width + 46))
fails = 0
for name, ok, detail in results:
    flag = "PASS" if ok else "FAIL"
    if not ok:
        fails += 1
    print("%-6s %-*s %s" % (flag, width, name, detail))
print("=" * (width + 46))
print("共 %d 项，通过 %d，失败 %d" % (len(results), len(results) - fails, fails))
sys.exit(1 if fails else 0)
