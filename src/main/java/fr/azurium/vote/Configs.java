package fr.azurium.vote;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Charge tous les fichiers de configuration, rangés par dossier. */
public final class Configs {

    public static final String CONFIG = "config/config.yml";
    public static final String MESSAGES = "messages/messages.yml";
    public static final String MENU_MAIN = "menus/main.yml";
    public static final String MENU_PALIERS = "menus/paliers.yml";
    public static final String R_VOTE = "rewards/vote.yml";
    public static final String R_PARTY = "rewards/voteparty.yml";
    public static final String R_PALIERS = "rewards/paliers.yml";

    private static final String[] ALL = {CONFIG, MESSAGES, MENU_MAIN, MENU_PALIERS, R_VOTE, R_PARTY, R_PALIERS};

    private final AzuriumVote plugin;
    private final Map<String, YamlConfiguration> loaded = new HashMap<>();

    public Configs(AzuriumVote plugin) {
        this.plugin = plugin;
    }

    public void load() {
        loaded.clear();
        for (String path : ALL) {
            File f = new File(plugin.getDataFolder(), path);
            if (!f.exists()) plugin.saveResource(path, false);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            // Valeurs par défaut du jar pour la config et les messages (évite les clés manquantes après une mise à jour)
            if (path.equals(CONFIG) || path.equals(MESSAGES)) {
                try (InputStream in = plugin.getResource(path)) {
                    if (in != null) {
                        cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
                    }
                } catch (IOException ignored) {
                }
            }
            loaded.put(path, cfg);
        }
    }

    public YamlConfiguration get(String path) {
        return loaded.get(path);
    }
}
