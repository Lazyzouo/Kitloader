# Changelog

All notable changes are documented here. Versions follow `MAJOR.MINOR.PATCH`; every published functional update increments the version, and major changes increment `MAJOR`.

所有重要变更均记录于此。版本遵循 `MAJOR.MINOR.PATCH`；每次发布功能更新都必须递增版本，重大更新递增 `MAJOR`。

## [2.0.14] - 2026-07-31

### Fixed

- GUI click targets are now resolved from the server raw slot and inventory view instead of inventory-object identity. Server cores or inventory wrappers that return equivalent but distinct inventory objects can no longer let controls be picked up and visually refreshed without executing their action.
- `/kitloader` now waits for the player's asynchronous data load before opening the category GUI. Clicks on an already-open category page are also cancelled while data is still loading, preventing slow production storage from exposing control items to native inventory movement.

## [2.0.14] - 2026-07-31（中文）

### 修复

- GUI 点击目标现在依据服务端原始槽位和库存视图解析，不再依赖库存对象引用相等。对于会返回“内容等价但对象不同”库存包装的服务端核心，按钮不再出现被拿下、随后视觉刷新补回但功能未执行的情况。
- `/kitloader` 现在会等待玩家异步数据加载完成后再打开分类界面；若已有分类界面仍处于数据加载阶段，其点击也会被取消，避免正式服存储较慢时按钮被原版库存事务移动。

## [2.0.13] - 2026-07-30

### Changed

- All chat, help-menu, and feedback text sent to players is now left-aligned at send time. Presentation padding before the first visible character is removed while legacy/hex colors and formatting remain intact.
- Localized, configured, and hard-coded player messages now share the same alignment path. The server-console startup banner and console messages are unchanged.

## [2.0.13] - 2026-07-30（中文）

### 变更

- 所有发送给玩家的聊天、帮助菜单及操作提示文本现在都会在发送时统一左对齐。首个可见字符前用于排版的空白会被移除，原有传统颜色、十六进制颜色及格式代码保持不变。
- 本地化文本、配置文本和源码内玩家提示现已共用同一套对齐入口；服务器后台启动横幅及后台消息不受影响。

## [2.0.12] - 2026-07-30

### Changed

- Ported the relevant MicroKits GUI interaction model: page construction and navigation now execute immediately when the inventory event already owns the player's Paper/Folia region, and use the player entity scheduler only for genuinely cross-thread calls.
- Left-click template claims now leave Minecraft's native clicked stack untouched, restore the display template on the next player tick, and replace the received display copy with the clean deliverable item. An empty-cursor fallback completes the claim if movement causes the native cursor transaction to be dropped.

### Fixed

- Moving or jumping no longer adds an avoidable scheduler-tick delay before Kitloader page, refresh, category, editor, confirmation, Ender Chest, or management UI actions execute.

## [2.0.12] - 2026-07-30（中文）

### 变更

- 移植 MicroKits 的相关 GUI 交互模型：库存事件已持有玩家 Paper/Folia 区域线程时立即构建和切换页面，只有真正跨线程的调用才交给玩家实体调度器。
- 左键模板领取不再改写点击事件中的展示槽位；下一玩家刻恢复展示模板，并把收到的展示副本替换为干净的实际物品。若移动导致原生光标事务丢失，则在空光标时完成补发。

### 修复

- 玩家移动或跳跃时，Kitloader 的翻页、刷新、分类、编辑、确认、末影箱及管理页面操作不再额外等待一个调度刻才执行。

## [2.0.11] - 2026-07-30

### Changed

- Reduced the startup banner content width from 72 to 60 columns to match the compact console layout, and restored the hyphen separator between the bilingual heading and detail rows.
- Existing colored fields, complete side borders, and CJK double-width alignment remain unchanged.

## [2.0.11] - 2026-07-30（中文）

### 变更

- 启动横幅内容宽度从 72 列缩短为 60 列，以匹配紧凑后台布局，并恢复双语标题与详情行之间的短横线分隔。
- 现有字段配色、完整左右边框及中日韩双列宽度对齐逻辑保持不变。

## [2.0.10] - 2026-07-30

### Changed

- Reworked the startup banner to match the compact service-console layout: 72-column cyan equals borders, two centered heading rows, a full-width equals separator, and tightly aligned detail rows.
- The heading now identifies `KITLOADER LOADOUT SERVICE`, followed by `KIT LOADOUT MANAGEMENT / KIT 配装管理`; existing colored version, author, tested-platform, language, GitHub, and open-source fields remain.

## [2.0.10] - 2026-07-30（中文）

### 变更

- 启动横幅改为紧凑服务后台样式：72 列青色等号边框、两行居中标题、整行等号分隔线及紧密对齐的详情行。
- 主标题改为 `KITLOADER LOADOUT SERVICE`，副标题为 `KIT LOADOUT MANAGEMENT / KIT 配装管理`；现有彩色版本、作者、测试平台、语言、GitHub 与开源声明字段继续保留。

## [2.0.9] - 2026-07-30

### Changed

- Expanded the startup console banner to a 64-column, fully bordered service layout with a centered Kitloader version heading, bilingual Kit-management subtitle, separator, and aligned detail rows.
- Banner alignment now measures Han, Hiragana, Katakana, and Hangul characters as double-width so bilingual text keeps a consistent right border. Version, author, tested platform, language, GitHub, and open-source statements retain the colored console palette.

## [2.0.9] - 2026-07-30（中文）

### 变更

- 启动后台横幅扩展为 64 列完整边框的服务布局，新增居中的 Kitloader 版本标题、中英 Kit 管理副标题、分隔线及对齐的详情行。
- 横幅对齐会将汉字、平假名、片假名与韩文按双列宽度计算，使双语内容保持统一右边框；版本、作者、测试平台、语言、GitHub 与开源声明继续使用彩色后台配色。

## [2.0.8] - 2026-07-30

### Changed

- Restored the exact Kitloader 1.4.1 native inventory event model across the main GUI, `/inv`, and `/regear`: default Bukkit event priority, native `getClickedInventory()`, and native `getCurrentItem()` handling.
- Removed the 2.0.7 raw-slot click resolver and forced event-cancellation overrides while preserving protected controls, editor limits, and the one-tick duplicate-claim lock.

### Fixed

- GUI controls and editable inventory actions no longer depend on the 2.0.7 event wrapper that could leave clicks visually acknowledged but unexecuted while the player was moving or jumping.
- Official package names include the plugin name, version, and language: `Kitloader-2.0.8-en.us.jar` and `Kitloader-2.0.8-zh.cn.jar`.

## [2.0.8] - 2026-07-30（中文）

### 变更

- 主界面、`/inv` 与 `/regear` 完整恢复 Kitloader 1.4.1 的原生库存事件模型：使用 Bukkit 默认事件优先级、原生 `getClickedInventory()` 与原生 `getCurrentItem()`。
- 移除 2.0.7 的原始槽位点击解析器和强制事件取消覆盖，同时保留受保护控件、编辑限制及一刻防重复领取锁。

### 修复

- GUI 控件与可编辑库存操作不再依赖 2.0.7 的事件包装层，修复玩家移动或跳跃时客户端显示已点击、对应操作却没有执行的问题。
- 官方包名包含插件名、版本号与语言：`Kitloader-2.0.8-en.us.jar`、`Kitloader-2.0.8-zh.cn.jar`。

## [2.0.7] - 2026-07-29

### Changed

- Kitloader's main GUI, `/inv`, and `/regear` click and drag handlers now run at the final mutable event priority, receive previously cancelled interactions, and resolve top/bottom inventory targets from the raw slot and server `InventoryView`.
- Editable slots explicitly restore native mouse, number-key, Shift-click, and drag transactions. Protected toolbars, confirmation pages, and restricted slots remain cancelled.

### Fixed

- Moving or jumping can no longer leave page arrows, refresh buttons, category controls, or other GUI actions visually clicked without executing their corresponding operation.
- Shift transfers and template claims now read their source stack from the server inventory view, avoiding null or stale event snapshots while preserving the 1.4.1 native pickup transaction and one-tick duplicate-claim lock.

## [2.0.7] - 2026-07-29（中文）

### 变更

- Kitloader 主界面、`/inv` 与 `/regear` 的点击和拖动监听现统一在最终可修改事件优先级执行，接收此前已取消的交互，并通过原始槽位与服务端 `InventoryView` 判断上下层库存。
- 可编辑槽位会显式恢复鼠标、数字键、Shift 点击和拖动的原生事务；受保护工具栏、确认页面及受限槽位仍保持取消。

### 修复

- 玩家移动或跳跃时，翻页、刷新、分类及其他 GUI 按键不再出现客户端已点击、对应操作却没有执行的情况。
- Shift 转移与模板领取会从服务端库存视图读取来源物品，避免事件快照为空或过期；同时保留 1.4.1 的原生领取事务与一刻防重复领取锁。

## [2.0.6] - 2026-07-29

### Changed

- Restored the proven Kitloader 1.4.1 inventory interaction model: GUI controls are handled directly by the native inventory click event, and left-click claims temporarily expose the deliverable item to Minecraft's own slot transaction before restoring the display template on the next player tick.
- Shift-click, number-key, and offhand claims now complete inside the click event again. The one-tick per-player claim lock remains active to prevent repeated same-tick grants.

### Fixed

- Removed the 2.0.5 raw-slot wrapper and delayed forced-resynchronization layer, which competed with Minecraft's inventory transaction while a player was moving. Item claims, refresh controls, category switches, and page arrows now follow the same interaction path as 1.4.1.

## [2.0.6] - 2026-07-29（中文）

### 变更

- 恢复经 Kitloader 1.4.1 验证的库存交互模型：GUI 控件直接在原生库存点击事件中处理；左键领取时临时将真实领取物品放入被点击槽位，让 Minecraft 自身完成槽位事务，并在下一玩家刻恢复展示模板。
- Shift 点击、数字键及副手领取重新在点击事件内即时完成；每名玩家一刻领取锁继续保留，防止同一刻重复发放。

### 修复

- 移除 2.0.5 新增、会在玩家移动时与 Minecraft 库存事务竞争的原始槽位包装及延迟强制同步层。物品领取、刷新、分类切换及翻页现在与 1.4.1 使用同一条交互路径。

## [2.0.5] - 2026-07-29

### Changed

- The startup-success message now uses the same colored console prefix renderer as the startup banner and every update-status notice.

### Fixed

- Every Kitloader GUI now resolves top/bottom inventory clicks from the server raw slot and reads the clicked item from the server inventory snapshot. Simultaneous movement or jump packets can no longer make item claims, refresh controls, category switches, or page arrows appear unclickable.

## [2.0.5] - 2026-07-29（中文）

### 变更

- 插件启动成功消息现在与启动横幅及全部更新状态通知使用同一套彩色后台前缀渲染。

### 修复

- 所有 Kitloader GUI 现在根据服务端原始槽位判断上下层库存，并从服务端库存快照读取被点击物品；移动或跳跃包与点击包同时到达时，领取、刷新、分类切换及翻页不再表现为无法点击。

## [2.0.4] - 2026-07-29

### Changed

- The startup banner now renders every line through the colored console sender.
- Update checking, latest-version, available-version, downloaded, manual-download, and failure notices now use distinct status colors.
- Official JAR assets now include the plugin version before the language suffix, for example `Kitloader-2.0.4-en.us.jar`; the builder, updater, workflows, verification, and documentation share this format.

### Fixed

- Template claims now execute on the player's scheduler one tick after the cancelled GUI click and revalidate the cursor, inventory, hotbar, or offhand destination before writing. This prevents movement/jump inventory synchronization from discarding the claimed item.

## [2.0.4] - 2026-07-29（中文）

### 变更

- 启动横幅的每一行现在都会通过支持颜色的后台发送器输出。
- 检查更新、已是最新、发现新版、下载完成、手动下载及下载失败提示现在会使用不同的状态颜色。
- 官方 JAR 资源现在会在语言后缀前包含插件版本号，例如 `Kitloader-2.0.4-zh.cn.jar`；构建器、更新器、工作流、校验与文档统一使用该格式。

### 修复

- 模板物品领取改为在取消 GUI 点击后的下一玩家刻执行，并在写入前重新检查光标、背包、快捷栏或副手目标，避免玩家移动或跳跃时的库存同步覆盖已领取物品。

## [2.0.3] - 2026-07-29

### Changed

- Players in `bypass-whitelist` now bypass the carried `inventory-max` across background cleanup, ground pickup, GUI claims, and shared-Kit loading. Ender Chest and saved-Kit limits remain enforced.
- The startup banner now appends the running version to the Kitloader display name.

### Fixed

- Template item pickup no longer relies on an uncancelled vanilla inventory transaction, so left-click claims continue to work while the player is moving or jumping. Shift, hotbar-key, and offhand shortcuts retain duplicate-pickup protection.

## [2.0.3] - 2026-07-29（中文）

### 变更

- `bypass-whitelist` 白名单玩家现可在后台清理、地面拾取、GUI 领取及共享 Kit 加载时绕过随身背包 `inventory-max`；末影箱与 Kit 保存限制仍然生效。
- 启动横幅会在 Kitloader 显示名称末尾附加当前运行版本号。

### 修复

- 模板物品左键领取不再依赖未取消的原版库存事务，玩家移动或跳跃时也能正常领取；Shift、数字键与副手快捷操作仍保留防重复领取锁。

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
