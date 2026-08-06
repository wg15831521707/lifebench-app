import json, os, sys, urllib.request, urllib.error, urllib.parse

# PAT 从环境变量读取，避免明文写入仓库
PAT = os.environ.get("GH_PAT", "")
if not PAT:
    print("请先设置环境变量 GH_PAT 再运行（PAT 不入库）")
    sys.exit(1)
OWNER, REPO = "wg15831521707", "lifebench-app"
TAG = "v1.5.24"
APK = "app/build/outputs/apk/debug/app-debug.apk"
NAME = "xiaoman-v1.5.24-debug.apk"
base = f"https://api.github.com/repos/{OWNER}/{REPO}"
H = {"Authorization": f"Bearer {PAT}", "Accept": "application/vnd.github+json",
     "Content-Type": "application/json", "User-Agent": "lifebench-release"}

req = urllib.request.Request(
    f"{base}/releases",
    data=json.dumps({
        "tag_name": TAG, "name": TAG,
        "body": ("小满 v1.5.24 — 抖音热榜实时更新（改用阿里云函数计算 FC 代理，默认域名 *.fcapp.run "
                 "国内可直连，彻底绕过 Cloudflare 的 DNS 污染；离线时自动回退本地缓存）。\n\n"
                 "- 热榜代理迁移至阿里云 FC，国内稳定实时拉取抖音热榜\n"
                 "- 下拉刷新 / 顶栏刷新按钮实时更新\n"
                 "- 时间戳行显示最近更新时间与条数\n"
                 "- 进页面静默刷新，网络失败回退缓存并 Toast 提示"),
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
