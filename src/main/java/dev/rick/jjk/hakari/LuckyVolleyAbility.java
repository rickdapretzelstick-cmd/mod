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
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jackpot 1 — Lucky Volley. A flurry of punches (Hakari can keep moving through it, carrying the target along) ending in
 * a powerful unblockable swipe that launches them away. A target low enough is sent flying for good by the swipe (the
 * finisher).
 */
public final class LuckyVolleyAbility extends Ability {
    public static final String ID = "lucky_volley";
    private static final int OPEN = 4, FLURRY_START = 8, FLURRY_EVERY = 2;

    public LuckyVolleyAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.volleyCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private LivingEntity target;
            private int flurry;
            private int finalAt = -1;

            @Override
            public void start() {
                Anim.play(user, "volley_open");
                setPhase(0, 40);
                Motion.set(user, HakariCombat.flat(user).scale(0.45).add(0, user.getDeltaMovement().y, 0));
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                Vec3 f = HakariCombat.flat(user);
                if (age == OPEN) {
                    target = HakariCombat.firstInFront(user, 2.8, 1.8, 2.2);
                    Hit open = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.volleyOpenerDamage).tag(AttackTag.MELEE)
                            .origin(user.getEyePosition()).knockback(Knockback.HOLD).hitstun(20).fx("lucky_hit", 1f).build();
                    Fx.play(level, "swing", user.getEyePosition().add(f.scale(0.9)), f, 1.3f, user.getId());
                    if (target == null || !HakariCombat.hit(open, target).connected()) {
                        target = null;
                        finalAt = age + 6;
                        return;
                    }
                    Anim.play(user, "volley_flurry");
                    setPhase(1, cfg.volleyFlurryHits * FLURRY_EVERY);
                }
                if (target != null && age >= FLURRY_START && flurry < cfg.volleyFlurryHits) {
                    if (!target.isAlive()) {
                        finish();
                        return;
                    }
                    Statuses.apply(target, CombatStatus.GRABBED, 4);
                    HakariCombat.carry(user, target, 1.5);
                    if ((age - FLURRY_START) % FLURRY_EVERY == 0) {
                        Hit jab = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.volleyFlurryDamage).tag(AttackTag.MELEE)
                                .origin(user.getEyePosition()).knockback(Knockback.HOLD).hitstun(8).noComboScaling().fx("lucky_flurry", 0.8f).build();
                        HakariCombat.hit(jab, target);
                        flurry++;
                        if (flurry == cfg.volleyFlurryHits) finalAt = age + 3;
                    }
                }
                if (age == finalAt) {
                    if (target != null && target.isAlive()) {
                        Anim.play(user, "volley_final");
                        setPhase(2, 8);
                        Statuses.remove(target, CombatStatus.GRABBED);
                        if (HakariCombat.finishable(target)) {
                            HakariCombat.execute(user, target, ID, "lucky_finisher");
                            Fx.shake(level, target.position(), 24, 1.1f, 12);
                            finalAt = age;
                            target = null;
                            return;
                        }
                        Hit last = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.volleyFinalDamage).tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.UNBLOCKABLE)
                                .origin(user.getEyePosition()).knockback(Knockback.directional(f, cfg.volleyFinalKnockback, 0.4)).hitstun(24)
                                .status(CombatStatus.LAUNCHED, 20).fx("lucky_final", 1.3f).build();
                        HakariCombat.hit(last, target);
                        Fx.shake(level, target.position(), 18, 0.8f, 10);
                    }
                }
                if (finalAt >= 0 && age >= finalAt + 7) finish();
                if (age > 60) finish();
            }

            @Override
            public void end() {
                if (target != null) Statuses.remove(target, CombatStatus.GRABBED);
            }

            @Override
            public float movementMultiplier() {
                // He can walk the barrage forward.
                return target != null ? 0.55f : 0.15f;
            }
        };
    }
}
