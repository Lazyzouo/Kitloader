package com.lazyz.kitloader;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerKitCommand implements CommandExecutor, TabCompleter {
    private final Kitloader plugin;
    private final DataManager dataManager;
    private final GuiManager guiManager;

    public PlayerKitCommand(Kitloader plugin, DataManager dataManager, GuiManager guiManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.guiManager = guiManager;
    }

    private void sendHelpMenu(CommandSender sender) {
        String adminPerm = plugin.getConfig().getString("settings.admin-permission", "kitloader.admin");
        if (sender.isOp() || sender.hasPermission(adminPerm)) {
            boolean showWhitelistCommands = sender instanceof Player player
                    && plugin.isBypassWhitelisted(player);
            String version = plugin.getDescription().getVersion();
            String author = plugin.getDescription().getAuthors().isEmpty()
                    ? "Unknown" : plugin.getDescription().getAuthors().get(0);
            for (String line : plugin.getMessageList("admin_help_menu")) {
                if (line == null || line.trim().isEmpty()) continue;
                if (!showWhitelistCommands
                        && (line.toLowerCase().contains("/inv ") || line.toLowerCase().contains("/regear "))) {
                    continue;
                }
                plugin.sendGameMessage(sender, line
                        .replace("{version}", version)
                        .replace("{author}", author));
            }
        } else {
            plugin.sendMsg(sender, "player_help_menu");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMsg(sender, "player_only");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMenu(player);
            return true;
        }

        String adminPerm = plugin.getConfig().getString("settings.admin-permission", "kitloader.admin");

        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            if (!player.isOp() && !player.hasPermission(adminPerm)) {
                plugin.sendGameMessage(player, "&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l你没有权限查看别人的Kit！");
                return true;
            }
            String targetName = args[0];
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
            DataManager.PlayerData targetData = dataManager.getOfflinePlayerData(target.getUniqueId());

            if (targetData != null && !targetData.kits.isEmpty()) {
                guiManager.openOtherPlayerKitListGui(player, targetName, new ArrayList<>(targetData.kits.keySet()));
            } else {
                plugin.sendGameMessage(player, "&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l玩家 &f&l" + targetName + " &#ff5e62&l没有任何保存的Kit！");
            }
            return true;
        }

        DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
        if (pData == null) {
            plugin.sendMsg(player, "data_loading");
            return true;
        }

        boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);
        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("list")) {
            if (pData.kits.isEmpty()) {
                plugin.sendMsg(player, "no_kits");
                return true;
            }
            guiManager.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet()));
            return true;
        }

        if (isRestricted && (subCmd.equals("save") || subCmd.equals("delete") || subCmd.equals("rename"))) {
            plugin.sendMsg(player, "restricted_action");
            return true;
        }

        if (isRestricted) {
            if (pData.hasUsed) {
                plugin.sendMsg(player, "single_use_limit");
                return true;
            }
        }

        if (subCmd.equals("rename") && args.length >= 3) {
            String oldName = args[1];
            String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

            CustomNamePolicy.NameValidation nameValidation =
                    CustomNamePolicy.validateKitName(plugin, newName);
            if (!nameValidation.valid()) {
                CustomNamePolicy.sendValidationFailure(plugin, player, nameValidation);
                return true;
            }
            if (!pData.kits.containsKey(oldName)) {
                plugin.sendMsg(player, "kit_not_found", "kit", oldName);
                return true;
            }
            if (pData.kits.containsKey(newName)) {
                plugin.sendGameMessage(player, "&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l已存在同名的Kit，请换一个名称！");
                return true;
            }

            ItemStack[] kitItems = pData.kits.remove(oldName);
            pData.kits.put(newName, kitItems);
            dataManager.savePlayerAsync(player.getUniqueId());
            plugin.sendGameMessage(player, "&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#00b09b&lKit已成功重命名为: &f&l" + newName);
            return true;
        }

        if (subCmd.equals("save") && args.length >= 2) {
            String kitName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            CustomNamePolicy.NameValidation nameValidation =
                    CustomNamePolicy.validateKitName(plugin, kitName);
            if (!nameValidation.valid()) {
                CustomNamePolicy.sendValidationFailure(plugin, player, nameValidation);
                return true;
            }
            boolean isEmpty = true;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    isEmpty = false; break;
                }
            }
            if (isEmpty) {
                plugin.sendMsg(player, "kit_save_empty");
                return true;
            }

            int maxKits = plugin.getConfig().getInt("settings.max-kits", 9);
            if (pData.kits.size() >= maxKits && !pData.kits.containsKey(kitName)) {
                plugin.sendMsg(player, "kit_limit_reached", "max", String.valueOf(maxKits), "kits", String.join(", ", pData.kits.keySet()));
                return true;
            }

            ItemStack[] contents = dataManager.copyItems(player.getInventory().getContents());
            int removedShulkers = plugin.enforceKitShulkerLimit(contents);
            if (removedShulkers > 0) plugin.sendMsg(player, "kit_shulker_trimmed",
                    "max", String.valueOf(plugin.getConfig().getInt("settings.shulker-limits.kit-save-max", 3)),
                    "removed", String.valueOf(removedShulkers));

            pData.kits.put(kitName, contents);
            dataManager.savePlayerAsync(player.getUniqueId());
            plugin.sendMsg(player, "kit_saved", "kit", kitName);
            return true;
        }

        if (subCmd.equals("delete") && args.length >= 2) {
            String kitName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (!pData.kits.containsKey(kitName)) {
                plugin.sendMsg(player, "kit_not_found", "kit", kitName);
                return true;
            }
            guiManager.cachePlayerTarget(player.getUniqueId(), kitName);
            guiManager.openConfirmDeletePlayerGui(player);
            return true;
        }

        String kitName = args[0];
        if (subCmd.equals("load") && args.length >= 2) {
            kitName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else if (!subCmd.equals("load") && pData.kits.containsKey(kitName)) {
            kitName = String.join(" ", args);
        }

        if (!pData.kits.containsKey(kitName)) {
            plugin.sendMsg(player, "kit_not_found", "kit", kitName);
            return true;
        }

        final String finalKitName = kitName;
        player.getScheduler().run(plugin, task -> {
            ItemStack[] contents = pData.kits.get(finalKitName);
            ItemStack[] cloned = new ItemStack[contents.length];

            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) {
                    cloned[i] = contents[i].clone();
                    plugin.markKitloaderShulker(cloned[i]);
                }
            }

            player.getInventory().clear();
            player.getInventory().setContents(cloned);
            dataManager.markKitLoaded(player);

            pData.hasUsed = true;
            dataManager.savePlayerAsync(player.getUniqueId());

            plugin.sendMsg(player, "kit_loaded", "kit", finalKitName);
        }, null);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return new ArrayList<>();
        DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
        if (pData != null && pData.isNaming()) return new ArrayList<>();

        String adminPerm = plugin.getConfig().getString("settings.admin-permission", "kitloader.admin");
        boolean isAdmin = player.isOp() || player.hasPermission(adminPerm);

        List<String> savedKits = pData != null ? new ArrayList<>(pData.kits.keySet()) : new ArrayList<>();

        boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("help");
            completions.add("list");
            completions.addAll(savedKits);

            if (!isRestricted) {
                completions.add("save");
                completions.add("delete");
                completions.add("rename");
                if (isAdmin) {
                    for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) completions.add(p.getName());
                }
            }
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            String subCmd = args[0].toLowerCase();

            if (subCmd.equals("load")) {
                return filterCompletions(savedKits, args[1]);
            }

            if (!isRestricted) {
                if (subCmd.equals("delete") || subCmd.equals("save") || subCmd.equals("rename")) {
                    return filterCompletions(savedKits, args[1]);
                }
                if (isAdmin) completions.add("list");
                return filterCompletions(completions, args[1]);
            }
        }
        return new ArrayList<>();
    }

    private List<String> filterCompletions(List<String> list, String prefix) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
