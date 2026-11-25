package dev.rick.jjk.battle;

import dev.rick.jjk.battle.BattleMove.Stance;

/** One side of a battle. Mutable; {@link #copy()} for the AI's look-ahead. */
public final class Fighter {
    public static final int MAX_HP = 100, MAX_CE = 100, MAX_METER = 100, MAX_CONTROL = 2;

    public final BattleKit kit;
    public int hp = MAX_HP, ce = 50, meter;
    /** Control: +1 each turn this fighter takes control of the fight (a clean win), up to 2; lost when the foe takes it. */
    public int control;
    public Stance stance = Stance.GROUNDED;
    /** Turns of: burning (damage each turn end), slowed (-2 initiative), domain strike boost (+1 strikes), in the zone. */
    public int burnTurns, burnDamage, slowTurns, strikeBoostTurns, zoneTurns;
    /** Turns left on this fighter's own domain. */
    public int domainTurns;

    public Fighter(BattleKit kit) {
        this.kit = kit;
    }

    public Fighter copy() {
        Fighter f = new Fighter(kit);
        f.hp = hp; f.ce = ce; f.meter = meter; f.control = control; f.stance = stance; f.burnTurns = burnTurns;
        f.burnDamage = burnDamage; f.slowTurns = slowTurns; f.strikeBoostTurns = strikeBoostTurns; f.zoneTurns = zoneTurns;
        f.domainTurns = domainTurns;
        return f;
    }

    public boolean domainReady() {
        return meter >= MAX_METER && domainTurns == 0;
    }
}
