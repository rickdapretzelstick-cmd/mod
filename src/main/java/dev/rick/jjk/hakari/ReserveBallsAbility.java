package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.PachinkoBallEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 1 — Reserve Balls. Hakari flicks a handful of steel pachinko balls out of his reserve: a quick three-ball volley aimed
 * at the crosshair target (or straight ahead). Each ball hitstuns lightly; the last one of the volley knocks back.
 */
public final class ReserveBallsAbility extends Ability {
    public static final String ID = "reserve_balls";

    public ReserveBallsAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.ballsCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.ballsCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        LivingEntity target = HakariCombat.aim(ctx.user(), 28, ctx.targetHint());
        return new AbilityInstance(this, ctx) {
            private int fired;

            @Override
            public void start() {
                Anim.play(user, "reserve_balls");
                setPhase(0, JJKConfig.get().hakari.ballsCount * JJKConfig.get().hakari.ballsInterval + 4);
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                if (age >= 3 && (age - 3) % cfg.ballsInterval == 0 && fired < cfg.ballsCount) {
                    Vec3 hand = user.getEyePosition().add(HakariCombat.flat(user).scale(0.7)).add(0, -0.3, 0);
                    Vec3 aimAt = target != null && target.isAlive() ? target.getBoundingBox().getCenter() : user.getEyePosition().add(user.getLookAngle().scale(20));
                    Vec3 dir = aimAt.subtract(hand).normalize();
                    // A slight fan so the balls read as a thrown handful.
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    double spread = (fired - (cfg.ballsCount - 1) / 2.0) * 0.045;
                    dir = dir.add(side.scale(spread)).add(0, 0.04, 0).normalize();
                    boolean last = fired == cfg.ballsCount - 1;
                    PachinkoBallEntity.fire(level, user, hand, dir, last);
                    Fx.play(level, "ball_throw", hand, dir, last ? 1.2f : 1f, user.getId());
                    fired++;
                }
                if (age >= 3 + cfg.ballsCount * cfg.ballsInterval + 2) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0.6f;
            }
        };
    }
}
