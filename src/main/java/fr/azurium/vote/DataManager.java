package fr.azurium.vote;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Stockage des votes (data/votes.yml). À utiliser uniquement depuis le thread principal. */
public final class DataManager {

    public static final class PlayerData {
        public final UUID uuid;
        public String name = "";
        public String month = "";
        public int monthVotes;
        public int totalVotes;
        public int pending;
        public final Set<Integer> claimed = new TreeSet<>();

        PlayerData(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private final AzuriumVote plugin;
    private final File file;
    private final Object ioLock = new Object();
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();

    private volatile int partyCurrent;
    private volatile int partyCount;
    private volatile int serverTotal;
    private final Map<String, Integer> unresolved = new ConcurrentHashMap<>();
    private volatile String lastMonth = "";
    private volatile boolean dirty;

    public DataManager(AzuriumVote plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/votes.yml");
    }

    public void load() {
        players.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        partyCurrent = y.getInt("server.party-current", 0);
        partyCount = y.getInt("server.party-count", 0);
        serverTotal = y.getInt("server.total-votes", 0);
        lastMonth = y.getString("server.last-month", "");
        unresolved.clear();
        for (String entry : y.getStringList("unresolved")) {
            int i = entry.lastIndexOf(':');
            if (i <= 0) continue;
            try {
                unresolved.merge(entry.substring(0, i), Integer.parseInt(entry.substring(i + 1)), Integer::sum);
            } catch (NumberFormatException ignored) {
            }
        }

        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = ps.getConfigurationSection(k);
            if (s == null) continue;
            PlayerData d = new PlayerData(id);
            d.name = s.getString("name", "");
            d.month = s.getString("month", "");
            d.monthVotes = s.getInt("month-votes", 0);
            d.totalVotes = s.getInt("total-votes", 0);
            d.pending = s.getInt("pending", 0);
            d.claimed.addAll(s.getIntegerList("claimed"));
            players.put(id, d);
        }
    }

    public void save(boolean sync) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("server.party-current", partyCurrent);
        y.set("server.party-count", partyCount);
        y.set("server.total-votes", serverTotal);
        y.set("server.last-month", lastMonth);
        for (PlayerData d : players.values()) {
            String p = "players." + d.uuid;
            y.set(p + ".name", d.name);
            y.set(p + ".month", d.month);
            y.set(p + ".month-votes", d.monthVotes);
            y.set(p + ".total-votes", d.totalVotes);
            y.set(p + ".pending", d.pending);
            y.set(p + ".claimed", new ArrayList<>(d.claimed));
        }
        List<String> un = new ArrayList<>();
        for (Map.Entry<String, Integer> e : unresolved.entrySet()) un.add(e.getKey() + ":" + e.getValue());
        y.set("unresolved", un);
        String content = y.saveToString();
        dirty = false;
        if (sync) write(content);
        else Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content));
    }

    private void write(String content) {
        synchronized (ioLock) {
            try {
                Path target = file.toPath();
                Path tmp = target.resolveSibling("votes.yml.tmp");
                Files.createDirectories(target.getParent());
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder data/votes.yml : " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ joueurs

    public String monthKey() {
        return YearMonth.now(plugin.zone()).toString();
    }

    /** Récupère (ou crée) les données d'un joueur, avec réinitialisation mensuelle automatique. */
    public PlayerData get(UUID id, String name) {
        PlayerData d = players.computeIfAbsent(id, PlayerData::new);
        if (name != null && !name.isEmpty() && !name.equals(d.name)) {
            d.name = name;
            dirty = true;
        }
        String key = monthKey();
        if (!key.equals(d.month)) {
            d.month = key;
            d.monthVotes = 0;
            d.claimed.clear();
            dirty = true;
        }
        return d;
    }

    /** Lecture seule (sans création ni mise à jour) : utilisable depuis n'importe quel thread. */
    public PlayerData peek(UUID id) {
        return players.get(id);
    }

    /** Vote reçu pour un pseudo que le serveur ne sait pas encore résoudre : appliqué à sa prochaine connexion. */
    public void queueUnresolved(String name, int amount) {
        unresolved.merge(name.toLowerCase(Locale.ROOT), amount, Integer::sum);
        dirty = true;
    }

    public int takeUnresolved(String name) {
        Integer n = unresolved.remove(name.toLowerCase(Locale.ROOT));
        if (n != null) dirty = true;
        return n == null ? 0 : n;
    }

    public UUID findByName(String name) {
        for (PlayerData d : players.values()) {
            if (d.name.equalsIgnoreCase(name)) return d.uuid;
        }
        return null;
    }

    public Iterable<PlayerData> all() {
        return players.values();
    }

    // ------------------------------------------------------------------ serveur

    public int partyCurrent() { return partyCurrent; }
    public void setPartyCurrent(int v) { partyCurrent = Math.max(0, v); dirty = true; }
    public int partyCount() { return partyCount; }
    public void incrementPartyCount() { partyCount++; dirty = true; }
    public int serverTotal() { return serverTotal; }
    public void addServerVotes(int n) { serverTotal += n; partyCurrent += n; dirty = true; }
    public String lastMonth() { return lastMonth; }
    public void setLastMonth(String m) { lastMonth = m; dirty = true; }
    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }
}
