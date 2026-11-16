package fr.azurium.vote;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Construit un item depuis une section de config (vanilla, nexo:xxx ou tête à texture). */
public final class ItemFactory {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ItemFactory() {
    }

    public static ItemStack fromSpec(AzuriumVote plugin, String spec) {
        if (spec == null || spec.isBlank()) return missing("(vide)");
        String s = spec.trim();
        if (s.regionMatches(true, 0, "nexo:", 0, 5)) {
            ItemStack n = nexo(s.substring(5).trim());
            if (n != null) return n;
            warn(plugin, "Item Nexo introuvable : " + s);
            return missing(s);
        }
        Material m = Material.matchMaterial(s);
        if (m != null && m.isItem() && !m.isAir()) return new ItemStack(m);
        warn(plugin, "Item inconnu : " + s);
        return missing(s);
    }

    /** Appel par réflexion : pas de dépendance de compilation sur Nexo. */
    private static ItemStack nexo(String id) {
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = api.getMethod("itemFromId", String.class).invoke(null, id);
            if (builder == null) return null;
            Object stack = builder.getClass().getMethod("build").invoke(builder);
            return stack instanceof ItemStack is ? is : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static ItemStack missing(String spec) {
        ItemStack st = new ItemStack(Material.BARRIER);
        ItemMeta meta = st.getItemMeta();
        meta.displayName(Text.color("&cItem introuvable : &f" + spec));
        st.setItemMeta(meta);
        return st;
    }

    private static void warn(AzuriumVote plugin, String msg) {
        if (WARNED.add(msg)) plugin.getLogger().warning(msg);
    }

    /**
     * @param multi       placeholders multi-lignes (une ligne de lore égale à la clé est remplacée par la liste)
     * @param defaultHide valeur par défaut de hide-flags
     */
    public static ItemStack build(AzuriumVote plugin, ConfigurationSection sec, Map<String, String> ph,
                                  Map<String, List<String>> multi, Player viewer, boolean defaultHide) {
        String spec = Text.replace(sec.getString("item", sec.getString("material", "STONE")), ph);
        ItemStack stack = fromSpec(plugin, spec);

        int amount = 1;
        try {
            amount = Integer.parseInt(Text.replace(String.valueOf(sec.get("amount", 1)), ph).trim());
        } catch (NumberFormatException ignored) {
        }
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (sec.contains("name")) {
            meta.displayName(Text.color(Text.replace(sec.getString("name"), ph)));
        }
        if (sec.contains("lore")) {
            List<Component> lore = new ArrayList<>();
            for (String line : sec.getStringList("lore")) {
                String t = line.trim();
                if (multi != null && multi.containsKey(t)) {
                    for (String l : multi.get(t)) lore.add(Text.color(Text.replace(l, ph)));
                } else {
                    lore.add(Text.color(Text.replace(line, ph)));
                }
            }
            meta.lore(lore);
        }
        if (sec.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        if (sec.getBoolean("hide-flags", defaultHide)) meta.addItemFlags(ItemFlag.values());

        if (meta instanceof SkullMeta sm) {
            String tex = sec.getString("texture");
            if (tex != null && !tex.isBlank()) {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new ProfileProperty("textures", Text.replace(tex, ph).trim()));
                sm.setPlayerProfile(profile);
            } else if (sec.getBoolean("head-owner", false) && viewer != null) {
                sm.setOwningPlayer(viewer);
            }
        }
        stack.setItemMeta(meta);
        if (sec.getBoolean("hide-tooltip", false)) hideTooltip(stack);
        return stack;
    }

    /** Supprime totalement l'infobulle de l'item (y compris les infos F3+H). */
    public static void hideTooltip(ItemStack stack) {
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
    }
}
