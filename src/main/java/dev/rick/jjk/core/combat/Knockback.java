package dev.rick.jjk.core.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** How a hit moves its target. Knockback replaces the target's velocity so combos stay consistent. */
public record Knockback(Type type, Vec3 vector, Vec3 origin, double strength, double up) {
    public enum Type { NONE, SET, RADIAL, TOWARD, KEEP }

    public static final Knockback NONE = new Knockback(Type.NONE, Vec3.ZERO, Vec3.ZERO, 0, 0);
    /** Keeps the target roughly where it is (combo hits): cancels most current velocity. */
    public static final Knockback HOLD = new Knockback(Type.KEEP, Vec3.ZERO, Vec3.ZERO, 0, 0);

    public static Knockback set(Vec3 velocity) {
        return new Knockback(Type.SET, velocity, Vec3.ZERO, 0, 0);
    }

    /** Pushes along {@code direction}'s horizontal component with {@code strength}, plus {@code up}. */
    public static Knockback directional(Vec3 direction, double strength, double up) {
        Vec3 h = new Vec3(direction.x, 0, direction.z);
        h = h.lengthSqr() < 1e-6 ? Vec3.ZERO : h.normalize().scale(strength);
        return new Knockback(Type.SET, new Vec3(h.x, up, h.z), Vec3.ZERO, strength, up);
    }

    public static Knockback radial(Vec3 origin, double strength, double up) {
        return new Knockback(Type.RADIAL, Vec3.ZERO, origin, strength, up);
    }

    public static Knockback toward(Vec3 point, double strength) {
        return new Knockback(Type.TOWARD, Vec3.ZERO, point, strength, 0);
    }

    /** Computes the velocity to give the target, or null to leave velocity alone. */
    public Vec3 compute(LivingEntity target, float multiplier) {
        double resist = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double scale = multiplier * (1.0 - Math.min(0.6, resist * 0.6));
        return switch (type) {
            case NONE -> null;
            case KEEP -> target.getDeltaMovement().multiply(0.2, target.onGround() ? 0 : 0.2, 0.2);
            case SET -> vector.scale(scale);
            case RADIAL -> {
                Vec3 center = target.getBoundingBox().getCenter();
                Vec3 d = new Vec3(center.x - origin.x, 0, center.z - origin.z);
                if (d.lengthSqr() < 1e-4) d = new Vec3(target.getRandom().nextDouble() - 0.5, 0, target.getRandom().nextDouble() - 0.5);
                d = d.normalize().scale(strength * scale);
                yield new Vec3(d.x, up * scale, d.z);
            }
            case TOWARD -> {
                Vec3 d = origin.subtract(target.getBoundingBox().getCenter());
                double len = d.length();
                if (len < 1e-3) yield Vec3.ZERO;
                yield d.scale(Math.min(len, strength * scale) / len);
            }
        };
    }
}
