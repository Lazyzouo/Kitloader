package com.lazyz.kitloader;

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
    static final int MAX_DISPLAY_NAME_LENGTH = 399;
    private static final String KIT_LABEL_PREFIX = "&#F2C94C&l";
    private static final int MAX_NESTED_DEPTH = 64;

    private CustomNamePolicy() {
    }

    static boolean isValidColoredDisplayName(String coloredName) {
        return coloredName != null && coloredName.length() <= MAX_DISPLAY_NAME_LENGTH;
    }

    static boolean isValidKitName(String kitName) {
        if (kitName == null || kitName.isBlank()) return false;
        return isValidColoredDisplayName(Kitloader.color(KIT_LABEL_PREFIX + kitName));
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
}
