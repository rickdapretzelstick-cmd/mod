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

    public static void play(LivingEntity entity, String anim, float speed) {
        if (entity.level().isClientSide()) return;
        Fx.toTrackers(entity, new AnimPayload(entity.getId(), anim, speed), true);
    }

    public static void stop(LivingEntity entity) {
        play(entity, "", 1f);
    }
}
