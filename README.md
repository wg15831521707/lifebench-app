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

**版本与兼容**：`compileSdk 34` / `minSdk 26`（Android 8.0+）/ `targetSdk 34`；当前版本 `versionName 1.0.0`（`versionCode 10000`）；包名 `com.lifebench.app`。

**版本号规则（语义化版本 SemVer）**：采用 `X.Y.Z` 三段式（X=主版本号，Y=次版本号，Z=修订号，均为非负整数且禁止前导零）。
- **主版本号 X**：重大更新（界面重设计 / 架构大幅调整 / 不兼容变更）时 `X+1`，同时 `Y=0`、`Z=0`（如 `0.8.7 → 1.0.0`）。
- **次版本号 Y**：向后兼容的新功能时 `Y+1`，`Z=0`。
- **修订号 Z**：向后兼容的缺陷修复时 `Z+1`。
- `versionCode` 由 `X*10000 + Y*100 + Z` 推导（如 `1.0.0 → 10000`），保证单调递增、可比较。

---

## 功能模块（v1.0.0）

底部四大主导航：**首页 / 工具 / 健身 / 我的**。

- **首页仪表盘**：年份日期 + 每日一语（中英）+ 待办四象限概览 + 已完成恢复 + 主题快捷切换。
- **工具枢纽**：
  - 待办：科维四象限（重要紧急 / 重要不紧急 / 紧急不重要 / 不重要不紧急），删除二次确认。
  - 密码箱：基于 Android Keystore 的 AES/GCM 加密存储。
  - 笔记、纪念日。
  - 设置：6 套主题色彩（青 / 蓝 / 绿 / 橙 / 粉 / 紫）、浅 / 深 / 跟随系统切换、字体缩放。
- **健身饮食枢纽**：
  - 番茄钟：前台服务常驻计时 + 白噪音离线合成（雨声 / 森林 / 海浪 / 咖啡馆）。
  - 睡眠：按日期记录与展示。
  - 记账：自定义分类（去重 + 可删）。
  - 饮食菜谱：今日饮食可改卡路里 / 可删。
  - 健身：双轨（记录 `date>0` / 模板 `date=0`）。
- **脑力训练**：舒尔特方格（当前唯一保留的训练模块）。
- **个人中心**：概览 / 设置入口 / 数据备份（导出导入，SAF 自选位置）/ 关于。

通用能力：浅深主题持久化、自绘图表（饼图 / 柱状 / 折线）、精确闹钟与通知（开机自恢复）、数据备份导出导入。

> 历史上曾包含步数、天气（联网）、速读 RSVP 等模块，已在早期版本迭代中移除，当前 v1.0.0 代码不含这些功能。

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
            │   ├── fit/FitScreens.kt
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
