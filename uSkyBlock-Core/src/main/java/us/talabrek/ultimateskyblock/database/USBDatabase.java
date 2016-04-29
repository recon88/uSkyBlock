package us.talabrek.ultimateskyblock.database;

import org.bukkit.configuration.file.FileConfiguration;
import us.talabrek.ultimateskyblock.database.pojo.IslandLogPOJO;
import us.talabrek.ultimateskyblock.database.pojo.IslandPOJO;
import us.talabrek.ultimateskyblock.database.pojo.IslandPermissionPOJO;
import us.talabrek.ultimateskyblock.database.pojo.OldNamePOJO;
import us.talabrek.ultimateskyblock.database.pojo.PlayerInfoPOJO;
import us.talabrek.ultimateskyblock.database.pojo.PlayerPOJO;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class USBDatabase extends MyDatabase {
    private final uSkyBlock plugin;
    private List<Class<?>> pojos;

    /**
     * Create an instance of USBDatabase
     *
     * @param plugin Plugin instancing this database
     */
    public USBDatabase(uSkyBlock plugin) {
        super(plugin);
        this.plugin = plugin;
        pojos = new ArrayList<>();
        pojos.add(PlayerPOJO.class);
        pojos.add(OldNamePOJO.class);
        pojos.add(PlayerInfoPOJO.class);
        pojos.add(IslandPOJO.class);
        pojos.add(IslandLogPOJO.class);
        pojos.add(IslandPermissionPOJO.class);
    }

    public void init() {
        FileConfiguration config = plugin.getConfig();
        String driver = config.getString("db.driver", "org.sqlite.JDBC");
        String url = config.getString("db.url", "jdbc:sqlite:{DIR}{NAME}.db");
        String username = config.getString("db.username", "uskyblock");
        String password = config.getString("db.password", "uskyblock");
        // TODO: 31/12/2015 - R4zorax: Handle rebuild in a sensible manner
        initializeDatabase(driver, url, username, password, "SERIALIZABLE", false, false);
    }

    @Override
    public List<Class<?>> getDatabaseClasses() {
        return pojos;
    }
}
