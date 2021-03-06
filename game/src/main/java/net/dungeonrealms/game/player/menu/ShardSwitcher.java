package net.dungeonrealms.game.player.menu;

import net.dungeonrealms.DungeonRealms;
import net.dungeonrealms.GameAPI;
import net.dungeonrealms.common.game.database.DatabaseAPI;
import net.dungeonrealms.common.game.database.data.EnumData;
import net.dungeonrealms.common.game.database.player.rank.Rank;
import net.dungeonrealms.common.game.menu.AbstractMenu;
import net.dungeonrealms.common.game.menu.gui.GUIButtonClickEvent;
import net.dungeonrealms.common.game.menu.gui.VolatileGUI;
import net.dungeonrealms.common.game.menu.item.GUIButton;
import net.dungeonrealms.common.game.updater.Updater;
import net.dungeonrealms.common.network.ShardInfo;
import net.dungeonrealms.common.network.bungeecord.BungeeServerInfo;
import net.dungeonrealms.common.network.bungeecord.BungeeServerTracker;
import net.dungeonrealms.game.miscellaneous.Cooldown;
import net.dungeonrealms.game.player.combat.CombatLog;
import net.dungeonrealms.game.title.TitleAPI;
import net.dungeonrealms.game.world.realms.Realms;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Class written by APOLLOSOFTWARE.IO on 6/18/2016
 */
public class ShardSwitcher extends AbstractMenu implements VolatileGUI {

    public static Map<String, Map<String, GUIButton>> CACHED_PING_SHARD_BUTTONS = new HashMap<>();
    private final String playerHostName;

    public ShardSwitcher(Player player) {
        super(DungeonRealms.getInstance(), "DungeonRealms Shards", AbstractMenu.round(getFilteredServers().size()), player.getUniqueId());
        setDestroyOnExit(true);

        this.playerHostName = player.getAddress().getHostName();

        List<BungeeServerInfo> servers = new ArrayList<>(getFilteredServers().values());

        Collections.sort(servers, (o1, o2) -> {
            int o1num = Integer.parseInt(o1.getServerName().substring(o1.getServerName().length() - 1));
            int o2num = Integer.parseInt(o2.getServerName().substring(o2.getServerName().length() - 1));

            if (!o1.getServerName().contains("us"))
                return -1;

            return o1num - o2num;
        });

        // DISPLAY AVAILABLE SHARDS //
        for (BungeeServerInfo info : servers) {
            String bungeeName = info.getServerName();
            String shardID = ShardInfo.getByPseudoName(bungeeName).getShardID();

            // Do not show YT / CS shards unless they've got the appropriate permission to see them.
            if ((shardID.contains("YT") && !Rank.isYouTuber(player)) || (shardID.contains("CS") && !Rank.isSupport(player)) || (shardID.equalsIgnoreCase("US-0") && !Rank.isGM(player)))
                continue;

            GUIButton button = new GUIButton(getShardItem(shardID)) {

                @Override
                public void action(GUIButtonClickEvent event) throws Exception {
                    Player player = event.getWhoClicked();
                    player.closeInventory();

                    if (CombatLog.isInCombat(player)) {
                        player.sendMessage(ChatColor.RED + "You cannot transfer shards while in combat.");
                        return;
                    }
                    if (Cooldown.hasCooldown(player.getUniqueId()))
                        return;

                    Cooldown.addCooldown(player.getUniqueId(), 1000L);

                    if (shardID.contains("SUB") && !Rank.isSubscriber(player)) {
                        player.sendMessage(new String[]{
                                ChatColor.RED + "This is a " + ChatColor.BOLD + ChatColor.UNDERLINE + "SUBSCRIBER ONLY" + ChatColor.RED + " shard!",
                                ChatColor.RED + "You can subscribe at: " + ChatColor.UNDERLINE + "http://shop.dungeonrealms.org"
                        });
                        return;
                    } else if ((shardID.contains("YT") && !Rank.isYouTuber(player)) || (shardID.contains("CS") && !Rank.isSupport(player))) {
                        player.sendMessage(ChatColor.RED + "You are " + ChatColor.BOLD + ChatColor.UNDERLINE + "NOT" + ChatColor.RED + " authorized to connect to this shard.");
                        return;
                    }

                    TitleAPI.sendTitle(player, 1, 300, 1, ChatColor.YELLOW + "Loading Shard - " + ChatColor.BOLD + shardID + ChatColor.YELLOW + " ...", ChatColor.GRAY.toString() + "Do not disconnect");

                    player.sendMessage(ChatColor.GRAY + "Retrieving relevant server information...");
                    player.sendMessage(" ");
                    player.sendMessage("                     " + ChatColor.YELLOW + "Loading Shard - " + ChatColor.BOLD + shardID + ChatColor.YELLOW + " ...");
                    player.sendMessage(ChatColor.GRAY + "Your current game session has been paused while you are transferred.");

                    final Location startingLocation = player.getLocation();

                    if (GameAPI.isInSafeRegion(startingLocation)) {
                        GameAPI.moveToShard(player, bungeeName);
                        return;
                    }

                    final int[] taskTimer = {5};

                    new Updater(DungeonRealms.getInstance(), 20L, null) {
                        @Override
                        public void run() {
                            if (taskTimer[0] <= 0) {
                                return;
                            }

                            if (!player.isOnline()) {
                                cancel();
                                return;
                            }

                            if (startingLocation.distanceSquared(player.getLocation()) >= 2.0D || CombatLog.isInCombat(player)) {
                                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "/shard - CANCELLED");
                                TitleAPI.sendTitle(player, 1, 1, 1, "");
                                GameAPI.getGamePlayer(player).setAbleToDrop(true);
                                player.removeMetadata("sharding", DungeonRealms.getInstance());
                                cancel();
                                return;
                            }

                            player.sendMessage(ChatColor.RED + "Transferring your character data ... " + net.md_5.bungee.api.ChatColor.BOLD + taskTimer[0] + "s");
                            taskTimer[0]--;

                            if (taskTimer[0] == 0) {
                                GameAPI.moveToShard(player, bungeeName);
                                cancel();
                            }
                        }
                    };
                }
            };

            List<String> lore = new ArrayList<>();

            if (!getServerType(shardID).equals(""))
                lore.add(ChatColor.RED.toString() + ChatColor.ITALIC + getServerType(shardID));


            //final int slot = getServerType(shardID).equals("") ? getSize() : Math.min(getInventorySize(), getInventorySize() - 1) - (getSize() - getNormalServers());

            lore.add(ChatColor.GREEN + "This shard is online!");
            lore.add(ChatColor.WHITE + "Click here to load your");
            lore.add(ChatColor.WHITE + "character onto this shard.");
            lore.add(" ");

            if (info.getMotd1().contains(","))
                lore.add(ChatColor.GRAY + "Load: " + info.getMotd1().split(",")[1].replace("}", "").replace("\"", ""));

            lore.add(ChatColor.GRAY + "Online: " + info.getOnlinePlayers() + "/" + info.getMaxPlayers());

            button.setDisplayName(getShardColour(shardID) + ChatColor.BOLD.toString() + shardID);
            button.setLore(lore);

//            button.setSlot(slot);
//            button.setGui(this);
//
//            if (!CACHED_PING_SHARD_BUTTONS.containsKey(playerHostName)) {
//                Map<String, GUIButton> map = new HashMap<>();
//                map.put(bungeeName, button);
//
//                CACHED_PING_SHARD_BUTTONS.put(playerHostName, map);
//            } else {
//                Map<String, GUIButton> map = CACHED_PING_SHARD_BUTTONS.get(playerHostName);
//                map.put(bungeeName, button);
//
//                CACHED_PING_SHARD_BUTTONS.put(playerHostName, map);
//            }
//
//            NetworkChannelListener.getInstance().sendNetworkMessage("DungeonRealms", "Ping", playerHostName);

            set(getSize(), button);
        }
    }


    private int getNormalServers() {
        int count = 0;

        for (String bungeeName : getFilteredServers().keySet()) {
            String shardID = ShardInfo.getByPseudoName(bungeeName).getShardID();
            if (getServerType(shardID).equals(""))
                count++;
        }

        return count;
    }

    /**
     * Returns the material associated with a shard.
     *
     * @param shardID
     * @return Material
     */
    private ItemStack getShardItem(String shardID) {
        shardID = shardID.toUpperCase();

        if (shardID.equals("US-0")) return new ItemStack(Material.DIAMOND);
        else if (shardID.startsWith("CS-")) return new ItemStack(Material.PRISMARINE_SHARD);
        else if (shardID.startsWith("YT-")) return new ItemStack(Material.GOLD_NUGGET);
        else if (shardID.startsWith("BR-")) return new ItemStack(Material.SAPLING, 1, (byte) 3);
        else if (shardID.startsWith("SUB-")) return new ItemStack(Material.EMERALD);

        return new ItemStack(Material.END_CRYSTAL);
    }

    /**
     * Returns the chat colour associated with a shard.
     *
     * @param shardID
     * @return ChatColor
     */
    private ChatColor getShardColour(String shardID) {
        shardID = shardID.toUpperCase();

        if (shardID.equals("US-0")) return ChatColor.AQUA;
        else if (shardID.startsWith("CS-")) return ChatColor.BLUE;
        else if (shardID.startsWith("YT-")) return ChatColor.RED;
        else if (shardID.startsWith("SUB-")) return ChatColor.GREEN;

        return ChatColor.YELLOW;
    }

    private static Map<String, BungeeServerInfo> getFilteredServers() {
        Map<String, BungeeServerInfo> filteredServers = new HashMap<>();

        for (Entry<String, BungeeServerInfo> e : BungeeServerTracker.getTrackedServers().entrySet()) {
            String bungeeName = e.getKey();
            if (ShardInfo.getByPseudoName(bungeeName) == null) continue;

            String shardID = ShardInfo.getByPseudoName(bungeeName).getShardID();
            BungeeServerInfo info = e.getValue();

            if (!info.isOnline() || shardID.equals(DungeonRealms.getInstance().shardid) || info.getOnlinePlayers() >= info.getMaxPlayers() || info.getMotd1().contains("offline"))
                continue;

            filteredServers.put(bungeeName, info);
        }

        return filteredServers;
    }

    public String getServerType(String shardID) {
        if (shardID.contains("SUB")) return "Subscribers Only";
        if (shardID.contains("YT")) return "YouTubers Only";
        if (shardID.contains("BR")) return "Brazilian Shard";
        if (shardID.contains("RP")) return "Role-playing Shard";
        if (shardID.contains("CS")) return "Support Agents Only";
        return "";
    }

    @Override
    public void open(Player player) {
        if (getSize() == 0) {
            player.sendMessage(ChatColor.RED + "Unable to find an available shard for you.");
            return;
        }

        if (Realms.getInstance().getRealm(player.getWorld()) != null) {
            player.sendMessage(ChatColor.RED + "You cannot shard in a realm.");
            return;
        }

        long lastShardTransfer = (long) DatabaseAPI.getInstance().getData(EnumData.LAST_SHARD_TRANSFER, player.getUniqueId());

        if (lastShardTransfer != 0 && !Rank.isGM(player)) {
            if (GameAPI.isInSafeRegion(player.getLocation()) && (System.currentTimeMillis() - lastShardTransfer) < 30000) {
                player.sendMessage(ChatColor.RED + "You must wait 30 seconds before you can transfer between shards.");
                return;
            } else if (!GameAPI.isInSafeRegion(player.getLocation()) && (System.currentTimeMillis() - lastShardTransfer) < 300000) {
                player.sendMessage(ChatColor.RED + "You must wait 5 minutes before you can transfer between shards.");
                return;
            }
        }

        if (CombatLog.isInCombat(player)) {
            player.sendMessage(ChatColor.RED + "You cannot transfer shards while in combat.");
            return;
        }


        player.openInventory(inventory);
    }

    @Override
    public void onDestroy(Event event) {
        CACHED_PING_SHARD_BUTTONS.remove(playerHostName);
    }

}
