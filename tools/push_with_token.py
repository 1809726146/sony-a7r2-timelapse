"""通过 HTTPS + PAT 推送（GitHub 网页可访问时使用）

用法：
    set GITHUB_TOKEN=ghp_xxx        (PowerShell: $env:GITHUB_TOKEN="ghp_xxx")
    python tools/push_with_token.py

或用参数：
    python tools/push_with_token.py <用户名> <token>
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "pylib"))
from dulwich import porcelain

ROOT = r"D:\大\sony-cam-dev"
OWNER = "1809726146"
REPO = "-A7R2--APP-"
BRANCH = "main"


def main():
    user = OWNER
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if len(sys.argv) >= 3:
        user, token = sys.argv[1], sys.argv[2]
    if not token:
        print("缺少 token：设置环境变量 GITHUB_TOKEN 或作为第二个参数传入")
        return 2

    url = "https://%s:%s@github.com/%s/%s.git" % (user, token, OWNER, REPO)
    print("push ->", "https://github.com/%s/%s.git" % (OWNER, REPO))
    try:
        result = porcelain.push(ROOT, url, "refs/heads/%s" % BRANCH)
        print("result:", result)
        return 0
    except Exception as e:
        print("PUSH FAILED:", type(e).__name__, str(e)[:500])
        return 1


if __name__ == "__main__":
    sys.exit(main())
