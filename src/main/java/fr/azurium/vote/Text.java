package fr.azurium.vote;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Couleurs (&a, &#RRGGBB, &lt;#RRGGBB&gt;), placeholders et sons. */
public final class Text {

    private static final LegacyComponentSerializer SER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final Pattern ANGLE_HEX = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private Text() {
    }

    public static String replace(String s, Map<String, String> ph) {
        if (s == null) return "";
        if (ph != null) {
            for (Map.Entry<String, String> e : ph.entrySet()) {
                s = s.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        return s;
    }

    public static Component color(String s) {
        if (s == null) s = "";
        s = ANGLE_HEX.matcher(s).replaceAll("&#$1");
        return SER.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Map<String, String> ph(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Format : "entity.player.levelup;volume;pitch" */
    public static void playSound(Player p, String spec) {
        if (p == null || spec == null || spec.isBlank()) return;
        String[] a = spec.split(";");
        float vol = 1f, pitch = 1f;
        try {
            if (a.length > 1) vol = Float.parseFloat(a[1].trim());
            if (a.length > 2) pitch = Float.parseFloat(a[2].trim());
        } catch (NumberFormatException ignored) {
        }
        p.playSound(p.getLocation(), a[0].trim().toLowerCase(Locale.ROOT), vol, pitch);
    }
}
