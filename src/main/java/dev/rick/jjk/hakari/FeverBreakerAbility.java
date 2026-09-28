package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 4 — Fever Breaker. A reaching kick that leaves the target suspended in front of two shutter doors, then a dropkick
 * that launches them wherever Hakari is facing when it lands (aim it up, down or sideways).
 * <ul>
 *   <li>Fever Crush (Shutter Doors pressed during the wind-up): the doors clamp the target in place while Hakari raises
 *       his foot, and an unblockable axe kick crushes them.</li>
 *   <li>On a ragdolled target the axe kick is far heavier; they bounce off it into the doors, which shatter under the
 *       stomp — unless Hakari looks slightly away from them, which keeps them standing.</li>
 * </ul>
 */
public final class FeverBreakerAbility extends Ability {
    public static final String ID = "fever_breaker";

    public FeverBreakerAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.feverCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.feverCooldown;
    }

    static boolean ragdolled(LivingEntity e) {
        return Combat.has(e, CombatStatus.LAUNCHED) || Combat.has(e, CombatStatus.KNOCKDOWN) || Combat.has(e, CombatStatus.SPIKED);
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Breaker(this, ctx);
    }

    static final class Breaker extends AbilityInstance implements DoorCombo {
        private boolean crush;
        @Nullable private LivingEntity victim;
        @Nullable private ShutterTrap doors;
        private boolean ragdoll;
        private int stompAt = -1, dropkickAt = -1, endAt = -1;

        Breaker(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        @Override
        public void start() {
            Anim.play(user, "fever_kick");
            setPhase(0, 60);
            Motion.set(user, HakariCombat.flat(user).scale(0.3).add(0, user.getDeltaMovement().y, 0));
        }

        @Override
        public boolean acceptsDoors() {
            return !crush && age < JJKConfig.get().hakari.feverWindup;
        }

        @Override
        public void addDoors() {
            crush = true;
            Anim.play(user, "fever_crush");
            Fx.play(level, "combo_doors", user.position().add(0, 1.4, 0), HakariCombat.flat(user), 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Hakari cfg = JJKConfig.get().hakari;
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (age == cfg.feverWindup) {
                if (crush) startCrush(cfg);
                else kick(cfg);
                return;
            }
            if (age == stompAt) stomp(cfg);
            if (dropkickAt >= 0 && age >= dropkickAt - 4 && age < dropkickAt && victim != null) {
                // Closing in for the dropkick.
                HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
                HakariCombat.drive(user, victim.position().subtract(user.position()), 0.5);
            }
            if (age == dropkickAt) dropkick(cfg);
            if (age > 70) finish();
        }

        private void kick(JJKConfig.Hakari cfg) {
            Vec3 f = HakariCombat.flat(user);
            Fx.play(level, "fever_swing", user.position().add(0, 1, 0).add(f.scale(1.2)), f, 1f, user.getId());
            victim = HakariCombat.firstInFront(user, 3.2, 1.8, 2.2);
            Hit kick = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.feverKickDamage)
                    .tag(AttackTag.MELEE, AttackTag.TECHNIQUE).origin(user.getEyePosition())
                    .knockback(Knockback.HOLD).hitstun(cfg.feverSuspendTicks + 12).guardDamage(2).fx("fever_kick", 1f).build();
            if (victim == null || !HakariCombat.hit(kick, victim).connected()) {
                victim = null;
                endAt = age + 8;
                return;
            }
            // Suspended in front of the doors, which stand behind them.
            Vec3 behind = victim.position().add(f.scale(1.1));
            doors = ShutterTrap.open(level, user, ShutterTrap.Mode.SUSPEND, victim.position().add(0, 0.2, 0), victim, 0);
            Fx.play(level, "fever_suspend", behind.add(0, 1.2, 0), f, 1f, user.getId());
            setPhase(1, cfg.feverSuspendTicks);
            dropkickAt = age + cfg.feverSuspendTicks;
        }

        private void dropkick(JJKConfig.Hakari cfg) {
            setPhase(2, 12);
            Anim.play(user, "fever_finish");
            if (victim == null || !victim.isAlive()) {
                endAt = age + 8;
                return;
            }
            if (doors != null) doors.release(true);
            doors = null;
            Statuses.remove(victim, CombatStatus.GRABBED);
            // The launch follows where Hakari is looking as the kick lands.
            Vec3 look = user.getLookAngle();
            Hit breaker = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.feverFinishDamage)
                    .tag(AttackTag.MELEE, AttackTag.TECHNIQUE, AttackTag.HEAVY, AttackTag.OTG).origin(user.getEyePosition())
                    .knockback(Knockback.set(look.normalize().scale(cfg.feverFinishKnockback).add(0, 0.25, 0))).hitstun(30)
                    .status(CombatStatus.LAUNCHED, 24).guardDamage(4).fx("fever_impact", 1.4f).build();
            if (user.distanceTo(victim) < 4.5 && HakariCombat.hit(breaker, victim).connected()) HakariCombat.visual(user, 1);
            Fx.play(level, "fever_break", victim.getBoundingBox().getCenter(), look, 1f, user.getId());
            Fx.shake(level, victim.position(), 22, 0.9f, 12);
            Motion.set(user, user.getDeltaMovement().scale(0.2));
            victim = null;
            endAt = age + 10;
        }

        private void startCrush(JJKConfig.Hakari cfg) {
            LivingEntity t = HakariCombat.firstInFront(user, 3.6, 2.0, 2.4);
            if (t == null) t = HakariCombat.aim(user, 5, null);
            victim = t;
            Vec3 spot = t != null ? t.position() : user.position().add(HakariCombat.flat(user).scale(2));
            ragdoll = t != null && ragdolled(t);
            doors = ShutterTrap.open(level, user, ShutterTrap.Mode.HOLD, spot, t, cfg.crushDoorDamage);
            setPhase(1, 12);
            Anim.play(user, "fever_raise");
            stompAt = age + 12;
        }

        private void stomp(JJKConfig.Hakari cfg) {
            setPhase(2, 12);
            Anim.play(user, "fever_axe");
            HakariCombat.visual(user, 1);
            boolean held = doors != null && doors.caught();
            LivingEntity t = victim;
            if (t != null && t.isAlive() && user.distanceTo(t) < 4.5) {
                float dmg = ragdoll && held ? cfg.crushRagdollStompDamage : cfg.crushStompDamage;
                Hit axe = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(dmg)
                        .tag(AttackTag.MELEE, AttackTag.TECHNIQUE, AttackTag.HEAVY, AttackTag.UNBLOCKABLE, AttackTag.OTG).origin(user.getEyePosition())
                        .knockback(ragdoll ? Knockback.set(new Vec3(0, 0.7, 0)) : Knockback.set(new Vec3(0, -0.6, 0))).hitstun(26)
                        .status(ragdoll ? CombatStatus.LAUNCHED : CombatStatus.KNOCKDOWN, 20).guardDamage(4).fx("fever_impact", ragdoll ? 1.6f : 1.3f).build();
                if (doors != null) Statuses.remove(t, CombatStatus.GRABBED);
                HakariCombat.hit(axe, t);
                Fx.play(level, "fever_crush", t.position(), Vec3.ZERO, ragdoll ? 1.4f : 1f, user.getId());
                Fx.shake(level, t.position(), 22, 1f, 12);
            }
            if (doors != null) {
                // Looking slightly away from the doors keeps them standing; otherwise the stomp shatters them.
                Vec3 toDoors = doors.center.subtract(user.position());
                Vec3 flatTo = new Vec3(toDoors.x, 0, toDoors.z);
                boolean away = flatTo.lengthSqr() > 1e-3 && flatTo.normalize().dot(HakariCombat.flat(user)) < 0.93;
                doors.release(!(ragdoll && away));
                doors = null;
            }
            victim = null;
            endAt = age + 12;
        }

        @Override
        public void end() {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            if (doors != null && !doors.isDone() && doors.mode != ShutterTrap.Mode.STRIKE) doors.release(true);
        }

        @Override
        public float movementMultiplier() {
            return 0.3f;
        }
    }
}
