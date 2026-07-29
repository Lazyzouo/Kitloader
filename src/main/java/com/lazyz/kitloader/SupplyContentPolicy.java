package com.lazyz.kitloader;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.List;

final class SupplyContentPolicy {
    static final int REQUIRED_FILLED_SLOTS = 27;
    static final int MAX_SIMILAR_STACKS = 16;

    private SupplyContentPolicy() {
    }

    static ValidationResult validateSupply(ItemStack supply) {
        if (supply == null || !(supply.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return ValidationResult.INVALID_BOX;
        }
        return validateContents(box.getInventory().getContents());
    }

    static ValidationResult validateContents(ItemStack[] contents) {
        if (contents == null || contents.length < REQUIRED_FILLED_SLOTS) {
            return ValidationResult.NOT_FULL;
        }

        List<ItemGroup> groups = new ArrayList<>();
        for (int slot = 0; slot < REQUIRED_FILLED_SLOTS; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) return ValidationResult.NOT_FULL;

            ItemGroup matchingGroup = null;
            for (ItemGroup group : groups) {
                if (UploadContentMatcher.sameItemIgnoringDisplayName(group.sample, item)) {
                    matchingGroup = group;
                    break;
                }
            }
            if (matchingGroup == null) groups.add(new ItemGroup(item.clone()));
            else matchingGroup.stackCount++;
        }

        if (groups.size() == 1) return ValidationResult.ALL_SAME;
        for (ItemGroup group : groups) {
            if (group.stackCount > MAX_SIMILAR_STACKS) return ValidationResult.TOO_MANY_SIMILAR;
        }
        return ValidationResult.VALID;
    }

    enum ValidationResult {
        VALID,
        INVALID_BOX,
        NOT_FULL,
        ALL_SAME,
        TOO_MANY_SIMILAR
    }

    private static final class ItemGroup {
        private final ItemStack sample;
        private int stackCount = 1;

        private ItemGroup(ItemStack sample) {
            this.sample = sample;
        }
    }
}
