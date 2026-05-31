package us.talabrek.ultimateskyblock.shop;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public class ShopCategory {
    private final String id;
    private final String name;
    private final ItemStack displayItem;
    private final List<ShopItem> items;

    public ShopCategory(String id, String name, ItemStack displayItem, List<ShopItem> items) {
        this.id = id;
        this.name = name;
        this.displayItem = displayItem;
        this.items = items;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ItemStack getDisplayItem() { return displayItem.clone(); }
    public List<ShopItem> getItems() { return items; }
}
