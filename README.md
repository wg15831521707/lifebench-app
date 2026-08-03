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

**版本与兼容**：`compileSdk 34` / `minSdk 26`（Android 8.0+）/ `targetSdk 34`；当前版本 `versionName 1.5.5`（`versionCode 10505`）；包名 `com.lifebench.app`。

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

### v1.3.7 (2026-07-31, patch)
- **首页工作台精细打磨 + Bug 修复**
  - **Bug 修复**
    - 睡眠概况胶囊与今日专注胶囊高度不齐：根因 `MetricLine` 的 value 文本大字号下换行把睡眠盒撑高；改为 value 单行省略 + 新增 `trailing` 槽位承载「睡眠质量」徽章（好=绿/中=橙/差=红），两胶囊外层 `Row(IntrinsicSize.Min)` + `fillMaxHeight()` 强制等高。
    - 首页「专注空间 / 工具箱」快捷入口跳转后，底部导航「首页」无法再点击跳转：根因 `HubShortcut` 用普通 `nav.navigate` 推栈与底部导航的 `popUpTo+saveState+launchSingleTop+restoreState` 模式不一致；统一为相同导航选项。
    - 从专注 / 记账页返回首页后数据陈旧：改 `LaunchedEffect(Unit)` 一次性加载为订阅 Room Flow（`focusMinutesBetweenFlow` / `sumByTypeFlow`），源数据变化自动刷新。
  - **精细打磨**
    - 「全部工具」网格由 4 列改为 3 列，图标 48→56dp 并加 2dp 投影，间距加大，更精致易点。
    - 待办四象限空格新增引导：「暂无任务」下加「＋ 点此添加任务」强调色提示，点击整格直达待办页。
    - 四象限「暂无任务」字色透明度 0.5→0.72，提升对比度可读性。

### v1.4.0 (2026-08-03, minor)
- **界面层级与质感重构（设计系统落地）**
  - **统一卡片分层**：`AppCard` 新增 `CardVariant`（SURFACE 发丝边轻投影 / ACCENT 主色容器 + 主色柔化阴影），用表面权重表达页面重心，告别全站平铺。
  - **动效系统组件化**：新增 `RingProgress`（环形进度缓动生长 + 圆心内容槽）、动画版 `PieChart`（环形扫入 + 分类间隙）、`BarChart`（逐根错峰生长）；新增 `BudgetProgress`（预算进度 + 超预算切换 error 色）；新增 `CountUpText`（数字滚动）、`Modifier.reveal`（错峰淡入上移）、`PulseBadge`（超预算呼吸徽标）。
  - **记账页（收支记账）接入**：收入/支出 KPI 改为 count-up 数字；预算卡升级为 ACCENT 重心卡，超预算时进度条变红并附脉冲「已超支」徽标；饼图/柱状切换均带生长动画；各卡片错峰入场。
  - **睡眠页联动**：`RingProgress` 升级为共享动画组件，睡眠达标率环形同样获得缓动生长。

### v1.5.0 (2026-08-03, minor)
- **页面层级与质感重构（落地 lifebench-redesign.html 方案）**
  - **设计系统组件化（新增 `ui/components/DesignSystem.kt`）**：提炼品牌渐变 `brandBrush()`（靠 `primary.compositeOver` 推导明暗、`不写死玫瑰粉`，跨主题预设通用）、`HeroCard`（首页渐变 Hero：问候 + 日期 + 头像 + 当日专注环形进度）、`SectionHeader`（4dp 主色竖条 + 标题 + 可选「查看 ›」，统一全站分组）、`ProfileHeader`（渐变圆形头像 + 昵称 + 累计坚持天数）、`StatBadge`（4 列网格统计卡）、`GradientPanel`（渐变强调面板）。
  - **首页工作台**：顶部替换为渐变 `HeroCard`（问候随时段变化「早上好/下午好/晚上好 王浩」+ 当日专注环），各区块统一改用 `SectionHeader`，卡片错峰 `reveal()` 入场（每日一语 → 专注/睡眠 → 本周支出 → 习惯 → 连续打卡），建立清晰的视觉层级与质感。
  - **个人中心**：新增 `ProfileHeader` 头部（渐变头像 + 「已坚持 X 天」），数据概览改 4 列 `StatBadge` 网格，版本号更新为 v1.5.0。
  - **专注页 hub**：「今日状态」前插入 `GradientPanel` 汇总今日专注（大数字 + 目标），与首页 Hero 形成呼应，强化品牌色面板权重。

### v1.5.1 (2026-08-03, patch)
- **三处体验问题修复（基于 v1.5.0 实际使用反馈）**
  - **睡眠页「手动记录」日期错位 bug（29 小时）**：原先只暴露 TimePicker，用户改时间却改不了日期，导致「今早 02:55 → 07:55」被误记为「昨天 02:55 → 今 07:55」显示 29h。新增 `SleepDateTimeRow` 双按钮（日期+时间独立可改），并在表单初始化时智能归位——若入睡与起床同日且入睡 > 起床，自动把入睡日回拨到昨天，避免日期错位。
  - **取消睡眠页「一键记昨晚（23:00→07:00）」**：固定 23→07 的硬编码快捷在用户实际睡眠时间灵活时反而误导，改为完全由用户手动设定入睡/起床的「日期+时间」。
  - **首页待办四象限空态去重**：原空态同时显示「暂无任务」+「+ 点此添加任务」图标 + 底部「立即处理/安排时间…」hint，三行信息重复。简化为单行「暂无任务」，整格可点击进入待办页完成添加。
  - **收支记账页柱状图形态重做**：原先 `BarChart` 单一品牌色，3 类支出同色、无法辨别。参照 `lifebench-expense-detail.html` 新增 `CategorizedBarChart`（`ui/components/Charts.kt`），每根柱子按 `ChartPalette` 取色（翡翠绿/琥珀/蓝/紫/珊瑚/青绿/石板灰），柱顶显示金额、柱底显示分类名，错峰生长动画；图例用同色圆点对齐；空数据时给出降级提示。饼图形态保持不变。

### v1.5.2 (2026-08-03, patch)
- **六处 UI 精细化（基于 v1.5.1 实际使用反馈）**
  - **睡眠「近一周平均睡眠达标率」环修正为正圆**：`Box(Modifier.size(72.dp).weight(0.4f))` 中 `size` + `weight` 在 Row 里互相覆盖导致宽>高 → 椭圆环。改为 `Modifier.weight(0.4f).aspectRatio(1f)` 强制 1:1。
  - **睡眠「手动记录」日期+时间按钮骨架瘦身**：`OutlinedButton` 加 `heightIn(max=40.dp)` + 紧凑 `contentPadding` + `maxLines=1/softWrap=false`，时间「02:42/07:45」不再被截断换行；外层 `Row` 加 `height(IntrinsicSize.Min)` 让日期与时间按钮同高。
  - **睡眠「目标 & 就寝提醒」按钮上下对齐**：就寝提醒行因含「关闭」TextButton（默认高 48dp）让整行撑高，「未设置」按钮相对「8h0m」下移。统一 `Row.height(48.dp)` + `Button.shape = RoundedCornerShape(20.dp)` + 「关闭」改高 32dp 紧凑 TextButton，两行垂直对齐。
  - **首页睡眠胶囊修复「5h...」截断 + 增强显示**：把「差/中/好」质量 chip 从 `MetricLine.trailing` 槽位移到 value 下方独立一行，附「近一晚质量」小字说明，value 文本不再被挤压显示省略号。
  - **收支记账页顶部双卡等高 + 卡片内部按内容自适应**：`Row.height(IntrinsicSize.Min)` + 两卡 `fillMaxHeight()` 强制「本月收入 ¥0.00」与「本月支出 ¥149.00」不同文字长度也保持高度一致，避免骨架变形。
  - **收支结构图例改彩色 PillChip**：饼图/柱状图图例统一改为「分类名 + 金额」写在主题色背景上、白字加粗的胶囊（参照 `lifebench-expense-detail.html` 视觉），取代原先「色点 + 灰字」两行结构。
  - **「＋ 记一笔」上移至月度预算下方**：从列表底部移到月度预算之后、收支结构之前，记账流程更顺手（看预算 → 立即记）。

### v1.5.3 (2026-08-03, patch)
- **模板对齐精细化（参照 lifebench-redesign.html）**
  - **首页「今日概览」补全区块标题**：今日专注 / 睡眠概况 / 本周支出 三张 KPI 胶囊归入统一「今日概览」SectionHeader，分组更清晰，与模板一致。
  - **首页「习惯打卡」整合为单张连续打卡卡**：原「今日打卡 / 最长连续」双胶囊 + 进度卡三段合并为一张「连续打卡」卡（左：最长连续大数字 + 右：今日打卡 / 里程碑 mini + 进度条 + 解锁提示），对应模板 `.streak` 布局，信息密度更合理、视觉更聚焦。
  - **个人中心「设置」卡补齐「导出全部备份」行**：与模板一致，设置卡内「全局设置」下新增「导出全部备份」快捷入口（触发系统文件选择器），保留下方独立「数据备份与恢复」卡。
  - 全程保持 v1.5.1 / v1.5.2 已修复项不回退：睡眠达标率环为正圆、手动记录日期+时间同行、目标/提醒按钮对齐、首页睡眠胶囊不截断、记账页卡片对称 + 彩色图例 pill + 记一笔在预算下；待办四象限空态仍为「暂无任务」，**未恢复**「＋ 点此添加任务」。

### v1.5.5 (2026-08-03, patch)
- **工作台更贴近模板 + 长页卡顿修复**
  - **移除首页两大枢纽卡**：删去「全部工具」与「待办四象限」之间的「专注空间 / 工具箱」两个 `HubShortcut` 入口卡，工作台结构更贴近模板（专注/工具页与底部导航已能覆盖这些入口）。
  - **工作台卡顿修复（内容增多后掉帧）**：根因是首页为单个 `Column(verticalScroll)` 非虚拟化长滚动容器，且整页 body 内联，任一 flow 变更即重排整页。改为 `LazyColumn`（仅合成/测量可视区），并把各区块（每日一语 / 今日概览 / 连续打卡 / 四象限 / 已完成 / 全部工具）抽成独立形参化 Composable，使数据变更只重排受影响区块；「已完成」列表与「全部工具」双列均虚拟化为子项。习惯连续打卡派生量用 `remember(habits, allCheckIns)` 缓存，避免无关 flow 触发时重算。视觉与间距与旧版保持一致。
  - 全程保持此前已修复项不回退；待办四象限空态仍为「暂无任务」，**未恢复**「＋ 点此添加任务」、「一键记昨晚」。

### v1.5.4 (2026-08-03, patch)
- **底部导航栏重构（参照模板 `.nav`）+ 首页「全部工具」统一（参照模板 `.tile`）**
  - **底部导航栏**：由 Material3 默认 `NavigationBar`（仅变色、无指示器）改为自定义 `BottomNavigationBar` —— 半透明磨砂表面 + 顶部发丝描边、固定 72dp 高、避让系统手势条；选中项显示「顶部品牌色小药丸」指示器（30×3dp）、图标轻微上移 1dp、文字转品牌色；首项标签由「首页」改为「工作台」与模板一致。每项 `selectable + Role.Tab` 保证键盘/读屏可达。
  - **首页「全部工具」统一为双列 ToolTile**：原 3 列图标网格（且 `heightIn(max=360)` 会导致 9 个工具被裁剪/内层滚动）改为与专注/工具页一致的双列 `ToolTile` 卡片（图标芯片 + 标题 + 一句说明 + 右箭头）；将 `ToolMeta`/`ToolTile` 抽到 `Common.kt` 共用，三处工具入口视觉一致。9 个工具全部可达，改用普通 Column 排版，消除嵌套滚动裁剪问题。
  - 全程保持此前已修复项不回退；待办四象限空态仍为「暂无任务」，**未恢复**「＋ 点此添加任务」、「一键记昨晚」。

### v1.3.8 (2026-07-31, patch)
- **构建与数据持久化加固（为应用内自动更新铺路）**
  - **统一稳定签名密钥**：新增正式签名 keystore（alias=lifebench，PKCS12，RSA 2048，有效期 10000 天），`debug` / `release` 构建均使用同一把密钥签名，跨本机与 CI 构建签名一致。覆盖安装自动保留 Room 数据库与 DataStore 数据，根治 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
  - **CI 注入密钥**：GitHub Actions 从仓库 Secrets（`SIGNING_KEY` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD` / `KEY_ALIAS`）解码密钥库后构建，产物名仍为 `app-debug.apk`，下载页与发版流程无需改动。
  - **Room 迁移策略加固**：`AppDatabase` `exportSchema` 改为 `true` 并写明迁移约定——数据库版本号每次必 +1、每跨一版必须新增 `Migration` 并在 `addMigrations()` 注册、**严禁使用 `fallbackToDestructiveMigration`**（否则会清库），schema 导出至 `app/schemas/` 便于校验。
  - **升级须知（一次性）**：因签名密钥由调试密钥切换为正式密钥，本次升级需先**卸载旧包再重装**（该次本地数据会清空）；重装后后续所有覆盖更新均自动保留数据，无需再卸载。

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
