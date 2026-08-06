import json, os, sys, urllib.request, urllib.error, urllib.parse

# PAT 从环境变量读取，避免明文写入仓库
PAT = os.environ.get("GH_PAT", "")
if not PAT:
    print("请先设置环境变量 GH_PAT 再运行（PAT 不入库）")
    sys.exit(1)
OWNER, REPO = "wg15831521707", "lifebench-app"
TAG = "v1.5.16"
APK = "app/build/outputs/apk/debug/app-debug.apk"
NAME = "xiaoman-v1.5.16-debug.apk"
base = f"https://api.github.com/repos/{OWNER}/{REPO}"
H = {"Authorization": f"Bearer {PAT}", "Accept": "application/vnd.github+json",
     "Content-Type": "application/json", "User-Agent": "lifebench-release"}

req = urllib.request.Request(
    f"{base}/releases",
    data=json.dumps({
        "tag_name": TAG, "name": TAG,
        "body": "小满 v1.5.16 — 抖音热榜跳转修复（已装抖音必开 App，不再落网页）。",
        "draft": False, "prerelease": False,
    }).encode("utf-8"),
    headers=H, method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=60) as r:
        rel = json.load(r)
    rid = rel["id"]
    print("release created id=", rid)
except urllib.error.HTTPError as e:
    if e.code == 422:
        with urllib.request.urlopen(urllib.request.Request(f"{base}/releases/tags/{TAG}", headers=H)) as r:
            rel = json.load(r)
        rid = rel["id"]
        print("release already exists id=", rid)
    else:
        print("create release HTTPError", e.code, e.read().decode("utf-8", "replace"))
        sys.exit(1)

with open(APK, "rb") as f:
    data = f.read()
up = rel["upload_url"].split("{")[0]
enc = urllib.parse.quote(NAME)
req3 = urllib.request.Request(
    f"{up}?name={enc}&label={enc}", data=data,
    headers={**H, "Content-Type": "application/vnd.android.package-archive"}, method="POST",
)
with urllib.request.urlopen(req3, timeout=120) as r:
    a = json.load(r)
print("uploaded:", a.get("browser_download_url"))
