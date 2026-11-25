package dev.rick.jjk.battle;

import dev.rick.jjk.battle.BattleMove.Category;
import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleMove.Stance;
import dev.rick.jjk.battle.BattleMove.Weight;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The turn-based kits. Beta: Vessel (Yuji) and Honored One (Gojo).
 *
 * <p>Combo routes are made by stances: a move that leaves the foe STAGGERED can be followed by one that links from
 * STAGGERED, and so on. Yuji: Divergent Fist → Manji Kick → Crushing Blow. Gojo: Blue → Twofold Kick → Red.
 */
public final class BattleKits {
    private BattleKits() {}

    public static final BattleMove FOCUS = BattleMove.of("focus", "Focus", Category.FOCUS)
            .line("Gather cursed energy (+35). Acts last; a hit cancels it and lands harder.").build();
    public static final BattleMove CLOSE_IN = BattleMove.of("close_in", "Close In", Category.MOVE).moveStep(-1)
            .from(Range.MID, Range.FAR).line("Dash to close range. Fast: attacks that need distance miss.").build();
    public static final BattleMove BACK_OFF = BattleMove.of("back_off", "Back Off", Category.MOVE).moveStep(1)
            .from(Range.CLOSE, Range.MID).line("Leap back a step. Fast: slower strikes miss.").build();
    public static final List<BattleMove> SHARED = List.of(FOCUS, CLOSE_IN, BACK_OFF);

    private static final Map<String, BattleKit> KITS = new LinkedHashMap<>();

    public static final BattleKit YUJI = register(new BattleKit("yuji", "Yuji", "Vessel", List.of(
            BattleMove.of("divergent_fist", "Divergent Fist", Category.TECHNIQUE).weight(Weight.LIGHT).power(10).hits(2)
                    .from(Range.CLOSE).inflicts(Stance.STAGGERED).blackFlash()
                    .line("A punch with a delayed cursed-energy impact: goes through guards. Staggers.").build(),
            BattleMove.of("manji_kick", "Manji Kick", Category.STRIKE).weight(Weight.LIGHT).power(10).cost(5)
                    .from(Range.CLOSE).needs(Stance.STAGGERED).inflicts(Stance.AIRBORNE).blackFlash()
                    .line("Follow-up: kick a staggered foe into the air.").build(),
            BattleMove.of("cursed_strikes", "Cursed Strikes", Category.STRIKE).weight(Weight.MEDIUM).power(13).cost(15).hits(6)
                    .from(Range.CLOSE, Range.MID).linkFrom(Stance.STAGGERED).inflicts(Stance.DOWN).rangeAfter(Range.MID)
                    .line("Dash in and rush. Reaches from mid range.").build(),
            BattleMove.of("crushing_blow", "Crushing Blow", Category.STRIKE).weight(Weight.HEAVY).power(22).cost(15)
                    .from(Range.CLOSE).linkFrom(Stance.AIRBORNE).inflicts(Stance.DOWN).rangeAfter(Range.MID).vsAirborne(1.3f)
                    .blackFlash().line("A huge slam. Extra damage on an airborne foe.").build()),
            BattleMove.of("guard", "Counter Stance", Category.GUARD)
                    .line("Block strikes; parry heavies with a counter. Techniques pierce it.").build(),
            3, 12, 1f,
            BattleMove.of("domain", "Malevolent Shrine", Category.DOMAIN).power(10).pierce()
                    .line("Domain: slashes burn the foe for 3 turns; your strikes come out faster.").build(),
            BattleMove.of("finisher", "Black Flash", Category.STRIKE).weight(Weight.HEAVY).power(16)
                    .linkFrom(Stance.STAGGERED, Stance.AIRBORNE, Stance.DOWN, Stance.GROUNDED).inflicts(Stance.DOWN).rangeAfter(Range.MID)
                    .blackFlash().line("Finisher: a cursed-energy spark at the end of a full combo.").build(),
            List.of(new BattleKit.NamedCombo("Divergent Black Flash", List.of("divergent_fist", "manji_kick", "crushing_blow")),
                    new BattleKit.NamedCombo("Vessel Rush", List.of("divergent_fist", "cursed_strikes")))));

    public static final BattleKit GOJO = register(new BattleKit("gojo", "Gojo", "Honored One", List.of(
            BattleMove.of("blue", "Lapse: Blue", Category.TECHNIQUE).weight(Weight.LIGHT).power(8).cost(10)
                    .inflicts(Stance.STAGGERED).rangeAfter(Range.CLOSE)
                    .line("Pull the foe in from any range. Staggers.").build(),
            BattleMove.of("twofold_kick", "Twofold Kick", Category.STRIKE).weight(Weight.MEDIUM).power(13).cost(5)
                    .from(Range.CLOSE).linkFrom(Stance.STAGGERED).inflicts(Stance.AIRBORNE).blackFlash()
                    .line("Two kicks that launch.").build(),
            BattleMove.of("red", "Reversal: Red", Category.TECHNIQUE).weight(Weight.HEAVY).power(22).cost(25)
                    .linkFrom(Stance.AIRBORNE).inflicts(Stance.DOWN).rangeAfter(Range.FAR).vsAirborne(1.2f)
                    .line("A blast that hurls the foe far away.").build(),
            BattleMove.of("hollow_purple", "Hollow Purple", Category.TECHNIQUE).weight(Weight.HEAVY).power(38).cost(60)
                    .from(Range.MID, Range.FAR).inflicts(Stance.DOWN).pierce()
                    .line("Unblockable. Huge damage, huge cost, slow to come out.").build()),
            BattleMove.of("guard", "Infinity", Category.GUARD)
                    .line("Block strikes and halve techniques that get through.").build(),
            2, 8, 0.5f,
            BattleMove.of("domain", "Unlimited Void", Category.DOMAIN).power(0).pierce()
                    .line("Domain: cancels the foe's move and slows them for 3 turns.").build(),
            BattleMove.of("finisher", "Limitless Barrage", Category.TECHNIQUE).weight(Weight.HEAVY).power(20)
                    .linkFrom(Stance.STAGGERED, Stance.AIRBORNE, Stance.DOWN, Stance.GROUNDED).inflicts(Stance.DOWN).rangeAfter(Range.FAR)
                    .line("Finisher: a blur of blows, then a point-blank Red.").build(),
            List.of(new BattleKit.NamedCombo("Lapse to Reversal", List.of("blue", "twofold_kick", "red")))));

    private static BattleKit register(BattleKit k) {
        KITS.put(k.character, k);
        return k;
    }

    public static BattleKit get(String character) {
        return KITS.get(character);
    }

    public static Iterable<String> ids() {
        return KITS.keySet();
    }
}
