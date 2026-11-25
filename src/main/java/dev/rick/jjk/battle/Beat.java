package dev.rick.jjk.battle;

import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleMove.Stance;

/**
 * One moment of a resolved turn, in play order. The cinematic layer turns each beat into a scene (attacker clip, the
 * target's reaction, camera, effects); the beats carry everything it needs, so every viewer plays the same fight.
 *
 * @param actor    the side doing it (0 or 1)
 * @param move     the move id ("" for beats that aren't a move: burn, KO)
 * @param kind     what happened
 * @param damage   damage dealt to the other side (for PARRY, the counter's damage to the attacker)
 * @param link     0 for an opener, 1.. for combo links
 * @param blackFlash whether this hit was a Black Flash
 * @param note     a title card to show (a named combo), or ""
 * @param control  part of a control sequence: the side that took control this turn, its whole combo played as one take
 */
public record Beat(int actor, String move, Kind kind, int damage, int link, boolean blackFlash, String note,
                   Range range, Stance targetStance, int hp0, int hp1, boolean control) {
    public enum Kind {
        /** A domain opens. */
        DOMAIN,
        /** Both opened domains: the actor's wins. */
        DOMAIN_CLASH,
        /** A clean hit (opener or link). */
        HIT,
        /** Both hit: the slower move beat the faster one's category and landed anyway. */
        TRADE,
        /** Equal initiative: the moves collide. */
        CLASH,
        /** A technique went through the guard. */
        PIERCE,
        /** The other side's guard blocked a strike (chip damage). */
        BLOCK,
        /** The other side's guard parried a heavy strike and countered. */
        PARRY,
        /** The move missed: out of range, or the stance it needs isn't there. */
        WHIFF,
        /** The move was cut off before it came out. */
        INTERRUPTED,
        /** A Close In / Back Off. */
        MOVE,
        /** Focus finished: cursed energy gained. */
        FOCUS,
        /** Guarded and nothing came. */
        GUARD,
        /** Couldn't act (caught in a domain). */
        STUNNED,
        /** End-of-turn burn damage. */
        BURN,
        /** The defender read the combo and stole control back (damage: the counter). */
        REVERSAL,
        /** The finisher at the end of a sequence at full control. */
        FINISHER,
        /** The actor won. */
        KO
    }
}
