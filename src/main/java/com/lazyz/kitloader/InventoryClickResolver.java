package com.lazyz.kitloader;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class InventoryClickResolver {
    private InventoryClickResolver() {
    }

    static boolean isTopClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        return rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize();
    }

    static boolean isBottomClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < topSize) return false;
        int convertedSlot = event.getView().convertSlot(rawSlot);
        return convertedSlot >= 0 && convertedSlot < event.getView().getBottomInventory().getSize();
    }

    static ItemStack clickedItem(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (isTopClick(event)) {
            return event.getView().getTopInventory().getItem(rawSlot);
        }
        if (isBottomClick(event)) {
            return event.getView().getBottomInventory().getItem(event.getView().convertSlot(rawSlot));
        }
        return event.getCurrentItem();
    }
}
