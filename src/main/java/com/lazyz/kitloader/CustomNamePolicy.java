package com.lazyz.kitloader;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class CustomNamePolicy {
    static final int MAX_EXPANDED_NAME_LENGTH = 399;
    static final int DEFAULT_ITEM_VISIBLE_LENGTH = 40;
    static final int DEFAULT_KIT_VISIBLE_LENGTH = 18;
    static final int DEFAULT_SUPPLY_VISIBLE_LENGTH = 18;
    private static final String KIT_LABEL_PREFIX = "&#F2C94C&l";
    private static final int MAX_NESTED_DEPTH = 64;

    private CustomNamePolicy() {
    }

    static NameValidation validateItemName(Kitloader plugin, String coloredName) {
        return validateColoredName(plugin, coloredName, NameType.ITEM);
    }

    static NameValidation validateSupplyName(Kitloader plugin, String coloredName) {
        return validateColoredName(plugin, coloredName, NameType.SUPPLY);
    }

    static NameValidation validateKitName(Kitloader plugin, String kitName) {
        if (kitName == null || kitName.isBlank()) {
            return new NameValidation(false, 0, maxVisibleLength(plugin, NameType.KIT), 0);
        }
        return validateColoredName(plugin, Kitloader.color(KIT_LABEL_PREFIX + kitName), NameType.KIT);
    }

    static boolean isValidKitName(Kitloader plugin, String kitName) {
        return validateKitName(plugin, kitName).valid();
    }

    static boolean isValidSupplyName(Kitloader plugin, String coloredName) {
        return validateSupplyName(plugin, coloredName).valid();
    }

    static int maxVisibleLength(Kitloader plugin, NameType type) {
        String path = switch (type) {
            case ITEM -> "settings.naming.item-max-visible-length";
            case KIT -> "settings.naming.kit-max-visible-length";
            case SUPPLY -> "settings.naming.supply-max-visible-length";
        };
        int fallback = switch (type) {
            case ITEM -> DEFAULT_ITEM_VISIBLE_LENGTH;
            case KIT -> DEFAULT_KIT_VISIBLE_LENGTH;
            case SUPPLY -> DEFAULT_SUPPLY_VISIBLE_LENGTH;
        };
        return Math.max(1, Math.min(MAX_EXPANDED_NAME_LENGTH,
                plugin.getConfig().getInt(path, fallback)));
    }

    private static NameValidation validateColoredName(Kitloader plugin, String coloredName, NameType type) {
        int maxVisible = maxVisibleLength(plugin, type);
        if (coloredName == null) return new NameValidation(false, 0, maxVisible, 0);

        String visibleName = ChatColor.stripColor(coloredName);
        int visibleLength = visibleName == null ? 0
                : visibleName.codePointCount(0, visibleName.length());
        int expandedLength = coloredName.length();
        boolean valid = visibleLength > 0 && visibleLength <= maxVisible
                && expandedLength <= MAX_EXPANDED_NAME_LENGTH;
        return new NameValidation(valid, visibleLength, maxVisible, expandedLength);
    }

    static void sendValidationFailure(Kitloader plugin, CommandSender sender, NameValidation validation) {
        if (validation.empty()) {
            plugin.sendMsg(sender, "name_empty");
            return;
        }
        if (validation.visibleTooLong()) {
            plugin.sendMsg(sender, "name_too_long",
                    "current", String.valueOf(validation.visibleLength()),
                    "max", String.valueOf(validation.maxVisibleLength()));
            return;
        }
        plugin.sendMsg(sender, "name_expanded_too_long",
                "current", String.valueOf(validation.expandedLength()),
                "max", String.valueOf(MAX_EXPANDED_NAME_LENGTH));
    }


    static String safeDefaultSupplyName(Kitloader plugin) {
        String configured = Kitloader.color(plugin.getConfig().getString(
                "settings.custom-supply.default-box-name", "Supply Box"));
        if (validateSupplyName(plugin, configured).valid()) return configured;

        int maxLength = maxVisibleLength(plugin, NameType.SUPPLY);
        String fallback = "Supply Box";
        if (fallback.codePointCount(0, fallback.length()) <= maxLength) return fallback;

        int endIndex = fallback.offsetByCodePoints(0, maxLength);
        return fallback.substring(0, endIndex);
    }

    static CleanupResult sanitizeItems(ItemStack[] items) {
        CleanupResult total = new CleanupResult(false, false, 0);
        if (items == null) return total;

        for (int index = 0; index < items.length; index++) {
            CleanupResult result = sanitizeItem(items[index]);
            if (result.removeRoot()) items[index] = null;
            total = total.merge(result);
        }
        return total;
    }

    static CleanupResult sanitizeItem(ItemStack item) {
        return sanitizeItem(item, 0);
    }

    private static CleanupResult sanitizeItem(ItemStack item, int depth) {
        if (item == null || item.getType().isAir()) return new CleanupResult(false, false, 0);
        if (depth > MAX_NESTED_DEPTH) return new CleanupResult(false, false, 0);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new CleanupResult(false, false, 0);
        if (meta.hasDisplayName() && meta.getDisplayName().length() >= 400) {
            return new CleanupResult(true, true, 1);
        }

        boolean changed = false;
        int removedItems = 0;

        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof Container container) {
                Inventory inventory = container.getSnapshotInventory();
                ItemStack[] contents = inventory.getContents();
                boolean containerChanged = false;
                for (int index = 0; index < contents.length; index++) {
                    CleanupResult nested = sanitizeItem(contents[index], depth + 1);
                    if (nested.removeRoot()) contents[index] = null;
                    containerChanged |= nested.changed();
                    removedItems += nested.removedItems();
                }
                if (containerChanged) {
                    inventory.setContents(contents);
                    blockStateMeta.setBlockState(container);
                    changed = true;
                }
            }
        }

        if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
            List<ItemStack> cleanItems = new ArrayList<>();
            boolean bundleChanged = false;
            for (ItemStack nestedItem : bundleMeta.getItems()) {
                CleanupResult nested = sanitizeItem(nestedItem, depth + 1);
                if (!nested.removeRoot()) cleanItems.add(nestedItem);
                bundleChanged |= nested.changed();
                removedItems += nested.removedItems();
            }
            if (bundleChanged) {
                bundleMeta.setItems(cleanItems);
                changed = true;
            }
        }

        if (changed) item.setItemMeta(meta);
        return new CleanupResult(false, changed, removedItems);
    }

    record CleanupResult(boolean removeRoot, boolean changed, int removedItems) {
        private CleanupResult merge(CleanupResult other) {
            return new CleanupResult(removeRoot || other.removeRoot,
                    changed || other.changed,
                    removedItems + other.removedItems);
        }
    }

    enum NameType {
        ITEM,
        KIT,
        SUPPLY
    }

    record NameValidation(boolean valid, int visibleLength, int maxVisibleLength, int expandedLength) {
        boolean empty() {
            return visibleLength == 0;
        }

        boolean visibleTooLong() {
            return visibleLength > maxVisibleLength;
        }
    }
}
