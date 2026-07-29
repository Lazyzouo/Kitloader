package com.lazyz.kitloader;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class UploadContentMatcher {
    private UploadContentMatcher() {
    }

    static boolean sameKitContents(ItemStack[] first, ItemStack[] second) {
        return sameItemCollection(first, second);
    }

    static boolean sameSupplyContents(ItemStack first, ItemStack second) {
        ItemStack[] firstContents = shulkerContents(first);
        ItemStack[] secondContents = shulkerContents(second);
        return firstContents != null && secondContents != null
                && sameItemCollection(firstContents, secondContents);
    }

    private static ItemStack[] shulkerContents(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) return null;
        return box.getInventory().getContents();
    }

    private static boolean sameItemCollection(ItemStack[] first, ItemStack[] second) {
        List<StackGroup> firstGroups = groupItems(first);
        List<StackGroup> secondGroups = groupItems(second);
        if (firstGroups.size() != secondGroups.size()) return false;

        boolean[] matched = new boolean[secondGroups.size()];
        for (StackGroup firstGroup : firstGroups) {
            boolean found = false;
            for (int index = 0; index < secondGroups.size(); index++) {
                StackGroup secondGroup = secondGroups.get(index);
                if (!matched[index] && firstGroup.amount == secondGroup.amount
                        && firstGroup.item.isSimilar(secondGroup.item)) {
                    matched[index] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static List<StackGroup> groupItems(ItemStack[] items) {
        List<StackGroup> groups = new ArrayList<>();
        if (items == null) return groups;
        for (ItemStack source : items) {
            if (source == null || source.getType().isAir()) continue;
            ItemStack normalized = normalizeItem(source);
            StackGroup existing = null;
            for (StackGroup group : groups) {
                if (group.item.isSimilar(normalized)) {
                    existing = group;
                    break;
                }
            }
            if (existing == null) groups.add(new StackGroup(normalized, source.getAmount()));
            else existing.amount += source.getAmount();
        }
        return groups;
    }

    private static ItemStack normalizeItem(ItemStack source) {
        ItemStack normalized = source.clone();
        ItemMeta rawMeta = normalized.getItemMeta();
        if (rawMeta == null) return normalized;

        rawMeta.setDisplayName(null);
        if (rawMeta instanceof BlockStateMeta blockMeta
                && blockMeta.getBlockState() instanceof ShulkerBox box) {
            ItemStack[] contents = box.getInventory().getContents();
            ItemStack[] normalizedContents = new ItemStack[contents.length];
            for (int index = 0; index < contents.length; index++) {
                ItemStack item = contents[index];
                normalizedContents[index] = item == null || item.getType().isAir()
                        ? null : normalizeItem(item);
            }
            box.getInventory().setContents(normalizedContents);
            blockMeta.setBlockState(box);
        }
        normalized.setItemMeta(rawMeta);
        return normalized;
    }

    private static final class StackGroup {
        private final ItemStack item;
        private long amount;

        private StackGroup(ItemStack item, long amount) {
            this.item = item;
            this.amount = amount;
        }
    }
}
