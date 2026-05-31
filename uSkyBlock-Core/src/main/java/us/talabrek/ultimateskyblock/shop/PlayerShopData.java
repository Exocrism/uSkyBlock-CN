package us.talabrek.ultimateskyblock.shop;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerShopData {
    private final Map<String, Integer> buyCounts = new HashMap<>();
    private final Map<String, Instant> lastBuyTime = new HashMap<>();
    private Instant lastResetTime = Instant.now();

    public int getBuyCount(String itemId) {
        return buyCounts.getOrDefault(itemId, 0);
    }

    public void incrementBuyCount(String itemId) {
        buyCounts.put(itemId, getBuyCount(itemId) + 1);
        lastBuyTime.put(itemId, Instant.now());
    }

    public Instant getLastBuyTime(String itemId) {
        return lastBuyTime.getOrDefault(itemId, Instant.EPOCH);
    }

    public Instant getLastResetTime() {
        return lastResetTime;
    }

    public void resetIfNeeded(long resetMinutes) {
        if (Instant.now().isAfter(lastResetTime.plusSeconds(resetMinutes * 60))) {
            buyCounts.clear();
            lastBuyTime.clear();
            lastResetTime = Instant.now();
        }
    }
}
