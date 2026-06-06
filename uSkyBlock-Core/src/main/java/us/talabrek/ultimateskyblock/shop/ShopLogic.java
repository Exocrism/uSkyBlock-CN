package us.talabrek.ultimateskyblock.shop;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dk.lockfuglsang.minecraft.file.FileUtil;
import dk.lockfuglsang.minecraft.util.ItemStackUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ShopLogic {
    // 限时商店特殊分类ID
    public static final String RANDOM_SHOP_CATEGORY_ID = "__random_shop__";

    private final uSkyBlock plugin;
    private final Logger logger;
    private final FileConfiguration config;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<UUID, PlayerShopData> playerData = new HashMap<>();
    private double priceIncreasePercent;
    private long resetMinutes;

    // 限时商店：每个玩家独立生成 40 件商品
    private final Map<UUID, List<RandomShopItem>> playerRandomShops = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private long lastRefreshTime = System.currentTimeMillis(); // 上次刷新时间

    @Inject
    public ShopLogic(@NotNull uSkyBlock plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = FileUtil.getYmlConfiguration("shop.yml");
        loadConfig();
        scheduleRandomShopRefresh(); // 启动限时商店定时刷新
    }

    private void loadConfig() {
        categories.clear();
        this.priceIncreasePercent = config.getDouble("price-increase.increase-percent", 25);
        this.resetMinutes = config.getLong("price-increase.reset-minutes", 60);

        ConfigurationSection catSection = config.getConfigurationSection("categories");
        if (catSection == null) return;

        for (String catId : catSection.getKeys(false)) {
            ConfigurationSection cat = catSection.getConfigurationSection(catId);
            if (cat == null) continue;

            String name = cat.getString("name", catId);
            ItemStack displayItem = ItemStackUtil.createItemStack(cat.getString("displayItem", "STONE"));

            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemSection = cat.getConfigurationSection("items");
            if (itemSection != null) {
                for (String itemId : itemSection.getKeys(false)) {
                    ConfigurationSection item = itemSection.getConfigurationSection(itemId);
                    if (item == null) continue;

                    ItemStack itemDisplay = ItemStackUtil.createItemStack(
                        item.getString("displayItem", "STONE"));
                    String displayName = item.getString("displayName");
                    double cost = item.getDouble("cost", 10);
                    int maxBuys = item.getInt("max-buys", 64);
                    int requiredLevel = item.getInt("required-level", 0);
                    List<String> requiredChallenges = item.getStringList("required-challenges");

                    List<String> itemRewards = item.getStringList("items");
                    if (itemRewards.isEmpty() && item.getString("items") != null) {
                        itemRewards.addAll(Arrays.asList(item.getString("items").split(" ")));
                    }

                    List<String> commands = item.getStringList("commands");
                    if (commands.isEmpty() && item.getString("commands") != null) {
                        commands.add(item.getString("commands"));
                    }

                    String bundleName = item.getString("bundle.name");
                    List<String> bundleDesc = item.getStringList("bundle.description");
                    boolean customPricing = item.getBoolean("custom-pricing", false);
                    boolean noPriceIncrease = item.getBoolean("no-price-increase", false);

                    items.add(new ShopItem(itemId, itemDisplay, displayName, cost, maxBuys,
                        requiredLevel, requiredChallenges, itemRewards, commands,
                        bundleName, bundleDesc, customPricing, noPriceIncrease));
                }
            }
            categories.put(catId, new ShopCategory(catId, name, displayItem, items));
        }
    }

    // ==================== 限时商店 ====================

    private void scheduleRandomShopRefresh() {
        // 立即刷新一次
        refreshRandomShop();

        // 计算到下一个 6:00, 12:00, 18:00 的毫秒数
        long current = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // 尝试 6,12,18 点，找到下一个最近的时间
        long delay = Long.MAX_VALUE;
        int[] hours = {0, 6, 12, 18};
        for (int h : hours) {
            cal.set(Calendar.HOUR_OF_DAY, h);
            long time = cal.getTimeInMillis();
            if (time <= current) {
                time += 24 * 60 * 60 * 1000; // 明天
            }
            long d = time - current;
            if (d < delay) {
                delay = d;
            }
        }

        // 使用 uSkyBlock 调度器
        plugin.getScheduler().sync(() -> {
            refreshRandomShop();
            broadcastRefresh();
            // 之后每 6 小时刷新一次
            plugin.getScheduler().sync(() -> {
                refreshRandomShop();
                broadcastRefresh();
            }, Duration.ofHours(6), Duration.ofHours(6));
        }, Duration.ofMillis(delay));
    }

    private void broadcastRefresh() {
        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage("\u00a7b\u9650\u65f6\u5546\u5e97\u5df2\u5237\u65b0\uff01");
            plugin.execCommand(p, "console:tellraw " + p.getName()
                + " [{\"text\":\"\u00a7e\u00a7l\u70b9\u51fb\u6b64\u5904\u6253\u5f00\",\"clickEvent\":{\"action\":\"run_command\","
                + "\"value\":\"/is\"}}]", false);
        }
    }

    public void refreshRandomShop() {
        playerRandomShops.clear();
        lastRefreshTime = System.currentTimeMillis();

        for (PlayerShopData data : playerData.values()) {
            data.resetRandomShopBuys();
        }
        logger.info("限时商店已刷新，所有玩家下次打开时生成新商品");
    }

    /** 玩家打开限时商店时调用，更新最后打开时间 */
    public void markRandomShopOpened(UUID uuid) {
        PlayerShopData data = getPlayerData(uuid);
        data.setLastRandomShopOpenTime(System.currentTimeMillis());
    }

    /** 检查玩家是否在最近一次刷新后还没打开过限时商店 */
    public boolean hasNotOpenedSinceRefresh(UUID uuid) {
        PlayerShopData data = getPlayerData(uuid);
        return data.getLastRandomShopOpenTime() < lastRefreshTime;
    }

    /**
     * 获取某玩家的限时商店列表（懒加载生成）。
     * 条件未满足的商品权重降低，折扣惩罚 +5%（即价格更高）。
     */
    public List<RandomShopItem> getRandomShopItems(org.bukkit.entity.Player player) {
        UUID uuid = player.getUniqueId();
        if (playerRandomShops.containsKey(uuid)) {
            return new ArrayList<>(playerRandomShops.get(uuid));
        }

        // 收集所有非自定义定价的商品
        List<ShopItem> allItems = new ArrayList<>();
        for (ShopCategory cat : categories.values()) {
            for (ShopItem item : cat.getItems()) {
                if (!item.isCustomPricing()) {
                    allItems.add(item);
                }
            }
        }
        if (allItems.isEmpty()) return Collections.emptyList();

        // 检查玩家条件（岛屿等级 + 挑战完成）
        us.talabrek.ultimateskyblock.player.PlayerInfo pi = plugin.getPlayerInfo(player);
        us.talabrek.ultimateskyblock.island.IslandInfo islandInfo = plugin.getIslandInfo(player);
        int playerLevel = islandInfo != null ? (int) islandInfo.getLevel() : 0;

        // 加权抽取 40 件
        List<RandomShopItem> result = new ArrayList<>();
        int[] weights = new int[allItems.size()];
        int totalWeight = 0;
        for (int i = 0; i < allItems.size(); i++) {
            ShopItem item = allItems.get(i);
            int rarity = RandomShopItem.calcRarity(item.getBaseCost());
            int w = RandomShopItem.getRarityWeight(rarity);

            boolean unmet = false;
            if (item.getRequiredLevel() > 0 && playerLevel < item.getRequiredLevel()) unmet = true;
            if (!unmet && !item.getRequiredChallenges().isEmpty()) {
                for (String chId : item.getRequiredChallenges()) {
                    if (pi == null || pi.checkChallenge(chId) == 0) {
                        unmet = true;
                        break;
                    }
                }
            }
            if (unmet) {
                w = Math.max(1, w - 3);
            }
            weights[i] = w;
            totalWeight += w;
        }

        int targetCount = Math.min(40, allItems.size());
        boolean[] picked = new boolean[allItems.size()];
        while (result.size() < targetCount) {
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            for (int i = 0; i < allItems.size(); i++) {
                if (picked[i]) continue;
                cumulative += weights[i];
                if (roll < cumulative) {
                    ShopItem item = allItems.get(i);
                    double penalty = 0;
                    boolean unmet = false;
                    if (item.getRequiredLevel() > 0 && playerLevel < item.getRequiredLevel()) unmet = true;
                    if (!unmet && !item.getRequiredChallenges().isEmpty()) {
                        for (String chId : item.getRequiredChallenges()) {
                            if (pi == null || pi.checkChallenge(chId) == 0) {
                                unmet = true;
                                break;
                            }
                        }
                    }
                    if (unmet) penalty = 0.05;
                    result.add(new RandomShopItem(allItems.get(i), penalty));
                    picked[i] = true;
                    totalWeight -= weights[i];
                    break;
                }
            }
        }

        result.sort((a, b) ->
            Double.compare(a.getOriginalItem().getBaseCost(), b.getOriginalItem().getBaseCost()));

        playerRandomShops.put(uuid, result);
        return new ArrayList<>(result);
    }

    @Deprecated
    public List<RandomShopItem> getRandomShopItems() {
        return Collections.emptyList();
    }

    public ShopCategory getRandomShopCategory() {
        return new ShopCategory(
            RANDOM_SHOP_CATEGORY_ID,
            "\u00a7d" + tr("限时物品商店"),
            new ItemStack(Material.EMERALD),
            Collections.emptyList()
        );
    }

    // ==================== 覆盖 getCategories，插入限时商店 ====================

    public List<ShopCategory> getCategories() {
        List<ShopCategory> cats = new ArrayList<>();
        cats.add(getRandomShopCategory());
        cats.addAll(categories.values());
        return cats;
    }

    // ==================== 原有方法 ====================

    public ShopCategory getCategory(String id) {
        if (RANDOM_SHOP_CATEGORY_ID.equals(id)) {
            return getRandomShopCategory();
        }
        return categories.get(id);
    }

    public PlayerShopData getPlayerData(UUID uuid) {
        PlayerShopData data = playerData.get(uuid);
        if (data == null) {
            PlayerInfo pi = plugin.getPlayerInfo(uuid);
            if (pi != null) {
                data = PlayerShopData.load(pi.getConfig().getConfigurationSection("shop"));
            } else {
                data = new PlayerShopData();
            }
            playerData.put(uuid, data);
        }
        data.resetIfNeeded(resetMinutes);
        return data;
    }

    public void savePlayerData(UUID uuid) {
        PlayerShopData data = playerData.get(uuid);
        if (data != null) {
            PlayerInfo pi = plugin.getPlayerInfo(uuid);
            if (pi != null) {
                data.save(pi.getConfig().createSection("shop"));
                pi.save();
            }
        }
    }

    public double getCurrentPrice(ShopItem item, UUID playerUuid) {
        if (item.isNoPriceIncrease()) {
            return item.getBaseCost();
        }
        PlayerShopData data = getPlayerData(playerUuid);
        int buyCount = data.getBuyCount(item.getId());
        double price = item.getBaseCost();
        for (int i = 0; i < buyCount; i++) {
            price *= (1 + priceIncreasePercent / 100.0);
        }
        return price;
    }

    public int getActualPrice(ShopItem item, UUID playerUuid) {
        return (int) Math.floor(getCurrentPrice(item, playerUuid));
    }

    public boolean canBuy(ShopItem item, UUID playerUuid) {
        PlayerShopData data = getPlayerData(playerUuid);
        return data.getBuyCount(item.getId()) < item.getMaxBuys();
    }

    public void reload() {
        FileUtil.reload();
        loadConfig();
        logger.info("ShopLogic: shop.yml reloaded. Loaded " + categories.size() + " categories.");
    }
}
