# Release Process / 发布流程

## Automated path / 自动流程

1. A functional change increments `build.gradle` version and updates `CHANGELOG.md` and, when applicable, `Kitloader.md`.
2. The configured post-commit hook pushes `main` to GitHub after a successful local commit.
3. GitHub Actions builds both official language packages on Java 21.
4. For a new version tag, Actions creates `v<version>` and publishes only `en.us.jar` and `zh.cn.jar` with English-first, Chinese-second release notes.
5. Servers check that official Release at startup and stage the matching verified package for the next restart.

1. 功能变更递增 `build.gradle` 版本，并更新 `CHANGELOG.md`；如影响管理员配置、限制或逻辑，还要更新 `Kitloader.md`。
2. 已配置的 post-commit Hook 在本地提交成功后推送 `main` 到 GitHub。
3. GitHub Actions 使用 Java 21 构建两个官方语言包。
4. 对新的版本标签，Actions 创建 `v<version>`，并只发布 `en.us.jar` 和 `zh.cn.jar`，Release Notes 先英文后中文。
5. 服务器启动时检测该官方 Release，并为下次重启暂存对应的已验证语言包。

## Release safeguards / 发布保护

- Build output is checked for exactly two JAR files.
- Release workflow does not upload a manually created source archive.
- `config.yml`, server data, plugin data, and local build folders are ignored by Git.
- The updater verifies the release asset's SHA-256 digest before staging it.

- 构建产物检查为恰好两个 JAR。
- Release 工作流不上传手工创建的源码压缩包。
- Git 忽略 `config.yml`、服务器数据、插件数据和本地构建目录。
- 更新器会先验证 Release 资源的 SHA-256 摘要，再暂存更新。
