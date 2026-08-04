package com.lazyz.kitloader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegearCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 47;
    private static final int CLOSE_SLOT = 49;
    private static final int VISIBILITY_SLOT = 51;
    private static final int NEXT_SLOT = 53;

    private final Kitloader plugin;
    private final DataManager data;
    private final GuiManager gui;
    private final Map<UUID, AdminSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> targetEditors = new ConcurrentHashMap<>();
    private final Set<UUID> transitioning = ConcurrentHashMap.newKeySet();

    public RegearCommand(Kitloader plugin, DataManager data, GuiManager gui) {
        this.plugin = plugin;
        this.data = data;
        this.gui = gui;
    }

    public void openFromCategory(Player admin, String supplyId, int returnPage) {
        openFromSupplySource(admin, supplyId, returnPage, false);
    }

    public void openFromEditSupply(Player admin, String supplyId, int returnPage) {
        openFromSupplySource(admin, supplyId, returnPage, true);
    }

    private void openFromSupplySource(Player admin, String supplyId, int returnPage, boolean returnToEditor) {
        if (!admin.isOp() || !plugin.isBypassWhitelisted(admin)) {
            plugin.sendMsg(admin, "whitelist_command_denied");
            return;
        }
        GuiManager.UploadedSupplyDetails details = gui.getUploadedSupplyDetails(supplyId);
        if (details == null) {
            plugin.sendMsg(admin, "regear_supply_missing");
            openSupplySource(admin, returnPage, returnToEditor);
            return;
        }

        DataManager.PlayerData targetData = data.getPlayerData(details.ownerId());
        if (targetData == null) targetData = data.getOfflinePlayerData(details.ownerId());
        if (targetData == null) {
            plugin.sendMsg(admin, "regear_supply_missing");
            return;
        }
        gui.ensureUploadedSupplyMetadata(details.ownerId(), targetData);

        closeSession(admin, true);
        UUID existingEditor = targetEditors.putIfAbsent(details.ownerId(), admin.getUniqueId());
        if (existingEditor != null && !existingEditor.equals(admin.getUniqueId())) {
            plugin.sendMsg(admin, "regear_target_busy", "player", details.ownerName());
            return;
        }

        AdminSession session = new AdminSession(details.ownerId(), details.ownerName(), targetData);
        session.directSupplyId = supplyId;
        session.categoryReturnPage = Math.max(0, returnPage);
        session.returnToSupplyEditor = returnToEditor;
        sessions.put(admin.getUniqueId(), session);
        openDirectManagement(admin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin)) {
            plugin.sendMsg(sender, "player_only");
            return true;
        }
        if (!plugin.isBypassWhitelisted(admin)) {
            plugin.sendMsg(admin, "whitelist_command_denied");
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("list")) {
            plugin.sendMsg(admin, "regear_usage");
            return true;
        }

        OfflinePlayer target = findTarget(args[0]);
        if (target == null) {
            plugin.sendMsg(admin, "regear_target_not_found", "player", args[0]);
            return true;
        }

        DataManager.PlayerData targetData = data.getPlayerData(target.getUniqueId());
        if (target.isOnline() && targetData == null) {
            plugin.sendMsg(admin, "regear_target_loading", "player", target.getName());
            return true;
        }
        if (targetData == null) targetData = data.getOfflinePlayerData(target.getUniqueId());
        gui.ensureUploadedSupplyMetadata(target.getUniqueId(), targetData);

        closeSession(admin, true);
        UUID existingEditor = targetEditors.putIfAbsent(target.getUniqueId(), admin.getUniqueId());
        if (existingEditor != null && !existingEditor.equals(admin.getUniqueId())) {
            plugin.sendMsg(admin, "regear_target_busy", "player", targetName(target));
            return true;
        }
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        sessions.put(admin.getUniqueId(), new AdminSession(target.getUniqueId(), targetName, targetData));
        openList(admin, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !plugin.isBypassWhitelisted(player)) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
                String name = target.getName();
                String uuid = target.getUniqueId().toString();
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(name);
                if (uuid.toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(uuid);
            }
            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions.stream().distinct().limit(200).toList();
        }
        if (args.length == 2 && "list".startsWith(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("list");
        }
        return new ArrayList<>();
    }

    private OfflinePlayer findTarget(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online;
        try {
            OfflinePlayer byUuid = Bukkit.getOfflinePlayer(UUID.fromString(input));
            if (byUuid.hasPlayedBefore() || byUuid.isOnline()) return byUuid;
        } catch (IllegalArgumentException ignored) {
        }
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(target -> target.getName() != null && target.getName().equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }

    private String targetName(OfflinePlayer target) {
        return target.getName() != null ? target.getName() : target.getUniqueId().toString();
    }

    private DataManager.PlayerData currentTargetData(AdminSession session) {
        DataManager.PlayerData cached = data.getPlayerData(session.targetId);
        return cached != null ? cached : session.targetData;
    }

    private void openList(Player admin, int requestedPage) {
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null) return;
        DataManager.PlayerData targetData = currentTargetData(session);
        gui.ensureUploadedSupplyMetadata(session.targetId, targetData);

        int maxPage = Math.max(0, (targetData.uploadedSupplies.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        session.page = page;
        session.editor = null;
        session.awaitingName = false;

        List<String> supplyIds = new ArrayList<>(Collections.nCopies(PAGE_SIZE, null));
        SupplyListHolder holder = new SupplyListHolder(admin.getUniqueId(), session.targetId, page, supplyIds);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Kitloader.color("&#F12711&lRegear补给管理 &8&l- &f&l" + session.targetName + " &8&lP" + (page + 1)));
        holder.bind(inventory);

        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= targetData.uploadedSupplies.size() || index >= targetData.uploadedSupplyIds.size()) break;
            ItemStack supply = targetData.uploadedSupplies.get(index);
            String supplyId = targetData.uploadedSupplyIds.get(index);
            if (supply == null || supply.getType().isAir() || supplyId == null || supplyId.isBlank()) continue;
            supplyIds.set(slot, supplyId);
            ItemStack display = gui.createSupplyDisplayItem(supply,
                    targetData.uploadedSuppliesVisible
                            ? "&#00B09B&l[公开中] &f&l当前显示在公共补给页"
                            : "&#FF5E62&l[已隐藏] &f&l仍可由 Regear 管理",
                    "&#F2C94C&l[✎] 左键 &f&l编辑名称、颜色和内容",
                    "&#FF5E62&l[✖] 右键 &f&l删除该补给");
            inventory.setItem(slot, display);
        }

        if (page > 0) inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "&#00B09B&l◀ 上一页", ""));
        inventory.setItem(INFO_SLOT, button(Material.BOOK, "&#F2C94C&l[?] 目标玩家",
                "&#95A5A6&l玩家: &f&l" + session.targetName
                        + "\n&#95A5A6&lUUID: &f&l" + session.targetId
                        + "\n&#95A5A6&l补给数量: &f&l" + targetData.uploadedSupplies.size()));
        inventory.setItem(CLOSE_SLOT, button(Material.IRON_DOOR, "&#FF5E62&l[✖] 关闭管理", ""));
        inventory.setItem(VISIBILITY_SLOT, button(
                targetData.uploadedSuppliesVisible ? Material.LIME_DYE : Material.GRAY_DYE,
                targetData.uploadedSuppliesVisible
                        ? "&#00B09B&l[公开中] 一键隐藏全部"
                        : "&#FF5E62&l[已隐藏] 一键公开全部",
                targetData.uploadedSuppliesVisible
                        ? "&#95A5A6&l点击后，将从所有玩家的公共补给页移除"
                        : "&#95A5A6&l点击后，所有玩家可重新在公共补给页看见"));
        if (targetData.uploadedSupplies.size() > (page + 1) * PAGE_SIZE) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "&#00B09B&l下一页 ▶", ""));
        }
        fill(inventory, PAGE_SIZE, 53);
        openManaged(admin, inventory);
    }

    private void startEditor(Player admin, String supplyId, int returnPage) {
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null) return;
        DataManager.PlayerData targetData = currentTargetData(session);
        int index = findSupplyIndex(targetData, supplyId);
        if (index < 0) {
            plugin.sendMsg(admin, "regear_supply_missing");
            openList(admin, returnPage);
            return;
        }

        ItemStack supply = targetData.uploadedSupplies.get(index);
        if (!(supply.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            plugin.sendMsg(admin, "regear_invalid_supply");
            return;
        }

        EditorSession editor = new EditorSession(supplyId, returnPage, supply,
                cloneItems(box.getInventory().getContents()), supply.getType(),
                meta.hasDisplayName() ? meta.getDisplayName() : null,
                cloneItems(admin.getInventory().getContents()), cloneItem(admin.getItemOnCursor()));
        session.editor = editor;
        openEditor(admin);
    }

    private void openEditor(Player admin) {
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null || session.editor == null) return;
        EditorSession editor = session.editor;

        SupplyEditorHolder holder = new SupplyEditorHolder(admin.getUniqueId(), session.targetId, editor.supplyId);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Kitloader.color("&#FF0099&lRegear编辑补给 &8&l- &f&l" + session.targetName));
        holder.bind(inventory);
        for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, cloneItem(editor.contents[slot]));

        inventory.setItem(45, button(Material.ARROW, "&#FF5E62&l◀ 取消并返回", "&#95A5A6&l放弃本次修改"));
        inventory.setItem(47, button(editor.material, "&#F2C94C&l✦ 切换补给盒颜色",
                "&#95A5A6&l当前: &f&l" + editor.material.name()));
        inventory.setItem(49, button(Material.NAME_TAG, "&#00B09B&l✎ 修改补给名称",
                "&#95A5A6&l当前: &f&l" + (editor.displayName == null
                        ? "未命名潜影盒" : ChatColor.stripColor(editor.displayName))));
        inventory.setItem(53, button(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l✔ 保存修改",
                "&#95A5A6&l同步更新玩家数据和公共补给记录"));
        fill(inventory, 27, 53);
        openManaged(admin, inventory);
    }

    private void openDeleteConfirmation(Player admin, String supplyId, int returnPage) {
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null) return;
        ConfirmDeleteHolder holder = new ConfirmDeleteHolder(
                admin.getUniqueId(), session.targetId, supplyId, returnPage);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Kitloader.color("&#ED213A&l确认删除玩家上传补给"));
        holder.bind(inventory);
        inventory.setItem(11, button(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l[✔] 永久删除", ""));
        inventory.setItem(15, button(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[✖] 取消", ""));
        fill(inventory, 0, 26);
        openManaged(admin, inventory);
    }

    private void openDirectManagement(Player admin) {
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null || session.directSupplyId == null) return;
        GuiManager.UploadedSupplyDetails details = gui.getUploadedSupplyDetails(session.directSupplyId);
        if (details == null || !details.ownerId().equals(session.targetId)) {
            plugin.sendMsg(admin, "regear_supply_missing");
            returnToSupplySource(admin, session);
            return;
        }

        DirectSupplyHolder holder = new DirectSupplyHolder(
                admin.getUniqueId(), session.targetId, session.directSupplyId);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Kitloader.color("&#FF0099&l玩家上传补给管理"));
        holder.bind(inventory);
        inventory.setItem(4, button(Material.BOOK, "&#F2C94C&l[?] 上传信息",
                "&#95A5A6&l上传者: &f&l" + details.ownerName()
                        + "\n&#95A5A6&lUUID: &f&l" + details.ownerId()
                        + "\n&#95A5A6&l上传时间: &f&l" + gui.formatSupplyUploadTime(details.uploadTime())));
        inventory.setItem(13, gui.createSupplyDisplayItem(details.item(),
                details.hidden()
                        ? "&#FF5E62&l[已隐藏] &f&l当前不在公共补给页显示"
                        : "&#00B09B&l[公开中] &f&l当前显示在公共补给页"));
        inventory.setItem(18, button(Material.ARROW,
                session.returnToSupplyEditor
                        ? "&#00B09B&l◀ 返回上一级"
                        : "&#00B09B&l◀ 返回补给分类",
                "&#95A5A6&l返回第 " + (session.categoryReturnPage + 1) + " 页"));
        inventory.setItem(22, button(Material.NAME_TAG, "&#F2C94C&l[✎] 修改补给名称",
                "&#95A5A6&l修改后同步玩家与公共补给页"));
        inventory.setItem(26, button(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[✖] 永久删除",
                "&#95A5A6&l进入二次确认页面"));
        fill(inventory, 0, 26);
        openManaged(admin, inventory);
    }

    private void returnToSupplySource(Player admin, AdminSession session) {
        int returnPage = session.categoryReturnPage == null ? 0 : session.categoryReturnPage;
        boolean returnToEditor = session.returnToSupplyEditor;
        closeSession(admin, false);
        openSupplySource(admin, returnPage, returnToEditor);
    }

    private void openSupplySource(Player admin, int returnPage, boolean returnToEditor) {
        if (returnToEditor) gui.openEditGui(admin, "supply", returnPage);
        else gui.openCategoryGui(admin, "supply", returnPage);
    }

    private void openManaged(Player player, Inventory inventory) {
        boolean replacingRegearInventory = player.getOpenInventory().getTopInventory().getHolder() instanceof RegearHolder;
        if (replacingRegearInventory) transitioning.add(player.getUniqueId());
        player.openInventory(inventory);
        if (replacingRegearInventory) {
            player.getScheduler().run(plugin, task -> transitioning.remove(player.getUniqueId()),
                    () -> transitioning.remove(player.getUniqueId()));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof RegearHolder regearHolder)) return;
        AdminSession activeSession = sessions.get(admin.getUniqueId());
        if (!regearHolder.adminId.equals(admin.getUniqueId())
                || activeSession == null || !regearHolder.targetId.equals(activeSession.targetId)
                || !plugin.isBypassWhitelisted(admin)
                || (activeSession != null && activeSession.directSupplyId != null && !admin.isOp())) {
            event.setCancelled(true);
            closeSession(admin, true);
            admin.closeInventory();
            plugin.sendMsg(admin, "whitelist_command_denied");
            return;
        }

        if (holder instanceof SupplyListHolder listHolder) {
            handleListClick(admin, event, listHolder);
        } else if (holder instanceof SupplyEditorHolder) {
            handleEditorClick(admin, event);
        } else if (holder instanceof DirectSupplyHolder) {
            handleDirectManagementClick(admin, event);
        } else if (holder instanceof ConfirmDeleteHolder confirmHolder) {
            handleDeleteClick(admin, event, confirmHolder);
        }
    }

    private void handleDirectManagementClick(Player admin, InventoryClickEvent event) {
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;
        if (!event.getClick().isLeftClick()) return;
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null || session.directSupplyId == null) return;

        if (rawSlot == 18) {
            returnToSupplySource(admin, session);
        } else if (rawSlot == 22) {
            session.awaitingName = true;
            transitioning.add(admin.getUniqueId());
            admin.closeInventory();
            admin.getScheduler().run(plugin, task -> transitioning.remove(admin.getUniqueId()),
                    () -> transitioning.remove(admin.getUniqueId()));
            plugin.sendMsg(admin, "regear_name_prompt", "max", String.valueOf(
                    CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.SUPPLY)));
        } else if (rawSlot == 26) {
            openDeleteConfirmation(admin, session.directSupplyId,
                    session.categoryReturnPage == null ? 0 : session.categoryReturnPage);
        }
    }

    private void handleListClick(Player admin, InventoryClickEvent event, SupplyListHolder holder) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (!event.getClick().isLeftClick() && slot >= PAGE_SIZE) return;
        if (slot == PREVIOUS_SLOT) {
            openList(admin, holder.page - 1);
        } else if (slot == CLOSE_SLOT) {
            closeSession(admin, false);
            admin.closeInventory();
        } else if (slot == VISIBILITY_SLOT) {
            AdminSession session = sessions.get(admin.getUniqueId());
            if (session == null) return;
            DataManager.PlayerData targetData = currentTargetData(session);
            boolean visible = gui.toggleUploadedSuppliesVisibility(session.targetId, targetData);
            gui.refreshUploadedSupplyManagementPage(session.targetId);
            plugin.sendMsg(admin, visible ? "regear_visibility_public" : "regear_visibility_hidden",
                    "player", session.targetName);
            openList(admin, holder.page);
        } else if (slot == NEXT_SLOT) {
            openList(admin, holder.page + 1);
        } else if (slot >= 0 && slot < PAGE_SIZE) {
            String supplyId = holder.supplyIds.get(slot);
            if (supplyId == null) return;
            if (event.getClick().isLeftClick()) {
                startEditor(admin, supplyId, holder.page);
            } else if (event.getClick().isRightClick()) {
                openDeleteConfirmation(admin, supplyId, holder.page);
            }
        }
    }

    private void handleEditorClick(Player admin, InventoryClickEvent event) {
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null || session.editor == null) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot >= 0 && rawSlot < topSize) {
            if (rawSlot >= 0 && rawSlot <= 26) {
                if (wouldInsertShulker(admin, event)) {
                    event.setCancelled(true);
                    plugin.sendMsg(admin, "shulker_nesting_forbidden");
                }
                return;
            }

            event.setCancelled(true);
            if (rawSlot != 45 && rawSlot != 47 && rawSlot != 49 && rawSlot != 53) return;
            if (!event.getClick().isLeftClick()) return;
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                plugin.sendMsg(admin, "regear_cursor_not_empty");
                return;
            }

            if (rawSlot == 45) {
                int returnPage = session.editor.returnPage;
                restoreAdminInventory(admin, session.editor);
                session.editor = null;
                openList(admin, returnPage);
            } else if (rawSlot == 47) {
                captureEditorContents(event.getView().getTopInventory(), session.editor);
                int index = Arrays.asList(GuiManager.SHULKERS).indexOf(session.editor.material);
                session.editor.material = GuiManager.SHULKERS[(Math.max(0, index) + 1) % GuiManager.SHULKERS.length];
                openEditor(admin);
            } else if (rawSlot == 49) {
                captureEditorContents(event.getView().getTopInventory(), session.editor);
                session.awaitingName = true;
                transitioning.add(admin.getUniqueId());
                admin.closeInventory();
                admin.getScheduler().run(plugin, task -> transitioning.remove(admin.getUniqueId()),
                        () -> transitioning.remove(admin.getUniqueId()));
                plugin.sendMsg(admin, "regear_name_prompt", "max", String.valueOf(
                        CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.SUPPLY)));
            } else {
                captureEditorContents(event.getView().getTopInventory(), session.editor);
                saveEditor(admin, session);
            }
            return;
        }

        if (rawSlot < topSize) {
            event.setCancelled(true);
            return;
        }
        int convertedSlot = event.getView().convertSlot(rawSlot);
        if (convertedSlot < 0
                || convertedSlot >= event.getView().getBottomInventory().getSize()) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            if (isShulker(clicked)) {
                plugin.sendMsg(admin, "shulker_nesting_forbidden");
                return;
            }
            moveIntoEditor(event, event.getView().getTopInventory());
        }
    }

    private boolean wouldInsertShulker(Player admin, InventoryClickEvent event) {
        ItemStack incoming = event.getCursor();
        if (event.getClick() == ClickType.NUMBER_KEY) {
            incoming = admin.getInventory().getItem(event.getHotbarButton());
        } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
            incoming = admin.getInventory().getItemInOffHand();
        }
        return incoming != null && !incoming.getType().isAir() && isShulker(incoming);
    }

    private void moveIntoEditor(InventoryClickEvent event, Inventory topInventory) {
        ItemStack clicked = event.getCurrentItem();
        int remaining = clicked.getAmount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < 27 && remaining > 0; slot++) {
                ItemStack target = topInventory.getItem(slot);
                if (pass == 0) {
                    if (target == null || target.getType().isAir() || !target.isSimilar(clicked)) continue;
                    int moved = Math.min(target.getMaxStackSize() - target.getAmount(), remaining);
                    if (moved <= 0) continue;
                    target.setAmount(target.getAmount() + moved);
                    remaining -= moved;
                } else {
                    if (target != null && !target.getType().isAir()) continue;
                    ItemStack movedItem = clicked.clone();
                    int moved = Math.min(movedItem.getMaxStackSize(), remaining);
                    movedItem.setAmount(moved);
                    topInventory.setItem(slot, movedItem);
                    remaining -= moved;
                }
            }
        }
        if (remaining <= 0) event.setCurrentItem(null);
        else event.getCurrentItem().setAmount(remaining);
    }

    private void handleDeleteClick(Player admin, InventoryClickEvent event, ConfirmDeleteHolder holder) {
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;
        if (!event.getClick().isLeftClick()) return;
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null) return;
        if (rawSlot == 15) {
            if (session.directSupplyId != null) openDirectManagement(admin);
            else openList(admin, holder.returnPage);
            return;
        }
        if (rawSlot != 11) return;

        DataManager.PlayerData targetData = currentTargetData(session);
        int index = findSupplyIndex(targetData, holder.supplyId);
        if (index < 0) {
            plugin.sendMsg(admin, "regear_supply_missing");
        } else {
            ItemStack removed = targetData.uploadedSupplies.get(index);
            if (gui.removeUploadedSupplyEverywhere(holder.supplyId, session.targetId)) {
                String name = removed.hasItemMeta() && removed.getItemMeta().hasDisplayName()
                        ? ChatColor.stripColor(removed.getItemMeta().getDisplayName()) : "未命名潜影盒";
                plugin.sendMsg(admin, "regear_delete_success", "player", session.targetName, "box", name);
            } else {
                plugin.sendMsg(admin, "regear_supply_missing");
            }
        }
        if (session.directSupplyId != null) returnToSupplySource(admin, session);
        else openList(admin, holder.returnPage);
    }

    private void saveEditor(Player admin, AdminSession session) {
        EditorSession editor = session.editor;
        DataManager.PlayerData targetData = currentTargetData(session);
        int index = findSupplyIndex(targetData, editor.supplyId);
        if (index < 0) {
            restoreAdminInventory(admin, editor);
            session.editor = null;
            plugin.sendMsg(admin, "regear_supply_missing");
            openList(admin, editor.returnPage);
            return;
        }

        for (ItemStack item : editor.contents) {
            if (isShulker(item)) {
                plugin.sendMsg(admin, "shulker_nesting_forbidden");
                return;
            }
        }

        if (!validateSupplyContents(admin, editor.contents)) return;
        ItemStack updated = editor.original.clone();
        updated.setType(editor.material);
        ItemMeta rawMeta = updated.getItemMeta();
        if (!(rawMeta instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box)) {
            plugin.sendMsg(admin, "regear_invalid_supply");
            return;
        }
        if (editor.displayName != null) meta.setDisplayName(editor.displayName);
        box.getInventory().setContents(cloneItems(editor.contents));
        meta.setBlockState(box);
        updated.setItemMeta(meta);
        gui.stripUploadedSupplyMetadata(updated);

        targetData.uploadedSupplies.set(index, updated);
        data.saveOfflinePlayerAsync(targetData);
        gui.updateUploadedSupply(editor.supplyId, session.targetId,
                !targetData.uploadedSuppliesVisible, updated);
        gui.refreshUploadedSupplyManagementPage(session.targetId);
        int returnPage = editor.returnPage;
        session.editor = null;
        plugin.sendMsg(admin, "regear_save_success", "player", session.targetName);
        admin.playSound(admin.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        openList(admin, returnPage);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof RegearHolder)) return;
        if (!(holder instanceof SupplyEditorHolder)) {
            event.setCancelled(true);
            return;
        }

        ItemStack dragged = event.getOldCursor();
        for (int slot : event.getRawSlots()) {
            if (slot >= event.getView().getTopInventory().getSize()) continue;
            if (slot > 26 || isShulker(dragged)) {
                event.setCancelled(true);
                if (isShulker(dragged)) plugin.sendMsg(admin, "shulker_nesting_forbidden");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRenameChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        AdminSession session = sessions.get(admin.getUniqueId());
        if (session == null || !session.awaitingName
                || (session.editor == null && session.directSupplyId == null)) return;
        event.setCancelled(true);

        String newName = event.getMessage().trim();
        String coloredName = Kitloader.color(newName);
        String visibleName = ChatColor.stripColor(coloredName);
        if (newName.isEmpty() || visibleName == null || visibleName.isBlank()) {
            admin.getScheduler().run(plugin, task -> plugin.sendMsg(admin, "regear_name_invalid"), null);
            return;
        }
        CustomNamePolicy.NameValidation nameValidation =
                CustomNamePolicy.validateSupplyName(plugin, coloredName);
        if (!nameValidation.valid()) {
            admin.getScheduler().run(plugin,
                    task -> CustomNamePolicy.sendValidationFailure(plugin, admin, nameValidation), null);
            return;
        }
        if (session.directSupplyId != null && session.editor == null) {
            session.awaitingName = false;
            admin.getScheduler().run(plugin, task -> renameDirectSupply(admin, session, coloredName), null);
        } else {
            session.editor.displayName = coloredName;
            session.awaitingName = false;
            admin.getScheduler().run(plugin, task -> {
                plugin.sendMsg(admin, "regear_name_updated");
                openEditor(admin);
            }, null);
        }
    }

    private void renameDirectSupply(Player admin, AdminSession session, String coloredName) {
        if (sessions.get(admin.getUniqueId()) != session || session.directSupplyId == null) return;
        DataManager.PlayerData targetData = currentTargetData(session);
        int index = findSupplyIndex(targetData, session.directSupplyId);
        if (index < 0) {
            session.awaitingName = false;
            plugin.sendMsg(admin, "regear_supply_missing");
            returnToSupplySource(admin, session);
            return;
        }

        ItemStack updated = targetData.uploadedSupplies.get(index).clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) {
            session.awaitingName = false;
            plugin.sendMsg(admin, "regear_invalid_supply");
            openDirectManagement(admin);
            return;
        }
        meta.setDisplayName(coloredName);
        updated.setItemMeta(meta);
        gui.stripUploadedSupplyMetadata(updated);
        targetData.uploadedSupplies.set(index, updated);
        data.saveOfflinePlayerAsync(targetData);
        gui.updateUploadedSupply(session.directSupplyId, session.targetId,
                !targetData.uploadedSuppliesVisible, updated);
        gui.refreshUploadedSupplyManagementPage(session.targetId);
        session.awaitingName = false;
        plugin.sendMsg(admin, "regear_name_updated");
        openDirectManagement(admin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandWhileNaming(PlayerCommandPreprocessEvent event) {
        AdminSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.awaitingName) return;
        event.setCancelled(true);
        plugin.sendMsg(event.getPlayer(), "regear_name_prompt", "max", String.valueOf(
                CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.SUPPLY)));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player admin)) return;
        if (!(event.getInventory().getHolder() instanceof RegearHolder)) return;
        if (transitioning.contains(admin.getUniqueId())) return;
        closeSession(admin, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        AdminSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.editor != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            AdminSession session = sessions.get(player.getUniqueId());
            if (session != null && session.editor != null) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        closeSession(event.getPlayer(), true);
        transitioning.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (UUID adminId : new ArrayList<>(sessions.keySet())) {
            Player admin = Bukkit.getPlayer(adminId);
            if (admin != null) closeSession(admin, true);
        }
        sessions.clear();
        targetEditors.clear();
        transitioning.clear();
    }

    private void closeSession(Player admin, boolean restoreInventory) {
        AdminSession session = sessions.remove(admin.getUniqueId());
        if (restoreInventory && session != null && session.editor != null) {
            restoreAdminInventory(admin, session.editor);
        }
        if (session != null) targetEditors.remove(session.targetId, admin.getUniqueId());
    }

    private void restoreAdminInventory(Player admin, EditorSession editor) {
        admin.getInventory().setContents(cloneItems(editor.adminInventory));
        admin.setItemOnCursor(cloneItem(editor.adminCursor));
        admin.updateInventory();
    }

    private void captureEditorContents(Inventory inventory, EditorSession editor) {
        for (int slot = 0; slot < 27; slot++) editor.contents[slot] = cloneItem(inventory.getItem(slot));
    }

    private int findSupplyIndex(DataManager.PlayerData targetData, String supplyId) {
        for (int index = 0; index < targetData.uploadedSupplyIds.size(); index++) {
            if (supplyId.equals(targetData.uploadedSupplyIds.get(index))
                    && index < targetData.uploadedSupplies.size()) return index;
        }
        return -1;
    }

    private boolean validateSupplyContents(Player admin, ItemStack[] contents) {
        CustomNamePolicy.CleanupResult cleanup = CustomNamePolicy.sanitizeItems(contents);
        if (cleanup.removedItems() > 0) {
            plugin.sendMsg(admin, "custom_name_items_removed",
                    "removed", String.valueOf(cleanup.removedItems()));
        }
        SupplyContentPolicy.Rules rules = SupplyContentPolicy.rules(plugin);
        SupplyContentPolicy.ValidationResult result = SupplyContentPolicy.validateContents(contents, rules);
        switch (result) {
            case VALID -> {
                return true;
            }
            case NOT_FULL -> plugin.sendMsg(admin, "supply_inventory_not_full",
                    "required", String.valueOf(rules.requiredFilledSlots()));
            case ALL_SAME -> plugin.sendMsg(admin, "supply_all_same_rejected");
            case TOO_MANY_SIMILAR -> plugin.sendMsg(admin, "supply_similar_stack_limit",
                    "max", String.valueOf(rules.maxSimilarStacks()));
            default -> plugin.sendMsg(admin, "supply_invalid_box");
        }
        return false;
    }

    private boolean isShulker(ItemStack item) {
        return item != null && item.getType().name().endsWith("SHULKER_BOX");
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Kitloader.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (String line : lore.split("\\n")) lines.add(Kitloader.color(line));
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(Inventory inventory, int start, int end) {
        for (int slot = start; slot <= end; slot++) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
                inventory.setItem(slot, button(Material.BLACK_STAINED_GLASS_PANE, "&7", ""));
            }
        }
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) copy[index] = cloneItem(items[index]);
        return copy;
    }

    private static final class AdminSession {
        private final UUID targetId;
        private final String targetName;
        private final DataManager.PlayerData targetData;
        private int page;
        private EditorSession editor;
        private volatile boolean awaitingName;
        private String directSupplyId;
        private Integer categoryReturnPage;
        private boolean returnToSupplyEditor;

        private AdminSession(UUID targetId, String targetName, DataManager.PlayerData targetData) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.targetData = targetData;
        }
    }

    private static final class EditorSession {
        private final String supplyId;
        private final int returnPage;
        private final ItemStack original;
        private final ItemStack[] adminInventory;
        private final ItemStack adminCursor;
        private ItemStack[] contents;
        private Material material;
        private String displayName;

        private EditorSession(String supplyId, int returnPage, ItemStack original, ItemStack[] contents,
                              Material material, String displayName,
                              ItemStack[] adminInventory, ItemStack adminCursor) {
            this.supplyId = supplyId;
            this.returnPage = returnPage;
            this.original = original.clone();
            this.contents = contents;
            this.material = material;
            this.displayName = displayName;
            this.adminInventory = adminInventory;
            this.adminCursor = adminCursor;
        }
    }

    private abstract static class RegearHolder implements InventoryHolder {
        private final UUID adminId;
        private final UUID targetId;
        private Inventory inventory;

        private RegearHolder(UUID adminId, UUID targetId) {
            this.adminId = adminId;
            this.targetId = targetId;
        }

        final void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SupplyListHolder extends RegearHolder {
        private final int page;
        private final List<String> supplyIds;

        private SupplyListHolder(UUID adminId, UUID targetId, int page, List<String> supplyIds) {
            super(adminId, targetId);
            this.page = page;
            this.supplyIds = supplyIds;
        }
    }

    private static final class SupplyEditorHolder extends RegearHolder {
        private final String supplyId;

        private SupplyEditorHolder(UUID adminId, UUID targetId, String supplyId) {
            super(adminId, targetId);
            this.supplyId = supplyId;
        }
    }

    private static final class DirectSupplyHolder extends RegearHolder {
        private final String supplyId;

        private DirectSupplyHolder(UUID adminId, UUID targetId, String supplyId) {
            super(adminId, targetId);
            this.supplyId = supplyId;
        }
    }

    private static final class ConfirmDeleteHolder extends RegearHolder {
        private final String supplyId;
        private final int returnPage;

        private ConfirmDeleteHolder(UUID adminId, UUID targetId, String supplyId, int returnPage) {
            super(adminId, targetId);
            this.supplyId = supplyId;
            this.returnPage = returnPage;
        }
    }
}
