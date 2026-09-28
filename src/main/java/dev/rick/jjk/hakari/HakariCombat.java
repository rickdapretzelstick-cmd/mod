package dev.rick.jjk.hakari;

import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Motion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Small shared pieces of Hakari's close-range kit: facing, strike boxes and resolving hits. */
final class HakariCombat {
    private HakariCombat() {}

    /** Horizontal facing. */
    static Vec3 flat(LivingEntity e) {
        Vec3 l = e.getLookAngle();
        Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** Everyone in a box in front of the user, from its chest outward. */
    static List<LivingEntity> front(LivingEntity user, double reach, double width, double height) {
        Vec3 origin = user.position().add(0, user.getBbHeight() * 0.5, 0);
        return HitboxQuery.targets(user, HitShape.orientedBox(origin.subtract(flat(user).scale(0.3)), flat(user), reach + 0.3, width, height), 0.3, false);
    }

    /** The nearest one of those, or null. */
    @Nullable
    static LivingEntity firstInFront(LivingEntity user, double reach, double width, double height) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity t : front(user, reach, width, height)) {
            double d = t.distanceToSqr(user);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    static HitResult hit(Hit hit, LivingEntity target) {
        return HitResolver.resolve(hit, target);
    }

    /** Hakari landed {@code count} visual moves inside his own domain (they drive the gamble toward a Riichi). */
    static void visual(LivingEntity user, int count) {
        Gamble g = IdleDeathGamble.gambleOf(user);
        if (g != null) g.visualMove(count);
    }

    /** Low enough to be finished off by a finisher move. */
    static boolean finishable(LivingEntity target) {
        return target.isAlive() && target.getHealth() <= target.getMaxHealth() * dev.rick.jjk.config.JJKConfig.get().hakari.finisherThreshold;
    }

    /** A finisher: the target is killed outright, with the finisher presentation. */
    static HitResult execute(LivingEntity user, LivingEntity target, String id, String fx) {
        Hit kill = Hit.builder(user, id).type(dev.rick.jjk.registry.ModDamageTypes.TECHNIQUE).damage(target.getHealth() + target.getMaxHealth() * 4)
                .tag(dev.rick.jjk.core.combat.AttackTag.ULTIMATE, dev.rick.jjk.core.combat.AttackTag.UNBLOCKABLE)
                .origin(user.getEyePosition()).knockback(dev.rick.jjk.core.combat.Knockback.directional(flat(user), 1.2, 0.5))
                .noComboScaling().fx(fx, 1.5f).build();
        return HitResolver.resolve(kill, target);
    }

    static List<HitResult> hitAll(Hit hit, List<LivingEntity> targets) {
        List<HitResult> out = new ArrayList<>();
        for (LivingEntity t : targets) out.add(hit(hit, t));
        return out;
    }

    static boolean landed(List<HitResult> results) {
        for (HitResult r : results) if (r.connected()) return true;
        return false;
    }

    /** Target for a pursuit or a lock-on move: the crosshair target in range, or null. */
    @Nullable
    static LivingEntity aim(LivingEntity user, double range, @Nullable Entity hint) {
        return Aim.target(user, range, 12, hint);
    }

    /** Pushes the user along a horizontal direction, keeping a little of their vertical motion. */
    static void drive(LivingEntity user, Vec3 dir, double speed) {
        Vec3 d = new Vec3(dir.x, 0, dir.z);
        if (d.lengthSqr() < 1e-6) return;
        d = d.normalize().scale(speed);
        Motion.set(user, new Vec3(d.x, Mth.clamp(user.getDeltaMovement().y, -0.4, 0.2), d.z));
    }

    /** Holds a grabbed target in front of the user. */
    static void carry(LivingEntity user, LivingEntity target, double distance) {
        Vec3 at = user.position().add(flat(user).scale(distance));
        Vec3 pull = at.subtract(target.position());
        Motion.set(target, new Vec3(pull.x * 0.8, pull.y * 0.5, pull.z * 0.8).add(user.getDeltaMovement().multiply(1, 0, 1)));
    }

    static void faceTowards(LivingEntity user, Vec3 point) {
        Vec3 d = point.subtract(user.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        user.setYRot(yaw);
        user.setYHeadRot(yaw);
        user.setYBodyRot(yaw);
    }
}
