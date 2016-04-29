package us.talabrek.ultimateskyblock.player;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.uuid.PlayerDB;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

/**
 * Holds the active players
 */
public class PlayerLogic {
    private static final Logger log = Logger.getLogger(PlayerLogic.class.getName());
    private final LoadingCache<UUID, PlayerInfo> playerCache;
    private final uSkyBlock plugin;
    private final PlayerDB playerDB;
    private final BukkitTask saveTask;

    public PlayerLogic(uSkyBlock plugin, final PlayerDB playerDB) {
        this.plugin = plugin;
        this.playerDB = playerDB;
        playerCache = CacheBuilder
                .from(plugin.getConfig().getString("options.advanced.playerCache", "maximumSize=200,expireAfterWrite=15m,expireAfterAccess=10m"))
                .removalListener(new RemovalListener<UUID, PlayerInfo>() {
                    @Override
                    public void onRemoval(RemovalNotification<UUID, PlayerInfo> removal) {
                        log.fine("Removing player-info for " + removal.getKey() + " from cache");
                        PlayerInfo playerInfo = removal.getValue();
                        if (playerInfo.isDirty()) {
                            playerInfo.saveToFile();
                        }
                    }
                })
                .build(new CacheLoader<UUID, PlayerInfo>() {
                           @Override
                           public PlayerInfo load(UUID s) throws Exception {
                               log.fine("Loading player-info from " + s + " into cache!");
                               return loadPlayerData(s);
                           }
                       }
                );
        int every = plugin.getConfig().getInt("options.advanced.player.saveEvery", 20 * 60 * 2);
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                saveDirtyToFiles();
            }
        }, every, every);
    }

    private void saveDirtyToFiles() {
        // asMap.values() should NOT touch the cache.
        for (PlayerInfo pi : playerCache.asMap().values()) {
            if (pi.isDirty()) {
                pi.saveToFile();
            }
        }
    }

    public PlayerInfo loadPlayerData(String playerName) {
        return loadPlayerData(playerDB.getUUIDFromName(playerName), playerName);
    }

    public PlayerInfo loadPlayerData(UUID uuid) {
        return loadPlayerData(uuid, playerDB.getName(uuid));
    }

    private PlayerInfo loadPlayerData(UUID playerUUID, String playerName) {
        if (playerUUID == null || playerName == null) {
            return null;
        }
        log.log(Level.FINER, "Loading player data for " + playerName);

        final PlayerInfo playerInfo = new PlayerInfo(playerName, playerUUID);

        final Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            if (playerInfo.getHasIsland()) {
                IslandInfo islandInfo = plugin.getIslandInfo(playerInfo);
                if (islandInfo != null) {
                    islandInfo.updatePermissionPerks(onlinePlayer, plugin.getPerkLogic().getPerk(onlinePlayer));
                }
            }
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (playerInfo.getHasIsland()) {
                                WorldGuardHandler.protectIsland(onlinePlayer, playerInfo);
                                plugin.getIslandLogic().clearFlatland(onlinePlayer, playerInfo.getIslandLocation(), 400);
                            }
                            if (plugin.isSkyAssociatedWorld(onlinePlayer.getWorld()) && !plugin.playerIsOnIsland(onlinePlayer)) {
                                // Check if banned
                                String islandName = WorldGuardHandler.getIslandNameAt(onlinePlayer.getLocation());
                                IslandInfo islandInfo = plugin.getIslandInfo(islandName);
                                if (islandInfo != null && islandInfo.isBanned(onlinePlayer)) {
                                    onlinePlayer.sendMessage(new String[]{
                                            tr("\u00a7eYou have been §cBANNED§e from {0}§e''s island.", islandInfo.getLeader()),
                                            tr("\u00a7eSending you to spawn.")
                                    });
                                    plugin.spawnTeleport(onlinePlayer, true);
                                } else if (islandInfo != null && islandInfo.isLocked()) {
                                    onlinePlayer.sendMessage(new String[]{
                                            tr("\u00a7eThe island has been §cLOCKED", islandInfo.getLeader()),
                                            tr("\u00a7eSending you to spawn.")
                                    });
                                    plugin.spawnTeleport(onlinePlayer, true);
                                }
                            }
                        }
                    }
            );
        }
        return playerInfo;
    }

    public PlayerInfo getPlayerInfo(Player player) {
        return getPlayerInfo(player.getName());
    }

    public PlayerInfo getPlayerInfo(String playerName) {
        UUID uuid = playerDB.getUUIDFromName(playerName);
        if (uuid == null || playerName == null) {
            return null;
        }
        // Do not return anything if it is loading.
        try {
            return playerCache.get(uuid);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e); // Escalate - we need it in the server log
        }
    }

    public void loadPlayerDataAsync(final Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                playerCache.refresh(player.getUniqueId());
            }
        });
    }

    public void removeActivePlayer(PlayerInfo pi) {
        playerCache.invalidate(pi.getPlayerName());
    }

    public void shutdown() {
        saveTask.cancel();
        flushCache();
    }

    public long flushCache() {
        long size = playerCache.size();
        playerCache.invalidateAll();
        return size;
    }
}
