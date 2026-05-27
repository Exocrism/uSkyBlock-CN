package com.yourpackage;

import org.bukkit.plugin.java.JavaPlugin;
import us.talabrek.ultimateskyblock.api.UltimateSkyblock;
import us.talabrek.ultimateskyblock.api.UltimateSkyblockProvider;

public class YourPlugin extends JavaPlugin {

    private UltimateSkyblock api;

    @Override
    public void onEnable() {
        try {
            api = UltimateSkyblockProvider.getInstance();
            getLogger().info("Successfully connected to uSkyBlock API!");
        } catch (Exception e) {
            getLogger().severe("Failed to get API: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    public UltimateSkyblock getAPI() {
        return api;
    }
}
