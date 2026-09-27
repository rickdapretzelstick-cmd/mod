package dev.rick.jjk.core.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Everything an ability needs when it activates. */
public record AbilityContext(AbilityCaster caster, LivingEntity user, ServerLevel level, AbilitySlot slot,
                             float forward, float strafe, @Nullable Entity targetHint) {

    /** World-space movement direction from WASD input (horizontal), or the look direction if there is none. */
    public Vec3 inputDirection() {
        if (Math.abs(forward) < 0.01f && Math.abs(strafe) < 0.01f) return user.getLookAngle();
        float yaw = user.getYRot() * ((float) Math.PI / 180f);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        Vec3 fwd = new Vec3(-sin, 0, cos);
        Vec3 left = new Vec3(cos, 0, sin);
        return fwd.scale(forward).add(left.scale(strafe)).normalize();
    }

    public boolean hasMovementInput() {
        return Math.abs(forward) > 0.01f || Math.abs(strafe) > 0.01f;
    }
}
