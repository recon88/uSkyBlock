package us.talabrek.ultimateskyblock.uuid;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.database.USBDatabase;
import us.talabrek.ultimateskyblock.database.pojo.OldNamePOJO;
import us.talabrek.ultimateskyblock.database.pojo.PlayerPOJO;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 */
public class EBeanPlayerDB implements PlayerDB {
    private static final Logger log = Logger.getLogger(EBeanPlayerDB.class.getName());

    private final uSkyBlock plugin;
    private final USBDatabase database;

    public EBeanPlayerDB(uSkyBlock plugin, USBDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public UUID getUUIDFromName(String name) {
        List<Object> uuids = database.getDatabase().find(PlayerPOJO.class)
                .where().eq("name", name)
                .findIds();
        if (uuids != null && !uuids.isEmpty() && uuids.get(0) instanceof UUID) {
            return (UUID) uuids.get(0);
        }
        return update(Bukkit.getOfflinePlayer(name)).getId();
    }

    private PlayerPOJO update(OfflinePlayer offlinePlayer) {
        PlayerPOJO pojo = getByUUID(offlinePlayer.getUniqueId());
        if (pojo == null) {
            pojo = new PlayerPOJO();
            pojo.setId(offlinePlayer.getUniqueId());
        }
        pojo.setName(offlinePlayer.getName());
        database.getDatabase().save(pojo);
        return pojo;
    }

    @Override
    public String getName(UUID uuid) {
        PlayerPOJO pojo = getByUUID(uuid);
        if (pojo != null) {
            return pojo.getName();
        }
        return update(Bukkit.getOfflinePlayer(uuid)).getName();
    }

    private PlayerPOJO getByUUID(UUID uuid) {
        return database.getDatabase().find(PlayerPOJO.class)
                    .where().eq("id", uuid)
                    .findUnique();
    }

    @Override
    public String getDisplayName(UUID uuid) {
        PlayerPOJO pojo = getByUUID(uuid);
        if (pojo != null) {
            return pojo.getDisplayName();
        }
        return update(Bukkit.getOfflinePlayer(uuid)).getDisplayName();
    }

    @Override
    public String getDisplayName(String playerName) {
        List<PlayerPOJO> pojos = database.getDatabase().find(PlayerPOJO.class)
                .where().eq("name", playerName)
                .findList();
        if (pojos != null && !pojos.isEmpty()) {
            return pojos.get(0).getDisplayName();
        }
        return update(Bukkit.getOfflinePlayer(playerName)).getDisplayName();
    }

    @Override
    public void updatePlayer(Player player) {
        PlayerPOJO pojo = database.getDatabase().find(PlayerPOJO.class)
                .where().eq("id", player.getUniqueId())
                .findUnique();
        if (pojo == null) {
            pojo = new PlayerPOJO();
            pojo.setId(player.getUniqueId());
            log.fine("Added " + player + " to PlayerDB");
        } else if (!pojo.getName().equals(player.getName())) {
            // Name changed
            OldNamePOJO oldName = new OldNamePOJO();
            oldName.setName(pojo.getName());
            oldName.setPlayer(pojo);
            database.getDatabase().save(oldName);
            // Fire event
            final AsyncPlayerNameChangedEvent event = new AsyncPlayerNameChangedEvent(player, pojo.getName(), player.getName());
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    plugin.getServer().getPluginManager().callEvent(event);
                }
            });
        }
        pojo.setDisplayName(player.getDisplayName());
        pojo.setName(player.getName());
        pojo.setLastActiveInSky(System.currentTimeMillis());
        database.getDatabase().save(pojo);
    }
}
