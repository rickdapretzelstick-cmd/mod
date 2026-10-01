package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rika Haymaker (JJS base Rika, 10s shared). She hovers over to the target if they are within 10 studs of her, then
 * slowly winds up a heavy blow that knocks them far back (12). Blockable, uninterruptible, bypasses ragdoll; a bullet.
 * Blocked, it doesn't ragdoll but keeps its knockback and deals 1.5x (18). A kill gives Yuta their technique.
 */
public final class RikaHaymakerAbility extends RikaMove {
    public static final String ID = "rika_haymaker";
    private static final int WINDUP = 22;

    public RikaHaymakerAbility() {
        super(ID, false);
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().rikaHaymakerCooldown;
    }

    @Override
    protected AbilityInstance create(AbilityContext ctx, RikaEntity rika, @Nullable LivingEntity found) {
        // Only someone within 10 studs of her: otherwise she swings where she is.
        LivingEntity t = found != null && found.distanceTo(rika) <= YutaCombat.cfg().rikaHaymakerReach + 2 ? found : null;
        return new RikaMove.Instance(this, ctx, rika, t, WINDUP + 12) {
            @Override
            protected void begin() {
                anim("rika_haymaker");
                Fx.play(level, "rika_haymaker_windup", rika.position().add(0, 2.0, 0), facing(), 1f, rika.getId());
            }

            @Override
            protected void step() {
                if (age < WINDUP) {
                    if (target != null && target.isAlive()) approach(target, 1.5, 0.1, 0.9);
                    return;
                }
                if (age == WINDUP) punch(YutaCombat.cfg());
                if (age >= WINDUP + 12) finish();
            }

            private void punch(JJKConfig.Yuta cfg) {
                Vec3 dir = facing();
                Vec3 fist = rika.position().add(0, 1.4, 0).add(dir.scale(1.8));
                Fx.play(level, "rika_haymaker", fist, dir, 1f, rika.getId());
                Fx.shake(level, fist, 20, 0.8f, 10);
                for (LivingEntity v : HitboxQuery.targets(user, HitShape.sphere(fist, 1.7), 0.3, false)) {
                    if (YutaCombat.finishable(v)) {
                        copyOnKill(YutaCombat.execute(user, v, ID, "hit_heavy"));
                        continue;
                    }
                    Knockback far = Knockback.directional(dir, 2.4, 0.55);
                    HitResult r = HakariCombat.hit(YutaCombat.rika(user, rika, ID, cfg.rikaHaymakerDamage, true).tag(AttackTag.OTG)
                            .knockback(far).hitstun(30).status(CombatStatus.LAUNCHED, 34).noComboScaling().fx("hit_heavy", 1.6f).build(), v);
                    if (r.outcome() == HitResult.Outcome.BLOCKED) {
                        // Blocked: no ragdoll, the same knockback, and half again the damage.
                        r = HakariCombat.hit(YutaCombat.rika(user, rika, ID, cfg.rikaHaymakerBlockedDamage, true).tag(AttackTag.UNBLOCKABLE)
                                .knockback(far).hitstun(12).noComboScaling().fx("block", 1.4f).build(), v);
                    }
                    copyOnKill(r);
                }
            }
        };
    }
}
