package dev.rick.jjk.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative velocity changes that are pushed to every client, including a player's own. */
public final class Motion {
    private Motion() {}

    public static void set(Entity e, Vec3 velocity) {
        e.setDeltaMovement(velocity);
        e.syncVelocity = true;
        e.needsSync = true;
        if (velocity.y > 0.05) e.setOnGround(false);
        e.resetFallDistance();
    }

    public static void add(Entity e, Vec3 delta) {
        set(e, e.getDeltaMovement().add(delta));
    }
}
