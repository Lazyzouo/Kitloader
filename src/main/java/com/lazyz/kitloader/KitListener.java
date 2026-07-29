package com.lazyz.kitloader;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class KitListener implements Listener {
    private final Kitloader plugin;
    private final DataManager data;
    private final GuiManager gui;
    private final Set<UUID> templatePickupLocks = ConcurrentHashMap.newKeySet();

    public KitListener(Kitloader plugin, DataManager data, GuiManager gui) {
        this.plugin = plugin;
        this.data = data;
        this.gui = gui;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        String cleaned = ChatColor.stripColor(text);
        cleaned = cleaned.replaceAll("(?i)§x(§[0-9a-f]){6}", "");
        cleaned = cleaned.replaceAll("(?i)&#[0-9a-f]{6}", "");
        cleaned = cleaned.replaceAll("(?i)[&§][0-9a-fk-or]", "");
        return Kitloader.canonicalize(cleaned.trim());
    }

    private boolean isRestrictedKitloaderGui(String cleanTitle) {
        String categoryPrefix = cleanText(plugin.getGuiTitle("category-prefix", ""));
        String editPrefix = cleanText(plugin.getGuiTitle("edit-prefix", ""));
        return (!categoryPrefix.isEmpty() && cleanTitle.startsWith(categoryPrefix))
                || (!editPrefix.isEmpty() && cleanTitle.startsWith(editPrefix))
                || cleanTitle.contains("自定义补给盒")
                || cleanTitle.contains("上传补给")
                || cleanTitle.contains("已上传的补给")
                || cleanTitle.contains("共享Kit")
                || cleanTitle.contains("末影箱直存模式")
                || cleanTitle.contains("专属末影箱")
                || cleanTitle.contains("附魔与物品编辑")
                || cleanTitle.contains("盔甲纹饰与名称")
                || cleanTitle.contains("在线玩家背包管理")
                || cleanTitle.contains("背包与装备")
                || cleanTitle.contains("末影箱编辑");
    }

    private boolean closeRestrictedKitloaderGui(Player player, String cleanTitle) {
        if (!plugin.isRestrictedKitloaderPlayer(player) || !isRestrictedKitloaderGui(cleanTitle)) return false;
        if (player.getOpenInventory().getTopInventory().getHolder() == null) gui.setSkipNextClose(player);
        player.closeInventory();
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
        if (pData != null) {
            pData.clearNaming();
            pData.editItemSession = null;
        }
        plugin.sendMsg(player, "restricted_command");
        return true;
    }

    private boolean isTrashPane(ItemStack item) {
        return item != null && item.getType() == Material.RED_STAINED_GLASS_PANE
                && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && cleanText(item.getItemMeta().getDisplayName()).contains("物品销毁区");
    }

    private org.bukkit.inventory.meta.trim.TrimPattern getPatternFromMaterial(Material mat) {
        String key = mat.name().toLowerCase().replace("_armor_trim_smithing_template", "");
        return Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(key));
    }

    private org.bukkit.inventory.meta.trim.TrimMaterial getTrimMaterialFromItem(Material mat) {
        String key = null;
        switch(mat) {
            case IRON_INGOT: key = "iron"; break;
            case COPPER_INGOT: key = "copper"; break;
            case GOLD_INGOT: key = "gold"; break;
            case LAPIS_LAZULI: key = "lapis"; break;
            case EMERALD: key = "emerald"; break;
            case DIAMOND: key = "diamond"; break;
            case NETHERITE_INGOT: key = "netherite"; break;
            case REDSTONE: key = "redstone"; break;
            case AMETHYST_SHARD: key = "amethyst"; break;
            case QUARTZ: key = "quartz"; break;
            default: break;
        }
        if (key != null) return Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(key));
        return null;
    }

    private void applyTrim(ItemStack item, org.bukkit.inventory.meta.trim.TrimPattern pattern, org.bukkit.inventory.meta.trim.TrimMaterial material) {
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta instanceof org.bukkit.inventory.meta.ArmorMeta) {
            org.bukkit.inventory.meta.ArmorMeta armorMeta = (org.bukkit.inventory.meta.ArmorMeta) meta;
            org.bukkit.inventory.meta.trim.ArmorTrim currentTrim = armorMeta.getTrim();

            org.bukkit.inventory.meta.trim.TrimPattern p = pattern != null ? pattern : (currentTrim != null ? currentTrim.getPattern() : getRandomTrimPattern());
            org.bukkit.inventory.meta.trim.TrimMaterial m = material != null ? material : (currentTrim != null ? currentTrim.getMaterial() : getRandomTrimMaterial());

            if (p != null && m != null) {
                armorMeta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(m, p));
                item.setItemMeta(armorMeta);
            }
        }
    }

    private org.bukkit.inventory.meta.trim.TrimPattern getRandomTrimPattern() {
        Material template = GuiManager.TRIM_PATTERNS[ThreadLocalRandom.current().nextInt(GuiManager.TRIM_PATTERNS.length)];
        return getPatternFromMaterial(template);
    }

    private org.bukkit.inventory.meta.trim.TrimMaterial getRandomTrimMaterial() {
        Material material = GuiManager.TRIM_MATERIALS[ThreadLocalRandom.current().nextInt(GuiManager.TRIM_MATERIALS.length)];
        return getTrimMaterialFromItem(material);
    }

    private void rejectEnchant(Player player, DataManager.PlayerData pData, String messageKey, String enchantName) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("settings.enchantments.rejection-cooldown-ms", 1500L);
        if (now - pData.lastEnchantRejectTime < Math.max(0L, cooldown)) return;

        pData.lastEnchantRejectTime = now;
        plugin.sendMsg(player, messageKey, "enchant", enchantName);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1.0f);
    }

    private ItemStack[] extractKitFromEditGui(Inventory inv) {
        ItemStack[] bkKit = new ItemStack[41];
        for (int i = 0; i <= 26; i++) bkKit[i + 9] = inv.getItem(i);
        for (int i = 27; i <= 35; i++) bkKit[i - 27] = inv.getItem(i);
        bkKit[39] = inv.getItem(36); bkKit[38] = inv.getItem(37);
        bkKit[37] = inv.getItem(38); bkKit[36] = inv.getItem(39);
        bkKit[40] = inv.getItem(40);
        return bkKit;
    }

    private boolean isKitChanged(ItemStack[] original, ItemStack[] current) {
        if (original == null || current == null) return true;
        int len = Math.max(original.length, current.length);
        for (int i = 0; i < len; i++) {
            ItemStack o = i < original.length ? original[i] : null;
            ItemStack c = i < current.length ? current[i] : null;
            boolean oEmpty = (o == null || o.getType().isAir());
            boolean cEmpty = (c == null || c.getType().isAir());
            if (oEmpty && cEmpty) continue;
            if (oEmpty != cEmpty) return true;
            if (!o.isSimilar(c) || o.getAmount() != c.getAmount()) return true;
        }
        return false;
    }

    private boolean isCategoryPageChanged(String category, int page, ItemStack[] current) {
        for (int i = 0; i < 36; i++) {
            ItemStack o = gui.getCategoryItem(category, page, i);
            ItemStack c = current[i];
            boolean oEmpty = (o == null || o.getType().isAir());
            boolean cEmpty = (c == null || c.getType().isAir());
            if (oEmpty && cEmpty) continue;
            if (oEmpty != cEmpty) return true;
            if (!o.isSimilar(c) || o.getAmount() != c.getAmount()) return true;
        }
        return false;
    }

    private boolean isCategoryPageOriginallyEmpty(String category, int page) {
        for (int i = 0; i < 36; i++) {
            ItemStack orig = gui.getCategoryItem(category, page, i);
            if (orig != null && !orig.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    public static void sendNamingInstructions(Player player, Kitloader plugin) {
        String cancelCmd = plugin.getConfig().getString("settings.naming.cancel-command", "/kitloader cancelname");
        plugin.sendMsg(player, "naming_instructions",
                "cancel_cmd", cancelCmd,
                "item_max", String.valueOf(CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.ITEM)),
                "kit_max", String.valueOf(CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.KIT)),
                "supply_max", String.valueOf(CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.SUPPLY)));
        plugin.sendMsg(player, "naming_limits",
                "item_max", String.valueOf(CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.ITEM)),
                "kit_max", String.valueOf(CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.KIT)),
                "supply_max", String.valueOf(CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.SUPPLY)));
    }

    private int countShulkers(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType().name().endsWith("SHULKER_BOX")) count += item.getAmount();
        }
        return count;
    }

    private void moveShiftClickedItemIntoKitEditor(InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        Inventory topInventory = event.getView().getTopInventory();
        int remaining = clicked.getAmount();

        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int targetSlot = 0; targetSlot <= 40 && remaining > 0; targetSlot++) {
                if (targetSlot >= 36 && targetSlot <= 39
                        && !isValidArmor(targetSlot, clicked.getType())) continue;

                ItemStack target = topInventory.getItem(targetSlot);
                if (pass == 0) {
                    if (target == null || target.getType().isAir() || !target.isSimilar(clicked)) continue;
                    int space = target.getMaxStackSize() - target.getAmount();
                    if (space <= 0) continue;
                    int moved = Math.min(space, remaining);
                    target.setAmount(target.getAmount() + moved);
                    remaining -= moved;
                } else {
                    if (target != null && !target.getType().isAir()) continue;
                    ItemStack movedItem = clicked.clone();
                    int moved = Math.min(movedItem.getMaxStackSize(), remaining);
                    movedItem.setAmount(moved);
                    topInventory.setItem(targetSlot, movedItem);
                    remaining -= moved;
                }
            }
        }

        if (remaining <= 0) event.setCurrentItem(null);
        else event.getCurrentItem().setAmount(remaining);
    }

    private boolean beginTemplatePickup(InventoryClickEvent event, Player player, ItemStack realItem) {
        event.setCancelled(true);
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT
                && click != ClickType.NUMBER_KEY && click != ClickType.SWAP_OFFHAND) return false;
        if (!templatePickupLocks.add(player.getUniqueId())) return false;

        ItemStack pickupItem = realItem.clone();
        plugin.markKitloaderShulker(pickupItem);
        if (click == ClickType.LEFT) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                templatePickupLocks.remove(player.getUniqueId());
                return false;
            }

            Inventory sourceInventory = event.getView().getTopInventory();
            int sourceSlot = event.getRawSlot();
            ItemStack displayTemplate = event.getCurrentItem() != null
                    ? event.getCurrentItem().clone() : null;
            event.setCurrentItem(pickupItem);
            event.setCancelled(false);
            player.getScheduler().runDelayed(plugin, task -> {
                templatePickupLocks.remove(player.getUniqueId());
                if (displayTemplate != null
                        && player.getOpenInventory().getTopInventory() == sourceInventory) {
                    sourceInventory.setItem(sourceSlot, displayTemplate);
                }
            }, () -> templatePickupLocks.remove(player.getUniqueId()), 1L);
            return true;
        }

        boolean granted = false;
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            int requestedAmount = pickupItem.getAmount();
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(pickupItem);
            int remainingAmount = remaining.values().stream().mapToInt(ItemStack::getAmount).sum();
            granted = remainingAmount < requestedAmount;
        } else if (click == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && hotbarSlot <= 8) {
                ItemStack target = player.getInventory().getItem(hotbarSlot);
                if (target == null || target.getType().isAir()) {
                    player.getInventory().setItem(hotbarSlot, pickupItem);
                    granted = true;
                } else if (target.isSimilar(pickupItem)
                        && target.getAmount() + pickupItem.getAmount() <= target.getMaxStackSize()) {
                    target.setAmount(target.getAmount() + pickupItem.getAmount());
                    granted = true;
                }
            }
        } else {
            ItemStack target = player.getInventory().getItemInOffHand();
            if (target == null || target.getType().isAir()) {
                player.getInventory().setItemInOffHand(pickupItem);
                granted = true;
            } else if (target.isSimilar(pickupItem)
                    && target.getAmount() + pickupItem.getAmount() <= target.getMaxStackSize()) {
                target.setAmount(target.getAmount() + pickupItem.getAmount());
                player.getInventory().setItemInOffHand(target);
                granted = true;
            }
        }

        if (!granted) {
            templatePickupLocks.remove(player.getUniqueId());
            return false;
        }
        player.getScheduler().runDelayed(plugin,
                task -> templatePickupLocks.remove(player.getUniqueId()),
                () -> templatePickupLocks.remove(player.getUniqueId()), 1L);
        return true;
    }

    private boolean storeSupplyInConfiguredEnderChest(Player player, DataManager.PlayerData pData, ItemStack supply) {
        Inventory enderChest = player.getEnderChest();
        int enderLimit = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
        int enderSlots = Math.min(gui.getUploadedSupplyEnderSlots(), enderChest.getSize());
        int currentShulkers = 0;
        for (int index = 0; index < enderSlots; index++) {
            ItemStack item = enderChest.getItem(index);
            if (plugin.isKitloaderShulker(item)) currentShulkers += item.getAmount();
        }

        long now = System.currentTimeMillis();
        pData.lastEnderChestPutTime = now;
        if (plugin.isKitloaderShulker(supply) && currentShulkers + supply.getAmount() > enderLimit) {
            if (now - pData.lastPickupWarningTime > 2000) {
                plugin.sendMsg(player, "shulker_limit_enderchest", "max", String.valueOf(enderLimit));
                pData.lastPickupWarningTime = now;
            }
            return false;
        }

        ItemStack remaining = gui.createUploadedSupplyDeliveryCopy(supply);
        plugin.markKitloaderShulker(remaining);
        for (int index = 0; index < enderSlots; index++) {
            ItemStack target = enderChest.getItem(index);
            if (target == null || target.getType().isAir()) {
                enderChest.setItem(index, remaining);
                remaining = null;
                break;
            }
            if (target.isSimilar(remaining) && target.getAmount() < target.getMaxStackSize()) {
                int space = target.getMaxStackSize() - target.getAmount();
                int moved = Math.min(space, remaining.getAmount());
                target.setAmount(target.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
                if (remaining.getAmount() <= 0) {
                    remaining = null;
                    break;
                }
            }
        }

        if (remaining != null) {
            if (now - pData.lastPickupWarningTime > 2000) {
                plugin.sendMsg(player, enderSlots > 9 ? "enderchest_full_dedicated" : "enderchest_full_small");
                pData.lastPickupWarningTime = now;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        if (!pData.hasUsed) {
            pData.hasUsed = true;
            data.savePlayerAsync(player.getUniqueId());
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        return true;
    }

    private boolean areConfiguredEnderChestSlotsFull(Player player) {
        Inventory enderChest = player.getEnderChest();
        int enderSlots = Math.min(gui.getUploadedSupplyEnderSlots(), enderChest.getSize());
        for (int index = 0; index < enderSlots; index++) {
            ItemStack item = enderChest.getItem(index);
            if (item == null || item.getType().isAir()) return false;
        }
        return true;
    }

    private void deleteCachedUploadedSupply(Player player, DataManager.PlayerData pData) {
        String supplyId = gui.getUploadedSupplyTarget(player.getUniqueId());
        if (supplyId == null) return;

        for (int index = 0; index < pData.uploadedSupplies.size(); index++) {
            if (index >= pData.uploadedSupplyIds.size()
                    || !supplyId.equals(pData.uploadedSupplyIds.get(index))) continue;
            ItemStack box = pData.uploadedSupplies.get(index);

            String boxName = box.hasItemMeta() && box.getItemMeta().hasDisplayName()
                    ? cleanText(box.getItemMeta().getDisplayName())
                    : "未命名潜影盒";
            gui.removeSupplyFromPublic(supplyId, player.getUniqueId());
            pData.uploadedSupplies.remove(index);
            pData.uploadedSupplyIds.remove(index);
            data.savePlayerAsync(player.getUniqueId());
            plugin.sendMsg(player, "supply_delete_success", "box", boxName);
            break;
        }
        gui.clearUploadedSupplyTarget(player.getUniqueId());
    }

    private boolean validateSupplyContents(Player player, ItemStack[] contents) {
        CustomNamePolicy.CleanupResult cleanup = CustomNamePolicy.sanitizeItems(contents);
        if (cleanup.removedItems() > 0) {
            plugin.sendMsg(player, "custom_name_items_removed",
                    "removed", String.valueOf(cleanup.removedItems()));
        }
        SupplyContentPolicy.Rules rules = SupplyContentPolicy.rules(plugin);
        SupplyContentPolicy.ValidationResult result = SupplyContentPolicy.validateContents(contents, rules);
        switch (result) {
            case VALID -> {
                return true;
            }
            case NOT_FULL -> plugin.sendMsg(player, "supply_inventory_not_full",
                    "required", String.valueOf(rules.requiredFilledSlots()));
            case ALL_SAME -> plugin.sendMsg(player, "supply_all_same_rejected");
            case TOO_MANY_SIMILAR -> plugin.sendMsg(player, "supply_similar_stack_limit",
                    "max", String.valueOf(rules.maxSimilarStacks()));
            default -> plugin.sendMsg(player, "supply_invalid_box");
        }
        return false;
    }

    private int getFilledSlots(Inventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && !item.getType().isAir()) count++;
        }
        return count;
    }

    private boolean isPlayerStorageFull(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(36, storage.length); i++) {
            if (storage[i] == null || storage[i].getType().isAir()) return false;
        }
        return storage.length >= 36;
    }

    private boolean hasFullPublicKitStorage(ItemStack[] items) {
        if (items == null || items.length < 36) return false;
        for (int i = 0; i < 36; i++) {
            if (items[i] == null || items[i].getType().isAir()) return false;
        }
        return true;
    }

    private boolean isValidArmor(int slot, Material type) {
        if (type == Material.AIR) return true;
        String name = type.name();
        if (slot == 36) return name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") || name.endsWith("_SKULL") || name.endsWith("_HEAD") || name.equals("CARVED_PUMPKIN");
        if (slot == 37) return name.endsWith("_CHESTPLATE") || name.equals("ELYTRA");
        if (slot == 38) return name.endsWith("_LEGGINGS");
        if (slot == 39) return name.endsWith("_BOOTS");
        return true;
    }

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        p.getScheduler().run(plugin, task -> {
            if (!p.isOnline()) return;
            plugin.checkPlayerShulkers(p);
            closeRestrictedKitloaderGui(p, cleanText(p.getOpenInventory().getTitle()));
        }, null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (plugin.isBypassWhitelisted(player)) return;
        ItemStack item = e.getItem().getItemStack();
        if (item.getType().name().endsWith("SHULKER_BOX")) {
            List<String> specialWorlds = plugin.getConfig().getStringList("settings.shulker-limits.special-limit-worlds");
            boolean inSpecialWorld = specialWorlds.contains(player.getWorld().getName());

            int ecLimit = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
            int limit = inSpecialWorld ? ecLimit : plugin.getConfig().getInt("settings.shulker-limits.inventory-max", 3);
            int count = countShulkers(player.getInventory());

            if (count + item.getAmount() > limit) {
                e.setCancelled(true);
                DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
                long now = System.currentTimeMillis();
                int cooldown = plugin.getConfig().getInt("settings.shulker-limits.warning-cooldown", 3000);
                if (pData != null && (now - pData.lastPickupWarningTime > cooldown)) {
                    pData.lastPickupWarningTime = now;
                    plugin.sendMsg(player, "shulker_limit_inventory", "max", String.valueOf(limit));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
        String cancelCmd = plugin.getConfig().getString("settings.naming.cancel-command", "/kitloader cancelname");

        String[] commandParts = e.getMessage().substring(1).trim().split("\\s+");
        String commandLabel = commandParts.length == 0 ? "" : commandParts[0].toLowerCase();
        int namespaceSeparator = commandLabel.lastIndexOf(':');
        if (namespaceSeparator >= 0) commandLabel = commandLabel.substring(namespaceSeparator + 1);
        boolean cancelNaming = commandLabel.equals("kitloader") && commandParts.length > 1
                && commandParts[1].equalsIgnoreCase("cancelname");
        if ((commandLabel.equals("inv") || commandLabel.equals("regear"))
                && !plugin.isBypassWhitelisted(player)) {
            e.setCancelled(true);
            plugin.sendMsg(player, "whitelist_command_denied");
            return;
        }
        if (plugin.isRestrictedKitloaderPlayer(player)
                && (commandLabel.equals("kitloader") || commandLabel.equals("inv"))
                && !cancelNaming) {
            e.setCancelled(true);
            plugin.sendMsg(player, "restricted_command");
            return;
        }

        if (pData != null && pData.isNaming()) {
            String cmd = e.getMessage().toLowerCase();
            if (!cmd.startsWith(cancelCmd)) {
                e.setCancelled(true);
                plugin.sendMsg(player, "naming_forbidden_command", "cancel_cmd", cancelCmd);
            }
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent e) {
        Player player = e.getPlayer();
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
        boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);
        if (isRestricted && pData != null) pData.clearNaming();
        if (pData != null && pData.isNaming()) {
            e.getCommands().clear();
            e.getCommands().add("kitloader");
            e.getCommands().add("kitloader:kitloader");
            return;
        }

        if (!plugin.isBypassWhitelisted(player)) {
            e.getCommands().remove("inv");
            e.getCommands().remove("kitloader:inv");
            e.getCommands().remove("regear");
            e.getCommands().remove("kitloader:regear");
        }

        if (isRestricted) {
            e.getCommands().remove("kitloader");
            e.getCommands().remove("kitloader:kitloader");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());

        if (pData != null && pData.isNaming()) {
            e.setCancelled(true);
            String coloredName = Kitloader.color(e.getMessage());

            CustomNamePolicy.NameValidation validation;
            if (pData.editSession != null && pData.editSession.isNaming) {
                validation = CustomNamePolicy.validateSupplyName(plugin, coloredName);
            } else if ((pData.publicEditSession != null && pData.publicEditSession.isNaming)
                    || (pData.namingContext != null
                    && (pData.namingContext.type == DataManager.NamingContext.Type.KIT_RENAME
                    || pData.namingContext.type == DataManager.NamingContext.Type.ADMIN_KIT_RENAME
                    || pData.namingContext.type == DataManager.NamingContext.Type.PUBLIC_KIT_RENAME))) {
                validation = CustomNamePolicy.validateKitName(plugin, coloredName);
            } else {
                validation = CustomNamePolicy.validateItemName(plugin, coloredName);
            }
            if (!validation.valid()) {
                CustomNamePolicy.sendValidationFailure(plugin, player, validation);
                return;
            }

            if (pData.editSession != null && pData.editSession.isNaming) {
                pData.editSession.name = coloredName;
                pData.editSession.isNaming = false;
                plugin.sendMsg(player, "name_set_supply", "name", coloredName);
                player.getScheduler().run(plugin, task -> gui.openCustomSupplyEditGui(player), null);
            } else if (pData.publicEditSession != null && pData.publicEditSession.isNaming) {
                pData.publicEditSession.name = coloredName;
                pData.publicEditSession.isNaming = false;
                player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#a8ff78&l共享Kit已命名为: &f&l" + coloredName));
                player.getScheduler().run(plugin, task -> gui.openConfirmPublicUploadGui(player), null);
            } else if (pData.namingContext != null) {
                DataManager.NamingContext ctx = pData.namingContext;

                if (ctx.type == DataManager.NamingContext.Type.PUBLIC_KIT_RENAME) {
                    String kitId = ctx.category;
                    String newName = coloredName;

                    boolean exists = data.publicKits.stream().anyMatch(k -> k.kitName.equals(newName) && !k.id.equals(kitId));
                    if (exists) {
                        player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l已存在同名共享Kit！"));
                        player.getScheduler().run(plugin, task -> {
                            DataManager.PublicKit targetPk = data.publicKits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
                            if (targetPk != null) gui.openPublicKitEditGui(player, targetPk, true);
                        }, null);
                    } else {
                        DataManager.PublicKit targetPk = data.publicKits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
                        if (targetPk != null) {
                            targetPk.kitName = newName;
                            targetPk.items = gui.getPublicKitEditCache(player.getUniqueId());
                            data.savePublicKits();
                            gui.clearPublicCache(player.getUniqueId());
                            player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#a8ff78&l共享Kit已重命名为: &f&l" + newName));
                            pData.namingContext = null;
                            player.getScheduler().run(plugin, task -> gui.openPublicKitEditGui(player, targetPk, false), null);
                        }
                    }
                    return;
                }

                if (ctx.type == DataManager.NamingContext.Type.KIT_RENAME) {
                    String oldName = ctx.category;
                    String newName = coloredName;
                    if (pData.kits.containsKey(newName)) {
                        plugin.sendMsg(player, "name_set_kit_exist");
                        player.getScheduler().run(plugin, task -> gui.openKitEditGui(player, oldName, true), null);
                    } else {
                        ItemStack[] savedItems = gui.getPlayerKitCache(player.getUniqueId());
                        if (savedItems == null) savedItems = pData.kits.get(oldName);
                        ItemStack[] renamedKit = data.copyItems(savedItems);
                        int removedShulkers = plugin.enforceKitShulkerLimit(renamedKit);
                        pData.kits.remove(oldName);
                        pData.kits.put(newName, renamedKit);
                        data.savePlayerAsync(player.getUniqueId());
                        if (removedShulkers > 0) plugin.sendMsg(player, "kit_shulker_trimmed",
                                "max", String.valueOf(plugin.getConfig().getInt("settings.shulker-limits.kit-save-max", 3)),
                                "removed", String.valueOf(removedShulkers));
                        gui.clearPlayerCache(player.getUniqueId());
                        plugin.sendMsg(player, "name_set_kit_success", "name", newName);
                        pData.namingContext = null;
                        player.getScheduler().run(plugin, task -> gui.openKitEditGui(player, newName, false), null);
                    }
                    return;
                }

                if (ctx.type == DataManager.NamingContext.Type.ADMIN_KIT_RENAME) {
                    String[] parts = ctx.category.split("@@");
                    String targetName = parts[0];
                    String oldName = parts[1];
                    String newName = coloredName;

                    org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                    DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());

                    if (tData != null && tData.kits.containsKey(newName)) {
                        plugin.sendMsg(player, "name_set_kit_exist");
                        player.getScheduler().run(plugin, task -> gui.openOtherPlayerKitEditGui(player, targetName, oldName, true), null);
                    } else if (tData != null) {
                        ItemStack[] savedItems = gui.getAdminKitCache(player.getUniqueId());
                        if (savedItems == null) savedItems = tData.kits.get(oldName);
                        ItemStack[] renamedKit = data.copyItems(savedItems);
                        int removedShulkers = plugin.enforceKitShulkerLimit(renamedKit);
                        tData.kits.remove(oldName);
                        tData.kits.put(newName, renamedKit);
                        data.saveOfflinePlayerAsync(tData);
                        if (removedShulkers > 0) plugin.sendMsg(player, "kit_shulker_trimmed",
                                "max", String.valueOf(plugin.getConfig().getInt("settings.shulker-limits.kit-save-max", 3)),
                                "removed", String.valueOf(removedShulkers));
                        gui.clearAdminCache(player.getUniqueId());
                        plugin.sendMsg(player, "name_set_kit_success", "name", newName);
                        pData.namingContext = null;
                        player.getScheduler().run(plugin, task -> gui.openOtherPlayerKitEditGui(player, targetName, newName, false), null);
                    }
                    return;
                }

                if (ctx.targetItem != null) {
                    ItemMeta meta = ctx.targetItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(coloredName);
                        ctx.targetItem.setItemMeta(meta);
                    }
                }

                if (ctx.type == DataManager.NamingContext.Type.EDIT_SESSION) {
                    plugin.sendMsg(player, "name_set_item", "name", coloredName);
                    pData.editItemSession.currentItem = ctx.targetItem;
                    if (gui.isArmor(ctx.targetItem)) {
                        player.getScheduler().run(plugin, task -> gui.openArmorTrimGui(player), null);
                    } else {
                        player.getScheduler().run(plugin, task -> gui.openEnchantGui(player), null);
                    }
                } else if (ctx.type == DataManager.NamingContext.Type.DIRECT) {
                    plugin.sendMsg(player, "name_set_item", "name", coloredName);
                    ItemStack given = ctx.targetItem.clone();
                    plugin.markKitloaderShulker(given);
                    player.getScheduler().run(plugin, task -> {
                        player.getInventory().addItem(given);
                        if (!pData.hasUsed) { pData.hasUsed = true; data.savePlayerAsync(player.getUniqueId()); }
                        gui.openCategoryGui(player, ctx.category, ctx.page);
                    }, null);
                }
                pData.namingContext = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        plugin.sanitizePlayerShulkers(e.getPlayer());
        data.loadPlayerAsync(e.getPlayer());
        plugin.refreshCommandTree(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        gui.clearNavigationState(player);
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
        if (pData != null) {
            pData.clearNaming();
            if (plugin.getConfig().getBoolean("settings.reset-on-quit", false)) {
                if (pData.hasUsed) pData.hasUsed = false;
            }
            data.savePlayerAsync(player.getUniqueId());
        }
        data.removeCache(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
            String cleanTitle = cleanText(player.getOpenInventory().getTitle());
            Inventory topInv = player.getOpenInventory().getTopInventory();
            boolean isEditing = false;
            String rawKitEditPrefix = cleanText(plugin.getGuiTitle("kit-edit-prefix", ""));
            if (cleanTitle.contains(" ") && pData != null && pData.editSession != null) {
                for (int i = 0; i < 27; i++) {
                    ItemStack item = topInv.getItem(i);
                    pData.editSession.items[i] = (item != null) ? item.clone() : null;
                }
                isEditing = true;
            }
            else if (cleanTitle.contains(" Kit") && !cleanTitle.contains(" ") && pData != null && pData.publicEditSession != null) {
                for (int i = 0; i < 41; i++) {
                    ItemStack item = topInv.getItem(i);
                    pData.publicEditSession.items[i] = (item != null) ? item.clone() : null;
                }
                isEditing = true;
            }
            else if (cleanTitle.contains(" Kit:")) {
                String kitId = gui.getPublicTargetCache(player.getUniqueId());
                if (kitId != null) {
                    gui.cachePublicKitEdit(player.getUniqueId(), extractKitFromEditGui(topInv));
                }
                isEditing = true;
            }
            else if (cleanTitle.contains(" Kit") && !cleanTitle.contains(" Kit:")) {
                String[] targetInfo = gui.getAdminTargetCache(player.getUniqueId());
                if (targetInfo != null) {
                    gui.cacheAdminKit(player.getUniqueId(), extractKitFromEditGui(topInv));
                }
                isEditing = true;
            }
            else if (cleanTitle.startsWith(rawKitEditPrefix)) {
                String kitName = gui.getPlayerTargetCache(player.getUniqueId());
                if (kitName != null) {
                    gui.cachePlayerKit(player.getUniqueId(), extractKitFromEditGui(topInv));
                }
                isEditing = true;
            }
            gui.setSkipNextClose(player);
            player.closeInventory();
            if (isEditing) {
                plugin.sendMsg(player, "edit_temporarily_saved_on_death");
            }
        }
        if (pData != null && pData.hasUsed) {
            pData.hasUsed = false;
            data.savePlayerAsync(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() != null) return;
        Player player = (Player) e.getPlayer();

        if (gui.checkAndClearSkipNextClose(player)) return;
        if (gui.checkAndClearNavigating(player)) return;

        String cleanTitle = cleanText(e.getView().getTitle());
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());

        String rawKitEditPrefix = cleanText(plugin.getGuiTitle("kit-edit-prefix", ""));
        String rawEditPrefix = cleanText(plugin.getGuiTitle("edit-prefix", ""));

        String tSavePlayer = cleanText(GuiManager.T_SAVE_PLAYER);
        String tDelPlayer = cleanText(GuiManager.T_DEL_PLAYER);
        String tSaveAdmin = cleanText(GuiManager.T_SAVE_ADMIN);
        String tDelAdmin = cleanText(GuiManager.T_DEL_ADMIN);
        String tSavePub = cleanText(GuiManager.T_SAVE_PUB);
        String tDelPub = cleanText(GuiManager.T_DEL_PUB);
        String tAbandonPub = cleanText(GuiManager.T_ABANDON_PUB);
        String tAbandonPlayer = cleanText(GuiManager.T_ABANDON_PLAYER);
        String tAbandonAdmin = cleanText(GuiManager.T_ABANDON_ADMIN);
        String tCancelUp = cleanText(GuiManager.T_CANCEL_UP);
        String tDoUp = cleanText(GuiManager.T_DO_UP);

        if (cleanTitle.equals(tSavePlayer) || cleanTitle.equals(tDelPlayer) || cleanTitle.equals(tAbandonPlayer)) {
            String kitName = gui.getPlayerTargetCache(player.getUniqueId());
            if (kitName != null) {
                player.getScheduler().run(plugin, t -> gui.openKitEditGui(player, kitName, true), null);
            }
            return;
        }

        if (cleanTitle.equals(tSaveAdmin) || cleanTitle.equals(tDelAdmin) || cleanTitle.equals(tAbandonAdmin)) {
            String[] targetInfo = gui.getAdminTargetCache(player.getUniqueId());
            if (targetInfo != null) {
                player.getScheduler().run(plugin, t -> gui.openOtherPlayerKitEditGui(player, targetInfo[0], targetInfo[1], true), null);
            }
            return;
        }

        if (cleanTitle.equals(tSavePub) || cleanTitle.equals(tDelPub) || cleanTitle.equals(tAbandonPub)) {
            String kitId = gui.getPublicTargetCache(player.getUniqueId());
            if (kitId != null) {
                DataManager.PublicKit pk = data.publicKits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
                if (pk != null) {
                    player.getScheduler().run(plugin, t -> gui.openPublicKitEditGui(player, pk, true), null);
                }
            }
            return;
        }

        if (cleanTitle.equals(tCancelUp) || cleanTitle.equals(tDoUp)) {
            player.getScheduler().run(plugin, t -> gui.openConfirmPublicUploadGui(player), null);
            return;
        }

        if (cleanTitle.contains("放弃编辑？") || cleanTitle.contains("上传补给？")) {
            player.getScheduler().run(plugin, t -> gui.openCustomSupplyEditGui(player), null);
            return;
        }

        if (cleanTitle.contains("上传共享Kit") && !cleanTitle.contains("确认")) {
            if (pData != null && pData.publicEditSession != null && !pData.publicEditSession.isNaming) {
                for (int i = 0; i < 41; i++) {
                    ItemStack item = e.getView().getTopInventory().getItem(i);
                    pData.publicEditSession.items[i] = (item != null) ? item.clone() : null;
                }
                player.getScheduler().run(plugin, t -> gui.openConfirmPublicCancelGui(player), null);
            }
            return;
        }

        if (cleanTitle.startsWith(rawKitEditPrefix)) {
            boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);
            if (isRestricted) return;

            String kitName = gui.getPlayerTargetCache(player.getUniqueId());
            if (kitName != null && pData != null && pData.kits.containsKey(kitName)) {
                ItemStack[] currentKit = extractKitFromEditGui(e.getView().getTopInventory());
                ItemStack[] originalKit = pData.kits.get(kitName);

                if (!isKitChanged(originalKit, currentKit)) {
                    gui.clearPlayerCache(player.getUniqueId());
                    player.getScheduler().run(plugin, t -> gui.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet())), null);
                } else {
                    gui.cachePlayerKit(player.getUniqueId(), currentKit);
                    gui.cachePlayerTarget(player.getUniqueId(), kitName);
                    player.getScheduler().run(plugin, t -> gui.openConfirmAbandonPlayerKitGui(player), null);
                }
            }
            return;
        }

        if (cleanTitle.contains("管理他人Kit")) {
            String[] targetInfo = gui.getAdminTargetCache(player.getUniqueId());
            if (targetInfo != null) {
                String targetName = targetInfo[0];
                String kitName = targetInfo[1];
                org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());
                ItemStack[] currentKit = extractKitFromEditGui(e.getView().getTopInventory());
                ItemStack[] originalKit = (tData != null) ? tData.kits.get(kitName) : null;

                if (!isKitChanged(originalKit, currentKit)) {
                    gui.clearAdminCache(player.getUniqueId());
                    if (tData != null) player.getScheduler().run(plugin, t -> gui.openOtherPlayerKitListGui(player, targetName, new ArrayList<>(tData.kits.keySet())), null);
                } else {
                    gui.cacheAdminKit(player.getUniqueId(), currentKit);
                    gui.cacheAdminTarget(player.getUniqueId(), targetName, kitName);
                    player.getScheduler().run(plugin, task -> gui.openConfirmAbandonAdminKitGui(player), null);
                }
            }
            return;
        }

        if (cleanTitle.contains("编辑共享Kit:")) {
            String kitId = gui.getPublicTargetCache(player.getUniqueId());
            DataManager.PublicKit kit = kitId == null ? null : data.publicKits.stream()
                    .filter(candidate -> candidate.id.equals(kitId)).findFirst().orElse(null);
            ItemStack[] currentKit = extractKitFromEditGui(e.getView().getTopInventory());
            if (kit == null || !isKitChanged(kit.items, currentKit)) {
                gui.clearPublicCache(player.getUniqueId());
                player.getScheduler().run(plugin,
                        task -> gui.openCategoryGui(player, "public_kits", 0), null);
            } else {
                gui.cachePublicKitEdit(player.getUniqueId(), currentKit);
                gui.cachePublicTarget(player.getUniqueId(), kitId);
                player.getScheduler().run(plugin,
                        task -> gui.openConfirmAbandonPublicGui(player), null);
            }
            return;
        }

        if (cleanTitle.contains("自定义补给盒")) {
            if (pData != null && pData.editSession != null && !pData.editSession.isNaming) {
                for (int i = 0; i < 27; i++) {
                    ItemStack item = e.getView().getTopInventory().getItem(i);
                    pData.editSession.items[i] = (item != null) ? item.clone() : null;
                }
                player.getScheduler().run(plugin, t -> gui.openConfirmGui(player, "&#34495E&l确认放弃编辑？", "放弃编辑", "继续编辑"), null);
            }
            return;
        }

        if (cleanTitle.contains("盔甲纹饰与名称") || cleanTitle.contains("附魔与物品编辑")) {
            if (pData != null && pData.editItemSession != null) {
                String cat = pData.editItemSession.category;
                int page = pData.editItemSession.page;
                pData.editItemSession = null;
                player.getScheduler().run(plugin, t -> gui.openCategoryGui(player, cat, page), null);
            }
            return;
        }

        if (cleanTitle.contains("已上传的补给")) {
            player.getScheduler().run(plugin, t -> gui.openCategoryGui(player, "supply", 0), null);
            return;
        }

        if (cleanTitle.contains("我的共享Kit")) {
            player.getScheduler().run(plugin, t -> gui.openCategoryGui(player, "public_kits", 0), null);
            return;
        }

        if (cleanTitle.contains("确认删除补给:")) {
            gui.clearUploadedSupplyTarget(player.getUniqueId());
            player.getScheduler().run(plugin, t -> gui.openUploadedSuppliesGui(player), null);
            return;
        }

        String rawCategoryPrefix = cleanText(plugin.getGuiTitle("category-prefix", ""));
        if (cleanTitle.startsWith(rawCategoryPrefix) && cleanTitle.contains(" - P")) {
            data.tryAutosavePlayer(player);
        }

        if (cleanTitle.startsWith(rawEditPrefix) && cleanTitle.contains(" - P")) {
            boolean hasAnyItem = false;
            ItemStack[] contents = new ItemStack[36];
            for (int i = 0; i < 36; i++) {
                ItemStack item = e.getInventory().getItem(i);
                contents[i] = (item != null) ? item.clone() : null;
                if (item != null && !item.getType().isAir()) hasAnyItem = true;
            }

            String remaining = cleanTitle.substring(rawEditPrefix.length());
            String[] parts = remaining.split(" - P");
            if (parts.length >= 2) {
                String category = parts[0].trim();
                int page = Integer.parseInt(parts[1].trim()) - 1;

                if (!isCategoryPageChanged(category, page, contents)) {
                    return;
                }

                if (!hasAnyItem) {
                    if (!isCategoryPageOriginallyEmpty(category, page)) {
                        plugin.sendMsg(player, "category_empty_nosave");
                    }
                    return;
                }

                gui.saveCategoryItems(category, page, contents);
                plugin.sendMsg(player, "gui_saved", "category", category, "page", String.valueOf(page + 1));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String cleanTitle = cleanText(event.getView().getTitle());
        if (plugin.isRestrictedKitloaderPlayer(player) && isRestrictedKitloaderGui(cleanTitle)) {
            event.setCancelled(true);
            closeRestrictedKitloaderGui(player, cleanTitle);
            return;
        }
        String rawKitEditPrefix = cleanText(plugin.getGuiTitle("kit-edit-prefix", ""));
        String rawCategoryPrefix = cleanText(plugin.getGuiTitle("category-prefix", ""));

        if (cleanTitle.equals(cleanText(GuiManager.T_SAVE_PLAYER)) ||
                cleanTitle.equals(cleanText(GuiManager.T_DEL_PLAYER)) ||
                cleanTitle.equals(cleanText(GuiManager.T_SAVE_ADMIN)) ||
                cleanTitle.equals(cleanText(GuiManager.T_DEL_ADMIN)) ||
                cleanTitle.equals(cleanText(GuiManager.T_SAVE_PUB)) ||
                cleanTitle.equals(cleanText(GuiManager.T_DEL_PUB)) ||
                cleanTitle.equals(cleanText(GuiManager.T_ABANDON_PUB)) ||
                cleanTitle.equals(cleanText(GuiManager.T_ABANDON_PLAYER)) ||
                cleanTitle.equals(cleanText(GuiManager.T_ABANDON_ADMIN)) ||
                cleanTitle.equals(cleanText(GuiManager.T_CANCEL_UP)) ||
                cleanTitle.equals(cleanText(GuiManager.T_DO_UP)) ||
                cleanTitle.contains("放弃编辑？") || cleanTitle.contains("上传补给？") ||
                cleanTitle.contains("确认删除补给:")) {
            event.setCancelled(true);
            return;
        }

        if (cleanTitle.contains("已上传的补给")) {
            event.setCancelled(true);
            return;
        }

        if (cleanTitle.startsWith(rawCategoryPrefix) && cleanTitle.contains(" - P")) {
            for (int slot : event.getRawSlots()) {
                if (slot >= 0 && slot < event.getView().getTopInventory().getSize()) {
                    if (slot < 36 || slot > 44 || (cleanTitle.contains("补给盒子") && (slot == 38 || slot == 40 || slot == 42))
                            || (cleanTitle.contains("一键Kit") && (slot == 39 || slot == 41))) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        if (cleanTitle.contains("上传共享Kit") && !cleanTitle.contains("确认")) {
            for (int slot : event.getRawSlots()) {
                if (slot >= 41 && slot <= 53) {
                    event.setCancelled(true);
                    return;
                }
            }
            player.getScheduler().run(plugin, task -> {
                DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
                if (pData != null && pData.publicEditSession != null) {
                    for (int i = 0; i < 41; i++) {
                        ItemStack it = event.getView().getTopInventory().getItem(i);
                        pData.publicEditSession.items[i] = (it != null) ? it.clone() : null;
                    }
                }
            }, null);
            return;
        }

        if (cleanTitle.contains("查看共享Kit")) {
            for (int slot : event.getRawSlots()) {
                if (slot >= 0 && slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true); return;
                }
            }
        }

        if (cleanTitle.startsWith(rawKitEditPrefix) || cleanTitle.contains("编辑共享Kit:")) {
            boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);
            if (isRestricted && !cleanTitle.contains("编辑共享Kit:")) {
                for (int slot : event.getRawSlots()) {
                    if (slot >= 0 && slot < event.getView().getTopInventory().getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            ItemStack draggedItem = event.getOldCursor();
            for (int slot : event.getRawSlots()) {
                if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) continue;
                if (slot >= 41) {
                    event.setCancelled(true);
                    return;
                }
                if (slot >= 36 && slot <= 39 && draggedItem != null && !draggedItem.getType().isAir()
                        && !isValidArmor(slot, draggedItem.getType())) {
                    event.setCancelled(true);
                    plugin.sendMsg(player, "armor_slot_mismatch");
                    return;
                }
            }
        }

        String rawEditPrefix = cleanText(plugin.getGuiTitle("edit-prefix", ""));
        if (cleanTitle.startsWith(rawEditPrefix) && cleanTitle.contains(" - P")) {
            for (int slot : event.getRawSlots()) {
                if (slot >= 36 && slot <= 53) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (cleanTitle.contains("自定义补给盒")) {
            for (int slot : event.getRawSlots()) {
                if (slot >= 27 && slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }

            ItemStack oldCursor = event.getOldCursor();
            if (oldCursor.getType().name().endsWith("SHULKER_BOX")) {
                for (int slot : event.getRawSlots()) {
                    if (slot >= 0 && slot <= 26) {
                        event.setCancelled(true);
                        plugin.sendMsg(player, "shulker_nesting_forbidden");
                        return;
                    }
                }
            }

            player.getScheduler().run(plugin, task -> {
                DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
                if (pData != null && pData.editSession != null) {
                    for (int i = 0; i < 27; i++) {
                        ItemStack it = event.getView().getTopInventory().getItem(i);
                        pData.editSession.items[i] = (it != null) ? it.clone() : null;
                    }
                }
            }, null);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String cleanTitle = cleanText(event.getView().getTitle());
        if (plugin.isRestrictedKitloaderPlayer(player) && isRestrictedKitloaderGui(cleanTitle)) {
            event.setCancelled(true);
            closeRestrictedKitloaderGui(player, cleanTitle);
            return;
        }
        DataManager.PlayerData pData = data.getPlayerData(player.getUniqueId());
        if (pData == null) return;

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();

        String tSavePlayer = cleanText(GuiManager.T_SAVE_PLAYER);
        String tDelPlayer = cleanText(GuiManager.T_DEL_PLAYER);
        String tSaveAdmin = cleanText(GuiManager.T_SAVE_ADMIN);
        String tDelAdmin = cleanText(GuiManager.T_DEL_ADMIN);
        String tSavePub = cleanText(GuiManager.T_SAVE_PUB);
        String tDelPub = cleanText(GuiManager.T_DEL_PUB);
        String tAbandonPub = cleanText(GuiManager.T_ABANDON_PUB);
        String tAbandonPlayer = cleanText(GuiManager.T_ABANDON_PLAYER);
        String tAbandonAdmin = cleanText(GuiManager.T_ABANDON_ADMIN);
        String tCancelUp = cleanText(GuiManager.T_CANCEL_UP);
        String tDoUp = cleanText(GuiManager.T_DO_UP);

        if (cleanTitle.equals(tCancelUp)) {
            event.setCancelled(true);
            if (slot == 11) {
                pData.publicEditSession = null;
                data.savePlayerAsync(player.getUniqueId());
                gui.openCategoryGui(player, "public_kits", 0);
            } else if (slot == 15) {
                gui.openPublicKitUploadGui(player);
            }
            return;
        }

        if (cleanTitle.equals(tDoUp)) {
            event.setCancelled(true);
            if (slot == 11) {
                if (!plugin.getConfig().getBoolean("settings.public-kits.upload-enabled", true)) {
                    plugin.sendMsg(player, "public_kit_upload_disabled");
                    return;
                }
                int uploadLimit = plugin.getConfig().getInt("settings.public-kits.max-limit", 5);
                long uploadedCount = data.publicKits.stream()
                        .filter(k -> k.uploaderUuid.equals(player.getUniqueId()))
                        .count();
                boolean editingExisting = pData.publicEditSession != null && pData.publicEditSession.kitId != null;
                if (!editingExisting && uploadedCount >= uploadLimit) {
                    plugin.sendMsg(player, "public_kit_upload_limit", "limit", String.valueOf(uploadLimit));
                    return;
                }
                if (pData.publicEditSession == null || !hasFullPublicKitStorage(pData.publicEditSession.items)) {
                    plugin.sendMsg(player, "public_kit_inventory_not_full");
                    return;
                }
                DataManager.PublicKit pk = new DataManager.PublicKit();
                if (pData.publicEditSession.kitId != null) {
                    pk.id = pData.publicEditSession.kitId;
                } else {
                    pk.id = UUID.randomUUID().toString();
                }
                pk.uploaderUuid = player.getUniqueId();
                pk.uploaderName = player.getName();
                pk.kitName = pData.publicEditSession.name;
                for (int i=0; i<41; i++) {
                    pk.items[i] = pData.publicEditSession.items[i] != null ? pData.publicEditSession.items[i].clone() : null;
                }
                if (!data.upsertPublicKit(pk)) {
                    plugin.sendMsg(player, "public_kit_duplicate");
                    return;
                }

                pData.publicEditSession = null;
                data.savePlayerAsync(player.getUniqueId());
                player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#a8ff78&l一键共享Kit已成功发布全局！"));
                gui.openCategoryGui(player, "public_kits", 0);
            } else if (slot == 13) {
                gui.setSkipNextClose(player); player.closeInventory();
                pData.publicEditSession.isNaming = true;
                sendNamingInstructions(player, plugin);
            } else if (slot == 15) {
                gui.openConfirmPublicCancelGui(player);
            }
            return;
        }

        if (cleanTitle.equals(tDelPub)) {
            event.setCancelled(true);
            if (slot == 11) {
                String kitId = gui.getPublicTargetCache(player.getUniqueId());
                if (kitId != null) {
                    data.publicKits.removeIf(k -> k.id.equals(kitId));
                    data.savePublicKits();
                    player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#a8ff78&l成功永久删除该共享Kit！"));
                }
                gui.clearPublicCache(player.getUniqueId());
                gui.openCategoryGui(player, "public_kits", 0);
            } else if (slot == 15) {
                gui.openMyPublicKitsGui(player);
            }
            return;
        }

        if (cleanTitle.equals(tAbandonPlayer)) {
            event.setCancelled(true);
            String kitName = gui.getPlayerTargetCache(player.getUniqueId());
            if (slot == 11) {
                gui.clearPlayerCache(player.getUniqueId());
                gui.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet()));
            } else if (slot == 15) {
                gui.openKitEditGui(player, kitName, true);
            }
            return;
        }

        if (cleanTitle.equals(tAbandonAdmin)) {
            event.setCancelled(true);
            String[] targetInfo = gui.getAdminTargetCache(player.getUniqueId());
            if (targetInfo != null) {
                if (slot == 11) {
                    gui.clearAdminCache(player.getUniqueId());
                    org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetInfo[0]);
                    DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());
                    gui.openOtherPlayerKitListGui(player, targetInfo[0], new ArrayList<>(tData.kits.keySet()));
                } else if (slot == 15) {
                    gui.openOtherPlayerKitEditGui(player, targetInfo[0], targetInfo[1], true);
                }
            }
            return;
        }

        if (cleanTitle.equals(tAbandonPub)) {
            event.setCancelled(true);
            String kitId = gui.getPublicTargetCache(player.getUniqueId());
            if (kitId != null) {
                if (slot == 11) {
                    gui.clearPublicCache(player.getUniqueId());
                    gui.openCategoryGui(player, "public_kits", 0);
                } else if (slot == 15) {
                    DataManager.PublicKit pk = data.publicKits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
                    if (pk != null) gui.openPublicKitEditGui(player, pk, true);
                }
            }
            return;
        }

        if (cleanTitle.equals(tSavePub)) {
            event.setCancelled(true);
            String kitId = gui.getPublicTargetCache(player.getUniqueId());
            if (kitId != null) {
                if (slot == 11) {
                    ItemStack[] bkKit = gui.getPublicKitEditCache(player.getUniqueId());
                    DataManager.PublicKit pk = data.publicKits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
                    if (pk != null && bkKit != null) {
                        pk.items = bkKit;
                        data.savePublicKits();
                        player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#a8ff78&l共享Kit保存成功！"));
                    }
                    gui.clearPublicCache(player.getUniqueId());
                    gui.openCategoryGui(player, "public_kits", 0);
                } else if (slot == 15) {
                    gui.clearPublicCache(player.getUniqueId());
                    gui.openCategoryGui(player, "public_kits", 0);
                }
            }
            return;
        }

        if (cleanTitle.equals(tSavePlayer)) {
            event.setCancelled(true);
            String kitName = gui.getPlayerTargetCache(player.getUniqueId());
            if (kitName == null) { gui.setSkipNextClose(player); player.closeInventory(); return; }
            if (slot == 11) {
                ItemStack[] bkKit = gui.getPlayerKitCache(player.getUniqueId());
                if (bkKit != null) {
                    ItemStack[] savedKit = data.copyItems(bkKit);
                    int removedShulkers = plugin.enforceKitShulkerLimit(savedKit);
                    pData.kits.put(kitName, savedKit);
                    data.savePlayerAsync(player.getUniqueId());
                    if (removedShulkers > 0) plugin.sendMsg(player, "kit_shulker_trimmed",
                            "max", String.valueOf(plugin.getConfig().getInt("settings.shulker-limits.kit-save-max", 3)),
                            "removed", String.valueOf(removedShulkers));
                    plugin.sendMsg(player, "kit_saved", "kit", kitName);
                }
                gui.clearPlayerCache(player.getUniqueId());
                gui.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet()));
            } else if (slot == 15) {
                gui.clearPlayerCache(player.getUniqueId());
                gui.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet()));
            }
            return;
        }

        if (cleanTitle.equals(tDelPlayer)) {
            event.setCancelled(true);
            String kitName = gui.getPlayerTargetCache(player.getUniqueId());
            if (kitName == null) { gui.setSkipNextClose(player); player.closeInventory(); return; }
            if (slot == 11) {
                pData.kits.remove(kitName);
                data.savePlayerAsync(player.getUniqueId());
                plugin.sendMsg(player, "kit_deleted", "kit", kitName);
                gui.clearPlayerCache(player.getUniqueId());
                gui.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet()));
            } else if (slot == 15) {
                gui.openKitEditGui(player, kitName, true);
            }
            return;
        }

        if (cleanTitle.equals(tSaveAdmin)) {
            event.setCancelled(true);
            String[] targetInfo = gui.getAdminTargetCache(player.getUniqueId());
            if (targetInfo == null) { gui.setSkipNextClose(player); player.closeInventory(); return; }
            String targetName = targetInfo[0]; String kitName = targetInfo[1];
            if (slot == 11) {
                ItemStack[] bkKit = gui.getAdminKitCache(player.getUniqueId());
                if (bkKit != null) {
                    org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                    DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());
                    if (tData != null) {
                        ItemStack[] savedKit = data.copyItems(bkKit);
                        int removedShulkers = plugin.enforceKitShulkerLimit(savedKit);
                        tData.kits.put(kitName, savedKit);
                        data.saveOfflinePlayerAsync(tData);
                        if (removedShulkers > 0) plugin.sendMsg(player, "kit_shulker_trimmed",
                                "max", String.valueOf(plugin.getConfig().getInt("settings.shulker-limits.kit-save-max", 3)),
                                "removed", String.valueOf(removedShulkers));
                    }
                }
                gui.clearAdminCache(player.getUniqueId());
                org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());
                gui.openOtherPlayerKitListGui(player, targetName, new ArrayList<>(tData.kits.keySet()));
            } else if (slot == 15) {
                gui.clearAdminCache(player.getUniqueId());
                org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());
                gui.openOtherPlayerKitListGui(player, targetName, new ArrayList<>(tData.kits.keySet()));
            }
            return;
        }

        if (cleanTitle.equals(tDelAdmin)) {
            event.setCancelled(true);
            String[] targetInfo = gui.getAdminTargetCache(player.getUniqueId());
            if (targetInfo == null) { gui.setSkipNextClose(player); player.closeInventory(); return; }
            String targetName = targetInfo[0]; String kitName = targetInfo[1];
            if (slot == 11) {
                org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());
                if (tData != null) {
                    tData.kits.remove(kitName);
                    data.saveOfflinePlayerAsync(tData);
                }
                gui.clearAdminCache(player.getUniqueId());
                gui.openOtherPlayerKitListGui(player, targetName, new ArrayList<>(tData.kits.keySet()));
            } else if (slot == 15) {
                gui.openOtherPlayerKitEditGui(player, targetName, kitName, true);
            }
            return;
        }

        if (cleanTitle.contains("确认放弃编辑？")) {
            event.setCancelled(true);
            if (slot == 11) { pData.editSession = null; gui.setSkipNextClose(player); player.closeInventory(); player.performCommand("kitloader"); }
            else if (slot == 15) { gui.openCustomSupplyEditGui(player); }
            return;
        }

        if (cleanTitle.contains("确认上传补给？")) {
            event.setCancelled(true);
            if (slot == 11) {
                if (!plugin.getConfig().getBoolean("settings.custom-supply.enabled", true)) {
                    plugin.sendMsg(player, "supply_upload_disabled");
                    return;
                }
                int limit = plugin.getConfig().getInt("settings.custom-supply.max-limit", 5);
                if (pData.uploadedSupplies.size() >= limit) {
                    plugin.sendMsg(player, "supply_upload_limit", "limit", String.valueOf(limit));
                    gui.setSkipNextClose(player); player.closeInventory(); player.performCommand("kitloader"); return;
                }
                if (!validateSupplyContents(player, pData.editSession.items)) return;
                ItemStack shulker = new ItemStack(pData.editSession.color);
                BlockStateMeta meta = (BlockStateMeta) shulker.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Kitloader.color(pData.editSession.name));
                    ShulkerBox box = (ShulkerBox) meta.getBlockState();
                    box.getInventory().setContents(pData.editSession.items);
                    meta.setBlockState(box); shulker.setItemMeta(meta);
                }
                gui.ensureUploadedSupplyMetadata(player, pData);
                String supplyId = gui.prepareUploadedSupply(player, shulker, pData.uploadedSuppliesVisible);
                if (supplyId == null) {
                    plugin.sendMsg(player, "supply_upload_duplicate");
                    return;
                }
                pData.uploadedSupplies.add(shulker);
                pData.uploadedSupplyIds.add(supplyId);

                pData.editSession = null;
                data.savePlayerAsync(player.getUniqueId());
                plugin.sendMsg(player, "supply_upload_success");
                gui.setSkipNextClose(player); player.closeInventory(); player.performCommand("kitloader");
            } else if (slot == 15) { gui.openCustomSupplyEditGui(player); }
            return;
        }

        if (cleanTitle.contains("确认删除补给:")) {
            event.setCancelled(true);
            if (slot == 11) {
                deleteCachedUploadedSupply(player, pData);
                gui.openUploadedSuppliesGui(player);
            } else if (slot == 15) {
                gui.clearUploadedSupplyTarget(player.getUniqueId());
                gui.openUploadedSuppliesGui(player);
            }
            return;
        }

        if (cleanTitle.contains("查看共享Kit")) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                if (slot == 45) {
                    gui.openCategoryGui(player, "public_kits", 0);
                } else if (slot == 53) {
        boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);

                    if (isRestricted && pData.hasUsed) {
                        gui.setSkipNextClose(player); player.closeInventory();
                        player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l当前世界仅限使用一次Kitloader，您已使用过一次，请死亡复活后再试！"));
                        return;
                    }

                    int ecLimit = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
                    int limit = isRestricted ? ecLimit : plugin.getConfig().getInt("settings.shulker-limits.inventory-max", 3);
                    int shulkers = 0;

                    ItemStack[] kitToLoad = new ItemStack[41];
                    for (int i=0; i<41; i++) {
                        ItemStack it = event.getView().getTopInventory().getItem(i);
                        if (it != null && !it.getType().isAir()) {
                            kitToLoad[i] = it.clone();
                            if (plugin.isKitloaderShulker(it)) shulkers += it.getAmount();
                        }
                    }

                    if (!plugin.isBypassWhitelisted(player) && shulkers > limit) {
                        gui.setSkipNextClose(player); player.closeInventory();
                        plugin.sendMsg(player, "shulker_limit_inventory", "max", String.valueOf(limit));
                        return;
                    }

                    gui.setSkipNextClose(player); player.closeInventory();
                    player.getInventory().clear();
                    for (int i=0; i<36; i++) {
                        if (kitToLoad[i] != null) player.getInventory().setItem(i, kitToLoad[i]);
                    }
                    if (kitToLoad[36] != null) player.getInventory().setBoots(kitToLoad[36]);
                    if (kitToLoad[37] != null) player.getInventory().setLeggings(kitToLoad[37]);
                    if (kitToLoad[38] != null) player.getInventory().setChestplate(kitToLoad[38]);
                    if (kitToLoad[39] != null) player.getInventory().setHelmet(kitToLoad[39]);
                    if (kitToLoad[40] != null) player.getInventory().setItemInOffHand(kitToLoad[40]);
                    data.markKitLoaded(player);

                    if (!pData.hasUsed) { pData.hasUsed = true; data.savePlayerAsync(player.getUniqueId()); }
                    player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#a8ff78&l成功提取并加载了该一键共享Kit！"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
            }
            return;
        }

        if (cleanTitle.contains("上传共享Kit") && !cleanTitle.contains("确认")) {
            if (slot >= 0 && slot <= 40 || slot > 53) {
                if (event.isShiftClick() && slot > 53) {
                    if (clickedItem == null || clickedItem.getType().isAir()) return;
                    event.setCancelled(true);
                    for (int i = 0; i < 41; i++) {
                        ItemStack target = event.getView().getTopInventory().getItem(i);
                        if (target == null || target.getType().isAir()) {
                            event.getView().getTopInventory().setItem(i, clickedItem.clone());
                            event.getCurrentItem().setAmount(0);
                            pData.publicEditSession.items[i] = clickedItem.clone();
                            return;
                        } else if (target.isSimilar(clickedItem) && target.getAmount() < target.getMaxStackSize()) {
                            int space = target.getMaxStackSize() - target.getAmount();
                            if (clickedItem.getAmount() <= space) {
                                target.setAmount(target.getAmount() + clickedItem.getAmount());
                                event.getCurrentItem().setAmount(0);
                                pData.publicEditSession.items[i] = target.clone();
                                return;
                            } else {
                                target.setAmount(target.getMaxStackSize());
                                clickedItem.setAmount(clickedItem.getAmount() - space);
                                pData.publicEditSession.items[i] = target.clone();
                            }
                        }
                    }
                } else if (slot >= 0 && slot <= 40) {
                    player.getScheduler().run(plugin, t -> {
                        ItemStack it = event.getView().getTopInventory().getItem(slot);
                        pData.publicEditSession.items[slot] = (it != null) ? it.clone() : null;
                    }, null);
                }
                return;
            }

            event.setCancelled(true);
            if (clickedItem == null || clickedItem.getType().isAir()) return;

            if (slot == 45) {
                gui.openConfirmPublicCancelGui(player);
            } else if (slot == 49) {
                for (int i = 0; i < 41; i++) {
                    ItemStack it = event.getView().getTopInventory().getItem(i);
                    pData.publicEditSession.items[i] = (it != null) ? it.clone() : null;
                }
                gui.setSkipNextClose(player); player.closeInventory();
                pData.publicEditSession.isNaming = true;
                sendNamingInstructions(player, plugin);
            } else if (slot == 53) {
                for (int i = 0; i < 41; i++) {
                    ItemStack it = event.getView().getTopInventory().getItem(i);
                    pData.publicEditSession.items[i] = (it != null) ? it.clone() : null;
                }
                boolean full = true;
                for (int i = 0; i < 36; i++) {
                    ItemStack it = event.getView().getTopInventory().getItem(i);
                    if (it == null || it.getType().isAir()) { full = false; break; }
                }
                if (!full) {
                    gui.setSkipNextClose(player); player.closeInventory();
                    data.savePlayerAsync(player.getUniqueId());
                    plugin.sendMsg(player, "public_kit_inventory_not_full");
                    return;
                }
                gui.openConfirmPublicUploadGui(player);
            }
            return;
        }

        if (cleanTitle.contains("我的共享Kit")) {
            event.setCancelled(true);
            if (clickedItem == null || clickedItem.getType().isAir() || clickedItem.getType().name().contains("STAINED_GLASS_PANE")) return;

            if (slot == 49) {
                gui.openCategoryGui(player, "public_kits", 0); return;
            }
            if (clickedItem.getType() == Material.CHEST && clickedItem.hasItemMeta()) {
                String kitId = null;
                for (String lore : clickedItem.getItemMeta().getLore()) {
                    String canonicalLore = Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(lore));
                    if (canonicalLore.startsWith("唯一ID: ")) {
                        kitId = canonicalLore.replace("唯一ID: ", "").trim();
                        break;
                    }
                }
                if (kitId != null) {
                    final String fid = kitId;
                    DataManager.PublicKit targetKit = data.publicKits.stream().filter(k -> k.id.equals(fid)).findFirst().orElse(null);
                    if (targetKit != null) {
                        if (event.getClick().isLeftClick()) {
                            gui.openPublicKitEditGui(player, targetKit, false);
                        } else if (event.getClick().isRightClick()) {
                            gui.openConfirmDeletePublicGui(player, kitId);
                        }
                    }
                }
            }
            return;
        }

        if (cleanTitle.contains("编辑共享Kit:")) {
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                if (slot >= 41 && slot <= 53) {
                    event.setCancelled(true);
                    String kitId = gui.getPublicTargetCache(player.getUniqueId());
                    if (slot == 45) {
                        ItemStack[] currentKit = extractKitFromEditGui(event.getView().getTopInventory());
                        DataManager.PublicKit pk = data.publicKits.stream().filter(k -> k.id.equals(kitId)).findFirst().orElse(null);
                        if (pk != null && !isKitChanged(pk.items, currentKit)) {
                            gui.openCategoryGui(player, "public_kits", 0);
                        } else {
                            gui.cachePublicKitEdit(player.getUniqueId(), currentKit);
                            gui.openConfirmSavePublicGui(player);
                        }
                    } else if (slot == 49) {
                        ItemStack[] currentKit = extractKitFromEditGui(event.getView().getTopInventory());
                        gui.cachePublicKitEdit(player.getUniqueId(), currentKit);
                        pData.namingContext = new DataManager.NamingContext(DataManager.NamingContext.Type.PUBLIC_KIT_RENAME, null, kitId, 0);
                        gui.setSkipNextClose(player); player.closeInventory();
                        sendNamingInstructions(player, plugin);
                    } else if (slot == 53) {
                        gui.openConfirmDeletePublicGui(player, kitId);
                    }
                    return;
                }
            }
            return;
        }

        if (cleanTitle.contains("专属末影箱")) {
            event.setCancelled(true);

            int uiSlots = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
            Inventory ec = player.getEnderChest();
            int maxSlots = Math.min(uiSlots, Math.min(45, ec.getSize()));

            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                if (slot == 49) {
                    gui.openCategoryGui(player, "supply", 0);
                    return;
                }

                if (slot >= 0 && slot < maxSlots) {
                    if (clickedItem == null || clickedItem.getType().isAir()) return;

                    long nowClick = System.currentTimeMillis();
                    pData.lastEnderChestPutTime = nowClick;

                    ItemStack cleanItem = gui.createUploadedSupplyDeliveryCopy(clickedItem);
                    plugin.markKitloaderShulker(cleanItem);
                    java.util.HashMap<Integer, ItemStack> left = player.getInventory().addItem(cleanItem);
                    if (left.isEmpty()) {
                        ec.setItem(slot, null);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    } else {
                        ec.setItem(slot, left.get(0));
                        plugin.sendMsg(player, "inventory_full");
                    }
                    gui.openDedicatedEnderChestGui(player);
                }
            }
            else if (event.getClickedInventory() == event.getView().getBottomInventory()) {
                if (clickedItem == null || clickedItem.getType().isAir()) return;

                long nowClick = System.currentTimeMillis();
                pData.lastEnderChestPutTime = nowClick;

                if (plugin.isKitloaderShulker(clickedItem)) {
                    int ecLimit = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
                    int currentShulkers = 0;
                    for (int i = 0; i < maxSlots; i++) {
                        ItemStack ecItem = ec.getItem(i);
                        if (plugin.isKitloaderShulker(ecItem)) currentShulkers += ecItem.getAmount();
                    }
                    if (currentShulkers + clickedItem.getAmount() > ecLimit) {
                        if (nowClick - pData.lastPickupWarningTime > 2000) {
                            plugin.sendMsg(player, "shulker_limit_enderchest", "max", String.valueOf(ecLimit));
                            pData.lastPickupWarningTime = nowClick;
                        }
                        return;
                    }
                }

                boolean added = false;
                for (int i = 0; i < maxSlots; i++) {
                    ItemStack target = ec.getItem(i);
                    if (target == null || target.getType().isAir()) {
                        ItemStack cleanItem = gui.createUploadedSupplyDeliveryCopy(clickedItem);
                        plugin.markKitloaderShulker(cleanItem);
                        ec.setItem(i, cleanItem);
                        clickedItem.setAmount(0);
                        added = true;
                        break;
                    } else if (target.isSimilar(clickedItem) && target.getAmount() < target.getMaxStackSize()) {
                        int space = target.getMaxStackSize() - target.getAmount();
                        if (clickedItem.getAmount() <= space) {
                            target.setAmount(target.getAmount() + clickedItem.getAmount());
                            clickedItem.setAmount(0);
                            added = true;
                            break;
                        } else {
                            target.setAmount(target.getMaxStackSize());
                            clickedItem.setAmount(clickedItem.getAmount() - space);
                        }
                    }
                }

                if (added) {
                    if (!pData.hasUsed) {
                        pData.hasUsed = true;
                        data.savePlayerAsync(player.getUniqueId());
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);

                    boolean isFull = true;
                    for (int i = 0; i < maxSlots; i++) {
                        ItemStack target = ec.getItem(i);
                        if (target == null || target.getType().isAir() || (target.isSimilar(clickedItem) && target.getAmount() < target.getMaxStackSize())) {
                            isFull = false;
                            break;
                        }
                    }

                    if (isFull) {
                        gui.setSkipNextClose(player);
                        player.closeInventory();
                        player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l您的专属末影箱已满，界面已自动关闭！"));
                    } else {
                        gui.openDedicatedEnderChestGui(player);
                    }
                } else {
                    if (nowClick - pData.lastPickupWarningTime > 2000) {
                        plugin.sendMsg(player, "enderchest_full_dedicated");
                        pData.lastPickupWarningTime = nowClick;
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
            return;
        }

        if (cleanTitle.contains("末影箱直存模式")) {
            event.setCancelled(true);

            String rawData = cleanTitle.substring(cleanTitle.indexOf("- P") + 3).trim();
            int currentPage = 0;
            try { currentPage = Integer.parseInt(rawData) - 1; } catch (NumberFormatException ignored) {}

            if (event.getClickedInventory() == event.getView().getBottomInventory()) {
                if (clickedItem == null || !plugin.isKitloaderShulker(clickedItem)) return;
                ItemStack cleanSupply = gui.createUploadedSupplyDeliveryCopy(clickedItem);
                plugin.markKitloaderShulker(cleanSupply);
                if (storeSupplyInConfiguredEnderChest(player, pData, cleanSupply)) {
                    event.setCurrentItem(null);
                    gui.openSupplyEnderChestGui(player, currentPage);
                }
                return;
            }
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;

            if (slot == 36 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                gui.openSupplyEnderChestGui(player, currentPage - 1);
                return;
            }
            if (slot == 40 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                gui.openCategoryGui(player, "supply", currentPage);
                return;
            }
            if (slot == 44 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                gui.openSupplyEnderChestGui(player, currentPage + 1);
                return;
            }

            if (slot >= 0 && slot <= 35) {
                if (clickedItem == null || clickedItem.getType().isAir()) return;

                long nowClick = System.currentTimeMillis();
                pData.lastEnderChestPutTime = nowClick;

                ItemStack realItem = gui.getCachedVisibleSupplyItem(player, currentPage, slot);
                if (realItem == null || realItem.getType().isAir()) return;

                ItemStack given = realItem.clone();
                plugin.markKitloaderShulker(given);

                Inventory ec = player.getEnderChest();
                int ecLimit = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
                int currentShulkers = 0;
                for (int i = 0; i < 9; i++) {
                    ItemStack ecItem = ec.getItem(i);
                    if (plugin.isKitloaderShulker(ecItem)) currentShulkers += ecItem.getAmount();
                }
                if (currentShulkers >= ecLimit) {
                    if (nowClick - pData.lastPickupWarningTime > 2000) {
                        plugin.sendMsg(player, "shulker_limit_enderchest", "max", String.valueOf(ecLimit));
                        pData.lastPickupWarningTime = nowClick;
                    }
                    return;
                }

                boolean added = false;
                for (int i = 0; i < 9; i++) {
                    ItemStack target = ec.getItem(i);
                    if (target == null || target.getType().isAir()) {
                        ec.setItem(i, given);
                        added = true; break;
                    } else if (target.isSimilar(given) && target.getAmount() < target.getMaxStackSize()) {
                        int space = target.getMaxStackSize() - target.getAmount();
                        if (given.getAmount() <= space) {
                            target.setAmount(target.getAmount() + given.getAmount());
                            added = true; break;
                        } else {
                            target.setAmount(target.getMaxStackSize());
                            given.setAmount(given.getAmount() - space);
                        }
                    }
                }

                if (added) {
                    if (!pData.hasUsed) { pData.hasUsed = true; data.savePlayerAsync(player.getUniqueId()); }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);

                    boolean isFull = true;
                    for (int i = 0; i < 9; i++) {
                        ItemStack target = ec.getItem(i);
                        if (target == null || target.getType().isAir() || (target.isSimilar(given) && target.getAmount() < target.getMaxStackSize())) {
                            isFull = false;
                            break;
                        }
                    }

                    if (isFull) {
                        gui.setSkipNextClose(player);
                        player.closeInventory();
                        player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l末影箱前 9 格已满，界面已自动关闭！"));
                    } else {
                        gui.openSupplyEnderChestGui(player, currentPage);
                    }
                } else {
                    if (nowClick - pData.lastPickupWarningTime > 2000) {
                        plugin.sendMsg(player, "enderchest_full_small");
                        pData.lastPickupWarningTime = nowClick;
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }

            if (slot >= 45 && slot <= 53) {
                if (clickedItem == null || clickedItem.getType().isAir()) return;

                long nowClick = System.currentTimeMillis();
                pData.lastEnderChestPutTime = nowClick;

                int ecIndex = slot - 45;
                Inventory ec = player.getEnderChest();
                ItemStack ecItem = ec.getItem(ecIndex);
                if (ecItem != null && !ecItem.getType().isAir()) {
                    ItemStack cleanItem = gui.createUploadedSupplyDeliveryCopy(ecItem);
                    plugin.markKitloaderShulker(cleanItem);
                    java.util.HashMap<Integer, ItemStack> left = player.getInventory().addItem(cleanItem);
                    if (left.isEmpty()) {
                        ec.setItem(ecIndex, null);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    } else {
                        ec.setItem(ecIndex, left.get(0));
                        plugin.sendMsg(player, "inventory_full");
                    }
                    gui.openSupplyEnderChestGui(player, currentPage);
                }
            }
            return;
        }

        String rawKitEditPrefix = cleanText(plugin.getGuiTitle("kit-edit-prefix", ""));

        if (cleanTitle.startsWith(rawKitEditPrefix) || cleanTitle.contains("管理他人Kit")) {
            boolean isOther = cleanTitle.contains("管理他人Kit");
            String kitName; String targetName;

            if (isOther) {
                String[] targetInfo = gui.getAdminTargetCache(player.getUniqueId());
                if (targetInfo == null) return;
                targetName = targetInfo[0]; kitName = targetInfo[1];
            } else {
                kitName = gui.getPlayerTargetCache(player.getUniqueId());
                if (kitName == null) return;
                targetName = player.getName();
            }

        boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);

            if (isRestricted && !isOther) {
                if (event.getClickedInventory() == event.getView().getBottomInventory() && event.isShiftClick()) {
                    event.setCancelled(true); return;
                }
                if (event.getClickedInventory() == event.getView().getTopInventory()) {
                    event.setCancelled(true);
                    if (slot == 45) {
                        gui.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet()));
                    } else if (slot == 53) {
                        plugin.sendMsg(player, "restricted_action");
                    }
                }
                return;
            }

            if (event.getClickedInventory() == event.getView().getBottomInventory() && event.isShiftClick()) {
                moveShiftClickedItemIntoKitEditor(event);
                return;
            }

            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                if (slot >= 41 && slot <= 53) {
                    event.setCancelled(true);
                    if (slot == 45) {
                        ItemStack[] currentKit = extractKitFromEditGui(event.getView().getTopInventory());
                        if (isOther) {
                            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                            DataManager.PlayerData tData = data.getOfflinePlayerData(target.getUniqueId());
                            ItemStack[] originalKit = (tData != null) ? tData.kits.get(kitName) : null;

                            if (!isKitChanged(originalKit, currentKit)) {
                                if (tData != null) gui.openOtherPlayerKitListGui(player, targetName, new ArrayList<>(tData.kits.keySet()));
                            } else {
                                gui.cacheAdminKit(player.getUniqueId(), currentKit);
                                gui.cacheAdminTarget(player.getUniqueId(), targetName, kitName);
                                gui.openConfirmSaveAdminGui(player);
                            }
                        } else {
                            ItemStack[] originalKit = pData.kits.get(kitName);
                            if (!isKitChanged(originalKit, currentKit)) {
                                gui.openPlayerKitListGui(player, new ArrayList<>(pData.kits.keySet()));
                            } else {
                                gui.cachePlayerKit(player.getUniqueId(), currentKit);
                                gui.cachePlayerTarget(player.getUniqueId(), kitName);
                                gui.openConfirmSavePlayerGui(player);
                            }
                        }
                    } else if (slot == 49) {
                        ItemStack[] currentKit = extractKitFromEditGui(event.getView().getTopInventory());
                        if (isOther) {
                            gui.cacheAdminKit(player.getUniqueId(), currentKit);
                            pData.namingContext = new DataManager.NamingContext(DataManager.NamingContext.Type.ADMIN_KIT_RENAME, null, targetName + "@@" + kitName, 0);
                        } else {
                            gui.cachePlayerKit(player.getUniqueId(), currentKit);
                            pData.namingContext = new DataManager.NamingContext(DataManager.NamingContext.Type.KIT_RENAME, null, kitName, 0);
                        }
                        gui.setSkipNextClose(player); player.closeInventory();
                        sendNamingInstructions(player, plugin);
                    } else if (slot == 53) {
                        if (isOther) {
                            gui.cacheAdminTarget(player.getUniqueId(), targetName, kitName);
                            gui.openConfirmDeleteAdminGui(player);
                        } else {
                            gui.cachePlayerTarget(player.getUniqueId(), kitName);
                            gui.openConfirmDeletePlayerGui(player);
                        }
                    }
                    return;
                }

                if (slot >= 36 && slot <= 39) {
                    ItemStack incoming = event.getCursor();
                    if (event.getClick() == ClickType.NUMBER_KEY) {
                        incoming = player.getInventory().getItem(event.getHotbarButton());
                    } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                        incoming = player.getInventory().getItemInOffHand();
                    }
                    if (incoming != null && !incoming.getType().isAir()
                            && !isValidArmor(slot, incoming.getType())) {
                        event.setCancelled(true);
                        plugin.sendMsg(player, "armor_slot_mismatch");
                    }
                }
            }
            return;
        }

        String rawEditPrefix = cleanText(plugin.getGuiTitle("edit-prefix", ""));
        if (cleanTitle.startsWith(rawEditPrefix) && cleanTitle.contains(" - P")) {
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                if (slot >= 36 && slot <= 53) {
                    event.setCancelled(true);
                    if (clickedItem != null && clickedItem.getType() == Material.ARROW) {
                        String remaining = cleanTitle.substring(rawEditPrefix.length());
                        String[] parts = remaining.split(" - P");
                        if (parts.length >= 2) {
                            String category = parts[0].trim();
                            int currentPage = Integer.parseInt(parts[1].trim()) - 1;

                            boolean hasAnyItem = false;
                            ItemStack[] contents = new ItemStack[36];
                            for (int i = 0; i < 36; i++) {
                                contents[i] = event.getView().getTopInventory().getItem(i);
                                if (contents[i] != null && !contents[i].getType().isAir()) hasAnyItem = true;
                            }

                            boolean isChanged = isCategoryPageChanged(category, currentPage, contents);

                            if (slot == 45 && currentPage > 0) {
                                if (isChanged) {
                                    if (hasAnyItem) {
                                        gui.saveCategoryItems(category, currentPage, contents);
                                        plugin.sendMsg(player, "gui_saved", "category", category, "page", String.valueOf(currentPage + 1));
                                    } else {
                                        if (!isCategoryPageOriginallyEmpty(category, currentPage)) {
                                            plugin.sendMsg(player, "category_empty_nosave");
                                        }
                                    }
                                }
                                gui.openEditGui(player, category, currentPage - 1);
                            } else if (slot == 53) {
                                if (isChanged) {
                                    if (hasAnyItem) {
                                        gui.saveCategoryItems(category, currentPage, contents);
                                        plugin.sendMsg(player, "gui_saved", "category", category, "page", String.valueOf(currentPage + 1));
                                    } else {
                                        if (!isCategoryPageOriginallyEmpty(category, currentPage)) {
                                            plugin.sendMsg(player, "category_empty_nosave");
                                        }
                                    }
                                }
                                gui.openEditGui(player, category, currentPage + 1);
                            }
                        }
                    }
                }
            }
            return;
        }

        if (cleanTitle.contains("个人Kit列表") || cleanTitle.contains("玩家Kit")) {
            event.setCancelled(true);
            if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType().name().contains("STAINED_GLASS_PANE")) return;

            final boolean isOther = cleanTitle.contains("玩家Kit");
            final String finalTargetName = isOther ? cleanTitle.substring(cleanTitle.indexOf(":") + 1).trim() : player.getName();

            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(finalTargetName);
            DataManager.PlayerData targetData = data.getOfflinePlayerData(target.getUniqueId());
            if (targetData == null) return;

            if (clickedItem.getType() == Material.CHEST && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                final String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                if (!targetData.kits.containsKey(displayName)) return;

                if (event.getClick().isLeftClick()) {
                    if (plugin.isRestrictedKitloaderPlayer(player) && pData.hasUsed) {
                        plugin.sendMsg(player, "single_use_limit");
                        return;
                    }
                    gui.setSkipNextClose(player);
                    player.closeInventory();
                    player.getScheduler().run(plugin, t -> {
                        ItemStack[] bkKit = targetData.kits.get(displayName);
                        player.getInventory().clear();
                        for (int i = 0; i < 36; i++) {
                            if (i >= bkKit.length || bkKit[i] == null) continue;
                            ItemStack item = bkKit[i].clone();
                            plugin.markKitloaderShulker(item);
                            player.getInventory().setItem(i, item);
                        }
                        if (bkKit.length >= 41) {
                            player.getInventory().setBoots(bkKit[36] != null ? bkKit[36].clone() : null);
                            player.getInventory().setLeggings(bkKit[37] != null ? bkKit[37].clone() : null);
                            player.getInventory().setChestplate(bkKit[38] != null ? bkKit[38].clone() : null);
                            player.getInventory().setHelmet(bkKit[39] != null ? bkKit[39].clone() : null);
                            player.getInventory().setItemInOffHand(bkKit[40] != null ? bkKit[40].clone() : null);
                        }
                        plugin.sanitizePlayerShulkers(player);
                        data.markKitLoaded(player);
                        if (!pData.hasUsed) {
                            pData.hasUsed = true;
                            data.savePlayerAsync(player.getUniqueId());
                        }
                        plugin.sendMsg(player, "kit_loaded", "kit", displayName);
                    }, null);
                } else if (event.getClick().isRightClick()) {
                    if (isOther && plugin.isRestrictedKitloaderPlayer(player)) {
                        plugin.sendMsg(player, "restricted_action");
                    } else if (isOther) gui.openOtherPlayerKitEditGui(player, finalTargetName, displayName);
                    else gui.openKitEditGui(player, displayName);
                }
            }
            return;
        }

        if (cleanTitle.contains("附魔与物品编辑")) {
            event.setCancelled(true);
            if (pData.editItemSession == null) return;

            if (clickedItem != null && clickedItem.getType() == Material.ENCHANTED_BOOK) {
                org.bukkit.enchantments.Enchantment targetEnc = null;

                if (clickedItem.hasItemMeta()) {
                    if (clickedItem.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                        org.bukkit.inventory.meta.EnchantmentStorageMeta em = (org.bukkit.inventory.meta.EnchantmentStorageMeta) clickedItem.getItemMeta();
                        if (!em.getStoredEnchants().isEmpty()) targetEnc = em.getStoredEnchants().keySet().iterator().next();
                    } else if (!clickedItem.getItemMeta().getEnchants().isEmpty()) {
                        targetEnc = clickedItem.getItemMeta().getEnchants().keySet().iterator().next();
                    }
                }

                if (targetEnc == null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                    String clickedName = cleanText(clickedItem.getItemMeta().getDisplayName()).replace(" ", "").toLowerCase();
                    for (org.bukkit.enchantments.Enchantment enc : Registry.ENCHANTMENT) {
                        String eName = cleanText(gui.getEnchantName(enc)).replace(" ", "").toLowerCase();
                        if (!eName.isEmpty() && clickedName.equals(eName)) {
                            targetEnc = enc;
                            break;
                        }
                    }
                }

                if (targetEnc != null) {
                    ItemStack currentItem = pData.editItemSession.currentItem;
                    ItemMeta meta = currentItem.getItemMeta();
                    if (meta == null) meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(currentItem.getType());

                    boolean isBook = (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta);
                    org.bukkit.inventory.meta.EnchantmentStorageMeta em = isBook ? (org.bukkit.inventory.meta.EnchantmentStorageMeta) meta : null;

                    boolean hasEnchant = isBook ? em.hasStoredEnchant(targetEnc) : meta.hasEnchant(targetEnc);

                    if (hasEnchant) {
                        if (isBook) em.removeStoredEnchant(targetEnc);
                        else meta.removeEnchant(targetEnc);
                        currentItem.setItemMeta(meta);
                        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.0f);
                    } else {
                        if (!targetEnc.canEnchantItem(currentItem)) {
                            String cleanEncName = cleanText(gui.getEnchantName(targetEnc));
                            rejectEnchant(player, pData, "enchant_unsupported", cleanEncName);
                        } else {
                            boolean conflict = false;
                            org.bukkit.enchantments.Enchantment conflictEnc = null;

                            if (!isBook) {
                                for (org.bukkit.enchantments.Enchantment existing : meta.getEnchants().keySet()) {
                                    if (targetEnc.conflictsWith(existing)) {
                                        conflictEnc = existing;
                                        conflict = true;
                                        break;
                                    }
                                }
                            } else {
                                for (org.bukkit.enchantments.Enchantment existing : em.getStoredEnchants().keySet()) {
                                    if (targetEnc.conflictsWith(existing)) {
                                        conflictEnc = existing;
                                        conflict = true;
                                        break;
                                    }
                                }
                            }

                            if (conflict) {
                                String cleanConflictName = cleanText(gui.getEnchantName(conflictEnc));
                                rejectEnchant(player, pData, "enchant_conflict", cleanConflictName);
                            } else {
                                if (isBook) em.addStoredEnchant(targetEnc, targetEnc.getMaxLevel(), true);
                                else meta.addEnchant(targetEnc, targetEnc.getMaxLevel(), true);
                                currentItem.setItemMeta(meta);
                                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.0f);
                            }
                        }
                    }
                    gui.openEnchantGui(player);
                }
            } else if (slot == 45) {
                gui.openCategoryGui(player, pData.editItemSession.category, pData.editItemSession.page);
            } else if (slot == 47 && clickedItem != null && clickedItem.getType() == Material.SMITHING_TABLE) {
                gui.openArmorTrimGui(player);
            } else if ((slot == 49 || slot == 51) && clickedItem != null && clickedItem.getType() == Material.NAME_TAG) {
                pData.namingContext = new DataManager.NamingContext(DataManager.NamingContext.Type.EDIT_SESSION, pData.editItemSession.currentItem.clone(), pData.editItemSession.category, pData.editItemSession.page);
                gui.setSkipNextClose(player); player.closeInventory();
                sendNamingInstructions(player, plugin);
            } else if (slot == 53) {
                ItemStack given = pData.editItemSession.currentItem.clone();
                plugin.markKitloaderShulker(given);
                String cat = pData.editItemSession.category;
                int page = pData.editItemSession.page;
                pData.editItemSession = null;

                gui.setSkipNextClose(player); player.closeInventory();
                player.getInventory().addItem(given);
                if (!pData.hasUsed) { pData.hasUsed = true; data.savePlayerAsync(player.getUniqueId()); }
                gui.openCategoryGui(player, cat, page);
            }
            return;
        }

        if (cleanTitle.contains("盔甲纹饰与名称")) {
            event.setCancelled(true);
            if (pData.editItemSession == null) return;

            if (clickedItem != null && clickedItem.getType().name().contains("TRIM_SMITHING_TEMPLATE")) {
                org.bukkit.inventory.meta.trim.TrimPattern pat = getPatternFromMaterial(clickedItem.getType());
                if (pat != null) {
                    ItemMeta meta = pData.editItemSession.currentItem.getItemMeta();
                    if (meta instanceof org.bukkit.inventory.meta.ArmorMeta) {
                        org.bukkit.inventory.meta.ArmorMeta armorMeta = (org.bukkit.inventory.meta.ArmorMeta) meta;

                        if (armorMeta.hasTrim() && armorMeta.getTrim().getPattern().equals(pat)) {
                            armorMeta.setTrim(null);
                            pData.editItemSession.currentItem.setItemMeta(armorMeta);
                            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.0f);
                            plugin.sendMsg(player, "trim_removed");
                        } else {
                            applyTrim(pData.editItemSession.currentItem, pat, null);
                            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.0f);
                        }
                    }
                }
                gui.openArmorTrimGui(player);
            } else if (clickedItem != null && !clickedItem.getType().name().contains("GLASS_PANE") && clickedItem.getType() != Material.ARROW && clickedItem.getType() != Material.NAME_TAG && slot != 4 && slot != 53) {
                org.bukkit.inventory.meta.trim.TrimMaterial mat = getTrimMaterialFromItem(clickedItem.getType());
                if (mat != null) {
                    ItemMeta meta = pData.editItemSession.currentItem.getItemMeta();
                    if (meta instanceof org.bukkit.inventory.meta.ArmorMeta) {
                        org.bukkit.inventory.meta.ArmorMeta armorMeta = (org.bukkit.inventory.meta.ArmorMeta) meta;

                        if (armorMeta.hasTrim() && armorMeta.getTrim().getMaterial().equals(mat)) {
                            armorMeta.setTrim(null);
                            pData.editItemSession.currentItem.setItemMeta(armorMeta);
                            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.0f);
                            plugin.sendMsg(player, "trim_removed");
                        } else {
                            applyTrim(pData.editItemSession.currentItem, null, mat);
                            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.0f);
                        }
                    }
                }
                gui.openArmorTrimGui(player);
            } else if (slot == 45) {
                gui.openEnchantGui(player);
            } else if (slot == 49) {
                pData.namingContext = new DataManager.NamingContext(DataManager.NamingContext.Type.EDIT_SESSION, pData.editItemSession.currentItem.clone(), pData.editItemSession.category, pData.editItemSession.page);
                gui.setSkipNextClose(player); player.closeInventory();
                sendNamingInstructions(player, plugin);
            } else if (slot == 53) {
                ItemStack given = pData.editItemSession.currentItem.clone();
                plugin.markKitloaderShulker(given);
                String cat = pData.editItemSession.category;
                int page = pData.editItemSession.page;
                pData.editItemSession = null;

                gui.setSkipNextClose(player); player.closeInventory();
                player.getInventory().addItem(given);
                if (!pData.hasUsed) { pData.hasUsed = true; data.savePlayerAsync(player.getUniqueId()); }
                gui.openCategoryGui(player, cat, page);
            }
            return;
        }

        if (cleanTitle.contains("自定义补给盒")) {
            if (slot >= 0 && slot <= 26 || slot > 53) {
                if (event.isShiftClick() && slot > 53) {
                    if (clickedItem == null || clickedItem.getType().isAir()) return;
                    if (clickedItem.getType().name().endsWith("SHULKER_BOX")) {
                        event.setCancelled(true);
                        plugin.sendMsg(player, "shulker_nesting_forbidden");
                        return;
                    }
                    event.setCancelled(true);
                    for (int i = 0; i < 27; i++) {
                        ItemStack target = event.getView().getTopInventory().getItem(i);
                        if (target == null || target.getType().isAir()) {
                            event.getView().getTopInventory().setItem(i, clickedItem.clone());
                            event.getCurrentItem().setAmount(0);
                            pData.editSession.items[i] = clickedItem.clone();
                            return;
                        } else if (target.isSimilar(clickedItem) && target.getAmount() < target.getMaxStackSize()) {
                            int space = target.getMaxStackSize() - target.getAmount();
                            if (clickedItem.getAmount() <= space) {
                                target.setAmount(target.getAmount() + clickedItem.getAmount());
                                event.getCurrentItem().setAmount(0);
                                pData.editSession.items[i] = target.clone();
                                return;
                            } else {
                                target.setAmount(target.getMaxStackSize());
                                clickedItem.setAmount(clickedItem.getAmount() - space);
                                pData.editSession.items[i] = target.clone();
                            }
                        }
                    }
                } else if (slot >= 0 && slot <= 26) {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && cursor.getType().name().endsWith("SHULKER_BOX")) {
                        event.setCancelled(true);
                        plugin.sendMsg(player, "shulker_nesting_forbidden");
                        return;
                    }
                    if (event.getClick() == ClickType.NUMBER_KEY) {
                        ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                        if (hotbarItem != null && hotbarItem.getType().name().endsWith("SHULKER_BOX")) {
                            event.setCancelled(true);
                            plugin.sendMsg(player, "shulker_nesting_forbidden");
                            return;
                        }
                    } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                        ItemStack offhandItem = player.getInventory().getItemInOffHand();
                        if (offhandItem != null && offhandItem.getType().name().endsWith("SHULKER_BOX")) {
                            event.setCancelled(true);
                            plugin.sendMsg(player, "shulker_nesting_forbidden");
                            return;
                        }
                    }
                    player.getScheduler().run(plugin, t -> {
                        ItemStack it = event.getView().getTopInventory().getItem(slot);
                        pData.editSession.items[slot] = (it != null) ? it.clone() : null;
                    }, null);
                }
                return;
            }

            event.setCancelled(true);
            if (clickedItem == null) return;

            if (slot == 47) {
                for (int i = 0; i < 27; i++) {
                    ItemStack it = event.getView().getTopInventory().getItem(i);
                    pData.editSession.items[i] = (it != null) ? it.clone() : null;
                }
                int currentIdx = Arrays.asList(GuiManager.SHULKERS).indexOf(pData.editSession.color);
                pData.editSession.color = GuiManager.SHULKERS[(currentIdx + 1) % GuiManager.SHULKERS.length];

                gui.openCustomSupplyEditGui(player);
                return;
            } else if (slot == 49) {
                for (int i = 0; i < 27; i++) {
                    ItemStack it = event.getView().getTopInventory().getItem(i);
                    pData.editSession.items[i] = (it != null) ? it.clone() : null;
                }
                gui.setSkipNextClose(player); player.closeInventory();
                data.savePlayerAsync(player.getUniqueId());
                plugin.sendMsg(player, "supply_temporarily_saved");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.0f);
            } else if (slot == 51) {
                for (int i = 0; i < 27; i++) {
                    ItemStack it = event.getView().getTopInventory().getItem(i);
                    pData.editSession.items[i] = (it != null) ? it.clone() : null;
                }
                gui.setSkipNextClose(player); player.closeInventory();
                pData.editSession.isNaming = true;
                sendNamingInstructions(player, plugin);
                return;
            } else if (slot == 45) {
                boolean hasItem = false;
                for (ItemStack item : pData.editSession.items) if (item != null && !item.getType().isAir()) { hasItem = true; break; }
                if (!hasItem) {
                    gui.setSkipNextClose(player); player.closeInventory();
                    player.getScheduler().run(plugin, t -> player.performCommand("kitloader"), null);
                } else {
                    gui.openConfirmGui(player, "&#34495E&l确认放弃编辑？", "放弃编辑", "继续编辑");
                }
            } else if (slot == 53) {
                for (int i = 0; i < 27; i++) {
                    ItemStack it = event.getView().getTopInventory().getItem(i);
                    pData.editSession.items[i] = (it != null) ? it.clone() : null;
                }
                if (!validateSupplyContents(player, pData.editSession.items)) return;
                gui.openConfirmGui(player, "&#34495E&l确认上传补给？", "确认打包", "返回编辑");
            }
            return;
        }

        if (cleanTitle.contains("确认放弃编辑？")) {
            event.setCancelled(true);
            if (slot == 11) { pData.editSession = null; gui.setSkipNextClose(player); player.closeInventory(); player.performCommand("kitloader"); }
            else if (slot == 15) { gui.openCustomSupplyEditGui(player); }
            return;
        }

        if (cleanTitle.contains("确认上传补给？")) {
            event.setCancelled(true);
            if (slot == 11) {
                int limit = plugin.getConfig().getInt("settings.custom-supply.max-limit", 5);
                if (pData.uploadedSupplies.size() >= limit) {
                    plugin.sendMsg(player, "supply_upload_limit", "limit", String.valueOf(limit));
                    gui.setSkipNextClose(player); player.closeInventory(); player.performCommand("kitloader"); return;
                }
                ItemStack shulker = new ItemStack(pData.editSession.color);
                if (!validateSupplyContents(player, pData.editSession.items)) return;
                BlockStateMeta meta = (BlockStateMeta) shulker.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Kitloader.color(pData.editSession.name));
                    ShulkerBox box = (ShulkerBox) meta.getBlockState();
                    box.getInventory().setContents(pData.editSession.items);
                    meta.setBlockState(box); shulker.setItemMeta(meta);
                }
                gui.ensureUploadedSupplyMetadata(player, pData);
                String supplyId = gui.prepareUploadedSupply(player, shulker, pData.uploadedSuppliesVisible);
                if (supplyId == null) {
                    plugin.sendMsg(player, "supply_upload_duplicate");
                    return;
                }
                pData.uploadedSupplies.add(shulker);
                pData.uploadedSupplyIds.add(supplyId);

                pData.editSession = null;
                data.savePlayerAsync(player.getUniqueId());
                plugin.sendMsg(player, "supply_upload_success");
                gui.setSkipNextClose(player); player.closeInventory(); player.performCommand("kitloader");
            } else if (slot == 15) { gui.openCustomSupplyEditGui(player); }
            return;
        }

        if (cleanTitle.contains("确认删除补给:")) {
            event.setCancelled(true);
            if (slot == 11) {
                deleteCachedUploadedSupply(player, pData);
                gui.openUploadedSuppliesGui(player);
            } else if (slot == 15) {
                gui.clearUploadedSupplyTarget(player.getUniqueId());
                gui.openUploadedSuppliesGui(player);
            }
            return;
        }

        if (cleanTitle.contains("已上传的补给")) {
            event.setCancelled(true);
            int currentPage = 0;
            int pageMarker = cleanTitle.lastIndexOf("- P");
            if (pageMarker >= 0) {
                try {
                    currentPage = Math.max(0, Integer.parseInt(cleanTitle.substring(pageMarker + 3).trim()) - 1);
                } catch (NumberFormatException ignored) {}
            }

            int pageSize = gui.getUploadedSupplyPageSize();
            int toolbarStart = pageSize;
            int enderStart = toolbarStart + 9;
            int enderSlots = Math.min(gui.getUploadedSupplyEnderSlots(), player.getEnderChest().getSize());

            if (event.getClickedInventory() == event.getView().getBottomInventory()) {
                if (clickedItem == null || !plugin.isKitloaderShulker(clickedItem)) return;
                ItemStack cleanSupply = gui.createUploadedSupplyDeliveryCopy(clickedItem);
                plugin.markKitloaderShulker(cleanSupply);
                if (storeSupplyInConfiguredEnderChest(player, pData, cleanSupply)) {
                    event.setCurrentItem(null);
                    gui.openUploadedSuppliesGui(player, currentPage);
                }
                return;
            }

            if (slot == toolbarStart && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                gui.openUploadedSuppliesGui(player, currentPage - 1);
                return;
            }
            if (slot == toolbarStart + 4 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                gui.openCategoryGui(player, "supply", 0);
                return;
            }
            if (slot == toolbarStart + 6 && clickedItem != null
                    && (clickedItem.getType() == Material.LIME_DYE || clickedItem.getType() == Material.GRAY_DYE)) {
                boolean visible = gui.toggleUploadedSuppliesVisibility(player);
                plugin.sendMsg(player, visible ? "supply_visibility_public" : "supply_visibility_hidden");
                gui.openUploadedSuppliesGui(player, currentPage);
                return;
            }
            if (slot == toolbarStart + 8 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                gui.openUploadedSuppliesGui(player, currentPage + 1);
                return;
            }

            if (slot >= enderStart && slot < enderStart + enderSlots) {
                int enderIndex = slot - enderStart;
                Inventory enderChest = player.getEnderChest();
                ItemStack enderItem = enderChest.getItem(enderIndex);
                if (enderItem == null || enderItem.getType().isAir()) return;

                ItemStack cleanItem = gui.createUploadedSupplyDeliveryCopy(enderItem);
                plugin.markKitloaderShulker(cleanItem);
                java.util.HashMap<Integer, ItemStack> left = player.getInventory().addItem(cleanItem);
                if (left.isEmpty()) {
                    enderChest.setItem(enderIndex, null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                } else {
                    enderChest.setItem(enderIndex, left.get(0));
                    plugin.sendMsg(player, "inventory_full");
                }
                gui.openUploadedSuppliesGui(player, currentPage);
                return;
            }

            if (slot >= 0 && slot < pageSize) {
                int supplyIndex = currentPage * pageSize + slot;
                if (supplyIndex < 0 || supplyIndex >= pData.uploadedSupplies.size()) return;

                ItemStack targetBox = pData.uploadedSupplies.get(supplyIndex);
                String boxName = targetBox.hasItemMeta() && targetBox.getItemMeta().hasDisplayName()
                        ? targetBox.getItemMeta().getDisplayName()
                        : Kitloader.color("&#34495E&l未命名潜影盒");

                if (event.getClick().isLeftClick() || event.getClick() == ClickType.SHIFT_RIGHT) {
                    ItemStack supply = gui.createUploadedSupplyDeliveryCopy(targetBox);
                    plugin.markKitloaderShulker(supply);
                    if (storeSupplyInConfiguredEnderChest(player, pData, supply)) {
                        if (areConfiguredEnderChestSlotsFull(player)) {
                            gui.setSkipNextClose(player);
                            player.closeInventory();
                            player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l当前开放的 "
                                    + enderSlots + " 格末影箱已满，界面已自动关闭！"));
                        } else {
                            gui.openUploadedSuppliesGui(player, currentPage);
                        }
                    }
                } else if (event.getClick().isRightClick()) {
                    if (supplyIndex >= pData.uploadedSupplyIds.size()) return;
                    String supplyId = pData.uploadedSupplyIds.get(supplyIndex);
                    if (supplyId == null || supplyId.isBlank()) return;
                    gui.cacheUploadedSupplyTarget(player.getUniqueId(), supplyId);
                    gui.openConfirmGui(player,
                            "&#CB2D3E&l确&#CB3042&l认&#CB3245&l删&#CB3549&l除&#CB384C&l补&#CB3A50&l给&#CB3D53&l:&f&l" + boxName,
                            "永久删除", "取消");
                }
            }
            return;
        }

        String rawCategoryPrefix = cleanText(plugin.getGuiTitle("category-prefix", ""));
        if (cleanTitle.startsWith(rawCategoryPrefix) && cleanTitle.contains(" - P")) {
            String rawData = cleanTitle.substring(rawCategoryPrefix.length());
            String[] titleParts = rawData.split(" - P");
            String currentDisplay = titleParts[0].trim();
            String currentCategory = gui.getCategoryByDisplay(currentDisplay);

            int currentPage = 0;
            if (titleParts.length > 1) {
                try { currentPage = Integer.parseInt(titleParts[1].trim()) - 1; } catch (NumberFormatException ignored) {}
            }

            if (event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                return;
            }

            if (event.getClickedInventory() == event.getView().getBottomInventory()) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                    if (clickedItem == null || clickedItem.getType().isAir()) return;

                    Inventory topInv = event.getView().getTopInventory();
                    int amount = clickedItem.getAmount();
                    int originalAmount = amount;

                    for (int i = 36; i <= 44; i++) {
                if (currentCategory.equals("supply") && (i == 38 || i == 42 || i == 40)) continue;
                        if (cleanTitle.contains("一键Kit") && (i == 39 || i == 41)) continue;

                        ItemStack target = topInv.getItem(i);
                        if (target == null || target.getType().isAir() || isTrashPane(target)) {
                            ItemStack clone = clickedItem.clone();
                            clone.setAmount(amount);
                            topInv.setItem(i, clone);
                            event.setCurrentItem(null);
                            return;
                        } else if (target.isSimilar(clickedItem)) {
                            int space = target.getMaxStackSize() - target.getAmount();
                            if (space > 0) {
                                if (amount <= space) {
                                    target.setAmount(target.getAmount() + amount);
                                    event.setCurrentItem(null);
                                    return;
                                } else {
                                    target.setAmount(target.getMaxStackSize());
                                    amount -= space;
                                }
                            }
                        }
                    }
                    if (amount != originalAmount) clickedItem.setAmount(amount);
                }
                return;
            }

            if (event.getClickedInventory() == null
                    || event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (slot == 45 || slot == 53) {
                event.setCancelled(true);
                if (clickedItem != null && clickedItem.getType() == Material.ARROW) {
                    if (slot == 45 && currentPage > 0) gui.openCategoryGui(player, currentCategory, currentPage - 1);
                    else if (slot == 53) gui.openCategoryGui(player, currentCategory, currentPage + 1);
                }
                return;
            }

            if (slot >= 36 && slot <= 44) {
                if (currentCategory.equals("supply") && (slot == 38 || slot == 40 || slot == 42)) {
                    event.setCancelled(true);
                    if (slot == 38) {
                        if (!plugin.getConfig().getBoolean("settings.custom-supply.enabled", true)) {
                            plugin.sendMsg(player, "supply_upload_disabled");
                            return;
                        }
                        gui.openCustomSupplyEditGui(player); return;
                    }
                    else if (slot == 40) {
                        int uiSlots = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
                        if (uiSlots > 9) {
                            gui.openDedicatedEnderChestGui(player);
                        } else {
                            gui.openSupplyEnderChestGui(player, 0);
                        }
                        return;
                    }
                    else if (slot == 42) { gui.openUploadedSuppliesGui(player); return; }
                    return;
                }

                if (cleanTitle.contains("一键Kit") && (slot == 39 || slot == 41)) {
                    event.setCancelled(true);
                    if (slot == 39) {
                        if (!plugin.getConfig().getBoolean("settings.public-kits.upload-enabled", true)) {
                            plugin.sendMsg(player, "public_kit_upload_disabled");
                            return;
                        }

                        int limit = plugin.getConfig().getInt("settings.public-kits.max-limit", 5);
                        long currentCount = data.publicKits.stream().filter(k -> k.uploaderUuid.equals(player.getUniqueId())).count();
                        if (currentCount >= limit) {
                            plugin.sendMsg(player, "public_kit_upload_limit", "limit", String.valueOf(limit));
                            return;
                        }

                        if (!isPlayerStorageFull(player)) {
                            plugin.sendMsg(player, "public_kit_inventory_not_full");
                            return;
                        }

                        pData.publicEditSession = new DataManager.EditPublicKitSession();
                        for (int i=0; i<36; i++) pData.publicEditSession.items[i] = player.getInventory().getItem(i) != null ? player.getInventory().getItem(i).clone() : null;
                        pData.publicEditSession.items[36] = player.getInventory().getBoots() != null ? player.getInventory().getBoots().clone() : null;
                        pData.publicEditSession.items[37] = player.getInventory().getLeggings() != null ? player.getInventory().getLeggings().clone() : null;
                        pData.publicEditSession.items[38] = player.getInventory().getChestplate() != null ? player.getInventory().getChestplate().clone() : null;
                        pData.publicEditSession.items[39] = player.getInventory().getHelmet() != null ? player.getInventory().getHelmet().clone() : null;
                        pData.publicEditSession.items[40] = player.getInventory().getItemInOffHand() != null ? player.getInventory().getItemInOffHand().clone() : null;

                        String baseName = player.getName();
                        String newName = baseName;
                        if (currentCount > 0) {
                            newName = baseName + "-" + currentCount;
                        }
                        int safety = (int) currentCount;
                        while (true) {
                            final String checkName = newName;
                            boolean exists = data.publicKits.stream().anyMatch(k -> k.kitName.equalsIgnoreCase(checkName));
                            if (!exists) break;
                            safety++;
                            newName = baseName + "-" + safety;
                        }
                        pData.publicEditSession.name = newName;
                        gui.openConfirmPublicUploadGui(player);
                        return;
                    }
                    else if (slot == 41) { gui.openMyPublicKitsGui(player); return; }
                    return;
                }

                if (isTrashPane(clickedItem)) {
                    event.setCancelled(true);
                    ItemStack cursor = event.getCursor();
                    if (event.getClick() == ClickType.NUMBER_KEY) {
                        ItemStack hotbarItem = event.getView().getBottomInventory().getItem(event.getHotbarButton());
                        if (hotbarItem != null && !hotbarItem.getType().isAir()) {
                            event.getView().getTopInventory().setItem(slot, hotbarItem.clone());
                            event.getView().getBottomInventory().setItem(event.getHotbarButton(), null);
                        }
                    } else if (cursor != null && !cursor.getType().isAir()) {
                        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
                            event.getView().getTopInventory().setItem(slot, cursor.clone());
                            event.getView().setCursor(null);
                        } else if (event.getClick() == ClickType.RIGHT) {
                            ItemStack place = cursor.clone();
                            place.setAmount(1);
                            event.getView().getTopInventory().setItem(slot, place);
                            cursor.setAmount(cursor.getAmount() - 1);
                            if (cursor.getAmount() <= 0) event.getView().setCursor(null);
                        }
                    }
                }
                return;
            }

            event.setCancelled(true);
            if (clickedItem == null || clickedItem.getType().isAir()) return;

            if (slot >= 45 && slot <= 53) {
                if (clickedItem.getType().name().contains("STAINED_GLASS_PANE")) return;

                if (slot == 46 || slot == 48 || slot == 50 || slot == 52) {
                    List<String> categories = gui.getCategories();
                    int index = (slot == 46) ? 0 : (slot == 48) ? 1 : (slot == 50) ? 2 : 3;
                    if (slot == 52) {
                        gui.openCategoryGui(player, "public_kits", 0);
                    } else if (index < categories.size()) {
                        gui.openCategoryGui(player, categories.get(index), 0);
                    }
                }
                return;
            }

            if (slot >= 0 && slot <= 35) {
                if (currentCategory.equals("public_kits")) {
                    List<DataManager.PublicKit> pks = data.publicKits;
                    int index = currentPage * 36 + slot;
                    if (index >= 0 && index < pks.size()) {
                        DataManager.PublicKit targetPk = pks.get(index);
                        if (event.getClick().isLeftClick()) {
        boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);

                            if (isRestricted && pData.hasUsed) {
                                gui.setSkipNextClose(player); player.closeInventory();
                                player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#ff5e62&l当前世界仅限使用一次Kitloader，您已使用过一次，请死亡复活后再试！"));
                                return;
                            }

                            int ecLimit = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
                            int limit = isRestricted ? ecLimit : plugin.getConfig().getInt("settings.shulker-limits.inventory-max", 3);
                            int shulkers = 0;
                            for (ItemStack it : targetPk.items) {
                                if (plugin.isKitloaderShulker(it)) shulkers += it.getAmount();
                            }
                            if (!plugin.isBypassWhitelisted(player) && shulkers > limit) {
                                gui.setSkipNextClose(player); player.closeInventory();
                                plugin.sendMsg(player, "shulker_limit_inventory", "max", String.valueOf(limit));
                                return;
                            }

                            gui.setSkipNextClose(player); player.closeInventory();
                            player.getInventory().clear();
                            for (int i=0; i<36; i++) {
                                if (targetPk.items[i] != null) player.getInventory().setItem(i, targetPk.items[i].clone());
                            }
                            if (targetPk.items[36] != null) player.getInventory().setBoots(targetPk.items[36].clone());
                            if (targetPk.items[37] != null) player.getInventory().setLeggings(targetPk.items[37].clone());
                            if (targetPk.items[38] != null) player.getInventory().setChestplate(targetPk.items[38].clone());
                            if (targetPk.items[39] != null) player.getInventory().setHelmet(targetPk.items[39].clone());
                            if (targetPk.items[40] != null) player.getInventory().setItemInOffHand(targetPk.items[40].clone());
                            data.markKitLoaded(player);

                            if (!pData.hasUsed) { pData.hasUsed = true; data.savePlayerAsync(player.getUniqueId()); }
                            player.sendMessage(Kitloader.color("&#00d2ff&l[&#3a7bd5&lKitloader&#00d2ff&l] &8&l» &#a8ff78&l成功提取并加载了该一键共享Kit！"));
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        } else if (event.getClick().isRightClick()) {
                            String adminPerm = plugin.getConfig().getString("settings.admin-permission", "kitloader.admin");
                            if (player.isOp() || player.hasPermission(adminPerm)) {
                                gui.openPublicKitEditGui(player, targetPk, false);
                            } else {
                                gui.openPublicKitViewGui(player, targetPk);
                            }
                        }
                    }
                    return;
                }

                ItemStack realItem = currentCategory.equals("supply")
                        ? gui.getCachedVisibleSupplyItem(player, currentPage, slot)
                        : gui.getVisibleCategoryItem(player, currentCategory, currentPage, slot);
                if (realItem == null || realItem.getType().isAir()) return;
                boolean enchantable = gui.isEnchantable(realItem);

                if (!currentCategory.equals("supply")) {
                    if (event.getClick() == ClickType.RIGHT || (event.getClick() == ClickType.LEFT && enchantable)) {
                        if (enchantable) {
                            pData.editItemSession = new DataManager.EditItemSession(realItem, currentCategory, currentPage);
                            gui.openEnchantGui(player);
                        } else {
                            pData.namingContext = new DataManager.NamingContext(DataManager.NamingContext.Type.DIRECT, realItem, currentCategory, currentPage);
                            gui.setSkipNextClose(player);
                            player.closeInventory();
                            sendNamingInstructions(player, plugin);
                        }
                        return;
                    }
                }

                ItemStack given = realItem.clone();
                if (!plugin.isBypassWhitelisted(player)
                        && given.getType().name().endsWith("SHULKER_BOX")) {
                    List<String> specialWorlds = plugin.getConfig().getStringList("settings.shulker-limits.special-limit-worlds");
                    boolean inSpecialWorld = specialWorlds.contains(player.getWorld().getName());
                    int ecLimit = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
                    int limit = inSpecialWorld ? ecLimit : plugin.getConfig().getInt("settings.shulker-limits.inventory-max", 3);
                    int shulkers = countShulkers(player.getInventory());

                    if (shulkers + given.getAmount() > limit) {
                        gui.setSkipNextClose(player);
                        player.closeInventory();
                        plugin.sendMsg(player, "shulker_limit_inventory", "max", String.valueOf(limit));
                        return;
                    }
                }

                if (!beginTemplatePickup(event, player, given)) return;

                if (!pData.hasUsed) {
                    pData.hasUsed = true;
                    data.savePlayerAsync(player.getUniqueId());
                }
            }
        }
    }
}
