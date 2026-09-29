package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.registry.ModDamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Gojo's shared combat rules on top of the common strike helpers ({@link HakariCombat}). */
public final class GojoCombat {
    private GojoCombat() {}

    /** Low enough for a Gojo finisher. */
    public static boolean finishable(LivingEntity target) {
        return target.isAlive() && target.getHealth() <= target.getMaxHealth() * JJKConfig.get().gojo.finisherThreshold;
    }

    /** A finisher: the target dies outright with the finisher presentation. */
    public static HitResult execute(LivingEntity user, LivingEntity target, String id, String fx) {
        return HakariCombat.execute(user, target, id, fx);
    }

    /** Ragdolled: launched, spiked or on the floor (JJS moves that "cannot bypass ragdoll" whiff on these). */
    public static boolean ragdolled(LivingEntity e) {
        var s = Combat.state(e);
        return s.has(CombatStatus.LAUNCHED) || s.has(CombatStatus.SPIKED) || s.has(CombatStatus.KNOCKDOWN);
    }

    /** In the middle of doing something (a move or a dash): Reversal Red's interruption variant. */
    public static boolean acting(LivingEntity e) {
        var c = dev.rick.jjk.core.ability.Casters.getOrNull(e);
        if (c != null && (c.isBusy() || c.melee.isCommitted())) return true;
        return Combat.state(e).has(CombatStatus.EVADING);
    }

    /** A Gojo strike: melee by default; {@code unblockable} skips guard. */
    public static Hit.Builder strike(LivingEntity user, String id, float damage, boolean unblockable) {
        Hit.Builder b = Hit.builder(user, id).type(ModDamageTypes.MELEE).damage(damage).tag(AttackTag.MELEE).origin(user.getEyePosition());
        if (unblockable) b.tag(AttackTag.UNBLOCKABLE);
        return b;
    }

    /** Point-blank Reversal Red on one target (Twofold Kick's finisher, Red's special variants). */
    public static void pointBlankRed(LivingEntity user, LivingEntity target, float damage, boolean kill) {
        if (!(user.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Vec3 at = target.getBoundingBox().getCenter();
        dev.rick.jjk.core.fx.Fx.play(level, "red_pointblank", at, user.getLookAngle(), 1.2f, user.getId());
        if (kill) {
            execute(user, target, RedAbility.ID, "red_explosion");
            return;
        }
        Vec3 away = at.subtract(user.position());
        away = new Vec3(away.x, 0, away.z);
        away = away.lengthSqr() < 1e-4 ? HakariCombat.flat(user) : away.normalize();
        Hit hit = Hit.builder(user, RedAbility.ID).type(ModDamageTypes.RED).damage(damage)
                .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.EXPLOSION, AttackTag.OTG).origin(user.getEyePosition())
                .knockback(Knockback.directional(away, JJKConfig.get().red.knockback, JJKConfig.get().red.launch)).hitstun(JJKConfig.get().red.hitstun)
                .status(CombatStatus.LAUNCHED, 28).fx("red_hit", 1.2f).build();
        HitResolver.resolve(hit, target);
    }
}
