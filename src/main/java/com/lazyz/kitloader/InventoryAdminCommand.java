package com.lazyz.kitloader;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InventoryAdminCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int LIST_PAGE_SIZE = 45;
    private static final int SAVE_EXIT_SLOT = 45;
    private static final int DISCARD_EXIT_SLOT = 47;
    private static final int SWITCH_VIEW_SLOT = 49;
    private static final int DISCARD_RETURN_SLOT = 53;

    private final Kitloader plugin;
    private final Map<UUID, EditSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> targetEditors = new ConcurrentHashMap<>();
    private final Set<UUID> transitioning = ConcurrentHashMap.newKeySet();

    public InventoryAdminCommand(Kitloader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin)) {
            plugin.sendMsg(sender, "player_only");
            return true;
        }
        if (plugin.isRestrictedKitloaderPlayer(admin)) {
            plugin.sendMsg(admin, "restricted_command");
            return true;
        }
        if (!admin.isOp()) {
            plugin.sendMsg(admin, "no_permission");
            return true;
        }
        if (!plugin.isBypassWhitelisted(admin)) {
            plugin.sendMsg(admin, "whitelist_command_denied");
            return true;
        }
        if (sessions.containsKey(admin.getUniqueId())) {
            plugin.sendMsg(admin, "inv_session_active");
            return true;
        }

        if (args.length == 0) {
            openPlayerList(admin, 0);
            return true;
        }
        if (args.length != 1) {
            plugin.sendMsg(admin, "inv_usage");
            return true;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            plugin.sendMsg(admin, "inv_target_not_found", "player", args[0]);
            return true;
        }
        beginSession(admin, target, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player admin) || plugin.isRestrictedKitloaderPlayer(admin)
                || !admin.isOp() || !plugin.isBypassWhitelisted(admin) || args.length != 1) {
            return new ArrayList<>();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(admin.getUniqueId())) continue;
            String name = player.getName();
            String uuid = player.getUniqueId().toString();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(name);
            if (uuid.toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(uuid);
        }
        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions;
    }

    private Player findOnlinePlayer(String input) {
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) return exact;
        try {
            Player byUuid = Bukkit.getPlayer(UUID.fromString(input));
            if (byUuid != null) return byUuid;
        } catch (IllegalArgumentException ignored) {
        }
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }

    private void openPlayerList(Player admin, int requestedPage) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(admin.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList());
        int maxPage = Math.max(0, (players.size() - 1) / LIST_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int start = page * LIST_PAGE_SIZE;
        List<UUID> targetIds = new ArrayList<>();

        PlayerListHolder holder = new PlayerListHolder(page, targetIds);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Kitloader.color("&#00D2FF&l在线玩家背包管理 &8&l- P" + (page + 1)));
        holder.bind(inventory);

        for (int slot = 0; slot < LIST_PAGE_SIZE && start + slot < players.size(); slot++) {
            Player target = players.get(start + slot);
            targetIds.add(target.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(Kitloader.color("&#00B09B&l" + target.getName()));
                meta.setLore(List.of(
                        Kitloader.color("&7"),
                        Kitloader.color("&#95A5A6&l点击查看并编辑该玩家的物品"),
                        Kitloader.color(targetEditors.containsKey(target.getUniqueId())
                                ? "&#FF5E62&l当前正由其他管理员编辑"
                                : "&#34495E&l当前可编辑")
                ));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);
        }

        if (page > 0) inventory.setItem(45, button(Material.ARROW, "&#00B09B&l◀ 上一页"));
        inventory.setItem(49, button(Material.IRON_DOOR, "&#FF5E62&l关闭"));
        if (players.size() > (page + 1) * LIST_PAGE_SIZE) {
            inventory.setItem(53, button(Material.ARROW, "&#00B09B&l下一页 ▶"));
        }
        fillEmpty(inventory, 45, 53);
        admin.openInventory(inventory);
    }

    private void beginSession(Player admin, Player target, int returnPage) {
        if (admin.getUniqueId().equals(target.getUniqueId())) {
            plugin.sendMsg(admin, "inv_cannot_edit_self");
            return;
        }
        if (!target.isOnline()) {
            plugin.sendMsg(admin, "inv_target_not_found", "player", target.getName());
            return;
        }

        UUID existingEditor = targetEditors.putIfAbsent(target.getUniqueId(), admin.getUniqueId());
        if (existingEditor != null) {
            plugin.sendMsg(admin, "inv_target_busy", "player", target.getName());
            return;
        }

        ItemStack[] adminSnapshot = snapshotPlayerInventory(admin);
        ItemStack adminCursor = cloneItem(admin.getItemOnCursor());
        target.getScheduler().run(plugin, task -> {
            if (!target.isOnline()) {
                targetEditors.remove(target.getUniqueId(), admin.getUniqueId());
                return;
            }
            ItemStack[] targetInventory = snapshotPlayerInventory(target);
            ItemStack[] targetEnder = cloneItems(target.getEnderChest().getContents());

            admin.getScheduler().run(plugin, adminTask -> {
                if (!admin.isOnline() || !target.isOnline()) {
                    targetEditors.remove(target.getUniqueId(), admin.getUniqueId());
                    if (admin.isOnline()) plugin.sendMsg(admin, "inv_target_not_found", "player", target.getName());
                    return;
                }
                if (sessions.containsKey(admin.getUniqueId())) {
                    targetEditors.remove(target.getUniqueId(), admin.getUniqueId());
                    plugin.sendMsg(admin, "inv_session_active");
                    return;
                }

                EditSession session = new EditSession(admin.getUniqueId(), target.getUniqueId(), target.getName(),
                        returnPage, targetInventory, targetEnder, adminSnapshot, adminCursor);
                sessions.put(admin.getUniqueId(), session);
                openEditor(admin, session, View.PLAYER_INVENTORY);
                startRealtimeSync(session);
            }, () -> targetEditors.remove(target.getUniqueId(), admin.getUniqueId()));
        }, () -> {
            targetEditors.remove(target.getUniqueId(), admin.getUniqueId());
            if (admin.isOnline()) {
                admin.getScheduler().run(plugin,
                        task -> plugin.sendMsg(admin, "inv_target_not_found", "player", target.getName()), null);
            }
        });
    }

    private void openEditor(Player admin, EditSession session, View view) {
        EditorHolder holder = new EditorHolder(session.id, view);
        String title = view == View.PLAYER_INVENTORY
                ? "&#00D2FF&l背包与装备 &8&l- &f&l" + session.targetName
                : "&#8E2DE2&l末影箱编辑 &8&l- &f&l" + session.targetName;
        Inventory inventory = Bukkit.createInventory(holder, 54, Kitloader.color(title));
        holder.bind(inventory);

        if (view == View.PLAYER_INVENTORY) {
            ItemStack[] items = session.workingInventory;
            for (int slot = 0; slot < 36; slot++) inventory.setItem(slot, cloneAt(items, slot));
            inventory.setItem(36, cloneAt(items, 39));
            inventory.setItem(37, cloneAt(items, 38));
            inventory.setItem(38, cloneAt(items, 37));
            inventory.setItem(39, cloneAt(items, 36));
            inventory.setItem(40, cloneAt(items, 40));
            inventory.setItem(42, button(Material.ARMOR_STAND, "&#F2C94C&l装备栏",
                    "&#95A5A6&l从左到右：头盔、胸甲、护腿、靴子、副手"));
            inventory.setItem(SWITCH_VIEW_SLOT, button(Material.ENDER_CHEST, "&#8E2DE2&l查看并编辑末影箱",
                    "&#95A5A6&l保留当前背包修改并切换页面"));
        } else {
            ItemStack[] items = session.workingEnder;
            for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, cloneAt(items, slot));
            inventory.setItem(SWITCH_VIEW_SLOT, button(Material.CHEST, "&#00B09B&l保留修改并返回背包",
                    "&#95A5A6&l保留当前末影箱修改并切换页面"));
        }

        inventory.setItem(SAVE_EXIT_SLOT, button(Material.LIME_CONCRETE, "&#00B09B&l保存并退出",
                "&#95A5A6&l提交背包与末影箱的全部修改"));
        inventory.setItem(DISCARD_EXIT_SLOT, button(Material.RED_CONCRETE, "&#FF5E62&l不保存并退出",
                "&#95A5A6&l恢复双方打开界面前的物品状态"));
        inventory.setItem(DISCARD_RETURN_SLOT, button(Material.ARROW, "&#F2C94C&l不保存并返回",
                view == View.PLAYER_INVENTORY
                        ? "&#95A5A6&l放弃全部修改并返回玩家列表"
                        : "&#95A5A6&l放弃本次末影箱修改并返回背包"));
        fillEditorDecoration(inventory, view);

        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof EditorHolder) {
            transitioning.add(admin.getUniqueId());
        }
        admin.openInventory(inventory);
    }

    private void fillEditorDecoration(Inventory inventory, View view) {
        int editableEnd = view == View.PLAYER_INVENTORY ? 40 : 26;
        for (int slot = editableEnd + 1; slot < 45; slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, filler());
        }
        for (int slot = 45; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, filler());
        }
    }

    private void fillEmpty(Inventory inventory, int start, int end) {
        for (int slot = start; slot <= end; slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, filler());
        }
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Kitloader.color(name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) lines.add(Kitloader.color(line));
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack filler() {
        return button(Material.BLACK_STAINED_GLASS_PANE, "&7");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof PlayerListHolder listHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
            if (slot >= 0 && slot < listHolder.targets.size()) {
                Player target = Bukkit.getPlayer(listHolder.targets.get(slot));
                if (target == null) {
                    plugin.sendMsg(admin, "inv_target_not_found", "player", "-");
                    openPlayerList(admin, listHolder.page);
                } else {
                    beginSession(admin, target, listHolder.page);
                }
            } else if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                openPlayerList(admin, listHolder.page - 1);
            } else if (slot == 49 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.IRON_DOOR) {
                admin.closeInventory();
            } else if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                openPlayerList(admin, listHolder.page + 1);
            }
            return;
        }

        if (!(holder instanceof EditorHolder editorHolder)) return;
        EditSession session = sessions.get(admin.getUniqueId());
        if (session == null || !session.id.equals(editorHolder.sessionId)) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < 0 || event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP
                || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot < topSize) {
            if (rawSlot == SAVE_EXIT_SLOT || rawSlot == DISCARD_EXIT_SLOT
                    || rawSlot == SWITCH_VIEW_SLOT || rawSlot == DISCARD_RETURN_SLOT) {
                event.setCancelled(true);
                handleEditorControl(admin, session, editorHolder, rawSlot, event.getView().getTopInventory());
                return;
            }
            int editableEnd = editorHolder.view == View.PLAYER_INVENTORY ? 40 : 26;
            if (rawSlot > editableEnd) {
                event.setCancelled(true);
                return;
            }
        }

        markAdminInteraction(session, editorHolder.view);
        scheduleCapture(admin, session, editorHolder);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorHolder holder)) return;
        EditSession session = sessions.get(admin.getUniqueId());
        if (session == null || !session.id.equals(holder.sessionId)) {
            event.setCancelled(true);
            return;
        }

        int editableEnd = holder.view == View.PLAYER_INVENTORY ? 40 : 26;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize() && rawSlot > editableEnd) {
                event.setCancelled(true);
                return;
            }
        }
        markAdminInteraction(session, holder.view);
        scheduleCapture(admin, session, holder);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player admin)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorHolder holder)) return;
        if (transitioning.remove(admin.getUniqueId())) return;

        EditSession session = sessions.get(admin.getUniqueId());
        if (session != null && session.id.equals(holder.sessionId)) {
            discardEntireSession(admin, session, false, false);
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && sessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        EditSession adminSession = sessions.remove(leaving.getUniqueId());
        if (adminSession != null) {
            targetEditors.remove(adminSession.targetId, adminSession.adminId);
            applyPlayerInventory(leaving, adminSession.originalAdminInventory);
            leaving.setItemOnCursor(cloneItem(adminSession.originalAdminCursor));
            restoreTarget(adminSession);
        }

        UUID adminId = targetEditors.remove(leaving.getUniqueId());
        if (adminId == null) return;
        EditSession targetSession = sessions.remove(adminId);
        if (targetSession == null) return;

        applyPlayerInventory(leaving, targetSession.originalTargetInventory);
        leaving.getEnderChest().setContents(cloneItems(targetSession.originalTargetEnder));
        Player admin = Bukkit.getPlayer(adminId);
        if (admin != null) {
            admin.getScheduler().run(plugin, task -> {
                transitioning.add(adminId);
                admin.closeInventory();
                restoreAdminAfterClose(admin, targetSession.originalAdminInventory,
                        targetSession.originalAdminCursor,
                        () -> plugin.sendMsg(admin, "inv_target_offline", "player", targetSession.targetName));
            }, null);
        }
    }

    private void handleEditorControl(Player admin, EditSession session, EditorHolder holder,
                                     int slot, Inventory topInventory) {
        if (slot == SAVE_EXIT_SLOT) {
            captureView(session, holder.view, topInventory, false);
            saveAndExit(admin, session);
            return;
        }
        if (slot == DISCARD_EXIT_SLOT) {
            discardEntireSession(admin, session, true, false);
            return;
        }
        if (slot == SWITCH_VIEW_SLOT) {
            captureView(session, holder.view, topInventory, true);
            if (holder.view == View.PLAYER_INVENTORY) {
                session.viewStartTarget = cloneItems(session.workingEnder);
                session.viewStartAdminInventory = snapshotPlayerInventory(admin);
                session.viewStartAdminCursor = cloneItem(admin.getItemOnCursor());
                openEditor(admin, session, View.ENDER_CHEST);
            } else {
                openEditor(admin, session, View.PLAYER_INVENTORY);
            }
            return;
        }

        if (holder.view == View.ENDER_CHEST) {
            rollbackEnderView(admin, session);
        } else {
            discardEntireSession(admin, session, true, true);
        }
    }

    private void scheduleCapture(Player admin, EditSession session, EditorHolder expectedHolder) {
        admin.getScheduler().run(plugin, task -> {
            EditSession current = sessions.get(admin.getUniqueId());
            Inventory top = admin.getOpenInventory().getTopInventory();
            if (current != session || !(top.getHolder() instanceof EditorHolder holder)
                    || !holder.sessionId.equals(expectedHolder.sessionId) || holder.view != expectedHolder.view) {
                return;
            }
            captureView(session, holder.view, top, true);
        }, null);
    }

    private void markAdminInteraction(EditSession session, View view) {
        AtomicBoolean writePending = view == View.PLAYER_INVENTORY
                ? session.inventoryWritePending : session.enderWritePending;
        AtomicLong revisions = view == View.PLAYER_INVENTORY
                ? session.inventoryRevision : session.enderRevision;
        writePending.set(true);
        revisions.incrementAndGet();
    }

    private void startRealtimeSync(EditSession session) {
        Player target = Bukkit.getPlayer(session.targetId);
        if (target == null) return;
        target.getScheduler().runAtFixedRate(plugin, task -> {
            if (!target.isOnline() || sessions.get(session.adminId) != session) {
                task.cancel();
                return;
            }
            synchronizeFromTarget(session, View.PLAYER_INVENTORY, snapshotPlayerInventory(target));
            synchronizeFromTarget(session, View.ENDER_CHEST,
                    cloneItems(target.getEnderChest().getContents()));
        }, () -> {}, 1L, 1L);
    }

    private void synchronizeFromTarget(EditSession session, View view, ItemStack[] targetSnapshot) {
        AtomicBoolean writePending = view == View.PLAYER_INVENTORY
                ? session.inventoryWritePending : session.enderWritePending;
        AtomicLong revisions = view == View.PLAYER_INVENTORY
                ? session.inventoryRevision : session.enderRevision;
        ItemStack[] lastTargetSnapshot = view == View.PLAYER_INVENTORY
                ? session.lastTargetInventory : session.lastTargetEnder;
        if (writePending.get() || sameItems(lastTargetSnapshot, targetSnapshot)) return;

        long previousRevision = revisions.get();
        if (!revisions.compareAndSet(previousRevision, previousRevision + 1) || writePending.get()) return;
        long revision = previousRevision + 1;
        ItemStack[] synchronizedSnapshot = cloneItems(targetSnapshot);
        if (view == View.PLAYER_INVENTORY) {
            session.lastTargetInventory = cloneItems(synchronizedSnapshot);
            session.workingInventory = cloneItems(synchronizedSnapshot);
        } else {
            session.lastTargetEnder = cloneItems(synchronizedSnapshot);
            session.workingEnder = cloneItems(synchronizedSnapshot);
        }

        Player admin = Bukkit.getPlayer(session.adminId);
        if (admin == null) return;
        admin.getScheduler().run(plugin, task -> {
            if (sessions.get(session.adminId) != session || revisions.get() != revision
                    || writePending.get()) return;
            Inventory top = admin.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof EditorHolder holder)
                    || !holder.sessionId.equals(session.id) || holder.view != view) return;
            updateEditorContents(top, view, synchronizedSnapshot);
            admin.updateInventory();
        }, null);
    }

    private void updateEditorContents(Inventory top, View view, ItemStack[] contents) {
        if (view == View.PLAYER_INVENTORY) {
            for (int slot = 0; slot < 36; slot++) top.setItem(slot, cloneAt(contents, slot));
            top.setItem(36, cloneAt(contents, 39));
            top.setItem(37, cloneAt(contents, 38));
            top.setItem(38, cloneAt(contents, 37));
            top.setItem(39, cloneAt(contents, 36));
            top.setItem(40, cloneAt(contents, 40));
        } else {
            for (int slot = 0; slot < 27; slot++) top.setItem(slot, cloneAt(contents, slot));
        }
    }

    private void captureView(EditSession session, View view, Inventory top, boolean realtime) {
        if (view == View.PLAYER_INVENTORY) {
            ItemStack[] contents = new ItemStack[41];
            for (int slot = 0; slot < 36; slot++) contents[slot] = cloneItem(top.getItem(slot));
            contents[39] = cloneItem(top.getItem(36));
            contents[38] = cloneItem(top.getItem(37));
            contents[37] = cloneItem(top.getItem(38));
            contents[36] = cloneItem(top.getItem(39));
            contents[40] = cloneItem(top.getItem(40));
            session.workingInventory = contents;
            if (realtime) applyTargetRealtime(session, view, contents);
        } else {
            ItemStack[] contents = new ItemStack[session.workingEnder.length];
            for (int slot = 0; slot < Math.min(27, contents.length); slot++) {
                contents[slot] = cloneItem(top.getItem(slot));
            }
            session.workingEnder = contents;
            if (realtime) applyTargetRealtime(session, view, contents);
        }
    }

    private void applyTargetRealtime(EditSession session, View view, ItemStack[] snapshot) {
        Player target = Bukkit.getPlayer(session.targetId);
        if (target == null) return;
        AtomicLong revisions = view == View.PLAYER_INVENTORY
                ? session.inventoryRevision : session.enderRevision;
        AtomicBoolean writePending = view == View.PLAYER_INVENTORY
                ? session.inventoryWritePending : session.enderWritePending;
        writePending.set(true);
        long revision = revisions.incrementAndGet();
        ItemStack[] immutableSnapshot = cloneItems(snapshot);
        target.getScheduler().run(plugin, task -> {
            if (sessions.get(session.adminId) != session || revisions.get() != revision) return;
            if (view == View.PLAYER_INVENTORY) {
                applyPlayerInventory(target, immutableSnapshot);
                session.lastTargetInventory = cloneItems(immutableSnapshot);
            } else {
                target.getEnderChest().setContents(cloneItems(immutableSnapshot));
                target.updateInventory();
                session.lastTargetEnder = cloneItems(immutableSnapshot);
            }
            if (revisions.get() == revision) writePending.set(false);
        }, () -> {
            if (revisions.get() == revision) writePending.set(false);
        });
    }

    private void saveAndExit(Player admin, EditSession session) {
        if (!sessions.remove(admin.getUniqueId(), session)) return;
        targetEditors.remove(session.targetId, session.adminId);
        Player target = Bukkit.getPlayer(session.targetId);
        if (target != null) {
            ItemStack[] inventory = cloneItems(session.workingInventory);
            ItemStack[] ender = cloneItems(session.workingEnder);
            target.getScheduler().run(plugin, task -> {
                applyPlayerInventory(target, inventory);
                target.getEnderChest().setContents(cloneItems(ender));
                target.updateInventory();
            }, null);
        }

        transitioning.add(admin.getUniqueId());
        admin.closeInventory();
        plugin.sendMsg(admin, "inv_saved", "player", session.targetName);
    }

    private void discardEntireSession(Player admin, EditSession session, boolean closeInventory, boolean returnToList) {
        if (!sessions.remove(admin.getUniqueId(), session)) return;
        targetEditors.remove(session.targetId, session.adminId);
        restoreTarget(session);

        if (closeInventory) {
            transitioning.add(admin.getUniqueId());
            admin.closeInventory();
        }
        plugin.sendMsg(admin, "inv_discarded", "player", session.targetName);
        restoreAdminAfterClose(admin, session.originalAdminInventory, session.originalAdminCursor,
                returnToList ? () -> openPlayerList(admin, session.returnPage) : null);
    }

    private void rollbackEnderView(Player admin, EditSession session) {
        session.enderRevision.incrementAndGet();
        session.workingEnder = cloneItems(session.viewStartTarget);
        Player target = Bukkit.getPlayer(session.targetId);
        if (target != null) {
            ItemStack[] snapshot = cloneItems(session.workingEnder);
            target.getScheduler().run(plugin, task -> {
                if (sessions.get(session.adminId) != session) return;
                target.getEnderChest().setContents(cloneItems(snapshot));
                target.updateInventory();
            }, null);
        }

        transitioning.add(admin.getUniqueId());
        admin.closeInventory();
        restoreAdminAfterClose(admin, session.viewStartAdminInventory, session.viewStartAdminCursor,
                () -> openEditor(admin, session, View.PLAYER_INVENTORY));
    }

    private void restoreAdminAfterClose(Player admin, ItemStack[] inventory, ItemStack cursor, Runnable afterRestore) {
        ItemStack[] snapshot = cloneItems(inventory);
        ItemStack cursorSnapshot = cloneItem(cursor);
        applyPlayerInventory(admin, snapshot);
        admin.setItemOnCursor(cloneItem(cursorSnapshot));
        admin.getScheduler().run(plugin, task -> {
            if (!admin.isOnline()) return;
            applyPlayerInventory(admin, snapshot);
            admin.setItemOnCursor(cloneItem(cursorSnapshot));
            if (afterRestore != null) afterRestore.run();
        }, null);
    }

    private void restoreTarget(EditSession session) {
        session.inventoryRevision.incrementAndGet();
        session.enderRevision.incrementAndGet();
        Player target = Bukkit.getPlayer(session.targetId);
        if (target == null) return;
        ItemStack[] inventory = cloneItems(session.originalTargetInventory);
        ItemStack[] ender = cloneItems(session.originalTargetEnder);
        target.getScheduler().run(plugin, task -> {
            applyPlayerInventory(target, inventory);
            target.getEnderChest().setContents(cloneItems(ender));
            target.updateInventory();
        }, null);
    }

    public void shutdown() {
        for (EditSession session : new ArrayList<>(sessions.values())) {
            Player admin = Bukkit.getPlayer(session.adminId);
            if (admin != null) {
                applyPlayerInventory(admin, session.originalAdminInventory);
                admin.setItemOnCursor(cloneItem(session.originalAdminCursor));
            }
            Player target = Bukkit.getPlayer(session.targetId);
            if (target != null) {
                applyPlayerInventory(target, session.originalTargetInventory);
                target.getEnderChest().setContents(cloneItems(session.originalTargetEnder));
            }
        }
        sessions.clear();
        targetEditors.clear();
        transitioning.clear();
    }

    private static ItemStack[] snapshotPlayerInventory(Player player) {
        ItemStack[] snapshot = new ItemStack[41];
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < Math.min(36, storage.length); slot++) {
            snapshot[slot] = cloneItem(storage[slot]);
        }
        snapshot[36] = cloneItem(player.getInventory().getBoots());
        snapshot[37] = cloneItem(player.getInventory().getLeggings());
        snapshot[38] = cloneItem(player.getInventory().getChestplate());
        snapshot[39] = cloneItem(player.getInventory().getHelmet());
        snapshot[40] = cloneItem(player.getInventory().getItemInOffHand());
        return snapshot;
    }

    private static void applyPlayerInventory(Player player, ItemStack[] snapshot) {
        ItemStack[] storage = new ItemStack[36];
        for (int slot = 0; slot < storage.length; slot++) storage[slot] = cloneAt(snapshot, slot);
        player.getInventory().setStorageContents(storage);
        player.getInventory().setBoots(cloneAt(snapshot, 36));
        player.getInventory().setLeggings(cloneAt(snapshot, 37));
        player.getInventory().setChestplate(cloneAt(snapshot, 38));
        player.getInventory().setHelmet(cloneAt(snapshot, 39));
        player.getInventory().setItemInOffHand(cloneAt(snapshot, 40));
        player.updateInventory();
    }

    private static ItemStack cloneAt(ItemStack[] items, int index) {
        return items != null && index >= 0 && index < items.length ? cloneItem(items[index]) : null;
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

    private static boolean sameItems(ItemStack[] first, ItemStack[] second) {
        if (first == null || second == null || first.length != second.length) return false;
        for (int index = 0; index < first.length; index++) {
            ItemStack firstItem = first[index];
            ItemStack secondItem = second[index];
            boolean firstEmpty = firstItem == null || firstItem.getType().isAir();
            boolean secondEmpty = secondItem == null || secondItem.getType().isAir();
            if (firstEmpty && secondEmpty) continue;
            if (firstEmpty != secondEmpty || firstItem.getAmount() != secondItem.getAmount()
                    || !firstItem.isSimilar(secondItem)) return false;
        }
        return true;
    }

    private enum View {
        PLAYER_INVENTORY,
        ENDER_CHEST
    }

    private abstract static class ManagedHolder implements InventoryHolder {
        private Inventory inventory;

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PlayerListHolder extends ManagedHolder {
        private final int page;
        private final List<UUID> targets;

        private PlayerListHolder(int page, List<UUID> targets) {
            this.page = page;
            this.targets = targets;
        }
    }

    private static final class EditorHolder extends ManagedHolder {
        private final UUID sessionId;
        private final View view;

        private EditorHolder(UUID sessionId, View view) {
            this.sessionId = sessionId;
            this.view = view;
        }
    }

    private static final class EditSession {
        private final UUID id = UUID.randomUUID();
        private final UUID adminId;
        private final UUID targetId;
        private final String targetName;
        private final int returnPage;
        private final ItemStack[] originalTargetInventory;
        private final ItemStack[] originalTargetEnder;
        private final ItemStack[] originalAdminInventory;
        private final ItemStack originalAdminCursor;
        private final AtomicLong inventoryRevision = new AtomicLong();
        private final AtomicLong enderRevision = new AtomicLong();
        private final AtomicBoolean inventoryWritePending = new AtomicBoolean();
        private final AtomicBoolean enderWritePending = new AtomicBoolean();
        private volatile ItemStack[] workingInventory;
        private volatile ItemStack[] workingEnder;
        private volatile ItemStack[] lastTargetInventory;
        private volatile ItemStack[] lastTargetEnder;
        private volatile ItemStack[] viewStartTarget;
        private volatile ItemStack[] viewStartAdminInventory;
        private volatile ItemStack viewStartAdminCursor;

        private EditSession(UUID adminId, UUID targetId, String targetName, int returnPage,
                            ItemStack[] targetInventory, ItemStack[] targetEnder,
                            ItemStack[] adminInventory, ItemStack adminCursor) {
            this.adminId = adminId;
            this.targetId = targetId;
            this.targetName = targetName;
            this.returnPage = returnPage;
            this.originalTargetInventory = cloneItems(targetInventory);
            this.originalTargetEnder = cloneItems(targetEnder);
            this.originalAdminInventory = cloneItems(adminInventory);
            this.originalAdminCursor = cloneItem(adminCursor);
            this.workingInventory = cloneItems(targetInventory);
            this.workingEnder = cloneItems(targetEnder);
            this.lastTargetInventory = cloneItems(targetInventory);
            this.lastTargetEnder = cloneItems(targetEnder);
            this.viewStartTarget = cloneItems(targetEnder);
            this.viewStartAdminInventory = cloneItems(adminInventory);
            this.viewStartAdminCursor = cloneItem(adminCursor);
        }
    }
}
