#!/usr/bin/env python3
# 小满发版脚本模板（已脱敏）。用法：设环境变量 LIFEBENCH_PAT 后运行。
#   LIFEBENCH_PAT=ghp_xxx python3 release_v1514.py
import json, os, sys, urllib.request, urllib.error, urllib.parse

PAT = os.environ.get("LIFEBENCH_PAT", "")
if not PAT:
    print("请先设置环境变量 LIFEBENCH_PAT"); sys.exit(2)
OWNER = "wg15831521707"
REPO = "lifebench-app"
TAG = "v1.5.14"
APK = "app/build/outputs/apk/debug/app-debug.apk"
NAME = "xiaoman-v1.5.14-debug.apk"
base = f"https://api.github.com/repos/{OWNER}/{REPO}"
H = {"Authorization": f"Bearer {PAT}", "Accept": "application/vnd.github+json", "User-Agent": "lifebench-release"}

req = urllib.request.Request(f"{base}/releases/tags/{TAG}", headers=H)
rel = json.load(urllib.request.urlopen(req))
rid = rel["id"]
with open(APK, "rb") as f:
    data = f.read()
up = rel["upload_url"].split("{")[0]
enc = urllib.parse.quote(NAME)
req2 = urllib.request.Request(f"{up}?name={enc}&label={enc}", data=data,
    headers={**H, "Content-Type": "application/vnd.android.package-archive"}, method="POST")
r = json.load(urllib.request.urlopen(req2))
print("uploaded:", r.get("browser_download_url"))
