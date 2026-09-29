package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.entity.FireArrowEntity;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Open — "Fuga" (JJS King of Curses, 40s). Fire in his hands, a clap, the gesture of drawing a bow: the flames become an
 * arrow. With total i-frames he aims and looses it at high speed, and where it lands a massive pillar of fire goes up,
 * lifting everyone in it. Unblockable, uninterruptible, an explosion. The finisher burns them to a crisp.
 */
public final class OpenAbility extends Ability {
    public static final String ID = "open";
    /** Phases of the wind-up, from the GIF: flames (0), the clap (1), the bow drawn (2), aiming with i-frames (3). */
    public static final int CLAP = 14, DRAW = 21, AIM = 34;

    public OpenAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().openCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private boolean fired;

            @Override
            public void start() {
                Anim.play(user, "open_flames");
                setPhase(0, CLAP);
                Fx.play(level, "open_hands", user.position().add(0, 1.2, 0), user.getLookAngle(), 1f, user.getId());
            }

            @Override
            public boolean uninterruptible() {
                return true;
            }

            @Override
            public void tick() {
                JJKConfig.Yuji cfg = YujiCombat.cfg();
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.3, 0));
                if (age == CLAP) {
                    Anim.play(user, "open_clap");
                    setPhase(1, DRAW - CLAP);
                    Fx.play(level, "open_clap", user.getEyePosition().add(HakariCombat.flat(user).scale(0.5)), user.getLookAngle(), 1f, user.getId());
                }
                if (age == DRAW) {
                    Anim.play(user, "open_draw");
                    setPhase(2, AIM - DRAW);
                    Fx.play(level, "open_draw", user.getEyePosition(), user.getLookAngle(), 1f, user.getId());
                }
                if (age == AIM) {
                    setPhase(3, cfg.openWindup - AIM);
                    Anim.play(user, "open_aim");
                }
                if (age >= AIM) Statuses.apply(user, CombatStatus.EVADING, 3);
                if (age == cfg.openWindup && !fired) {
                    fired = true;
                    Anim.play(user, "open_release");
                    Vec3 dir = user.getLookAngle();
                    FireArrowEntity.fire(level, user, user.getEyePosition().add(dir.scale(1.2)).add(0, -0.2, 0), dir);
                    Fx.play(level, "open_fire", user.getEyePosition(), dir, 1f, user.getId());
                    Motion.set(user, dir.scale(-0.4));
                }
                if (age >= cfg.openWindup + 10) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0.1f;
            }
        };
    }

    /** The arrow landed: the pillar of fire. */
    public static void pillar(ServerLevel level, LivingEntity owner, Vec3 at) {
        JJKConfig.Yuji cfg = YujiCombat.cfg();
        Fx.play(level, "open_pillar", at, new Vec3(0, 1, 0), (float) cfg.openPillarRadius, owner.getId());
        Fx.shake(level, at, 48, 1.3f, 20);
        Fx.flash(level, at, 40, 0xA0FF8020, 8);
        HitShape shape = HitShape.capsule(at.add(0, -1, 0), at.add(0, 10, 0), cfg.openPillarRadius);
        for (LivingEntity t : HitboxQuery.targets(owner, shape, 0.3, false)) {
            if (YujiCombat.finishable(t)) {
                YujiCombat.execute(owner, t, ID, "open_burn");
                continue;
            }
            HakariCombat.hit(YujiCombat.slash(owner, ID, cfg.openDamage).tag(AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION).origin(at)
                    .knockback(Knockback.set(new Vec3(0, cfg.openLift, 0))).hitstun(40).status(CombatStatus.LAUNCHED, 44)
                    .fx("open_hit", 1.4f).build(), t);
            t.igniteForSeconds(4);
        }
        dev.rick.jjk.util.Destruction.sphere(level, at, 2.5, 5f, 60, owner, null, "jjk:open");
    }
}
