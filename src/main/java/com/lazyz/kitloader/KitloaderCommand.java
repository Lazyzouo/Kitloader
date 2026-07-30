package com.lazyz.kitloader;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class KitloaderCommand implements CommandExecutor, TabCompleter {
    private final Kitloader plugin;
    private final GuiManager gui;
    private final DataManager data;
    private final InventoryLimitCommand inventoryLimitCommand;

    public KitloaderCommand(Kitloader plugin, GuiManager gui, DataManager data) {
        this.plugin = plugin;
        this.gui = gui;
        this.data = data;
        this.inventoryLimitCommand = new InventoryLimitCommand(plugin);
    }

    private void sendFormatMsg(CommandSender sender, String text) {
        String prefix = Kitloader.color(plugin.getMessageString("prefix", "&#00D2FF&l[&#3A7BD5&lKitloader&#00D2FF&l] &8&l» &7&l"));
        plugin.sendGameMessage(sender, prefix + text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player restrictedPlayer && plugin.isRestrictedKitloaderPlayer(restrictedPlayer)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("cancelname")) {
                DataManager.PlayerData pData = data.getPlayerData(restrictedPlayer.getUniqueId());
                if (pData != null) pData.clearNaming();
                plugin.sendMsg(restrictedPlayer, "naming_cancelled");
            } else {
                plugin.sendMsg(restrictedPlayer, "restricted_command");
            }
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                String currentTitle = org.bukkit.ChatColor.stripColor(player.getOpenInventory().getTitle());
                String categoryPrefix = org.bukkit.ChatColor.stripColor(plugin.getGuiTitle("category-prefix", ""));
                if (currentTitle != null && categoryPrefix != null
                        && currentTitle.startsWith(categoryPrefix) && currentTitle.contains(" - P")) {
                    data.tryAutosavePlayer(player);
                }

                String defaultCat = plugin.getConfig().getString("settings.default-category", "supply");
                gui.openCategoryGui(player, defaultCat, 0);
            } else {
                plugin.sendMsg(sender, "player_only");
            }
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("reload")) {
            if (!sender.hasPermission("kitloader.admin")) {
                plugin.sendMsg(sender, "no_permission");
                return true;
            }
            plugin.reloadPlugin();
            data.loadPublicKits();
            data.revalidateCachedPlayerData();
            gui.refreshOpenSupplyPages();
            plugin.refreshOnlineCommandTrees();
            plugin.sendMsg(sender, "reload_success");
            return true;
        }

        if (subCmd.equals("cancelname")) {
            if (sender instanceof Player player) {
                DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
                if (pData != null && pData.isNaming()) {
                    plugin.sendMsg(player, "naming_cancelled");

                    if (pData.editSession != null && pData.editSession.isNaming) {
                        pData.editSession.isNaming = false;
                        player.getScheduler().run(plugin, task -> gui.openCustomSupplyEditGui(player), null);
                    } else if (pData.publicEditSession != null && pData.publicEditSession.isNaming) {
                        pData.publicEditSession.isNaming = false;
                        player.getScheduler().run(plugin, task -> gui.openConfirmPublicUploadGui(player), null);
                    } else if (pData.namingContext != null) {
                        DataManager.NamingContext ctx = pData.namingContext;
                        if (ctx.type == DataManager.NamingContext.Type.PUBLIC_KIT_RENAME) {
                            String kitId = ctx.category;
                            DataManager.PublicKit pk = data.publicKits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
                            if (pk != null) player.getScheduler().run(plugin, task -> gui.openPublicKitEditGui(player, pk, true), null);
                        } else if (ctx.type == DataManager.NamingContext.Type.KIT_RENAME) {
                            player.getScheduler().run(plugin, task -> gui.openKitEditGui(player, ctx.category, true), null);
                        } else if (ctx.type == DataManager.NamingContext.Type.ADMIN_KIT_RENAME) {
                            String[] parts = ctx.category.split("@@");
                            if (parts.length == 2) {
                                player.getScheduler().run(plugin, task -> gui.openOtherPlayerKitEditGui(player, parts[0], parts[1], true), null);
                            }
                        } else if (ctx.type == DataManager.NamingContext.Type.EDIT_SESSION) {
                            if (ctx.targetItem != null && gui.isArmor(ctx.targetItem)) {
                                player.getScheduler().run(plugin, task -> gui.openArmorTrimGui(player), null);
                            } else {
                                player.getScheduler().run(plugin, task -> gui.openEnchantGui(player), null);
                            }
                        } else if (ctx.type == DataManager.NamingContext.Type.DIRECT) {
                            player.getScheduler().run(plugin, task -> gui.openCategoryGui(player, ctx.category, ctx.page), null);
                        }
                        pData.namingContext = null;
                    } else {
                        pData.clearNaming();
                    }
                }
            }
            return true;
        }

        if (subCmd.equals("ecmax")) {
            if (!sender.hasPermission("kitloader.admin")) {
                plugin.sendMsg(sender, "no_permission");
                return true;
            }
            if (args.length < 2) {
                sendFormatMsg(sender, "&c&l格式错误 &#34495E&l► &#95A5A6&l用法: &f&l/kitloader ecmax <数值>");
                return true;
            }
            try {
                int max = Integer.parseInt(args[1]);
                if (max > 27 || max <= 0) {
                    plugin.sendMsg(sender, "ecmax_invalid_range");
                    return true;
                }
                plugin.getConfig().set("settings.shulker-limits.enderchest-max", max);
                plugin.saveConfig();
                plugin.sendMsg(sender, "ecmax_set_success", "slots", String.valueOf(max));
            } catch (NumberFormatException e) {
                plugin.sendMsg(sender, "invalid_number");
            }
            return true;
        }

        if (subCmd.equals("invmax")) {
            return inventoryLimitCommand.handle(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        if (subCmd.equals("whitelist") || subCmd.equals("kitwhitelist")) {
            if (!sender.hasPermission("kitloader.admin")) {
                plugin.sendMsg(sender, "no_permission");
                return true;
            }
            if (args.length < 2) {
                sendFormatMsg(sender, "&c&l格式错误 &#34495E&l► &#95A5A6&l用法: &f&l/kitloader whitelist <add/remove/list> [玩家名]");
                return true;
            }
            String action = args[1].toLowerCase();
            List<String> whitelist = plugin.getConfig().getStringList("settings.bypass-whitelist");

            if (action.equals("list")) {
                if (whitelist.isEmpty()) {
                    sendFormatMsg(sender, "&e&l系统提示 &#34495E&l► &#95A5A6&l当前世界限制豁免白名单为空。");
                } else {
                    plugin.sendMsg(sender, "whitelist_list", "players", String.join("&8&l, &f&l", whitelist));
                }
                return true;
            }

            if (args.length < 3) {
                sendFormatMsg(sender, "&c&l格式错误 &#34495E&l► &#95A5A6&l用法: &f&l/kitloader whitelist " + action + " <玩家名>");
                return true;
            }

            String target = args[2];

            if (action.equals("add")) {
                boolean alreadyPresent = whitelist.stream().anyMatch(entry -> entry.equalsIgnoreCase(target));
                if (!alreadyPresent) {
                    whitelist.add(target);
                    plugin.getConfig().set("settings.bypass-whitelist", whitelist);
                    plugin.saveConfig();
                    plugin.refreshOnlineCommandTrees();
                    plugin.sendMsg(sender, "whitelist_add", "player", target);
                } else {
                    sendFormatMsg(sender, "&c&l操作失败 &#34495E&l► &#95A5A6&l该玩家已在白名单中！");
                }
            } else if (action.equals("remove")) {
                int targetIndex = -1;
                for (int index = 0; index < whitelist.size(); index++) {
                    if (whitelist.get(index).equalsIgnoreCase(target)) {
                        targetIndex = index;
                        break;
                    }
                }
                if (targetIndex >= 0) {
                    whitelist.remove(targetIndex);
                    plugin.getConfig().set("settings.bypass-whitelist", whitelist);
                    plugin.saveConfig();
                    plugin.refreshOnlineCommandTrees();
                    plugin.sendMsg(sender, "whitelist_remove", "player", target);
                } else {
                    sendFormatMsg(sender, "&c&l操作失败 &#34495E&l► &#95A5A6&l该玩家不在白名单中！");
                }
            } else {
                sendFormatMsg(sender, "&c&l格式错误 &#34495E&l► &#95A5A6&l用法: &f&l/kitloader whitelist <add/remove/list> [玩家名]");
            }
            return true;
        }

        if (subCmd.equals("edit")) {
            if (!sender.hasPermission("kitloader.admin")) {
                plugin.sendMsg(sender, "no_permission");
                return true;
            }
            if (!(sender instanceof Player player)) {
                plugin.sendMsg(sender, "player_only");
                return true;
            }
            if (args.length < 2) {
                sendFormatMsg(sender, "&c&l格式错误 &#34495E&l► &#95A5A6&l用法: &f&l/kitloader edit <分类>");
                return true;
            }
            String category = args[1];
            gui.openEditGui(player, category, 0);
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        boolean isNaming = false;
        if (sender instanceof Player player) {
            DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
            if (pData != null && pData.isNaming()) isNaming = true;
        }

        if (args.length == 1) {
            if (sender.hasPermission("kitloader.admin")) {
                completions.addAll(Arrays.asList("reload", "whitelist", "edit", "ecmax"));
            }
            String adminPermission = plugin.getConfig().getString("settings.admin-permission", "kitloader.admin");
            if (sender.isOp() || sender.hasPermission(adminPermission)) {
                completions.add("invmax");
            }
            if (isNaming) {
                completions.add("cancelname");
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if ((subCmd.equals("whitelist") || subCmd.equals("kitwhitelist")) && sender.hasPermission("kitloader.admin")) {
                completions.addAll(Arrays.asList("add", "remove", "list"));
            } else if (subCmd.equals("edit") && sender.hasPermission("kitloader.admin")) {
                completions.addAll(gui.getCategories());
            } else if (subCmd.equals("ecmax") || subCmd.equals("invmax")) {
                return new ArrayList<>();
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            if ((subCmd.equals("whitelist") || subCmd.equals("kitwhitelist")) && sender.hasPermission("kitloader.admin")) {
                if (args[1].equalsIgnoreCase("add")) {
                    completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                } else if (args[1].equalsIgnoreCase("remove")) {
                    completions.addAll(plugin.getConfig().getStringList("settings.bypass-whitelist"));
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
