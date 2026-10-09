package dev.afkpoints;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite storage. All DB access runs on one dedicated thread, so writes are
 * ordered and the main thread never blocks on disk. Balances live in a cache
 * while the player is online. Mutate balances from the main thread only.
 */
public final class Storage {
    private final AfkPointsPlugin plugin;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "AfkPoints-IO"));
    private Connection conn;

    Storage(AfkPointsPlugin plugin) {
        this.plugin = plugin;
    }

    void init() throws SQLException {
        plugin.getDataFolder().mkdirs();
        conn = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "data.db").getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS balances (uuid TEXT PRIMARY KEY, points INTEGER NOT NULL)");
        }
    }

    /** Blocks the calling thread until loaded. Safe to call from the async pre-login thread. */
    void load(UUID id) {
        try {
            long value = io.submit(() -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT points FROM balances WHERE uuid = ?")) {
                    ps.setString(1, id.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getLong(1) : 0L;
                    }
                }
            }).get();
            cache.put(id, value);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load balance for " + id, e);
        }
    }

    void unload(UUID id) {
        cache.remove(id);
    }

    boolean isLoaded(UUID id) {
        return cache.containsKey(id);
    }

    public long get(UUID id) {
        return cache.getOrDefault(id, 0L);
    }

    public void set(UUID id, long value) {
        long v = Math.max(0L, value);
        cache.put(id, v);
        persist(id, v);
    }

    public void add(UUID id, long delta) {
        set(id, get(id) + delta);
    }

    /** Returns false (and changes nothing) if the player lacks the points. */
    public boolean take(UUID id, long amount) {
        if (!isLoaded(id) || get(id) < amount) return false;
        set(id, get(id) - amount);
        return true;
    }

    private void persist(UUID id, long value) {
        io.execute(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO balances(uuid, points) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET points = excluded.points")) {
                ps.setString(1, id.toString());
                ps.setLong(2, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save balance for " + id, e);
            }
        });
    }

    void close() {
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
        }
    }
}
