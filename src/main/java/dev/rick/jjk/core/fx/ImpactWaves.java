package dev.rick.jjk.core.fx;

import dev.rick.jjk.core.fx.ImpactWave.Builder;
import dev.rick.jjk.core.fx.ImpactWave.Direction;
import dev.rick.jjk.core.fx.ImpactWave.Ground;
import dev.rick.jjk.core.fx.ImpactWave.Signature;
import dev.rick.jjk.core.fx.ImpactWave.Theme;
import dev.rick.jjk.core.fx.ImpactWave.Tier;
import org.jetbrains.annotations.Nullable;

import static dev.rick.jjk.core.fx.ImpactWave.Direction.ALONG;
import static dev.rick.jjk.core.fx.ImpactWave.Direction.DOWN;
import static dev.rick.jjk.core.fx.ImpactWave.Direction.RADIAL;
import static dev.rick.jjk.core.fx.ImpactWave.Direction.SWEEP;
import static dev.rick.jjk.core.fx.ImpactWave.Direction.UP;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.BOLTS;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.CRESCENT;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.DISCHARGE;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.FLARE;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.SLASH;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.SMOKE;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.SPEEDLINES;
import static dev.rick.jjk.core.fx.ImpactWave.Signature.SPIRAL;
import static dev.rick.jjk.core.fx.ImpactWave.Tier.HEAVY;
import static dev.rick.jjk.core.fx.ImpactWave.Tier.LIGHT;
import static dev.rick.jjk.core.fx.ImpactWave.Tier.MEDIUM;
import static dev.rick.jjk.core.fx.ImpactWave.Tier.SPECIAL;
import static dev.rick.jjk.core.fx.ImpactWave.Tier.ULTIMATE;

/**
 * The library of impact-wave profiles: every blow that shocks the world has one of its own here, named
 * {@code <theme>.<move>}, built round what the move physically does and who is doing it.
 *
 * <p>The basic attacks resolve through {@link #melee(String, String)}: a character's own set ({@code gojo.light},
 * {@code hakari.finisher}...) where its identity needs one, the neutral {@code melee.*} set otherwise. The rest are
 * named by the moves that fire them ({@code Hit.Builder#wave}, {@code ImpactWave#play}).
 *
 * <p>Design notes, per character:
 * <ul>
 *   <li><b>Yuji</b>: force, not light. Compressed-air speed lines and a dark pressure dot on contact, dust where his
 *   feet dig in; cursed energy (cyan) only where the technique is the thing hitting (Divergent Fist's lag).</li>
 *   <li><b>Sukuna</b>: nothing is pushed, everything is cut. Thin black and white cuts, no dust, a dark blot.</li>
 *   <li><b>Gojo</b>: crisp and thin. A shock shell of displaced space, light winding in, never any dirt.</li>
 *   <li><b>Hakari</b>: a street fight. Dust, grit, and the flare of luck going off on the big ones.</li>
 *   <li><b>Yuta</b>: the blade's crescent, pink only where his energy is loose; the steel arm throws sparks.</li>
 *   <li><b>Rika</b>: mass. Dark smoke, the ground giving, long rolling shocks.</li>
 *   <li><b>Ryu</b>: Cursed Energy Discharge: electric arcs and steam, the ground answering his slams; Decadence
 *   runs gold-hot.</li>
 * </ul>
 */
public final class ImpactWaves {
    private static boolean loaded;

    private ImpactWaves() {}

    /** The wave for one of the basic attacks in a character's set, falling back to the neutral set. */
    public static String melee(String theme, String move) {
        if (theme != null && !theme.isEmpty() && ImpactWave.get(theme + "." + move) != null) return theme + "." + move;
        return "melee." + move;
    }

    static synchronized void init() {
        if (loaded) return;
        loaded = true;
        neutral();
        yuji();
        sukuna();
        gojo();
        hakari();
        yuta();
        rika();
        ryu();
    }

    private static Builder w(String name, Tier tier, Theme theme, Direction dir) {
        return new Builder(name, tier, theme).direction(dir);
    }

    /** The default basic-attack set: a brawler's, white and grey. */
    private static void neutral() {
        Theme t = Theme.NEUTRAL;
        w("melee.light", LIGHT, t, ALONG).radius(0.9f).register();
        w("melee.air", LIGHT, t, RADIAL).radius(0.8f).clean().register();
        w("melee.finisher", MEDIUM, t, ALONG).radius(1.8f).dust(0.6f).debris(0.4f).lift(2.4f).register();
        w("melee.uppercut", MEDIUM, t, UP).lift(2.4f).dust(0.6f).debris(0.3f).register();
        w("melee.downslam", HEAVY, t, DOWN).ground(Ground.ALWAYS).radius(2.4f).lens(0.8f).dust(1.2f).debris(1.0f).shake(0.4f, 10).delay(2).once(true).register();
        w("melee.sprint", MEDIUM, t, ALONG).radius(1.8f).lift(2.8f).dust(0.7f).debris(0.3f).register();
        w("melee.stomp", MEDIUM, t, DOWN).ground(Ground.ALWAYS).radius(1.6f).flash(0.9f).dust(1.0f).debris(0.8f).register();
        w("melee.heavy", HEAVY, t, ALONG).radius(2.4f).lens(1.4f).ink(0.4f).dust(1.0f).debris(0.6f).shake(0.45f, 12).fov(0.02f).register();
        w("melee.heavy_full", HEAVY, t, ALONG).radius(3.0f).rings(2).lens(2.0f).ink(0.6f).lift(3.0f).dust(1.2f).debris(0.8f).shake(0.6f, 14).fov(0.03f).delay(3).register();
    }

    private static void yuji() {
        Theme fist = Theme.NEUTRAL, ce = Theme.YUJI;
        // The basics: brutal and close. A dark pressure dot on every contact, the air driven through on the big ones.
        w("yuji.light", LIGHT, fist, ALONG).radius(0.8f).particles(6).velocity(0.45f).ink(0.25f).victim(0.3f).register();
        w("yuji.air", LIGHT, fist, RADIAL).radius(0.8f).clean().particles(5).register();
        w("yuji.finisher", MEDIUM, fist, ALONG).signature(SPEEDLINES).radius(1.9f).lift(2.6f).ink(0.4f).dust(0.8f).debris(0.5f).shake(0.3f, 9).register();
        w("yuji.uppercut", MEDIUM, fist, UP).lift(2.8f).ink(0.3f).dust(0.8f).debris(0.4f).register();
        w("yuji.downslam", HEAVY, fist, DOWN).ground(Ground.ALWAYS).radius(2.6f).dust(1.4f).debris(1.2f).shake(0.5f, 12).delay(2).once(true).register();
        w("yuji.sprint", MEDIUM, fist, ALONG).signature(SPEEDLINES).radius(1.8f).lift(3.2f).dust(0.7f).ink(0.3f).register();
        w("yuji.stomp", MEDIUM, fist, DOWN).ground(Ground.ALWAYS).radius(1.7f).dust(1.2f).debris(1.0f).register();
        w("yuji.heavy", HEAVY, fist, ALONG).signature(SPEEDLINES).radius(2.6f).lens(1.6f).ink(0.6f).lift(3.4f).dust(1.0f).debris(0.6f).shake(0.5f, 13).fov(0.025f).register();
        w("yuji.heavy_full", HEAVY, fist, ALONG).signature(SPEEDLINES).radius(3.2f).rings(2).lens(2.2f).ink(0.8f).lift(4.2f).dust(1.2f).debris(0.8f).shake(0.65f, 15).fov(0.03f).delay(3).register();
        // Divergent Fist: the fist is plain force; the cursed energy that lags behind it is what bursts outward.
        w("yuji.divergent", SPECIAL, fist, ALONG).signature(SPEEDLINES).radius(2.2f).lens(1.6f).ink(0.7f).lift(3.6f).dust(1.0f).debris(0.4f).shake(0.55f, 16).fov(0.03f).delay(0).register();
        w("yuji.divergent_lag", SPECIAL, ce, RADIAL).signature(BOLTS).radius(3.0f).rings(2).lens(2.4f).ink(0.5f).dust(0.6f).debris(0.4f).shake(0.6f, 18).fov(0.03f).register();
        // Cursed Strikes: the last punch of the rush, the calf kick's chop, the floor struck to launch, the dive.
        w("yuji.strikes_final", SPECIAL, fist, ALONG).signature(SPEEDLINES).radius(2.4f).lens(1.4f).ink(0.6f).lift(3.4f).dust(0.9f).debris(0.4f).shake(0.5f, 14).fov(0.025f).register();
        w("yuji.strikes_kick", MEDIUM, fist, SWEEP).signature(CRESCENT).radius(1.6f).dust(0.3f).debris(0f).ink(0.3f).register();
        w("yuji.strikes_floor", HEAVY, ce, UP).ground(Ground.ALWAYS).radius(2.8f).lift(3.2f).dust(1.5f).debris(1.4f).shake(0.5f, 14).once(true).register();
        w("yuji.strikes_dive", HEAVY, ce, DOWN).ground(Ground.ALWAYS).radius(3.2f).rings(2).dust(1.8f).debris(1.6f).shake(0.7f, 18).delay(3).once(true).register();
        // Crushing Blow: the slams into the floor, the fling skyward, the suplex.
        w("yuji.crushing_slam", SPECIAL, ce, DOWN).signature(SMOKE).ground(Ground.ALWAYS).radius(3.4f).rings(2).lens(1.8f).ink(0.7f).dust(1.8f).debris(1.8f).shake(0.8f, 20).fov(0.03f).delay(3).once(true).register();
        w("yuji.crushing_floor", SPECIAL, ce, DOWN).signature(SMOKE).ground(Ground.ALWAYS).radius(3.6f).rings(2).lens(2.0f).ink(0.7f).dust(2.0f).debris(2.0f).shake(0.8f, 20).fov(0.03f).delay(3).victim(0).once(true).register();
        w("yuji.crushing_release", MEDIUM, ce, UP).lift(3.4f).dust(0.6f).debris(0.3f).register();
        w("yuji.crushing_suplex", HEAVY, fist, DOWN).ground(Ground.ALWAYS).radius(2.8f).dust(1.6f).debris(1.4f).shake(0.6f, 16).once(true).register();
        // Manji Kick: the rising roundhouse, and the slam that ends the leg lock.
        w("yuji.manji", SPECIAL, fist, SWEEP).signature(CRESCENT).radius(2.2f).lift(2.4f).lens(1.0f).ink(0.4f).dust(0.4f).debris(0f).shake(0.4f, 12).register();
        w("yuji.manji_slam", HEAVY, fist, DOWN).ground(Ground.ALWAYS).radius(2.8f).dust(1.6f).debris(1.4f).shake(0.6f, 16).once(true).register();
    }

    private static void sukuna() {
        Theme t = Theme.SUKUNA;
        // Shrine: every basic is a slash. Nothing is pushed, nothing kicked up.
        w("sukuna.light", LIGHT, t, SWEEP).signature(SLASH).clean().radius(0.9f).particles(3).flash(0.6f).ink(0.3f).register();
        w("sukuna.air", LIGHT, t, SWEEP).signature(SLASH).clean().radius(0.9f).particles(3).flash(0.6f).ink(0.3f).register();
        w("sukuna.finisher", MEDIUM, t, SWEEP).signature(SLASH).clean().radius(1.7f).ink(0.5f).flash(1.1f).shake(0.2f, 8).register();
        w("sukuna.sprint", MEDIUM, t, SWEEP).signature(SLASH).clean().radius(1.8f).ink(0.5f).lift(3.0f).register();
        w("sukuna.stomp", MEDIUM, t, DOWN).ground(Ground.ALWAYS).radius(1.6f).dust(0.5f).debris(0.4f).register();
        w("sukuna.heavy", HEAVY, t, SWEEP).signature(SLASH).clean().radius(2.4f).ink(0.7f).lens(1.2f).shake(0.4f, 12).register();
        w("sukuna.heavy_full", HEAVY, t, SWEEP).signature(SLASH).clean().radius(3.0f).rings(2).ink(0.9f).lens(1.6f).shake(0.5f, 14).fov(0.025f).register();
        // Rush: the knee skyward, and the slam back down.
        w("sukuna.rush_knee", MEDIUM, t, UP).lift(2.8f).clean().ink(0.4f).register();
        w("sukuna.rush_slam", HEAVY, t, DOWN).ground(Ground.ALWAYS).radius(3.0f).rings(2).dust(1.4f).debris(1.2f).ink(0.6f).shake(0.6f, 16).once(true).register();
    }

    private static void gojo() {
        Theme t = Theme.GOJO;
        // The basics: thin crisp rings and a small shell of displaced air, never any dirt.
        w("gojo.light", LIGHT, t, ALONG).clean().radius(1.1f).thickness(0.06f).flash(0.8f).particles(3).lens(0.6f).register();
        w("gojo.air", LIGHT, t, RADIAL).clean().radius(1.0f).thickness(0.06f).flash(0.8f).particles(3).lens(0.6f).register();
        w("gojo.finisher", MEDIUM, t, ALONG).clean().radius(2.2f).thickness(0.08f).rings(2).lens(1.4f).particles(6).lift(2.8f).shake(0.2f, 8).register();
        w("gojo.uppercut", MEDIUM, t, UP).clean().lift(3.2f).lens(1.2f).thickness(0.08f).particles(6).register();
        w("gojo.downslam", HEAVY, t, DOWN).radius(2.6f).thickness(0.1f).lens(1.8f).ink(0.2f).dust(0.4f).debris(0.3f).shake(0.4f, 12).once(true).register();
        w("gojo.sprint", MEDIUM, t, ALONG).clean().radius(2.0f).thickness(0.08f).lens(1.6f).lift(3.6f).particles(6).register();
        w("gojo.stomp", MEDIUM, t, DOWN).radius(1.6f).dust(0.3f).debris(0.3f).flash(0.9f).register();
        w("gojo.heavy", HEAVY, t, ALONG).signature(SPIRAL).clean().radius(2.4f).thickness(0.08f).rings(2).lens(2.0f).ink(0.2f).lift(3.2f).shake(0.4f, 12).fov(0.02f).register();
        w("gojo.heavy_full", HEAVY, t, ALONG).signature(SPIRAL).clean().radius(3.0f).thickness(0.1f).rings(3).lens(2.6f).ink(0.3f).lift(4.0f).shake(0.55f, 14).fov(0.03f).delay(3).register();
        // Lapse Blue's kick, Twofold Kick's two, Rapid Punches' heavies and final blow, the kick from above, the
        // strike out of a teleport, Face Grater's toss: every one a crisp displacement of space.
        w("gojo.blue_kick", SPECIAL, t, ALONG).signature(SPIRAL).clean().radius(2.8f).thickness(0.1f).rings(2).lens(2.4f).lift(3.8f).shake(0.5f, 16).fov(0.03f).register();
        w("gojo.twofold_1", SPECIAL, t, UP).clean().radius(2.2f).thickness(0.09f).rings(2).lens(1.8f).lift(3.4f).shake(0.35f, 12).register();
        w("gojo.twofold_2", SPECIAL, t, UP).signature(SPIRAL).clean().radius(2.8f).thickness(0.1f).rings(3).lens(2.6f).lift(4.8f).shake(0.5f, 16).fov(0.03f).register();
        w("gojo.rapid_heavy", MEDIUM, t, ALONG).clean().radius(1.6f).thickness(0.08f).lens(0.8f).particles(6).victim(0.4f).register();
        w("gojo.rapid_final", SPECIAL, t, ALONG).signature(SPIRAL).clean().radius(3.0f).thickness(0.1f).rings(3).lens(2.8f).lift(5.0f).shake(0.6f, 18).fov(0.035f).delay(3).register();
        w("gojo.air_kick", HEAVY, t, DOWN).ground(Ground.ALWAYS).radius(2.8f).thickness(0.1f).lens(1.8f).dust(0.6f).debris(0.5f).shake(0.5f, 14).once(true).register();
        w("gojo.teleport_strike", SPECIAL, t, ALONG).signature(SPIRAL).clean().radius(2.4f).thickness(0.09f).rings(2).lens(2.0f).lift(3.4f).shake(0.4f, 14).fov(0.025f).register();
        // Reversal Red loosed point-blank into their back: its burst, driven on through them.
        w("gojo.red_pointblank", SPECIAL, new Theme("red", Theme.WHITE, new float[] {1f, 0.55f, 0.2f}, new float[] {1f, 0.18f, 0.12f}, new float[] {0.3f, 0.12f, 0.1f}), ALONG)
                .signature(Signature.SPIKES).radius(3.2f).rings(2).lens(2.4f).ink(0.5f).lift(5.0f).dust(1.0f).debris(0.6f).shake(0.7f, 20).fov(0.035f).delay(3).register();
        w("gojo.face_grater", HEAVY, t, ALONG).radius(2.4f).thickness(0.1f).lens(1.4f).dust(0.8f).debris(0.4f).lift(3.6f).shake(0.45f, 14).register();
    }

    private static void hakari() {
        Theme t = Theme.HAKARI;
        // The basics: swung too wide and landed anyway, dust and scuffs with everything.
        w("hakari.light", LIGHT, t, ALONG).radius(1.0f).particles(6).dust(0.2f).ink(0.2f).register();
        w("hakari.air", LIGHT, t, RADIAL).radius(0.9f).clean().particles(5).register();
        w("hakari.finisher", MEDIUM, t, ALONG).signature(FLARE).radius(2.0f).ink(0.4f).lift(2.8f).dust(1.0f).debris(0.5f).shake(0.3f, 9).register();
        w("hakari.uppercut", MEDIUM, t, UP).signature(FLARE).lift(3.0f).dust(0.9f).debris(0.4f).register();
        w("hakari.downslam", HEAVY, t, DOWN).ground(Ground.ALWAYS).radius(2.6f).dust(1.5f).debris(1.3f).shake(0.5f, 12).delay(2).once(true).register();
        w("hakari.sprint", MEDIUM, t, ALONG).radius(1.9f).lift(3.2f).dust(1.0f).ink(0.4f).register();
        w("hakari.stomp", MEDIUM, t, DOWN).ground(Ground.ALWAYS).radius(1.8f).dust(1.4f).debris(1.0f).register();
        w("hakari.heavy", HEAVY, t, ALONG).signature(FLARE).radius(2.6f).lens(1.4f).ink(0.6f).lift(3.2f).dust(1.2f).debris(0.6f).shake(0.5f, 13).fov(0.025f).register();
        w("hakari.heavy_full", HEAVY, t, ALONG).signature(FLARE).radius(3.2f).rings(2).lens(2.0f).ink(0.8f).lift(4.2f).dust(1.4f).debris(0.8f).shake(0.65f, 15).fov(0.03f).delay(3).register();
        // Rough Energy: the punch that sends them flying, and the stomp that launches everyone around upward.
        w("hakari.rough", SPECIAL, t, ALONG).signature(FLARE).radius(3.2f).rings(2).lens(2.0f).ink(0.7f).lift(4.6f).dust(1.4f).debris(0.8f).shake(0.7f, 18).fov(0.03f).delay(3).register();
        w("hakari.rough_stomp", SPECIAL, t, UP).signature(FLARE).ground(Ground.ALWAYS).radius(4.0f).rings(2).lift(4.0f).dust(2.0f).debris(1.8f).shake(0.8f, 20).fov(0.03f).delay(3).once(true).register();
        // Fever Breaker: the reaching kick, the dropkick off the doors, the axe kick through them.
        w("hakari.fever_kick", MEDIUM, t, ALONG).radius(1.8f).lift(2.2f).dust(0.5f).debris(0.2f).ink(0.3f).register();
        w("hakari.fever_break", SPECIAL, t, ALONG).signature(FLARE).radius(3.0f).rings(2).lens(1.6f).ink(0.5f).lift(5.2f).dust(1.2f).debris(0.5f).shake(0.6f, 16).fov(0.03f).register();
        w("hakari.fever_axe", SPECIAL, t, DOWN).ground(Ground.ALWAYS).radius(3.0f).rings(2).ink(0.6f).dust(1.8f).debris(1.6f).shake(0.7f, 18).fov(0.03f).delay(3).once(true).register();
        // Jackpot: Lucky Volley's blows, Lucky Rushdown's throw, Overwhelming Luck's hits and its last punch, Energy
        // Surge's dash punch and the kick down out of the sky; Door Guard's answer.
        w("hakari.volley_hit", LIGHT, t, ALONG).radius(0.8f).particles(4).flash(0.7f).register();
        w("hakari.volley_final", SPECIAL, t, ALONG).signature(FLARE).radius(2.8f).rings(2).lens(1.4f).ink(0.5f).lift(4.6f).dust(1.0f).debris(0.4f).shake(0.55f, 15).fov(0.03f).register();
        w("hakari.rushdown_throw", SPECIAL, t, ALONG).signature(FLARE).radius(2.6f).rings(2).lens(1.2f).lift(4.2f).dust(1.2f).debris(0.5f).shake(0.5f, 14).fov(0.025f).register();
        w("hakari.overwhelm_hit", MEDIUM, t, ALONG).signature(FLARE).radius(1.8f).dust(0.6f).ink(0.3f).register();
        w("hakari.overwhelm_final", SPECIAL, t, ALONG).signature(FLARE).radius(3.4f).rings(3).lens(2.2f).ink(0.7f).lift(5.2f).dust(1.6f).debris(1.0f).shake(0.8f, 22).fov(0.035f).delay(4).register();
        w("hakari.surge_hit", MEDIUM, t, UP).signature(FLARE).lift(4.2f).dust(0.8f).debris(0.3f).register();
        w("hakari.surge_kick", SPECIAL, t, DOWN).signature(FLARE).ground(Ground.ALWAYS).radius(3.2f).rings(2).dust(1.6f).debris(1.4f).shake(0.7f, 18).fov(0.03f).once(true).register();
        w("hakari.door_counter", MEDIUM, t, ALONG).signature(FLARE).radius(2.0f).lens(1.0f).lift(3.0f).dust(0.4f).register();
    }

    private static void yuta() {
        Theme t = Theme.YUTA, s = Theme.STEEL;
        // The katana: every basic a cut, drawn through; pink only on the blows his energy is loose in.
        w("yuta.light", LIGHT, t, SWEEP).signature(CRESCENT).clean().radius(1.0f).particles(4).flash(0.7f).register();
        w("yuta.air", LIGHT, t, SWEEP).signature(CRESCENT).clean().radius(1.0f).particles(4).flash(0.7f).register();
        w("yuta.finisher", MEDIUM, t, SWEEP).signature(CRESCENT).clean().radius(1.8f).thickness(0.07f).ink(0.3f).shake(0.2f, 8).register();
        w("yuta.uppercut", MEDIUM, t, UP).signature(CRESCENT).clean().lift(3.0f).thickness(0.07f).register();
        w("yuta.downslam", HEAVY, t, DOWN).signature(CRESCENT).radius(2.4f).dust(0.5f).debris(0.4f).shake(0.4f, 10).once(true).register();
        w("yuta.sprint", MEDIUM, t, ALONG).signature(CRESCENT).clean().radius(1.8f).lift(3.2f).register();
        w("yuta.stomp", MEDIUM, t, DOWN).radius(1.5f).dust(0.6f).debris(0.4f).flash(0.8f).register();
        w("yuta.heavy", HEAVY, t, SWEEP).signature(CRESCENT).clean().radius(2.6f).thickness(0.08f).lens(1.2f).ink(0.5f).shake(0.4f, 12).fov(0.02f).register();
        w("yuta.heavy_full", HEAVY, t, SWEEP).signature(CRESCENT).clean().radius(3.2f).thickness(0.09f).rings(2).lens(1.6f).ink(0.7f).shake(0.55f, 14).fov(0.03f).register();
        // The steel arm: a hammer, gold sparks off the casing.
        w("steel.light", LIGHT, s, ALONG).radius(1.0f).particles(8).velocity(0.6f).ink(0.2f).register();
        w("steel.air", LIGHT, s, RADIAL).radius(0.9f).clean().particles(6).velocity(0.6f).register();
        w("steel.finisher", MEDIUM, s, ALONG).radius(2.0f).particles(12).velocity(0.65f).ink(0.4f).dust(0.6f).debris(0.4f).lift(2.6f).shake(0.3f, 9).register();
        w("steel.uppercut", MEDIUM, s, UP).lift(3.0f).particles(12).velocity(0.65f).dust(0.6f).register();
        w("steel.downslam", HEAVY, s, DOWN).ground(Ground.ALWAYS).radius(2.6f).particles(16).dust(1.2f).debris(1.0f).shake(0.5f, 12).once(true).register();
        w("steel.sprint", MEDIUM, s, ALONG).radius(1.9f).lift(3.2f).particles(12).velocity(0.65f).dust(0.6f).register();
        w("steel.stomp", MEDIUM, s, DOWN).ground(Ground.ALWAYS).radius(1.7f).dust(1.0f).debris(0.8f).register();
        w("steel.heavy", HEAVY, s, ALONG).radius(2.6f).lens(1.4f).ink(0.6f).particles(18).velocity(0.7f).dust(1.0f).debris(0.6f).lift(3.2f).shake(0.5f, 13).fov(0.025f).register();
        w("steel.heavy_full", HEAVY, s, ALONG).radius(3.2f).rings(2).lens(2.0f).ink(0.8f).particles(24).velocity(0.75f).dust(1.2f).debris(0.8f).lift(4.2f).shake(0.65f, 15).fov(0.03f).delay(3).register();
        // Severing Path (the sweep along the floor, the three swings, the launch), Veilstep, Resolute Slash.
        w("yuta.severing_sweep", MEDIUM, t, ALONG).signature(CRESCENT).radius(1.8f).dust(0.6f).debris(0.2f).lift(2.4f).register();
        w("yuta.severing", MEDIUM, t, SWEEP).signature(CRESCENT).clean().radius(1.6f).thickness(0.07f).ink(0.3f).register();
        w("yuta.severing_launch", SPECIAL, t, UP).signature(CRESCENT).clean().radius(2.4f).rings(2).lift(4.2f).thickness(0.08f).shake(0.4f, 12).fov(0.02f).register();
        w("yuta.veilstep", MEDIUM, t, UP).lift(3.2f).dust(0.5f).debris(0.2f).register();
        w("yuta.resolute", SPECIAL, t, SWEEP).signature(CRESCENT).clean().radius(2.6f).thickness(0.06f).rings(2).ink(0.6f).lens(1.0f).shake(0.45f, 14).fov(0.025f).register();
        // Second Wind: the face slammed into the floor; the pummel's kick and its last blow.
        w("yuta.second_wind_slam", SPECIAL, t, DOWN).ground(Ground.ALWAYS).radius(3.0f).rings(2).ink(0.6f).dust(1.6f).debris(1.4f).shake(0.7f, 18).fov(0.03f).delay(3).once(true).register();
        w("yuta.pummel_hit", LIGHT, t, ALONG).radius(0.8f).particles(4).register();
        w("yuta.pummel_kick", MEDIUM, t, DOWN).ground(Ground.ALWAYS).radius(2.0f).dust(1.0f).debris(0.6f).shake(0.3f, 10).register();
        w("yuta.pummel_final", SPECIAL, t, ALONG).signature(CRESCENT).radius(2.6f).rings(2).lift(4.4f).ink(0.5f).dust(0.8f).debris(0.3f).shake(0.5f, 14).fov(0.025f).register();
        // Outburst's burst from the draw, Energy Ripple's blade in the floor (the dome is its own effect: this is the
        // ground answering it), Fakeout's energy bursting inside them, Elbow Rush's elbow and last blow.
        w("yuta.outburst", SPECIAL, t, UP).signature(BOLTS).ground(Ground.ALWAYS).radius(3.6f).rings(3).lens(2.4f).lift(4.2f).dust(1.4f).debris(1.0f).shake(0.7f, 20).fov(0.03f).delay(3).victim(0).once(true).register();
        w("yuta.energy_ripple", HEAVY, t, DOWN).ground(Ground.ALWAYS).radius(5.0f).life(10).rings(3).flash(0.6f).lens(0f).dust(2.0f).debris(1.5f).shake(0.8f, 24).fov(0.03f).delay(4).victim(0).once(true).register();
        w("yuta.fakeout", MEDIUM, t, SWEEP).signature(CRESCENT).clean().radius(1.8f).ink(0.4f).register();
        w("yuta.fakeout_burst", SPECIAL, t, RADIAL).signature(BOLTS).clean().radius(2.6f).rings(2).lens(1.8f).ink(0.5f).shake(0.5f, 14).fov(0.025f).register();
        w("yuta.elbow_hit", MEDIUM, t, ALONG).radius(1.8f).ink(0.4f).lift(2.6f).dust(0.4f).register();
        w("yuta.elbow_final", SPECIAL, t, ALONG).signature(BOLTS).radius(3.0f).rings(2).lens(1.6f).ink(0.5f).lift(4.6f).dust(0.8f).debris(0.3f).shake(0.6f, 16).fov(0.03f).register();
    }

    private static void rika() {
        Theme t = Theme.RIKA;
        // Mass. Everything she does lands as weight: dark smoke, the ground giving, a long roll.
        w("rika.smash", SPECIAL, t, DOWN).signature(SMOKE).ground(Ground.ALWAYS).radius(3.6f).rings(2).flash(1.6f).ink(0.8f).dust(2.0f).debris(2.0f).shake(0.9f, 22).fov(0.03f).delay(4).once(true).register();
        w("rika.downslam", SPECIAL, t, DOWN).signature(SMOKE).ground(Ground.ALWAYS).radius(3.2f).rings(2).flash(1.5f).ink(0.8f).dust(1.8f).debris(1.8f).shake(0.8f, 20).fov(0.03f).delay(4).once(true).register();
        w("rika.slam", HEAVY, t, DOWN).signature(SMOKE).ground(Ground.ALWAYS).radius(2.6f).flash(1.3f).ink(0.6f).dust(1.4f).debris(1.2f).shake(0.5f, 14).once(true).register();
        w("rika.slam_final", SPECIAL, t, DOWN).signature(SMOKE).ground(Ground.ALWAYS).radius(3.4f).rings(2).flash(1.6f).ink(0.8f).dust(2.0f).debris(1.8f).shake(0.8f, 20).fov(0.03f).delay(4).once(true).register();
        w("rika.haymaker", SPECIAL, t, ALONG).signature(SMOKE).radius(3.6f).rings(3).flash(1.8f).lens(2.6f).ink(0.9f).lift(6.0f).dust(1.6f).debris(0.8f).shake(0.9f, 24).fov(0.04f).delay(4).register();
        w("rika.throw_crash", HEAVY, t, ALONG).signature(SMOKE).radius(2.8f).flash(1.4f).ink(0.6f).lift(3.6f).dust(1.4f).debris(1.0f).shake(0.6f, 16).fov(0.025f).register();
    }

    private static void ryu() {
        Theme t = Theme.RYU, d = Theme.DECADENCE;
        // The basics: full-weight slugs, a step behind every one.
        w("ryu.light", LIGHT, t, ALONG).radius(1.0f).particles(6).velocity(0.45f).ink(0.25f).dust(0.2f).victim(0.3f).register();
        w("ryu.air", LIGHT, t, RADIAL).radius(0.9f).clean().particles(5).register();
        w("ryu.finisher", MEDIUM, t, ALONG).radius(2.0f).ink(0.5f).lift(2.8f).dust(0.9f).debris(0.5f).shake(0.3f, 10).register();
        w("ryu.uppercut", MEDIUM, t, UP).lift(3.2f).dust(0.9f).debris(0.4f).register();
        w("ryu.downslam", HEAVY, t, DOWN).ground(Ground.ALWAYS).radius(2.6f).dust(1.6f).debris(1.4f).shake(0.55f, 13).delay(2).once(true).register();
        w("ryu.sprint", MEDIUM, t, ALONG).radius(1.9f).lift(3.4f).dust(1.0f).ink(0.5f).register();
        w("ryu.stomp", MEDIUM, t, DOWN).ground(Ground.ALWAYS).radius(1.8f).dust(1.4f).debris(1.1f).register();
        w("ryu.heavy", HEAVY, t, ALONG).signature(DISCHARGE).radius(2.8f).lens(1.6f).ink(0.7f).lift(3.6f).dust(1.2f).debris(0.6f).shake(0.55f, 14).fov(0.03f).register();
        w("ryu.heavy_full", HEAVY, t, ALONG).signature(DISCHARGE).radius(3.4f).rings(2).lens(2.2f).ink(0.9f).lift(4.6f).dust(1.4f).debris(0.8f).shake(0.7f, 16).fov(0.035f).delay(3).register();
        // Cursed Energy Discharge: the M1 ray and Appetizer's blasts, Granite Blast's two shots.
        w("ryu.ray", MEDIUM, t, ALONG).signature(DISCHARGE).clean().radius(1.8f).particles(12).velocity(0.6f).lift(2.4f).register();
        w("ryu.granite", SPECIAL, t, ALONG).signature(DISCHARGE).clean().radius(2.4f).lens(1.4f).particles(16).velocity(0.65f).lift(3.4f).shake(0.4f, 14).fov(0.02f).register();
        w("ryu.granite_held", SPECIAL, t, ALONG).signature(DISCHARGE).clean().radius(3.0f).rings(2).lens(2.0f).particles(22).velocity(0.75f).lift(5.0f).shake(0.6f, 18).fov(0.03f).register();
        // Unsatisfied: the back driven into them, and the toss.
        w("ryu.tetsuzanko", SPECIAL, t, ALONG).signature(DISCHARGE).radius(3.0f).rings(2).ink(0.7f).lift(4.0f).dust(1.2f).debris(0.5f).shake(0.6f, 16).fov(0.03f).register();
        w("ryu.toss", SPECIAL, t, UP).signature(DISCHARGE).radius(2.6f).lift(5.0f).dust(1.0f).debris(0.4f).shake(0.4f, 14).register();
        // Second Helping: the slam into the floor; in the air the punch, and the discharge in it going off a beat later.
        w("ryu.helping_slam", SPECIAL, t, DOWN).signature(DISCHARGE).ground(Ground.ALWAYS).radius(3.6f).rings(2).ink(0.7f).dust(1.8f).debris(1.8f).shake(0.8f, 22).fov(0.03f).delay(3).once(true).register();
        w("ryu.helping_air", MEDIUM, t, ALONG).signature(DISCHARGE).clean().radius(1.8f).ink(0.4f).register();
        w("ryu.delayed", SPECIAL, t, DOWN).signature(DISCHARGE).ground(Ground.ALWAYS).radius(3.0f).rings(2).dust(1.4f).debris(1.0f).shake(0.6f, 18).fov(0.03f).once(true).register();
        // Appetizer's ray out of the ground, lifting them.
        w("ryu.appetizer_ray", SPECIAL, t, UP).signature(DISCHARGE).radius(2.6f).lift(5.0f).dust(0.8f).debris(0.6f).shake(0.4f, 14).register();
        // Decadence. "What are you after?": the floor, then the trade and the launch.
        w("ryu.floor_slam", SPECIAL, d, UP).signature(DISCHARGE).ground(Ground.ALWAYS).radius(4.0f).rings(2).lift(4.0f).dust(2.0f).debris(1.8f).shake(0.8f, 22).fov(0.03f).delay(3).victim(0).once(true).register();
        w("ryu.after_hit", HEAVY, d, ALONG).signature(DISCHARGE).radius(2.4f).ink(0.6f).lift(3.4f).dust(0.6f).register();
        w("ryu.after_launch", SPECIAL, d, ALONG).signature(DISCHARGE).radius(3.2f).rings(2).lens(2.0f).ink(0.7f).lift(5.0f).dust(1.0f).debris(0.4f).shake(0.7f, 18).fov(0.035f).register();
        // "I had no idea...": the grab, fist on fist, the shove.
        w("ryu.noidea_grab", HEAVY, d, ALONG).signature(DISCHARGE).radius(2.4f).ink(0.5f).lift(3.0f).dust(0.6f).register();
        w("ryu.noidea_blow", LIGHT, d, ALONG).signature(DISCHARGE).clean().radius(0.9f).particles(8).velocity(0.5f).flash(0.8f).register();
        w("ryu.noidea_push", SPECIAL, d, ALONG).signature(DISCHARGE).radius(2.6f).lift(4.0f).ink(0.5f).dust(0.8f).shake(0.5f, 14).register();
        // "This is what dessert is like!": the kick, the swing that takes hold, the blows, the kick away.
        w("ryu.dessert_kick", MEDIUM, d, ALONG).radius(1.8f).dust(0.6f).ink(0.4f).register();
        w("ryu.dessert_swing", HEAVY, d, SWEEP).signature(DISCHARGE).radius(2.2f).ink(0.5f).dust(0.4f).register();
        w("ryu.dessert_blow", LIGHT, d, ALONG).signature(DISCHARGE).clean().radius(0.9f).particles(8).velocity(0.5f).flash(0.8f).register();
        w("ryu.dessert_push", SPECIAL, d, ALONG).signature(DISCHARGE).radius(3.0f).rings(2).lift(4.6f).ink(0.6f).dust(1.0f).shake(0.6f, 16).fov(0.03f).register();
        // "You weren't invited.": the punch that sends them to his left, and the wall that flies.
        w("ryu.invited", ULTIMATE, d, ALONG).signature(DISCHARGE).radius(3.8f).rings(3).lens(2.8f).ink(0.9f).lift(7.0f).dust(1.6f).debris(1.0f).shake(1.0f, 26).fov(0.04f).delay(4).register();
    }

    /** For tests: whether a profile name resolves. */
    public static boolean exists(@Nullable String name) {
        return name != null && ImpactWave.get(name) != null;
    }
}
