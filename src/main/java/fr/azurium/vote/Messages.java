package fr.azurium.vote;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Messages {

    private final AzuriumVote plugin;

    public Messages(AzuriumVote plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() {
        return plugin.configs().get(Configs.MESSAGES);
    }

    public String prefix() {
        return cfg().getString("prefix", "");
    }

    public List<String> lines(String key) {
        YamlConfiguration c = cfg();
        if (c.isList(key)) return c.getStringList(key);
        String s = c.getString(key);
        return s == null ? List.of() : List.of(s);
    }

    private Map<String, String> withPrefix(Map<String, String> ph) {
        Map<String, String> m = new HashMap<>();
        m.put("%prefix%", prefix());
        if (ph != null) m.putAll(ph);
        return m;
    }

    /** Une valeur vide ("") désactive le message. */
    public void send(CommandSender to, String key, Map<String, String> ph) {
        Map<String, String> m = withPrefix(ph);
        for (String line : lines(key)) {
            if (line == null || line.isEmpty()) continue;
            to.sendMessage(Text.color(Text.replace(line, m)));
        }
    }

    public void broadcast(String key, Map<String, String> ph) {
        Map<String, String> m = withPrefix(ph);
        for (String line : lines(key)) {
            if (line == null || line.isEmpty()) continue;
            Bukkit.broadcast(Text.color(Text.replace(line, m)));
        }
    }

    /** Envoie le lien de vote, cliquable. */
    public void sendVoteLink(Player p, Map<String, String> ph) {
        Map<String, String> m = withPrefix(ph);
        String url = m.getOrDefault("%url%", "");
        Component hover = Text.color(Text.replace(cfg().getString("vote-link-hover", ""), m));
        for (String line : lines("vote-link")) {
            if (line == null || line.isEmpty()) continue;
            Component c = Text.color(Text.replace(line, m));
            if (!url.isEmpty()) {
                c = c.clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(hover));
            }
            p.sendMessage(c);
        }
    }
}
