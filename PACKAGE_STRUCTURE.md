## OnlySight（唯见）包结构设计

```
app/src/main/java/com/focusai/app/
│
├── FocusAiApplication.kt          # 全局依赖：Settings / Stats / ApiClient
├── MainActivity.kt                # 入口 Activity，Compose 宿主
│
├── data/
│   ├── api/
│   │   ├── ChatModels.kt          # OpenAI 请求/响应数据类
│   │   ├── OpenAiApi.kt           # Retrofit 接口
│   │   └── ApiClientFactory.kt    # 构建客户端 + analyzeScreenText()
│   ├── db/
│   │   ├── Entities.kt            # Room 实体
│   │   ├── StatsDao.kt            # DAO
│   │   └── AppDatabase.kt         # 数据库单例
│   ├── prefs/
│   │   ├── AppSettings.kt         # 配置模型 & 常量
│   │   └── SettingsRepository.kt  # DataStore 读写
│   └── StatsRepository.kt         # 统计业务封装
│
├── service/
│   └── FocusAccessibilityService.kt  # ★ 无障碍服务核心逻辑
│
├── viewmodel/
│   ├── FocusViewModel.kt          # 首页：监督开关 + 番茄钟
│   ├── StatsViewModel.kt          # 统计页
│   ├── SettingsViewModel.kt       # API 配置页
│   └── AboutViewModel.kt          # 关于 & 语言
│
├── ui/
│   ├── FocusApp.kt                # Scaffold + 底部导航 + NavHost
│   ├── focus/FocusScreen.kt
│   ├── stats/StatsScreen.kt
│   ├── settings/SettingsScreen.kt
│   ├── about/AboutScreen.kt
│   ├── navigation/MainTab.kt
│   └── theme/Theme.kt
│
└── util/
    ├── AccessibilityUtils.kt      # 检测/跳转无障碍设置
    ├── TextExtractor.kt           # DFS 抓取屏幕可见文字
    ├── NotificationHelper.kt      # 打断通知
    ├── ToastHelper.kt
    ├── LocaleHelper.kt            # 应用内语言切换
    └── TimeFormatter.kt
```

## 核心数据流

```
AccessibilityEvent
    → TextExtractor.extractVisibleText()
    → 防抖 3 秒
    → ApiClientFactory.analyzeScreenText()
    → 返回 1 → performGlobalAction(HOME) + Room 记录 + Toast/Notification
```

## 资源文件

- `res/values/strings.xml` — 中文（默认）
- `res/values-en/strings.xml` — 英文
- `res/xml/accessibility_service_config.xml` — 无障碍服务配置
