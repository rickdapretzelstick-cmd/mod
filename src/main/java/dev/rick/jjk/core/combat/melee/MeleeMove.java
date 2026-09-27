package dev.rick.jjk.core.combat.melee;

import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.hitbox.HitShape;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Frame data and behaviour for one melee attack.
 * Startup: telegraph, no hitbox. Active: hitbox live, each target hit once. Recovery: can be cancelled
 * into the next chain hit after {@link #chainAt} and into techniques/dash once the active frames are over.
 */
public record MeleeMove(
        String id,
        String anim,
        int startup,
        int active,
        int recovery,
        /** Ticks after start when the next attack in the chain may begin. */
        int chainAt,
        float movementMultiplier,
        Function<LivingEntity, HitShape> hitbox,
        BiFunction<LivingEntity, LivingEntity, Hit> hit,
        @Nullable Consumer<LivingEntity> onStart,
        @Nullable BiConsumer<LivingEntity, HitResult> onConnect) {

    public int total() {
        return startup + active + recovery;
    }
}
