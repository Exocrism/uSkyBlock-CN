package us.talabrek.ultimateskyblock.shop;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.ArrayList;

public class ShopItem {
    private final String id;
    private final ItemStack displayItem;
    private final double baseCost;
    private final int maxBuys;
    private final int requiredLevel;
    private final List<String> requiredChallenges;
    private final List<String> itemRewards;
    private final List<String> commands;

    public ShopItem(String id, ItemStack displayItem, double baseCost, int maxBuys,
                    int requiredLevel, List<String> requiredChallenges,
                    List<String> itemRewards, List<String> commands) {
        this.id = id;
        this.displayItem = displayItem;
        this.baseCost = baseCost;
        this.maxBuys = maxBuys;
        this.requiredLevel = requiredLevel;
        this.requiredChallenges = requiredChallenges;
        this.itemRewards = itemRewards != null ? itemRewards : new ArrayList<>();
        this.commands = commands != null ? commands : new ArrayList<>();
    }

    public String getId() { return id; }
    public ItemStack getDisplayItem() { return displayItem.clone(); }
    public double getBaseCost() { return baseCost; }
    public int getMaxBuys() { return maxBuys; }
    public int getRequiredLevel() { return requiredLevel; }
    public List<String> getRequiredChallenges() { return requiredChallenges; }
    public List<String> getItemRewards() { return itemRewards; }
    public List<String> getCommands() { return commands; }
}
