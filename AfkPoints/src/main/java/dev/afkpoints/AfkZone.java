package dev.afkpoints;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Axis-aligned block cuboid in one world. */
record AfkZone(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    static AfkZone of(Location a, Location b) {
        return new AfkZone(a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    boolean contains(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
                && l.getBlockX() >= minX && l.getBlockX() <= maxX
                && l.getBlockY() >= minY && l.getBlockY() <= maxY
                && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
    }

    void save(File file) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", world);
        y.set("minX", minX); y.set("minY", minY); y.set("minZ", minZ);
        y.set("maxX", maxX); y.set("maxY", maxY); y.set("maxZ", maxZ);
        y.save(file);
    }

    static AfkZone load(File file) {
        if (!file.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String w = y.getString("world");
        if (w == null) return null;
        return new AfkZone(w, y.getInt("minX"), y.getInt("minY"), y.getInt("minZ"),
                y.getInt("maxX"), y.getInt("maxY"), y.getInt("maxZ"));
    }
}
