package us.talabrek.ultimateskyblock.menu;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dk.lockfuglsang.minecraft.util.ItemStackUtil;
import dk.lockfuglsang.minecraft.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.PluginConfig;
import us.talabrek.ultimateskyblock.challenge.Challenge;
import us.talabrek.ultimateskyblock.challenge.ChallengeLogic;
import us.talabrek.ultimateskyblock.handler.ConfirmHandler;
import us.talabrek.ultimateskyblock.island.IslandGenerator;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.island.LimitLogic;
import us.talabrek.ultimateskyblock.player.IslandPerk;
import us.talabrek.ultimateskyblock.player.PerkLogic;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.player.UltimateHolder;
import us.talabrek.ultimateskyblock.player.UltimateHolder.MenuType;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.GuiItemUtil;
import us.talabrek.ultimateskyblock.util.Scheduler;
import us.talabrek.ultimateskyblock.shop.ShopCategory;
import us.talabrek.ultimateskyblock.shop.ShopItem;
import us.talabrek.ultimateskyblock.shop.PlayerShopData;
import us.talabrek.ultimateskyblock.shop.ShopLogic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dk.lockfuglsang.minecraft.po.I18nUtil.pre;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;
import static dk.lockfuglsang.minecraft.util.FormatUtil.stripFormatting;
import static java.util.Objects.requireNonNull;
import static us.talabrek.ultimateskyblock.challenge.ChallengeLogic.CHALLENGE_PAGESIZE;
import static us.talabrek.ultimateskyblock.challenge.ChallengeLogic.COLS_PER_ROW;
import static us.talabrek.ultimateskyblock.util.LogUtil.log;

// TODO: Move all the texts to resource-files (translatable).

/**
 * The UI menu of uSkyBlock (using the inventory UI).
 */
@Singleton
public class SkyBlockMenu {
    private final Pattern PERM_VALUE_PATTERN = Pattern.compile("(\\[(?<perm>(?<not>[!])?[^\\]]+)\\])?(?<value>.*)");
    private final Pattern CHALLENGE_PAGE_HEADER = Pattern.compile(tr("Challenge Menu") + ".*\\((?<p>[0-9]+)/(?<max>[0-9]+)\\)");
    private final Pattern SHOP_PAGE_HEADER = Pattern.compile(stripFormatting(tr("商店")) + ".*\\((?<p>[0-9]+)/(?<max>[0-9]+)\\)");

    private final uSkyBlock plugin;
    private final ChallengeLogic challengeLogic;
    private final PluginConfig config;
    private final PerkLogic perkLogic;
    private final IslandGenerator islandGenerator;
    private final LimitLogic limitLogic;
    private final ConfirmHandler confirmHandler;
    private final Scheduler scheduler;

    private final ItemStack sign = new ItemStack(Material.OAK_SIGN, 1);
    private final ItemStack biome = new ItemStack(Material.JUNGLE_SAPLING, 1);
    private final ItemStack lock = new ItemStack(Material.IRON_BARS, 1);
    private final ItemStack warpset = new ItemStack(Material.END_PORTAL_FRAME, 1);
    private final ItemStack warptoggle = new ItemStack(Material.LEVER, 1);
    private final ItemStack invite = new ItemStack(Material.CARROT_ON_A_STICK, 1);
    private final ItemStack kick = new ItemStack(Material.LEATHER_BOOTS, 1);
    private final List<PartyPermissionMenuItem> permissionMenuItems = Arrays.asList(
        new PartyPermissionMenuItem(biome, "canChangeBiome", tr("Change Biome"),
            tr("change the island''s biome.")),
        new PartyPermissionMenuItem(lock, "canToggleLock", tr("Toggle Island Lock"),
            tr("toggle the island''s lock.")),
        new PartyPermissionMenuItem(warpset, "canChangeWarp", tr("Set Island Warp"),
            tr("set the island''s warp."),
            tr("set the island''s warp,\nwhich allows non-group\nmembers to teleport to\nthe island.")),
        new PartyPermissionMenuItem(warptoggle, "canToggleWarp", tr("Toggle Island Warp"),
            tr("toggle the island''s warp."),
            tr("toggle the island''s warp,\nallowing them to turn it\non or off at anytime, but\nnot set the location.")),
        new PartyPermissionMenuItem(invite, "canInviteOthers", tr("Invite Players"),
            tr("invite others to the island."),
            tr("""
                invite
                other players to the island if
                there is enough room for more
                members""")),
        new PartyPermissionMenuItem(kick, "canKickOthers", tr("Kick Players"),
            tr("kick others from the island."),
            tr("""
                kick
                other players from the island,
                but they are unable to kick
                the island leader."""))
    );

    @Inject
    public SkyBlockMenu(
        @NotNull uSkyBlock plugin,
        @NotNull ChallengeLogic challengeLogic,
        @NotNull PluginConfig config,
        @NotNull PerkLogic perkLogic,
        @NotNull IslandGenerator islandGenerator,
        @NotNull LimitLogic limitLogic,
        @NotNull ConfirmHandler confirmHandler,
        @NotNull Scheduler scheduler
    ) {
        this.plugin = plugin;
        this.challengeLogic = challengeLogic;
        this.config = config;
        this.perkLogic = perkLogic;
        this.islandGenerator = islandGenerator;
        this.limitLogic = limitLogic;
        this.confirmHandler = confirmHandler;
        this.scheduler = scheduler;
    }

    // ==================== 修复 2：displayPartyPlayerGUI ====================

    public Inventory displayPartyPlayerGUI(final Player inventoryViewer, final PlayerProfile partyMember) {
        if (partyMember == null || partyMember.getName() == null || partyMember.getUniqueId() == null) {
            plugin.getLogger().warning("Invalid PlayerProfile passed to displayPartyPlayerGUI, returning to party menu.");
            return displayPartyGUI(inventoryViewer);
        }
        String name = partyMember.getName();
        List<String> lores = new ArrayList<>();
        String emptyTitle = tr("{0} <{1}>", "", tr("Permissions"));
        String title = tr("{0} <{1}>", name.substring(0, Math.min(32 - emptyTitle.length(), name.length())), tr("Permissions"));
        Inventory menu = Bukkit.createInventory(new UltimateHolder(inventoryViewer, title, MenuType.DEFAULT), 9, title);
        final ItemStack pHead = new ItemStack(Material.PLAYER_HEAD, 1);
        final SkullMeta meta3 = requireNonNull((SkullMeta) requireNonNull(pHead.getItemMeta()));
        ItemMeta meta2 = requireNonNull(requireNonNull(sign.getItemMeta()));
        meta2.setDisplayName(tr("\u00a79Player Permissions"));
        addLore(lores, tr("\u00a7eClick here to return to\n\u00a7eyour island group''s info."));
        meta2.setLore(lores);
        sign.setItemMeta(meta2);
        menu.addItem(sign);
        lores.clear();
        meta3.setOwnerProfile(partyMember);
        meta3.setDisplayName(tr("\u00a7e{0}''\u00a79s Permissions", name));
        addLore(lores, tr("\u00a7eHover over an icon to view\n\u00a7ea permission. Change the\n\u00a7epermission by clicking it."));
        meta3.setLore(lores);
        pHead.setItemMeta(meta3);
        menu.addItem(pHead);
        lores.clear();
        IslandInfo islandInfo = plugin.getIslandInfo(inventoryViewer);
        boolean isLeader = islandInfo.isLeader(inventoryViewer);
        for (PartyPermissionMenuItem menuItem : permissionMenuItems) {
            ItemStack itemStack = menuItem.getIcon();
            meta2 = requireNonNull(requireNonNull(itemStack.getItemMeta()));
            if (islandInfo.hasPerm(partyMember.getUniqueId(), menuItem.getPerm())) {
                meta2.setDisplayName("\u00a7a" + menuItem.getTitle());
                lores.add(tr("\u00a7fThis player \u00a7acan"));
                addLore(lores, "\u00a7f", menuItem.getDescription());
                if (isLeader) {
                    addLore(lores, "\u00a7f", tr("Click here to remove this permission."));
                }
            } else {
                meta2.setDisplayName("\u00a7c" + menuItem.getTitle());
                lores.add(tr("\u00a7fThis player \u00a7ccannot"));
                addLore(lores, "\u00a7f", menuItem.getDescription());
                if (isLeader) {
                    addLore(lores, "\u00a7f", tr("Click here to grant this permission."));
                }
            }
            meta2.setLore(lores);
            itemStack.setItemMeta(meta2);
            menu.addItem(itemStack);
            lores.clear();
        }
        return menu;
    }

    private void addLore(List<String> lores, String format, String multiLine) {
        for (String line : multiLine.split("\n")) {
            lores.add(format + line);
        }
    }

    private void addLore(List<String> lores, String multiLine) {
        addLore(lores, "", multiLine);
    }

    public Inventory displayPartyGUI(final Player player) {
        List<String> lores = new ArrayList<>();
        String title = "\u00a79" + tr("Island Group Members");
        Inventory menu = Bukkit.createInventory(new UltimateHolder(player, title, MenuType.DEFAULT), 18, title);
        IslandInfo islandInfo = plugin.getIslandInfo(player);
        final Set<UUID> memberList = islandInfo.getMemberUUIDs();
        final ItemMeta meta2 = requireNonNull(requireNonNull(sign.getItemMeta()));
        meta2.setDisplayName("\u00a7a" + tr("Island Group Members"));
        lores.add(tr("Group Members: \u00a72{0}\u00a77/\u00a7e{1}", islandInfo.getPartySize(), islandInfo.getMaxPartySize()));
        if (islandInfo.getPartySize() < islandInfo.getMaxPartySize()) {
            addLore(lores, tr("\u00a7aMore players can be invited to this island."));
        } else {
            addLore(lores, tr("\u00a7cThis island is full."));
        }
        addLore(lores, tr("\u00a7eHover over a player''s icon to\n\u00a7eview their permissions. The\n\u00a7eleader can change permissions\n\u00a7eby clicking a player''s icon."));
        meta2.setLore(lores);
        sign.setItemMeta(meta2);
        menu.addItem(sign.clone());
        lores.clear();
        for (UUID memberId : memberList) {
            ItemStack headItem = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta meta3 = requireNonNull((SkullMeta) requireNonNull(headItem.getItemMeta()));
            // ★★★ 获取玩家名字，用于显示和创建完整的 PlayerProfile ★★★
            String memberName = Bukkit.getOfflinePlayer(memberId).getName();
            if (memberName == null) {
                memberName = memberId.toString();
            }
            meta3.setDisplayName(tr("\u00a7e{0}''s\u00a79 Permissions", memberName));
            // ★★★ 用完整信息创建 PlayerProfile，避免 getName() 返回 null ★★★
            meta3.setOwnerProfile(Bukkit.createPlayerProfile(memberId, memberName));
            boolean isLeader = islandInfo.isLeader(memberId);
            if (isLeader) {
                addLore(lores, "\u00a7a\u00a7l", tr("Leader"));
            } else {
                addLore(lores, "\u00a7e\u00a7l", tr("Member"));
            }
            for (PartyPermissionMenuItem perm : permissionMenuItems) {
                if (isLeader || islandInfo.hasPerm(memberId, perm.getPerm())) {
                    lores.add("\u00a7a" + tr("Can {0}", "\u00a7f" + perm.getShortDescription()));
                } else {
                    lores.add("\u00a7c" + tr("Cannot {0}", "\u00a7f" + perm.getShortDescription()));
                }
            }
            if (islandInfo.isLeader(player.getName())) {
                addLore(lores, tr("\u00a7e<Click to change this player''s permissions>"));
            }
            meta3.setLore(lores);
            headItem.setItemMeta(meta3);
            menu.addItem(headItem);
            lores.clear();
        }
        return menu;
    }

    public Inventory displayLogGUI(final Player player) {
        List<String> lores = new ArrayList<>();
        String title = "\u00a79" + tr("Island Log");
        Inventory menu = Bukkit.createInventory(new UltimateHolder(player, title, MenuType.DEFAULT), 9, title);
        ItemMeta meta4 = requireNonNull(requireNonNull(sign.getItemMeta()));
        meta4.setDisplayName("\u00a79\u00a7l" + tr("Island Log"));
        addLore(lores, tr("\u00a7eClick here to return to\n\u00a7ethe main island screen."));
        meta4.setLore(lores);
        sign.setItemMeta(meta4);
        menu.addItem(sign);
        lores.clear();
        ItemStack menuItem = new ItemStack(Material.WRITABLE_BOOK, 1);
        meta4 = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta4.setDisplayName(tr("\u00a7e\u00a7lIsland Log"));
        lores.addAll(plugin.getIslandInfo(player).getLog());
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.setItem(8, menuItem);
        lores.clear();
        return menu;
    }

    private void addExtraMenus(Player player, Inventory menu) {
        ConfigurationSection extras = config.getYamlConfig().getConfigurationSection("options.extra-menus");
        if (extras == null) {
            return;
        }
        for (String sIndex : extras.getKeys(false)) {
            ConfigurationSection menuSection = extras.getConfigurationSection(sIndex);
            if (menuSection == null) {
                continue;
            }
            try {
                int index = Integer.parseInt(sIndex, 10);
                String title = menuSection.getString("title", "\u00a9Unknown");
                String icon = menuSection.getString("displayItem", "CHEST");
                List<String> lores = new ArrayList<>();
                for (String l : menuSection.getStringList("lore")) {
                    Matcher matcher = PERM_VALUE_PATTERN.matcher(l);
                    if (matcher.matches()) {
                        String perm = matcher.group("perm");
                        String lore = matcher.group("value");
                        boolean not = matcher.group("not") != null;
                        if (perm != null) {
                            boolean hasPerm = player.hasPermission(perm);
                            if ((hasPerm && !not) || (!hasPerm && not)) {
                                lores.add(lore);
                            }
                        } else {
                            lores.add(lore);
                        }
                    }
                }
                // Only SIMPLE icons supported...
                ItemStack item = GuiItemUtil.createGuiDisplayItem(icon, title);
                ItemMeta meta = requireNonNull(requireNonNull(item.getItemMeta()));
                meta.setLore(lores);
                item.setItemMeta(meta);
                menu.setItem(index, item);
            } catch (Exception e) {
                log(Level.INFO, "\u00a79[uSkyBlock]\u00a7r Unable to add extra-menu " + sIndex + ": " + e);
            }
        }
    }

    private boolean isExtraMenuAction(Player player, ItemStack currentItem) {
        ConfigurationSection extras = config.getYamlConfig().getConfigurationSection("options.extra-menus");
        if (extras == null || currentItem == null || currentItem.getItemMeta() == null) {
            return false;
        }
        Material itemType = currentItem.getType();
        String itemTitle = requireNonNull(currentItem.getItemMeta()).getDisplayName();
        for (String sIndex : extras.getKeys(false)) {
            ConfigurationSection menuSection = extras.getConfigurationSection(sIndex);
            if (menuSection == null) {
                continue;
            }
            try {
                String title = menuSection.getString("title", "\u00a9Unknown");
                String icon = menuSection.getString("displayItem", "CHEST");
                Material material = Material.matchMaterial(icon);
                if (title.equals(itemTitle) && material == itemType) {
                    for (String command : menuSection.getStringList("commands")) {
                        Matcher matcher = PERM_VALUE_PATTERN.matcher(command);
                        if (matcher.matches()) {
                            String perm = matcher.group("perm");
                            String cmd = matcher.group("value");
                            boolean not = matcher.group("not") != null;
                            if (perm != null) {
                                boolean hasPerm = player.hasPermission(perm);
                                if ((hasPerm && !not) || (!hasPerm && not)) {
                                    plugin.execCommand(player, cmd, false);
                                }
                            } else {
                                plugin.execCommand(player, cmd, false);
                            }
                        } else {
                            log(Level.INFO, "\u00a7a[uSkyBlock] Malformed menu " + title + ", invalid command : " + command);
                        }
                    }
                    return true;
                }
            } catch (Exception e) {
                log(Level.INFO, "\u00a79[uSkyBlock]\u00a7r Unable to execute commands for extra-menu " + sIndex + ": " + e);
            }
        }
        return false;
    }

    public Inventory displayChallengeGUI(final Player player, int page, String playerName) {
        int total = challengeLogic.getTotalPages();
        String title = "\u00a79" + pre("{0} ({1}/{2})", tr("Challenge Menu"), page, total);
        Inventory menu = Bukkit.createInventory(new UltimateHolder(player, title, MenuType.DEFAULT), CHALLENGE_PAGESIZE + COLS_PER_ROW, title);
        final PlayerInfo pi = playerName == null ? plugin.getPlayerInfo(player) : plugin.getPlayerInfo(playerName);
        challengeLogic.populateChallengeRank(menu, pi, page, playerName != null && player.hasPermission("usb.mod.bypassrestriction"));
        int[] pages = new int[9];
        pages[0] = 1;
        pages[8] = total;
        int startOffset = 2;
        if (page > 5) {
            startOffset = (int) ((Math.round(page / 2d)) - 1);
            if (startOffset > total - 7) {
                startOffset = total - 7;
            }
        }
        for (int i = 0; i < 7; i++) {
            pages[i + 1] = startOffset + i;
        }
        for (int i = 0; i < pages.length; i++) {
            int p = pages[i];
            if (p >= 1 && p <= total) {
                ItemStack pageItem;
                if (p == page) {
                    pageItem = GuiItemUtil.createGuiDisplayItem(Material.WRITABLE_BOOK, tr("\u00a77Current page"));
                } else {
                    pageItem = GuiItemUtil.createGuiDisplayItem(Material.BOOK, tr("\u00a77Page {0}", p));
                }
                if (i == 0) {
                    pageItem = ItemStackUtil.builder(pageItem)
                        .displayName(tr("\u00a77First Page"))
                        .lore(playerName != null ? playerName : "")
                        .build();
                } else if (i == 8) {
                    pageItem = ItemStackUtil.builder(pageItem).displayName(tr("\u00a77Last Page")).build();
                }
                pageItem.setAmount(p);
                menu.setItem(i + CHALLENGE_PAGESIZE, pageItem);
            }
        }
        return menu;
    }

    public Inventory displayIslandGUI(final Player player) {
        if (plugin.hasIsland(player)) {
            return createMainMenu(player);
        } else {
            return createInitMenu(player);
        }
    }

    private Inventory createInitMenu(Player player) {
        List<String> schemeNames = islandGenerator.getSchemeNames();
        int menuSize = (int) Math.ceil(getMaxSchemeIndex(schemeNames) / 9d) * 9;
        String title = "\u00a79" + tr("Island Create Menu");
        Inventory menu = Bukkit.createInventory(new UltimateHolder(player, title, MenuType.DEFAULT), menuSize, title);
        List<String> lores = new ArrayList<>();
        ItemStack menuItem = new ItemStack(Material.OAK_SAPLING, 1);
        ItemMeta meta = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta.setDisplayName(tr("\u00a7a\u00a7lStart an Island"));
        addLore(lores, "\u00a7f", tr("Start your skyblock journey\nby starting your own island.\nComplete challenges to earn\nitems and skybucks to help\nexpand your skyblock. You can\ninvite others to join in\nbuilding your island empire!\n\u00a7e\u00a7lClick here to start!"));
        meta.setLore(lores);
        menuItem.setItemMeta(meta);
        menu.addItem(menuItem);
        lores.clear();

        if (config.getYamlConfig().getBoolean("island-schemes-enabled", true) && schemeNames.size() > 1) {
            int index = 1;
            for (String schemeName : schemeNames) {
                IslandPerk islandPerk = perkLogic.getIslandPerk(schemeName);
                boolean enabled = config.getYamlConfig().getBoolean("island-schemes." + islandPerk.getSchemeName() + ".enabled", true);
                if (!enabled) {
                    continue; // Skip
                }
                index = Math.max(config.getYamlConfig().getInt("island-schemes." + islandPerk.getSchemeName() + ".index", index), 1);
                menuItem = islandPerk.getDisplayItem();
                meta = requireNonNull(requireNonNull(menuItem.getItemMeta()));
                lores = meta.getLore();
                if (lores == null) {
                    lores = new ArrayList<>();
                }
                if (player.hasPermission(islandPerk.getPermission())) {
                    addLore(lores, tr("\u00a7aClick to create!"));
                } else {
                    addLore(lores, tr("\u00a7cNo access!\n\u00a77({0})", islandPerk.getPermission()));
                }
                meta.setLore(lores);
                menuItem.setItemMeta(meta);
                menu.setItem(index++, menuItem);
            }
        }

        lores.clear();
        menuItem = new ItemStack(Material.SHORT_GRASS, 1);
        meta = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta.setDisplayName(tr("\u00a7a\u00a7lReturn to Spawn"));
        addLore(lores, "\u00a7f", tr("Teleport to the spawn area."));
        meta.setLore(lores);
        menuItem.setItemMeta(meta);
        menu.setItem(menuSize - 2, menuItem);

        lores.clear();
        menuItem = new ItemStack(Material.PLAYER_HEAD, 1);
        final SkullMeta meta2 = requireNonNull((SkullMeta) requireNonNull(menuItem.getItemMeta()));
        meta2.setDisplayName(tr("\u00a7a\u00a7lJoin an Island"));
        addLore(lores, "\u00a7f", tr("Want to join another player''s\nisland instead of starting\nyour own? If another player\ninvites you to their island\nyou can click here or use\n\u00a7e/island accept\u00a7f to join them.\n\u00a7e\u00a7lClick here to accept an invite!\n\u00a7e\u00a7l(You must be invited first)"));
        meta2.setLore(lores);
        menuItem.setItemMeta(meta2);
        menu.setItem(menuSize - 1, menuItem);
        return menu;
    }

    private int getMaxSchemeIndex(List<String> schemeNames) {
        int index = 1;
        for (String schemeName : schemeNames) {
            int nextIndex = config.getYamlConfig().getInt("island-schemes." + schemeName + ".index", index);
            if (nextIndex > index) {
                index = nextIndex;
            } else {
                index++;
            }
        }
        return index + 3;
    }

    private Inventory createMainMenu(Player player) {
        String title = "\u00a79" + tr("Island Menu");
        Inventory menu = Bukkit.createInventory(new UltimateHolder(player, title, MenuType.DEFAULT), 18, title);
        List<String> lores = new ArrayList<>();
        ItemStack menuItem = new ItemStack(Material.OAK_DOOR, 1);
        ItemMeta meta4 = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta4.setDisplayName(tr("\u00a7a\u00a7lReturn Home"));
        addLore(lores, "\u00a7f", tr("Return to your island''s home\npoint. You can change your home\npoint to any location on your\nisland using \u00a7b/island sethome\n\u00a7e\u00a7lClick here to return home."));
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.addItem(menuItem);
        lores.clear();

        IslandInfo islandInfo = plugin.getIslandInfo(player);

        menuItem = new ItemStack(Material.DIAMOND_ORE, 1);
        meta4 = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta4.setDisplayName(tr("\u00a7a\u00a7lChallenges"));
        addLore(lores, "\u00a7f", tr("View a list of \u00a79challenges that\nyou can complete on your island\nto earn skybucks, items, perks,\nand titles."));
        if (challengeLogic.isEnabled()) {
            addLore(lores, tr("\u00a7e\u00a7lClick here to view challenges."));
        } else {
            addLore(lores, tr("\u00a74\u00a7lChallenges disabled."));
        }
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.addItem(menuItem);

        lores.clear();
        menuItem = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        meta4 = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta4.setDisplayName(tr("\u00a7a\u00a7lIsland Level"));
        addLore(lores, tr("\u00a7eCurrent Level: \u00a7a{0,number,##.#}", islandInfo.getLevel()));
        addLore(lores, limitLogic.getSummary(islandInfo));
        addLore(lores, "\u00a7f", tr("Gain island levels by expanding\nyour skyblock and completing\ncertain challenges. Rarer blocks\nwill add more to your level.\n\u00a7e\u00a7lClick here to refresh.\n\u00a7e\u00a7l(must be on island)"));
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.addItem(menuItem);
        lores.clear();

        menuItem = new ItemStack(Material.PLAYER_HEAD, 1);
        final SkullMeta meta2 = requireNonNull((SkullMeta) requireNonNull(menuItem.getItemMeta()));
        meta2.setDisplayName("\u00a7a\u00a7l" + tr("Island Group"));
        lores.add(tr("\u00a7eMembers: \u00a72{0}/{1}", islandInfo.getPartySize(), islandInfo.getMaxPartySize()));
        addLore(lores, "\u00a7f", tr("View the members of your island\ngroup and their permissions. If\nyou are the island leader, you\ncan change the member permissions.\n\u00a7e\u00a7lClick here to view or change."));
        meta2.setLore(lores);
        menuItem.setItemMeta(meta2);
        menu.addItem(menuItem);
        lores.clear();

        menuItem = new ItemStack(Material.JUNGLE_SAPLING, 1);
        meta4 = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta4.setDisplayName("\u00a7a\u00a7l" + tr("Change Island Biome"));
        lores.add(tr("\u00a7eCurrent Biome: \u00a7b{0}", tr(islandInfo.getBiomeName())));        addLore(lores, "\u00a7f", tr("The island biome affects things\nlike grass color and spawning\nof both animals and monsters."));
        if (islandInfo.hasPerm(player, "canChangeBiome")) {
            addLore(lores, tr("\u00a7e\u00a7lClick here to change biomes."));
        } else {
            addLore(lores, tr("\u00a7c\u00a7lYou can't change the biome."));
        }
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.addItem(menuItem);
        lores.clear();

        menuItem = new ItemStack(Material.IRON_BARS, 1);
        meta4 = requireNonNull(requireNonNull(menuItem.getItemMeta()));
        meta4.setDisplayName(tr("\u00a7a\u00a7lIsland Lock"));
        if (plugin.getIslandInfo(player).isLocked()) {
            addLore(lores, tr("\u00a7eLock Status: \u00a7aActive\n\u00a7fYour island is currently \u00a7clocked.\n\u00a7fPlayers outside of your group\n\u00a7fare unable to enter your island."));
            if (islandInfo.hasPerm(player, "canToggleLock") && player.hasPermission("usb.island.lock")) {
                addLore(lores, tr("\u00a7e\u00a7lClick here to unlock your island."));
            } else {
                addLore(lores, tr("\u00a7c\u00a7lYou can't change the lock."));
            }
        } else {
            addLore(lores, tr("\u00a7eLock Status: \u00a78Inactive\n\u00a7fYour island is currently \u00a7aunlocked.\n\u00a7fAll players are able to enter your\n\u00a7fisland, but only you and your group\n\u00a7fmembers may build there."));
            if (islandInfo.hasPerm(player, "canToggleLock") && player.hasPermission("usb.island.lock")) {
                addLore(lores, tr("\u00a7e\u00a7lClick here to lock your island."));
            } else {
                addLore(lores, tr("\u00a7c\u00a7lYou can't change the lock."));
            }
        }
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        //menu.addItem(menuItem);
        lores.clear();

        if (plugin.getIslandInfo(player).hasWarp()) {
            menuItem = new ItemStack(Material.END_PORTAL_FRAME, 1);
            meta4 = requireNonNull(requireNonNull(menuItem.getItemMeta()));
            meta4.setDisplayName(tr("\u00a7a\u00a7lIsland Warp"));
            addLore(lores, tr("\u00a7eWarp Status: \u00a7aActive\n\u00a7fOther players may warp to your\n\u00a7fisland at anytime to the point\n\u00a7fyou set using \u00a7d/island setwarp."));
            if (islandInfo.hasPerm(player, "canToggleWarp") && player.hasPermission("usb.island.togglewarp")) {
                addLore(lores, tr("\u00a7e\u00a7lClick here to deactivate."));
            } else {
                addLore(lores, tr("\u00a7c\u00a7lYou can't change the warp."));
            }
        } else {
            menuItem = new ItemStack(Material.END_STONE, 1);
            meta4 = requireNonNull(menuItem.getItemMeta());
            meta4.setDisplayName(tr("\u00a7a\u00a7lIsland Warp"));
            addLore(lores, tr("\u00a7eWarp Status: \u00a78Inactive\n\u00a7fOther players can't warp to your\n\u00a7fisland. Set a warp point using\n\u00a7d/island setwarp \u00a7fbefore activating."));
            if (islandInfo.hasPerm(player, "canToggleWarp") && player.hasPermission("usb.island.togglewarp")) {
                addLore(lores, tr("\u00a7e\u00a7lClick here to activate."));
            } else {
                addLore(lores, tr("\u00a7c\u00a7lYou can't change the warp."));
            }
        }
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.addItem(menuItem);
        lores.clear();

        menuItem = new ItemStack(Material.NETHER_STAR, 1);
        meta4 = menuItem.getItemMeta();
        meta4.setDisplayName("\u00a7b\u00a7l" + tr("商店"));
        addLore(lores, "\u00a7f使用\u00a7b天空币\u00a7f购买各类\u00a7a基础或稀有物资\u00a7f，以及\u00a7d特殊权限\u00a7f！\n\u00a7e\u00a7l点击进入商店");
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.addItem(menuItem);
        lores.clear();

        menuItem = new ItemStack(Material.SHORT_GRASS, 1);
        meta4 = requireNonNull(menuItem.getItemMeta());
        meta4.setDisplayName(tr("\u00a7a\u00a7lReturn to Spawn"));
        addLore(lores, "\u00a7f", tr("Teleport to the spawn area."));
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.addItem(menuItem);
        lores.clear();

        menuItem = new ItemStack(Material.WRITABLE_BOOK, 1);
        meta4 = requireNonNull(menuItem.getItemMeta());
        meta4.setDisplayName(tr("\u00a7a\u00a7lIsland Log"));
        addLore(lores, "\u00a7f", tr("View a log of events from\nyour island such as member,\nbiome, and warp changes.\n\u00a7e\u00a7lClick to view the log."));
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.setItem(8, menuItem); // Last item, first line
        lores.clear();

        menuItem = new ItemStack(Material.RED_BED, 1); // red bed
        meta4 = requireNonNull(menuItem.getItemMeta());
        meta4.setDisplayName(tr("\u00a7a\u00a7lChange Home Location"));
        addLore(lores, "\u00a7f", tr("When you teleport to your\nisland you will be taken to\nthis location.\n\u00a7e\u00a7lClick here to change."));
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.setItem(9, menuItem); // First item, 2nd line
        lores.clear();

        menuItem = new ItemStack(Material.BEACON, 1);
        meta4 = requireNonNull(menuItem.getItemMeta());
        meta4.setDisplayName(tr("\u00a7a\u00a7lChange Warp Location"));
        addLore(lores, "\u00a7f", tr("When your warp is activated,\nother players will be taken to\nthis point when they teleport\nto your island."));
        if (islandInfo.hasPerm(player, "canChangeWarp") && player.hasPermission("usb.island.setwarp")) {
            addLore(lores, tr("\u00a7e\u00a7lClick here to change."));
        } else {
            addLore(lores, tr("\u00a7c\u00a7lYou can't change the warp."));
        }
        meta4.setLore(lores);
        menuItem.setItemMeta(meta4);
        menu.setItem(13, menuItem);
        lores.clear();
        if (islandInfo.isLeader(player)) {
            if (config.getYamlConfig().getBoolean("island-schemes-enabled", true)) {
                menuItem = new ItemStack(Material.PODZOL, 1);
                meta4 = requireNonNull(menuItem.getItemMeta());
                meta4.setDisplayName(tr("\u00a7c\u00a7lRestart Island"));
                addLore(lores, "\u00a7f", tr("Restarts your island.\n\u00a74WARNING! \u00a7cwill remove your items and island!"));
                meta4.setLore(lores);
                menuItem.setItemMeta(meta4);
                menu.setItem(17, menuItem);
                lores.clear();
            }
        } else {
            menuItem = new ItemStack(Material.IRON_DOOR, 1);
            meta4 = requireNonNull(menuItem.getItemMeta());
            meta4.setDisplayName(tr("\u00a7c\u00a7lLeave Island"));
            addLore(lores, "\u00a7f", tr("Leaves your island.\n\u00a74WARNING! \u00a7cwill remove all your items!"));
            addLore(lores, tr("\u00a7cClick to leave"));
            meta4.setLore(lores);
            menuItem.setItemMeta(meta4);
            menu.setItem(17, menuItem);
            lores.clear();
            Duration durationLeft = confirmHandler.durationLeft(player, "/is leave");
            if (durationLeft.isPositive()) {
                updateLeaveMenuItemTimer(player, menu, menuItem);
            }
        }
        addExtraMenus(player, menu);
        return menu;
    }

    public void onClick(InventoryClickEvent event) {
        ItemStack currentItem = event != null ? event.getCurrentItem() : null;
        if (event == null || currentItem == null) {
            return; // Bail out, nothing we can do anyway
        }
        Player p = (Player) event.getWhoClicked();
        ItemMeta meta = requireNonNull(currentItem.getItemMeta());
        SkullMeta skull = meta instanceof SkullMeta ? (SkullMeta) meta : null;
        if (!(event.getInventory().getHolder() instanceof UltimateHolder))
            return;
        String inventoryName = stripFormatting(((UltimateHolder) event.getInventory().getHolder()).getTitle());
        int slotIndex = event.getSlot();
        int menuSize = event.getInventory().getSize();
        if (inventoryName.equalsIgnoreCase(stripFormatting(tr("Island Group Members")))) {
            onClickPartyMenu(event, currentItem, p, meta, skull, slotIndex);
        } else if (inventoryName.contains(stripFormatting(tr("Permissions")))) {
            onClickPermissionMenu(event, currentItem, p, inventoryName, slotIndex);
        } else if (inventoryName.contains(stripFormatting(tr("Challenge Menu")))) {
            onClickChallengeMenu(event, currentItem, p, inventoryName);
        } else if (inventoryName.equalsIgnoreCase(stripFormatting(tr("Island Log")))) {
            onClickLogMenu(event, p, slotIndex);
        } else if (inventoryName.equalsIgnoreCase(stripFormatting(tr("Island Menu")))) {
            onClickMainMenu(event, currentItem, p, slotIndex);
        } else if (inventoryName.equalsIgnoreCase(stripFormatting(tr("Island Create Menu")))) {
            onClickCreateMenu(event, p, meta, slotIndex, menuSize);
        } else if (inventoryName.equalsIgnoreCase(stripFormatting(tr("Island Restart Menu")))) {
            onClickRestartMenu(event, p, meta, slotIndex, currentItem);
        } else if (inventoryName.startsWith(stripFormatting(tr("商店")))) {
            onClickShopMenu(event, currentItem, p, inventoryName, slotIndex);
        }

    }

    private void onClickRestartMenu(final InventoryClickEvent event, final Player p, ItemMeta meta, int slotIndex, ItemStack currentItem) {
        event.setCancelled(true);
        if (slotIndex == 0) {
            p.openInventory(createMainMenu(p));
        } else if (currentItem != null && meta != null && meta.hasDisplayName()) {
            String schemeName = stripFormatting(meta.getDisplayName());
            IslandPerk islandPerk = perkLogic.getIslandPerk(schemeName);
            if (perkLogic.getSchemes(p).contains(schemeName) && p.hasPermission(islandPerk.getPermission())) {
                if (confirmHandler.durationLeft(p, "/is restart").isPositive()) {
                    p.performCommand("island restart " + schemeName);
                } else {
                    p.performCommand("island restart " + schemeName);
                    updateRestartMenuTimer(p, event.getInventory());
                }
            }
        }
    }

    private void updateRestartMenuTimer(final Player p, final Inventory inventory) {
        final BukkitTask[] hackySharing = new BukkitTask[1];
        hackySharing[0] = scheduler.sync(() -> {
            if (inventory.getViewers().contains(p)) {
                updateRestartMenu(inventory, p, islandGenerator.getSchemeNames());
            }
            if (!confirmHandler.durationLeft(p, "/is restart").isPositive() || !inventory.getViewers().contains(p)) {
                if (hackySharing.length > 0 && hackySharing[0] != null) {
                    hackySharing[0].cancel();
                }
            }
        }, Duration.ZERO, Duration.ofSeconds(1));
    }

    private void onClickCreateMenu(InventoryClickEvent event, Player p, ItemMeta meta, int slotIndex, int menuSize) {
        event.setCancelled(true);
        if (slotIndex == 0) {
            p.performCommand("island create");
        } else if (slotIndex == menuSize - 2) {
            p.performCommand("island spawn");
        } else if (slotIndex == menuSize - 1) {
            p.performCommand("island accept");
        } else if (meta != null && meta.hasDisplayName()) {
            String schemeName = stripFormatting(meta.getDisplayName());
            if (perkLogic.getSchemes(p).contains(schemeName)) {
                p.performCommand("island create " + schemeName);
            } else {
                p.sendMessage(tr("\u00a7eYou do not have access to that island-schematic!"));
            }
        }
    }

    private void onClickMainMenu(InventoryClickEvent event, ItemStack currentItem, Player player, int slotIndex) {
        event.setCancelled(true);
        if (slotIndex < 0 || slotIndex > 35) {
            return;
        }
        PlayerInfo playerInfo = plugin.getPlayerInfo(player);
        IslandInfo islandInfo = plugin.getIslandInfo(playerInfo);
        if (currentItem.getType() == Material.JUNGLE_SAPLING) {
            player.performCommand("island biome");
        } else if (currentItem.getType() == Material.PLAYER_HEAD) {
            player.performCommand("island party");
        } else if (currentItem.getType() == Material.RED_BED) {
            player.performCommand("island sethome");
            player.performCommand("island");
        } else if (currentItem.getType() == Material.SHORT_GRASS) {
            player.performCommand("island spawn");
        } else if (currentItem.getType() == Material.BEACON) {
            player.performCommand("island setwarp");
            player.performCommand("island");
        } else if (currentItem.getType() == Material.NETHER_STAR) {
            player.openInventory(displayShopGUI(player, null, 1));
        } else if (currentItem.getType() == Material.WRITABLE_BOOK) {
            player.performCommand("island log");
        } else if (currentItem.getType() == Material.OAK_DOOR) {
            player.performCommand("island home");
        } else if (currentItem.getType() == Material.EXPERIENCE_BOTTLE) {
            player.performCommand("island level");
        } else if (currentItem.getType() == Material.DIAMOND_ORE) {
            player.performCommand("c");
        } else if (currentItem.getType() == Material.END_STONE || currentItem.getType() == Material.END_PORTAL_FRAME) {
            player.performCommand("island togglewarp");
            player.performCommand("island");
        } else if (currentItem.getType() == Material.IRON_BARS && islandInfo.isLocked()) {
            player.performCommand("island unlock");
            player.performCommand("island");
        } else if (currentItem.getType() == Material.IRON_BARS && !islandInfo.isLocked()) {
            player.performCommand("island lock");
            player.performCommand("island");
        } else if (slotIndex == 17) {
            if (islandInfo.isLeader(player) && config.getYamlConfig().getBoolean("island-schemes-enabled", true)) {
                player.openInventory(createRestartGUI(player));
            } else {
                if (confirmHandler.durationLeft(player, "/is leave").isPositive()) {
                    player.performCommand("island leave");
                } else {
                    player.performCommand("island leave");
                    updateLeaveMenuItemTimer(player, event.getInventory(), currentItem);
                }
            }
        } else {
            if (!isExtraMenuAction(player, currentItem)) {
                player.performCommand("island");
            }
        }
    }

    private void updateLeaveMenuItemTimer(final Player player, final Inventory inventory, final ItemStack currentItem) {
        BukkitTask[] hackySharing = new BukkitTask[1];
        hackySharing[0] = scheduler.sync(() -> {
            Duration durationLeft = confirmHandler.durationLeft(player, "/is leave");
            if (inventory.getViewers().contains(player)) {
                updateLeaveMenuItem(inventory, currentItem, durationLeft);
            }
            if (!durationLeft.isPositive() || !inventory.getViewers().contains(player)) {
                if (hackySharing.length > 0 && hackySharing[0] != null) {
                    hackySharing[0].cancel();
                }
            }
        }, Duration.ZERO, Duration.ofSeconds(1));
    }

    private void updateLeaveMenuItem(Inventory inventory, ItemStack currentItem, Duration durationLeft) {
        if (currentItem == null || currentItem.getItemMeta() == null || currentItem.getItemMeta().getLore() == null) {
            return;
        }
        ItemMeta meta = requireNonNull(currentItem.getItemMeta());
        List<String> lore = meta.getLore();
        if (!durationLeft.isNegative()) {
            lore.set(lore.size() - 1, tr("\u00a7cClick within \u00a79{0}\u00a7c to leave!", TimeUtil.durationAsString(durationLeft)));
        } else {
            lore.set(lore.size() - 1, tr("\u00a7cClick to leave"));
        }
        meta.setLore(lore);
        currentItem.setItemMeta(meta);
        inventory.setItem(17, currentItem);
    }

    public Inventory createRestartGUI(Player player) {
        List<String> schemeNames = islandGenerator.getSchemeNames();
        int menuSize = (int) Math.ceil(getMaxSchemeIndex(schemeNames) / 9d) * 9;
        String title = "\u00a79" + tr("Island Restart Menu");
        Inventory menu = Bukkit.createInventory(new UltimateHolder(player, title, MenuType.DEFAULT), menuSize, title);
        List<String> lores = new ArrayList<>();
        ItemStack menuItem = new ItemStack(Material.OAK_SIGN, 1);
        ItemMeta meta = requireNonNull(menuItem.getItemMeta());
        meta.setDisplayName(tr("\u00a7a\u00a7lReturn to the main menu"));
        meta.setLore(lores);
        menuItem.setItemMeta(meta);
        menu.addItem(menuItem);
        lores.clear();

        updateRestartMenu(menu, player, schemeNames);
        if (confirmHandler.durationLeft(player, "/is restart").isPositive()) {
            updateRestartMenuTimer(player, menu);
        }
        return menu;
    }

    private void updateRestartMenu(Inventory menu, Player player, List<String> schemeNames) {
        ItemStack menuItem;
        ItemMeta meta;
        List<String> lores;
        int index = 1;
        for (String schemeName : schemeNames) {
            IslandPerk islandPerk = perkLogic.getIslandPerk(schemeName);
            boolean enabled = config.getYamlConfig().getBoolean("island-schemes." + islandPerk.getSchemeName() + ".enabled", true);
            if (!enabled) {
                continue; // Skip
            }
            index = config.getYamlConfig().getInt("island-schemes." + islandPerk.getSchemeName() + ".index", index);
            menuItem = islandPerk.getDisplayItem();
            meta = requireNonNull(menuItem.getItemMeta());
            lores = meta.getLore();
            if (lores == null) {
                lores = new ArrayList<>();
            }
            if (player.hasPermission(islandPerk.getPermission())) {
                Duration durationLeft = confirmHandler.durationLeft(player, "/is restart");
                if (durationLeft.isPositive()) {
                    addLore(lores, tr("\u00a7cClick within \u00a79{0}\u00a7c to restart!", TimeUtil.durationAsString(durationLeft)));
                } else {
                    addLore(lores, tr("\u00a7aClick to restart!"));
                }
            } else {
                addLore(lores, tr("\u00a7cNo access!\n\u00a77({0})", islandPerk.getPermission()));
            }
            meta.setLore(lores);
            menuItem.setItemMeta(meta);
            menu.setItem(index++, menuItem);
        }
    }

    private void onClickLogMenu(InventoryClickEvent event, Player p, int slotIndex) {
        event.setCancelled(true);
        if (slotIndex < 0 || slotIndex > 35) {
            return;
        }
        p.performCommand("island");
    }

    private void onClickChallengeMenu(InventoryClickEvent event, ItemStack currentItem, Player p, String inventoryName) {
        int slotIndex = event.getRawSlot();
        event.setCancelled(true);
        Matcher m = CHALLENGE_PAGE_HEADER.matcher(inventoryName);
        int page = 1;
        int max = challengeLogic.getTotalPages();
        if (m.find()) {
            page = Integer.parseInt(m.group("p"));
            max = Integer.parseInt(m.group("max"));
        }
        ItemStack item = event.getInventory().getItem(event.getInventory().getSize() - 9);
        String playerName = item != null && item.hasItemMeta() && requireNonNull(item.getItemMeta()).getLore() != null
            && !item.getItemMeta().getLore().isEmpty()
            ? item.getItemMeta().getLore().getFirst()
            : null;
        if (playerName != null && playerName.trim().isEmpty()) {
            playerName = null;
        }
        // Last row is pagination
        if (slotIndex >= CHALLENGE_PAGESIZE && slotIndex < CHALLENGE_PAGESIZE + COLS_PER_ROW
            && currentItem != null && currentItem.getType() != Material.AIR) {
            // Pagination
            p.openInventory(displayChallengeGUI(p, currentItem.getAmount(), playerName));
            return;
        }
        // If in action bar or anywhere else, just bail out
        if (slotIndex < 0 || slotIndex > CHALLENGE_PAGESIZE || isAirOrLocked(currentItem)) {
            return;
        }
        if ((slotIndex % 9) > 0) { // 0,9... are the rank-headers...
            if (currentItem.getItemMeta() != null) {
                String challenge = requireNonNull(currentItem.getItemMeta()).getDisplayName();
                String challengeName = stripFormatting(challenge);
                p.performCommand("c c " + challengeName);
            }
            p.openInventory(displayChallengeGUI(p, page, playerName));
        } else {
            if (slotIndex < (CHALLENGE_PAGESIZE / 2)) { // Upper half
                if (page > 1) {
                    p.openInventory(displayChallengeGUI(p, page - 1, playerName));
                } else {
                    p.performCommand("island");
                }
            } else if (page < max) {
                p.openInventory(displayChallengeGUI(p, page + 1, playerName));
            } else {
                p.performCommand("island");
            }
        }
    }

    private boolean isAirOrLocked(ItemStack currentItem) {
        return currentItem != null && currentItem.getType() == Material.AIR ||
            currentItem != null && currentItem.getItemMeta() != null && currentItem.getItemMeta().getDisplayName().equals(tr("\u00a74\u00a7lLocked Challenge"));
    }

    // ==================== 修复 3：onClickPermissionMenu ====================

    private void onClickPermissionMenu(InventoryClickEvent event, ItemStack currentItem, Player p, String inventoryName, int slotIndex) {
        event.setCancelled(true);
        if (slotIndex < 0 || slotIndex > 35) {
            return;
        }
        IslandInfo islandInfo = plugin.getIslandInfo(p);
        if (!plugin.getIslandInfo(p).isLeader(p)) {
            p.openInventory(displayPartyGUI(p));
        }
        String[] playerPerm = inventoryName.split(" ");
        String name = playerPerm[0];
        UUID uuid = plugin.getPlayerDB().getUUIDFromName(name);
        PlayerProfile profile = Bukkit.createPlayerProfile(uuid, name);
        ItemStack skullItem = event.getInventory().getItem(1);
        if (skullItem != null && skullItem.getType().equals(Material.PLAYER_HEAD)) {
            ItemMeta meta = requireNonNull(skullItem.getItemMeta());
            if (meta instanceof SkullMeta skullMeta) {
                PlayerProfile skullProfile = skullMeta.getOwnerProfile();
                if (skullProfile != null && skullProfile.getName() != null && skullProfile.getUniqueId() != null) {
                    profile = skullProfile;
                }
            }
        }
        for (PartyPermissionMenuItem item : permissionMenuItems) {
            if (currentItem.getType() == item.getIcon().getType()) {
                islandInfo.togglePerm(profile.getUniqueId(), item.getPerm());
                if (profile != null && profile.getName() != null && profile.getUniqueId() != null) {
                    p.openInventory(displayPartyPlayerGUI(p, profile));
                } else {
                    p.openInventory(displayPartyGUI(p));
                    plugin.getLogger().warning("Invalid PlayerProfile in permission menu, returning to party menu.");
                }
                return;
            }
        }
        if (currentItem.getType() == Material.OAK_SIGN) {
            p.openInventory(displayPartyGUI(p));
        } else {
            if (profile != null && profile.getName() != null && profile.getUniqueId() != null) {
                p.openInventory(displayPartyPlayerGUI(p, profile));
            } else {
                p.openInventory(displayPartyGUI(p));
                plugin.getLogger().warning("Invalid PlayerProfile in permission menu, returning to party menu.");
            }
        }
    }

    // ==================== 修复 1：onClickPartyMenu ====================

    private void onClickPartyMenu(InventoryClickEvent event, ItemStack currentItem, Player p, ItemMeta meta, SkullMeta skull, int slotIndex) {
        event.setCancelled(true);
        if (slotIndex < 0 || slotIndex > 35) {
            return;
        }
        if (meta == null || currentItem.getType() == Material.OAK_SIGN) {
            p.performCommand("island");
        } else if (skull != null && skull.hasOwner() && plugin.getIslandInfo(p).isLeader(p)) {
            PlayerProfile ownerProfile = skull.getOwnerProfile();
            if (ownerProfile != null && ownerProfile.getName() != null && ownerProfile.getUniqueId() != null) {
                p.openInventory(displayPartyPlayerGUI(p, ownerProfile));
            } else {
                p.openInventory(displayPartyGUI(p));
                plugin.getLogger().warning("Unable to get valid PlayerProfile for skull item, returning to party menu.");
            }
        }
    }

    public List<PartyPermissionMenuItem> getPermissionMenuItems() {
        return permissionMenuItems;
    }
    // ==================== 商店 GUI (仿 Challenge GUI 布局) ====================

    // 商店菜单：与挑战菜单高度一致，6行物品 + 1行分页
    private static final int SHOP_PAGESIZE = CHALLENGE_PAGESIZE;
    // 第一列（分类）不算在可用物品区，实际物品从第2列开始
    private static final int SHOP_ITEM_COLS = COLS_PER_ROW - 1;
    // 每页显示物品数：5行 * 8列 = 40个
    private static final int SHOP_ITEMS_PER_PAGE = (SHOP_PAGESIZE / COLS_PER_ROW) * SHOP_ITEM_COLS;

    public Inventory displayShopGUI(Player player, String categoryId, int page) {
        ShopLogic shopLogic = plugin.getShopLogic();
        List<ShopCategory> categories = shopLogic.getCategories();
        if (categories.isEmpty()) {
            player.sendMessage(tr("\u00a7c商店暂无物品"));
            return createMainMenu(player);
        }

        // 默认选中第一个分类
        if (categoryId == null && !categories.isEmpty()) {
            categoryId = categories.getFirst().getId();
        }
        ShopCategory currentCategory = shopLogic.getCategory(categoryId);
        if (currentCategory == null && !categories.isEmpty()) {
            currentCategory = categories.getFirst();
            categoryId = currentCategory.getId();
        }

        int totalPages = Math.max(1, (int) Math.ceil(currentCategory.getItems().size() / (double) SHOP_ITEMS_PER_PAGE));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // 使用与挑战菜单相同的大尺寸
        String title = "\u00a79" + tr("商店") + "-" + stripFormatting(currentCategory.getName()) + " (" + page + "/" + totalPages + ")";
        Inventory menu = Bukkit.createInventory(
            new UltimateHolder(player, title, MenuType.DEFAULT), SHOP_PAGESIZE + COLS_PER_ROW, title);

        // 第一列：分类列表
        int catSlot = 0;
        for (ShopCategory cat : categories) {
            if (catSlot >= SHOP_PAGESIZE) break;
            // 使用 GuiItemUtil 来正确处理颜色
            ItemStack catItem = GuiItemUtil.createGuiDisplayItem(
                cat.getDisplayItem().getType().name(),
                cat.getId().equals(categoryId) ? "\u00a7a\u00a7l" + cat.getName() : "\u00a77" + cat.getName()
            );
            if (cat.getId().equals(categoryId) && catItem != null) {
                ItemMeta catMeta = catItem.getItemMeta();
                if (catMeta != null) {
                    catMeta.addEnchant(org.bukkit.enchantments.Enchantment.LOYALTY, 1, true);
                                        catMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    catItem.setItemMeta(catMeta);
                }
            }
            menu.setItem(catSlot, catItem);
            catSlot += COLS_PER_ROW;
        }

        // 物品区域
        List<ShopItem> items = currentCategory.getItems();
        int startIndex = (page - 1) * SHOP_ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + SHOP_ITEMS_PER_PAGE, items.size());

        int slot = 1; // 从第二列开始
        for (int i = startIndex; i < endIndex; i++) {
            if (slot % COLS_PER_ROW == 0) slot++; // 跳过第一列（分类）
            if (slot >= SHOP_PAGESIZE) break; // 不要覆盖到翻页行

            List<String> unmetChallenges = new ArrayList<>();

            ShopItem shopItem = items.get(i);
            List<String> lores = new ArrayList<>();
            PlayerInfo pi = plugin.getPlayerInfo(player);
            PlayerShopData data = shopLogic.getPlayerData(player.getUniqueId());

            int actualPrice = shopLogic.getActualPrice(shopItem, player.getUniqueId());
            int bought = data.getBuyCount(shopItem.getId());

            lores.add(tr("\u00a7e价格: \u00a7f{0,number,#}", actualPrice));
            lores.add(tr("\u00a7e已购买: \u00a7f{0}/{1}", bought, shopItem.getMaxBuys()));

            // 收集所有未满足条件
            List<String> unmetReasons = new ArrayList<>();

            // 检查岛屿等级
            IslandInfo info = plugin.getIslandInfo(player);
            if (shopItem.getRequiredLevel() > 0 && info.getLevel() < shopItem.getRequiredLevel()) {
                unmetReasons.add(tr("\u00a7c需要岛屿等级: \u00a7f{0} \u00a77(当前: \u00a7f{1,number,##.#}\u00a77)", shopItem.getRequiredLevel(), info.getLevel()));
            }

            // 检查任务完成（只显示未完成的）
            if (!shopItem.getRequiredChallenges().isEmpty()) {
                boolean hasUnmet = false;
                for (String challengeId : shopItem.getRequiredChallenges()) {
                    if (pi.checkChallenge(challengeId) == 0) {
                        if (!hasUnmet) {
                            unmetReasons.add(tr("\u00a7c需要任务:"));
                            hasUnmet = true;
                        }
                        Challenge challenge = challengeLogic.getChallenge(challengeId);
                        String displayName = challenge != null ? challenge.getDisplayName() : challengeId;
                        unmetReasons.add(tr("\u00a7c  {0}", displayName));
                    }
                }
            }

            // 检查购买上限
            if (bought >= shopItem.getMaxBuys()) {
                unmetReasons.add(tr("\u00a7c已达购买上限"));
            }

            // 判断是否锁定
            boolean locked = !unmetReasons.isEmpty();

            if (locked) {
                lores.add(tr("\u00a7c\u00a7l商品已锁定"));
                lores.addAll(unmetReasons);
            } else {
                lores.add(tr("\u00a7e\u00a7l点击购买"));
            }

            ItemStack itemStack = ItemStackUtil.builder(shopItem.getDisplayItem().clone())
                .lore(lores)
                .build();

            menu.setItem(slot, itemStack);
            slot++;
        }

        // 底部分页（最后一格返回主菜单）
        int[] pages = new int[9];
        pages[0] = 1;
        pages[8] = 0; // 最后一格用于返回主菜单
        int startOffset = 2;
        if (page > 5) {
            startOffset = (int) ((Math.round(page / 2d)) - 1);
            if (startOffset > totalPages - 7) {
                startOffset = totalPages - 7;
            }
        }
        for (int i = 0; i < 7; i++) {
            pages[i + 1] = startOffset + i;
        }
        for (int i = 0; i < pages.length; i++) {
            int p = pages[i];
            if (i == 8) {
                // 最后一格：返回主菜单
                ItemStack backItem = GuiItemUtil.createGuiDisplayItem(Material.OAK_DOOR, tr("\u00a77返回主菜单"));
                backItem = ItemStackUtil.builder(backItem).displayName(tr("\u00a77返回主菜单")).build();
                menu.setItem(SHOP_PAGESIZE + 8, backItem);
            } else if (p >= 1 && p <= totalPages) {
                ItemStack pageItem;
                if (p == page) {
                    pageItem = GuiItemUtil.createGuiDisplayItem(Material.WRITABLE_BOOK, tr("\u00a77Current page"));
                } else {
                    pageItem = GuiItemUtil.createGuiDisplayItem(Material.BOOK, tr("\u00a77Page {0}", p));
                }
                if (i == 0) {
                    pageItem = ItemStackUtil.builder(pageItem)
                        .displayName(tr("\u00a77First Page"))
                        .build();
                }
                pageItem.setAmount(p);
                menu.setItem(i + SHOP_PAGESIZE, pageItem);
            }
        }

        return menu;
    }

    private void onClickShopMenu(InventoryClickEvent event, ItemStack currentItem,
                                  Player player, String inventoryName, int slotIndex) {
        event.setCancelled(true);

        // 只处理商店 GUI 内的点击，忽略玩家背包的点击
        if (event.getRawSlot() != event.getSlot()) {
            return;
        }

        ShopLogic shopLogic = plugin.getShopLogic();

        // 点击分类
        if (slotIndex % COLS_PER_ROW == 0 && slotIndex < SHOP_PAGESIZE) {
            List<ShopCategory> categories = shopLogic.getCategories();
            int catIndex = slotIndex / COLS_PER_ROW;
            if (catIndex < categories.size()) {
                player.openInventory(displayShopGUI(player, categories.get(catIndex).getId(), 1));
            }
            return;
        }

        // 翻页 / 返回主菜单
        if (slotIndex >= SHOP_PAGESIZE && slotIndex < SHOP_PAGESIZE + COLS_PER_ROW
            && currentItem != null && currentItem.getType() != Material.AIR) {
            if (slotIndex == SHOP_PAGESIZE + 8) {
                // 最后一格：返回主菜单
                player.openInventory(createMainMenu(player));
            } else {
                // 翻页
                player.openInventory(displayShopGUI(player, getCategoryIdFromInventoryName(inventoryName), currentItem.getAmount()));
            }
            return;
        }

        // 点击物品 - 购买（通过 Material + 物品内部ID匹配）
        String categoryId = getCategoryIdFromInventoryName(inventoryName);
        if (categoryId != null) {
            ShopCategory category = shopLogic.getCategory(categoryId);
            if (category != null && currentItem != null && currentItem.getType() != Material.AIR) {
                Material clickedType = currentItem.getType();
                for (ShopItem item : category.getItems()) {
                    if (item.getDisplayItem().getType() == clickedType) {
                        buyShopItem(player, category, item);
                        return;
                    }
                }
            }
        }
        player.openInventory(displayShopGUI(player, categoryId, 1));
    }

    private String getCategoryIdFromInventoryName(String inventoryName) {
        ShopLogic shopLogic = plugin.getShopLogic();
        for (ShopCategory cat : shopLogic.getCategories()) {
            if (stripFormatting(inventoryName).contains(stripFormatting(cat.getName()))) {
                return cat.getId();
            }
        }
        return null;
    }

    private void buyShopItem(Player player, ShopCategory category, ShopItem item) {
        PlayerInfo pi = plugin.getPlayerInfo(player);
        UUID uuid = player.getUniqueId();
        ShopLogic shopLogic = plugin.getShopLogic();
        PlayerShopData data = shopLogic.getPlayerData(uuid);

        IslandInfo islandInfo = plugin.getIslandInfo(player);
        if (islandInfo.getLevel() < item.getRequiredLevel()) {
            player.sendMessage(tr("\u00a7c你的岛屿等级不足！需要 {0} 级", item.getRequiredLevel()));
            return;
        }

        for (String challengeId : item.getRequiredChallenges()) {
            if (pi.checkChallenge(challengeId) == 0) {
                // 尝试根据挑战ID获取其在配置文件中的显示名称
                String displayName = challengeId;
                Challenge challenge = challengeLogic.getChallenge(challengeId);
                if (challenge != null) {
                    displayName = stripFormatting(challenge.getDisplayName()); // 获取可读的显示名
                }
                player.sendMessage(tr("\u00a7c你需要先完成挑战: {0}", displayName));
                return;
            }
        }

        if (data.getBuyCount(item.getId()) >= item.getMaxBuys()) {
            player.sendMessage(tr("\u00a7c已达到最大购买数量！"));
            return;
        }

        int actualPrice = shopLogic.getActualPrice(item, uuid);
        plugin.getHookManager().getEconomyHook().ifPresent(hook -> {
            double balance = hook.getBalance(player);
            if (balance < actualPrice) {
                player.sendMessage(tr("\u00a7c余额不足！需要 {0,number,#}，你只有 {1,number,#}", actualPrice, (int) balance));
                return;
            }
            hook.withdrawPlayer(player, actualPrice);

            // === 物品发放（完全复刻 ChallengeLogic.giveReward） ===

            // 方式一：items 列表
            if (!item.getItemRewards().isEmpty()) {
                List<ItemStackUtil.ItemProbability> probs = ItemStackUtil.createItemsWithProbability(item.getItemRewards());
                List<ItemStack> rewardItems = new ArrayList<>();
                for (ItemStackUtil.ItemProbability prob : probs) {
                    rewardItems.add(prob.item().clone());
                }
                HashMap<Integer, ItemStack> leftOvers = player.getInventory().addItem(rewardItems.toArray(new ItemStack[0]));
                for (ItemStack leftOver : leftOvers.values()) {
                    player.getWorld().dropItem(player.getLocation(), leftOver);
                }
                if (!leftOvers.isEmpty()) {
                    player.sendMessage(tr("\u00a7e你的背包满了，部分物品掉在地上。"));
                }
            }

            // 方式二：commands 列表
            for (String cmd : item.getCommands()) {
                plugin.execCommand(player, cmd, true);
            }

            data.incrementBuyCount(item.getId());
            shopLogic.savePlayerData(uuid);  // ← 新增：保存到文件
            double remaining = hook.getBalance(player);
            player.sendMessage(tr("\u00a7a购买成功！花费 {0,number,#}，余额 {1,number,#}", actualPrice, (int) remaining));
            player.sendMessage(tr("\u00a7e剩余购买次数: {0}/{1}",
                item.getMaxBuys() - data.getBuyCount(item.getId()), item.getMaxBuys()));
        });

        player.openInventory(displayShopGUI(player, category.getId(), 1));
    }
}
