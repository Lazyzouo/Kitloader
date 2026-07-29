# Kitloader

> **Official project statement**
>
> Kitloader is a fully open-source project. It contains no backdoors, telemetry, or mechanism for collecting server data. All Kits, uploaded supplies, and configuration data created by the plugin remain on the server where the plugin is installed. Version checks and optional downloads only contact this project's GitHub Release endpoint to obtain a published Kitloader release. The sole official release channel is [Lazyzouo/Kitloader Releases](https://github.com/Lazyzouo/Kitloader/releases).

> **官方项目声明**
>
> Kitloader 是彻底开源的项目，不含后门、遥测或获取服务器数据的机制。插件创建的 Kit、上传补给和配置数据只保存在安装该插件的服务器上。版本检测及可选下载只会访问本项目的 GitHub Release 接口，以获取已发布的 Kitloader 版本。唯一官方发布渠道为 [Lazyzouo/Kitloader Releases](https://github.com/Lazyzouo/Kitloader/releases)。

Kitloader is a Paper/Folia Kit, supply-box, Ender Chest, and administrator inventory-management plugin. It is tested against **Paper and Folia 1.21.11** and requires **Java 21**.

Kitloader 是 Paper/Folia 的 Kit、补给盒、末影箱及管理员背包管理插件。基线测试版本为 **Paper 与 Folia 1.21.11**，需要 **Java 21**。

## Official Downloads / 官方下载

Download only from [official Releases](https://github.com/Lazyzouo/Kitloader/releases). Each Release deliberately provides exactly two installable assets:

| Asset | Default language | Use |
| --- | --- | --- |
| `Kitloader-<version>-en.us.jar` | English (`en_US`) | English server default package |
| `Kitloader-<version>-zh.cn.jar` | Simplified Chinese (`zh_CN`) | Simplified Chinese server default package |

GitHub may display automatically generated source snapshots. They are source archives, not plugin installation packages. The only supported plugin downloads are the two JAR files above.

只从[官方 Releases](https://github.com/Lazyzouo/Kitloader/releases)下载。每个 Release 只提供以下两个可安装资源：

| 资源 | 默认语言 | 用途 |
| --- | --- | --- |
| `Kitloader-<version>-en.us.jar` | English (`en_US`) | 英文默认包 |
| `Kitloader-<version>-zh.cn.jar` | Simplified Chinese (`zh_CN`) | 简体中文默认包 |

GitHub 可能显示自动生成的源码快照。它们只是源码压缩包，不是插件安装包；仅上表两个 JAR 为受支持的下载文件。

## Features / 功能

- Personal Kit save, load, rename, delete, list, limits, and single-use-world protection.
- Shared Kit upload and management with full-inventory, duplicate-content, and upload-count validation.
- Full 27-slot custom supply uploads with duplicate protection, all-one-type rejection, a 16-stack similar-item cap, visibility controls, time ordering, and instantly refreshed public pages.
- Dynamic Ender Chest interfaces, configurable unlocked slots, and dedicated supply delivery handling.
- `/inv` live inventory and Ender Chest editor; `/regear <player> list` online/offline uploaded-supply manager.
- Armor-trim, dye, item-name, and enchantment editing with conflict rate limiting plus ICUAC-compatible 399-character final-name enforcement and recursive legacy cleanup.
- Chinese and English runtime modes, automatic official update checks, SHA-256 verified staged downloads, and Folia-aware scheduling.

- 个人 Kit 保存、加载、重命名、删除、列表、数量限制及单次使用世界保护。
- 共享 Kit 上传与管理，具备满背包、相同内容和上传数量校验。
- 自定义潜影盒补给要求填满 27 格，拦截重复内容、整盒全同和同类超过 16 组，并支持公开/隐藏、按时间排序与公共页面即时刷新。
- 动态末影箱界面、可配置开放格数和补给直存处理。
- `/inv` 实时背包与末影箱编辑；`/regear <玩家> list` 在线/离线上传补给管理。
- 盔甲纹饰、染色、物品命名和附魔编辑，附带冲突提示防刷屏、ICUAC 兼容的 399 字符最终名称限制及旧数据递归清理。
- 中英文运行模式、官方更新检测、SHA-256 校验的暂存下载和 Folia 调度适配。

## Requirements / 运行要求

| Requirement | Supported baseline |
| --- | --- |
| Server software | Paper or Folia 1.21.11 |
| Java | Java 21 |
| Permissions | Bukkit/Paper permission system and OP where documented |
| Network for updates | Optional HTTPS access to `api.github.com` and GitHub release downloads |

| 要求 | 支持基线 |
| --- | --- |
| 服务端核心 | Paper 或 Folia 1.21.11 |
| Java | Java 21 |
| 权限 | Bukkit/Paper 权限系统，并在说明处使用 OP |
| 更新网络 | 可选，需要 HTTPS 访问 `api.github.com` 与 GitHub Release 下载地址 |

## Installation / 安装

1. Download `Kitloader-<version>-en.us.jar` or `Kitloader-<version>-zh.cn.jar` from the [official Releases page](https://github.com/Lazyzouo/Kitloader/releases).
2. Put exactly one language package in the server `plugins/` directory.
3. Start the server once. Kitloader creates its data folder and the selected official default `config.yml`.
4. Configure the generated `plugins/Kitloader/config.yml`, then run `/kitloader reload` or restart the server.

1. 从[官方 Releases 页面](https://github.com/Lazyzouo/Kitloader/releases)下载 `Kitloader-<version>-en.us.jar` 或 `Kitloader-<version>-zh.cn.jar`。
2. 只将一个语言包放进服务端 `plugins/` 目录。
3. 启动一次服务器，Kitloader 会创建数据目录与所选语言包对应的官方默认 `config.yml`。
4. 修改生成的 `plugins/Kitloader/config.yml`，然后执行 `/kitloader reload` 或重启服务器。

## Updates / 更新

The official presets enable `updates.enabled` and `updates.auto-download`. At startup, Kitloader checks the latest official Release. When a newer version exists, it downloads only the matching `Kitloader-<latest-version>-en.us.jar` or `Kitloader-<latest-version>-zh.cn.jar` into the Bukkit update folder, verifies the GitHub-provided SHA-256 digest, and installs it on the next restart. A failure never replaces the running JAR and prints the official Releases URL for manual download.

官方预设默认启用 `updates.enabled` 与 `updates.auto-download`。服务器启动时，Kitloader 会检查最新官方 Release。发现新版本后，只下载对应的 `Kitloader-<latest-version>-en.us.jar` 或 `Kitloader-<latest-version>-zh.cn.jar` 到 Bukkit 更新目录，校验 GitHub 提供的 SHA-256 摘要，并在下次重启安装。更新失败不会替换正在使用的 JAR，并会在后台提示官方 Releases 地址供服主手动下载。

## Configuration and Operations / 配置与运维

- [Administrator guide / 管理员说明](Kitloader.md)
- [Configuration reference / 配置参考](docs/CONFIGURATION.md)
- [Security policy / 安全政策](SECURITY.md)
- [Support / 支持](SUPPORT.md)
- [Change log / 更新日志](CHANGELOG.md)
- [Release process / 发布流程](docs/RELEASE_PROCESS.md)

## Development / 开发

The repository does not commit a personal runtime `src/main/resources/config.yml`. Official defaults are held in `presets/` and are used only while building the two release language packages. Runtime data, server folders, and local builds are ignored to prevent local server settings from being published accidentally.

仓库不会提交个人运行时的 `src/main/resources/config.yml`。官方预设放在 `presets/`，只用于构建两个语言包。运行数据、服务器目录和本地构建产物均被忽略，以避免本地服务器参数被意外公开。

## License / 许可证

MIT License. See [LICENSE](LICENSE).

MIT 许可证，详见 [LICENSE](LICENSE)。
