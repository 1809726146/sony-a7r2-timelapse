"""Upload the packaged project to GitHub via REST API — handles empty repositories."""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN", "").strip()
REPO = os.environ.get("GH_REPO", "sony-a7r2-timelapse").strip()
SRC = os.environ.get("GH_SRC", r"D:\大\sony-cam-dev\dist\延时摄影-v1.1")
BRANCH = os.environ.get("GH_BRANCH", "main")

SKIP_DIRS = {".git", "__pycache__", "build", "obj", "gen"}


def call(method, path, payload=None, timeout=90, expect_404=False):
    url = API + path
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "token " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "dsh-upload")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        if expect_404 and e.code in (404, 409):
            return None
        raise RuntimeError("%s %s -> %s %s" % (method, path, e.code, text[:300]))


def collect(root):
    out = []
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            full = os.path.join(base, fn)
            rel = os.path.relpath(full, root).replace("\\", "/")
            out.append((rel, full))
    return sorted(out)


def main():
    if not TOKEN:
        print("GH_TOKEN missing")
        return 1

    me = call("GET", "/user")
    owner = me["login"]
    print("owner:", owner)

    repo = call("GET", "/repos/%s/%s" % (owner, REPO), expect_404=True)
    if repo is None:
        print("repo not found — create it on github.com first")
        return 2
    print("repo:", repo["html_url"], "empty:", repo.get("size") == 0)

    # 默认分支可能与本地不同
    branch = repo.get("default_branch") or BRANCH
    ref = call("GET", "/repos/%s/%s/git/ref/heads/%s" % (owner, REPO, branch), expect_404=True)
    base_sha = ref["object"]["sha"] if ref else None
    print("branch:", branch, "base:", base_sha)

    files = collect(SRC)
    print("files:", len(files))

    tree = []
    for i, (rel, full) in enumerate(files, 1):
        with open(full, "rb") as f:
            content = f.read()
        blob = None
        for attempt in range(3):
            try:
                blob = call("POST", "/repos/%s/%s/git/blobs" % (owner, REPO), {
                    "content": base64.b64encode(content).decode("ascii"),
                    "encoding": "base64",
                })
                break
            except RuntimeError as e:
                if attempt == 2:
                    raise
                time.sleep(2)
        tree.append({"path": rel, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        if i % 10 == 0 or i == len(files):
            print("  uploaded %d/%d (%s)" % (i, len(files), rel))

    tree_payload = {"tree": tree}
    if base_sha:
        tree_payload["base_tree"] = base_sha
    new_tree = call("POST", "/repos/%s/%s/git/trees" % (owner, REPO), tree_payload)

    commit_payload = {
        "message": "延时摄影 v1.1 — 索尼 A7R II 机内延时摄影 App\n\n"
                   "- 完整工程（源码/资源/构建脚本）\n"
                   "- 已签名 APK（versionCode 51 / versionName 1.1）\n"
                   "- 使用手册、设计稿、构建说明",
        "tree": new_tree["sha"],
    }
    if base_sha:
        commit_payload["parents"] = [base_sha]
    commit = call("POST", "/repos/%s/%s/git/commits" % (owner, REPO), commit_payload)

    if base_sha:
        call("PATCH", "/repos/%s/%s/git/refs/heads/%s" % (owner, REPO, branch), {
            "sha": commit["sha"], "force": False})
    else:
        call("POST", "/repos/%s/%s/git/refs" % (owner, REPO), {
            "ref": "refs/heads/" + branch, "sha": commit["sha"]})

    print("DONE:", repo["html_url"])
    print("commit:", commit["sha"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
