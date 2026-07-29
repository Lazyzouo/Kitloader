# Changelog

All notable changes are documented here. Versions follow `MAJOR.MINOR.PATCH`; every published functional update increments the version, and major changes increment `MAJOR`.

所有重要变更均记录于此。版本遵循 `MAJOR.MINOR.PATCH`；每次发布功能更新都必须递增版本，重大更新递增 `MAJOR`。

## [2.0.0] - 2026-07-29

### Added

- Official open-source repository foundation, policies, issue templates, and reproducible GitHub Actions Release automation.
- Official English and Simplified Chinese packages: `en.us.jar` and `zh.cn.jar`.
- English runtime localization layer while preserving the original source and Chinese configuration comments/messages.
- GitHub Release update checker with language-appropriate download selection, maximum-size protection, and SHA-256 digest verification.
- Prominent no-backdoor/no-telemetry data-handling statement and official-release declaration.
- Paper/Folia 1.21.11 and Java 21 baseline documentation.

### Changed

- Author metadata is now `Lazyz`.
- The build excludes personal `config.yml` and local `main.zip`; releases are built only from official presets.
- `/kitloader reload` now reloads language data as well as configuration and GUI data.
- English GUI category title parsing and supply-page refresh use canonical title matching.

### Fixed

- Personal Kit upload-limit feedback is now configured in both language packages.
- Edit sessions saved because of player death now use localized feedback.

## [2.0.0] - 2026-07-29（中文）

### 新增

- 官方开源仓库基础、政策、Issue 模板和可复现的 GitHub Actions 自动 Release 发布流程。
- 官方英文与简体中文包：`en.us.jar`、`zh.cn.jar`。
- 英文运行时本地化层，同时保留原始源码和中文配置注释/消息。
- GitHub Release 更新检测：按语言选择下载、限制文件大小并校验 SHA-256 摘要。
- 显著的无后门、无遥测、数据处理声明与官方发布声明。
- Paper/Folia 1.21.11 及 Java 21 基线说明。

### 变更

- 作者元数据更新为 `Lazyz`。
- 构建排除个人 `config.yml` 和本地 `main.zip`；Release 只使用官方预设构建。
- `/kitloader reload` 现在同时重载语言、配置和 GUI 数据。
- 英文 GUI 分类标题解析与补给页刷新采用规范化标题匹配。

### 修复

- 两个语言包均配置了个人 Kit 数量上限反馈。
- 玩家死亡导致的编辑暂存改为本地化反馈。
