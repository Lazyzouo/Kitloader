# Kitloader Administrator Guide

> **Official project statement:** Kitloader is fully open source. It contains no backdoor, telemetry, or remote collection of server data. Kits, uploaded supplies, and configuration data remain on the server that creates them. Update checks and optional downloads use only this project's GitHub Release endpoint. Obtain releases only from [Lazyzouo/Kitloader Releases](https://github.com/Lazyzouo/Kitloader/releases).

**Version:** 2.0.6

**Tested baseline:** Paper/Folia 1.21.11  
**Java:** 21  
**Author:** Lazyz

## 1. Installation and language

Install exactly one official asset from [Releases](https://github.com/Lazyzouo/Kitloader/releases) in `plugins/`:

| Package | Default `language` | Audience |
| --- | --- | --- |
| `Kitloader-2.0.6-en.us.jar` | `en_US` | English servers |
| `Kitloader-2.0.6-zh.cn.jar` | `zh_CN` | Simplified Chinese servers |

Both packages contain the same code and behavior. They differ only in the official default language/configuration preset. Do not install both at once. `src/main/resources/config.yml` is intentionally a local runtime file and is never published. The repository stores official presets under `presets/`.

## 2. Commands and access

| Command | Access | Behavior |
| --- | --- | --- |
| `/kit` | Player | Opens help; `/kit <name>` loads a saved Kit |
| `/kit list` | Player | Opens the player's personal Kit list |
| `/kit save <name>` | Player | Saves the current inventory as a personal Kit |
| `/kit delete <name>` | Player | Deletes a personal Kit after confirmation |
| `/kitloader` | Player | Opens the configured default category |
| `/kitloader reload` | `kitloader.admin` | Reloads configuration, language data, GUI data, and public Kits |
| `/kitloader whitelist <add|remove|list>` | `kitloader.admin` | Manages `bypass-whitelist` |
| `/kitloader ecmax <1-27>` | `kitloader.admin` | Updates Ender Chest limit and dynamic UI capacity |
| `/kitloader invmax <1-36>` | `kitloader.admin` | Updates carried and saved-Kit shulker limits |
| `/inv [player]` | OP plus `bypass-whitelist` | Live inventory/equipment/Ender Chest editor for online players |
| `/regear <player> list` | `bypass-whitelist` | Manages any known player's uploaded supplies, including hidden supplies |

In `single-use-worlds`, non-whitelisted players may use only `/kit`, `/kit list`, and `/kit help`; other Kitloader commands and Kitloader UIs are blocked. The whitelist accepts player names or UUIDs and also authorizes `/regear`. `/inv` additionally requires OP.

## 3. Official defaults

| Option | Official default | Administrator impact |
| --- | ---: | --- |
| `updates.enabled` | `true` | Checks official Release at startup |
| `updates.auto-download` | `true` | Stages the verified matching JAR for next restart |
| `settings.max-kits` | `9` | Personal Kit maximum per player |
| `custom-supply.enabled` | `true` | Lets players upload custom supplies |
| `custom-supply.max-limit` | `3` | Uploaded supply maximum per player |
| `custom-supply.content-policy.required-filled-slots` | `27` | Minimum occupied supply slots; range 1-27 |
| `custom-supply.content-policy.reject-all-same` | `true` | Rejects a supply containing only one similar item type |
| `custom-supply.content-policy.max-similar-stacks` | `16` | Maximum occupied stacks of one similar type; range 1-27 |
| `naming.item-max-visible-length` | `40` | Custom item-name visible-character limit |
| `naming.kit-max-visible-length` | `18` | Personal/shared Kit-name visible-character limit |
| `naming.supply-max-visible-length` | `18` | Uploaded supply-name visible-character limit |
| `public-kits.upload-enabled` | `true` | Lets players upload shared Kits |
| `public-kits.max-limit` | `2` | Shared Kit maximum per player |
| `autosave.required-filled-slots` | `36` | Required filled storage slots for autosave |
| `shulker-limits.kit-save-max` | `3` | Maximum shulker boxes retained in a saved Kit |
| `shulker-limits.inventory-max` | `3` | Default carried shulker-box maximum |
| `shulker-limits.enderchest-max` | `9` | Ender Chest shulker limit and dynamic UI capacity |
| `enchantments.rejection-cooldown-ms` | `1500` | Prevents enchantment-conflict feedback/sound spam |

The complete option reference is in [docs/CONFIGURATION.md](docs/CONFIGURATION.md). `single-use-worlds`, `special-limit-worlds`, and `bypass-whitelist` intentionally default to empty lists so the official release never imports a server owner's world names or player data.

## 4. Core logic and limits

### Personal Kits

- A save copies the player's storage inventory; excess shulker boxes are trimmed from the saved copy according to `kit-save-max`.
- Loading clears the current inventory and applies the stored copy. In restricted worlds, a player must die and respawn before loading again.
- Autosave runs only when closing or refreshing the Kitloader interface, not after every Kit use. It requires the configured filled-slot threshold and skips a snapshot identical to the newest autosave.
- Successful snapshots use increasing names (`autosave-1`, `autosave-2`, ...). They count toward `max-kits`; when full, only the oldest autosave is rotated out. `autosave` and `autosave-N` are reserved system names. Kits outside that namespace are never removed, so autosave is skipped when all slots are manual Kits.

### Shared Kits

- Upload requires all 36 storage slots to be filled; armor and offhand are ignored.
- Content matching prevents duplicate shared Kits even if names or item display names change.
- Upload counts are enforced per player. Shared Kit management allows edit, rename, publication, and deletion.
- Leaving the shared-Kit editor now compares the working copy: unchanged edits return directly, while changed edits offer save/discard controls; ESC/E opens a discard confirmation instead of closing every Kitloader UI.

### Custom supplies

- Supply content policy is hot-reloadable: the official defaults require 27 occupied slots, reject all-one-type contents, and allow one similar type in at most 16 occupied stacks. The numeric values are clamped to 1-27. Nested boxes, per-player limits, and equivalent uploads remain rejected; renaming does not bypass grouping.
- Existing uploads that violate the current policy are removed from player data and public records during startup, load, or `/kitloader reload`. Existing over-limit supply names are reset to a safe configured default without deleting the box.
- Uploads and public supply entries are ordered by upload time.
- A hidden supply is removed immediately from every public page, including the owner's public page, and cannot be claimed there. It remains editable in uploaded-supply management.
- Publishing a hidden supply appends it at the end of the public supply sequence and refreshes open supply pages.

### Ender Chest and shulker limits

- `enderchest-max` controls both allowed shulker box count and dynamically visible storage slots. Values above nine use the larger UI without forcing a 54-slot UI when nine slots are configured.
- `/kitloader invmax` synchronizes carried and personal-Kit save limits. Players in `bypass-whitelist` bypass only the carried `inventory-max`; Ender Chest and saved-Kit limits still apply.
- The live `/inv` editor synchronizes target inventory and Ender Chest changes while it remains open.

### Item editor

- GUI controls use the proven 1.4.1 native inventory-click path, so item claims, refresh controls, category switches, and page arrows remain interactive while the player moves or jumps. Left click lets Minecraft complete its own slot transaction and restores the display template on the next player tick; Shift click, number-key, and offhand claims complete inside the event. A one-tick per-player lock prevents repeated same-tick grants.
- Armor trim and raw-material dyes choose a random eligible trim/material instead of always choosing the first entry.
- Incompatible enchantments are rejected with a cooldown so messages and rejection sounds cannot flood the player.
- The disposal area intentionally produces no destruction sound and no "silent" wording.
- Visible Unicode characters are counted after Minecraft color and format codes are removed. Official defaults are 40 for custom item names and 18 for personal/shared Kit and uploaded-supply names; all three are hot-reloadable from `settings.naming`. A non-configurable 399-character expanded Bukkit safety cap remains, and persisted items at 400 or more are recursively removed before ICUAC can scan them.

## 5. Updating

The startup banner, startup-success message, and all update-check states use one colored console prefix and status palette.

At startup, Kitloader compares its version with the latest official Release. When a newer version exists and auto-download is enabled, it selects `Kitloader-<latest-version>-en.us.jar` or `Kitloader-<latest-version>-zh.cn.jar` from the active `language`, rejects files over 50 MiB, verifies the GitHub `sha256:` asset digest, and puts the verified JAR in Bukkit's update directory. It never replaces the running JAR. If any update step fails, the console prints the official Release URL for manual download.

Only download from [https://github.com/Lazyzouo/Kitloader/releases](https://github.com/Lazyzouo/Kitloader/releases). GitHub-generated source snapshots are not installation packages.

## 6. Release maintenance

Every functional update must increment `build.gradle` version, update `CHANGELOG.md`, and update this guide when administrators need to know about new configuration, limits, compatibility, or logic. Major updates increment the major version. GitHub Actions builds the two language packages and publishes only `Kitloader-<version>-en.us.jar` and `Kitloader-<version>-zh.cn.jar` for a new version tag; source archives are not manually uploaded.

---

# Kitloader 管理员说明

> **官方项目声明：**Kitloader 是彻底开源的项目，不含后门、遥测或远程收集服务器数据的机制。Kit、上传补给和配置数据只保存在创建它们的服务器上。更新检查及可选下载只会使用本项目的 GitHub Release 接口。请只从 [Lazyzouo/Kitloader Releases](https://github.com/Lazyzouo/Kitloader/releases) 获取发布包。

**版本：**2.0.6

**测试基线：**Paper/Folia 1.21.11  
**Java：**21  
**作者：**Lazyz

## 1. 安装与语言

从 [Releases](https://github.com/Lazyzouo/Kitloader/releases) 下载一个官方资源放进 `plugins/`：

| 包 | 默认 `language` | 面向服务器 |
| --- | --- | --- |
| `Kitloader-2.0.6-en.us.jar` | `en_US` | 英文服务器 |
| `Kitloader-2.0.6-zh.cn.jar` | `zh_CN` | 简体中文服务器 |

两个包的源码和功能完全一致，只是官方默认语言/配置预设不同。不要同时安装两个包。`src/main/resources/config.yml` 被视为本地运行文件，不会发布；仓库中的官方预设位于 `presets/`。

## 2. 指令与权限

| 指令 | 权限 | 行为 |
| --- | --- | --- |
| `/kit` | 玩家 | 打开帮助；`/kit <名称>` 加载已保存 Kit |
| `/kit list` | 玩家 | 打开自己的个人 Kit 列表 |
| `/kit save <名称>` | 玩家 | 将当前背包保存为个人 Kit |
| `/kit delete <名称>` | 玩家 | 确认后删除个人 Kit |
| `/kitloader` | 玩家 | 打开配置的默认分类 |
| `/kitloader reload` | `kitloader.admin` | 重载配置、语言、GUI 与公共 Kit |
| `/kitloader whitelist <add|remove|list>` | `kitloader.admin` | 管理 `bypass-whitelist` |
| `/kitloader ecmax <1-27>` | `kitloader.admin` | 设置末影箱限制与动态 UI 容量 |
| `/kitloader invmax <1-36>` | `kitloader.admin` | 同步携带与保存 Kit 的潜影盒限制 |
| `/inv [玩家]` | OP 且在 `bypass-whitelist` | 实时编辑在线玩家背包、装备和末影箱 |
| `/regear <玩家> list` | `bypass-whitelist` | 管理任意已知玩家的上传补给，包括隐藏补给 |

在 `single-use-worlds` 中，未在白名单的玩家只可使用 `/kit`、`/kit list`、`/kit help`，其他 Kitloader 指令和 UI 均会被禁止。白名单支持玩家名或 UUID，同时授权 `/regear`；`/inv` 还要求 OP。

## 3. 官方默认值

| 选项 | 官方默认值 | 管理员影响 |
| --- | ---: | --- |
| `updates.enabled` | `true` | 启动时检测官方 Release |
| `updates.auto-download` | `true` | 下载已验证的对应 JAR，供下次重启安装 |
| `settings.max-kits` | `9` | 每人个人 Kit 上限 |
| `custom-supply.enabled` | `true` | 允许玩家上传自定义补给 |
| `custom-supply.max-limit` | `3` | 每人上传补给上限 |
| `custom-supply.content-policy.required-filled-slots` | `27` | 补给至少填入格数，范围 1-27 |
| `custom-supply.content-policy.reject-all-same` | `true` | 拒绝整盒只有一种相似物品 |
| `custom-supply.content-policy.max-similar-stacks` | `16` | 同类物品最多占用组数，范围 1-27 |
| `naming.item-max-visible-length` | `40` | 物品自定义名称可见字符上限 |
| `naming.kit-max-visible-length` | `18` | 个人/共享 Kit 名称可见字符上限 |
| `naming.supply-max-visible-length` | `18` | 上传补给名称可见字符上限 |
| `public-kits.upload-enabled` | `true` | 允许玩家上传共享 Kit |
| `public-kits.max-limit` | `2` | 每人共享 Kit 上限 |
| `autosave.required-filled-slots` | `36` | 自动保存所需填满的储物格数 |
| `shulker-limits.kit-save-max` | `3` | 保存 Kit 中保留的潜影盒上限 |
| `shulker-limits.inventory-max` | `3` | 默认可携带潜影盒上限 |
| `shulker-limits.enderchest-max` | `9` | 末影箱潜影盒限制与动态 UI 容量 |
| `enchantments.rejection-cooldown-ms` | `1500` | 防止附魔冲突消息/音效刷屏 |

完整参数参考请见 [docs/CONFIGURATION.md](docs/CONFIGURATION.md)。`single-use-worlds`、`special-limit-worlds` 与 `bypass-whitelist` 默认均为空，保证官方发布包不会包含服主的世界名称或玩家数据。

## 4. 核心逻辑与限制

### 个人 Kit

- 保存时复制玩家储物栏；超过 `kit-save-max` 的潜影盒只会从保存副本中剔除。
- 加载会清空当前背包并应用保存副本。受限世界中，玩家必须死亡并复活后才可再次加载。
- 自动保存只在关闭或刷新 Kitloader 界面时触发，不会在每次使用 Kit 后触发；必须达到填满格数阈值，且与最新自动保存相同的内容不会重复生成。
- 成功快照按 `autosave-1`、`autosave-2` 递增并计入 `max-kits`。`autosave` 与 `autosave-N` 属于系统保留名称；达到上限时只轮换此命名空间内最旧的自动保存，绝不删除其他手动命名 Kit。若所有位置都是手动 Kit，则跳过自动保存。

### 共享 Kit

- 上传必须填满 36 个储物格；盔甲和副手不参与判断。
- 内容匹配会阻止相同共享 Kit，即使修改 Kit 名称或物品显示名也不能绕过。
- 上传数量按玩家限制；管理页面可编辑、重命名、公开及删除。
- 离开共享 Kit 编辑页时会比较工作副本：无改动直接返回，有改动则可保存或放弃；按 ESC/E 会打开放弃确认，不再直接关闭全部 Kitloader UI。

### 自定义补给

- 补给内容规则可热重载：官方默认至少填入 27 格、拒绝整盒全同、同类最多占用 16 组，数字会限制在 1-27。盒中盒、每人上限和内容重复仍会被拒绝，重命名不能绕过同类分组。
- 启动、加载或执行 `/kitloader reload` 时，会从玩家数据和公共记录删除不符合当前规则的旧补给；旧补给名称若超过当前上限，只会重置为安全默认名，不会删除整盒物品。
- 上传补给与公共补给按上传时间排序。
- 隐藏补给会立即从所有公共页移除，包括上传者自己的公共页，且不能在公共页领取；仍可在已上传补给管理页编辑。
- 重新公开时会排列到公共补给末尾，并刷新已打开的补给页。

### 末影箱与潜影盒限制

- `enderchest-max` 同时控制允许的潜影盒数量和动态显示的储物格。数值大于 9 时使用大界面；设置为 9 不会错误切换为 54 格 UI。
- `/kitloader invmax` 会同步携带与个人 Kit 保存的潜影盒限制。`bypass-whitelist` 玩家仅绕过随身背包的 `inventory-max`，末影箱与 Kit 保存限制仍然生效。
- 实时 `/inv` 编辑器在打开期间会同步目标玩家背包和末影箱的变更。

### 物品编辑

- GUI 控件恢复使用经 1.4.1 验证的原生库存点击路径，因此玩家移动或跳跃时仍可领取物品、刷新、切换分类和翻页。左键领取由 Minecraft 自身完成槽位事务，并在下一玩家刻恢复展示模板；Shift 点击、数字键及副手领取在事件内即时完成。每名玩家一刻领取锁可防止同一刻重复发放。
- 盔甲纹饰和原材料染色从可用项中随机选择，不再固定使用第一个。
- 不兼容附魔会在冷却内拒绝，防止提示和拒绝音效刷屏。
- 销毁区不播放销毁音效，也不显示“无声”字样。
- 可见 Unicode 字符会在去除 Minecraft 颜色与格式代码后计数。官方默认物品名称 40 字符、个人/共享 Kit 与上传补给名称 18 字符，三项均可在 `settings.naming` 热修改；不可配置的 399 字符 Bukkit 展开安全线仍保留，达到 400 字符的持久化物品会在 ICUAC 扫描前递归删除。

## 5. 更新
启动横幅、启动成功消息与全部更新检查状态统一使用同一套彩色后台前缀及状态配色。


启动时，Kitloader 会与最新官方 Release 比较版本。当存在新版本并启用自动下载时，会根据当前 `language` 选择 `Kitloader-<latest-version>-en.us.jar` 或 `Kitloader-<latest-version>-zh.cn.jar`，拒绝大于 50 MiB 的文件，验证 GitHub `sha256:` 资源摘要，并将验证后的 JAR 放到 Bukkit 更新目录。它绝不会替换正在运行的 JAR。任一步失败时，后台会打印官方 Release 地址供手动下载。

只应从 [https://github.com/Lazyzouo/Kitloader/releases](https://github.com/Lazyzouo/Kitloader/releases) 下载。GitHub 自动生成的源码快照不是安装包。

## 6. 发布维护

每次功能更新必须递增 `build.gradle` 版本、更新 `CHANGELOG.md`；如果管理员需要了解新的配置、限制、兼容性或逻辑，也必须更新本说明。重大更新递增主版本号。GitHub Actions 会构建两个语言包，并在新版本标签时只发布 `Kitloader-<version>-en.us.jar`、`Kitloader-<version>-zh.cn.jar`；不会手工上传源码压缩包。
