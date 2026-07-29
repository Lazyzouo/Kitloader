package com.lazyz.kitloader;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class GuiManager {
    private final Kitloader plugin;
    private final DataManager dataManager;
    private final File guiFile;
    private FileConfiguration guiConfig;
    private final NamespacedKey supplyIdKey;
    private final NamespacedKey supplyOwnerKey;
    private final NamespacedKey supplyHiddenKey;
    private static final String SUPPLY_METADATA_PREFIX = "§0§rKitloaderSupply|";
    private static final int MAX_SUPPLY_DISPLAY_LORE_LINES = 20;
    private final Object uploadedSupplyLock = new Object();
    private final Map<String, UploadedSupplyRecord> uploadedSupplyRecords = new LinkedHashMap<>();
    private final AtomicLong uploadedSupplyClock = new AtomicLong();

    private final Set<java.util.UUID> navigatingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<java.util.UUID, Long> pageNavigationVersions = new ConcurrentHashMap<>();

    private final Map<java.util.UUID, ItemStack[]> adminKitCache = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, String[]> adminTargetCache = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, ItemStack[]> playerKitCache = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, String> playerTargetCache = new ConcurrentHashMap<>();

    private final Map<java.util.UUID, ItemStack[]> publicKitEditCache = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, String> publicTargetCache = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, String> uploadedSupplyTargetCache = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, SupplyPageView> supplyPageViews = new ConcurrentHashMap<>();

    private final Set<java.util.UUID> skipNextClose = ConcurrentHashMap.newKeySet();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static final Material[] SHULKERS = {
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
    };

    public static final Material[] TRIM_PATTERNS = {
            Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
    };
    public static final Material[] TRIM_MATERIALS = {
            Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT, Material.LAPIS_LAZULI,
            Material.EMERALD, Material.DIAMOND, Material.NETHERITE_INGOT, Material.REDSTONE,
            Material.AMETHYST_SHARD, Material.QUARTZ
    };

    private static final String[] RAINBOW_COLORS = {"&#ff5e62", "&#ff9966", "&#f2c94c", "&#00b09b", "&#0575e6", "&#667db6", "&#a83279"};
    private int animationTick = 0;

    public GuiManager(Kitloader plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.guiFile = new File(plugin.getDataFolder(), "gui_items.yml");
        this.supplyIdKey = new NamespacedKey(plugin, "uploaded_supply_id");
        this.supplyOwnerKey = new NamespacedKey(plugin, "uploaded_supply_owner");
        this.supplyHiddenKey = new NamespacedKey(plugin, "uploaded_supply_hidden");
        loadGuiConfig();
        migrateLegacyPublicSupplyMetadata();
        startDynamicTask();
    }

    private String formatTime(long time) {
        return dateFormat.format(new Date(time));
    }

    public void cacheAdminKit(java.util.UUID uuid, ItemStack[] kit) { adminKitCache.put(uuid, kit); }
    public ItemStack[] getAdminKitCache(java.util.UUID uuid) { return adminKitCache.get(uuid); }
    public void cacheAdminTarget(java.util.UUID uuid, String target, String kit) { adminTargetCache.put(uuid, new String[]{target, kit}); }
    public String[] getAdminTargetCache(java.util.UUID uuid) { return adminTargetCache.get(uuid); }
    public void clearAdminCache(java.util.UUID uuid) { adminKitCache.remove(uuid); adminTargetCache.remove(uuid); }

    public void cachePlayerKit(java.util.UUID uuid, ItemStack[] kit) { playerKitCache.put(uuid, kit); }
    public ItemStack[] getPlayerKitCache(java.util.UUID uuid) { return playerKitCache.get(uuid); }
    public void cachePlayerTarget(java.util.UUID uuid, String kit) { playerTargetCache.put(uuid, kit); }
    public String getPlayerTargetCache(java.util.UUID uuid) { return playerTargetCache.get(uuid); }
    public void clearPlayerCache(java.util.UUID uuid) { playerKitCache.remove(uuid); playerTargetCache.remove(uuid); }

    public void cachePublicKitEdit(java.util.UUID uuid, ItemStack[] kit) { publicKitEditCache.put(uuid, kit); }
    public ItemStack[] getPublicKitEditCache(java.util.UUID uuid) { return publicKitEditCache.get(uuid); }
    public void cachePublicTarget(java.util.UUID uuid, String id) { publicTargetCache.put(uuid, id); }
    public String getPublicTargetCache(java.util.UUID uuid) { return publicTargetCache.get(uuid); }
    public void clearPublicCache(java.util.UUID uuid) { publicKitEditCache.remove(uuid); publicTargetCache.remove(uuid); }

    public void cacheUploadedSupplyTarget(java.util.UUID uuid, String supplyId) { uploadedSupplyTargetCache.put(uuid, supplyId); }
    public String getUploadedSupplyTarget(java.util.UUID uuid) { return uploadedSupplyTargetCache.get(uuid); }
    public void clearUploadedSupplyTarget(java.util.UUID uuid) { uploadedSupplyTargetCache.remove(uuid); }

    public void setSkipNextClose(Player player) { skipNextClose.add(player.getUniqueId()); }
    public boolean checkAndClearSkipNextClose(Player player) { return skipNextClose.remove(player.getUniqueId()); }

    public void loadGuiConfig() {
        synchronized (uploadedSupplyLock) {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!guiFile.exists()) {
                try { guiFile.createNewFile(); } catch (IOException ignored) {}
            }
            guiConfig = YamlConfiguration.loadConfiguration(guiFile);

            if (!guiConfig.contains("categories_settings")) {
                guiConfig.set("categories_settings.combat.display", "战斗装备");
                guiConfig.set("categories_settings.combat.type", "DYNAMIC_ITEMS");
                guiConfig.set("categories.combat", new ArrayList<>());

                guiConfig.set("categories_settings.supply.display", "补给盒子");
                guiConfig.set("categories_settings.supply.type", "DYNAMIC_SHULKER");
                guiConfig.set("categories.supply", new ArrayList<>());

                guiConfig.set("categories_settings.consumables.display", "消耗用品");
                guiConfig.set("categories_settings.consumables.type", "DYNAMIC_ITEMS");
                guiConfig.set("categories.consumables", new ArrayList<>());

                try { guiConfig.save(guiFile); } catch (IOException ignored) {}
            }
            loadUploadedSupplyRecords();
        }
    }

    private void loadUploadedSupplyRecords() {
        boolean changed = false;
        synchronized (uploadedSupplyLock) {
            uploadedSupplyRecords.clear();
            ConfigurationSection section = guiConfig.getConfigurationSection("uploaded_supplies");
            if (section == null) return;

            List<String> ids = new ArrayList<>(section.getKeys(false));
            long fallbackTime = Math.max(1L, System.currentTimeMillis() - ids.size());
            for (int recordIndex = 0; recordIndex < ids.size(); recordIndex++) {
                String id = ids.get(recordIndex);
                String path = "uploaded_supplies." + id;
                String ownerRaw = guiConfig.getString(path + ".owner");
                ItemStack item = guiConfig.getItemStack(path + ".item");
                if (ownerRaw == null || item == null || item.getType().isAir()) {
                    changed = true;
                    continue;
                }
                try {
                    UUID owner = UUID.fromString(ownerRaw);
                    ItemStack cleanItem = item.clone();
                    changed |= stripUploadedSupplyMetadata(cleanItem);
                    String uploadTimePath = path + ".uploadTime";
                    long uploadTime;
                    if (guiConfig.contains(uploadTimePath)) {
                        uploadTime = guiConfig.getLong(uploadTimePath);
                    } else {
                        uploadTime = fallbackTime + recordIndex;
                        changed = true;
                    }
                    uploadedSupplyClock.accumulateAndGet(uploadTime, Math::max);
                    uploadedSupplyRecords.put(id, new UploadedSupplyRecord(
                            id, owner, guiConfig.getBoolean(path + ".hidden", false), cleanItem, uploadTime));
                } catch (IllegalArgumentException ignored) {
                    changed = true;
                }
            }
            sortUploadedSupplyRecords();
        }
        if (changed) saveUploadedSupplyRecords();
    }

    public void setNavigating(Player player) { navigatingPlayers.add(player.getUniqueId()); }
    public boolean checkAndClearNavigating(Player player) { return navigatingPlayers.remove(player.getUniqueId()); }

    private long beginPageNavigation(Player player) {
        return pageNavigationVersions.merge(player.getUniqueId(), 1L, Long::sum);
    }

    private boolean isLatestPageNavigation(Player player, long version) {
        return pageNavigationVersions.getOrDefault(player.getUniqueId(), 0L) == version;
    }

    public void clearNavigationState(Player player) {
        navigatingPlayers.remove(player.getUniqueId());
        pageNavigationVersions.remove(player.getUniqueId());
    }

    public void fillBeautifulGradient(Inventory inv, int emptyStart, int emptyEnd) {
        Material[] darkTheme = {
                Material.BLACK_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.LIGHT_GRAY_STAINED_GLASS_PANE
        };
        for (int i = 0; i < inv.getSize(); i++) {
            if (i >= emptyStart && i <= emptyEnd) continue;
            if (inv.getItem(i) == null || inv.getItem(i).getType().isAir()) {
                int col = i % 9;
                int dist = Math.abs(col - 4);
                Material m = darkTheme[dist % darkTheme.length];

                ItemStack glass = new ItemStack(m);
                ItemMeta meta = glass.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§7");
                    glass.setItemMeta(meta);
                }
                inv.setItem(i, glass);
            }
        }
    }

    public List<Integer> getCenteredSlots(int rowStart, int rowEnd, int totalItems, int maxPerRow) {
        List<Integer> slots = new ArrayList<>();
        if (totalItems <= 0) return slots;

        int maxAvailableRows = rowEnd - rowStart + 1;
        int rowsToUse = (int) Math.ceil((double) totalItems / maxPerRow);
        if (rowsToUse > maxAvailableRows) rowsToUse = maxAvailableRows;
        if (rowsToUse <= 0) rowsToUse = 1;

        int baseItemsPerRow = totalItems / rowsToUse;
        int remainder = totalItems % rowsToUse;

        int currentItem = 0;
        for (int r = 0; r < rowsToUse; r++) {
            int itemsThisRow = baseItemsPerRow + (r < remainder ? 1 : 0);
            int startSlot = (rowStart + r) * 9 + (9 - itemsThisRow) / 2;
            for (int i = 0; i < itemsThisRow; i++) {
                if (currentItem >= totalItems) break;
                slots.add(startSlot + i);
                currentItem++;
            }
        }
        return slots;
    }

    public boolean isEnchantable(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getType() == Material.ENCHANTED_BOOK) return false;
        for (org.bukkit.enchantments.Enchantment enc : org.bukkit.Registry.ENCHANTMENT) {
            if (enc.canEnchantItem(item)) return true;
        }
        return false;
    }

    public boolean isArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    public List<org.bukkit.enchantments.Enchantment> getValidEnchants(ItemStack item) {
        List<org.bukkit.enchantments.Enchantment> list = new ArrayList<>();
        if (item == null || item.getType().isAir()) return list;
        for (org.bukkit.enchantments.Enchantment enc : org.bukkit.Registry.ENCHANTMENT) {
            if (enc.canEnchantItem(item)) list.add(enc);
        }
        return list;
    }

    public String getEnchantName(org.bukkit.enchantments.Enchantment enc) {
        String key = enc.getKey().getKey();
        String prefix = "&#F2C94C&l";
        if (key.contains("curse")) prefix = "&#FF5E62&l";
        else if (key.equals("sharpness") || key.equals("smite") || key.equals("bane_of_arthropods") || key.equals("power") || key.equals("impaling") || key.equals("piercing") || key.equals("breach") || key.equals("density") || key.equals("wind_burst") || key.equals("lunge") || key.equals("fire_aspect") || key.equals("sweeping_edge")) prefix = "&#FF5E62&l";
        else if (key.contains("protection") || key.equals("feather_falling") || key.equals("thorns")) prefix = "&#00D2FF&l";

        switch(key) {
            case "protection": return prefix + "保护";
            case "fire_protection": return prefix + "火焰保护";
            case "feather_falling": return prefix + "摔落保护";
            case "blast_protection": return prefix + "爆炸保护";
            case "projectile_protection": return prefix + "弹射物保护";
            case "respiration": return prefix + "水下呼吸";
            case "aqua_affinity": return prefix + "水下速掘";
            case "thorns": return prefix + "荆棘";
            case "depth_strider": return prefix + "深海探索者";
            case "frost_walker": return prefix + "冰霜行者";
            case "binding_curse": return prefix + "绑定诅咒";
            case "sharpness": return prefix + "锋利";
            case "smite": return prefix + "亡灵杀手";
            case "bane_of_arthropods": return prefix + "节肢杀手";
            case "knockback": return prefix + "击退";
            case "fire_aspect": return prefix + "火焰附加";
            case "looting": return prefix + "抢夺";
            case "sweeping_edge": return prefix + "横扫之刃";
            case "efficiency": return prefix + "效率";
            case "silk_touch": return prefix + "精准采集";
            case "unbreaking": return prefix + "耐久";
            case "fortune": return prefix + "时运";
            case "power": return prefix + "力量";
            case "punch": return prefix + "冲击";
            case "flame": return prefix + "火矢";
            case "infinity": return prefix + "无限";
            case "luck_of_the_sea": return prefix + "海之眷顾";
            case "lure": return prefix + "饵钓";
            case "loyalty": return prefix + "忠诚";
            case "impaling": return prefix + "穿刺";
            case "riptide": return prefix + "激流";
            case "channeling": return prefix + "引雷";
            case "multishot": return prefix + "多重射击";
            case "quick_charge": return prefix + "快速装填";
            case "piercing": return prefix + "穿透";
            case "mending": return prefix + "经验修补";
            case "vanishing_curse": return prefix + "消失诅咒";
            case "soul_speed": return prefix + "灵魂疾行";
            case "swift_sneak": return prefix + "迅捷潜行";
            case "density": return prefix + "致密";
            case "breach": return prefix + "破甲";
            case "wind_burst": return prefix + "风爆";
            case "lunge": return prefix + "突进";
            default:
                String[] words = key.split("_");
                StringBuilder name = new StringBuilder();
                for(String w : words) name.append(w.substring(0,1).toUpperCase()).append(w.substring(1)).append(" ");
                return Kitloader.color(prefix + name.toString().trim());
        }
    }

    public ItemStack getCategoryItem(String category, int page, int slotIndex) {
        if (category.equals("public_kits")) {
            List<DataManager.PublicKit> pks = dataManager.publicKits;
            int index = page * 36 + slotIndex;
            if (index >= 0 && index < pks.size()) {
                DataManager.PublicKit pk = pks.get(index);
                ItemStack displayItem = new ItemStack(Material.CHEST);
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Kitloader.color("&#F2C94C&l" + pk.kitName));
                    List<String> lore = new ArrayList<>();
                    lore.add(Kitloader.color("&7&l✦ 上传者: &f&l" + pk.uploaderName));
                    lore.add(Kitloader.color("&7&l✦ 时间: &8&l" + formatTime(pk.uploadTime)));
                    lore.add(Kitloader.color("&7"));
                    lore.add(Kitloader.color("&#00B09B&l[▶] 左键 &f&l直接加载该Kit"));
                    lore.add(Kitloader.color("&#F2C94C&l[★] 右键 &f&l仅查看该Kit内容"));
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                return displayItem;
            }
            return null;
        }

        List<?> itemsRaw = guiConfig.getList("categories." + category);
        if (itemsRaw != null) {
            int index = page * 36 + slotIndex;
            if (index >= 0 && index < itemsRaw.size()) {
                Object obj = itemsRaw.get(index);
                if (obj instanceof ItemStack) return ((ItemStack) obj).clone();
            }
        }
        return null;
    }

    public String getCategoryByDisplay(String displayRaw) {
        if (Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(displayRaw)).equals("一键Kit")) return "public_kits";

        String display = Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(displayRaw));
        if (guiConfig.contains("categories_settings")) {
            for (String key : guiConfig.getConfigurationSection("categories_settings").getKeys(false)) {
                String confDisplay = Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(
                        Kitloader.color(guiConfig.getString("categories_settings." + key + ".display", key))));
                if (display.equals(confDisplay)) {
                    return key;
                }
            }
        }
        return displayRaw;
    }

    public List<String> getCategories() {
        if (guiConfig.contains("categories")) return new ArrayList<>(guiConfig.getConfigurationSection("categories").getKeys(false));
        return new ArrayList<>();
    }

    private void startDynamicTask() {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> {
            animationTick++;
            Material currentShulker = SHULKERS[(animationTick / 10) % SHULKERS.length];
            String colorStr = RAINBOW_COLORS[(animationTick / 5) % RAINBOW_COLORS.length];

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory() == null) continue;
                Inventory capturedInventory = player.getOpenInventory().getTopInventory();
                String currentTitle = player.getOpenInventory().getTitle();
                String rawTitle = Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(currentTitle));

                String rawCategoryPrefix = Kitloader.canonicalize(
                        org.bukkit.ChatColor.stripColor(plugin.getGuiTitle("category-prefix", "")));
                boolean isCategoryGui = rawTitle.startsWith(rawCategoryPrefix) && rawTitle.contains(" - P");
                boolean isEditGui = rawTitle.equals("自定义补给盒");

                if (isCategoryGui || isEditGui) {
                    player.getScheduler().run(plugin, entityTask -> {
                        if (!player.isOnline() || player.getOpenInventory().getTopInventory() == null) return;
                        if (player.getOpenInventory().getTopInventory() != capturedInventory
                                || !player.getOpenInventory().getTitle().equals(currentTitle)) return;
                        Inventory topInv = capturedInventory;

                        if (isCategoryGui) {
                            updateCategoryIcons(topInv, colorStr, currentShulker, animationTick);
                            if (rawTitle.contains("补给盒子")) {
                                boolean enabled = plugin.getConfig().getBoolean("settings.custom-supply.enabled", true);
                                ItemStack customBoxBtn = new ItemStack(enabled ? Material.CHEST : Material.BARRIER);
                                ItemMeta customMeta = customBoxBtn.getItemMeta();
                                if (customMeta != null) {
                                    customMeta.setDisplayName(Kitloader.color(enabled
                                            ? colorStr + "&l[+] 自定义上传补给"
                                            : "&#FF5E62&l[X] 补给上传已关闭"));
                                    customMeta.setLore(List.of(Kitloader.color(enabled
                                            ? "&#95A5A6&l点击编辑并上传自定义补给"
                                            : "&#95A5A6&l管理员当前禁止玩家上传补给")));
                                    customBoxBtn.setItemMeta(customMeta);
                                }
                                topInv.setItem(38, customBoxBtn);
                            }
                        }

                        if (isEditGui) {
                            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
                            if (pData != null && pData.editSession != null) {
                                ItemStack colorSwitch = new ItemStack(pData.editSession.color);
                                ItemMeta switchMeta = colorSwitch.getItemMeta();
                                if (switchMeta != null) {
                                    switchMeta.setDisplayName(Kitloader.color(colorStr + "&l✦ 切换补给盒颜色"));
                                    switchMeta.setLore(List.of(Kitloader.color("&#95A5A6&l当前选定颜色: &f&l" + pData.editSession.color.name())));
                                    colorSwitch.setItemMeta(switchMeta);
                                }
                                topInv.setItem(47, colorSwitch);
                            }
                        }
                    }, null);
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void updateCategoryIcons(Inventory inv, String colorStr, Material currentShulker, int tick) {
        if (!guiConfig.contains("categories_settings")) return;

        int[] catSlots = {46, 48, 50};
        int index = 0;

        for (String catKey : guiConfig.getConfigurationSection("categories_settings").getKeys(false)) {
            if (index >= catSlots.length) break;
            int slot = catSlots[index];

            String display = guiConfig.getString("categories_settings." + catKey + ".display", catKey);
            String type = guiConfig.getString("categories_settings." + catKey + ".type", "NORMAL");

            ItemStack iconItem;
            if (type.equals("DYNAMIC_SHULKER")) {
                iconItem = new ItemStack(currentShulker);
            } else if (type.equals("DYNAMIC_ITEMS")) {
                List<?> itemsRaw = guiConfig.getList("categories." + catKey);
                if (itemsRaw == null || itemsRaw.isEmpty()) {
                    iconItem = new ItemStack(Material.CHEST);
                } else {
                    List<ItemStack> validItems = new ArrayList<>();
                    for (Object obj : itemsRaw) {
                        if (obj instanceof ItemStack item && !item.getType().isAir()
                                && item.getType() != Material.TOTEM_OF_UNDYING) validItems.add(item);
                    }
                    if (validItems.isEmpty()) {
                        iconItem = new ItemStack(Material.CHEST);
                    } else {
                        iconItem = validItems.get((tick / 10) % validItems.size()).clone();
                    }
                }
            } else {
                iconItem = new ItemStack(Material.CHEST);
            }

            ItemMeta meta = iconItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Kitloader.color(colorStr + "&l" + display));
                if (meta.hasEnchants()) {
                    for (org.bukkit.enchantments.Enchantment enchant : new ArrayList<>(meta.getEnchants().keySet())) meta.removeEnchant(enchant);
                }
                iconItem.setItemMeta(meta);
            }
            inv.setItem(slot, iconItem);
            index++;
        }

        inv.setItem(52, createBtn(Material.CHEST, "&#F2C94C&l★ 一键Kit", "&#95A5A6&l点击进入所有玩家共享的Kit库"));
    }

    private List<ItemStack> getRawCategoryItems(String category) {
        List<ItemStack> items = new ArrayList<>();
        synchronized (uploadedSupplyLock) {
            List<?> rawList = guiConfig.getList("categories." + category);
            if (rawList != null) {
                for (Object obj : rawList) {
                    if (obj instanceof ItemStack item && !item.getType().isAir()) items.add(item.clone());
                }
            }
        }
        return items;
    }

    private void saveUploadedSupplyRecords() {
        synchronized (uploadedSupplyLock) {
            writeUploadedSupplyRecordsToConfig();
            try { guiConfig.save(guiFile); } catch (IOException ignored) {}
        }
    }

    private void saveSupplyState(List<ItemStack> staticItems) {
        synchronized (uploadedSupplyLock) {
            guiConfig.set("categories.supply", staticItems);
            writeUploadedSupplyRecordsToConfig();
            try { guiConfig.save(guiFile); } catch (IOException ignored) {}
        }
    }

    private void writeUploadedSupplyRecordsToConfig() {
        guiConfig.set("uploaded_supplies", null);
        sortUploadedSupplyRecords();
        for (UploadedSupplyRecord record : uploadedSupplyRecords.values()) {
            String path = "uploaded_supplies." + record.id;
            guiConfig.set(path + ".owner", record.owner.toString());
            guiConfig.set(path + ".hidden", record.hidden);
            guiConfig.set(path + ".uploadTime", record.uploadTime);
            guiConfig.set(path + ".item", record.item.clone());
        }
    }

    private long nextUploadedSupplyTime() {
        return uploadedSupplyClock.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
    }

    private void sortUploadedSupplyRecords() {
        List<UploadedSupplyRecord> records = new ArrayList<>(uploadedSupplyRecords.values());
        records.sort(Comparator.comparingLong(record -> record.uploadTime));
        uploadedSupplyRecords.clear();
        for (UploadedSupplyRecord record : records) uploadedSupplyRecords.put(record.id, record);
    }

    private ItemStack withoutUploadedSupplyMetadata(ItemStack item) {
        if (item == null) return null;
        ItemStack clone = item.clone();
        stripUploadedSupplyMetadata(clone);
        return clone;
    }

    public boolean stripUploadedSupplyMetadata(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        boolean changed = hasLegacySupplyPersistentData(meta);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (lore.removeIf(this::isSupplyMetadataLine)) changed = true;
        if (!changed) return false;

        meta.setLore(lore.isEmpty() ? null : lore);
        removeLegacySupplyPersistentData(meta);
        item.setItemMeta(meta);
        return true;
    }

    private UploadedSupplyMetadata readLegacyUploadedSupplyMetadata(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (!isSupplyMetadataLine(line)) continue;
                String[] parts = line.substring(SUPPLY_METADATA_PREFIX.length()).split("\\|", -1);
                if (parts.length == 3 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    return new UploadedSupplyMetadata(parts[0], parts[1], parts[2].equals("1"));
                }
            }
        }

        String id = meta.getPersistentDataContainer().get(supplyIdKey, PersistentDataType.STRING);
        String owner = meta.getPersistentDataContainer().get(supplyOwnerKey, PersistentDataType.STRING);
        Byte hidden = meta.getPersistentDataContainer().get(supplyHiddenKey, PersistentDataType.BYTE);
        if (id == null || owner == null) return null;
        return new UploadedSupplyMetadata(id, owner, hidden != null && hidden == (byte) 1);
    }

    private boolean isSupplyMetadataLine(String line) {
        return line != null && line.startsWith(SUPPLY_METADATA_PREFIX);
    }

    private boolean hasLegacySupplyPersistentData(ItemMeta meta) {
        return meta.getPersistentDataContainer().has(supplyIdKey, PersistentDataType.STRING)
                || meta.getPersistentDataContainer().has(supplyOwnerKey, PersistentDataType.STRING)
                || meta.getPersistentDataContainer().has(supplyHiddenKey, PersistentDataType.BYTE);
    }

    private void removeLegacySupplyPersistentData(ItemMeta meta) {
        meta.getPersistentDataContainer().remove(supplyIdKey);
        meta.getPersistentDataContainer().remove(supplyOwnerKey);
        meta.getPersistentDataContainer().remove(supplyHiddenKey);
    }

    private void migrateLegacyPublicSupplyMetadata() {
        List<ItemStack> publicItems = getRawCategoryItems("supply");
        List<ItemStack> staticItems = new ArrayList<>();
        boolean changed = false;
        for (ItemStack item : publicItems) {
            UploadedSupplyMetadata metadata = readLegacyUploadedSupplyMetadata(item);
            if (metadata != null) {
                try {
                    UUID owner = UUID.fromString(metadata.owner);
                    ItemStack cleanItem = withoutUploadedSupplyMetadata(item);
                    synchronized (uploadedSupplyLock) {
                        uploadedSupplyRecords.putIfAbsent(metadata.id,
                                new UploadedSupplyRecord(metadata.id, owner, metadata.hidden, cleanItem,
                                        nextUploadedSupplyTime()));
                    }
                    changed = true;
                    continue;
                } catch (IllegalArgumentException ignored) {
                    changed |= stripUploadedSupplyMetadata(item);
                }
            } else {
                changed |= stripUploadedSupplyMetadata(item);
            }
            staticItems.add(item);
        }
        if (changed) saveSupplyState(staticItems);
    }

    public ItemStack createUploadedSupplyDeliveryCopy(ItemStack item) {
        return withoutUploadedSupplyMetadata(item);
    }

    public int getUploadedSupplyEnderSlots() {
        return Math.max(1, Math.min(27,
                plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9)));
    }

    public int getUploadedSupplyEnderAreaSlots() {
        int slots = getUploadedSupplyEnderSlots();
        return ((slots + 8) / 9) * 9;
    }

    public int getUploadedSupplyPageSize() {
        return 45 - getUploadedSupplyEnderAreaSlots();
    }

    private boolean isSameSupplyIgnoringMetadata(ItemStack first, ItemStack second) {
        ItemStack cleanFirst = withoutUploadedSupplyMetadata(first);
        ItemStack cleanSecond = withoutUploadedSupplyMetadata(second);
        return cleanFirst != null && cleanSecond != null
                && cleanFirst.getAmount() == cleanSecond.getAmount()
                && cleanFirst.isSimilar(cleanSecond);
    }

    public String prepareUploadedSupply(Player owner, ItemStack box, boolean visible) {
        stripUploadedSupplyMetadata(box);
        String supplyId = UUID.randomUUID().toString();
        synchronized (uploadedSupplyLock) {
            for (UploadedSupplyRecord existing : uploadedSupplyRecords.values()) {
                if (UploadContentMatcher.sameSupplyContents(existing.item, box)) return null;
            }
            uploadedSupplyRecords.put(supplyId, new UploadedSupplyRecord(
                    supplyId, owner.getUniqueId(), !visible, box.clone(), nextUploadedSupplyTime()));
            sortUploadedSupplyRecords();
        }
        saveUploadedSupplyRecords();
        refreshOpenSupplyPages();
        return supplyId;
    }

    public void ensureUploadedSupplyMetadata(Player player, DataManager.PlayerData pData) {
        ensureUploadedSupplyMetadata(player.getUniqueId(), pData);
    }

    public void ensureUploadedSupplyMetadata(UUID ownerId, DataManager.PlayerData pData) {
        if (pData == null) return;

        boolean playerChanged = pData.uploadedSupplyIds.size() != pData.uploadedSupplies.size();
        while (pData.uploadedSupplyIds.size() < pData.uploadedSupplies.size()) {
            pData.uploadedSupplyIds.add("");
        }
        while (pData.uploadedSupplyIds.size() > pData.uploadedSupplies.size()) {
            pData.uploadedSupplyIds.remove(pData.uploadedSupplyIds.size() - 1);
        }

        List<ItemStack> staticItems = getRawCategoryItems("supply");
        Set<String> claimedRecordIds = new HashSet<>();
        boolean recordsChanged = false;
        boolean staticChanged = false;

        synchronized (uploadedSupplyLock) {
        for (int supplyIndex = 0; supplyIndex < pData.uploadedSupplies.size(); supplyIndex++) {
            ItemStack ownedBox = pData.uploadedSupplies.get(supplyIndex);
            if (ownedBox == null || ownedBox.getType().isAir()) continue;

            UploadedSupplyMetadata legacyMetadata = readLegacyUploadedSupplyMetadata(ownedBox);
            if (stripUploadedSupplyMetadata(ownedBox)) playerChanged = true;

            String supplyId = pData.uploadedSupplyIds.get(supplyIndex);
            UploadedSupplyRecord record = supplyId.isBlank() ? null : uploadedSupplyRecords.get(supplyId);
            if (record != null && !record.owner.equals(ownerId)) {
                record = null;
                supplyId = "";
            }

            if (record == null && legacyMetadata != null
                    && legacyMetadata.owner.equals(ownerId.toString())) {
                UploadedSupplyRecord legacyRecord = uploadedSupplyRecords.get(legacyMetadata.id);
                if (legacyRecord != null && legacyRecord.owner.equals(ownerId)) {
                    supplyId = legacyMetadata.id;
                    record = legacyRecord;
                } else if (supplyId.isBlank() && !uploadedSupplyRecords.containsKey(legacyMetadata.id)) {
                    supplyId = legacyMetadata.id;
                }
            }

            if (record == null) {
                for (UploadedSupplyRecord candidate : uploadedSupplyRecords.values()) {
                    if (claimedRecordIds.contains(candidate.id)
                            || !candidate.owner.equals(ownerId)) continue;
                    if (isSameSupplyIgnoringMetadata(candidate.item, ownedBox)) {
                        supplyId = candidate.id;
                        record = candidate;
                        break;
                    }
                }
            }

            if (record == null) {
                for (int staticIndex = 0; staticIndex < staticItems.size(); staticIndex++) {
                    if (!isSameSupplyIgnoringMetadata(staticItems.get(staticIndex), ownedBox)) continue;
                    staticItems.remove(staticIndex);
                    staticChanged = true;
                    break;
                }

                if (supplyId.isBlank() || uploadedSupplyRecords.containsKey(supplyId)) {
                    supplyId = UUID.randomUUID().toString();
                }
                record = new UploadedSupplyRecord(supplyId, ownerId,
                        !pData.uploadedSuppliesVisible, ownedBox.clone(), nextUploadedSupplyTime());
                uploadedSupplyRecords.put(supplyId, record);
                recordsChanged = true;
            } else {
                boolean hidden = !pData.uploadedSuppliesVisible;
                if (record.hidden != hidden || !isSameSupplyIgnoringMetadata(record.item, ownedBox)) {
                    record.hidden = hidden;
                    record.item = ownedBox.clone();
                    recordsChanged = true;
                }
            }

            claimedRecordIds.add(supplyId);
            if (!supplyId.equals(pData.uploadedSupplyIds.get(supplyIndex))) {
                pData.uploadedSupplyIds.set(supplyIndex, supplyId);
                playerChanged = true;
            }
        }
        if (recordsChanged) sortUploadedSupplyRecords();
        if (sortPlayerUploadedSuppliesByTime(pData)) playerChanged = true;
        }

        if (recordsChanged || staticChanged) saveSupplyState(staticItems);
        if (playerChanged) dataManager.saveOfflinePlayerAsync(pData);
    }

    public boolean updateUploadedSupply(String supplyId, UUID ownerId, boolean hidden, ItemStack item) {
        if (supplyId == null || supplyId.isBlank() || item == null || item.getType().isAir()) return false;
        ItemStack cleanItem = createUploadedSupplyDeliveryCopy(item);
        synchronized (uploadedSupplyLock) {
            UploadedSupplyRecord record = uploadedSupplyRecords.get(supplyId);
            if (record != null && !record.owner.equals(ownerId)) return false;
            if (record == null) {
                uploadedSupplyRecords.put(supplyId,
                        new UploadedSupplyRecord(supplyId, ownerId, hidden, cleanItem,
                                nextUploadedSupplyTime()));
            } else {
                record.hidden = hidden;
                record.item = cleanItem;
            }
        }
        saveUploadedSupplyRecords();
        refreshOpenSupplyPages();
        return true;
    }

    public boolean toggleUploadedSuppliesVisibility(Player player) {
        DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
        if (pData == null) return true;

        ensureUploadedSupplyMetadata(player, pData);
        pData.uploadedSuppliesVisible = !pData.uploadedSuppliesVisible;
        boolean hidden = !pData.uploadedSuppliesVisible;

        boolean recordsChanged = false;
        synchronized (uploadedSupplyLock) {
            for (String supplyId : pData.uploadedSupplyIds) {
                UploadedSupplyRecord record = uploadedSupplyRecords.get(supplyId);
                if (record != null && record.owner.equals(player.getUniqueId()) && record.hidden != hidden) {
                    record.hidden = hidden;
                    recordsChanged = true;
                }
            }

            if (!hidden) {
                for (String supplyId : pData.uploadedSupplyIds) {
                    UploadedSupplyRecord record = uploadedSupplyRecords.get(supplyId);
                    if (record != null && record.owner.equals(player.getUniqueId())) {
                        record.uploadTime = nextUploadedSupplyTime();
                        recordsChanged = true;
                    }
                }
                if (recordsChanged) sortUploadedSupplyRecords();
                sortPlayerUploadedSuppliesByTime(pData);
            }

        }

        if (recordsChanged) saveUploadedSupplyRecords();
        dataManager.savePlayerAsync(player.getUniqueId());
        refreshOpenSupplyPages();
        return pData.uploadedSuppliesVisible;
    }

    private boolean sortPlayerUploadedSuppliesByTime(DataManager.PlayerData pData) {
        int size = Math.min(pData.uploadedSupplies.size(), pData.uploadedSupplyIds.size());
        if (size < 2) return false;

        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < size; index++) order.add(index);
        order.sort(Comparator.comparingLong((Integer index) -> {
                    UploadedSupplyRecord record = uploadedSupplyRecords.get(pData.uploadedSupplyIds.get(index));
                    return record != null ? record.uploadTime : Long.MAX_VALUE;
                }));

        boolean changed = false;
        for (int index = 0; index < size; index++) {
            if (order.get(index) != index) {
                changed = true;
                break;
            }
        }
        if (!changed) return false;

        List<ItemStack> sortedSupplies = new ArrayList<>(pData.uploadedSupplies);
        List<String> sortedIds = new ArrayList<>(pData.uploadedSupplyIds);
        for (int index = 0; index < size; index++) {
            int sourceIndex = order.get(index);
            sortedSupplies.set(index, pData.uploadedSupplies.get(sourceIndex));
            sortedIds.set(index, pData.uploadedSupplyIds.get(sourceIndex));
        }
        pData.uploadedSupplies.clear();
        pData.uploadedSupplies.addAll(sortedSupplies);
        pData.uploadedSupplyIds.clear();
        pData.uploadedSupplyIds.addAll(sortedIds);
        return true;
    }

    public void refreshOpenSupplyPages() {
        String categoryPrefix = Kitloader.canonicalize(
                org.bukkit.ChatColor.stripColor(plugin.getGuiTitle("category-prefix", "")));
        String supplyDisplay = org.bukkit.ChatColor.stripColor(
                guiConfig.getString("categories_settings.supply.display", "补给盒子"));
        String publicSupplyPrefix = categoryPrefix + supplyDisplay + " - P";

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.getScheduler().run(plugin, task -> {
                if (!viewer.isOnline()) return;
                String title = Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(viewer.getOpenInventory().getTitle()));
                if (title == null) return;

                int requestedPage = parseSupplyPage(title);
                int maxPage = Math.max(0, (getVisibleSupplyEntries(viewer).size() - 1) / 36);
                int page = Math.max(0, Math.min(requestedPage, maxPage));
                if (title.startsWith(publicSupplyPrefix)) {
                    openCategoryGui(viewer, "supply", page);
                } else if (title.contains("末影箱直存模式") && title.contains(" - P")) {
                    openSupplyEnderChestGui(viewer, page);
                }
            }, null);
        }
    }

    public void refreshUploadedSupplyManagementPage(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;
        owner.getScheduler().run(plugin, task -> {
            if (!owner.isOnline()) return;
            String title = Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(owner.getOpenInventory().getTitle()));
            if (title == null || !title.contains("已上传的补给")) return;
            openUploadedSuppliesGui(owner, parseSupplyPage(title));
        }, null);
    }

    private int parseSupplyPage(String title) {
        int marker = title.lastIndexOf(" - P");
        if (marker < 0) return 0;
        try {
            return Math.max(0, Integer.parseInt(title.substring(marker + 3).trim()) - 1);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public List<ItemStack> getVisibleCategoryItems(Player viewer, String category) {
        if (!category.equals("supply")) return getRawCategoryItems(category);

        List<ItemStack> visible = new ArrayList<>();
        for (SupplyPageEntry entry : getVisibleSupplyEntries(viewer)) {
            visible.add(entry.item.clone());
        }
        return visible;
    }

    private List<SupplyPageEntry> getVisibleSupplyEntries(Player viewer) {
        List<SupplyPageEntry> entries = new ArrayList<>();
        for (ItemStack staticItem : getRawCategoryItems("supply")) {
            entries.add(new SupplyPageEntry(null, staticItem.clone()));
        }
        synchronized (uploadedSupplyLock) {
            List<UploadedSupplyRecord> records = new ArrayList<>(uploadedSupplyRecords.values());
            records.sort(Comparator.comparingLong(record -> record.uploadTime));
            for (UploadedSupplyRecord record : records) {
                if (!record.hidden) {
                    entries.add(new SupplyPageEntry(record.id, record.item.clone()));
                }
            }
        }
        return entries;
    }

    public ItemStack getVisibleCategoryItem(Player viewer, String category, int page, int slotIndex) {
        List<ItemStack> items = getVisibleCategoryItems(viewer, category);
        int index = page * 36 + slotIndex;
        if (index < 0 || index >= items.size()) return null;
        ItemStack item = items.get(index);
        return category.equals("supply") ? createUploadedSupplyDeliveryCopy(item) : item.clone();
    }

    private void cacheSupplyPageView(Player player, int page) {
        SupplyPageEntry[] pageItems = new SupplyPageEntry[36];
        List<SupplyPageEntry> entries = getVisibleSupplyEntries(player);
        int startIndex = page * 36;
        for (int slot = 0; slot < pageItems.length; slot++) {
            int index = startIndex + slot;
            if (index < entries.size()) pageItems[slot] = entries.get(index);
        }
        supplyPageViews.put(player.getUniqueId(), new SupplyPageView(page, pageItems));
    }

    public ItemStack getCachedVisibleSupplyItem(Player viewer, int page, int slot) {
        SupplyPageView view = supplyPageViews.get(viewer.getUniqueId());
        if (view == null || view.page != page || slot < 0 || slot >= view.items.length) return null;
        SupplyPageEntry entry = view.items[slot];
        if (entry == null || entry.item == null || entry.item.getType().isAir()) return null;
        if (entry.uploadedSupplyId == null) return createUploadedSupplyDeliveryCopy(entry.item);

        synchronized (uploadedSupplyLock) {
            UploadedSupplyRecord record = uploadedSupplyRecords.get(entry.uploadedSupplyId);
            if (record == null || record.hidden) return null;
            return createUploadedSupplyDeliveryCopy(record.item);
        }
    }

    public boolean removeSupplyFromPublic(String supplyId, UUID owner) {
        boolean removed;
        synchronized (uploadedSupplyLock) {
            UploadedSupplyRecord record = uploadedSupplyRecords.get(supplyId);
            removed = record != null && record.owner.equals(owner);
            if (removed) uploadedSupplyRecords.remove(supplyId);
        }
        if (removed) {
            saveUploadedSupplyRecords();
            refreshOpenSupplyPages();
        }
        return removed;
    }

    public ItemStack createSupplyDisplayItem(ItemStack item, String... operationLore) {
        ItemStack displayItem = createUploadedSupplyDeliveryCopy(item);
        if (displayItem == null) return null;
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;

        List<String> originalLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        originalLore.removeIf(this::isSupplyOperationLore);
        int reservedLines = operationLore.length + (originalLore.isEmpty() ? 0 : 1);
        int maxOriginalLines = Math.max(0, MAX_SUPPLY_DISPLAY_LORE_LINES - reservedLines);
        if (originalLore.size() > maxOriginalLines) {
            originalLore = new ArrayList<>(originalLore.subList(0, maxOriginalLines));
        }
        if (!originalLore.isEmpty() && operationLore.length > 0) originalLore.add(Kitloader.color("&7"));
        for (String line : operationLore) originalLore.add(Kitloader.color(line));
        meta.setLore(originalLore.isEmpty() ? null : originalLore);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    private boolean isSupplyOperationLore(String line) {
        String plain = Kitloader.canonicalize(org.bukkit.ChatColor.stripColor(line == null ? "" : line));
        return plain.contains("其他玩家可以在公共补给页看见")
                || plain.contains("当前只有你自己可以看见")
                || plain.contains("直接存入下方")
                || plain.contains("永久删除该补给")
                || plain.contains("直接获取物品")
                || plain.contains("仅你自己可见")
                || plain.contains("仅可在本管理页使用")
                || plain.contains("已从公共补给页移除");
    }

    private ItemStack createBtn(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Kitloader.color(name));
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String l : lore.split("\n")) coloredLore.add(Kitloader.color(l));
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openCustomSupplyEditGui(Player player) {
        player.getScheduler().run(plugin, task -> {
            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
            if (pData.editSession == null) pData.editSession = new DataManager.EditSession();

            Inventory inv = Bukkit.createInventory(null, 54, Kitloader.color("&#FF0099&l[+] &#FF1188&l自&#FF2277&l定&#FF3366&l义&#FF4455&l补&#FF5544&l给&#FF5544&l盒"));
            for (int i = 0; i < 27; i++) {
                if (pData.editSession.items[i] != null) {
                    inv.setItem(i, pData.editSession.items[i].clone());
                }
            }

            inv.setItem(45, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l✖ 取消上传并返回", "&#95A5A6&l清空当前内容并返回"));
            inv.setItem(47, createBtn(pData.editSession.color, "&#F2C94C&l✦ 切换补给盒颜色", "&#95A5A6&l当前选定: &f&l" + pData.editSession.color.name()));
            inv.setItem(49, createBtn(Material.YELLOW_STAINED_GLASS_PANE, "&#F2C94C&l[S] 不上传暂时保存", "&#95A5A6&l点击暂存编辑进度"));
            inv.setItem(51, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 修改补给盒名称", "&#95A5A6&l当前: &f&l" + pData.editSession.name));
            inv.setItem(53, createBtn(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l✔ 确认打包上传", "&#95A5A6&l打包为你专属的补给盒"));

            fillBeautifulGradient(inv, 0, 26);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openPublicKitUploadGui(Player player) {
        player.getScheduler().run(plugin, task -> {
            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
            if (pData.publicEditSession == null) pData.publicEditSession = new DataManager.EditPublicKitSession();

            Inventory inv = Bukkit.createInventory(null, 54, Kitloader.color("&#FF0099&l▲ &#FF1188&l上&#FF2277&l传&#FF3366&l共&#FF4455&l享&#FF5544&lK&#FF5544&li&#FF5544&lt"));
            for (int i = 0; i < 41; i++) {
                if (pData.publicEditSession.items[i] != null) {
                    inv.setItem(i, pData.publicEditSession.items[i].clone());
                }
            }

            inv.setItem(45, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l✖ 放弃上传并退出", "&#95A5A6&l清空当前进度\n&#95A5A6&l(需二次确认)"));
            inv.setItem(49, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 命名此Kit", "&#95A5A6&l当前名称: &f&l" + pData.publicEditSession.name));
            inv.setItem(53, createBtn(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l✔ 确认上传共享", "&#95A5A6&l检查是否满配并上传\n&#95A5A6&l(需二次确认)"));

            fillBeautifulGradient(inv, 0, 40);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openMyPublicKitsGui(Player player) {
        player.getScheduler().run(plugin, task -> {
            Inventory inv = Bukkit.createInventory(null, 54, Kitloader.color("&#F12711&l[=] &#F34217&l我&#F45E1E&l的&#F67924&l共&#F8952B&l享&#F9B031&lK&#F9B031&li&#F9B031&lt"));

            List<DataManager.PublicKit> myKits = new ArrayList<>();
            for (DataManager.PublicKit pk : dataManager.publicKits) {
                if (pk.uploaderUuid.equals(player.getUniqueId())) myKits.add(pk);
            }

            inv.setItem(4, createBtn(Material.BOOK, "&#F2C94C&l[?] 操作提示指南", "\n&#00B09B&l[▶] 左键 &f&l编辑并更新该共享Kit\n&#FF5E62&l[✖] 右键 &f&l永久删除该共享Kit"));

            List<Integer> slots = getCenteredSlots(1, 3, myKits.size(), 7);
            for (int i = 0; i < myKits.size() && i < slots.size(); i++) {
                DataManager.PublicKit pk = myKits.get(i);
                ItemStack displayItem = new ItemStack(Material.CHEST);
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Kitloader.color("&#F2C94C&l" + pk.kitName));
                    List<String> lore = new ArrayList<>();
                    lore.add(Kitloader.color("&7&l唯一ID: &8&l" + pk.id));
                    lore.add(Kitloader.color("&7&l上传时间: &8&l" + formatTime(pk.uploadTime)));
                    lore.add(Kitloader.color("&7"));
                    lore.add(Kitloader.color("&#00B09B&l[✎] 左键 &f&l编辑与重命名"));
                    lore.add(Kitloader.color("&#FF5E62&l[✖] 右键 &f&l永久删除"));
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                inv.setItem(slots.get(i), displayItem);
            }

            inv.setItem(49, createBtn(Material.ARROW, "&#00B09B&l◀ 返回上一级", "&#95A5A6&l回到一键Kit大厅"));
            fillBeautifulGradient(inv, -1, -1);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openPublicKitViewGui(Player player, DataManager.PublicKit pk) {
        player.getScheduler().run(plugin, task -> {
            Inventory inv = Bukkit.createInventory(null, 54, Kitloader.color("&#FF0099&l★ &#FF1188&l查&#FF2277&l看&#FF3366&l共&#FF4455&l享&#FF5544&lK&#FF5544&li&#FF5544&lt"));
            for (int i = 0; i < 41; i++) {
                if (pk.items[i] != null) inv.setItem(i, pk.items[i].clone());
            }
            inv.setItem(45, createBtn(Material.ARROW, "&#00B09B&l◀ 返回上一页", "&#95A5A6&l返回Kit列表"));
            inv.setItem(53, createBtn(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l✔ 确认提取此Kit", "&#95A5A6&l将Kit加载到自己身上"));

            fillBeautifulGradient(inv, 0, 40);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openPublicKitEditGui(Player player, DataManager.PublicKit pk, boolean fromCache) {
        player.getScheduler().run(plugin, task -> {
            cachePublicTarget(player.getUniqueId(), pk.id);
            String title = Kitloader.color("&#FF0099&l编&#FF0E9B&l辑&#FF1C9D&l共&#FF2A9F&l享&#FF38A1&lK&#FF46A3&li&#FF54A5&lt&#FF62A7&l: &f&l" + pk.kitName);
            Inventory inv = Bukkit.createInventory(null, 54, title);

            ItemStack[] bkKit = null;
            if (fromCache && publicKitEditCache.containsKey(player.getUniqueId())) {
                bkKit = publicKitEditCache.get(player.getUniqueId());
            } else {
                bkKit = pk.items;
            }

            if (bkKit != null) {
                for (int i = 9; i <= 35; i++) if(i < bkKit.length && bkKit[i] != null) inv.setItem(i - 9, bkKit[i].clone());
                for (int i = 0; i <= 8; i++)  if(i < bkKit.length && bkKit[i] != null) inv.setItem(i + 27, bkKit[i].clone());
                if (bkKit.length >= 41) {
                    if (bkKit[39] != null) inv.setItem(36, bkKit[39].clone());
                    if (bkKit[38] != null) inv.setItem(37, bkKit[38].clone());
                    if (bkKit[37] != null) inv.setItem(38, bkKit[37].clone());
                    if (bkKit[36] != null) inv.setItem(39, bkKit[36].clone());
                    if (bkKit[40] != null) inv.setItem(40, bkKit[40].clone());
                }
            }

            inv.setItem(45, createBtn(Material.ARROW, "&#00B09B&l◀ 返回", "&#95A5A6&l保存对此共享Kit的修改"));
            inv.setItem(49, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 重命名该Kit", "&#95A5A6&l修改该共享Kit的名称"));
            inv.setItem(53, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[X] 确认永久删除", "&#95A5A6&l将该Kit从共享库中彻底抹除"));

            fillBeautifulGradient(inv, 0, 40);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openUploadedSuppliesGui(Player player) {
        openUploadedSuppliesGui(player, 0);
    }

    public void openUploadedSuppliesGui(Player player, int requestedPage) {
        long navigationVersion = beginPageNavigation(player);
        player.getScheduler().run(plugin, task -> {
            if (!isLatestPageNavigation(player, navigationVersion)) return;

            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
            if (pData == null) return;
            ensureUploadedSupplyMetadata(player, pData);

            int pageSize = getUploadedSupplyPageSize();
            int toolbarStart = pageSize;
            int enderStart = toolbarStart + 9;
            int enderSlots = getUploadedSupplyEnderSlots();
            int enderAreaSlots = getUploadedSupplyEnderAreaSlots();
            int maxPage = Math.max(0, (pData.uploadedSupplies.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = Kitloader.color("&#F12711&l[=] &#F34217&l已&#F45E1E&l上&#F67924&l传&#F8952B&l的&#F9B031&l补&#F9B031&l给 &8&l- P" + (page + 1));
            Inventory inv = Bukkit.createInventory(null, 54, title);

            int startIndex = page * pageSize;
            for (int slot = 0; slot < pageSize; slot++) {
                int supplyIndex = startIndex + slot;
                if (supplyIndex >= pData.uploadedSupplies.size()) break;

                ItemStack box = createSupplyDisplayItem(
                        pData.uploadedSupplies.get(supplyIndex),
                        pData.uploadedSuppliesVisible
                                ? "&#00B09B&l[公开中] &f&l所有玩家可在公共补给页看见"
                                : "&#FF5E62&l[已隐藏] &f&l已从公共补给页移除，仅可在本管理页使用",
                        "&#00B09B&l[▶] 左键/Shift &f&l直接存入下方末影箱",
                        "&#FF5E62&l[✖] 右键 &f&l永久删除该补给");
                inv.setItem(slot, box);
            }

            if (page > 0) {
                inv.setItem(toolbarStart, createBtn(Material.ARROW, plugin.getGuiTitle("btn-prev-page", ""), ""));
            }
            inv.setItem(toolbarStart + 2, createBtn(Material.BOOK, "&#F2C94C&l[?] 操作提示",
                    "&#00B09B&l左键/Shift：&f&l直接存入末影箱\n&#FF5E62&l右键补给：&f&l永久删除\n&#95A5A6&l底部区域：&f&l已开放 " + enderSlots + " 格末影箱"));
            inv.setItem(toolbarStart + 4, createBtn(Material.ARROW, "&#00B09B&l◀ 返回补给页面", "&#95A5A6&l回到公共补给盒子列表"));
            inv.setItem(toolbarStart + 6, createBtn(
                    pData.uploadedSuppliesVisible ? Material.LIME_DYE : Material.GRAY_DYE,
                    pData.uploadedSuppliesVisible ? "&#00B09B&l[公开中] 一键隐藏全部" : "&#FF5E62&l[已隐藏] 一键公开全部",
                    pData.uploadedSuppliesVisible
                            ? "&#95A5A6&l点击后，将从所有玩家的公共补给页移除"
                            : "&#95A5A6&l点击后，所有玩家可重新在公共补给页看见"));
            if (pData.uploadedSupplies.size() > (page + 1) * pageSize) {
                inv.setItem(toolbarStart + 8, createBtn(Material.ARROW, plugin.getGuiTitle("btn-next-page", ""), ""));
            }

            for (int slot = toolbarStart; slot < toolbarStart + 9; slot++) {
                if (inv.getItem(slot) == null || inv.getItem(slot).getType().isAir()) {
                    inv.setItem(slot, createBtn(Material.BLACK_STAINED_GLASS_PANE, "§7", ""));
                }
            }

            Inventory enderChest = player.getEnderChest();
            for (int index = 0; index < enderAreaSlots; index++) {
                if (index < enderSlots) {
                    ItemStack enderItem = enderChest.getItem(index);
                    if (enderItem != null && !enderItem.getType().isAir()) {
                        inv.setItem(enderStart + index, createUploadedSupplyDeliveryCopy(enderItem));
                    }
                } else {
                    inv.setItem(enderStart + index, createBtn(Material.RED_STAINED_GLASS_PANE,
                            "&#FF5E62&l✖ 未解锁的格子", "&#95A5A6&l可通过末影箱上限配置开放"));
                }
            }

            fillBeautifulGradient(inv, 0, pageSize - 1);
            if (!isLatestPageNavigation(player, navigationVersion)) return;
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openEnchantGui(Player player) {
        player.getScheduler().run(plugin, task -> {
            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
            if (pData == null || pData.editItemSession == null) return;

            Inventory inv = Bukkit.createInventory(null, 54, Kitloader.color("&#8E2DE2&l附&#8020DF&l魔&#7213DC&l与&#6406D9&l物&#5600D6&l品&#4A00E0&l编&#3D00EB&l辑"));
            inv.setItem(4, pData.editItemSession.currentItem.clone());

            List<org.bukkit.enchantments.Enchantment> valid = getValidEnchants(pData.editItemSession.currentItem);
            valid.sort(Comparator.comparing(this::getEnchantName));

            List<Integer> slots = getCenteredSlots(1, 4, valid.size(), 7);
            for (int i = 0; i < valid.size() && i < slots.size(); i++) {
                org.bukkit.enchantments.Enchantment enc = valid.get(i);
                boolean has = pData.editItemSession.currentItem.containsEnchantment(enc);

                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta meta = book.getItemMeta();
                meta.setDisplayName(Kitloader.color(getEnchantName(enc)));
                List<String> lore = new ArrayList<>();
                lore.add(Kitloader.color(has ? "&#95A5A6&l状态: &#00B09B&l✔ 已添加" : "&#95A5A6&l状态: &#FF5E62&l✖ 未添加"));
                lore.add(Kitloader.color("&7"));
                lore.add(Kitloader.color("&#F2C94C&l[▶] 点击 &f&l添加/移除该附魔"));
                meta.setLore(lore);
                book.setItemMeta(meta);
                inv.setItem(slots.get(i), book);
            }

            inv.setItem(45, createBtn(Material.ARROW, "&#00B09B&l◀ 放弃并返回", "&#95A5A6&l不保存更改返回"));
            if (isArmor(pData.editItemSession.currentItem)) {
                inv.setItem(47, createBtn(Material.SMITHING_TABLE, "&#A83279&l✦ 盔甲纹饰编辑", "&#95A5A6&l进入盔甲纹饰自定义界面"));
                inv.setItem(51, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 重命名装备", "&#95A5A6&l为该物品自定义名称"));
            } else {
                inv.setItem(49, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 重命名物品", "&#95A5A6&l为该物品自定义名称"));
            }
            inv.setItem(53, createBtn(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l✔ 保存并获取物品", "&#95A5A6&l将当前物品发放到背包"));

            fillBeautifulGradient(inv, -1, -1);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openArmorTrimGui(Player player) {
        player.getScheduler().run(plugin, task -> {
            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
            if (pData == null || pData.editItemSession == null) return;

            Inventory inv = Bukkit.createInventory(null, 54, Kitloader.color("&#000046&l盔&#071D43&l甲&#0F3941&l纹&#16563E&l饰&#1D733B&l与&#248F39&l名&#2CB036&l称"));
            inv.setItem(4, pData.editItemSession.currentItem.clone());

            List<Integer> patSlots = getCenteredSlots(1, 2, TRIM_PATTERNS.length, 9);
            List<Integer> matSlots = getCenteredSlots(3, 4, TRIM_MATERIALS.length, 7);

            ArmorMeta armorMeta = pData.editItemSession.currentItem.getItemMeta() instanceof ArmorMeta
                    ? (ArmorMeta) pData.editItemSession.currentItem.getItemMeta() : null;
            org.bukkit.inventory.meta.trim.ArmorTrim currentTrim = armorMeta != null ? armorMeta.getTrim() : null;

            for (int i = 0; i < TRIM_PATTERNS.length && i < patSlots.size(); i++) {
                String key = TRIM_PATTERNS[i].name().toLowerCase().replace("_armor_trim_smithing_template", "");
                org.bukkit.inventory.meta.trim.TrimPattern pattern = org.bukkit.Registry.TRIM_PATTERN.get(org.bukkit.NamespacedKey.minecraft(key));
                boolean applied = currentTrim != null && pattern != null && currentTrim.getPattern().equals(pattern);
                String status = applied ? "&#00B09B&l[已应用] 再次点击可移除" : "&#95A5A6&l[未应用] 点击应用此纹饰";
                inv.setItem(patSlots.get(i), createBtn(TRIM_PATTERNS[i], "&#00D2FF&l[纹饰] " + TRIM_PATTERNS[i].name().replace("_ARMOR_TRIM_SMITHING_TEMPLATE", ""), status));
            }
            for (int i = 0; i < TRIM_MATERIALS.length && i < matSlots.size(); i++) {
                org.bukkit.inventory.meta.trim.TrimMaterial material = getTrimMaterial(TRIM_MATERIALS[i]);
                boolean applied = currentTrim != null && material != null && currentTrim.getMaterial().equals(material);
                String status = applied ? "&#00B09B&l[已应用] 再次点击可移除" : "&#95A5A6&l[未应用] 点击应用此材质";
                inv.setItem(matSlots.get(i), createBtn(TRIM_MATERIALS[i], "&#F2C94C&l[材质] " + TRIM_MATERIALS[i].name(), status));
            }

            inv.setItem(45, createBtn(Material.ARROW, "&#00B09B&l◀ 返回上一级", "&#95A5A6&l回到附魔编辑界面"));
            inv.setItem(49, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 重命名装备", "&#95A5A6&l为该装备自定义名称"));
            inv.setItem(53, createBtn(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l✔ 保存并获取装备", "&#95A5A6&l将当前装备发放到背包"));

            fillBeautifulGradient(inv, -1, -1);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    private org.bukkit.inventory.meta.trim.TrimMaterial getTrimMaterial(Material material) {
        String key = switch (material) {
            case IRON_INGOT -> "iron";
            case COPPER_INGOT -> "copper";
            case GOLD_INGOT -> "gold";
            case LAPIS_LAZULI -> "lapis";
            case EMERALD -> "emerald";
            case DIAMOND -> "diamond";
            case NETHERITE_INGOT -> "netherite";
            case REDSTONE -> "redstone";
            case AMETHYST_SHARD -> "amethyst";
            case QUARTZ -> "quartz";
            default -> null;
        };
        return key == null ? null : org.bukkit.Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft(key));
    }

    public static final String T_SAVE_PLAYER = "&#11998E&l[S] 确认保存个人Kit修改";
    public static final String T_DEL_PLAYER = "&#ED213A&l[X] 确认永久删除个人Kit";
    public static final String T_SAVE_ADMIN = "&#11998E&l[S] 确认保存玩家Kit修改";
    public static final String T_DEL_ADMIN = "&#ED213A&l[X] 确认永久删除玩家Kit";
    public static final String T_SAVE_PUB = "&#11998E&l[S] 确认保存共享Kit修改";
    public static final String T_DEL_PUB = "&#ED213A&l[X] 确认永久删除共享Kit";
    public static final String T_ABANDON_PUB = "&#ED213A&l[!] ✖ 确认放弃编辑共享Kit";
    public static final String T_ABANDON_PLAYER = "&#ED213A&l[!] ✖ 确认放弃编辑个人Kit";
    public static final String T_ABANDON_ADMIN = "&#ED213A&l[!] ✖ 确认放弃编辑玩家Kit";
    public static final String T_CANCEL_UP = "&#ED213A&l[!] ✖ 确认放弃上传共享Kit";
    public static final String T_DO_UP = "&#11998E&l[?] ▲ 确认发布共享Kit";

    public void openConfirmGui(Player player, String title, String confirmText, String cancelText) {
        player.getScheduler().run(plugin, task -> {
            Inventory inv = Bukkit.createInventory(null, 27, Kitloader.color(title));
            inv.setItem(11, createBtn(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l[✔] " + confirmText, ""));
            inv.setItem(15, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[✖] " + cancelText, ""));
            fillBeautifulGradient(inv, -1, -1);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openConfirmSavePlayerGui(Player player) { openConfirmGui(player, T_SAVE_PLAYER, "保存并返回", "放弃并返回"); }
    public void openConfirmDeletePlayerGui(Player player) { openConfirmGui(player, T_DEL_PLAYER, "确认永久删除", "取消操作并返回"); }
    public void openConfirmSaveAdminGui(Player player) { openConfirmGui(player, T_SAVE_ADMIN, "保存修改并返回", "放弃修改并返回"); }
    public void openConfirmDeleteAdminGui(Player player) { openConfirmGui(player, T_DEL_ADMIN, "确认永久删除", "取消"); }
    public void openConfirmSavePublicGui(Player player) { openConfirmGui(player, T_SAVE_PUB, "保存修改并返回", "返回继续编辑"); }
    public void openConfirmDeletePublicGui(Player player, String id) {
        cachePublicTarget(player.getUniqueId(), id);
        openConfirmGui(player, T_DEL_PUB, "确认永久删除", "取消并返回");
    }
    public void openConfirmAbandonPublicGui(Player player) { openConfirmGui(player, T_ABANDON_PUB, "确认放弃并返回", "继续编辑"); }
    public void openConfirmAbandonPlayerKitGui(Player player) { openConfirmGui(player, T_ABANDON_PLAYER, "确认放弃并返回", "继续编辑"); }
    public void openConfirmAbandonAdminKitGui(Player player) { openConfirmGui(player, T_ABANDON_ADMIN, "确认放弃并返回", "继续编辑"); }
    public void openConfirmPublicCancelGui(Player player) { openConfirmGui(player, T_CANCEL_UP, "确认放弃并退出", "返回继续"); }

    public void openConfirmPublicUploadGui(Player player) {
        player.getScheduler().run(plugin, task -> {
            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());
            Inventory inv = Bukkit.createInventory(null, 27, Kitloader.color(T_DO_UP));
            inv.setItem(11, createBtn(Material.LIME_STAINED_GLASS_PANE, "&#00B09B&l[✔] 确认发布共享", "&#95A5A6&l将当前的背包直接作为Kit上传"));
            inv.setItem(13, createBtn(Material.NAME_TAG, "&#F2C94C&l✎ 重命名此Kit", "&#95A5A6&l当前名称: &f&l" + pData.publicEditSession.name));
            inv.setItem(15, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[✖] 取消并返回", "&#95A5A6&l返回继续编辑"));
            fillBeautifulGradient(inv, -1, -1);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openCategoryGui(Player player, String category, int page) {
        if (plugin.isRestrictedKitloaderPlayer(player)) {
            plugin.sendMsg(player, "restricted_command");
            return;
        }
        long navigationVersion = beginPageNavigation(player);
        player.getScheduler().run(plugin, task -> {
            if (!isLatestPageNavigation(player, navigationVersion)) return;
            ItemStack[] allItems;
            String display;

            if (category.equals("public_kits")) {
                display = "一键Kit";
                allItems = new ItemStack[dataManager.publicKits.size()];
                for (int i=0; i<dataManager.publicKits.size(); i++) allItems[i] = getCategoryItem(category, 0, i);
            } else {
                display = guiConfig.getString("categories_settings." + category + ".display", category);
                List<ItemStack> visibleItems = getVisibleCategoryItems(player, category);
                allItems = visibleItems.toArray(new ItemStack[0]);
                if (category.equals("supply")) cacheSupplyPageView(player, page);
            }

            String title = Kitloader.color(plugin.getGuiTitle("category-prefix", "")
                    + display + " - P" + (page + 1));
            Inventory inv = Bukkit.createInventory(null, 54, title);

            int startIndex = page * 36;
            for (int i = 0; i < 36; i++) {
                if (startIndex + i < allItems.length && allItems[startIndex + i] != null) {
                    ItemStack sourceItem = allItems[startIndex + i];
                    ItemStack displayItem;
                    if (category.equals("supply")) {
                        displayItem = createSupplyDisplayItem(sourceItem,
                                "&#00B09B&l[▶] 左键/Shift &f&l直接获取物品");
                    } else {
                        displayItem = sourceItem.clone();
                    }
                    if (!category.equals("public_kits") && !category.equals("supply")) {
                        ItemMeta meta = displayItem.getItemMeta();
                        if (meta != null) {
                            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                            lore.add(Kitloader.color("&7"));
                            if (isEnchantable(displayItem)) {
                                lore.add(Kitloader.color("&#00B09B&l[▶] 左/右键 &f&l自定义附魔与编辑"));
                                lore.add(Kitloader.color("&#F2C94C&l[⇧] Shift 点击 &f&l直接获取物品"));
                            } else {
                                lore.add(Kitloader.color("&#00B09B&l[▶] 左键/Shift &f&l直接获取物品"));
                                lore.add(Kitloader.color("&#FF5E62&l[✎] 右键 &f&l重命名物品"));
                            }
                            meta.setLore(lore);
                            displayItem.setItemMeta(meta);
                        }
                    }
                    inv.setItem(i, displayItem);
                }
            }

            if (page > 0) inv.setItem(45, createBtn(Material.ARROW, plugin.getGuiTitle("btn-prev-page", ""), ""));
            if (allItems.length > (page + 1) * 36) inv.setItem(53, createBtn(Material.ARROW, plugin.getGuiTitle("btn-next-page", ""), ""));

            if (category.equals("supply")) {
                boolean supplyUploadEnabled = plugin.getConfig().getBoolean("settings.custom-supply.enabled", true);
                inv.setItem(38, createBtn(supplyUploadEnabled ? Material.CHEST : Material.BARRIER,
                        supplyUploadEnabled ? "&#F2C94C&l[+] 自定义上传补给" : "&#FF5E62&l[X] 补给上传已关闭",
                        supplyUploadEnabled ? "&#95A5A6&l点击编辑并上传自定义补给" : "&#95A5A6&l管理员当前禁止玩家上传补给"));
                inv.setItem(40, createBtn(Material.ENDER_CHEST, "&#D32F2F&l[>] 快捷末影箱 (直存模式)", "&#95A5A6&l点击切换为末影箱直存模式\n&#95A5A6&l配置超过9格时自动使用大界面\n&#95A5A6&l可将补给盒直接存入其中"));
                inv.setItem(42, createBtn(Material.CHEST, "&#F2C94C&l[=] 查看已上传补给", "&#95A5A6&l管理自己上传的补给"));
            } else if (category.equals("public_kits")) {
                boolean kitUploadEnabled = plugin.getConfig().getBoolean("settings.public-kits.upload-enabled", true);
                inv.setItem(39, createBtn(kitUploadEnabled ? Material.CHEST : Material.BARRIER,
                        kitUploadEnabled ? "&#F2C94C&l[+] 上传我的Kit" : "&#FF5E62&l[X] Kit上传已关闭",
                        kitUploadEnabled ? "&#95A5A6&l上传当前满载背包作为共享Kit" : "&#95A5A6&l管理员当前禁止玩家上传Kit"));
                inv.setItem(41, createBtn(Material.CHEST, "&#F2C94C&l[=] 我上传的Kit", "&#95A5A6&l管理、编辑、重命名或删除"));
            }

            for (int i = 36; i <= 44; i++) {
                if (category.equals("supply") && (i == 38 || i == 40 || i == 42)) continue;
                if (category.equals("public_kits") && (i == 39 || i == 41)) continue;
                inv.setItem(i, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[X] 物品销毁区", "&#95A5A6&l放置于此处的物品将被销毁"));
            }

            fillBeautifulGradient(inv, 0, 35);
            if (!isLatestPageNavigation(player, navigationVersion)) return;
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openEditGui(Player player, String category, int page) {
        long navigationVersion = beginPageNavigation(player);
        player.getScheduler().run(plugin, task -> {
            if (!isLatestPageNavigation(player, navigationVersion)) return;
            String title = plugin.getGuiTitle("edit-prefix", "") + category + " - P" + (page + 1);
            Inventory inv = Bukkit.createInventory(null, 54, title);
            List<?> itemsRaw = guiConfig.getList("categories." + category);
            if (itemsRaw != null) {
                int startIndex = page * 36;
                for (int i = 0; i < 36; i++) {
                    if (startIndex + i < itemsRaw.size() && itemsRaw.get(startIndex + i) != null) inv.setItem(i, ((ItemStack) itemsRaw.get(startIndex + i)).clone());
                }
            }
            if (page > 0) inv.setItem(45, createBtn(Material.ARROW, plugin.getGuiTitle("btn-prev-page", ""), ""));
            inv.setItem(53, createBtn(Material.ARROW, plugin.getGuiTitle("btn-next-page", ""), ""));

            fillBeautifulGradient(inv, 0, 35);
            if (!isLatestPageNavigation(player, navigationVersion)) return;
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void saveCategoryItems(String category, int page, ItemStack[] pageContents) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            synchronized (uploadedSupplyLock) {
                List<ItemStack> allItems = new ArrayList<>();
                List<?> rawList = guiConfig.getList("categories." + category);
                if (rawList != null) for (Object obj : rawList) allItems.add(obj instanceof ItemStack ? ((ItemStack) obj).clone() : null);

                int startIndex = page * 36;
                while (allItems.size() < startIndex + 36) allItems.add(null);
                for (int i = 0; i < 36; i++) allItems.set(startIndex + i, pageContents[i] != null ? pageContents[i].clone() : null);

                List<ItemStack> compacted = new ArrayList<>();
                for (ItemStack item : allItems) if (item != null && !item.getType().isAir()) compacted.add(item);

                guiConfig.set("categories." + category, compacted);
                try { guiConfig.save(guiFile); loadGuiConfig(); } catch (IOException ignored) {}
            }
        });
    }

    public void openPlayerKitListGui(Player player, List<String> kitNames) {
        player.getScheduler().run(plugin, task -> {
            String title = plugin.getGuiTitle("kit-list-title", "");
            Inventory inv = Bukkit.createInventory(null, 54, title);

            boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);

            String lorePath = isRestricted ? "kit-item-lore-restricted" : "kit-item-lore";
            List<String> translatedLore = new ArrayList<>();
            for(String l : plugin.getGuiStringList(lorePath)) translatedLore.add(Kitloader.color(l));

            List<String> sortedKits = new ArrayList<>(kitNames);
            sortedKits.sort(String::compareTo);

            List<Integer> slots = getCenteredSlots(1, 3, sortedKits.size(), 7);
            for (int i = 0; i < sortedKits.size() && i < slots.size(); i++) {
                String kitName = sortedKits.get(i);
                ItemStack item = new ItemStack(Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Kitloader.color("&#F2C94C&l" + kitName));
                    meta.setLore(translatedLore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slots.get(i), item);
            }

            fillBeautifulGradient(inv, -1, -1);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openKitEditGui(Player player, String kitName) { openKitEditGui(player, kitName, false); }

    public void openKitEditGui(Player player, String kitName, boolean fromCache) {
        player.getScheduler().run(plugin, task -> {
            cachePlayerTarget(player.getUniqueId(), kitName);
            String titlePrefix = plugin.getGuiTitle("kit-edit-prefix", "");
            Inventory inv = Bukkit.createInventory(null, 54, titlePrefix + kitName);
            DataManager.PlayerData pData = dataManager.getPlayerData(player.getUniqueId());

            ItemStack[] bkKit = null;
            if (fromCache && playerKitCache.containsKey(player.getUniqueId())) {
                bkKit = playerKitCache.get(player.getUniqueId());
            } else if (pData != null && pData.kits.containsKey(kitName)) {
                bkKit = pData.kits.get(kitName);
            }

            if (bkKit != null) {
                for (int i = 9; i <= 35; i++) if(i < bkKit.length && bkKit[i] != null) inv.setItem(i - 9, bkKit[i].clone());
                for (int i = 0; i <= 8; i++)  if(i < bkKit.length && bkKit[i] != null) inv.setItem(i + 27, bkKit[i].clone());
                if (bkKit.length >= 41) {
                    if (bkKit[39] != null) inv.setItem(36, bkKit[39].clone());
                    if (bkKit[38] != null) inv.setItem(37, bkKit[38].clone());
                    if (bkKit[37] != null) inv.setItem(38, bkKit[37].clone());
                    if (bkKit[36] != null) inv.setItem(39, bkKit[36].clone());
                    if (bkKit[40] != null) inv.setItem(40, bkKit[40].clone());
                }
            }

            boolean isRestricted = plugin.isRestrictedKitloaderPlayer(player);

            if (isRestricted) {
                inv.setItem(45, createBtn(Material.ARROW, "&#00B09B&l◀ 返回列表", "&#95A5A6&l当前世界为只读模式"));
                inv.setItem(49, createBtn(Material.GRAY_DYE, "&#95A5A6&l[-] 无法重命名", "&#95A5A6&l当前世界禁止编辑操作"));
                inv.setItem(53, createBtn(Material.GRAY_DYE, "&#95A5A6&l[✖] 无法删除", "&#95A5A6&l当前世界禁止删除操作"));
            } else {
                inv.setItem(45, createBtn(Material.ARROW, "&#00B09B&l◀ 返回", "&#95A5A6&l点击返回上级菜单 (若有变动将询问保存)"));
                inv.setItem(49, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 重命名该Kit", "&#95A5A6&l点击为该Kit自定义新名称"));
                inv.setItem(53, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[X] 删除此Kit", "&#95A5A6&l点击进入彻底删除二次确认"));
            }

            fillBeautifulGradient(inv, 0, 40);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openOtherPlayerKitListGui(Player player, String targetName, List<String> kitNames) {
        player.getScheduler().run(plugin, task -> {
            String title = Kitloader.color("&#FF0099&l管&#FF11A2&l理&#FF22AB&l玩&#FF33B4&l家&#FF44BD&lK&#FF55C6&li&#FF55C6&lt: &f&l" + targetName);
            Inventory inv = Bukkit.createInventory(null, 54, title);

            List<String> rawLore = plugin.getGuiStringList("kit-item-lore");
            List<String> translatedLore = new ArrayList<>();
            for(String l : rawLore) translatedLore.add(Kitloader.color(l));
            translatedLore.add(Kitloader.color("&#95A5A6"));
            translatedLore.add(Kitloader.color("&#FF5E62&l[!] &f&l这是其他玩家的Kit"));
            translatedLore.add(Kitloader.color("&#00B09B&l[▶] 左键 &f&l直接覆盖加载到你的背包"));

            List<String> sortedKits = new ArrayList<>(kitNames);
            sortedKits.sort(String::compareTo);

            List<Integer> slots = getCenteredSlots(1, 3, sortedKits.size(), 7);
            for (int i = 0; i < sortedKits.size() && i < slots.size(); i++) {
                String kitName = sortedKits.get(i);
                ItemStack item = new ItemStack(Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Kitloader.color("&#F2C94C&l" + kitName));
                    meta.setLore(translatedLore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slots.get(i), item);
            }

            fillBeautifulGradient(inv, -1, -1);
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openOtherPlayerKitEditGui(Player admin, String targetName, String kitName) { openOtherPlayerKitEditGui(admin, targetName, kitName, false); }

    public void openOtherPlayerKitEditGui(Player admin, String targetName, String kitName, boolean fromCache) {
        admin.getScheduler().run(plugin, task -> {
            cacheAdminTarget(admin.getUniqueId(), targetName, kitName);
            String title = Kitloader.color("&#FF0099&l管&#FF0E9B&l理&#FF1C9D&l他&#FF2A9F&l人&#FF38A1&lK&#FF46A3&li&#FF54A5&lt&#FF62A7&l: &f&l" + targetName + " &#808080&l- &e&l" + kitName);
            Inventory inv = Bukkit.createInventory(null, 54, title);

            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
            DataManager.PlayerData tData = dataManager.getOfflinePlayerData(target.getUniqueId());

            ItemStack[] bkKit = null;
            if (fromCache && adminKitCache.containsKey(admin.getUniqueId())) {
                bkKit = adminKitCache.get(admin.getUniqueId());
            } else if (tData != null && tData.kits.containsKey(kitName)) {
                bkKit = tData.kits.get(kitName);
            }

            if (bkKit != null) {
                for (int i = 9; i <= 35; i++) if(i < bkKit.length && bkKit[i] != null) inv.setItem(i - 9, bkKit[i].clone());
                for (int i = 0; i <= 8; i++)  if(i < bkKit.length && bkKit[i] != null) inv.setItem(i + 27, bkKit[i].clone());
                if (bkKit.length >= 41) {
                    if (bkKit[39] != null) inv.setItem(36, bkKit[39].clone());
                    if (bkKit[38] != null) inv.setItem(37, bkKit[38].clone());
                    if (bkKit[37] != null) inv.setItem(38, bkKit[37].clone());
                    if (bkKit[36] != null) inv.setItem(39, bkKit[36].clone());
                    if (bkKit[40] != null) inv.setItem(40, bkKit[40].clone());
                }
            }

            inv.setItem(45, createBtn(Material.ARROW, "&#00B09B&l◀ 返回", "&#95A5A6&l保存对该玩家Kit的修改"));
            inv.setItem(49, createBtn(Material.NAME_TAG, "&#00B09B&l✎ 重命名该Kit", "&#95A5A6&l修改该玩家该Kit的名称"));
            inv.setItem(53, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l[X] 确认永久删除", "&#95A5A6&l将该Kit从玩家数据中抹除"));

            fillBeautifulGradient(inv, 0, 40);
            setNavigating(admin);
            admin.openInventory(inv);
            checkAndClearNavigating(admin);
        }, null);
    }

    public void openSupplyEnderChestGui(Player player, int page) {
        long navigationVersion = beginPageNavigation(player);
        player.getScheduler().run(plugin, task -> {
            if (!isLatestPageNavigation(player, navigationVersion)) return;
            String title = Kitloader.color("&#D32F2F&l末&#D73F46&l影&#DB4548&l箱&#DF4C4A&l直&#E3524C&l存&#E7594F&l模&#EB5F51&l式 &8&l- P" + (page + 1));
            Inventory inv = Bukkit.createInventory(null, 54, title);

            List<ItemStack> visibleItems = getVisibleCategoryItems(player, "supply");
            cacheSupplyPageView(player, page);
            ItemStack[] allItems = visibleItems.toArray(new ItemStack[0]);
            int startIndex = page * 36;
            for (int i = 0; i < 36; i++) {
                if (startIndex + i < allItems.length && allItems[startIndex + i] != null) {
                    ItemStack sourceItem = allItems[startIndex + i];
                    ItemStack displayItem = createSupplyDisplayItem(sourceItem,
                            "&#00B09B&l[▶] 左键/Shift &f&l直接存入下方的末影箱");
                    inv.setItem(i, displayItem);
                }
            }

            if (page > 0) inv.setItem(36, createBtn(Material.ARROW, plugin.getGuiTitle("btn-prev-page", ""), ""));
            inv.setItem(40, createBtn(Material.ARROW, "&#00B09B&l◀ 返回常规模式", "&#95A5A6&l回到常规的补给盒子界面"));
            if (allItems.length > (page + 1) * 36) inv.setItem(44, createBtn(Material.ARROW, plugin.getGuiTitle("btn-next-page", ""), ""));

            for(int i = 36; i <= 44; i++) {
                if(inv.getItem(i) == null || inv.getItem(i).getType().isAir()) {
                    inv.setItem(i, createBtn(Material.BLACK_STAINED_GLASS_PANE, "§7", ""));
                }
            }

            Inventory ec = player.getEnderChest();
            for (int i = 0; i < 9; i++) {
                ItemStack ecItem = ec.getItem(i);
                if (ecItem != null && !ecItem.getType().isAir()) {
                    inv.setItem(45 + i, createUploadedSupplyDeliveryCopy(ecItem));
                }
            }

            fillBeautifulGradient(inv, 0, 35);
            if (!isLatestPageNavigation(player, navigationVersion)) return;
            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    public void openDedicatedEnderChestGui(Player player) {
        player.getScheduler().run(plugin, task -> {
            int uiSlots = plugin.getConfig().getInt("settings.shulker-limits.enderchest-max", 9);
            if (uiSlots <= 9) {
                openSupplyEnderChestGui(player, 0);
                return;
            }

            String title = Kitloader.color("&#D32F2F&l专&#D73F46&l属&#DB4548&l末&#DF4C4A&l影&#E3524C&l箱");
            Inventory inv = Bukkit.createInventory(null, 54, title);

            Inventory ec = player.getEnderChest();
            int maxSlots = Math.min(uiSlots, Math.min(45, ec.getSize()));

            for (int i = 0; i < 45; i++) {
                if (i < maxSlots) {
                    ItemStack ecItem = ec.getItem(i);
                    if (ecItem != null && !ecItem.getType().isAir()) {
                        inv.setItem(i, createUploadedSupplyDeliveryCopy(ecItem));
                    }
                } else {
                    inv.setItem(i, createBtn(Material.RED_STAINED_GLASS_PANE, "&#FF5E62&l✖ 未解锁的格子", "&#95A5A6&l该格子暂未开放\n&#95A5A6&l可通过配置文件进行上限扩展"));
                }
            }

            for (int i = 45; i <= 53; i++) {
                if (i == 49) {
                    inv.setItem(i, createBtn(Material.ARROW, "&#00B09B&l◀ 返回补给盒子", "&#95A5A6&l点击返回常规分类列表"));
                } else {
                    inv.setItem(i, createBtn(Material.BLACK_STAINED_GLASS_PANE, "§7", ""));
                }
            }

            setNavigating(player);
            player.openInventory(inv);
            checkAndClearNavigating(player);
        }, null);
    }

    private static final class UploadedSupplyMetadata {
        private final String id;
        private final String owner;
        private final boolean hidden;

        private UploadedSupplyMetadata(String id, String owner, boolean hidden) {
            this.id = id;
            this.owner = owner;
            this.hidden = hidden;
        }
    }

    private static final class UploadedSupplyRecord {
        private final String id;
        private final UUID owner;
        private boolean hidden;
        private ItemStack item;
        private long uploadTime;

        private UploadedSupplyRecord(String id, UUID owner, boolean hidden, ItemStack item, long uploadTime) {
            this.id = id;
            this.owner = owner;
            this.hidden = hidden;
            this.item = item;
            this.uploadTime = uploadTime;
        }
    }

    private static final class SupplyPageEntry {
        private final String uploadedSupplyId;
        private final ItemStack item;

        private SupplyPageEntry(String uploadedSupplyId, ItemStack item) {
            this.uploadedSupplyId = uploadedSupplyId;
            this.item = item;
        }
    }

    private static final class SupplyPageView {
        private final int page;
        private final SupplyPageEntry[] items;

        private SupplyPageView(int page, SupplyPageEntry[] items) {
            this.page = page;
            this.items = items;
        }
    }
}
