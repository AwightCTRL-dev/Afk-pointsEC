package dev.afkpoints;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * %afkpoints_balance%            -> 1520
 * %afkpoints_balance_formatted%  -> 1,520
 * Reads the in-memory cache only, so it is cheap and safe to call from any thread.
 */
final class AfkExpansion extends PlaceholderExpansion {
    private final AfkPointsPlugin plugin;

    AfkExpansion(AfkPointsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "afkpoints";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AfkPoints";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        long value = plugin.storage().get(player.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "balance", "points" -> String.valueOf(value);
            case "balance_formatted" -> String.format(Locale.US, "%,d", value);
            default -> null;
        };
    }
}
