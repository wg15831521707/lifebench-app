import json, os, sys, urllib.request, urllib.error

# PAT 已从本文件移除：请在环境变量 LIFEBENCH_PAT 中提供后再运行，避免明文入库。
PAT = os.environ.get("LIFEBENCH_PAT", "")
if not PAT:
    print("请先设置环境变量 LIFEBENCH_PAT 再运行"); sys.exit(2)
OWNER = "wg15831521707"
REPO = "lifebench-app"
TAG = "v1.5.13"
APK = "app/build/outputs/apk/debug/app-debug.apk"
if not os.path.exists(APK):
    APK = "E:/WorkBuddy/WorkBunch/\u5b89\u5353\u9879\u76ee\u6e90\u7801/app/build/outputs/apk/debug/app-debug.apk"

BODY = ("v1.5.13 修复：抖音热榜视频墙点击卡片直接拉起抖音官方 App（私有 scheme snssdk1128://），"
        "不再落到系统浏览器网页版；仅当设备未安装抖音时才回退网页。\n\n"
        "安装包已用稳定签名密钥，覆盖安装不丢数据。")

def api(method, url, data=None, headers=None, is_upload=False):
    h = headers or {}
    h["Authorization"] = "Bearer " + PAT
    if is_upload:
        h["Content-Type"] = "application/vnd.android.package-archive"
    else:
        h["Accept"] = "application/vnd.github+json"
        if data is not None:
            h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.getcode(), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")

create_url = f"https://api.github.com/repos/{OWNER}/{REPO}/releases"
payload = json.dumps({"tag_name": TAG, "name": TAG, "body": BODY, "draft": False, "prerelease": False}).encode("utf-8")
code, resp = api("POST", create_url, data=payload)
print("create release:", code)
if code >= 400:
    print(resp); sys.exit(1)
rel = json.loads(resp); rel_id = rel["id"]; print("release id:", rel_id)
upload_url = f"https://uploads.github.com/repos/{OWNER}/{REPO}/releases/{rel_id}/assets?name=app-debug.apk"
with open(APK, "rb") as f:
    apk_bytes = f.read()
ucode, uresp = api("POST", upload_url, data=apk_bytes, is_upload=True)
print("upload asset:", ucode, uresp[:200])
print("DONE")
