package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * "This is what dessert is like!" (JJS True Cannon awakened 3, 20s). With total armor he runs forward into a blockable,
 * stunning kick that slides the target back (7), then a wild swing (3). Landed, his Awakening drain stops as the two
 * trade a flurry of blows (four of 5) before they're pushed apart (6); 10 to himself, 2 a blow. Interrupting an action
 * with the kick guarantees the swing. Feintable until the swing.
 */
public final class DessertAbility extends Ability {
    public static final String ID = "dessert";

    public DessertAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public boolean isTechnique() {
        return false;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return RyuCombat.cfg().dessertCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    static final class Instance extends AbilityInstance implements Feintable {
        @Nullable private LivingEntity victim;
        private int kickAt = -1, swingAt = -1, scene = -1, endAt = -1, blows;
        private float meter;

        Instance(Ability a, AbilityContext ctx) {
            super(a, ctx);
        }

        @Override
        public boolean feintable() {
            return swingAt < 0;
        }

        @Override
        public void start() {
            Anim.play(user, "ryu_dessert_run");
            setPhase(0, 24);
        }

        @Override
        public void tick() {
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (kickAt < 0) {
                // The run, armored against everything.
                Statuses.apply(user, CombatStatus.MELEE_ARMOR, 2);
                Statuses.apply(user, CombatStatus.BULLET_ARMOR, 2);
                HakariCombat.drive(user, HakariCombat.flat(user), 0.62);
                LivingEntity t = age >= 3 ? HakariCombat.firstInFront(user, 1.8, 1.5, 2.2) : null;
                if (t != null) {
                    kickAt = age;
                    Anim.play(user, "ryu_dessert_kick");
                    boolean acting = dev.rick.jjk.gojo.GojoCombat.acting(t);
                    HitResult r = HakariCombat.hit(RyuCombat.strike(user, ID, cfg.dessertKick, false)
                            .knockback(Knockback.directional(HakariCombat.flat(user), 0.55, 0)).hitstun(acting ? 22 : 14).fx("ryu_kick", 1f).build(), t);
                    if (r.connected()) victim = t;
                } else if (age > 22) {
                    endAt = age + 8;
                    Anim.play(user, "ryu_dessert_miss");
                }
                return;
            }
            if (swingAt < 0) {
                if (age < kickAt + 6) return;
                swingAt = age;
                Anim.play(user, "ryu_dessert_swing");
                LivingEntity t = victim != null ? victim : HakariCombat.firstInFront(user, 2.6, 1.8, 2.2);
                if (t == null) {
                    endAt = age + 12;
                    return;
                }
                HitResult r = HakariCombat.hit(RyuCombat.strike(user, ID, cfg.dessertSwing, true).knockback(Knockback.HOLD).hitstun(30)
                        .fx("ryu_punch_heavy", 1f).build(), t);
                if (!r.connected()) {
                    endAt = age + 12;
                    return;
                }
                victim = t;
                scene = age;
                meter = caster.awakening();
                Statuses.apply(t, CombatStatus.GRABBED, 30);
                Anim.play(user, "ryu_dessert_flurry");
                Fx.play(level, "ryu_dessert_scene", t.getBoundingBox().getCenter(), HakariCombat.flat(user), 1f, user.getId());
                return;
            }
            if (victim == null || !victim.isAlive()) {
                endAt = age + 6;
                return;
            }
            // The cutscene: the Awakening holds while they trade blows.
            if (caster.awakening() < meter) caster.setAwakening(meter);
            HakariCombat.carry(user, victim, 1.5);
            HakariCombat.faceTowards(victim, user.getEyePosition());
            int k = age - scene;
            if (k > 0 && k % 6 == 0 && blows < 4) {
                blows++;
                HakariCombat.hit(RyuCombat.strike(user, ID, cfg.dessertBlow, true).knockback(Knockback.HOLD).hitstun(14).noComboScaling()
                        .fx("ryu_punch_heavy", 1f).build(), victim);
                RyuCombat.selfDamage(user, cfg.dessertSelf);
                Fx.play(level, "ryu_fist_clash", victim.getBoundingBox().getCenter(), HakariCombat.flat(user), 1.2f, user.getId());
                Fx.shake(level, user.position(), 20, 0.5f, 6);
            }
            if (blows >= 4 && k >= 28) {
                Statuses.remove(victim, CombatStatus.GRABBED);
                Vec3 away = victim.position().subtract(user.position()).normalize();
                if (RyuCombat.finishable(victim)) RyuCombat.execute(user, victim, ID, "ryu_punch_heavy");
                else HakariCombat.hit(RyuCombat.strike(user, ID, cfg.dessertPush, true).knockback(Knockback.set(away.scale(1.1).add(0, 0.3, 0)))
                        .hitstun(20).status(CombatStatus.LAUNCHED, 16).fx("ryu_punch_heavy", 1.3f).build(), victim);
                victim = null;
                endAt = age + 10;
            }
        }

        @Override
        public boolean uninterruptible() {
            return kickAt < 0 || scene >= 0;
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void end() {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
        }
    }
}
