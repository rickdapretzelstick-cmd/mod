package dev.rick.jjk.yuta;

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
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Severing Path (JJS, 15s). Yuta slides 18 studs forward sweeping the floor with his blade, cursed energy running
 * along it. Catching someone, he locks them in front of him and follows up with three quick swings, the last launching
 * them upward (4 for the sweep, 2.3 a swing). Blockable; bypasses ragdoll. Blocked, he is pushed off and the rest is
 * lost. The finisher beheads them with the last slash.
 *
 * <p>Veilstep (walking backwards): a back roll of 27 studs with melee i-frames that launches anyone in the way (9).
 */
public final class SeveringPathAbility extends Ability {
    public static final String ID = "severing_path";

    public SeveringPathAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YutaCombat.cfg().severingCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().severingCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaCombat.drawKatana(ctx.user());
        if (ctx.forward() < -0.1f && dev.rick.jjk.progression.mastery.Mastery.unlocked(ctx.user(), "severing_path.veilstep")) return new Veilstep(this, ctx);
        return new Instance(this, ctx);
    }

    static final class Instance extends AbilityInstance {
        @Nullable private LivingEntity victim;
        private int caughtAt = -1;
        private int endAt = -1;
        private int swings;
        private final Vec3 dir;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            dir = HakariCombat.flat(ctx.user());
        }

        @Override
        public void start() {
            Anim.play(user, "yuta_severing_path");
            setPhase(0, YutaCombat.cfg().severingWindup + YutaCombat.cfg().severingSlideTicks);
            Fx.play(level, "severing_ready", user.position().add(0, 0.6, 0), dir, 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (endAt >= 0) {
                if (victim == null) Motion.set(user, user.getDeltaMovement().multiply(0.5, 1, 0.5));
                if (age >= endAt) finish();
                return;
            }
            if (victim != null) {
                swings(cfg);
                return;
            }
            if (age < cfg.severingWindup) {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                return;
            }
            int slid = age - cfg.severingWindup;
            if (slid == 0) {
                Fx.play(level, "severing_slide", user.position().add(0, 0.3, 0), dir, 1f, user.getId());
                setPhase(1, cfg.severingSlideTicks);
            }
            HakariCombat.drive(user, dir, cfg.severingSlide / cfg.severingSlideTicks);
            // The sweep along the floor: anyone in front at shin height is caught.
            List<LivingEntity> hits = HitboxQuery.targets(user, HitShape.orientedBox(user.position().add(0, 0.6, 0), dir, 1.8, 1.4, 1.6), 0.3, false);
            for (LivingEntity t : hits) {
                HitResult r = HakariCombat.hit(YutaCombat.strike(user, ID, cfg.severingSweepDamage, false).tag(AttackTag.OTG)
                        .knockback(Knockback.HOLD).hitstun(30).fx("severing_sweep", 1f).build(), t);
                if (r.outcome() == HitResult.Outcome.BLOCKED) {
                    // Blocked: pushed off, and the swings that would follow are lost.
                    Motion.set(user, dir.scale(-0.55).add(0, 0.2, 0));
                    endAt = age + 12;
                    return;
                }
                if (r.connected()) {
                    victim = t;
                    caughtAt = age;
                    YutaCombat.setTarget(user, t);
                    Motion.set(user, Vec3.ZERO);
                    Anim.play(user, "yuta_severing_swings");
                    setPhase(2, 22);
                    return;
                }
            }
            if (slid >= cfg.severingSlideTicks) {
                // Whiffed: a long recovery.
                Anim.play(user, "yuta_severing_whiff");
                endAt = age + 14;
            }
        }

        private void swings(JJKConfig.Yuta cfg) {
            if (!victim.isAlive()) {
                finish();
                return;
            }
            int since = age - caughtAt;
            // Locked in front of him.
            Statuses.apply(victim, CombatStatus.GRABBED, 3);
            HakariCombat.carry(user, victim, 1.5);
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            if (since == 6 || since == 12 || since == 18) {
                swings++;
                boolean last = swings == 3;
                Fx.play(level, "severing_swing_" + swings, victim.getBoundingBox().getCenter(), dir, last ? 1.4f : 1f, user.getId());
                if (last && YutaCombat.finishable(victim)) {
                    Statuses.remove(victim, CombatStatus.GRABBED);
                    YutaCombat.execute(user, victim, ID, "severing_finisher");
                    endAt = age + 10;
                    return;
                }
                var b = YutaCombat.strike(user, ID, cfg.severingSwingDamage, false).tag(AttackTag.OTG).noComboScaling();
                if (last) {
                    Statuses.remove(victim, CombatStatus.GRABBED);
                    b.knockback(Knockback.set(new Vec3(0, 1.05, 0).add(dir.scale(0.15)))).hitstun(28).status(CombatStatus.LAUNCHED, 30).fx("severing_hit_4", 1.1f);
                } else {
                    b.knockback(Knockback.HOLD).hitstun(16).fx("severing_hit_" + (swings + 1), 0.8f);
                }
                HitResult r = HakariCombat.hit(b.build(), victim);
                if (r.outcome() == HitResult.Outcome.BLOCKED) {
                    Statuses.remove(victim, CombatStatus.GRABBED);
                    Motion.set(user, dir.scale(-0.5).add(0, 0.2, 0));
                    endAt = age + 10;
                    return;
                }
                if (last) endAt = age + 8;
            }
        }

        @Override
        public void interrupt(String reason) {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            super.interrupt(reason);
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }
    }

    /** Veilstep: the back roll. */
    static final class Veilstep extends AbilityInstance {
        private final Vec3 back;
        private final Set<LivingEntity> hit = new HashSet<>();

        Veilstep(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            back = HakariCombat.flat(ctx.user()).scale(-1);
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            Anim.play(user, "yuta_veilstep");
            setPhase(0, cfg.veilstepTicks);
            Statuses.apply(user, CombatStatus.MELEE_ARMOR, cfg.veilstepTicks + 2);
            Fx.play(level, "veilstep", user.position().add(0, 0.5, 0), back, 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (age <= cfg.veilstepTicks) {
                HakariCombat.drive(user, back, cfg.veilstepDistance / cfg.veilstepTicks);
                for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(user.position().add(0, 0.9, 0), 1.3), 0.3, false)) {
                    if (!hit.add(t)) continue;
                    HakariCombat.hit(YutaCombat.strike(user, ID, cfg.veilstepDamage, false).tag(AttackTag.BLOCKABLE_360, AttackTag.OTG)
                            .knockback(Knockback.set(new Vec3(0, 1.0, 0))).hitstun(26).status(CombatStatus.LAUNCHED, 28).fx("veilstep_hit", 1f).build(), t);
                    YutaCombat.setTarget(user, t);
                }
            } else {
                Motion.set(user, user.getDeltaMovement().multiply(0.4, 1, 0.4));
            }
            if (age >= cfg.veilstepTicks + 6) finish();
        }
    }
}
