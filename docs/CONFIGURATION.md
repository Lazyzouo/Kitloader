# Configuration Reference / 配置参考

**Baseline:** Paper/Folia 1.21.11, Java 21. The initial generated `config.yml` comes from the selected release package. Personal runtime configuration is not stored in this repository.

**基线：**Paper/Folia 1.21.11、Java 21。初次生成的 `config.yml` 来自所选 Release 包。本仓库不保存个人运行时配置。

## Official defaults / 官方默认值

| Path | Default | Effect |
| --- | ---: | --- |
| `language` | package-specific | `en_US` or `zh_CN` runtime text |
| `updates.enabled` | `true` | Checks official GitHub Releases at startup |
| `updates.auto-download` | `true` | Stages a verified matching-language JAR for next restart |
| `settings.max-kits` | `9` | Maximum personal Kits per player |
| `settings.custom-supply.enabled` | `true` | Allows player custom-supply uploads |
| `settings.custom-supply.max-limit` | `3` | Maximum uploaded supply boxes per player |
| `settings.custom-supply.content-policy.required-filled-slots` | `27` | Minimum occupied slots; accepted range 1-27 |
| `settings.custom-supply.content-policy.reject-all-same` | `true` | Rejects a box containing only one similar item type |
| `settings.custom-supply.content-policy.max-similar-stacks` | `16` | Maximum occupied stacks of one similar type; range 1-27 |
| `settings.naming.item-max-visible-length` | `40` | Custom item-name visible Unicode limit; range 1-399 |
| `settings.naming.kit-max-visible-length` | `18` | Personal/shared Kit-name visible Unicode limit; range 1-399 |
| `settings.naming.supply-max-visible-length` | `18` | Uploaded supply-name visible Unicode limit; range 1-399 |
| `settings.public-kits.upload-enabled` | `true` | Allows shared-Kit uploads |
| `settings.public-kits.max-limit` | `2` | Maximum shared Kits per player |
| `settings.single-use-worlds` | `[]` | Worlds with one load per death/respawn cycle |
| `settings.bypass-whitelist` | `[]` | Players/UUIDs bypassing world restrictions and carried `inventory-max`, and allowed `/regear`; `/inv` also requires OP |
| `settings.autosave.required-filled-slots` | `36` | Filled storage slots required for autosave |
| `settings.shulker-limits.kit-save-max` | `3` | Maximum shulker boxes kept in a saved Kit |
| `settings.shulker-limits.inventory-max` | `3` | Default carried shulker-box maximum |
| `settings.shulker-limits.enderchest-max` | `9` | Ender Chest shulker maximum and dynamic UI capacity |
| `settings.enchantments.rejection-cooldown-ms` | `1500` | Cooldown for enchantment conflict rejection feedback |

| 路径 | 默认值 | 作用 |
| --- | ---: | --- |
| `language` | 按包决定 | `en_US` 或 `zh_CN` 运行时文字 |
| `updates.enabled` | `true` | 启动时检测官方 GitHub Releases |
| `updates.auto-download` | `true` | 下载已验证的对应语言包，供下次重启安装 |
| `settings.max-kits` | `9` | 每位玩家的个人 Kit 上限 |
| `settings.custom-supply.enabled` | `true` | 允许玩家上传自定义补给 |
| `settings.custom-supply.max-limit` | `3` | 每位玩家的上传补给盒上限 |
| `settings.custom-supply.content-policy.required-filled-slots` | `27` | 补给至少填入格数，范围 1-27 |
| `settings.custom-supply.content-policy.reject-all-same` | `true` | 拒绝整盒只有一种相似物品 |
| `settings.custom-supply.content-policy.max-similar-stacks` | `16` | 同类物品最多占用组数，范围 1-27 |
| `settings.naming.item-max-visible-length` | `40` | 物品自定义名称可见 Unicode 字符上限，范围 1-399 |
| `settings.naming.kit-max-visible-length` | `18` | 个人/共享 Kit 名称可见 Unicode 字符上限，范围 1-399 |
| `settings.naming.supply-max-visible-length` | `18` | 上传补给名称可见 Unicode 字符上限，范围 1-399 |
| `settings.public-kits.upload-enabled` | `true` | 允许上传共享 Kit |
| `settings.public-kits.max-limit` | `2` | 每位玩家的共享 Kit 上限 |
| `settings.single-use-worlds` | `[]` | 每次死亡/复活周期只能加载一次 Kit 的世界 |
| `settings.bypass-whitelist` | `[]` | 绕过世界限制与随身 `inventory-max`，且可用 `/regear` 的玩家/UUID；`/inv` 还要求 OP |
| `settings.autosave.required-filled-slots` | `36` | 触发自动保存所需填满的储物格数 |
| `settings.shulker-limits.kit-save-max` | `3` | 保存 Kit 时保留的潜影盒上限 |
| `settings.shulker-limits.inventory-max` | `3` | 默认可携带潜影盒上限 |
| `settings.shulker-limits.enderchest-max` | `9` | 末影箱潜影盒上限与动态 UI 容量 |
| `settings.enchantments.rejection-cooldown-ms` | `1500` | 附魔冲突拒绝反馈的冷却时间 |

## Hot-reloadable policies and fixed safety limits / 可热重载规则与固定安全线

The six naming and supply-policy options above are read at validation time. Apply changes with `/kitloader reload`; no server restart is required. Numeric values outside their documented range are clamped.

- Visible-name length counts Unicode code points after Minecraft color and format codes are removed. Color/format codes therefore do not consume the 40/18/18 visible limits.
- The expanded Bukkit display-name safety cap remains fixed at 399 characters for ICUAC compatibility. Persisted items reaching 400 characters are recursively removed before display.
- Similar-supply grouping ignores item display names. Setting `reject-all-same: false` alone does not permit 27 identical stacks while `max-similar-stacks` remains 16; set the latter to 27 as well when that behavior is intended.
- Startup, player load, and reload remove uploaded supplies that violate the current policy. Over-limit existing supply names are reset to the configured safe default without deleting the box.
- Autosave snapshots use increasing names (`autosave-1`, `autosave-2`, ...), skip unchanged content, and count toward `settings.max-kits`. `autosave` and `autosave-N` are reserved system names. At the limit, only the oldest Kit in that namespace rotates out; all other manual Kits are preserved.

以上六项名称与补给规则会在每次校验时读取。修改后执行 `/kitloader reload` 即可生效，无需重启；超出文档范围的数字会自动限制到有效范围。

- 名称按去除 Minecraft 颜色与格式代码后的 Unicode 码点计数，因此颜色/格式代码不占用 40/18/18 的可见字符额度。
- 为兼容 ICUAC，展开后的 Bukkit 显示名固定保留 399 字符安全线；达到 400 字符的持久化物品会在显示前递归删除。
- 补给同类分组忽略物品显示名。若只把 `reject-all-same` 改为 `false`，但 `max-similar-stacks` 仍为 16，27 组相同物品仍会被拒绝；需要允许时应同时改为 27。
- 启动、玩家加载和热重载会删除不符合当前补给规则的上传记录；旧补给名称超限时只会重置为安全默认名，不会删除整盒物品。
- 自动保存使用递增的 `autosave-1`、`autosave-2`，相同内容不会重复生成，并计入 `settings.max-kits`。`autosave` 与 `autosave-N` 属于系统保留名称；达到上限时只轮换此命名空间内最旧的 Kit，不会删除其他手动 Kit。

## Update behavior / 更新行为

Only newer semantic versions are staged. The updater requests the latest Release from `Lazyzouo/Kitloader`, selects `Kitloader-<latest-version>-en.us.jar` or `Kitloader-<latest-version>-zh.cn.jar` according to `language`, caps the download at 50 MiB, requires GitHub's `sha256:` asset digest, and writes a verified file to Bukkit's update directory. Disable either update option if the server must not make outbound GitHub requests.

只会暂存更高的语义版本。更新器从 `Lazyzouo/Kitloader` 获取最新 Release，按 `language` 选择 `Kitloader-<latest-version>-en.us.jar` 或 `Kitloader-<latest-version>-zh.cn.jar`，限制下载为 50 MiB，要求 GitHub 的 `sha256:` 资源摘要，并将验证后的文件写入 Bukkit 更新目录。如服务器不能访问 GitHub，可关闭任一更新选项。
