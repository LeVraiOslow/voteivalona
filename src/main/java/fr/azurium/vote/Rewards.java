package fr.azurium.vote;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Map;

/**
 * Applique un "bloc de récompense" : commands, items, messages, title, sound.
 * Commandes : "[console] cmd" (par défaut) ou "[player] cmd".
 */
public final class Rewards {

    private Rewards() {
    }

    public static void give(AzuriumVote plugin, Player player, ConfigurationSection sec,
                            Map<String, String> ph, int times) {
        if (sec == null || player == null) return;
        boolean drop = plugin.configs().get(Configs.CONFIG).getBoolean("rewards.drop-if-full", true);

        for (int i = 0; i < Math.max(1, times); i++) {
            for (String raw : sec.getStringList("commands")) {
                run(player, Text.replace(raw, ph));
            }
            ConfigurationSection items = sec.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection is = items.getConfigurationSection(key);
                    if (is == null) continue;
                    ItemStack st = ItemFactory.build(plugin, is, ph, null, player, false);
                    Map<Integer, ItemStack> left = player.getInventory().addItem(st);
                    if (drop) {
                        for (ItemStack rest : left.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), rest);
                        }
                    }
                }
            }
        }

        for (String m : sec.getStringList("messages")) {
            if (!m.isEmpty()) player.sendMessage(Text.color(Text.replace(m, ph)));
        }

        ConfigurationSection t = sec.getConfigurationSection("title");
        if (t != null) {
            player.showTitle(Title.title(
                    Text.color(Text.replace(t.getString("title", ""), ph)),
                    Text.color(Text.replace(t.getString("subtitle", ""), ph)),
                    Title.Times.times(
                            Duration.ofMillis(t.getInt("fade-in", 10) * 50L),
                            Duration.ofMillis(t.getInt("stay", 60) * 50L),
                            Duration.ofMillis(t.getInt("fade-out", 20) * 50L))));
        }

        Text.playSound(player, sec.getString("sound", ""));
    }

    public static void run(Player p, String cmd) {
        String c = cmd == null ? "" : cmd.trim();
        boolean asPlayer = false;
        if (c.regionMatches(true, 0, "[player]", 0, 8)) {
            asPlayer = true;
            c = c.substring(8).trim();
        } else if (c.regionMatches(true, 0, "[console]", 0, 9)) {
            c = c.substring(9).trim();
        }
        if (c.startsWith("/")) c = c.substring(1);
        if (c.isEmpty()) return;
        Bukkit.dispatchCommand(asPlayer ? p : Bukkit.getConsoleSender(), c);
    }
}
