package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jackpot 2 — Lucky Rushdown. Hakari breaks into a long forward run; whoever he meets is grabbed by the leg, dragged
 * across the floor (ploughing through whatever is in the way) and thrown forward. Unblockable. On a target low enough
 * (the finisher) the drag goes on longer, then he hurls them into the air, leaps after them and ends it with a punch.
 */
public final class LuckyRushdownAbility extends Ability {
    public static final String ID = "lucky_rushdown";

    public LuckyRushdownAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.rushdownCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        LivingEntity aimed = HakariCombat.aim(ctx.user(), 24, ctx.targetHint());
        return new AbilityInstance(this, ctx) {
            private LivingEntity victim;
            private int grabbedAt = -1;
            private int endAt = -1;
            private boolean finisher;
            private int punchAt = -1;

            @Override
            public void start() {
                Anim.play(user, "rushdown_run");
                setPhase(0, JJKConfig.get().hakari.rushdownRunTicks);
                Fx.play(level, "rushdown_start", user.position().add(0, 0.6, 0), HakariCombat.flat(user), 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                if (punchAt >= 0) {
                    finisherAir(cfg);
                    return;
                }
                if (endAt >= 0) {
                    if (age >= endAt) finish();
                    return;
                }
                if (victim == null) {
                    // Running: steer toward the aimed target, or straight ahead.
                    Vec3 dir = aimed != null && aimed.isAlive() ? aimed.position().subtract(user.position()) : HakariCombat.flat(user);
                    if (aimed != null && aimed.isAlive()) HakariCombat.faceTowards(user, aimed.getBoundingBox().getCenter());
                    HakariCombat.drive(user, dir, cfg.rushdownSpeed);
                    if (age % 3 == 0) Fx.play(level, "rushdown_step", user.position(), HakariCombat.flat(user), 1f, user.getId());
                    LivingEntity hit = HakariCombat.firstInFront(user, 1.8, 1.6, 2.2);
                    if (hit != null) {
                        Hit grab = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.rushdownGrabDamage).tag(AttackTag.MELEE, AttackTag.UNBLOCKABLE)
                                .origin(user.getEyePosition()).knockback(Knockback.HOLD).hitstun(cfg.rushdownDragTicks + 6).fx("rushdown_grab", 1f).build();
                        if (HakariCombat.hit(grab, hit).connected()) {
                            victim = hit;
                            grabbedAt = age;
                            finisher = HakariCombat.finishable(hit);
                            Anim.play(user, "rushdown_drag");
                            setPhase(1, cfg.rushdownDragTicks);
                            Fx.shake(level, user.position(), 14, 0.5f, 8);
                            return;
                        }
                    }
                    if (age >= cfg.rushdownRunTicks) {
                        Motion.set(user, user.getDeltaMovement().scale(0.3));
                        endAt = age + 6;
                    }
                    return;
                }
                if (!victim.isAlive()) {
                    finish();
                    return;
                }
                // Dragging: keep charging with them held in front, tearing through the ground.
                Vec3 f = HakariCombat.flat(user);
                HakariCombat.drive(user, f, cfg.rushdownSpeed * 0.8);
                Statuses.apply(victim, CombatStatus.GRABBED, 4);
                HakariCombat.carry(user, victim, 1.3);
                if (age % 2 == 0) {
                    Fx.play(level, "rushdown_drag", victim.position(), f, 1f, user.getId());
                    if (Destruction.allowed(level)) {
                        BlockPos under = BlockPos.containing(victim.position().add(f.scale(0.6)).add(0, -0.5, 0));
                        Destruction.destroy(level, under, 2f, user, "jjk:lucky_rushdown");
                        Destruction.destroy(level, under.above(), 2f, user, "jjk:lucky_rushdown");
                    }
                }
                if (finisher && age - grabbedAt >= cfg.rushdownFinisherDragTicks) {
                    // The finisher: hurled into the air, and Hakari goes up after them.
                    Statuses.remove(victim, CombatStatus.GRABBED);
                    Anim.play(user, "rushdown_throw");
                    setPhase(2, 16);
                    Motion.set(victim, f.scale(0.2).add(0, 1.45, 0));
                    Statuses.apply(victim, CombatStatus.LAUNCHED, 30);
                    Fx.play(level, "rushdown_throw", victim.position(), new Vec3(0, 1, 0), 1.4f, user.getId());
                    punchAt = age + 12;
                    return;
                }
                if (!finisher && age - grabbedAt >= cfg.rushdownDragTicks) {
                    Statuses.remove(victim, CombatStatus.GRABBED);
                    Anim.play(user, "rushdown_throw");
                    setPhase(2, 10);
                    Hit toss = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.rushdownThrowDamage).tag(AttackTag.MELEE, AttackTag.HEAVY)
                            .origin(user.getEyePosition()).knockback(Knockback.directional(f, cfg.rushdownThrowKnockback, 0.7)).hitstun(30)
                            .status(CombatStatus.LAUNCHED, 26).fx("rushdown_throw", 1.4f).build();
                    HakariCombat.hit(toss, victim);
                    Fx.shake(level, victim.position(), 22, 1.0f, 12);
                    Motion.set(user, f.scale(0.1));
                    victim = null;
                    endAt = age + 8;
                }
            }

            /** Leaping after the hurled target, then the punch that finishes it. */
            private void finisherAir(JJKConfig.Hakari cfg) {
                if (victim == null || !victim.isAlive()) {
                    finish();
                    return;
                }
                if (age == punchAt - 7) {
                    Anim.play(user, "energy_leap");
                    Vec3 to = victim.position().subtract(user.position());
                    Motion.set(user, new Vec3(to.x * 0.18, Math.max(0.9, to.y * 0.22 + 0.5), to.z * 0.18));
                }
                if (age == punchAt) {
                    HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
                    Anim.play(user, "volley_final");
                    if (user.distanceTo(victim) < 5) HakariCombat.execute(user, victim, ID, "rushdown_finisher");
                    Fx.shake(level, victim.position(), 26, 1.2f, 14);
                    victim = null;
                    endAt = age + 10;
                    punchAt = -1;
                }
            }

            @Override
            public void end() {
                if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            }
        };
    }
}
