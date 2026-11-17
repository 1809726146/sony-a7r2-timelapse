"""用 dulwich 配置远程并按 main 分支推送"""
import os
import sys

sys.path.insert(0, r"D:\大\sony-cam-dev\tools\pylib")
from dulwich import porcelain
from dulwich.repo import Repo

ROOT = r"D:\大\sony-cam-dev"
REMOTE_NAME = "origin"
REMOTE_URL = "git@github.com:1809726146/-A7R2--APP-.git"
BRANCH = b"refs/heads/main"


def main():
    repo = Repo(ROOT)

    # 1) 配置 remote
    try:
        porcelain.set_remote_add(ROOT, REMOTE_NAME, REMOTE_URL)
        print("remote added:", REMOTE_URL)
    except Exception as e:
        print("remote exists or failed:", e)

    # 2) 当前分支改名为 main
    head = repo.head()
    print("head:", head.decode())
    repo.refs[b"HEAD"] = b"ref: " + BRANCH
    repo.refs[BRANCH] = head
    if b"refs/heads/master" in repo.refs:
        del repo.refs[b"refs/heads/master"]
    print("branch ->", BRANCH.decode())

    # 3) 推送
    print("pushing ...")
    try:
        result = porcelain.push(ROOT, REMOTE_URL, BRANCH.decode())
        print("push result:", result)
    except Exception as e:
        print("PUSH FAILED:", type(e).__name__, str(e)[:400])


if __name__ == "__main__":
    main()
