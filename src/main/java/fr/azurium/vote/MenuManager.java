package fr.azurium.vote;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Construit et gère les menus (menus/main.yml et menus/paliers.yml). */
public final class MenuManager {

    public record Entry(String action, List<String> commands, Integer palier) {
    }

    private final AzuriumVote plugin;

    public MenuManager(AzuriumVote plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration menuCfg(String menu) {
        return plugin.configs().get("menus/" + menu + ".yml");
    }

    public void open(Player p, String menu) {
        YamlConfiguration cfg = menuCfg(menu);
        if (cfg == null) return;
        MenuHolder h = new MenuHolder(menu);
        Map<String, String> ph = plugin.service().placeholders(p);
        int size = ((Math.max(1, cfg.getInt("size", 27)) + 8) / 9) * 9;
        size = Math.max(9, Math.min(54, size));
        Inventory inv = Bukkit.createInventory(h, size, Text.color(Text.replace(cfg.getString("title", "&8Vote"), ph)));
        h.setInventory(inv);
        fill(p, h, inv, cfg);
        p.openInventory(inv);
        plugin.service().sound(p, "open");
    }

    public void refresh(Player p, MenuHolder h) {
        YamlConfiguration cfg = menuCfg(h.menu());
        if (cfg == null) return;
        Inventory inv = h.getInventory();
        inv.clear();
        h.entries().clear();
        fill(p, h, inv, cfg);
    }

    private void fill(Player p, MenuHolder h, Inventory inv, YamlConfiguration cfg) {
        VoteService svc = plugin.service();
        Map<String, String> ph = svc.placeholders(p);

        // Items statiques (action, commandes)
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                ItemStack stack = ItemFactory.build(plugin, s, ph, null, p, true);
                Entry e = new Entry(s.getString("action", "NONE"), s.getStringList("commands"), null);
                for (int slot : slots(s, inv.getSize())) {
                    inv.setItem(slot, stack.clone());
                    h.entries().put(slot, e);
                }
            }
        }

        // Paliers (menu des paliers)
        ConfigurationSection pal = cfg.getConfigurationSection("paliers");
        if (pal != null) {
            DataManager.PlayerData d = plugin.data().get(p.getUniqueId(), p.getName());
            for (int n : svc.palierList()) {
                ConfigurationSection ps = pal.getConfigurationSection(String.valueOf(n));
                if (ps == null) continue;
                int slot = ps.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) continue;

                String state = svc.state(d, n);
                MemoryConfiguration merged = new MemoryConfiguration();
                ConfigurationSection base = cfg.getConfigurationSection("states." + state);
                if (base != null) base.getValues(false).forEach(merged::set);
                ConfigurationSection over = ps.getConfigurationSection(state);
                if (over != null) over.getValues(false).forEach(merged::set);

                Map<String, String> pph = new HashMap<>(ph);
                pph.put("%palier%", String.valueOf(n));
                pph.put("%palier_remaining%", String.valueOf(Math.max(0, n - d.monthVotes)));
                pph.put("%state%", state);
                Map<String, List<String>> multi = Map.of("%rewards%", svc.palierDescription(n));

                inv.setItem(slot, ItemFactory.build(plugin, merged, pph, multi, p, true));
                h.entries().put(slot, new Entry("PALIER", List.of(), n));
            }
        }

        // Décor de remplissage (en dernier)
        ConfigurationSection f = cfg.getConfigurationSection("filler");
        if (f != null && f.getBoolean("enabled", true)) {
            ItemStack filler = ItemFactory.build(plugin, f, ph, null, p, true);
            if (f.getBoolean("hide-tooltip", true)) ItemFactory.hideTooltip(filler);
            for (int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) == null) inv.setItem(i, filler.clone());
            }
        }
    }

    public void handleClick(Player p, MenuHolder h, Entry e, ClickType click) {
        if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != h) return;
        VoteService svc = plugin.service();

        if (e.palier() != null) {
            ClickType need = parseClick(menuCfg(h.menu()).getString("claim-click", "LEFT"));
            if (click == need) {
                svc.claimPalier(p, e.palier());
                refresh(p, h);
            }
            return;
        }

        svc.sound(p, "click");
        Map<String, String> ph = svc.placeholders(p);
        String action = e.action() == null ? "NONE" : e.action().toUpperCase(Locale.ROOT);
        switch (action) {
            case "VOTE":
                p.closeInventory();
                plugin.messages().sendVoteLink(p, ph);
                break;
            case "REWARDS":
                svc.claimPending(p);
                refresh(p, h);
                break;
            case "VOTEPARTY":
                plugin.messages().send(p, "party-info", ph);
                break;
            case "PALIERS":
                open(p, "paliers");
                break;
            case "MAIN":
                open(p, "main");
                break;
            case "CLOSE":
                p.closeInventory();
                break;
            default:
                break;
        }
        for (String c : e.commands()) Rewards.run(p, Text.replace(c, ph));
    }

    private static ClickType parseClick(String s) {
        try {
            return ClickType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return ClickType.LEFT;
        }
    }

    /** slot: 10 | slots: [0, 1, "3-5"] */
    private static List<Integer> slots(ConfigurationSection s, int size) {
        List<Integer> out = new ArrayList<>();
        if (s.contains("slot")) parseSlot(out, s.get("slot"));
        if (s.contains("slots")) {
            List<?> list = s.getList("slots");
            if (list != null) for (Object o : list) parseSlot(out, o);
        }
        out.removeIf(i -> i < 0 || i >= size);
        return out;
    }

    private static void parseSlot(List<Integer> out, Object o) {
        String t = String.valueOf(o).trim();
        try {
            if (t.contains("-")) {
                String[] a = t.split("-");
                int from = Integer.parseInt(a[0].trim());
                int to = Integer.parseInt(a[1].trim());
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) out.add(i);
            } else {
                out.add(Integer.parseInt(t));
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
