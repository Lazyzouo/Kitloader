package com.lazyz.kitloader;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Kitloader extends JavaPlugin {

    private static volatile LanguageManager activeLanguageManager;
    private DataManager dataManager;
    private GuiManager guiManager;
    private InventoryAdminCommand inventoryAdminCommand;
    private RegearCommand regearCommand;
    private LanguageManager languageManager;
    private UpdateChecker updateChecker;

    private static final String LEGACY_TRACK_TAG = "§r§0§k§r";
    private static final int STARTUP_BANNER_WIDTH = 60;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        languageManager = new LanguageManager(this);
        activeLanguageManager = languageManager;
        migrateLegacyRestrictedWorldName();
        migrateLegacyInventoryWhitelist();

        dataManager = new DataManager(this);
        guiManager = new GuiManager(this, dataManager);

        PlayerKitCommand kitCmd = new PlayerKitCommand(this, dataManager, guiManager);
        if (getCommand("kit") != null) {
            getCommand("kit").setExecutor(kitCmd);
            getCommand("kit").setTabCompleter(kitCmd);
        }

        KitloaderCommand kitloaderCmd = new KitloaderCommand(this, guiManager, dataManager);
        if (getCommand("kitloader") != null) {
            getCommand("kitloader").setExecutor(kitloaderCmd);
            getCommand("kitloader").setTabCompleter(kitloaderCmd);
        }

        inventoryAdminCommand = new InventoryAdminCommand(this);
        if (getCommand("inv") != null) {
            getCommand("inv").setExecutor(inventoryAdminCommand);
            getCommand("inv").setTabCompleter(inventoryAdminCommand);
        }

        regearCommand = new RegearCommand(this, dataManager, guiManager);
        if (getCommand("regear") != null) {
            getCommand("regear").setExecutor(regearCommand);
            getCommand("regear").setTabCompleter(regearCommand);
        }

        getServer().getPluginManager().registerEvents(new KitListener(this, dataManager, guiManager), this);
        getServer().getPluginManager().registerEvents(inventoryAdminCommand, this);
        getServer().getPluginManager().registerEvents(regearCommand, this);

        startShulkerLimitTracker();
        printStartupBanner();
        logLocalized("plugin_enabled");
        updateChecker = new UpdateChecker(this);
        updateChecker.checkOnStartup();
    }

    @Override
    public void onDisable() {
        if (inventoryAdminCommand != null) inventoryAdminCommand.shutdown();
        if (regearCommand != null) regearCommand.shutdown();
        if (activeLanguageManager == languageManager) activeLanguageManager = null;
    }

    public static String color(String msg) {
        if (msg == null) return null;
        LanguageManager manager = activeLanguageManager;
        if (manager != null) msg = manager.translateInline(msg);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("&#[a-fA-F0-9]{6}");
        java.util.regex.Matcher matcher = pattern.matcher(msg);
        while (matcher.find()) {
            String hexCode = msg.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace("&#", "x");
            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : ch) {
                builder.append("&").append(c);
            }
            msg = msg.replace(hexCode, builder.toString());
            matcher = pattern.matcher(msg);
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static String canonicalize(String text) {
        LanguageManager manager = activeLanguageManager;
        return manager == null ? text : manager.canonicalize(text);
    }

    public boolean isKitloaderShulker(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("SHULKER_BOX");
    }

    public boolean markKitloaderShulker(ItemStack item) {
        if (item == null || !item.getType().name().endsWith("SHULKER_BOX")) return false;
        boolean modified = guiManager != null && guiManager.stripUploadedSupplyMetadata(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return modified;

        if (meta.hasDisplayName() && meta.getDisplayName().contains(LEGACY_TRACK_TAG)) {
            meta.setDisplayName(meta.getDisplayName().replace(LEGACY_TRACK_TAG, ""));
            modified = true;
        }
        if (meta.hasLore()) {
            List<String> cleanedLore = new ArrayList<>();
            for (String line : meta.getLore()) {
                if (line == null || !line.contains(LEGACY_TRACK_TAG)) {
                    cleanedLore.add(line);
                    continue;
                }
                String cleaned = line.replace(LEGACY_TRACK_TAG, "");
                if (!org.bukkit.ChatColor.stripColor(cleaned).isBlank()) cleanedLore.add(cleaned);
                modified = true;
            }
            if (modified) meta.setLore(cleanedLore.isEmpty() ? null : cleanedLore);
        }
        if (modified) item.setItemMeta(meta);
        return modified;
    }

    public void sanitizePlayerShulkers(Player player) {
        int removedItems = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            CustomNamePolicy.CleanupResult cleanup = CustomNamePolicy.sanitizeItem(item);
            removedItems += cleanup.removedItems();
            if (cleanup.removeRoot()) {
                inventory.setItem(slot, null);
            } else if (cleanup.changed() || markKitloaderShulker(item)) {
                inventory.setItem(slot, item);
            }
        }

        Inventory enderChest = player.getEnderChest();
        for (int slot = 0; slot < enderChest.getSize(); slot++) {
            ItemStack item = enderChest.getItem(slot);
            CustomNamePolicy.CleanupResult cleanup = CustomNamePolicy.sanitizeItem(item);
            removedItems += cleanup.removedItems();
            if (cleanup.removeRoot()) {
                enderChest.setItem(slot, null);
            } else if (cleanup.changed() || markKitloaderShulker(item)) {
                enderChest.setItem(slot, item);
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        CustomNamePolicy.CleanupResult cursorCleanup = CustomNamePolicy.sanitizeItem(cursor);
        removedItems += cursorCleanup.removedItems();
        if (cursorCleanup.removeRoot()) {
            player.setItemOnCursor(null);
        } else if (cursorCleanup.changed() || markKitloaderShulker(cursor)) {
            player.setItemOnCursor(cursor);
        }

        if (removedItems > 0) {
            sendMsg(player, "custom_name_items_removed", "removed", String.valueOf(removedItems));
        }
    }

    public boolean isRestrictedKitloaderPlayer(Player player) {
        boolean restrictedWorld = getConfig().getStringList("settings.single-use-worlds").stream()
                .anyMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()));
        return restrictedWorld && !isBypassWhitelisted(player);
    }

    public boolean isBypassWhitelisted(Player player) {
        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        return getConfig().getStringList("settings.bypass-whitelist").stream()
                .anyMatch(entry -> entry.equalsIgnoreCase(name) || entry.equalsIgnoreCase(uuid));
    }

    public void refreshOnlineCommandTrees() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshCommandTree(player);
        }
    }

    public void refreshCommandTree(Player player) {
        if (player == null) return;
        player.getScheduler().run(this, task -> {
            if (player.isOnline()) player.updateCommands();
        }, null);
    }

    GuiManager getGuiManager() {
        return guiManager;
    }

    private void migrateLegacyRestrictedWorldName() {
        List<String> worlds = new ArrayList<>(getConfig().getStringList("settings.single-use-worlds"));
        boolean hasOverworld = worlds.stream().anyMatch(world -> world.equalsIgnoreCase("overworld"));
        if (hasOverworld) return;

        for (int index = 0; index < worlds.size(); index++) {
            if (!worlds.get(index).equalsIgnoreCase("overworld2")) continue;
            worlds.set(index, "overworld");
            getConfig().set("settings.single-use-worlds", worlds);
            saveConfig();
            return;
        }
    }

    private void migrateLegacyInventoryWhitelist() {
        if (!getConfig().contains("settings.inventory-editor.whitelist")) return;

        List<String> bypassWhitelist = new ArrayList<>(getConfig().getStringList("settings.bypass-whitelist"));
        for (String legacyEntry : getConfig().getStringList("settings.inventory-editor.whitelist")) {
            boolean alreadyPresent = bypassWhitelist.stream()
                    .anyMatch(entry -> entry.equalsIgnoreCase(legacyEntry));
            if (!alreadyPresent) bypassWhitelist.add(legacyEntry);
        }
        getConfig().set("settings.bypass-whitelist", bypassWhitelist);
        getConfig().set("settings.inventory-editor", null);
        saveConfig();
    }

    public int enforceKitShulkerLimit(ItemStack[] items) {
        if (items == null) return 0;
        int max = Math.max(0, getConfig().getInt("settings.shulker-limits.kit-save-max", 3));
        int kept = 0;
        int removed = 0;
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (!isKitloaderShulker(item)) continue;

            int amount = item.getAmount();
            int remaining = Math.max(0, max - kept);
            if (remaining >= amount) {
                kept += amount;
            } else if (remaining > 0) {
                ItemStack limited = item.clone();
                limited.setAmount(remaining);
                items[index] = limited;
                kept += remaining;
                removed += amount - remaining;
            } else {
                items[index] = null;
                removed += amount;
            }
        }
        return removed;
    }

    private void startShulkerLimitTracker() {
        getServer().getAsyncScheduler().runAtFixedRate(this, t -> {
            try {
                if (!getConfig().getBoolean("settings.shulker-limits.enabled", true)) return;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.getScheduler().run(this, task -> {
                        try {
                            if (p.isOnline()) checkPlayerShulkers(p);
                        } catch (Exception ex) {}
                    }, null);
                }
            } catch (Exception e) {}
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void checkPlayerShulkers(Player p) {
        sanitizePlayerShulkers(p);
        boolean modifiedInv = false;
        boolean modifiedEc = false;

        List<String> specialWorlds = getConfig().getStringList("settings.shulker-limits.special-limit-worlds");
        boolean inSpecialWorld = specialWorlds.contains(p.getWorld().getName());

        int ecLimit = getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
        int invLimit = inSpecialWorld ? ecLimit : getConfig().getInt("settings.shulker-limits.inventory-max", 3);

        int ecCount = 0;
        Inventory ec = p.getEnderChest();
        for (int i = 0; i < ec.getSize(); i++) {
            ItemStack item = ec.getItem(i);
            if (isKitloaderShulker(item)) {
                ecCount += item.getAmount();
                if (ecCount > ecLimit) {
                    ec.setItem(i, null);
                    modifiedEc = true;
                }
            }
        }
        if (modifiedEc) {
            if (p.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) p.closeInventory();
            sendMsg(p, "shulker_limit_enderchest", "max", String.valueOf(ecLimit));
        }

        if (isBypassWhitelisted(p)) return;

        int invCount = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isKitloaderShulker(item)) {
                invCount += item.getAmount();
                if (invCount > invLimit) {
                    inv.setItem(i, null);
                    modifiedInv = true;
                }
            }
        }

        ItemStack cursor = p.getItemOnCursor();
        if (isKitloaderShulker(cursor)) {
            invCount += cursor.getAmount();
            if (invCount > invLimit) {
                p.setItemOnCursor(null);
                modifiedInv = true;
            }
        }
        if (modifiedInv) sendMsg(p, "shulker_limit_inventory", "max", String.valueOf(invLimit));
    }

    public void reloadPlugin() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) saveDefaultConfig();
        reloadConfig();
        languageManager.reload();
        guiManager.loadGuiConfig();
    }

    public String getGuiTitle(String key, String def) {
        return color(languageManager.getGuiString(key, def));
    }

    public List<String> getGuiStringList(String key) {
        return languageManager.getGuiStringList(key);
    }

    public List<String> getMessageList(String key) {
        return languageManager.getMessageList(key);
    }

    public String getMessageString(String key, String fallback) {
        return languageManager.getMessageString(key, fallback);
    }

    static String leftAlignGameText(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder formatting = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == '\u00a7' && index + 1 < text.length()) {
                formatting.append(text, index, index + 2);
                index += 2;
                continue;
            }

            int codePoint = text.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) break;
            index += Character.charCount(codePoint);
        }
        return formatting.append(text, index, text.length()).toString();
    }

    public void sendGameMessage(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        String colored = color(message);
        sender.sendMessage(sender instanceof Player ? leftAlignGameText(colored) : colored);
    }

    public void sendMsg(CommandSender sender, String key, String... placeholders) {
        if (sender == null) return;
        String prefix = color(languageManager.getMessageString("prefix", ""));
        String author = getDescription().getAuthors().isEmpty() ? "Unknown" : getDescription().getAuthors().get(0);

        Object configured = languageManager.getMessage(key);
        if (configured instanceof List<?>) {
            List<String> list = languageManager.getMessageList(key);
            if (list.isEmpty()) return;
            for (String line : list) {
                if (line == null || line.trim().isEmpty()) continue;
                line = applyPlaceholders(line, placeholders);
                line = line.replace("{prefix}", prefix).replace("{version}", getDescription().getVersion()).replace("{author}", author);
                sendGameMessage(sender, line);
            }
        } else {
            String msg = configured instanceof String value ? value : null;
            if (msg == null || msg.trim().isEmpty()) return;
            msg = applyPlaceholders(msg, placeholders);
            msg = msg.replace("{prefix}", prefix).replace("{version}", getDescription().getVersion()).replace("{author}", author);
            for (String line : msg.split("\\n")) {
                sendGameMessage(sender, line);
            }
        }
    }

    public void logLocalized(String key, String... placeholders) {
        String message = languageManager.getMessageString(key, key);
        message = applyPlaceholders(message, placeholders)
                .replace("{version}", getDescription().getVersion())
                .replace("{author}", getDescription().getAuthors().isEmpty()
                        ? "Unknown"
                        : getDescription().getAuthors().get(0))
                .replace("{prefix}", "");
        String statusColor = switch (key) {
            case "update_checking" -> "&b";
            case "plugin_enabled", "update_latest", "update_downloaded" -> "&a";
            case "update_available", "update_manual" -> "&e";
            case "update_failed" -> "&c";
            default -> "&7";
        };
        logConsole(statusColor + message);
    }

    private void printStartupBanner() {
        String version = getDescription().getVersion();
        String author = getDescription().getAuthors().isEmpty()
                ? "Unknown"
                : getDescription().getAuthors().get(0);

        logConsole(bannerBorder('='));
        logConsole(bannerCentered("KITLOADER LOADOUT SERVICE v" + version, "&b&l"));
        logConsole(bannerCentered("KIT LOADOUT MANAGEMENT / KIT 配装管理", "&f&l"));
        logConsole(bannerBorder('-'));
        logConsole(bannerDetail("Version / 版本", version, "&a"));
        logConsole(bannerDetail("Author  / 作者", author, "&e"));
        logConsole(bannerDetail("Tested  / 测试", "Paper & Folia 1.21.11", "&a"));
        logConsole(bannerDetail("Language/ 语言", languageManager.getLanguage(), "&b"));
        logConsole(bannerDetail("GitHub        ", UpdateChecker.REPOSITORY_URL, "&9"));
        logConsole(bannerMessage(
                "&aOpen source. &fNo telemetry or server-data upload.",
                "Open source. No telemetry or server-data upload."));
        logConsole(bannerBorder('='));
    }

    private void logConsole(String message) {
        getServer().getConsoleSender().sendMessage(color("&8[&bKitloader&8] &r" + message));
    }

    private String bannerBorder(char fill) {
        return "&3&l+" + String.valueOf(fill).repeat(STARTUP_BANNER_WIDTH) + "+";
    }

    private String bannerCentered(String text, String textColor) {
        int textWidth = displayWidth(text);
        int leftPadding = Math.max(0, (STARTUP_BANNER_WIDTH - textWidth) / 2);
        int rightPadding = Math.max(0, STARTUP_BANNER_WIDTH - textWidth - leftPadding);
        return "&3&l|" + " ".repeat(leftPadding) + textColor + text
                + "&3&l" + " ".repeat(rightPadding) + "|";
    }

    private String bannerDetail(String label, String value, String valueColor) {
        String plainText = label + " : " + value;
        int rightPadding = Math.max(0, STARTUP_BANNER_WIDTH - 1 - displayWidth(plainText));
        return "&3&l| &f" + label + " &8: " + valueColor + value
                + "&3&l" + " ".repeat(rightPadding) + "|";
    }

    private String bannerMessage(String coloredText, String plainText) {
        int rightPadding = Math.max(0, STARTUP_BANNER_WIDTH - 1 - displayWidth(plainText));
        return "&3&l| " + coloredText + "&3&l" + " ".repeat(rightPadding) + "|";
    }

    private int displayWidth(String text) {
        int width = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            width += script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL ? 2 : 1;
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    private String applyPlaceholders(String text, String... placeholders) {
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i + 1] != null ? placeholders[i + 1] : "");
            }
        }
        return text;
    }

    public DataManager getDataManager() { return dataManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
}
