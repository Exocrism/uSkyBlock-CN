package us.talabrek.ultimateskyblock.command.island;

import com.google.inject.Inject;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Map;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

public class BuyHopperLimitCommand extends RequireIslandCommand {

    @Inject
    public BuyHopperLimitCommand(uSkyBlock plugin) {
        super(plugin, "hopper", "usb.island.create", "?oper", marktr("Pay to add hopper limits."));
    }

    private int calcPrice(int curlimit) {
        return curlimit * 5;
    }

    @Override
    protected boolean doExecute(String alias, Player player, PlayerInfo pi, IslandInfo island, Map<String, Object> data, String... args) {
        us.talabrek.ultimateskyblock.api.IslandInfo islandInfo = uSkyBlock.getInstance().getIslandInfo(player);
        int curlimit = islandInfo.getHopperLimit();
        if (args.length > 0 && args[0].equals("buy")) {
            int price = calcPrice(curlimit);
            uSkyBlock.getInstance().getHookManager().getEconomyHook().ifPresent((hook) -> {
                boolean success;
                String result = hook.withdrawPlayer(player, price);
                success = result == null;
                if (success) {
                    islandInfo.setHopperLimit(curlimit + 1);
                    player.sendMessage("\u00a7a\u00a7l" + tr("Buy Extra Hopper Success!"));
                    player.sendMessage("\u00a77" + tr("======================"));
                } else {
                    player.sendMessage("\u00a7c\u00a7l" + tr("Buy Extra Hopper Failed!"));
                    if (hook.getBalance(player) < price) {
                        player.sendMessage("\u00a7d" + tr("You do not have enough money!"));
                        player.sendMessage("\u00a7d" + tr("You have {0}", hook.getBalance(player)));
                    }
                    player.sendMessage("\u00a7c" + tr("{0}", result));
                }
            });
        } else {
            player.sendMessage("\u00a7b\u00a7l" + tr("Buy Extra Hopper Limit"));
            player.sendMessage("\u00a77" + tr("======================"));
        }
        player.sendMessage("\u00a7b" + tr("Current Extra Limit is {0}", islandInfo.getHopperLimit()));
        player.sendMessage("\u00a7b" + tr("Price to buy another hopper limit is {0}", calcPrice(islandInfo.getHopperLimit())));
        plugin.execCommand(player, "console:tellraw " + player.getName() + " [{\"text\":\"" + tr("click to buy hopper") + "\",\"color\":\"green\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/is hopper buy\"}}]", false);
        return true;
    }
}
