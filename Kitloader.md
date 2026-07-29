# Kitloader Administrator Guide

> **Official project statement:** Kitloader is fully open source. It contains no backdoor, telemetry, or remote collection of server data. Kits, uploaded supplies, and configuration data remain on the server that creates them. Update checks and optional downloads use only this project's GitHub Release endpoint. Obtain releases only from [Lazyzouo/Kitloader Releases](https://github.com/Lazyzouo/Kitloader/releases).

**Version:** 2.0.0  
**Tested baseline:** Paper/Folia 1.21.11  
**Java:** 21  
**Author:** Lazyz

## 1. Installation and language

Install exactly one official asset from [Releases](https://github.com/Lazyzouo/Kitloader/releases) in `plugins/`:

| Package | Default `language` | Audience |
| --- | --- | --- |
| `en.us.jar` | `en_US` | English servers |
| `zh.cn.jar` | `zh_CN` | Simplified Chinese servers |

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
- Autosave runs only when closing or refreshing the Kitloader interface, not after every Kit use. It requires the configured filled-slot threshold.

### Shared Kits

- Upload requires all 36 storage slots to be filled; armor and offhand are ignored.
- Content matching prevents duplicate shared Kits even if names or item display names change.
- Upload counts are enforced per player. Shared Kit management allows edit, rename, publication, and deletion.

### Custom supplies

- Upload requires a non-empty shulker box, rejects nested shulker boxes, checks per-player limits, and rejects equivalent contents.
- Uploads and public supply entries are ordered by upload time.
- A hidden supply is removed immediately from every public page, including the owner's public page, and cannot be claimed there. It remains editable in uploaded-supply management.
- Publishing a hidden supply appends it at the end of the public supply sequence and refreshes open supply pages.

### Ender Chest and shulker limits

- `enderchest-max` controls both allowed shulker box count and dynamically visible storage slots. Values above nine use the larger UI without forcing a 54-slot UI when nine slots are configured.
- `/kitloader invmax` synchronizes carried and personal-Kit save limits.
- The live `/inv` editor synchronizes target inventory and Ender Chest changes while it remains open.

### Item editor

- Left click and Shift click claim/transfer supported items; right click opens the relevant editor when appropriate.
- Armor trim and raw-material dyes choose a random eligible trim/material instead of always choosing the first entry.
- Incompatible enchantments are rejected with a cooldown so messages and rejection sounds cannot flood the player.
- The disposal area intentionally produces no destruction sound and no "silent" wording.

## 5. Updating

At startup, Kitloader compares its version with the latest official Release. When a newer version exists and auto-download is enabled, it selects `en.us.jar` or `zh.cn.jar` from the active `language`, rejects files over 50 MiB, verifies the GitHub `sha256:` asset digest, and puts the verified JAR in Bukkit's update directory. It never replaces the running JAR. If any update step fails, the console prints the official Release URL for manual download.

Only download from [https://github.com/Lazyzouo/Kitloader/releases](https://github.com/Lazyzouo/Kitloader/releases). GitHub-generated source snapshots are not installation packages.

## 6. Release maintenance

Every functional update must increment `build.gradle` version, update `CHANGELOG.md`, and update this guide when administrators need to know about new configuration, limits, compatibility, or logic. Major updates increment the major version. GitHub Actions builds the two language packages and publishes only `en.us.jar` and `zh.cn.jar` for a new version tag; source archives are not manually uploaded.

---

# Kitloader 管理员说明

> **官方项目声明：**Kitloader 是彻底开源的项目，不含后门、遥测或远程收集服务器数据的机制。Kit、上传补给和配置数据只保存在创建它们的服务器上。更新检查及可选下载只会使用本项目的 GitHub Release 接口。请只从 [Lazyzouo/Kitloader Releases](https://github.com/Lazyzouo/Kitloader/releases) 获取发布包。

**版本：**2.0.0  
**测试基线：**Paper/Folia 1.21.11  
**Java：**21  
**作者：**Lazyz

## 1. 安装与语言

从 [Releases](https://github.com/Lazyzouo/Kitloader/releases) 下载一个官方资源放进 `plugins/`：

| 包 | 默认 `language` | 面向服务器 |
| --- | --- | --- |
| `en.us.jar` | `en_US` | 英文服务器 |
| `zh.cn.jar` | `zh_CN` | 简体中文服务器 |

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
- 自动保存只在关闭或刷新 Kitloader 界面时触发，不会在每次使用 Kit 后触发，并要求达到填满格数阈值。

### 共享 Kit

- 上传必须填满 36 个储物格；盔甲和副手不参与判断。
- 内容匹配会阻止相同共享 Kit，即使修改 Kit 名称或物品显示名也不能绕过。
- 上传数量按玩家限制；管理页面可编辑、重命名、公开及删除。

### 自定义补给

- 上传要求潜影盒非空，禁止盒中盒，检查每人上限，并拒绝内容等价的补给。
- 上传补给与公共补给按上传时间排序。
- 隐藏补给会立即从所有公共页移除，包括上传者自己的公共页，且不能在公共页领取；仍可在已上传补给管理页编辑。
- 重新公开时会排列到公共补给末尾，并刷新已打开的补给页。

### 末影箱与潜影盒限制

- `enderchest-max` 同时控制允许的潜影盒数量和动态显示的储物格。数值大于 9 时使用大界面；设置为 9 不会错误切换为 54 格 UI。
- `/kitloader invmax` 会同步携带与个人 Kit 保存的潜影盒限制。
- 实时 `/inv` 编辑器在打开期间会同步目标玩家背包和末影箱的变更。

### 物品编辑

- 左键与 Shift 点击用于领取/转移支持的物品；适当情况下右键打开编辑界面。
- 盔甲纹饰和原材料染色从可用项中随机选择，不再固定使用第一个。
- 不兼容附魔会在冷却内拒绝，防止提示和拒绝音效刷屏。
- 销毁区不播放销毁音效，也不显示“无声”字样。

## 5. 更新

启动时，Kitloader 会与最新官方 Release 比较版本。当存在新版本并启用自动下载时，会根据当前 `language` 选择 `en.us.jar` 或 `zh.cn.jar`，拒绝大于 50 MiB 的文件，验证 GitHub `sha256:` 资源摘要，并将验证后的 JAR 放到 Bukkit 更新目录。它绝不会替换正在运行的 JAR。任一步失败时，后台会打印官方 Release 地址供手动下载。

只应从 [https://github.com/Lazyzouo/Kitloader/releases](https://github.com/Lazyzouo/Kitloader/releases) 下载。GitHub 自动生成的源码快照不是安装包。

## 6. 发布维护

每次功能更新必须递增 `build.gradle` 版本、更新 `CHANGELOG.md`；如果管理员需要了解新的配置、限制、兼容性或逻辑，也必须更新本说明。重大更新递增主版本号。GitHub Actions 会构建两个语言包，并在新版本标签时只发布 `en.us.jar`、`zh.cn.jar`；不会手工上传源码压缩包。
