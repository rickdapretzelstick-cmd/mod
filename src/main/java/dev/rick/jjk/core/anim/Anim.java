package dev.rick.jjk.core.anim;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.AnimPayload;
import net.minecraft.world.entity.LivingEntity;

/** Server-side animation triggers. Animations themselves are defined client-side (see client anim package). */
public final class Anim {
    private Anim() {}

    public static void play(LivingEntity entity, String anim) {
        play(entity, anim, 1f);
    }

    /** Animations that hold their last pose until replaced (mirrors the client's held defs). */
    private static final java.util.Set<String> HELD = java.util.Set.of("heavy_charge", "guard", "red_charge", "purple_blue", "purple_red",
            "purple_fusion", "max_red_charge", "domain_sign", "rough_charge", "fever_rush", "door_guard", "idg_sign", "rushdown_run",
            "rushdown_drag", "surge_dash");
    /** Entities currently showing a held pose. */
    private static final java.util.Set<LivingEntity> HOLDING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static void play(LivingEntity entity, String anim, float speed) {
        if (entity.level().isClientSide()) return;
        if (HELD.contains(anim)) HOLDING.add(entity);
        else HOLDING.remove(entity);
        Fx.toTrackers(entity, new AnimPayload(entity.getId(), anim, speed), true);
    }

    public static void stop(LivingEntity entity) {
        play(entity, "", 1f);
    }

    /** Releases a held pose left over from a move that has ended, so the user isn't left frozen in it. */
    public static void releaseHeld(LivingEntity entity) {
        if (HOLDING.contains(entity)) stop(entity);
    }

    public static boolean isHolding(LivingEntity entity) {
        return HOLDING.contains(entity);
    }
}
