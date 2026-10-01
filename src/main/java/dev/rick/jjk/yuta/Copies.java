package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.JJKCharacter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The techniques Copy can use (Copy Wheel's entries), after the JJS wiki: Cursed Speech is his own from the start; the
 * rest he takes from whoever Rika attacks (Rika Downslam, Rika Slam, Elbow Rush) or kills (Rika Smash, Rika Haymaker).
 * Each one plays the original character's own move with Yuta as its user.
 */
public final class Copies {
    public static final String SPEECH = "speech", LIMITLESS = "limitless", DOORS = "doors", SHRINE = "shrine", DISMANTLE = "dismantle";

    /** A copyable technique: its wheel name, the move it becomes, and whether it counts as an Awakening move (25 s). */
    public record Technique(String id, String name, String move, boolean awakened, @Nullable Ability ability) {}

    public static final Map<String, Technique> ALL = new LinkedHashMap<>();

    static {
        add(new Technique(SPEECH, "Cursed Speech", "Don't move!", false, null));
        add(new Technique(LIMITLESS, "Limitless", "Reversal Red MAX", true, new dev.rick.jjk.gojo.RedAbility(true)));
        add(new Technique(DOORS, "Doors", "Shutter Doors", false, new dev.rick.jjk.hakari.ShutterDoorsAbility()));
        add(new Technique(SHRINE, "Shrine", "Dismantle", false, new dev.rick.jjk.yuji.DismantleAbility()));
        add(new Technique(DISMANTLE, "Dismantle", "Strong Dismantle", true, new dev.rick.jjk.yuji.DismantleAbility()));
    }

    private static void add(Technique t) {
        ALL.put(t.id, t);
    }

    private Copies() {}

    /**
     * The technique Rika can take from {@code victim}, or null: Honored One's Limitless, Restless Gambler's Doors,
     * Vessel's Shrine (Strongest of History's Dismantle once Sukuna has taken over); blocking dummies and other NPCs
     * carry Shrine. Players without a character (and other Cursed Partners) have nothing to give.
     */
    @Nullable
    public static String from(LivingEntity victim) {
        AbilityCaster c = Casters.getOrNull(victim);
        JJKCharacter ch = c == null ? null : c.character();
        if (ch == null) return victim instanceof Player ? null : SHRINE;
        return switch (ch.id) {
            case "gojo" -> LIMITLESS;
            case "hakari" -> DOORS;
            case "yuji" -> c.isAwakened() ? DISMANTLE : SHRINE;
            default -> null;
        };
    }
}
