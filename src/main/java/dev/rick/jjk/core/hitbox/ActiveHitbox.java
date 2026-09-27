package dev.rick.jjk.core.hitbox;

import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A hitbox that persists for several ticks (a lingering blast, a sweeping beam). Each target can be hit at most once
 * per {@code rehitInterval} ticks (or once total if the interval is 0).
 */
public final class ActiveHitbox {
    private final LivingEntity owner;
    private final Supplier<HitShape> shape;
    private final Function<LivingEntity, Hit> hitFactory;
    private final int duration;
    private final int rehitInterval;
    private final double tolerance;
    private final boolean lineOfSight;
    private final Map<UUID, Integer> lastHit = new HashMap<>();
    private int age;

    public ActiveHitbox(LivingEntity owner, Supplier<HitShape> shape, Function<LivingEntity, Hit> hitFactory, int duration, int rehitInterval,
                        double tolerance, boolean lineOfSight) {
        this.owner = owner;
        this.shape = shape;
        this.hitFactory = hitFactory;
        this.duration = duration;
        this.rehitInterval = rehitInterval;
        this.tolerance = tolerance;
        this.lineOfSight = lineOfSight;
    }

    /** Returns false once expired. */
    public boolean tick() {
        if (age++ >= duration || owner.isRemoved()) return false;
        HitShape s = shape.get();
        if (s == null) return false;
        for (LivingEntity t : HitboxQuery.targets(owner, s, tolerance, lineOfSight)) {
            Integer last = lastHit.get(t.getUUID());
            if (last != null && (rehitInterval <= 0 || age - last < rehitInterval)) continue;
            HitResult r = HitResolver.resolve(hitFactory.apply(t), t);
            if (r.outcome().contacted()) lastHit.put(t.getUUID(), age);
        }
        return true;
    }

    public LivingEntity owner() {
        return owner;
    }
}
