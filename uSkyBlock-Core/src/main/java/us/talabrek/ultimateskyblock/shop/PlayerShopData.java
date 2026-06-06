package us.talabrek.ultimateskyblock.shop;

import org.bukkit.configuration.ConfigurationSection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class PlayerShopData {
    private final Map<String, Integer> buyCounts = new HashMap<>();
    private final Map<String, Long> lastBuyTime = new HashMap<>();
    private final Map<String, Integer> randomShopBuys = new HashMap<>();
    private long lastResetTime = Instant.now().getEpochSecond();
    private long lastRandomShopOpenTime = 0; // 最后打开限时商店的时间戳

    public int getBuyCount(String itemId) {
        return buyCounts.getOrDefault(itemId, 0);
    }

    public void incrementBuyCount(String itemId) {
        buyCounts.put(itemId, getBuyCount(itemId) + 1);
        lastBuyTime.put(itemId, Instant.now().getEpochSecond());
    }

    public long getLastBuyTime(String itemId) {
        return lastBuyTime.getOrDefault(itemId, Instant.EPOCH.getEpochSecond());
    }

    public long getLastResetTime() {
        return lastResetTime;
    }

    public void resetIfNeeded(long resetMinutes) {
        if (Instant.now().isAfter(Instant.ofEpochSecond(lastResetTime).plusSeconds(resetMinutes * 60))) {
            buyCounts.clear();
            lastBuyTime.clear();
            lastResetTime = Instant.now().getEpochSecond();
        }
    }

    // === 序列化到 YAML ===
    public void save(ConfigurationSection section) {
        section.set("lastResetTime", lastResetTime);
        for (Map.Entry<String, Integer> entry : buyCounts.entrySet()) {
            section.set("buyCounts." + entry.getKey(), entry.getValue());
        }
    }

    // === 从 YAML 加载 ===
    public static PlayerShopData load(ConfigurationSection section) {
        PlayerShopData data = new PlayerShopData();
        if (section != null) {
            data.lastResetTime = section.getLong("lastResetTime", Instant.now().getEpochSecond());
            ConfigurationSection counts = section.getConfigurationSection("buyCounts");
            if (counts != null) {
                for (String key : counts.getKeys(false)) {
                    data.buyCounts.put(key, counts.getInt(key));
                }
            }
        }
        return data;
    }

    public int getRandomShopBuyCount(String itemId) {
    return randomShopBuys.getOrDefault(itemId, 0);
    }
    public void incrementRandomShopBuyCount(String itemId) {
        randomShopBuys.put(itemId, getRandomShopBuyCount(itemId) + 1);
    }
    public void resetRandomShopBuys() {
        randomShopBuys.clear();
    }

    public long getLastRandomShopOpenTime() { return lastRandomShopOpenTime; }
    public void setLastRandomShopOpenTime(long time) { this.lastRandomShopOpenTime = time; }

}
