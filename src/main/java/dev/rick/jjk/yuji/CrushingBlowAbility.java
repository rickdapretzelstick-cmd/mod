package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Crushing Blow (JJS, 15s). Cursed energy charges in his hand (cyan, crackling) as he gets ready to slam the floor. A
 * target close enough is grabbed by the torso, slammed into the ground twice and flung into the sky as he lets go;
 * nobody close, and the blow into the floor releases a weaker shockwave that cancels wake-ups and stuns for a moment.
 * Unblockable, bypasses ragdoll. The finisher slams once, then a full German suplex.
 *
 * <p>Airborne: he dashes across the air at the target (360 blockable) and does the same; feinting it keeps the hop.
 */
public final class CrushingBlowAbility extends Ability {
    public static final String ID = "crushing_blow";

    public CrushingBlowAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YujiCombat.cfg().crushingCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().crushingCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx, Combat.isAirborne(ctx.user()), ctx.targetHint());
    }

    private static final class Instance extends AbilityInstance implements Feintable {
        private final boolean air;
        @Nullable private final Entity hint;
        @Nullable private LivingEntity victim;
        @Nullable private LivingEntity aimed;
        private int grabbedAt = -1;
        private boolean finisher;
        private int dashUntil = -1;
        private int endAt = -1;

        Instance(Ability ability, AbilityContext ctx, boolean air, @Nullable Entity hint) {
            super(ability, ctx);
            this.air = air;
            this.hint = hint;
        }

        @Override
        public boolean feintable() {
            return victim == null && dashUntil < 0 && age < YujiCombat.cfg().crushingWindup;
        }

        @Override
        public void start() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            Anim.play(user, air ? "crushing_blow_air" : "crushing_blow_charge");
            setPhase(0, cfg.crushingWindup);
            Fx.play(level, "crushing_charge", user.position().add(0, 1, 0), user.getLookAngle(), 1f, user.getId());
            if (air) {
                // The hop feinting it keeps.
                aimed = Aim.target(user, cfg.crushingAirDash + 2, 20, hint);
                Motion.set(user, HakariCombat.flat(user).scale(0.35).add(0, 0.38, 0));
            }
        }

        @Override
        public void tick() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (victim != null) {
                slams(cfg);
                return;
            }
            if (age < cfg.crushingWindup) {
                if (!air) Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                else Statuses.apply(user, CombatStatus.HOVER, 2);
                return;
            }
            if (air) {
                airDash(cfg);
                return;
            }
            // The blow comes down: a target close enough is grabbed, otherwise the floor takes it.
            LivingEntity t = HakariCombat.firstInFront(user, cfg.crushingReach, 1.6, 2.2);
            if (t != null && grab(t, true)) return;
            shockwave(cfg);
        }

        private void airDash(JJKConfig.Yuji cfg) {
            if (dashUntil < 0) {
                dashUntil = age + 9;
                Anim.play(user, "crushing_blow_dash");
                setPhase(1, 9);
                Fx.play(level, "crushing_dash", user.position().add(0, 1, 0), user.getLookAngle(), 1f, user.getId());
            }
            Vec3 to = aimed != null && aimed.isAlive() ? aimed.getBoundingBox().getCenter().subtract(user.position().add(0, 1, 0))
                    : user.getLookAngle();
            Vec3 d = to.lengthSqr() < 1e-4 ? HakariCombat.flat(user) : to.normalize();
            Motion.set(user, d.scale(cfg.crushingAirDash / 9.0));
            Statuses.apply(user, CombatStatus.HOVER, 2);
            List<LivingEntity> hits = HitboxQuery.targets(user, HitShape.sphere(user.position().add(0, 1, 0).add(d.scale(0.8)), 1.4), 0.3, false);
            if (!hits.isEmpty() && grab(hits.getFirst(), false)) return;
            if (age >= dashUntil || user.onGround()) {
                Motion.set(user, user.getDeltaMovement().scale(0.3));
                endAt = age + 6;
            }
        }

        private boolean grab(LivingEntity t, boolean unblockable) {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            var b = YujiCombat.strike(user, ID, 0, unblockable).knockback(Knockback.HOLD).hitstun(40).fx("crushing_grab", 1f);
            if (!unblockable) b.tag(AttackTag.BLOCKABLE_360);
            HitResult r = HakariCombat.hit(b.build(), t);
            if (!r.connected()) {
                if (r.outcome().contacted()) endAt = age + 8;
                return r.outcome().contacted();
            }
            victim = t;
            grabbedAt = age;
            finisher = YujiCombat.finishable(t);
            Statuses.apply(t, CombatStatus.GRABBED, 40);
            Anim.play(user, finisher ? "crushing_blow_suplex" : "crushing_blow_slam");
            setPhase(2, 30);
            return true;
        }

        private void slams(JJKConfig.Yuji cfg) {
            if (!victim.isAlive()) {
                finish();
                return;
            }
            int t = age - grabbedAt;
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            // Held down in front of him, driven into the floor.
            Vec3 at = user.position().add(HakariCombat.flat(user).scale(1.2));
            Motion.set(victim, at.subtract(victim.position()).scale(0.6).add(0, -0.3, 0));
            Statuses.apply(victim, CombatStatus.GRABBED, 4);
            boolean slam = finisher ? t == 3 : t == 3 || t == 13;
            if (slam) slam(cfg, at);
            if (finisher && t == 20) {
                // The German suplex.
                Anim.play(user, "crushing_blow_suplex_throw");
                Statuses.remove(victim, CombatStatus.GRABBED);
                Fx.play(level, "crushing_suplex", user.position().subtract(HakariCombat.flat(user).scale(1.2)), new Vec3(0, -1, 0), 1.3f, user.getId());
                YujiCombat.execute(user, victim, ID, "crushing_finisher");
                Fx.shake(level, user.position(), 24, 1.1f, 12);
                victim = null;
                endAt = age + 12;
            }
            if (!finisher && t == 22) {
                // Let go: flung into the sky.
                Statuses.remove(victim, CombatStatus.GRABBED);
                Anim.play(user, "crushing_blow_release");
                HakariCombat.hit(YujiCombat.strike(user, ID, 0, true).knockback(Knockback.set(new Vec3(0, cfg.crushingLaunch, 0)))
                        .hitstun(36).status(CombatStatus.LAUNCHED, 40).fx("hit_launch", 1.3f).build(), victim);
                victim = null;
                endAt = age + 8;
            }
        }

        private void slam(JJKConfig.Yuji cfg, Vec3 at) {
            HakariCombat.hit(YujiCombat.strike(user, ID, cfg.crushingSlamDamage, true).knockback(Knockback.set(new Vec3(0, -0.6, 0))).hitstun(30)
                    .fx("crushing_slam", 1.2f).noComboScaling().build(), victim);
            Fx.play(level, "crushing_impact", at, new Vec3(0, 1, 0), 1f, user.getId());
            Fx.shake(level, at, 18, 0.7f, 8);
            crater(at, 1.6);
        }

        private void shockwave(JJKConfig.Yuji cfg) {
            Anim.play(user, "crushing_blow_whiff");
            Vec3 at = user.position().add(HakariCombat.flat(user).scale(1.3));
            Fx.play(level, "crushing_impact", at, new Vec3(0, 1, 0), 1.2f, user.getId());
            Fx.shake(level, at, 18, 0.6f, 8);
            crater(at, 2.2);
            for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(at, cfg.crushingShockwaveRadius), 0.3, false)) {
                // It catches people getting up, too.
                Statuses.remove(t, CombatStatus.WAKEUP);
                HakariCombat.hit(YujiCombat.strike(user, ID, cfg.crushingShockwaveDamage, true).tag(AttackTag.OTG)
                        .knockback(Knockback.radial(at, 0.3, 0.2)).hitstun(12).fx("hit_light", 1f).build(), t);
            }
            endAt = age + 10;
        }

        private void crater(Vec3 at, double r) {
            if (!Destruction.allowed(level)) return;
            BlockPos c = BlockPos.containing(at.add(0, -0.5, 0));
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 0, 2))) {
                if (Vec3.atCenterOf(p).distanceTo(at) <= r) Destruction.destroy(level, p, 2f, user, "jjk:crushing_blow");
            }
        }

        @Override
        public float movementMultiplier() {
            return 0.2f;
        }

        @Override
        public void end() {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
        }
    }
}
