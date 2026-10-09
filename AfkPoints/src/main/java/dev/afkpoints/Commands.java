package dev.afkpoints;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

final class Commands implements CommandExecutor, TabCompleter {
    private final AfkPointsPlugin plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    Commands(AfkPointsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "afkpoints" -> {
                if (!(sender instanceof Player p)) {
                    plugin.msg(sender, "players-only");
                    return true;
                }
                plugin.msg(p, "balance", "{balance}", String.valueOf(plugin.storage().get(p.getUniqueId())));
            }
            case "afkshop" -> {
                if (!(sender instanceof Player p)) {
                    plugin.msg(sender, "players-only");
                    return true;
                }
                plugin.shop().open(p);
            }
            case "afkadmin" -> admin(sender, args);
            default -> { }
        }
        return true;
    }

    private void admin(CommandSender sender, String[] a) {
        if (a.length == 0) {
            plugin.msg(sender, "usage");
            return;
        }
        switch (a[0].toLowerCase()) {
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player p)) {
                    plugin.msg(sender, "players-only");
                    return;
                }
                Location block = p.getLocation().getBlock().getLocation();
                (a[0].equalsIgnoreCase("pos1") ? pos1 : pos2).put(p.getUniqueId(), block);
                plugin.msg(p, "pos-set", "{pos}", a[0].toLowerCase());
            }
            case "setzone" -> {
                if (!(sender instanceof Player p)) {
                    plugin.msg(sender, "players-only");
                    return;
                }
                Location l1 = pos1.get(p.getUniqueId());
                Location l2 = pos2.get(p.getUniqueId());
                if (l1 == null || l2 == null || l1.getWorld() != l2.getWorld()) {
                    plugin.msg(p, "zone-error");
                    return;
                }
                try {
                    plugin.setZone(AfkZone.of(l1, l2));
                    plugin.msg(p, "zone-set");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save zone.yml", e);
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.shop().reload();
                plugin.msg(sender, "reloaded");
            }
            case "give", "take", "set" -> money(sender, a);
            default -> plugin.msg(sender, "usage");
        }
    }

    private void money(CommandSender sender, String[] a) {
        if (a.length < 3) {
            plugin.msg(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        Storage storage = plugin.storage();
        if (target == null || !storage.isLoaded(target.getUniqueId())) {
            plugin.msg(sender, "player-offline");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(a[2]);
        } catch (NumberFormatException e) {
            plugin.msg(sender, "invalid-amount");
            return;
        }
        if (amount < 0) {
            plugin.msg(sender, "invalid-amount");
            return;
        }
        UUID id = target.getUniqueId();
        switch (a[0].toLowerCase()) {
            case "give" -> storage.add(id, amount);
            case "take" -> storage.set(id, storage.get(id) - amount);
            default -> storage.set(id, amount);
        }
        boolean silent = a.length > 3 && a[3].equalsIgnoreCase("silent");
        if (!silent) {
            plugin.msg(sender, "admin-done", "{player}", target.getName(), "{balance}", String.valueOf(storage.get(id)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        if (!cmd.getName().equalsIgnoreCase("afkadmin")) return Collections.emptyList();
        if (a.length == 1) {
            return List.of("pos1", "pos2", "setzone", "give", "take", "set", "reload").stream()
                    .filter(s -> s.startsWith(a[0].toLowerCase())).toList();
        }
        if (a.length == 2 && List.of("give", "take", "set").contains(a[0].toLowerCase())) return null; // player names
        return Collections.emptyList();
    }
}
