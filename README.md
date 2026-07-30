# LifeBench 生活工作台

一个**纯本地、零广告、离线可用**的 Android 生活效率工作台。所有数据保存在本机（Room 数据库 + DataStore + 内部文件），不依赖任何云端账号。

> 说明：本项目按需求自研实现计时 / 计分 / 计算 / 交互逻辑，并非任何闭源 App 的逆向代码，可运行、可扩展、可二次开发。

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin 1.9.22 |
| UI | Jetpack Compose（BOM 2024.02.02 / Compose 1.6.2）+ Material 3 |
| 架构 | Repository 单例 + Compose 状态（无独立 ViewModel 层），源码逐行中文注释 |
| 本地存储 | Room 2.6.1（14 张表）+ DataStore 1.1.1 + 内部文件 |
| 导航 | Navigation 2.7.7（20 路由 / 底部 4 栏） |
| 其他 | Gson 2.10.1（备份导入导出）、Kotlin 协程 1.7.3、kapt |
| 构建 | Gradle 8.6 + Android Gradle Plugin 8.3.2 |

**版本与兼容**：`compileSdk 34` / `minSdk 26`（Android 8.0+）/ `targetSdk 34`；当前版本 `versionName 1.3.6`（`versionCode 10306`）；包名 `com.lifebench.app`。

## 更新日志

### v1.3.2 (2026-07-30, patch)
- **Bug 修复**
  - 舒尔特方格 8×8 数字颜色：调色板从「黑/绿/红/teal」（绿与 teal 色相过近）改为「黑/红/蓝/橙」（色相四向分散），并在 `reset()` 用防相邻同色分布（每格从「排除上一格颜色」的子集随机），8×8 等小格内也一眼能区分。
- **优化**
  - 舒尔特方格 SFX（新增 `app/src/main/java/com/lifebench/app/audio/SchulteAudio.kt`，SoundPool 驱动 4 路并发）：
    - **倒计时（3,2,1）**：3 声柔和钟铃（Soft bell countdown, mixkit.co, 0:03）
    - **开始**：3 声「go」蜂鸣（Sport start bleeps, mixkit.co, 0:03）
    - **点对**：清脆正向提示（Correct answer fast notification, mixkit.co, 0:01）
    - **点错**：低频错误蜂鸣（Game show wrong answer buzz, mixkit.co, 0:01）
    - **完成**：胜利叮（Quick win video game notification, mixkit.co, 0:01）
    - 资源来自 [mixkit.co](https://mixkit.co/license/#sfxFree)（CC0 自由使用，无需署名）；5 个 MP3 落入 `res/raw/`，总 ~800KB。

### v1.3.3 (2026-07-30, patch)
- **音效精简替换**
  - 移除原 5 个 mixkit 音效，改为 3 个用户指定音效（英文资源名，避免中文路径编码问题）：
    - **`schulte_start`**：开始提示音（开始声音.mp3）。
    - **`schulte_correct`**：点中下一个数字（正确音效.mp3）。
    - **`schulte_wrong`**：点错数字（错误提示音.wav）。
  - `SchulteAudio` 仅保留 `start()` / `correct()` / `wrong()` 三个方法；倒计时过程不再逐秒发声。

### v1.3.4 (2026-07-30, patch)
- **体验优化（舒尔特方格）**
  - 开始提示音 `schulte_start` 的播放时机从「点开始按钮那一刻」调整为「倒计时 1 结束、数字方格呈现的同一刻」，音效与棋盘出现严格同步，更符合"开始"语义。

### v1.3.5 (2026-07-30, patch)
- **睡眠页重构（显示 + 功能）**
  - **显示优化**：首屏「近一周平均睡眠」改为「达标率环形进度 + 平均时长」小结卡（占目标比例）；记录列表每条标注「记录日期（月日）+ 入睡→起床时刻」与「睡眠质量」色块徽标（差/中/好），解决跨午夜记混；建议文字按档位（偏少/波动/偏多/良好）配图标 + 语义背景高亮；折线图增加目标基准虚线。
  - **一键记录（核心）**：新增「😴 我睡觉啦 / 🌞 我起床啦」两个时间戳按钮，按入睡日去重自动拼成一晚一条记录，带已记/未记状态与进度反馈；新增「一键记昨晚（23:00→07:00）」快捷按钮。
  - **功能补全**：睡眠质量评分（差/中/好 三星，已建字段首次接入 UI），可点星标快捷评价；目标睡眠时长设定（DataStore，默认 8h）；本周 vs 上周平均对比卡；记录支持删除（长按星标按钮旁删除图标）。
  - **就寝提醒**：复用通知开关，新增就寝提醒时间设定，到点发通知（精确闹钟 + 开机恢复），可一键关闭。
  - 主页「睡眠概况」胶囊同步显示最近一次睡眠质量（如「7h30m · 好」）。

### v1.3.6 (2026-07-31, patch)
- **体验修复 + 白噪音升级**
  - 睡眠页「😴 我睡觉啦 / 🌞 我起床啦」两个按钮改为统一两行布局（图标 + 文案始终同一行，已记/待记录状态始终占位），两按钮文案严格对齐，不再因状态不同导致错位。
  - 修复「目标睡眠时长」「就寝提醒」显示值被本地默认覆盖的问题：改为直接读取 DataStore 实时值，修改后即时反映、重新进入页面也显示已保存值（不再回退到 8h0m / 22:30）。
  - 修复全局设置「月度消费预算」保存后重新打开仍显示默认 2000 的问题：输入框绑定到预算实时值（整数去小数显示）。
  - 主题色彩预设「蔷薇粉」调整为列表首位并设为默认主题色（新装及未手动改过主题的用户默认蔷薇粉）。
  - 全局设置「月度消费预算」去除括号标注（元），仅保留纯标题。
  - 番茄钟白噪音：移除旧版实时合成的雨声/森林/海浪/咖啡馆，改用真实音频文件 `res/raw/fireplace.mp3`（火炉白噪音），离线循环播放；播放器改为基于 MediaPlayer 的预设映射，后续新增白噪音只需放文件 + 加一条映射，零改播放逻辑。

### v1.3.1 (2026-07-30, patch)
- **Bug 修复**
  - 舒尔特方格 7×7 / 8×8 规格 chip 改用 `FlowRow` 自适应换行，修复窄屏被挤成 3 行的版式问题；规格范围 3×3 ~ 8×8。
  - 「提前结束 / 取消倒计时」不再写入成绩、也不更新「各规格最佳成绩」（之前会与自然完成一样入库）。
- **优化**
  - 舒尔特方格：开始前 3-2-1 倒计时（覆盖网格区域），方便玩家预热进入专注状态。
  - 网格 cell：白底 + 1dp 描边 + 8dp 圆角 + 大字，更清爽。
  - 数字多色（默认/绿/红/teal，限 4 色不杂乱），增强视觉训练辨识度。
  - 计时器改为 `mm:ss` 等宽格式 + teal 大字居中显示。

**版本号规则（语义化版本 SemVer）**：采用 `X.Y.Z` 三段式（X=主版本号，Y=次版本号，Z=修订号，均为非负整数且禁止前导零）。
- **主版本号 X**：重大更新（界面重设计 / 架构大幅调整 / 不兼容变更）时 `X+1`，同时 `Y=0`、`Z=0`（如 `0.8.7 → 1.0.0`）。
- **次版本号 Y**：向后兼容的新功能时 `Y+1`，`Z=0`。
- **修订号 Z**：向后兼容的缺陷修复时 `Z+1`。
- `versionCode` 由 `X*10000 + Y*100 + Z` 推导（如 `1.0.0 → 10000`），保证单调递增、可比较。

---

## 功能模块

底部四大主导航：**首页 / 专注 / 工具 / 我的**。

- **首页仪表盘**：年份日期 + 每日一语（中英）+ 待办四象限概览 + 今日专注 / 睡眠 / 饮食 / 打卡快照 + 枢纽快捷卡（专注空间 / 工具箱）+ 主题快捷切换。
- **专注空间（自我提升 hub）**：
  - 今日状态卡：今日专注时长、昨晚睡眠、今日饮食、今日打卡，点按进入对应子模块。
  - 番茄钟：前台服务常驻计时 + 白噪音离线合成（雨声 / 森林 / 海浪 / 咖啡馆）。
  - 睡眠：按日期记录与展示。
  - 饮食菜谱：今日饮食可改卡路里 / 可删。
  - 习惯打卡：连续打卡与统计。
- **工具箱（效率工具 hub）**：
  - 效率工具：待办（科维四象限，删除二次确认）、记账（自定义分类，去重 + 可删）、舒尔特方格（专注力训练）。
  - 安全与记录：密码箱（基于 Android Keystore 的 AES/GCM 加密存储）、笔记、纪念日倒计时。
  - 设置：6 套主题色彩（青 / 蓝 / 绿 / 橙 / 粉 / 紫）、浅 / 深 / 跟随系统切换、字体缩放。
- **个人中心**：数据概览 / 设置入口 / 数据备份（导出导入，SAF 自选位置）/ 关于。

通用能力：浅深主题持久化、自绘图表（饼图 / 柱状 / 折线）、精确闹钟与通知（开机自恢复）、数据备份导出导入。

---

## 环境要求

- **JDK 17**（构建脚本已配置 `jvmTarget = 17`）
- **Android SDK**（platform-34 + 构建工具）
- **Android Studio**（推荐）或命令行 Gradle

---

## 构建与运行

```bash
# 克隆
git clone https://github.com/wg15831521707/lifebench-app.git
cd lifebench-app

# Debug 构建（产物：app/build/outputs/apk/debug/app-debug.apk）
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug
```

**正式签名**：在本地 `gradle.properties` 配置签名信息（`KEYSTORE_PWD` / `KEY_PWD`，详见 `设计文档/06-APK签名打包教程.md`），再构建 release。当前 `release` 默认复用 debug 签名，上线前请在 `app/build.gradle.kts` 开启 `isMinifyEnabled` 并补全 ProGuard。

---

## 目录结构

```
安卓项目源码/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradlew / gradlew.bat            # Gradle 启动脚本（锁 8.6）
└── app/
    ├── build.gradle.kts / proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                      # values / drawable / mipmap
        └── java/com/lifebench/app/
            ├── MainActivity.kt           # 入口，挂载 Compose、套用全局主题
            ├── LifeBenchApplication.kt   # 初始化 Repo、建通知渠道
            ├── WhiteNoisePlayer.kt       # 白噪音离线合成
            ├── data/
            │   ├── AppDatabase.kt        # Room（14 表，单例）
            │   ├── entity/Entities.kt    # 全部实体
            │   ├── dao/Daos.kt           # 全部 DAO
            │   ├── RepositoryProvider.kt # Repo 单例
            │   ├── DataStore.kt          # 设置/主题持久化
            │   ├── WeatherDemo.kt        # 天气演示数据（离线占位）
            │   └── remote/               # 空目录占位
            ├── navigation/               # AppNav / BottomNav / Screen
            ├── ui/theme/                 # Theme/Color/Type/Shape/Dimension/Animation/ThemePresets
            ├── ui/components/            # Common（通用组件）/ Charts（自绘图表）
            ├── ui/screens/
            │   ├── home/HomeScreen.kt
            │   ├── tools/ToolsScreens.kt
            │   ├── brain/BrainScreens.kt
            │   └── profile/ProfileScreen.kt
            ├── util/                     # TimeUtil/CalcUtil/CryptoUtil/NotificationUtil/BackupUtil
            ├── service/FocusService.kt   # 番茄钟前台服务
            └── receiver/                 # AlarmReceiver / BootReceiver
```

---

## 文档

配套设计文档见仓库外 `设计文档/` 目录（或随交付包提供）：

- `00-项目总览与交付说明.md` —— 交付清单、工程特征、诚实声明
- `01-产品需求文档PRD.md` —— 产品定位与验收
- `06-APK签名打包教程.md` —— 签名与构建
- `07-新手使用说明书.md` / `08-源码自定义修改教程.md` —— 使用与二次开发

---

## 许可证

个人学习 / 非商业用途。如需上线发布，请自行完成多轮真机 QA 与合规审查。
