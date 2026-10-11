package dev.rick.jjk.core.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The impact kit, from the server: impact frames and the slowed-time look go to one player (the one the moment is
 * for); ground cracks, dust rings and shoves are seen by everyone nearby. Drawn by the client's ImpactFx.
 */
public final class Impact {
    /** Impact frame styles (the client's ImpactFrames.Style, in the same order). */
    public static final int INK = 0, BLACK_FLASH = 1, NEGATIVE = 2;

    private Impact() {}

    /** An anime impact frame on this player's screen ({@code strength} 0.5..2 holds it longer). */
    public static void frame(ServerPlayer p, int style, float strength) {
        Fx.play((ServerLevel) p.level(), "impact_frame", p.position(), new Vec3(style, 0, 0), strength, p.getId());
    }

    /** The slowed-time look on this player's screen for {@code ms}. */
    public static void slowMotion(ServerPlayer p, int ms) {
        Fx.play((ServerLevel) p.level(), "impact_slowmo", p.position(), Vec3.ZERO, ms / 100f, p.getId());
    }

    /** The ground splits at {@code at} (fissures biased along {@code along}; {@code size} 0.5..2). */
    public static void crack(ServerLevel level, Vec3 at, Vec3 along, float size) {
        Fx.play(level, "impact_crack", at, along, size);
    }

    /** A ring of dust rolling out along the ground. */
    public static void dust(ServerLevel level, Vec3 at, float size) {
        Fx.play(level, "impact_dust", at, Vec3.ZERO, size);
    }

    /** A directional burst of dust and air where bodies push. */
    public static void shove(ServerLevel level, Vec3 at, Vec3 direction, float size) {
        Fx.play(level, "impact_shove", at, direction, size);
    }
}
