package dev.rick.jjk.core.combat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Describes what kind of attack something is. Defensive techniques decide how to react by looking at tags,
 * never at who the attacker is, so future techniques can counter or bypass defenses by carrying the right tags.
 */
public final class AttackTag {
    private static final Map<String, AttackTag> TAGS = new ConcurrentHashMap<>();

    public static final AttackTag MELEE = of("melee");
    public static final AttackTag HEAVY = of("heavy");
    public static final AttackTag PROJECTILE = of("projectile");
    public static final AttackTag TECHNIQUE = of("technique");
    public static final AttackTag EXPLOSION = of("explosion");
    public static final AttackTag AREA = of("area");
    public static final AttackTag ENVIRONMENTAL = of("environmental");
    /** Can't be guarded. */
    public static final AttackTag UNBLOCKABLE = of("unblockable");
    /** Shatters guard instantly. */
    public static final AttackTag GUARD_BREAK = of("guard_break");
    /** Connects with knocked-down targets. */
    public static final AttackTag OTG = of("otg");
    /** A domain's guaranteed hit. */
    public static final AttackTag SURE_HIT = of("sure_hit");
    /** Neutralises techniques on contact (domain amplification, inverted spear...). */
    public static final AttackTag NEUTRALIZES_TECHNIQUES = of("neutralizes_techniques");
    /** Ignores Infinity specifically. */
    public static final AttackTag BYPASS_INFINITY = of("bypass_infinity");
    /** Ultimate/Awakening-level attack: eligible for finisher presentation. */
    public static final AttackTag ULTIMATE = of("ultimate");
    /** Produced by the Limitless technique. */
    public static final AttackTag LIMITLESS = of("limitless");
    /** JJS "360 blockable": a guard stops it from any side, not only the front (Shrine's slashes, Dismantle...). */
    public static final AttackTag BLOCKABLE_360 = of("blockable_360");
    /** Builds nobody's Awakening meter, the attacker's or the target's (Authentic Mutual Love's blades, Jacob's Ladder). */
    public static final AttackTag NO_METER = of("no_meter");

    public final String id;

    private AttackTag(String id) {
        this.id = id;
    }

    public static AttackTag of(String id) {
        return TAGS.computeIfAbsent(id, AttackTag::new);
    }

    @Override
    public String toString() {
        return id;
    }
}
