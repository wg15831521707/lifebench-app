#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
发布小满 v1.5.27 到 GitHub Release。
- 仓库：wg15831521707/lifebench-app
- 资产：app/build/outputs/apk/debug/app-debug.apk -> xiaoman-v1.5.27-debug.apk
- 同签名（SHA-256 b6d007...ae760），覆盖安装保留数据。
"""
import os
import sys
import json
import urllib.request
import urllib.error

REPO = "wg15831521707/lifebench-app"
TAG = "v1.5.27"
VERSION = "1.5.27"
APK_PATH = "app/build/outputs/apk/debug/app-debug.apk"
APK_NAME = "xiaoman-v1.5.27-debug.apk"

PAT = os.environ.get("GH_PAT")
if not PAT:
    print("ERROR: 需要环境变量 GH_PAT")
    sys.exit(1)

BASE = f"https://api.github.com/repos/{REPO}"
UPLOAD = f"https://uploads.github.com/repos/{REPO}/releases"

RELEASE_BODY = """## 小满 v1.5.27

### 移除抖音热榜模块
- 删除工具页 / 首页「全部工具」的「抖音热榜」入口、抖音视频墙页面、数据层、本地封面资源，以及阿里云函数计算 FC 代理源码与服务配置。
- 移除 `INTERNET` 权限与抖音相关 `<queries>` 包可见性声明，卸载 `accompanist-swiperefresh` 依赖。
- 应用回归纯本地、零网络依赖，更轻量、更省电。

> 同签名覆盖安装，数据不丢。

**下载（国内加速）**
https://ghproxy.net/https://github.com/wg15831521707/lifebench-app/releases/download/v1.5.27/xiaoman-v1.5.27-debug.apk
""".strip()

def api(method, url, data=None, is_upload=False):
    headers = {
        "Authorization": f"Bearer {PAT}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "xiaoman-release",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if is_upload:
        headers["Content-Type"] = "application/vnd.android.package-archive"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.getcode(), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        return e.code, body

# 1) 创建 Release（自动生成 tag）
code, body = api("POST", f"{BASE}/releases", data=json.dumps({
    "tag_name": TAG,
    "name": f"小满 v{VERSION}",
    "body": RELEASE_BODY,
    "draft": False,
    "prerelease": False,
}).encode("utf-8"))
print(f"create release -> {code}")
if code >= 400:
    print("BODY:", body)
    sys.exit(1)
rel = json.loads(body)
rel_id = rel["id"]
print(f"release id = {rel_id}")

# 2) 上传 APK 资产
with open(APK_PATH, "rb") as f:
    apk_bytes = f.read()
code, body = api(
    "POST",
    f"{UPLOAD}/{rel_id}/assets?name={APK_NAME}",
    data=apk_bytes,
    is_upload=True,
)
print(f"upload asset -> {code}")
if code >= 400:
    print("BODY:", body)
    sys.exit(1)
print("OK ->", json.loads(body).get("browser_download_url"))
