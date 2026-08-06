"""release_v1526.py — 发布 v1.5.26（诊断日志增强版）到 GitHub Release"""
import hashlib
import json
import os
import sys
import urllib.request

REPO = "wg15831521707/lifebench-app"
TAG = "v1.5.26"
VERSION = "1.5.26"
TITLE = f"v{VERSION} — 抖音热榜刷新诊断增强"
APK_PATH = os.path.join(os.path.dirname(__file__), "app/build/outputs/apk/debug/app-debug.apk")
NOTES = """## v1.5.26 — 抖音热榜刷新诊断增强

### 变更
- **DouyinRepository fetchRemote() 全链路 Log.d 日志**（TAG=`DouyinRepo`）：
  - 请求开始 → 连接成功 → HTTP 响应码 → 响应体长度/前100字
  - Gson 解析条数 + 前3条 label 原始类型（Double? String? null?）
  - 归一化完成日志
  - 失败时 Log.e 打印完整异常类名+消息+堆栈
- 超时从 10s 提升到 12s（容许弱网）

### 排查指引
装上此版本后，用 USB 连接电脑，运行：
```
adb logcat -s DouyinRepo:* *:S
```
然后进入抖音热榜页面点 🔄 刷新，观察 logcat 输出：
- 若看到 `→ 归一化完成，返回 N 条` → 刷新**成功了**，数据已更新
- 若卡在 `→ 已连接，等待响应码...` 后无输出 → **网络超时/DNS 解析失败**
- 若出现 `✗ 请求失败: xxx` → 具体错误原因会显示
- 若完全无任何 `DouyinRepo` 日志 → **refresh() 未被调用**（APK 版本问题？）

### 诊断后
把 logcat 输出发给我，我就能精确定位是网络问题、DNS 污染、还是代码 bug。
"""

PAT = os.environ.get("GH_PAT", "")
if not PAT:
    # 尝试从本地配置读取
    try:
        with open(os.path.expanduser("~/.gh_pat"), "r") as f:
            PAT = f.read().strip()
    except FileNotFoundError:
        pass

if not PAT:
    print("ERROR: 需要 GH_PAT 环境变量或 ~/.gh_pat 文件")
    sys.exit(1)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def api(method, path, data=None, base="api.github.com"):
    url = f"https://{base}{path}"
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Authorization", f"token {PAT}")
    req.add_header("Accept", "application/vnd.github.v3+json")
    if body:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        print(f"HTTP {e.code} {method} {path}: {err[:300]}")
        raise


def main():
    if not os.path.exists(APK_PATH):
        print(f"APK 不存在: {APK_PATH}")
        sys.exit(1)

    apk_sha = sha256_file(APK_PATH)
    apk_size = os.path.getsize(APK_PATH)
    apk_name = f"xiaoman-v{VERSION}-debug.apk"
    print(f"APK: {apk_name} ({apk_size // 1024}KB) SHA256={apk_sha[:16]}...")

    # 1) Create Release
    release = api("POST", f"/repos/{REPO}/releases", {
        "tag_name": TAG,
        "name": TITLE,
        "body": NOTES,
        "draft": False,
        "prerelease": False,
    })
    release_id = release["id"]
    upload_url = release["upload_url"].replace("{?name,label}", "")
    print(f"Release #{release_id} created")

    # 2) Upload APK
    with open(APK_PATH, "rb") as f:
        apk_data = f.read()
    url = f"{upload_url}?name={apk_name}"
    req = urllib.request.Request(url, data=apk_data, method="POST")
    req.add_header("Authorization", f"token {PAT}")
    req.add_header("Content-Type", "application/vnd.android.package-archive")
    with urllib.request.urlopen(req, timeout=120) as resp:
        asset = json.loads(resp.read())
    print(f"Asset uploaded: {asset['name']} ({asset['size'] // 1024}KB)")

    print(f"\nDone! {release['html_url']}")


if __name__ == "__main__":
    main()
