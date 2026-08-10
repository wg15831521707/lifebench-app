#!/usr/bin/env python3
# 小满 v1.5.32 发版脚本（资产名去除 -debug 字样，改为 -release.apk）
# 用法：GH_PAT=xxx python release_v1532.py
# 注意：token 仅从环境变量读取，绝不明文写入文件（避免触发 GitHub 密钥扫描）。
# 幂等：若 v1.5.32 Release 已存在，则删除旧 apk 资产后重新上传，不会因重复创建而 409。
import os
import json
import urllib.request
import urllib.error

TOK = os.environ.get("GH_PAT")
if not TOK:
    raise SystemExit("缺少环境变量 GH_PAT，请先设置后再运行。")

REPO = "wg15831521707/lifebench-app"
TAG = "v1.5.32"
NAME = f"xiaoman-{TAG}-release.apk"
APK = "app/build/outputs/apk/release/app-release.apk"
H = {
    "Authorization": f"Bearer {TOK}",
    "Accept": "application/vnd.github+json",
    "User-Agent": "lifebench-release",
    "X-GitHub-Api-Version": "2022-11-28",
}

BODY = """\
- 滑切月份不再抢整页滚动：在日历上滑动切月时，外层整页滚动被拦截，不再被带着翻页；松手后页面恢复正常滚动。
- 单月 + 上下滑动切月：固定只显示当前月，上滑看更早、下滑看更新月份（带箭头按钮），整卡约 1 屏。
- 方格边界清晰：每个日期格加发丝级边框，空格填充明显浅于背景，网格一目了然。
- 习惯图例 + 当天明细：底部常驻「习惯图例」（每习惯色块+emoji+名）；点击已打卡日弹窗列出当天各习惯。
"""

def req(method, url, data=None):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(url, data=body, headers=H, method=method)
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            return resp.read().decode(), resp.status
    except urllib.error.HTTPError as e:
        return e.read().decode(), e.code

# 1) 获取已存在的 Release（v1.5.32 应已存在），不存在则创建
raw, status = req("GET", f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}")
if status == 200:
    rel = json.loads(raw)
    rel_id = rel["id"]
    print("release exists:", TAG, "id=", rel_id)
    # 清理旧 apk 资产（如旧 xiaoman-v1.5.32-debug.apk），避免重复
    for a in rel.get("assets", []):
        if a["name"].startswith(f"xiaoman-{TAG}"):
            req("DELETE", f"https://api.github.com/repos/{REPO}/assets/{a['id']}")
            print("deleted old asset:", a["name"])
else:
    payload = {"tag_name": TAG, "name": TAG, "body": BODY, "draft": False, "prerelease": False}
    raw, _ = req("POST", f"https://api.github.com/repos/{REPO}/releases", payload)
    rel = json.loads(raw)
    rel_id = rel["id"]
    print("release created:", TAG, "id=", rel_id)

# 2) 上传资产（新文件名，去除 -debug 字样）
with open(APK, "rb") as f:
    asset = f.read()
r = urllib.request.Request(
    f"https://uploads.github.com/repos/{REPO}/releases/{rel_id}/assets?name={NAME}",
    data=asset,
    headers={**H, "Content-Type": "application/vnd.android.package-archive"},
    method="POST",
)
with urllib.request.urlopen(r, timeout=180) as resp:
    d = json.loads(resp.read())
    print("uploaded:", d.get("name"), d.get("size"), "->", d.get("browser_download_url"))
