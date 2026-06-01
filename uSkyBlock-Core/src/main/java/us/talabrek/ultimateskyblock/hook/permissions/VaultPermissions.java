package us.talabrek.ultimateskyblock.hook.permissions;

import net.milkbowl.vault2.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Optional;

public class VaultPermissions extends PermissionsHook implements Listener {
    private Permission permission;

    public VaultPermissions(@NotNull uSkyBlock plugin) {
        super(plugin, "Vault");
        setupPermission().ifPresent(vaultPlugin -> this.permission = vaultPlugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private Optional<Permission> setupPermission() {
        RegisteredServiceProvider<Permission> rsp =
            plugin.getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            permission = rsp.getProvider();
            plugin.getLogger().info("Using " + permission.getName() + " as permission provider.");
            return Optional.of(permission);
        }
        return Optional.empty();
    }

    @Override
    public boolean addPermission(@NotNull Player player, @NotNull String perk) {
        if (permission == null) {
            setupPermission().ifPresent(p -> this.permission = p);
        }
        if (permission != null) {
            return permission.playerAdd(player, perk);
        }
        // 兜底方案：直接用控制台命令添加权限
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
            "lp user " + player.getName() + " permission set " + perk + " true");
        return true;
    }

    @Override
    public boolean removePermission(@NotNull Player player, @NotNull String perk) {
        if (permission == null) {
            setupPermission().ifPresent(p -> this.permission = p);
        }
        if (permission != null) {
            return permission.playerRemove(player, perk);
        }
        // 兜底方案：直接用控制台命令移除权限
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
            "lp user " + player.getName() + " permission unset " + perk);
        return true;
    }

    @EventHandler
    public void onPermissionRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getProvider() instanceof Permission) {
            setupPermission().ifPresent(vaultPlugin -> this.permission = vaultPlugin);
        }
    }

    @EventHandler
    public void onPermissionUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getProvider() instanceof Permission) {
            this.permission = null;
            setupPermission().ifPresent(vaultPlugin -> this.permission = vaultPlugin);
        }
    }
}
