"""Upload the packaged project to GitHub via the REST API (no git binary required).

Environment:
  GH_TOKEN       personal access token (repo scope)
  GH_REPO        repository name (default: a7r2-timelapse)
  GH_VISIBILITY  public | private   (default: public)
  GH_DESC        repository description
  GH_SRC         local folder to upload (default: dist/延时摄影-v1.1)
"""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN", "").strip()
REPO = os.environ.get("GH_REPO", "a7r2-timelapse").strip()
VIS = os.environ.get("GH_VISIBILITY", "public").strip()
DESC = os.environ.get("GH_DESC", "Sony A7R II (ILCE-7RM2) in-camera timelapse app")
SRC = os.environ.get("GH_SRC", r"D:\大\sony-cam-dev\dist\延时摄影-v1.1")

SKIP_DIRS = {".git", "__pycache__", "build", "obj", "gen"}


def call(method, path, payload=None, timeout=60):
    url = API + path
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
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
        raise RuntimeError("%s %s -> %s %s" % (method, path, e.code, text[:400]))


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
    print("user:", owner)

    # 1) 仓库：存在就用，不存在就建
    try:
        repo = call("GET", "/repos/%s/%s" % (owner, REPO))
        print("repo exists:", repo["html_url"])
    except RuntimeError as e:
        if "404" not in str(e):
            raise
        repo = call("POST", "/user/repos", {
            "name": REPO,
            "description": DESC,
            "private": VIS == "private",
            "has_issues": True,
            "has_wiki": False,
            "auto_init": True,
        })
        print("repo created:", repo["html_url"])
        time.sleep(2)

    # 2) 取默认分支
    branch = repo.get("default_branch") or "main"
    ref = call("GET", "/repos/%s/%s/git/ref/heads/%s" % (owner, REPO, branch))
    base_sha = ref["object"]["sha"]

    # 3) 上传 blob + 构建 tree
    files = collect(SRC)
    print("files:", len(files))
    tree = []
    for i, (rel, full) in enumerate(files, 1):
        with open(full, "rb") as f:
            content = f.read()
        blob = call("POST", "/repos/%s/%s/git/blobs" % (owner, REPO), {
            "content": base64.b64encode(content).decode("ascii"),
            "encoding": "base64",
        })
        tree.append({"path": rel, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        if i % 10 == 0 or i == len(files):
            print("  uploaded %d/%d" % (i, len(files)))

    new_tree = call("POST", "/repos/%s/%s/git/trees" % (owner, REPO), {
        "base_tree": ref["object"]["sha"],
        "tree": tree,
    })

    commit = call("POST", "/repos/%s/%s/git/commits" % (owner, REPO), {
        "message": "延时摄影 v1.1 — Sony A7R II 机内延时摄影 App",
        "tree": new_tree["sha"],
        "parents": [base_sha],
    })

    call("PATCH", "/repos/%s/%s/git/refs/heads/%s" % (owner, REPO, branch), {
        "sha": commit["sha"],
        "force": False,
    })

    print("done:", repo["html_url"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
