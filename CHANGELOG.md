# Changelog

All notable changes are documented here. Versions follow `MAJOR.MINOR.PATCH`; every published functional update increments the version, and major changes increment `MAJOR`.

所有重要变更均记录于此。版本遵循 `MAJOR.MINOR.PATCH`；每次发布功能更新都必须递增版本，重大更新递增 `MAJOR`。

## [2.0.2] - 2026-07-29

### Added

- Added hot-reloadable visible Unicode name limits: 40 characters for custom item names and 18 for personal/shared Kit and uploaded-supply names by official default.
- Added hot-reloadable uploaded-supply content policy options for required occupied slots, all-same rejection, and maximum similar occupied stacks.
- Naming prompts and rejection messages now report the active limits and count characters after Minecraft color/format codes are removed.

### Changed

- Autosave now creates increasing snapshots (`autosave-1`, `autosave-2`, ...), skips unchanged content, respects `max-kits`, and rotates only the oldest autosave instead of overwriting one fixed Kit.
- Startup, load, and `/kitloader reload` revalidate existing supply data against the active policy. Existing over-limit supply names are reset safely without deleting the box.
- Supply-policy feedback now reflects dynamic occupied-slot and similar-stack values.

### Fixed

- Name length is measured as visible Unicode code points after color and format codes are stripped, while preserving the fixed 399-character expanded Bukkit safety cap for ICUAC compatibility.
- Tightening a Kit-name limit can no longer trap recovery-name generation in an endless suffix loop.

## [2.0.2] - 2026-07-29（中文）

### 新增

- 新增可热重载的 Unicode 可见名称上限：官方默认物品自定义名称 40 字符，个人/共享 Kit 与上传补给名称 18 字符。
- 新增可热重载的上传补给内容规则：最少填入格数、是否拒绝整盒全同、同类最多占用组数。
- 命名说明和拒绝提示会显示当前生效上限，并在去除 Minecraft 颜色/格式代码后计算字符数。

### 变更

- 自动保存改为递增快照（`autosave-1`、`autosave-2`……），相同内容不会重复生成；快照遵守 `max-kits`，达到上限时只轮换最旧的自动保存，不再覆盖固定 Kit。
- 启动、加载及执行 `/kitloader reload` 时，会按当前规则重新校验旧补给；旧补给名称超限时安全重置名称，不删除整盒物品。
- 补给规则提示会显示当前动态填入格数及同类组数。

### 修复

- 名称长度改为去除颜色与格式代码后的 Unicode 可见码点计数，同时保留用于 ICUAC 兼容的 399 字符 Bukkit 展开安全线。
- 收紧 Kit 名称上限时，恢复名称生成不再因不断追加后缀而陷入死循环。

## [2.0.1] - 2026-07-29

### Added

- ICUAC-compatible custom-name enforcement: final Bukkit display names, including expanded color codes, are capped at 399 characters across chat naming, `/kit`, and `/regear`.
- Recursive startup/load cleanup removes persisted items whose display names reach 400 characters from Kits, GUI categories, uploaded supplies, container snapshots, and bundles before ICUAC can scan them.
- Uploaded supplies now require all 27 slots, reject all-one-type contents, and limit one similar item type to 16 occupied stacks; item renaming does not bypass grouping.
- Existing uploaded supplies that violate the new content policy are removed from player files and public supply records with localized player/console notices.

### Changed

- Official release assets are now named `Kitloader-en.us.jar` and `Kitloader-zh.cn.jar`; the updater, verification scripts, workflow, and documentation use the same names.
- GitHub Release notes now append the current version's English-first, Chinese-second CHANGELOG section automatically.

### Fixed

- Deleting an uploaded shared Kit now clears its edit cache and returns to the public one-click Kit page.
- Shared-Kit edits can now be discarded from the save confirmation. Closing the editor with ESC/E compares changes and opens discard confirmation instead of closing all Kitloader interfaces.
- Supply-policy validation now also applies to `/regear` edits and both upload confirmation paths.

## [2.0.1] - 2026-07-29（中文）

### 新增

- 新增 ICUAC 兼容的自定义名称限制：聊天命名、`/kit` 与 `/regear` 均按颜色代码展开后的 Bukkit 最终显示名限制为 399 字符。
- 启动/加载时递归清理达到 400 字符的持久化物品，覆盖 Kit、GUI 分类、上传补给、容器快照与收纳袋，确保在 ICUAC 扫描前删除。
- 上传补给现在必须填满 27 格，禁止整盒只有同一种物品，并限制同一种相似物品最多占用 16 组；重命名不能绕过分组。
- 加载时会删除不符合新内容规则的现有玩家补给和公共补给记录，并显示本地化的玩家/后台提示。

### 变更

- 官方 Release 资源改名为 `Kitloader-en.us.jar` 与 `Kitloader-zh.cn.jar`；自动更新器、校验脚本、工作流和文档同步使用新名称。
- GitHub Release Notes 会自动附加当前版本英文在前、中文在后的 CHANGELOG 内容。

### 修复

- 删除已上传共享 Kit 后会清理编辑缓存并返回公共“一键 Kit”页面。
- 共享 Kit 编辑现在可在保存确认页放弃修改；按 ESC/E 关闭时会比较改动并打开放弃确认，不再关闭全部 Kitloader 界面。
- 补给内容规则同时覆盖 `/regear` 编辑和两条上传确认路径。

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
