# OnlySight（唯见）

OnlySight（中文名：唯见）是一款极简 Android 自律应用。  
它基于 **MediaProjection 屏幕截图 + 多模态视觉大模型 (VLM) + AccessibilityService 回桌面**，对娱乐内容进行识别并打断，帮助你减少沉迷刷屏。

> 说明：当前包名与历史代码命名仍是 `com.focusai.app`（不影响使用）。

## 工作原理（视觉版）

1. 用户在首页授予「无障碍权限」并打开「监督开关」
2. 系统弹出「允许录屏」对话框，用户同意后启动前台服务
3. 前台服务每 **4 秒** 抓取一帧屏幕画面，缩放到最大边 512px、JPEG 压缩、Base64 编码
4. 把图片塞进多模态 Chat Completion 请求（默认豆包 `doubao-1-5-vision-pro-32k-250115`）
5. 模型只输出 `0`（专注）或 `1`（娱乐）
6. 收到 `1` → 通过无障碍服务 `performGlobalAction(GLOBAL_ACTION_HOME)` 把用户踢回桌面，并落库 + 通知

**全程不读取屏幕文字节点，不依赖任何 OCR/文本扫描。**

## 功能概览

- **AI 监督开关**：一键启停视觉监督前台服务
- **监督规则**：可自定义「专注目标」与「禁止内容标签」
- **高级提示词**：设置页可启用自定义模板，支持 `{focusGoal}` / `{forbiddenTags}` 占位符，**真正送入视觉模型**
- **打断统计**：今日打断次数 + 最近打断记录
- **多语言**：跟随系统 / 中文 / English
- **本地存储**：所有数据本地保存（BYOK，自带 API Key）

## 快速开始（开发者）

1. 用 **Android Studio** 打开本项目
2. 等待 Gradle Sync 完成
3. 连接 Android 手机（开启 USB 调试）并点击 **Run ▶**
4. App 内按顺序配置：**配置 API** → **授权无障碍** → **打开监督开关** → **同意录屏**

## 默认配置

| 项 | 默认值 |
|----|--------|
| Base URL | `https://ark.cn-beijing.volces.com/api/v3` |
| Model | `doubao-1-5-vision-pro-32k-250115` |
| 抓帧周期 | 4000 ms |
| 图像最大边 | 512 px |
| JPEG 质量 | 70 |

兼容 OpenAI 多模态协议的其它 VLM（如 OpenAI GPT-4o、Qwen-VL）也可直接换 Base URL/Model 使用。

## 项目结构

```
com.focusai.app/
├── MainActivity.kt
├── FocusAiApplication.kt
├── data/
│   ├── api/          # ChatModels（多模态）、Retrofit 工厂
│   ├── db/           # Room：InterceptionEntity + DAO
│   └── prefs/        # DataStore：API/规则/Prompt 模板
├── service/
│   ├── VisualSupervisionService.kt   # 核心：截屏循环 + VLM 调用
│   └── FocusAccessibilityService.kt  # 仅用于回桌面
├── ui/               # focus / stats / settings / about
├── util/             # 通知、无障碍工具、Prompt 渲染、时间格式化
└── viewmodel/
```

## 技术栈

- Kotlin 1.9 + Jetpack Compose
- MVVM + StateFlow + Coroutines
- Room + DataStore
- Retrofit + OkHttp
- MediaProjection + ImageReader + VirtualDisplay
- AccessibilityService（仅用于 `GLOBAL_ACTION_HOME`）

## 安全与隐私

- App 会将**屏幕画面 JPEG**发送到你配置的视觉模型服务端
- 请仅使用可信 API 服务商
- 不要在仓库提交 API Key、签名文件、`local.properties`

## 支持作者 / Support the author

如果觉得唯见对你有帮助，欢迎扫码请作者喝杯咖啡 ☕  
你的支持是持续维护的动力。

<p align="center">
  <img src="docs/donate_qr.png" alt="Deerp 的赞赏码" width="260" />
  <br/>
  <sub>Deerp 的赞赏码</sub>
</p>

## 许可证

本项目采用 [MIT License](./LICENSE)。

## 联系方式

QQ：3203581595@qq.com
