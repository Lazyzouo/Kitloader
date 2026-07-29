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
| `settings.public-kits.upload-enabled` | `true` | Allows shared-Kit uploads |
| `settings.public-kits.max-limit` | `2` | Maximum shared Kits per player |
| `settings.single-use-worlds` | `[]` | Worlds with one load per death/respawn cycle |
| `settings.bypass-whitelist` | `[]` | Players/UUIDs bypassing world restrictions and allowed `/regear`; `/inv` also requires OP |
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
| `settings.public-kits.upload-enabled` | `true` | 允许上传共享 Kit |
| `settings.public-kits.max-limit` | `2` | 每位玩家的共享 Kit 上限 |
| `settings.single-use-worlds` | `[]` | 每次死亡/复活周期只能加载一次 Kit 的世界 |
| `settings.bypass-whitelist` | `[]` | 绕过世界限制且可用 `/regear` 的玩家/UUID；`/inv` 还要求 OP |
| `settings.autosave.required-filled-slots` | `36` | 触发自动保存所需填满的储物格数 |
| `settings.shulker-limits.kit-save-max` | `3` | 保存 Kit 时保留的潜影盒上限 |
| `settings.shulker-limits.inventory-max` | `3` | 默认可携带潜影盒上限 |
| `settings.shulker-limits.enderchest-max` | `9` | 末影箱潜影盒上限与动态 UI 容量 |
| `settings.enchantments.rejection-cooldown-ms` | `1500` | 附魔冲突拒绝反馈的冷却时间 |

## Update behavior / 更新行为

Only newer semantic versions are staged. The updater requests the latest Release from `Lazyzouo/Kitloader`, selects `en.us.jar` or `zh.cn.jar` according to `language`, caps the download at 50 MiB, requires GitHub's `sha256:` asset digest, and writes a verified file to Bukkit's update directory. Disable either update option if the server must not make outbound GitHub requests.

只会暂存更高的语义版本。更新器从 `Lazyzouo/Kitloader` 获取最新 Release，按 `language` 选择 `en.us.jar` 或 `zh.cn.jar`，限制下载为 50 MiB，要求 GitHub 的 `sha256:` 资源摘要，并将验证后的文件写入 Bukkit 更新目录。如服务器不能访问 GitHub，可关闭任一更新选项。
