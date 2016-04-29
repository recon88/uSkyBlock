package us.talabrek.ultimateskyblock.imports.playerdb;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.QueryIterator;
import dk.lockfuglsang.minecraft.file.FileUtil;
import us.talabrek.ultimateskyblock.database.pojo.OldNamePOJO;
import us.talabrek.ultimateskyblock.database.pojo.PlayerPOJO;
import us.talabrek.ultimateskyblock.imports.USBImporter;
import us.talabrek.ultimateskyblock.mojang.NameUUIDConsumer;
import us.talabrek.ultimateskyblock.mojang.ProgressCallback;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.TimeUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

/**
 * Imports all players into the PlayerDB
 */
public class PlayerDBImporter implements USBImporter, ProgressCallback, NameUUIDConsumer {
    private static final Logger log = Logger.getLogger(PlayerDBImporter.class.getName());
    private static final long FEEDBACK_EVERY_MS = 5000;

    private long lastFeedback;
    private long tStart;
    private uSkyBlock plugin;

    @Override
    public String getName() {
        return "playerdb";
    }

    @Override
    public boolean importFile(uSkyBlock plugin, File file) {
        return false;
    }

    @Override
    public int importOrphans(uSkyBlock plugin, File configFolder) {
        return 0;
    }

    @Override
    public File[] getFiles(uSkyBlock plugin) {
        return new File[0];
    }

    @Override
    public void completed(uSkyBlock plugin, int success, int failed) {
        this.plugin = plugin;
        tStart = System.currentTimeMillis();
        String[] playerFiles = new File(plugin.getDataFolder(), "players").list(FileUtil.createYmlFilenameFilter());
        List<String> names = new ArrayList<>(playerFiles.length);
        // All player files
        for (String filename : playerFiles) {
            names.add(FileUtil.getBasename(filename));
        }
        // Filter out those already known
        EbeanServer database = plugin.getDatabase();
        try (QueryIterator<PlayerPOJO> iterate = database.find(PlayerPOJO.class).findIterate()) {
            for (;iterate.hasNext();) {
                PlayerPOJO pojo = iterate.next();
                names.remove(pojo.getName());
            }
        }
        try (QueryIterator<OldNamePOJO> iterate = database.find(OldNamePOJO.class).findIterate()) {
            for (;iterate.hasNext();) {
                names.remove(iterate.next().getName());
            }
        }
        // Query Bukkit API
        try {
            log.info(tr("Found {0} names to lookup from Mojang", names.size()));
            lastFeedback = System.currentTimeMillis();
            plugin.getMojangAPI().fetchUUIDs(names, this, this);
            long t2 = System.currentTimeMillis();
            log.info(tr("Done updating the PlayerDB in {0}", TimeUtil.millisAsString(t2 - tStart)));
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to import player-names", e);
        }
    }

    @Override
    public void progress(int progress, int failed, int total, String message) {
        long t = System.currentTimeMillis();
        if (lastFeedback < (t-FEEDBACK_EVERY_MS)) {
            lastFeedback = t;
            String msg = tr("Import progress ({0}): {1}/{3} ({4,number,##}%, {2} failed, elapsed {5})",
                    message,
                    progress, failed, total,
                    (100d * progress / total),
                    TimeUtil.millisAsString(t - tStart), message);
            log.info(msg);
        }
    }

    @Override
    public void complete(boolean success) {
        // Nothing for now
    }

    @Override
    public void error(String message) {
        log.info(tr("Error: {0}", message));
    }

    @Override
    public void success(Map<String, UUID> nameMap) {
        EbeanServer database = plugin.getDatabase();
        for (Map.Entry<String, UUID> entry : nameMap.entrySet()) {
            PlayerPOJO pojo = database.find(PlayerPOJO.class)
                    .where().eq("id", entry.getValue())
                    .findUnique();
            if (pojo == null) {
                pojo = new PlayerPOJO();
                pojo.setId(entry.getValue());
            } else if (!pojo.getName().equals(entry.getKey())) {
                OldNamePOJO oldName = new OldNamePOJO();
                oldName.setPlayer(pojo);
                oldName.setName(pojo.getName());
                pojo.getNames().add(oldName);
            }
            pojo.setName(entry.getKey());
            database.save(pojo);
        }
    }

    @Override
    public void renamed(String oldName, String newName, UUID id) {
        // TODO: 03/01/2016 - R4zorax: Do something about the newName
        EbeanServer database = plugin.getDatabase();
        PlayerPOJO pojo = database.find(PlayerPOJO.class)
                .where().eq("id", id)
                .findUnique();
        if (pojo == null) {
            pojo = new PlayerPOJO();
            pojo.setName(newName);
            pojo.setId(id);
        } else {
            OldNamePOJO namePOJO = new OldNamePOJO();
            namePOJO.setName(oldName);
            namePOJO.setPlayer(pojo);
            pojo.setName(newName);
            pojo.getNames().add(namePOJO);
        }
        database.save(pojo);
    }

    @Override
    public void unknown(List<String> unknownNames) {
        // TODO: 03/01/2016 - R4zorax: Do something here (i.e. quarantine playerinfo files).
    }
}
