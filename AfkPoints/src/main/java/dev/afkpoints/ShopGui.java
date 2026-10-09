package dev.afkpoints;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ShopGui implements Listener {

    record Entry(int slot, ItemStack icon, String name, long price, Material material, int amount, List<String> commands) {}

    static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final AfkPointsPlugin plugin;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private String title = "&8AFK Shop";
    private int rows = 3;

    ShopGui(AfkPointsPlugin plugin) {
        this.plugin = plugin;
    }

    void reload() {
        entries.clear();
        ConfigurationSection shop = plugin.getConfig().getConfigurationSection("shop");
        if (shop == null) return;
        title = shop.getString("title", "&8AFK Shop");
        rows = Math.max(1, Math.min(6, shop.getInt("rows", 3)));

        ConfigurationSection items = shop.getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection s = items.getConfigurationSection(key);
            if (s == null) continue;

            Material mat = Material.matchMaterial(s.getString("material", "STONE"));
            if (mat == null || mat.isAir() || !mat.isItem()) {
                plugin.getLogger().warning("Shop item '" + key + "': invalid material, skipped.");
                continue;
            }
            int slot = s.getInt("slot", -1);
            if (slot < 0 || slot >= rows * 9 || entries.containsKey(slot)) {
                plugin.getLogger().warning("Shop item '" + key + "': slot invalid or already used, skipped.");
                continue;
            }
            long price = Math.max(0, s.getLong("price", 1));
            int amount = Math.max(1, s.getInt("amount", 1));
            String name = s.getString("name", "&f" + mat.name());

            ItemStack icon = new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(AfkPointsPlugin.color(name));
            List<Component> lore = new ArrayList<>();
            for (String line : s.getStringList("lore")) {
                lore.add(AfkPointsPlugin.color(line.replace("{price}", String.valueOf(price))));
            }
            meta.lore(lore);
            icon.setItemMeta(meta);

            entries.put(slot, new Entry(slot, icon, name, price, mat, amount, s.getStringList("commands")));
        }
    }

    void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, AfkPointsPlugin.color(title));
        holder.inventory = inv;
        entries.values().forEach(e -> inv.setItem(e.slot(), e.icon()));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        e.setCancelled(true); // nothing can be taken out, put in, or shift-clicked
        if (e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Entry entry = entries.get(e.getSlot());
        if (entry != null) buy(p, entry);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void buy(Player p, Entry entry) {
        UUID id = p.getUniqueId();
        Storage storage = plugin.storage();

        if (!storage.isLoaded(id)) {
            plugin.msg(p, "data-not-loaded");
            return;
        }
        if (storage.get(id) < entry.price()) {
            plugin.msg(p, "not-enough", "{price}", String.valueOf(entry.price()), "{balance}", String.valueOf(storage.get(id)));
            return;
        }

        boolean runCommands = !entry.commands().isEmpty();
        ItemStack stack = new ItemStack(entry.material(), entry.amount());
        if (!runCommands && !hasSpace(p, stack)) {
            plugin.msg(p, "inventory-full");
            return;
        }

        // Check done above, so take() cannot fail and the player is never charged without delivery.
        if (!storage.take(id, entry.price())) return;

        if (runCommands) {
            for (String cmd : entry.commands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("%player%", p.getName()).replace("%uuid%", id.toString()));
            }
        } else {
            p.getInventory().addItem(stack);
        }
        plugin.msg(p, "purchased", "{item}", entry.name().replaceAll("&[0-9a-fk-orA-FK-OR]", ""),
                "{price}", String.valueOf(entry.price()));
    }

    private boolean hasSpace(Player p, ItemStack stack) {
        int need = stack.getAmount();
        int max = stack.getMaxStackSize();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) need -= max;
            else if (it.isSimilar(stack)) need -= Math.max(0, max - it.getAmount());
            if (need <= 0) return true;
        }
        return false;
    }
}
