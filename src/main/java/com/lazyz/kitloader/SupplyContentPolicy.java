package com.lazyz.kitloader;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.List;

final class SupplyContentPolicy {
    static final int DEFAULT_REQUIRED_FILLED_SLOTS = 27;
    static final int DEFAULT_MAX_SIMILAR_STACKS = 16;
    private static final int MAX_SUPPLY_SLOTS = 27;

    private SupplyContentPolicy() {
    }

    static Rules rules(Kitloader plugin) {
        int requiredFilledSlots = clamp(
                plugin.getConfig().getInt("settings.custom-supply.content-policy.required-filled-slots",
                        DEFAULT_REQUIRED_FILLED_SLOTS),
                1, MAX_SUPPLY_SLOTS);
        int maxSimilarStacks = clamp(
                plugin.getConfig().getInt("settings.custom-supply.content-policy.max-similar-stacks",
                        DEFAULT_MAX_SIMILAR_STACKS),
                1, MAX_SUPPLY_SLOTS);
        boolean rejectAllSame = plugin.getConfig().getBoolean(
                "settings.custom-supply.content-policy.reject-all-same", true);
        return new Rules(requiredFilledSlots, rejectAllSame, maxSimilarStacks);
    }

    static ValidationResult validateSupply(Kitloader plugin, ItemStack supply) {
        if (supply == null || !(supply.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return ValidationResult.INVALID_BOX;
        }
        return validateContents(box.getInventory().getContents(), rules(plugin));
    }

    static ValidationResult validateContents(Kitloader plugin, ItemStack[] contents) {
        return validateContents(contents, rules(plugin));
    }

    static ValidationResult validateContents(ItemStack[] contents, Rules rules) {
        if (contents == null) return ValidationResult.NOT_FULL;

        List<ItemGroup> groups = new ArrayList<>();
        int filledSlots = 0;
        int slots = Math.min(MAX_SUPPLY_SLOTS, contents.length);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;
            filledSlots++;

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

        if (filledSlots < rules.requiredFilledSlots()) return ValidationResult.NOT_FULL;
        if (rules.rejectAllSame() && groups.size() == 1) return ValidationResult.ALL_SAME;
        for (ItemGroup group : groups) {
            if (group.stackCount > rules.maxSimilarStacks()) return ValidationResult.TOO_MANY_SIMILAR;
        }
        return ValidationResult.VALID;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Rules(int requiredFilledSlots, boolean rejectAllSame, int maxSimilarStacks) {
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
