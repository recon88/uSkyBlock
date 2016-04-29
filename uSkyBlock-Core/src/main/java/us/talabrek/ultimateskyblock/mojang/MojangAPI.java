/**
 * Features include:
 * <pre>
 *     * Mojang API auto-throttling when receiving HTTP 429 (TOO MANY REQUESTS)
 *     * Incremental consumer strategy (consume each response, instead of blocking for everything).
 * </pre>
 *
 * Inspired by https://gist.github.com/evilmidget38/26d70114b834f71fb3b4
 */
package us.talabrek.ultimateskyblock.mojang;


import com.google.common.base.Charsets;
import org.bukkit.Bukkit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import us.talabrek.ultimateskyblock.util.TimeUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

public class MojangAPI {
    private static final int PROFILES_PER_REQUEST = 100;
    private static final String PROFILE_URL = "https://api.mojang.com/profiles/minecraft";
    private static final String NAME_URL = "https://api.mojang.com/users/profiles/minecraft/{0}?at=0";
    private static final int MAX_RETRIES = 3;
    private static final long THROTTLE = 100L;
    private static final int TOO_MANY_REQUESTS = 429;

    private final JSONParser jsonParser = new JSONParser();

    private static final int HISTORY_SIZE = 200;
    private static final long[] HISTORY = new long[HISTORY_SIZE];
    private static int historyIndex = -1;
    private static long adaptiveThrottle;

    public void fetchUUIDs(List<String> names, NameUUIDConsumer consumer, ProgressCallback callback) {
        if (Bukkit.getOnlineMode()) {
            try {
                fetchCurrent(names, consumer, callback);
                callback.complete(true);
            } catch (Exception e) {
                callback.complete(false);
            }
        } else {
            fetchOfflineMode(names, consumer, callback);
        }
    }

    private void fetchOfflineMode(List<String> names, NameUUIDConsumer consumer, ProgressCallback callback) {
        int requests = (int) Math.ceil(names.size() / PROFILES_PER_REQUEST);
        int failed = 0;
        for (int i = 0; i < requests; i++) {
            int fromIndex = i * PROFILES_PER_REQUEST;
            int toIndex = Math.min((i + 1) * 100, names.size());
            List<String> segment = names.subList(fromIndex, toIndex);
            Map<String, UUID> tempMap = new HashMap<>();

            for (String name : segment) {
                // TODO: 04/01/2016 - R4zorax: Name validation as in Spigot?
                // Spigot way: Note! Case sensitive (online mode isn't).
                tempMap.put(name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(Charsets.UTF_8)));
            }
            int missing = segment.size() - tempMap.size();
            consumer.success(tempMap);
            if (missing > 0) {
                List<String> unknown = new ArrayList<>(segment);
                unknown.removeAll(tempMap.keySet());
                consumer.unknown(unknown);
            }
            if (callback != null) {
                failed += missing;
                callback.progress(toIndex, failed, names.size(), "OfflineMode");
            }
        }
        if (callback != null) {
            callback.complete(true);
        }
    }

    private void fetchCurrent(List<String> names, NameUUIDConsumer consumer, ProgressCallback callback) throws Exception {
        int requests = (int) Math.ceil(names.size() / PROFILES_PER_REQUEST);
        int failed = 0;
        for (int i = 0; i < requests; i++) {
            int fromIndex = i * PROFILES_PER_REQUEST;
            int toIndex = Math.min((i + 1) * 100, names.size());
            List<String> segment = names.subList(fromIndex, toIndex);
            HttpURLConnection connection = createPostConnection();
            String body = JSONArray.toJSONString(segment);
            writeBody(connection, body);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Map<String, UUID> tempMap = new HashMap<>();
                JSONArray array = (JSONArray) jsonParser.parse(new InputStreamReader(connection.getInputStream()));
                for (Object profile : array) {
                    JSONObject jsonProfile = (JSONObject) profile;
                    String id = (String) jsonProfile.get("id");
                    String name = (String) jsonProfile.get("name");
                    UUID uuid = getUUID(id);
                    tempMap.put(name, uuid);
                }
                int missing = segment.size() - tempMap.size();
                consumer.success(tempMap);
                if (missing > 0) {
                    List<String> unknown = new ArrayList<>(segment);
                    unknown.removeAll(tempMap.keySet());
                    consumer.unknown(unknown);
                }
                if (callback != null) {
                    failed += missing;
                    callback.progress(toIndex, failed, names.size(), "Online");
                }
            } else if (responseCode == TOO_MANY_REQUESTS) {
                i--;
            } else {
                i--; // retry segment
                if (callback != null) {
                    callback.error(connection.getResponseMessage());
                }
            }
            throttle(responseCode, i != requests - 1, callback);
        }
    }

    private void throttle(int responseCode, boolean hasMore, ProgressCallback callback) throws InterruptedException {
        if (responseCode == HttpURLConnection.HTTP_OK) {
            addHistory(System.currentTimeMillis());
        }
        if (!hasMore) {
            return;
        }
        if (responseCode == TOO_MANY_REQUESTS) {
            adaptiveThrottle = recalculateThrottle();
            callback.error(tr("Too many requests for Mojangs API, sleeping {0}", TimeUtil.millisAsShort(adaptiveThrottle)));
            Thread.sleep(adaptiveThrottle);
        } else if (adaptiveThrottle > 0) {
            adaptiveThrottle *= 0.9; // Slowly decrease throttling
            Thread.sleep(adaptiveThrottle);
        }
    }

    private static synchronized void addHistory(long timeMillis) {
        historyIndex = (historyIndex + 1) % HISTORY_SIZE; // rollover
        HISTORY[historyIndex] = timeMillis;
    }

    private static synchronized long getHistoryAverage() {
        int index = (historyIndex + 1) % HISTORY_SIZE;
        long now = System.currentTimeMillis();
        if (HISTORY[index] == 0 && HISTORY[0] != 0 && historyIndex != 0) {
            // No full history yet
            return (now - HISTORY[0]) / historyIndex;
        } else if (HISTORY[index] != 0) {
            return (now - HISTORY[index]) / HISTORY_SIZE;
        }
        return THROTTLE;
    }

    /**
     * Calculates the number of ms to sleep, to avoid too many requests from the Mojang API
     */
    private long recalculateThrottle() {
        long historyAverage = getHistoryAverage();
        if (adaptiveThrottle < historyAverage) {
            adaptiveThrottle = historyAverage;
        } else {
            adaptiveThrottle *= 1.5;
        }
        return adaptiveThrottle;
    }

    private static HttpURLConnection createGetConnection(String name) throws IOException {
        URL url = new URL(MessageFormat.format(NAME_URL, name));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(false);
        return connection;
    }

    private static HttpURLConnection createPostConnection() throws Exception {
        URL url = new URL(PROFILE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, String body) throws Exception {
        OutputStream stream = connection.getOutputStream();
        stream.write(body.getBytes());
        stream.flush();
        stream.close();
    }

    private static UUID getUUID(String id) {
        return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20, 32));
    }
}