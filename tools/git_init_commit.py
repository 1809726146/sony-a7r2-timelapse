"""用 dulwich 建本地仓库并提交（不依赖 git 可执行文件）"""
import os
import sys

sys.path.insert(0, r"D:\大\sony-cam-dev\tools\pylib")
from dulwich import porcelain
from dulwich.repo import Repo

ROOT = r"D:\大\sony-cam-dev"
EXCLUDE_DIRS = {"toolchain", "dist", "_archive", "build", "obj", "gen", "logs", "pylib", ".git"}
EXCLUDE_EXT = {".apk.idsig", ".log", ".bak", ".tmp"}
MAX_SIZE = 20 * 1024 * 1024      # 单文件 20MB 上限


def collect():
    files = []
    for base, dirs, names in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS and not d.startswith(".dsh-office-")]
        for n in names:
            p = os.path.join(base, n)
            rel = os.path.relpath(p, ROOT).replace("\\", "/")
            ext = os.path.splitext(n)[1].lower()
            if ext in EXCLUDE_EXT:
                continue
            try:
                if os.path.getsize(p) > MAX_SIZE:
                    continue
            except OSError:
                continue
            files.append(rel)
    return sorted(files)


def main():
    repo_path = os.path.join(ROOT, ".git")
    if not os.path.isdir(repo_path):
        porcelain.init(ROOT)
        print("init repo")

    files = collect()
    porcelain.add(ROOT, paths=files)
    print("staged files:", len(files))

    msg = """v3.9: 延时摄影 App（ILCE-7RM2）

- 四选项卡菜单：拍摄 / 设置 / 延时设置 / 预设
- 方向键只选择、转盘改值、确认键执行
- 间隔 5 秒–24:59:59，张数 1–9999（快转 ±10）
- 预设文件 1–4，文件内编辑间隔/张数/使用场景，C2 循环切换
- ISO AUTO 显示并可调上下限
- 设置锁定、夜拍调光、C1 日志快照、卡余张数
- 中文：优先相机 GB18030 字体，内置 Noto 子集兜底
"""
    author = b"Shenwenjun <shenwenjun@users.noreply.github.com>"
    try:
        porcelain.commit(ROOT, message=msg.encode("utf-8"), author=author, committer=author)
    except Exception as e:
        print("commit failed:", e)
        raise

    repo = Repo(ROOT)
    head = repo.head()
    n = sum(1 for _ in repo.get_walker())
    print("commit:", head.decode(), "objects:", n)


if __name__ == "__main__":
    main()
