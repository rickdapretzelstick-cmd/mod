package dev.rick.jjk.battle;

import java.util.ArrayList;
import java.util.List;

/**
 * A character's turn-based kit: four moves, a guard with the character's own flavour, and a domain. Focus, Close In and
 * Back Off are shared by everyone ({@link BattleKits}).
 */
public final class BattleKit {
    /** A move sequence that earns a title card when a combo plays it in order. */
    public record NamedCombo(String name, List<String> sequence) {}

    public final String character, name, title;
    public final List<BattleMove> moves;
    public final BattleMove guard, domain;
    /** Lands at the end of a full combo while at max control (not picked from the menu). */
    public final BattleMove finisher;
    /** Strike links at or below this speed are caught by this fighter's guard after a technique pierced it. */
    public final int counterSpeed;
    /** Damage the guard's counter deals when it parries a heavy strike. */
    public final int parryDamage;
    /** Damage taken from techniques that pierce the guard (Infinity halves them). */
    public final float guardTechniqueMult;
    public final List<NamedCombo> combos;

    public BattleKit(String character, String name, String title, List<BattleMove> moves, BattleMove guard, int counterSpeed,
                     int parryDamage, float guardTechniqueMult, BattleMove domain, BattleMove finisher, List<NamedCombo> combos) {
        this.character = character; this.name = name; this.title = title; this.moves = List.copyOf(moves); this.guard = guard;
        this.counterSpeed = counterSpeed; this.parryDamage = parryDamage; this.guardTechniqueMult = guardTechniqueMult;
        this.domain = domain; this.finisher = finisher; this.combos = List.copyOf(combos);
    }

    /** Every move this fighter can pick: the four, guard, domain, and the shared ones. */
    public List<BattleMove> all() {
        List<BattleMove> out = new ArrayList<>(moves);
        out.add(guard);
        out.add(domain);
        out.addAll(BattleKits.SHARED);
        return out;
    }

    public BattleMove move(String id) {
        for (BattleMove m : all()) if (m.id.equals(id)) return m;
        return finisher.id.equals(id) ? finisher : null;
    }
}
