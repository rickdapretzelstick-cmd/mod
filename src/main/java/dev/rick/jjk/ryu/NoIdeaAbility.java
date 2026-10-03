package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
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
 * "I had no idea..." (JJS True Cannon awakened 2, 15s). He winds his arm back for a punch; landed, the two clash fists
 * in a short exchange and he pushes them away at the end: grab 3, seven punches of 2, push 3 (20), costing him 2 on the
 * grab and 1 a punch. Explosion armor: hits during the wind-up (anything but a ragdolling swarm) lengthen it instead of
 * stopping him. Feintable at any time.
 */
public final class NoIdeaAbility extends Ability {
    public static final String ID = "no_idea";

    public NoIdeaAbility() {
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
        return RyuCombat.cfg().noIdeaCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    static final class Instance extends AbilityInstance implements Feintable {
        private int windup;
        private float lastHealth;
        @Nullable private LivingEntity victim;
        private int grabAt = -1, endAt = -1, punches;

        Instance(Ability a, AbilityContext ctx) {
            super(a, ctx);
        }

        @Override
        public boolean feintable() {
            return true;
        }

        @Override
        public void start() {
            windup = RyuCombat.cfg().noIdeaWindup;
            lastHealth = user.getHealth();
            Anim.play(user, "ryu_no_idea_windup");
            RyuCombat.sfx(user, "ryu_noidea_start", 1f);
            setPhase(0, windup);
        }

        @Override
        public void tick() {
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (grabAt < 0) {
                // Armored: a blow lengthens the wind-up rather than ending it.
                if (user.getHealth() < lastHealth - 0.01f && windup < cfg.noIdeaWindup + 30) {
                    windup += 6;
                    Combat.state(user).remove(CombatStatus.HITSTUN);
                    Fx.play(level, "ryu_armor", user.position().add(0, 1, 0), Vec3.ZERO, 1f, user.getId());
                }
                lastHealth = user.getHealth();
                if (age < windup) return;
                Anim.play(user, "ryu_no_idea_punch");
                LivingEntity t = HakariCombat.firstInFront(user, 3.0, 1.6, 2.2);
                if (t == null) {
                    endAt = age + 12;
                    return;
                }
                HitResult r = HakariCombat.hit(RyuCombat.strike(user, ID, cfg.noIdeaGrab, true).knockback(Knockback.HOLD).hitstun(40)
                        .fx(RyuCombat.hitFx("ryu_noidea_hit", true), 1f).build(), t);
                if (!r.connected()) {
                    endAt = age + 12;
                    return;
                }
                victim = t;
                grabAt = age;
                RyuCombat.selfDamage(user, cfg.noIdeaSelfGrab);
                Statuses.apply(t, CombatStatus.GRABBED, 40);
                Anim.play(user, "ryu_no_idea_exchange");
                RyuCombat.sfx(user, "ryu_weave", 1f);
                setPhase(1, 34);
                return;
            }
            if (victim == null || !victim.isAlive()) {
                endAt = age + 6;
                return;
            }
            HakariCombat.carry(user, victim, 1.5);
            HakariCombat.faceTowards(victim, user.getEyePosition());
            int k = age - grabAt;
            if (k > 0 && k % 4 == 0 && punches < 7) {
                // Fist against fist: he gives as good as he gets, and his output wins.
                punches++;
                HakariCombat.hit(RyuCombat.strike(user, ID, cfg.noIdeaPunch, true).knockback(Knockback.HOLD).hitstun(12)
                        .fx(RyuCombat.hitFx("ryu_noidea_second", false), 0.8f).noComboScaling().build(), victim);
                RyuCombat.selfDamage(user, cfg.noIdeaSelfPunch);
                Fx.play(level, "ryu_fist_clash", user.getEyePosition().add(HakariCombat.flat(user).scale(0.8)), HakariCombat.flat(user), 1f, user.getId());
            }
            if (punches >= 7 && k >= 32) {
                Statuses.remove(victim, CombatStatus.GRABBED);
                HakariCombat.hit(RyuCombat.strike(user, ID, cfg.noIdeaPush, true)
                        .knockback(Knockback.directional(HakariCombat.flat(user), 0.9, 0.2)).hitstun(16).fx(RyuCombat.hitFx("ryu_noidea_hit_1", true), 1.1f).build(), victim);
                victim = null;
                endAt = age + 8;
            }
        }

        @Override
        public boolean uninterruptible() {
            return grabAt < 0 && endAt < 0;
        }

        @Override
        public float movementMultiplier() {
            return grabAt < 0 ? 0.35f : 0f;
        }

        @Override
        public void end() {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
        }
    }
}
