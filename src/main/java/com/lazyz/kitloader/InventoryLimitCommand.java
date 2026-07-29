package com.lazyz.kitloader;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class InventoryLimitCommand implements CommandExecutor, TabCompleter {
    private final Kitloader plugin;

    public InventoryLimitCommand(Kitloader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, args);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (sender instanceof Player player && plugin.isRestrictedKitloaderPlayer(player)) {
            plugin.sendMsg(player, "restricted_command");
            return true;
        }
        String adminPermission = plugin.getConfig().getString("settings.admin-permission", "kitloader.admin");
        if (!sender.isOp() && !sender.hasPermission(adminPermission)) {
            plugin.sendMsg(sender, "no_permission");
            return true;
        }
        if (args.length != 1) {
            plugin.sendMsg(sender, "invmax_usage");
            return true;
        }

        try {
            int max = Integer.parseInt(args[0]);
            if (max < 1 || max > 36) {
                plugin.sendMsg(sender, "invmax_invalid_range");
                return true;
            }
            plugin.getConfig().set("settings.shulker-limits.inventory-max", max);
            plugin.getConfig().set("settings.shulker-limits.kit-save-max", max);
            plugin.saveConfig();
            plugin.sendMsg(sender, "invmax_set_success", "max", String.valueOf(max));
        } catch (NumberFormatException ignored) {
            plugin.sendMsg(sender, "invalid_number");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
