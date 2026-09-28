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
 * Jackpot 2 — Lucky Rushdown. Hakari breaks into a sprint and runs his target down (steering after them), smashes into
 * them, drags them along the ground ploughing through whatever is in the way, and hurls them. Approach → contact → drag
 * → throw.
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

            @Override
            public void start() {
                Anim.play(user, "rushdown_run");
                setPhase(0, JJKConfig.get().hakari.rushdownRunTicks);
                Fx.play(level, "rushdown_start", user.position().add(0, 0.6, 0), HakariCombat.flat(user), 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
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
                        Hit grab = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.rushdownGrabDamage).tag(AttackTag.MELEE)
                                .origin(user.getEyePosition()).knockback(Knockback.HOLD).hitstun(cfg.rushdownDragTicks + 6).fx("rushdown_grab", 1f).build();
                        if (HakariCombat.hit(grab, hit).connected()) {
                            victim = hit;
                            grabbedAt = age;
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
                if (age - grabbedAt >= cfg.rushdownDragTicks) {
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

            @Override
            public void end() {
                if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            }
        };
    }
}
