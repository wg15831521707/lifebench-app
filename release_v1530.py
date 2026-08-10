#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""小满 v1.5.30 发版：新建 GitHub Release + 上传 release 签名 APK。

- 复用稳定签名密钥（证书 SHA-256 b6d007...ae760），覆盖安装保留数据。
- 新建 tag v1.5.30 的 Release，资产名 xiaoman-v1.5.30-debug.apk（与加速页静态文案一致）。
- 加速页 JS 会实时拉取 latest Release，发版后线上页面自动切到 v1.5.30。
"""
import os
import sys
import json
import urllib.request
import urllib.error

TOK = os.environ.get("GH_PAT")
if not TOK:
    print("GH_PAT 未设置：请先 export GH_PAT=<your_token> 再运行本脚本")
    sys.exit(1)
REPO = "wg15831521707/lifebench-app"
APK = "app/build/outputs/apk/release/app-release.apk"
NAME = "xiaoman-v1.5.30-debug.apk"
TAG = "v1.5.30"
REL_NAME = "小满 v1.5.30"
BODY = """### 习惯热力图改折叠手风琴
- 当前月完整展开，历史月份折叠为单行摘要（「打卡 X/Y 天」+ 迷你热度条），点一下原地展开。
- 首屏更短更省事：进入习惯页一眼可见本月 + 全部历史月份摘要，整体高度从约 6 屏压缩到约 1 屏，滚动效率大幅提升。
- 年份/本月标签保留：月头仍显示「2026年8月」并标注「本月」，纯竖向滚动不变。
- 沿用既有体验：保留 heatColor 分级配色、今日高亮边框与点击「日期 + 次数」提示；展开/收起带平滑动画。
"""

H = {
    "Authorization": f"Bearer {TOK}",
    "Accept": "application/vnd.github+json",
    "User-Agent": "lifebench-release",
    "X-GitHub-Api-Version": "2022-11-28",
}


def api(method, url, data=None, binary=None, ctype=None):
    if binary is not None:
        req = urllib.request.Request(url, data=binary, method=method, headers={**H, "Content-Type": ctype})
    elif data is not None:
        payload = json.dumps(data).encode("utf-8")
        req = urllib.request.Request(url, data=payload, method=method, headers={**H, "Content-Type": "application/json"})
    else:
        req = urllib.request.Request(url, method=method, headers=H)
    with urllib.request.urlopen(req, timeout=120) as r:
        raw = r.read()
        return r.status, (json.loads(raw) if raw else {})


def main():
    if not os.path.isfile(APK):
        print("APK NOT FOUND:", APK)
        sys.exit(1)

    # 1) 新建 Release
    st, rel = api("POST", f"https://api.github.com/repos/{REPO}/releases", data={
        "tag_name": TAG,
        "name": REL_NAME,
        "body": BODY,
        "draft": False,
        "prerelease": False,
    })
    if st < 200 or st >= 300:
        print("CREATE RELEASE FAILED", st, rel)
        sys.exit(1)
    rel_id = rel["id"]
    print("release created ->", rel_id, rel.get("html_url"))

    # 2) 上传资产（同名便于加速页直链稳定）
    size = os.path.getsize(APK)
    with open(APK, "rb") as f:
        blob = f.read()
    st, asset = api(
        "POST",
        f"https://uploads.github.com/repos/{REPO}/releases/{rel_id}/assets?name={NAME}",
        binary=blob,
        ctype="application/vnd.android.package-archive",
    )
    if st < 200 or st >= 300:
        print("UPLOAD FAILED", st, asset)
        sys.exit(1)
    print("upload ->", st, asset.get("name"), asset.get("size"), asset.get("browser_download_url"))


if __name__ == "__main__":
    main()
