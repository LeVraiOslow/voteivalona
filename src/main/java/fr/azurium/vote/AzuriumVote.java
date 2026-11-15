package fr.azurium.vote;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;

public final class AzuriumVote extends JavaPlugin {

    private Configs configs;
    private Messages messages;
    private DataManager data;
    private VoteService service;
    private MenuManager menus;

    @Override
    public void onEnable() {
        new File(getDataFolder(), "data").mkdirs();

        configs = new Configs(this);
        configs.load();
        messages = new Messages(this);
        data = new DataManager(this);
        data.load();
        service = new VoteService(this);
        menus = new MenuManager(this);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        PluginCommand cmd = getCommand("vote");
        if (cmd != null) {
            VoteCommand vc = new VoteCommand(this);
            cmd.setExecutor(vc);
            cmd.setTabCompleter(vc);
        }

        // Vérification du changement de mois (toutes les minutes)
        Bukkit.getScheduler().runTaskTimer(this, () -> service.tickMonth(), 100L, 1200L);
        // Sauvegarde automatique (toutes les 30 s si modifications)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (data.isDirty()) data.save(false);
        }, 600L, 600L);

        if (getServer().getPluginManager().getPlugin("AzLink") == null) {
            getLogger().warning("AzLink n'est pas installé : les votes du site ne seront pas reçus (commande : /vote addvote <joueur>).");
        }
        if (getServer().getPluginManager().getPlugin("Nexo") == null) {
            getLogger().info("Nexo non détecté : les items nexo:xxx ne seront pas disponibles.");
        }
    }

    @Override
    public void onDisable() {
        if (data != null) data.save(true);
    }

    public void reloadAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                p.closeInventory();
            }
        }
        configs.load();
    }

    public ZoneId zone() {
        try {
            return ZoneId.of(configs.get(Configs.CONFIG).getString("timezone", "Europe/Paris"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public Configs configs() { return configs; }
    public Messages messages() { return messages; }
    public DataManager data() { return data; }
    public VoteService service() { return service; }
    public MenuManager menus() { return menus; }
}
