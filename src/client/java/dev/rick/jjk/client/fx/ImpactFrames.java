package dev.rick.jjk.client.fx;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Anime impact frames and the look of slowed time, drawn as full-screen post effects over the world (the HUD stays
 * readable on top). An impact frame is a few hard frames, timed in real milliseconds so it looks the same at any frame
 * rate:
 * <ul>
 *   <li>{@link Style#INK}: everything near you a solid black silhouette on white paper, focus lines rushing in (the two
 *   line patterns alternate, so it crackles); then one inverted frame; then back.</li>
 *   <li>{@link Style#BLACK_FLASH}: black silhouettes on crimson, then inverted, then crimson again.</li>
 *   <li>{@link Style#NEGATIVE}: the picture inverted and crushed to hard contrast.</li>
 * </ul>
 * {@link #slowMotion} drains colour, darkens the edges and smears them while a moment plays out "slowly" (the game
 * itself never slows: in multiplayer it couldn't, for one player alone).
 *
 * <p>Off with the Impact Frames setting ({@code client.impactFrames}) or Screen Flashes; slow motion's look is off under
 * Reduced Camera Motion too.
 */
public final class ImpactFrames {
    public enum Style { INK, BLACK_FLASH, NEGATIVE, BLACK_FLASH_LETHAL }

    /** An inked look: drawn with the figures-only mask, or (no bodies near enough to mask) from depth alone. */
    private record Ink(Identifier masked, Identifier depth) {}

    private static final Ink INK_A = new Ink(JJK.id("ink_mask_a"), JJK.id("ink_impact_a")), INK_B = new Ink(JJK.id("ink_mask_b"), JJK.id("ink_impact_b")),
            INK_REVERSE = new Ink(JJK.id("ink_mask_reverse"), JJK.id("ink_reverse")),
            BLACK_FLASH = new Ink(JJK.id("ink_mask_black_flash"), JJK.id("black_flash_impact"));
    private static final Identifier NEGATIVE = JJK.id("negative_impact"), SLOWMO = JJK.id("slowmo");
    /** Bodies within this many blocks of the camera are inked as figures. */
    public static final double FIGURE_RANGE = 24;

    /** One frame of a sequence: an inked look or a plain effect, until how many ms in. */
    private record Beat(@Nullable Ink ink, @Nullable Identifier effect, double untilMs) {
        Beat(Ink ink, double untilMs) {
            this(ink, null, untilMs);
        }

        Beat(Identifier effect, double untilMs) {
            this(null, effect, untilMs);
        }
    }

    private static List<Beat> sequence = List.of();
    private static long started;
    private static long slowUntil;
    @Nullable private static Identifier held;
    @Nullable private static Ink heldInk;

    private ImpactFrames() {}

    public static boolean enabled() {
        JJKConfig.Client c = JJKConfig.get().client;
        return c.impactFrames && c.screenFlashes && !c.reducedFlashes;
    }

    /** Plays an impact frame now ({@code strength} 0.5..2 stretches it: a bigger hit holds a little longer). */
    public static void play(Style style, float strength) {
        if (!enabled()) return;
        double k = Math.max(0.5, Math.min(2.0, strength));
        List<Beat> beats = new ArrayList<>();
        switch (style) {
            case INK -> {
                beats.add(new Beat(INK_A, 45 * k));
                beats.add(new Beat(INK_B, 90 * k));
                beats.add(new Beat(INK_REVERSE, 125 * k));
                beats.add(new Beat(INK_A, 160 * k));
            }
            case BLACK_FLASH -> {
                beats.add(new Beat(BLACK_FLASH, 60 * k));
                beats.add(new Beat(INK_REVERSE, 95 * k));
                beats.add(new Beat(BLACK_FLASH, 150 * k));
            }
            case BLACK_FLASH_LETHAL -> {
                // The lethal one (after the Resolute Black Flash finisher): crimson, inverted, crimson, the hard negative,
                // crimson again, held longer than any other frame.
                beats.add(new Beat(BLACK_FLASH, 90 * k));
                beats.add(new Beat(INK_REVERSE, 140 * k));
                beats.add(new Beat(BLACK_FLASH, 230 * k));
                beats.add(new Beat(NEGATIVE, 280 * k));
                beats.add(new Beat(BLACK_FLASH, 380 * k));
            }
            case NEGATIVE -> {
                beats.add(new Beat(NEGATIVE, 70 * k));
                beats.add(new Beat(INK_REVERSE, 100 * k));
            }
        }
        sequence = beats;
        started = System.nanoTime();
    }

    /** The slowed-time look for {@code ms} (extended if it is already on). */
    public static void slowMotion(int ms) {
        if (!enabled() || dev.rick.jjk.client.camera.CameraDirector.reducedMotion()) return;
        slowUntil = Math.max(slowUntil, System.nanoTime() + ms * 1_000_000L);
    }

    public static boolean slow() {
        return System.nanoTime() < slowUntil;
    }

    @Nullable
    private static Beat beat() {
        if (sequence.isEmpty()) return null;
        double t = (System.nanoTime() - started) / 1e6;
        for (Beat b : sequence) if (t < b.untilMs) return b;
        sequence = List.of();
        return null;
    }

    @Nullable
    private static Ink ink() {
        if (heldInk != null) return heldInk;
        if (held != null) return null;
        Beat b = beat();
        return b == null ? null : b.ink;
    }

    /** Whether bodies near the camera are being inked as figures this frame (they are drawn into the silhouette buffer). */
    public static boolean inking() {
        return ink() != null && figuresNear();
    }

    /** The figures-only chain to draw in place of the glowing outline this frame, or null. */
    @Nullable
    public static Identifier maskChain() {
        Ink i = ink();
        return i != null && figuresNear() ? i.masked : null;
    }

    /** Whether there is any body near enough to ink (else the depth-only look stands in). */
    private static boolean figuresNear() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        var cam = mc.gameRenderer.mainCamera().position();
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.LivingEntity && !e.isInvisible() && e.position().distanceToSqr(cam) < FIGURE_RANGE * FIGURE_RANGE) return true;
        }
        return false;
    }

    /** The post effects to draw this frame on top of the level (slowed time, a plain impact frame, an unmasked ink look). */
    public static List<Identifier> current() {
        List<Identifier> out = new ArrayList<>(2);
        if (held != null) {
            out.add(held);
            return out;
        }
        if (heldInk != null) {
            if (!figuresNear()) out.add(heldInk.depth);
            return out;
        }
        if (slow()) out.add(SLOWMO);
        Beat b = beat();
        if (b != null) {
            if (b.effect != null) out.add(b.effect);
            else if (!figuresNear()) out.add(b.ink.depth);
        }
        return out;
    }

    /** Whether any impact frame or slowed time is playing. */
    public static boolean playing() {
        return held != null || heldInk != null || slow() || beat() != null;
    }

    public static void reset() {
        sequence = List.of();
        slowUntil = 0;
        held = null;
        heldInk = null;
    }

    /** Tests: hold one plain effect on screen (null lets go). */
    public static void holdForTest(@Nullable String effect) {
        held = effect == null ? null : JJK.id(effect);
        heldInk = null;
    }

    /** Tests: hold one inked look on screen (0 ink, 1 ink b, 2 reverse, 3 Black Flash; -1 lets go). */
    public static void holdInkForTest(int which) {
        held = null;
        heldInk = which < 0 ? null : new Ink[] {INK_A, INK_B, INK_REVERSE, BLACK_FLASH}[which];
    }
}
