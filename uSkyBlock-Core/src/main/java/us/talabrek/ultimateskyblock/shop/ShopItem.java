package us.talabrek.ultimateskyblock.shop;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.ArrayList;

public class ShopItem {
    private final String id;
    private final ItemStack displayItem;
    private final String displayName;
    private final double baseCost;
    private final int maxBuys;
    private final int requiredLevel;
    private final List<String> requiredChallenges;
    private final List<String> itemRewards;
    private final List<String> commands;
    private final String bundleName;
    private final List<String> bundleDescription;
    private final boolean customPricing;
    private final boolean noPriceIncrease;


    public ShopItem(String id, ItemStack displayItem, String displayName, double baseCost, int maxBuys,
                    int requiredLevel, List<String> requiredChallenges,
                    List<String> itemRewards, List<String> commands,
                    String bundleName, List<String> bundleDescription,
                    boolean customPricing, boolean noPriceIncrease) {
        this.id = id;
        this.displayItem = displayItem;
        this.displayName = displayName;
        this.baseCost = baseCost;
        this.maxBuys = maxBuys;
        this.requiredLevel = requiredLevel;
        this.requiredChallenges = requiredChallenges;
        this.itemRewards = itemRewards != null ? itemRewards : new ArrayList<>();
        this.commands = commands != null ? commands : new ArrayList<>();
        this.bundleName = bundleName;
        this.bundleDescription = bundleDescription != null ? bundleDescription : new ArrayList<>();
        this.customPricing = customPricing;
        this.noPriceIncrease = noPriceIncrease;
    }

    public String getId() { return id; }
    public ItemStack getDisplayItem() { return displayItem.clone(); }
    public String getDisplayName() { return displayName; }
    public double getBaseCost() { return baseCost; }
    public int getMaxBuys() { return maxBuys; }
    public int getRequiredLevel() { return requiredLevel; }
    public List<String> getRequiredChallenges() { return requiredChallenges; }
    public List<String> getItemRewards() { return itemRewards; }
    public List<String> getCommands() { return commands; }
    public String getBundleName() { return bundleName; }
    public List<String> getBundleDescription() { return bundleDescription; }
    public boolean isCustomPricing() { return customPricing; }
    public boolean isNoPriceIncrease() { return noPriceIncrease; }
}
