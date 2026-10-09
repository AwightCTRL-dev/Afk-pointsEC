package dev.afkpoints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class AfkPointsPlugin extends JavaPlugin {
    private Storage storage;
    private ShopGui shop;
    private AfkZone zone;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        storage = new Storage(this);
        try {
            storage.init();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not open the database, disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        zone = AfkZone.load(zoneFile());
        shop = new ShopGui(this);
        shop.reload();

        getServer().getPluginManager().registerEvents(shop, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        Commands commands = new Commands(this);
        for (String name : List.of("afkpoints", "afkshop", "afkadmin")) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(commands);
                pc.setTabCompleter(commands);
            }
        }

        // Players already online (plugin loaded while the server is running)
        for (Player p : Bukkit.getOnlinePlayers()) storage.load(p.getUniqueId());

        new EarnTask(this).runTaskTimer(this, 20L, 20L);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AfkExpansion(this).register();
            getLogger().info("Registered PlaceholderAPI expansion: %afkpoints_balance%");
        }
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.close(); // flushes all pending writes
    }

    public Storage storage() { return storage; }
    ShopGui shop() { return shop; }
    AfkZone zone() { return zone; }

    void setZone(AfkZone newZone) throws IOException {
        newZone.save(zoneFile());
        zone = newZone;
    }

    private File zoneFile() {
        return new File(getDataFolder(), "zone.yml");
    }

    static Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }

    void msg(CommandSender to, String key, String... replacements) {
        String s = getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) s = s.replace(replacements[i], replacements[i + 1]);
        to.sendMessage(color(getConfig().getString("messages.prefix", "") + s));
    }
}
