package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.entity.TechniqueEntity;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Outburst (JJS, 16s). Yuta grips his holstered katana by the handle and pours cursed energy into the coming swing:
 * the draw (2) sets off a giant burst (4) in a 13 stud radius that throws everyone upward. Semi-blockable: it deals its
 * damage through a guard, though it can't kill through one. Held, the burst grows through three stages (a bar at his
 * right): +2 damage and about 2 studs of radius each; at the last stage it is fully unblockable unless he was hit
 * before it went off. The finisher cuts them in half and the burst guts the pieces.
 *
 * <p>Bullet counter: hit in the first 0.25 s, the swing parries. A melee attacker is pushed back stunned while he gains
 * melee and bullet i-frames; the move goes on a short 4 s cooldown and Rika's cooldowns skip 2 s. A projectile is sent
 * back where it came from and the move stays off cooldown.
 */
public final class OutburstAbility extends Ability {
    public static final String ID = "outburst";

    public OutburstAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YutaCombat.cfg().outburstCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().outburstCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    /** The burst's stage while he holds it (0 to 3), or -1 when he isn't. For the bar beside him. */
    public static int stage(LivingEntity user) {
        AbilityCaster c = Casters.getOrNull(user);
        return c != null && c.cast() instanceof Instance i && !i.isFinished() && i.swingAt < 0 ? i.stage() : -1;
    }

    /** His parry window is open right now. */
    static boolean parrying(LivingEntity user) {
        AbilityCaster c = Casters.getOrNull(user);
        return c != null && c.cast() instanceof Instance i && !i.isFinished() && i.parryOpen();
    }

    /** The defense caught something in the window. */
    static void parried(LivingEntity user, @Nullable Entity attacker, @Nullable Entity direct, boolean bullet) {
        AbilityCaster c = Casters.getOrNull(user);
        if (c != null && c.cast() instanceof Instance i) i.parry(attacker, direct, bullet);
    }

    public static final class Instance extends AbilityInstance {
        private final AbilitySlot slot;
        private final int mode;
        private final float healthAtStart;
        private int swingAt = -1;
        private int burstAt = -1;
        private int endAt = -1;
        private boolean parried;
        private boolean wasHit;
        private int fixedStage = -1;
        @Nullable private LivingEntity swung;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            slot = ctx.slot();
            mode = ctx.caster().mode();
            healthAtStart = ctx.user().getHealth();
        }

        int stage() {
            if (fixedStage >= 0) return fixedStage;
            return Math.min(3, Math.max(0, age - YutaCombat.cfg().outburstWindup) / YutaCombat.cfg().outburstStageTicks);
        }

        boolean parryOpen() {
            return !parried && swingAt < 0 && age <= YutaCombat.cfg().outburstParryTicks;
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            YutaState.of(user).outburstAt = level.getGameTime();
            Statuses.apply(user, CombatStatus.BULLET_ARMOR, cfg.outburstParryTicks + 1);
            Anim.play(user, "yuta_outburst_grip");
            setPhase(0, cfg.outburstWindup + 3 * cfg.outburstStageTicks);
            Fx.play(level, "outburst_grip", user.position().add(0, 0.9, 0), HakariCombat.flat(user), 1f, user.getId());
            YutaSync.send(user);
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (user.getHealth() < healthAtStart - 0.01f) wasHit = true;
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (swingAt < 0) {
                Motion.set(user, new Vec3(user.getDeltaMovement().x * 0.3, Math.min(0, user.getDeltaMovement().y), user.getDeltaMovement().z * 0.3));
                int max = cfg.outburstWindup + 3 * cfg.outburstStageTicks + 16;
                int st = stage();
                if (age > cfg.outburstWindup && (age - cfg.outburstWindup) % cfg.outburstStageTicks == 0 && st > 0 && age - cfg.outburstWindup <= 3 * cfg.outburstStageTicks) {
                    Fx.play(level, "outburst_stage", user.position().add(0, 0.9, 0), HakariCombat.flat(user), st, user.getId());
                    YutaSync.send(user);
                }
                if ((!held && age >= cfg.outburstWindup) || age >= max) swing(cfg);
                return;
            }
            if (age == burstAt) burst(cfg);
        }

        /** The draw: a swing in front, then the burst goes off around him. */
        private void swing(JJKConfig.Yuta cfg) {
            fixedStage = stage();
            swingAt = age;
            burstAt = age + 3;
            YutaCombat.drawKatana(user);
            Anim.play(user, "yuta_outburst_swing");
            setPhase(1, 14);
            Fx.play(level, "outburst_swing", user.position().add(0, 1.0, 0), HakariCombat.flat(user), 1f + fixedStage * 0.15f, user.getId());
            YutaSync.send(user);
            LivingEntity t = HakariCombat.firstInFront(user, 2.6, 2.4, 2.2);
            if (t == null) return;
            if (YutaCombat.finishable(t) && (!dev.rick.jjk.core.combat.Combat.isGuarding(t) || fixedStage >= 3 && !wasHit)) {
                swung = t;
                return;
            }
            HitResult r = HakariCombat.hit(YutaCombat.strike(user, ID, cfg.outburstSwingDamage, false).tag(AttackTag.OTG)
                    .knockback(Knockback.HOLD).hitstun(10).fx("severing_swing", 1f).build(), t);
            if (r.outcome().contacted()) {
                swung = t;
                YutaCombat.setTarget(user, t);
            }
        }

        private void burst(JJKConfig.Yuta cfg) {
            int st = fixedStage;
            double radius = cfg.outburstRadius + st * cfg.outburstStageRadius;
            float damage = cfg.outburstBurstDamage + st * cfg.outburstStageDamage;
            boolean unblockable = st >= 3 && !wasHit;
            Vec3 at = user.position().add(0, 0.8, 0);
            Fx.play(level, "outburst_burst", at, HakariCombat.flat(user), (float) radius, user.getId());
            Fx.shake(level, at, radius * 4, 0.6f + st * 0.15f, 10);
            for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(at, radius), 0.3, false)) {
                if (t == swung && YutaCombat.finishable(t) && (unblockable || !dev.rick.jjk.core.combat.Combat.isGuarding(t))) {
                    // Cut in half, and the burst guts the pieces.
                    YutaCombat.execute(user, t, ID, "outburst_finisher");
                    continue;
                }
                Vec3 away = t.position().subtract(user.position());
                Vec3 flat = new Vec3(away.x, 0, away.z);
                flat = flat.lengthSqr() < 1e-4 ? HakariCombat.flat(user) : flat.normalize();
                var b = YutaCombat.strike(user, ID, damage, unblockable).tag(AttackTag.EXPLOSION, AttackTag.AREA, AttackTag.OTG)
                        .knockback(Knockback.set(flat.scale(0.35).add(0, 0.95 + st * 0.06, 0))).hitstun(26)
                        .status(CombatStatus.LAUNCHED, 30).fx("outburst_hit", 1f).noComboScaling();
                HitResult r = HakariCombat.hit(b.build(), t);
                if (r.outcome() == HitResult.Outcome.BLOCKED) {
                    // Semi-blockable: the damage goes through the guard, but it can't kill through one.
                    float through = Math.min(damage, Math.max(0, t.getHealth() - 1f));
                    if (through > 0) {
                        HakariCombat.hit(YutaCombat.strike(user, ID, through, true).tag(AttackTag.EXPLOSION).knockback(Knockback.NONE)
                                .noComboScaling().fx("outburst_hit", 0.6f).build(), t);
                    }
                }
                YutaCombat.setTarget(user, t);
            }
            endAt = age + 10;
        }

        /** Something hit him in the parry window. */
        void parry(@Nullable Entity attacker, @Nullable Entity direct, boolean bullet) {
            if (parried) return;
            parried = true;
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            swingAt = age;
            fixedStage = 0;
            YutaCombat.drawKatana(user);
            Anim.play(user, "yuta_outburst_parry");
            setPhase(2, 12);
            Fx.play(level, "outburst_parry", user.position().add(0, 1.1, 0), HakariCombat.flat(user), 1f, user.getId());
            if (bullet) {
                // Sent straight back where it came from; the move stays off cooldown.
                if (direct != null && !(direct instanceof LivingEntity)) reflect(direct, attacker);
                resetCooldown();
            } else {
                if (attacker instanceof LivingEntity a) {
                    Vec3 push = a.position().subtract(user.position());
                    Vec3 flat = new Vec3(push.x, 0, push.z);
                    flat = flat.lengthSqr() < 1e-4 ? HakariCombat.flat(user) : flat.normalize();
                    Statuses.apply(a, CombatStatus.GUARD_BROKEN, cfg.outburstParryStun);
                    Motion.set(a, flat.scale(0.9).add(0, 0.2, 0));
                    YutaCombat.setTarget(user, a);
                }
                Statuses.apply(user, CombatStatus.MELEE_ARMOR, 16);
                Statuses.apply(user, CombatStatus.BULLET_ARMOR, 16);
                resetCooldown();
                caster.startCooldown(slot, mode, cfg.outburstParryCooldown);
                // Rika's own cooldowns skip 2 seconds.
                caster.reduceCooldowns(YutaCharacter.RIKA, cfg.outburstRikaRefund);
                caster.reduceCooldowns(YutaCharacter.RIKA_AWAKENED, cfg.outburstRikaRefund);
            }
            YutaSync.send(user);
            endAt = age + 12;
        }

        private void resetCooldown() {
            caster.resetSlot(slot, mode);
        }

        private void reflect(Entity e, @Nullable Entity shooter) {
            Vec3 v = e.getDeltaMovement();
            Vec3 back = shooter != null ? shooter.getBoundingBox().getCenter().subtract(e.position()) : v.scale(-1);
            double speed = Math.max(0.8, v.length());
            Motion.set(e, back.lengthSqr() < 1e-4 ? v.scale(-1) : back.normalize().scale(speed));
            if (e instanceof Projectile p) p.setOwner(user);
            if (e instanceof TechniqueEntity te) te.setOwner(user);
        }

        @Override
        public float movementMultiplier() {
            return swingAt < 0 ? 0.35f : 0f;
        }

        @Override
        public void end() {
            YutaState.of(user).outburstAt = -1;
            YutaSync.send(user);
        }
    }
}
