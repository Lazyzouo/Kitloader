# Contributing / 贡献指南

Contributions target `main`, must preserve Paper/Folia 1.21.11 and Java 21 compatibility, and must not add telemetry, external data collection, or a backdoor. Keep Chinese and English user-facing behavior aligned. Do not commit local runtime `config.yml`, plugin data, server directories, or build output.

贡献应提交到 `main`，必须保持 Paper/Folia 1.21.11 与 Java 21 兼容，且不得添加遥测、外部数据收集或后门。中英文用户可见行为应保持一致。不得提交本地运行时 `config.yml`、插件数据、服务器目录或构建产物。

Every functional change must increment the version in `build.gradle`, update `CHANGELOG.md`, and update `Kitloader.md` when administrators need to know about configuration, limits, or logic changes. Major changes increment the major version.

每次功能变更都必须递增 `build.gradle` 版本、更新 `CHANGELOG.md`，并在管理员需要了解配置、限制或逻辑变化时更新 `Kitloader.md`。重大变更递增主版本号。
