package dev.afkpoints;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runs every second; awards points to players who stayed in the AFK zone long enough. */
final class EarnTask extends BukkitRunnable {
    private final AfkPointsPlugin plugin;
    private final Map<UUID, Integer> seconds = new HashMap<>();

    EarnTask(AfkPointsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("earn.enabled", false)) return;

        seconds.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);

        AfkZone zone = plugin.zone();
        if (zone == null) return;

        FileConfiguration cfg = plugin.getConfig();
        int interval = Math.max(1, cfg.getInt("earn.interval-seconds", 60));
        long amount = cfg.getLong("earn.points", 1);
        Storage storage = plugin.storage();

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!storage.isLoaded(id) || !zone.contains(p.getLocation())) {
                seconds.remove(id);
                continue;
            }
            int s = seconds.merge(id, 1, Integer::sum);
            if (s >= interval) {
                seconds.put(id, 0);
                storage.add(id, amount);
                String msg = cfg.getString("messages.earned", "+{points}")
                        .replace("{points}", String.valueOf(amount))
                        .replace("{balance}", String.valueOf(storage.get(id)));
                p.sendActionBar(AfkPointsPlugin.color(msg));
            }
        }
    }
}
