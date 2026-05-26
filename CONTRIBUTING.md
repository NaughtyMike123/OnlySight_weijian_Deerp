# Contributing to OnlySight（唯见）

感谢你愿意参与改进 OnlySight。

## 开始之前

- 确认你的修改不会提交敏感信息（API Key、签名文件、`.env`、`local.properties`）。
- 建议先开一个 Issue 讨论较大改动，避免重复工作。

## 本地开发

1. Fork 或 clone 项目
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 在真机上验证核心功能（无障碍、监督、统计、设置）

## 分支与提交建议

- 使用短分支名：`feat/...`、`fix/...`、`docs/...`
- 提交信息建议：
  - `feat: add xxx`
  - `fix: resolve xxx`
  - `docs: update xxx`

## Pull Request 要求

- 描述清楚改动动机与影响范围
- 附上必要截图（UI 变更）或日志（功能修复）
- 保持 PR 小而可审查，避免一次性混入大量无关改动
- 确保没有新增 lint 错误

## 行为准则

保持尊重、聚焦问题、友善沟通。
