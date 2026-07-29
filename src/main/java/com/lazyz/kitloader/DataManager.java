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
    private static final String LEGACY_AUTOSAVE_NAME = "autosave";
    private static final String AUTOSAVE_PREFIX = "autosave-";
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
        CleanupSummary cleanup = sanitizePublicKitData();
        changed |= cleanup.changed();
        logCustomNameCleanup("public_kits.yml", cleanup);
        sortPublicKits();
        if (changed) savePublicKits();
    }

    public synchronized void savePublicKits() {
        CleanupSummary cleanup = sanitizePublicKitData();
        logCustomNameCleanup("public_kits.yml", cleanup);
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
                if (data.pendingRemovedCustomNameItems > 0) {
                    plugin.sendMsg(player, "custom_name_items_removed",
                            "removed", String.valueOf(data.pendingRemovedCustomNameItems));
                    data.pendingRemovedCustomNameItems = 0;
                }
                if (data.pendingRemovedInvalidSupplies > 0) {
                    plugin.sendMsg(player, "supply_policy_existing_removed",
                            "removed", String.valueOf(data.pendingRemovedInvalidSupplies));
                    data.pendingRemovedInvalidSupplies = 0;
                }
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
        CleanupSummary cleanup = sanitizePlayerData(data);
        logSupplyCleanup(uuid + ".yml", cleanup);
        logCustomNameCleanup(uuid + ".yml", cleanup);
        if (cleanup.changed()) saveOfflinePlayerAsync(data);
        return data;
    }

    public void saveOfflinePlayerAsync(PlayerData data) {
        CleanupSummary cleanup = sanitizePlayerData(data);
        logSupplyCleanup(data.uuid + ".yml", cleanup);
        logCustomNameCleanup(data.uuid + ".yml", cleanup);
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
        String latestAutosave = latestAutosaveName(data.kits.keySet());
        if (latestAutosave != null && sameItems(data.kits.get(latestAutosave), current)) return false;

        int nextAutosaveNumber = nextAutosaveNumber(data.kits.keySet());
        int maxKits = Math.max(1, plugin.getConfig().getInt("settings.max-kits", 9));
        while (data.kits.size() >= maxKits) {
            String oldestAutosave = oldestAutosaveName(data.kits.keySet());
            if (oldestAutosave == null) return false;
            data.kits.remove(oldestAutosave);
        }

        String autosaveName = AUTOSAVE_PREFIX + nextAutosaveNumber;
        data.kits.put(autosaveName, current);
        savePlayerAsync(player.getUniqueId());
        if (removedShulkers > 0) plugin.sendMsg(player, "kit_shulker_trimmed",
                "max", String.valueOf(plugin.getConfig().getInt("settings.shulker-limits.kit-save-max", 3)),
                "removed", String.valueOf(removedShulkers));
        plugin.sendMsg(player, "autosave_success", "kit", autosaveName);
        return true;
    }

    private String latestAutosaveName(Collection<String> kitNames) {
        return kitNames.stream()
                .filter(this::isAutosaveName)
                .max(Comparator.comparingInt(this::autosaveNumber))
                .orElse(null);
    }

    private String oldestAutosaveName(Collection<String> kitNames) {
        return kitNames.stream()
                .filter(this::isAutosaveName)
                .min(Comparator.comparingInt(this::autosaveNumber))
                .orElse(null);
    }

    private int nextAutosaveNumber(Collection<String> kitNames) {
        return kitNames.stream()
                .filter(this::isAutosaveName)
                .mapToInt(this::autosaveNumber)
                .max()
                .orElse(0) + 1;
    }

    private boolean isAutosaveName(String name) {
        if (LEGACY_AUTOSAVE_NAME.equals(name)) return true;
        if (name == null || !name.startsWith(AUTOSAVE_PREFIX)) return false;
        try {
            return Integer.parseInt(name.substring(AUTOSAVE_PREFIX.length())) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private int autosaveNumber(String name) {
        if (LEGACY_AUTOSAVE_NAME.equals(name)) return 0;
        try {
            return Integer.parseInt(name.substring(AUTOSAVE_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public void revalidateCachedPlayerData() {
        for (PlayerData playerData : cache.values()) {
            CleanupSummary cleanup = sanitizePlayerData(playerData);
            logSupplyCleanup(playerData.uuid + ".yml", cleanup);
            logCustomNameCleanup(playerData.uuid + ".yml", cleanup);
            if (cleanup.changed()) saveOfflinePlayerAsync(playerData);

            Player player = plugin.getServer().getPlayer(playerData.uuid);
            if (player == null || !player.isOnline()) continue;
            player.getScheduler().run(plugin, task -> {
                if (playerData.pendingRemovedCustomNameItems > 0) {
                    plugin.sendMsg(player, "custom_name_items_removed",
                            "removed", String.valueOf(playerData.pendingRemovedCustomNameItems));
                    playerData.pendingRemovedCustomNameItems = 0;
                }
                if (playerData.pendingRemovedInvalidSupplies > 0) {
                    plugin.sendMsg(player, "supply_policy_existing_removed",
                            "removed", String.valueOf(playerData.pendingRemovedInvalidSupplies));
                    playerData.pendingRemovedInvalidSupplies = 0;
                }
            }, null);
        }
    }

    public ItemStack[] copyItems(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] clone = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) clone[i] = source[i] != null ? source[i].clone() : null;
        return clone;
    }

    private CleanupSummary sanitizePublicKitData() {
        boolean changed = false;
        int removedItems = 0;
        int renamedNames = 0;
        Set<String> usedNames = new HashSet<>();

        for (PublicKit kit : publicKits) {
            CustomNamePolicy.CleanupResult itemCleanup = CustomNamePolicy.sanitizeItems(kit.items);
            changed |= itemCleanup.changed();
            removedItems += itemCleanup.removedItems();

            if (!CustomNamePolicy.isValidKitName(plugin, kit.kitName)) {
                kit.kitName = nextSafeKitName("Recovered Shared", usedNames);
                changed = true;
                renamedNames++;
            }
            usedNames.add(kit.kitName);
        }
        return new CleanupSummary(changed, removedItems, renamedNames, 0);
    }

    private CleanupSummary sanitizePlayerData(PlayerData data) {
        synchronized (data) {
            boolean changed = false;
            int removedItems = 0;
            int renamedNames = 0;
            int removedSupplies = 0;

            Map<String, ItemStack[]> cleanKits = new LinkedHashMap<>();
            for (Map.Entry<String, ItemStack[]> entry : data.kits.entrySet()) {
                String name = entry.getKey();
                if ((!isAutosaveName(name) && !CustomNamePolicy.isValidKitName(plugin, name))
                        || cleanKits.containsKey(name)) {
                    name = nextSafeKitName("Recovered Kit", cleanKits.keySet());
                    changed = true;
                    renamedNames++;
                }

                ItemStack[] items = entry.getValue();
                CustomNamePolicy.CleanupResult itemCleanup = CustomNamePolicy.sanitizeItems(items);
                changed |= itemCleanup.changed();
                removedItems += itemCleanup.removedItems();
                cleanKits.put(name, items);
            }
            if (changed || !data.kits.keySet().equals(cleanKits.keySet())) data.kits = cleanKits;

            for (int index = 0; index < data.uploadedSupplies.size();) {
                ItemStack supply = data.uploadedSupplies.get(index);
                CustomNamePolicy.CleanupResult itemCleanup = CustomNamePolicy.sanitizeItem(supply);
                changed |= itemCleanup.changed();
                removedItems += itemCleanup.removedItems();

                boolean removeSupply = itemCleanup.removeRoot();
                if (!removeSupply && SupplyContentPolicy.validateSupply(plugin, supply)
                        != SupplyContentPolicy.ValidationResult.VALID) {
                    removeSupply = true;
                    removedSupplies++;
                }
                if (!removeSupply && supply.getItemMeta() != null
                        && supply.getItemMeta().hasDisplayName()) {
                    org.bukkit.inventory.meta.ItemMeta meta = supply.getItemMeta();
                    if (!CustomNamePolicy.isValidSupplyName(plugin, meta.getDisplayName())) {
                        meta.setDisplayName(CustomNamePolicy.safeDefaultSupplyName(plugin));
                        supply.setItemMeta(meta);
                        changed = true;
                        renamedNames++;
                    }
                }
                if (removeSupply) {
                    changed = true;
                    data.uploadedSupplies.remove(index);
                    if (index < data.uploadedSupplyIds.size()) data.uploadedSupplyIds.remove(index);
                    continue;
                }
                index++;
            }
            while (data.uploadedSupplyIds.size() > data.uploadedSupplies.size()) {
                data.uploadedSupplyIds.remove(data.uploadedSupplyIds.size() - 1);
                changed = true;
            }
            while (data.uploadedSupplyIds.size() < data.uploadedSupplies.size()) {
                data.uploadedSupplyIds.add("");
                changed = true;
            }

            if (data.editSession != null) {
                CustomNamePolicy.CleanupResult itemCleanup = CustomNamePolicy.sanitizeItems(data.editSession.items);
                changed |= itemCleanup.changed();
                removedItems += itemCleanup.removedItems();
                if (!CustomNamePolicy.isValidSupplyName(plugin, Kitloader.color(data.editSession.name))) {
                    data.editSession.name = CustomNamePolicy.safeDefaultSupplyName(plugin);
                    changed = true;
                    renamedNames++;
                }
            }

            if (data.publicEditSession != null) {
                CustomNamePolicy.CleanupResult itemCleanup = CustomNamePolicy.sanitizeItems(data.publicEditSession.items);
                changed |= itemCleanup.changed();
                removedItems += itemCleanup.removedItems();
                if (!CustomNamePolicy.isValidKitName(plugin, data.publicEditSession.name)) {
                    data.publicEditSession.name = nextSafeKitName("Recovered Shared", Set.of());
                    changed = true;
                    renamedNames++;
                }
            }

            if (removedItems > 0) data.pendingRemovedCustomNameItems += removedItems;
            if (removedSupplies > 0) data.pendingRemovedInvalidSupplies += removedSupplies;
            return new CleanupSummary(changed, removedItems, renamedNames, removedSupplies);
        }
    }

    private String nextSafeKitName(String base, Collection<String> usedNames) {
        int maxLength = CustomNamePolicy.maxVisibleLength(plugin, CustomNamePolicy.NameType.KIT);
        if (maxLength < 3) return nextCompactKitName(usedNames);
        int suffix = 1;
        while (true) {
            String suffixText = suffix == 1 ? "" : " " + suffix;
            int baseLimit = Math.max(1, maxLength - suffixText.length());
            String safeBase = truncateCodePoints(base, baseLimit);
            String candidate = safeBase + suffixText;
            if (!usedNames.contains(candidate) && CustomNamePolicy.isValidKitName(plugin, candidate)) {
                return candidate;
            }
            suffix++;
        }
    }

    private String nextCompactKitName(Collection<String> usedNames) {
        int basicStart = 0x4E00;
        int basicCount = 0x9FFF - basicStart + 1;
        int extensionStart = 0x20000;
        int extensionCount = 0x2FA1D - extensionStart + 1;
        int candidateCount = basicCount + extensionCount;

        for (int index = 0; index < candidateCount; index++) {
            int codePoint = index < basicCount
                    ? basicStart + index
                    : extensionStart + index - basicCount;
            String candidate = new String(Character.toChars(codePoint));
            if (!usedNames.contains(candidate)
                    && CustomNamePolicy.isValidKitName(plugin, candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "No recovery Kit name is available within the configured visible-length limit.");
    }

    private String truncateCodePoints(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) return value;
        int endIndex = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, endIndex);
    }

    private void logCustomNameCleanup(String scope, CleanupSummary cleanup) {
        if (cleanup.removedItems() == 0 && cleanup.renamedNames() == 0) return;
        plugin.logLocalized("custom_name_cleanup_log",
                "scope", scope,
                "removed", String.valueOf(cleanup.removedItems()),
                "renamed", String.valueOf(cleanup.renamedNames()));
    }

    private void logSupplyCleanup(String scope, CleanupSummary cleanup) {
        if (cleanup.removedSupplies() == 0) return;
        plugin.logLocalized("supply_policy_cleanup_log",
                "scope", scope,
                "removed", String.valueOf(cleanup.removedSupplies()));
    }

    private record CleanupSummary(boolean changed, int removedItems, int renamedNames, int removedSupplies) {
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
        public transient int pendingRemovedCustomNameItems = 0;
        public transient int pendingRemovedInvalidSupplies = 0;
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
