package dev.rick.jjk.battle.game;

import dev.rick.jjk.battle.BattleMove;
import dev.rick.jjk.battle.BattleMove.Stance;
import dev.rick.jjk.battle.Beat;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns each beat into a scene on the server: who plays which clip when, where the fighters move, which effects and
 * sounds fire, and the target's reaction at the moment of contact. The clips are the mod's existing animation clips,
 * used as material; the moves themselves are the turn-based ones.
 */
public final class BattleScenes {
    private BattleScenes() {}

    /** A scene: cues on a clock. {@code contact} is when the hit lands (the camera cuts on it). */
    public record Scene(int length, int contact, List<Cue> cues) {}

    public record Cue(int tick, Runnable run) {}

    /**
     * How a move plays: clips at ticks, whether the attacker dashes in (and when), the contact ticks (several for a
     * multi-hit), and effects at the windup and at each contact.
     */
    private record MoveScene(List<Clip> clips, int lungeAt, int[] contacts, String windupFx, String contactFx, String lateFx) {
        int contact() {
            return contacts[contacts.length - 1];
        }
    }

    private record Clip(int tick, String name) {}

    private static MoveScene move(int lungeAt, int[] contacts, String windupFx, String contactFx, String lateFx, Object... clips) {
        List<Clip> list = new ArrayList<>();
        for (int i = 0; i < clips.length; i += 2) list.add(new Clip((Integer) clips[i], (String) clips[i + 1]));
        return new MoveScene(list, lungeAt, contacts, windupFx, contactFx, lateFx);
    }

    private static final Map<String, MoveScene> MOVES = Map.of(
            "divergent_fist", move(3, new int[] {9}, "divergent_charge", "divergent_impact", "divergent_flash",
                    0, "divergent_windup", 6, "divergent_punch"),
            "manji_kick", move(0, new int[] {5}, "manji_startup", "manji_crush", "",
                    0, "manji_kick"),
            "cursed_strikes", move(0, new int[] {9, 13, 17, 21, 26}, "cursed_strikes_start", "cursed_strikes_impact", "",
                    0, "cursed_strikes_slide", 6, "cursed_strikes_flurry", 22, "cursed_strikes_final"),
            "crushing_blow", move(8, new int[] {13}, "crushing_charge", "crushing_impact", "",
                    0, "crushing_blow_charge", 9, "crushing_blow_slam"),
            "blue", move(-1, new int[] {8}, "blue_cast", "blue_spawn", "",
                    0, "blue_cast"),
            "twofold_kick", move(0, new int[] {5, 10}, "", "swing", "",
                    0, "twofold_1", 7, "twofold_2"),
            "red", move(-1, new int[] {15}, "red_amplified", "red_fire", "",
                    0, "red_charge", 12, "red_release"),
            "hollow_purple", move(-1, new int[] {38}, "purple_blue", "purple_fire", "",
                    0, "purple_blue", 10, "purple_red", 20, "purple_fusion", 32, "purple_release"));

    /** The scene for {@code b}, played by {@code s}. */
    public static Scene build(BattleSession s, Beat b) {
        List<Cue> cues = new ArrayList<>();
        int me = b.actor(), foe = 1 - me;
        BattleMove move = s.kit(me).move(b.move());
        switch (b.kind()) {
            case HIT, PIERCE, TRADE, CLASH, BLOCK, PARRY, WHIFF -> {
                MoveScene ms = MOVES.get(b.move());
                if (ms == null) return simple(cues, 10);
                // Links come out quicker: the windup is the previous hit's follow-through.
                float speed = b.link() > 0 ? 1.35f : 1f;
                int contact = Math.round(ms.contact() / speed);
                for (Clip c : ms.clips()) cues.add(new Cue(Math.round(c.tick() / speed), () -> s.anim(me, c.name(), speed)));
                if (!ms.windupFx().isEmpty() && b.link() == 0) cues.add(new Cue(0, () -> s.fxAt(me, ms.windupFx(), false)));
                if (ms.lungeAt() >= 0 || b.kind() == Beat.Kind.CLASH) {
                    int at = Math.max(0, Math.round(Math.max(0, ms.lungeAt()) / speed));
                    cues.add(new Cue(at, () -> s.lunge(me, b.kind() == Beat.Kind.CLASH)));
                }
                boolean multi = ms.contacts().length > 1;
                for (int k = 0; k < ms.contacts().length; k++) {
                    int t = Math.round(ms.contacts()[k] / speed);
                    boolean last = k == ms.contacts().length - 1;
                    cues.add(new Cue(t, () -> contact(s, b, move, me, foe, ms, last, multi)));
                }
                if (!ms.lateFx().isEmpty() && landed(b)) cues.add(new Cue(contact + 4, () -> {
                    s.fxAt(foe, ms.lateFx(), true);
                    s.shake(0.5f, 6);
                }));
                cues.add(new Cue(contact + 6, s::settle));
                return new Scene(contact + (b.blackFlash() ? 22 : 14), contact, cues);
            }
            case INTERRUPTED -> {
                return simple(cues, 6);
            }
            case MOVE -> {
                cues.add(new Cue(0, () -> s.anim(me, move != null && move.moveStep < 0 ? "dash_forward" : "dash_back", 1f)));
                cues.add(new Cue(0, () -> s.sound(me, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.4f)));
                cues.add(new Cue(1, s::settleFast));
                return new Scene(12, 3, cues);
            }
            case FOCUS -> {
                cues.add(new Cue(0, () -> s.anim(me, "heavy_charge", 1f)));
                cues.add(new Cue(2, () -> s.fxAt(me, "domain_charge", false)));
                cues.add(new Cue(14, () -> s.stop(me)));
                return new Scene(18, 10, cues);
            }
            case GUARD -> {
                cues.add(new Cue(0, () -> s.anim(me, s.guardClip(me), 1f)));
                cues.add(new Cue(12, () -> s.stop(me)));
                return new Scene(14, 6, cues);
            }
            case STUNNED -> {
                cues.add(new Cue(0, () -> s.anim(me, "reaction_overload", 1f)));
                return new Scene(16, 4, cues);
            }
            case DOMAIN, DOMAIN_CLASH -> {
                boolean gojo = s.kit(me).character.equals("gojo");
                cues.add(new Cue(0, () -> s.anim(me, gojo ? "domain_sign" : "shrine_sign", 1f)));
                if (b.kind() == Beat.Kind.DOMAIN_CLASH) cues.add(new Cue(0, () -> s.anim(foe, s.kit(foe).character.equals("gojo") ? "domain_sign" : "shrine_sign", 1f)));
                cues.add(new Cue(4, () -> s.fxAt(me, "domain_charge", false)));
                cues.add(new Cue(30, () -> {
                    s.fxAt(foe, gojo ? "infinity_on" : "shrine_slash", true);
                    s.flash(gojo ? 0xFFE8F4FF : 0xFF600010);
                    s.shake(1f, 20);
                    s.sound(foe, SoundEvents.WARDEN_SONIC_BOOM, 1f, gojo ? 1.4f : 0.7f);
                    s.anim(foe, gojo ? "reaction_overload" : "reaction_hitstun", 1f);
                }));
                cues.add(new Cue(48, () -> {
                    s.stop(me);
                    s.settle();
                }));
                return new Scene(56, 30, cues);
            }
            case BURN -> {
                cues.add(new Cue(2, () -> {
                    s.fxAt(foe, "shrine_slash", true);
                    s.anim(foe, "reaction_hitstun", 1f);
                    s.sound(foe, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
                }));
                return new Scene(14, 2, cues);
            }
            case KO -> {
                cues.add(new Cue(0, () -> {
                    s.anim(foe, "reaction_knockdown", 0.7f);
                    s.shake(1f, 16);
                    s.sound(foe, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f);
                }));
                return new Scene(50, 0, cues);
            }
            default -> {
                return simple(cues, 8);
            }
        }
    }

    private static boolean landed(Beat b) {
        return switch (b.kind()) {
            case HIT, PIERCE, TRADE, CLASH -> b.damage() > 0;
            default -> false;
        };
    }

    private static void contact(BattleSession s, Beat b, BattleMove move, int me, int foe, MoveScene ms, boolean last, boolean multi) {
        switch (b.kind()) {
            case WHIFF -> {
                if (!last) return;
                s.sound(me, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.9f);
                s.anim(foe, "dash_left", 1f);
            }
            case BLOCK -> {
                s.anim(foe, s.guardClip(foe), 1f);
                s.sound(foe, SoundEvents.SHIELD_BLOCK, 1f, 1f);
                if (last) {
                    s.anim(me, "reaction_hitstun", 1f);
                    s.shake(0.3f, 4);
                }
            }
            case PARRY -> {
                s.anim(foe, s.guardClip(foe), 1f);
                s.sound(foe, SoundEvents.SHIELD_BLOCK, 1f, 0.6f);
                if (last) {
                    s.anim(me, "reaction_guard_broken", 1f);
                    s.anim(foe, "light_1", 1.2f);
                    s.shake(0.6f, 8);
                }
            }
            default -> {
                // A hit: effects every contact, the reaction for the stance it leaves on the last.
                s.fxAt(foe, ms.contactFx(), true);
                s.sound(foe, last && move != null && move.weight == BattleMove.Weight.HEAVY ? SoundEvents.PLAYER_ATTACK_CRIT
                        : SoundEvents.PLAYER_ATTACK_STRONG, 1f, multi && !last ? 1.3f : 1f);
                if (!last) {
                    s.anim(foe, "reaction_hitstun", 1.3f);
                    s.shake(0.2f, 3);
                    return;
                }
                if (b.blackFlash()) {
                    s.fxAt(foe, "black_flash_kokusen", true);
                    s.flash(0xFF100010);
                    s.shake(1.2f, 14);
                    s.sound(foe, SoundEvents.GENERIC_EXPLODE, 1f, 1.5f);
                } else {
                    s.shake(move != null && move.weight == BattleMove.Weight.HEAVY ? 0.8f : 0.4f, 6);
                }
                Stance st = b.targetStance();
                s.anim(foe, switch (st) {
                    case AIRBORNE -> "reaction_hit_launched";
                    case DOWN -> "reaction_knockdown";
                    default -> b.move().equals("blue") ? "reaction_pulled" : "reaction_hitstun";
                }, 1f);
                s.lift(foe, st == Stance.AIRBORNE ? 1.6 : 0);
                if (b.kind() == Beat.Kind.CLASH) s.flash(0xFFFFFFFF);
            }
        }
    }

    private static Scene simple(List<Cue> cues, int length) {
        return new Scene(length, 0, cues);
    }
}
