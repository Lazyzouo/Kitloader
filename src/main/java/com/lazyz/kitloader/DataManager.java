package com.lazyz.kitloader;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class DataManager {
    private final Kitloader plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> saveVersions = new ConcurrentHashMap<>();
    private final File dataFolder;

    private final File publicKitsFile;
    private FileConfiguration publicKitsConfig;
    public final List<PublicKit> publicKits = new CopyOnWriteArrayList<>();
    private final AtomicLong publicKitClock = new AtomicLong();
    private final AtomicLong publicKitSaveVersion = new AtomicLong();

    public DataManager(Kitloader plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "players");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        this.publicKitsFile = new File(plugin.getDataFolder(), "public_kits.yml");
        loadPublicKits();
    }

    public synchronized void loadPublicKits() {
        if (!publicKitsFile.exists()) {
            try { publicKitsFile.createNewFile(); } catch (IOException ignored) {}
        }
        publicKitsConfig = YamlConfiguration.loadConfiguration(publicKitsFile);
        publicKits.clear();
        boolean changed = false;
        if (publicKitsConfig.contains("kits")) {
            List<String> keys = new ArrayList<>(publicKitsConfig.getConfigurationSection("kits").getKeys(false));
            long fallbackTime = Math.max(1L, System.currentTimeMillis() - keys.size());
            for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
                String key = keys.get(keyIndex);
                PublicKit pk = new PublicKit();
                pk.id = key;
                pk.uploaderUuid = UUID.fromString(publicKitsConfig.getString("kits." + key + ".uuid"));
                pk.uploaderName = publicKitsConfig.getString("kits." + key + ".name");
                pk.kitName = publicKitsConfig.getString("kits." + key + ".kitName");
                String uploadTimePath = "kits." + key + ".uploadTime";
                if (publicKitsConfig.contains(uploadTimePath)) {
                    pk.uploadTime = publicKitsConfig.getLong(uploadTimePath);
                } else {
                    pk.uploadTime = fallbackTime + keyIndex;
                    changed = true;
                }
                publicKitClock.accumulateAndGet(pk.uploadTime, Math::max);
                List<?> list = publicKitsConfig.getList("kits." + key + ".items");
                if (list != null) {
                    for (int i = 0; i < list.size() && i < 41; i++) {
                        Object obj = list.get(i);
                        pk.items[i] = obj instanceof ItemStack ? (ItemStack) obj : null;
                    }
                }
                publicKits.add(pk);
            }
        }
        sortPublicKits();
        if (changed) savePublicKits();
    }

    public void savePublicKits() {
        sortPublicKits();
        long saveVersion = publicKitSaveVersion.incrementAndGet();
        List<PublicKit> snapshot = new ArrayList<>();
        for (PublicKit source : publicKits) {
            PublicKit copy = new PublicKit();
            copy.id = source.id;
            copy.uploaderUuid = source.uploaderUuid;
            copy.uploaderName = source.uploaderName;
            copy.kitName = source.kitName;
            copy.uploadTime = source.uploadTime;
            copy.items = copyItems(source.items);
            snapshot.add(copy);
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            if (publicKitSaveVersion.get() != saveVersion) return;
            publicKitsConfig.set("kits", null);
            for (PublicKit pk : snapshot) {
                publicKitsConfig.set("kits." + pk.id + ".uuid", pk.uploaderUuid.toString());
                publicKitsConfig.set("kits." + pk.id + ".name", pk.uploaderName);
                publicKitsConfig.set("kits." + pk.id + ".kitName", pk.kitName);
                publicKitsConfig.set("kits." + pk.id + ".uploadTime", pk.uploadTime);
                publicKitsConfig.set("kits." + pk.id + ".items", Arrays.asList(pk.items));
            }
            try { publicKitsConfig.save(publicKitsFile); } catch (IOException ignored) {}
        });
    }

    public void loadPlayerAsync(Player player) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            File file = new File(dataFolder, player.getUniqueId() + ".yml");
            PlayerData data = loadPlayerData(player.getUniqueId(), file);
            cache.put(player.getUniqueId(), data);
            player.getScheduler().run(plugin, scheduledTask -> {
                if (!player.isOnline() || cache.get(player.getUniqueId()) != data) return;
                GuiManager guiManager = plugin.getGuiManager();
                if (guiManager != null) guiManager.ensureUploadedSupplyMetadata(player, data);
            }, null);
        });
    }

    public synchronized boolean upsertPublicKit(PublicKit candidate) {
        PublicKit existingRecord = null;
        for (PublicKit existing : publicKits) {
            if (existing.id.equals(candidate.id)) {
                existingRecord = existing;
                continue;
            }
            if (UploadContentMatcher.sameKitContents(existing.items, candidate.items)) return false;
        }

        candidate.uploadTime = existingRecord != null
                ? existingRecord.uploadTime : nextPublicKitTime();
        int existingIndex = existingRecord != null ? publicKits.indexOf(existingRecord) : -1;
        if (existingIndex >= 0) publicKits.set(existingIndex, candidate);
        else publicKits.add(candidate);
        sortPublicKits();
        savePublicKits();
        return true;
    }

    private long nextPublicKitTime() {
        return publicKitClock.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
    }

    private void sortPublicKits() {
        publicKits.sort(Comparator.comparingLong(kit -> kit.uploadTime));
    }

    public PlayerData getOfflinePlayerData(UUID uuid) {
        if (cache.containsKey(uuid)) return cache.get(uuid);
        File file = new File(dataFolder, uuid + ".yml");
        return loadPlayerData(uuid, file);
    }

    private PlayerData loadPlayerData(UUID uuid, File file) {
        PlayerData data = new PlayerData(uuid);
        if (!file.exists()) return data;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.hasUsed = config.getBoolean("hasUsed", false);

        if (config.contains("kits")) {
            for (String key : config.getConfigurationSection("kits").getKeys(false)) {
                List<?> list = config.getList("kits." + key);
                if (list != null) data.kits.put(key, list.toArray(new ItemStack[0]));
            }
        }
        if (config.contains("uploaded_supplies")) {
            List<?> list = config.getList("uploaded_supplies");
            if (list != null) {
                for (Object obj : list) {
                    if (obj instanceof ItemStack item) data.uploadedSupplies.add(item.clone());
                }
            }
        }
        data.uploadedSupplyIds.addAll(config.getStringList("uploaded_supply_ids"));
        data.uploadedSuppliesVisible = config.getBoolean("uploaded_supplies_visible", true);

        if (config.contains("edit_session")) {
            data.editSession = new EditSession();
            data.editSession.name = config.getString("edit_session.name", "潜影盒");
            String materialName = config.getString("edit_session.color", "SHULKER_BOX");
            Material material = Material.getMaterial(materialName);
            data.editSession.color = material != null ? material : Material.SHULKER_BOX;
            List<?> items = config.getList("edit_session.items");
            if (items != null) {
                for (int index = 0; index < items.size() && index < 27; index++) {
                    Object item = items.get(index);
                    data.editSession.items[index] = item instanceof ItemStack stack ? stack.clone() : null;
                }
            }
        }

        if (config.contains("public_edit_session")) {
            data.publicEditSession = new EditPublicKitSession();
            data.publicEditSession.name = config.getString("public_edit_session.name", "未命名共享Kit");
            data.publicEditSession.kitId = config.getString("public_edit_session.kitId", null);
            List<?> items = config.getList("public_edit_session.items");
            if (items != null) {
                for (int index = 0; index < items.size() && index < 41; index++) {
                    Object item = items.get(index);
                    data.publicEditSession.items[index] = item instanceof ItemStack stack ? stack.clone() : null;
                }
            }
        }
        return data;
    }

    public void saveOfflinePlayerAsync(PlayerData data) {
        UUID uuid = data.uuid;
        long saveVersion = saveVersions.merge(uuid, 1L, Long::sum);
        boolean hasUsedSnapshot = data.hasUsed;
        boolean suppliesVisibleSnapshot = data.uploadedSuppliesVisible;

        Map<String, ItemStack[]> kitsSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStack[]> entry : data.kits.entrySet()) {
            kitsSnapshot.put(entry.getKey(), copyItems(entry.getValue()));
        }

        List<ItemStack> suppliesSnapshot = new ArrayList<>();
        for (ItemStack item : data.uploadedSupplies) {
            suppliesSnapshot.add(item != null ? item.clone() : null);
        }
        List<String> supplyIdsSnapshot = new ArrayList<>(data.uploadedSupplyIds);

        EditSession editSessionSnapshot = null;
        if (data.editSession != null) {
            editSessionSnapshot = new EditSession();
            editSessionSnapshot.name = data.editSession.name;
            editSessionSnapshot.color = data.editSession.color;
            editSessionSnapshot.items = copyItems(data.editSession.items);
        }

        EditPublicKitSession publicEditSessionSnapshot = null;
        if (data.publicEditSession != null) {
            publicEditSessionSnapshot = new EditPublicKitSession();
            publicEditSessionSnapshot.name = data.publicEditSession.name;
            publicEditSessionSnapshot.kitId = data.publicEditSession.kitId;
            publicEditSessionSnapshot.items = copyItems(data.publicEditSession.items);
        }

        EditSession finalEditSessionSnapshot = editSessionSnapshot;
        EditPublicKitSession finalPublicEditSessionSnapshot = publicEditSessionSnapshot;
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            if (saveVersions.getOrDefault(uuid, 0L) != saveVersion) return;

            File file = new File(dataFolder, uuid + ".yml");
            FileConfiguration config = new YamlConfiguration();

            config.set("hasUsed", hasUsedSnapshot);
            for (Map.Entry<String, ItemStack[]> entry : kitsSnapshot.entrySet()) {
                config.set("kits." + entry.getKey(), entry.getValue());
            }
            config.set("uploaded_supplies", suppliesSnapshot);
            config.set("uploaded_supply_ids", supplyIdsSnapshot);
            config.set("uploaded_supplies_visible", suppliesVisibleSnapshot);

            if (finalEditSessionSnapshot != null) {
                config.set("edit_session.name", finalEditSessionSnapshot.name);
                config.set("edit_session.color", finalEditSessionSnapshot.color.name());
                config.set("edit_session.items", Arrays.asList(finalEditSessionSnapshot.items));
            } else {
                config.set("edit_session", null);
            }

            if (finalPublicEditSessionSnapshot != null) {
                config.set("public_edit_session.name", finalPublicEditSessionSnapshot.name);
                config.set("public_edit_session.kitId", finalPublicEditSessionSnapshot.kitId);
                config.set("public_edit_session.items", Arrays.asList(finalPublicEditSessionSnapshot.items));
            } else {
                config.set("public_edit_session", null);
            }
            try {
                config.save(file);
            } catch (IOException ignored) {
            } finally {
                saveVersions.remove(uuid, saveVersion);
            }
        });
    }

    public void savePlayerAsync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        saveOfflinePlayerAsync(data);
    }

    public PlayerData getPlayerData(UUID uuid) { return cache.get(uuid); }

    public void markKitLoaded(Player player) {
        plugin.sanitizePlayerShulkers(player);
        PlayerData data = cache.get(player.getUniqueId());
        if (data != null) data.lastLoadedKitSnapshot = copyItems(player.getInventory().getContents());
    }

    public boolean tryAutosavePlayer(Player player) {
        if (!plugin.getConfig().getBoolean("settings.autosave.enabled", true)) return false;
        PlayerData data = cache.get(player.getUniqueId());
        if (data == null) return false;

        int requiredSlots = plugin.getConfig().getInt("settings.autosave.required-filled-slots", 36);
        int filledSlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) filledSlots++;
        }
        if (filledSlots < requiredSlots) return false;

        ItemStack[] current = copyItems(player.getInventory().getContents());
        if (sameItems(data.lastLoadedKitSnapshot, current)) return false;

        data.lastLoadedKitSnapshot = null;
        int removedShulkers = plugin.enforceKitShulkerLimit(current);
        if (sameItems(data.kits.get("autosave"), current)) return false;

        data.kits.put("autosave", current);
        savePlayerAsync(player.getUniqueId());
        if (removedShulkers > 0) plugin.sendMsg(player, "kit_shulker_trimmed",
                "max", String.valueOf(plugin.getConfig().getInt("settings.shulker-limits.kit-save-max", 3)),
                "removed", String.valueOf(removedShulkers));
        plugin.sendMsg(player, "autosave_success");
        return true;
    }

    public ItemStack[] copyItems(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] clone = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) clone[i] = source[i] != null ? source[i].clone() : null;
        return clone;
    }

    private boolean sameItems(ItemStack[] first, ItemStack[] second) {
        if (first == null || second == null || first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) {
            ItemStack a = first[i];
            ItemStack b = second[i];
            boolean aEmpty = a == null || a.getType().isAir();
            boolean bEmpty = b == null || b.getType().isAir();
            if (aEmpty && bEmpty) continue;
            if (aEmpty != bEmpty || a.getAmount() != b.getAmount() || !a.isSimilar(b)) return false;
        }
        return true;
    }

    public void removeCache(UUID uuid) {
        savePlayerAsync(uuid);
        cache.remove(uuid);
    }

    public static class PublicKit {
        public String id;
        public UUID uploaderUuid;
        public String uploaderName;
        public String kitName;
        public long uploadTime = System.currentTimeMillis();
        public ItemStack[] items = new ItemStack[41];
    }

    public static class EditPublicKitSession {
        public ItemStack[] items = new ItemStack[41];
        public String name = "未命名共享Kit";
        public String kitId = null;
        public boolean isNaming = false;
    }

    public static class EditSession {
        public ItemStack[] items = new ItemStack[27];
        public Material color = Material.SHULKER_BOX;
        public String name = "潜影盒";
        public boolean isNaming = false;
    }

    public static class EditItemSession {
        public ItemStack currentItem;
        public String category;
        public int page;
        public EditItemSession(ItemStack item, String category, int page) {
            this.currentItem = item.clone();
            this.category = category;
            this.page = page;
        }
    }

    public static class NamingContext {
        public enum Type { EDIT_SESSION, DIRECT, KIT_RENAME, ADMIN_KIT_RENAME, PUBLIC_KIT_RENAME }
        public Type type;
        public ItemStack targetItem;
        public String category;
        public int page;
        public NamingContext(Type type, ItemStack item, String category, int page) {
            this.type = type; this.targetItem = item; this.category = category; this.page = page;
        }
    }

    public static class PlayerData {
        public UUID uuid;
        public boolean hasUsed = false;
        public Map<String, ItemStack[]> kits = new HashMap<>();
        public List<ItemStack> uploadedSupplies = new ArrayList<>();
        public List<String> uploadedSupplyIds = new ArrayList<>();
        public boolean uploadedSuppliesVisible = true;
        public EditSession editSession = null;
        public EditPublicKitSession publicEditSession = null;

        public transient EditItemSession editItemSession = null;
        public transient NamingContext namingContext = null;
        public transient long lastPickupWarningTime = 0;
        public transient long lastEnderChestPutTime = 0;
        public transient long lastEnchantRejectTime = 0;
        public transient ItemStack[] lastLoadedKitSnapshot = null;

        public PlayerData(UUID uuid) { this.uuid = uuid; }

        public boolean isNaming() {
            return (editSession != null && editSession.isNaming) ||
                    (publicEditSession != null && publicEditSession.isNaming) ||
                    namingContext != null;
        }

        public void clearNaming() {
            if (editSession != null) editSession.isNaming = false;
            if (publicEditSession != null) publicEditSession.isNaming = false;
            namingContext = null;
        }
    }
}
