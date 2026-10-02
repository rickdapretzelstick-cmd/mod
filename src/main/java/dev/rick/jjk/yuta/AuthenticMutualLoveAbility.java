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
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainCinematics;
import dev.rick.jjk.core.domain.DomainCounter;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Authentic Mutual Love (JJS True Love domain, 120s, 45s; a longer wind-up than most). See {@link AuthenticMutualLove}.
 *
 * <p>Jacob's Ladder (used again after four direct katana swings, with a target in sight): a divine ray from the sky
 * catches them and slowly lifts them while draining their health (62.5) and 35% of their Awakening meter (50% if
 * they are awakened). He has i-frames during the wind-up and the ray ignores everyone else's; it takes its toll on the
 * domain, which shatters at once. Once a domain. The finisher: their soul keeps rising while the body falls.
 */
public final class AuthenticMutualLoveAbility extends Ability implements dev.rick.jjk.core.domain.DomainAbility {
    public static final String ID = "authentic_mutual_love";
    public static final AuthenticMutualLoveAbility INSTANCE = new AuthenticMutualLoveAbility();
    static final int LADDER_WINDUP = 14;

    private AuthenticMutualLoveAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public DomainDefinition domain() {
        return AuthenticMutualLove.INSTANCE;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YutaCombat.cfg().domainCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().domainCooldown;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        return DomainManager.ownedBy(ctx.user()) != null ? "domain_active" : null;
    }

    /** Jacob's Ladder is ready: four direct katana swings in this domain, and not used yet. */
    public static boolean ladderReady(LivingEntity user) {
        YutaState s = YutaState.get(user);
        DomainInstance d = DomainManager.ownedBy(user);
        return s != null && d != null && d.definition == AuthenticMutualLove.INSTANCE && d.isLive()
                && s.ladderHits >= YutaCombat.cfg().ladderHits && !s.ladderUsed;
    }

    /** The domain's key pressed again: Jacob's Ladder, if it's ready and someone is in sight. */
    static boolean ladderPress(AbilityCaster caster, @Nullable Entity hint) {
        LivingEntity user = caster.owner;
        if (!ladderReady(user) || caster.isBusy()) return false;
        LivingEntity t = HakariCombat.aim(user, 40, hint);
        if (t == null) return false;
        YutaState.of(user).ladderUsed = true;
        caster.begin(new Ladder(caster, user, t));
        return true;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private boolean expanded;

            @Override
            public void start() {
                int startup = YutaCombat.cfg().domainStartup;
                Anim.play(user, "yuta_domain_sign");
                setPhase(0, startup);
                Fx.play(level, "aml_charge", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                DomainCinematics.opening(user, AuthenticMutualLove.INSTANCE, startup);
                DomainCounter.opening(user, AuthenticMutualLove.INSTANCE);
            }

            @Override
            public void tick() {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.3, 0));
                if (age >= YutaCombat.cfg().domainStartup) {
                    expanded = DomainManager.expand(user, AuthenticMutualLove.INSTANCE) != null;
                    Anim.play(user, "domain_release");
                    finish();
                }
            }

            @Override
            public float movementMultiplier() {
                return 0f;
            }

            @Override
            public void interrupt(String reason) {
                if (!expanded) {
                    caster.setEnergy(caster.energy() + YutaCombat.cfg().domainCost * 0.75f);
                    for (AbilitySlot s : AbilitySlot.values()) {
                        if (caster.ability(s) == ability) {
                            caster.resetSlot(s);
                            caster.startCooldown(s, 60);
                        }
                    }
                    Fx.play(level, "domain_fizzle", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                }
                super.interrupt(reason);
            }
        };
    }

    /** Jacob's Ladder: the ray from the sky. */
    static final class Ladder extends AbilityInstance {
        private final LivingEntity victim;
        private final Vec3 base;
        private int hits;

        Ladder(AbilityCaster caster, LivingEntity user, LivingEntity victim) {
            super(INSTANCE, new AbilityContext(caster, user, (ServerLevel) user.level(), AbilitySlot.SKILL_4, 0, 0, victim));
            this.victim = victim;
            base = victim.position();
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            Statuses.apply(user, CombatStatus.EVADING, LADDER_WINDUP + 2);
            Anim.play(user, "yuta_jacobs_ladder");
            setPhase(0, LADDER_WINDUP + cfg.ladderTicks);
            Fx.play(level, "jacobs_ladder", base, new Vec3(0, 1, 0), LADDER_WINDUP + cfg.ladderTicks, victim.getId());
            // It takes its toll on the domain: it shatters.
            DomainInstance d = DomainManager.ownedBy(user);
            if (d != null) DomainManager.cancel(d, DomainInstance.EndReason.CANCELLED);
            // Their Awakening meter drains away with them.
            AbilityCaster vc = Casters.getOrNull(victim);
            if (vc != null) {
                float share = vc.isAwakened() ? cfg.ladderDrainAwakened : cfg.ladderDrain;
                vc.setAwakening(vc.awakening() - vc.maxAwakening() * share);
            }
            YutaSync.send(user);
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            if (!victim.isAlive()) {
                if (age > LADDER_WINDUP) finish();
                return;
            }
            // Caught in the ray and slowly lifted.
            if (age >= LADDER_WINDUP / 2) {
                Statuses.apply(victim, CombatStatus.GRABBED, 4);
                Vec3 want = base.add(0, Math.min(6, (age - LADDER_WINDUP / 2) * 0.09), 0);
                Motion.set(victim, want.subtract(victim.position()).scale(0.4));
            }
            int t = age - LADDER_WINDUP;
            int every = Math.max(1, cfg.ladderTicks / 6);
            if (t >= 0 && t < cfg.ladderTicks && t % every == 0) {
                hits++;
                float per = cfg.ladderDamage / 6f;
                if (victim.getHealth() <= per + 0.01f || hits >= 6 && YutaCombat.finishable(victim)) {
                    // Their soul keeps rising while the body falls.
                    Fx.play(level, "jacobs_ladder_finisher", victim.position(), new Vec3(0, 1, 0), 1f, victim.getId());
                    Statuses.remove(victim, CombatStatus.GRABBED);
                    YutaCombat.execute(user, victim, ID, "jacobs_ladder_finisher");
                    return;
                }
                // It bypasses every i-frame.
                Hit hit = Hit.builder(user, ID).type(ModDamageTypes.SURE_HIT).damage(per)
                        .tag(AttackTag.SURE_HIT, AttackTag.UNBLOCKABLE, AttackTag.TECHNIQUE, AttackTag.BYPASS_INFINITY, AttackTag.OTG, AttackTag.NO_METER)
                        .origin(victim.position().add(0, 6, 0)).knockback(Knockback.HOLD).hitstun(20).noComboScaling().fx("ladder_hit", 1f).build();
                HitResolver.resolve(hit, victim);
            }
            if (t >= cfg.ladderTicks) {
                Statuses.remove(victim, CombatStatus.GRABBED);
                finish();
            }
        }

        @Override
        public boolean uninterruptible() {
            return true;
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void end() {
            Statuses.remove(victim, CombatStatus.GRABBED);
            YutaState.of(user).fists = true;
        }
    }
}
