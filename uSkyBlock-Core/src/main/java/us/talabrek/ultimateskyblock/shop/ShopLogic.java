package us.talabrek.ultimateskyblock.shop;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dk.lockfuglsang.minecraft.file.FileUtil;
import dk.lockfuglsang.minecraft.util.ItemStackUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.player.PlayerInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Arrays;

@Singleton
public class ShopLogic {
    private final uSkyBlock plugin;
    private final Logger logger;
    private final FileConfiguration config;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<UUID, PlayerShopData> playerData = new HashMap<>();
    private double priceIncreasePercent;
    private long resetMinutes;

    @Inject
    public ShopLogic(@NotNull uSkyBlock plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = FileUtil.getYmlConfiguration("shop.yml");
        loadConfig();
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
                    double cost = item.getDouble("cost", 10);
                    int maxBuys = item.getInt("max-buys", 64);
                    int requiredLevel = item.getInt("required-level", 0);
                    List<String> requiredChallenges = item.getStringList("required-challenges");

                    // 解析 items 列表（与挑战配置格式一致）
                    List<String> itemRewards = item.getStringList("items");
                    if (itemRewards.isEmpty() && item.getString("items") != null) {
                        itemRewards.addAll(Arrays.asList(item.getString("items").split(" ")));
                    }

                    // 解析 commands 列表
                    List<String> commands = item.getStringList("commands");
                    if (commands.isEmpty() && item.getString("commands") != null) {
                        commands.add(item.getString("commands"));
                    }

                    items.add(new ShopItem(itemId, itemDisplay, cost, maxBuys,
                        requiredLevel, requiredChallenges, itemRewards, commands));                }
            }
            categories.put(catId, new ShopCategory(catId, name, displayItem, items));
        }
    }

    public List<ShopCategory> getCategories() {
        return new ArrayList<>(categories.values());
    }

    public ShopCategory getCategory(String id) {
        return categories.get(id);
    }

    public PlayerShopData getPlayerData(UUID uuid) {
        PlayerShopData data = playerData.get(uuid);
        if (data == null) {
            // 尝试从玩家数据文件加载
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
        // 检查最大购买数量
        if (data.getBuyCount(item.getId()) >= item.getMaxBuys()) {
            return false;
        }
        // 检查岛屿等级（由调用方检查）
        // 检查任务完成（由调用方检查）
        return true;
    }

    public void reload() {
        FileUtil.reload();
        loadConfig();
        logger.info("ShopLogic: shop.yml reloaded. Loaded " + categories.size() + " categories.");
    }
}
