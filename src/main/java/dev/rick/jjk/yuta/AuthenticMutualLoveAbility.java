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
 * <p>Jacob's Ladder (the domain's key again after four direct katana hits, with a target in front within range): a
 * divine ray from the sky catches them and slowly lifts them, then takes their health (62.5) and 35% of their Awakening
 * meter (50% if they are awakened) in one blow. He has i-frames during the wind-up and the ray ignores everyone else's;
 * it takes its toll on the domain, which shatters with the blow. Once a domain. The finisher: their soul keeps rising
 * while the body falls. (JJS shatters the domain as the ray comes down; here it shatters on the final blow, so the lift
 * plays out inside the domain.)
 */
public final class AuthenticMutualLoveAbility extends Ability implements dev.rick.jjk.core.domain.DomainAbility {
    public static final String ID = "authentic_mutual_love";
    public static final AuthenticMutualLoveAbility INSTANCE = new AuthenticMutualLoveAbility();
    static final int LADDER_WINDUP = 14, LADDER_DESCEND = 6;

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
        // Someone in front of him, inside his domain.
        DomainInstance d = DomainManager.ownedBy(user);
        if (t == null || d == null || !d.contains(t)) return false;
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

    /**
     * Jacob's Ladder: the ray from the sky. Its sequence, all server-timed and seen by everyone:
     * <ol>
     *   <li>Aim ({@link #LADDER_WINDUP} ticks): Yuta points skyward, a gold circle marks the target's ground; he has
     *       i-frames.</li>
     *   <li>The ray descends ({@link #LADDER_DESCEND} ticks) from the sky onto them.</li>
     *   <li>Lift ({@code ladderTicks}): caught and restrained, they rise slowly in the light.</li>
     *   <li>Impact: their health (62.5) and Awakening meter (35%, 50% awakened) are taken once, in one blow (or the
     *       finisher: the soul keeps rising while the body falls), and the domain shatters with it.</li>
     * </ol>
     * If Yuta dies or leaves mid-way the target is let go; the domain ends with him (the framework's own rule).
     */
    static final class Ladder extends AbilityInstance {
        private final LivingEntity victim;
        private final Vec3 base;
        private boolean struck;

        Ladder(AbilityCaster caster, LivingEntity user, LivingEntity victim) {
            super(INSTANCE, new AbilityContext(caster, user, (ServerLevel) user.level(), AbilitySlot.SKILL_4, 0, 0, victim));
            this.victim = victim;
            base = victim.position();
        }

        private int impactAt() {
            return LADDER_WINDUP + LADDER_DESCEND + YutaCombat.cfg().ladderTicks;
        }

        @Override
        public void start() {
            Statuses.apply(user, CombatStatus.EVADING, LADDER_WINDUP + 2);
            Anim.play(user, "yuta_jacobs_ladder");
            HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
            setPhase(0, impactAt());
            Fx.play(level, "ladder_mark", base, Vec3.ZERO, LADDER_WINDUP + LADDER_DESCEND, victim.getId());
            YutaState.of(user).ladderHits = 0;
            YutaSync.send(user);
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (!user.isAlive() || user.level() != level) {
                finish();
                return;
            }
            if (struck) {
                if (age >= impactAt() + 8) finish();
                return;
            }
            if (!victim.isAlive() || victim.level() != level) {
                // Gone before the end: the ladder still takes its toll on the domain.
                shatter();
                finish();
                return;
            }
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            if (age == LADDER_WINDUP) {
                // The ray comes down from the sky.
                Fx.play(level, "jacobs_ladder", base, new Vec3(0, 1, 0), LADDER_DESCEND + cfg.ladderTicks, victim.getId());
            }
            if (age >= LADDER_WINDUP) {
                Statuses.apply(victim, CombatStatus.GRABBED, 4);
                // Caught in it: held, then slowly lifted.
                int lift = Math.max(0, age - LADDER_WINDUP - LADDER_DESCEND);
                Vec3 want = base.add(0, Math.min(5, lift * 0.085), 0);
                Motion.set(victim, want.subtract(victim.position()).scale(0.4));
                if (lift > 0 && lift % 10 == 0) Fx.play(level, "ladder_hit", victim.getBoundingBox().getCenter(), Vec3.ZERO, 1f, victim.getId());
            }
            if (age >= impactAt()) strike(cfg);
        }

        /** The one blow: health and meter, once; then the domain shatters. */
        private void strike(JJKConfig.Yuta cfg) {
            struck = true;
            Statuses.remove(victim, CombatStatus.GRABBED);
            AbilityCaster vc = Casters.getOrNull(victim);
            if (vc != null) {
                float share = vc.isAwakened() ? cfg.ladderDrainAwakened : cfg.ladderDrain;
                vc.setAwakening(vc.awakening() - vc.maxAwakening() * share);
            }
            Fx.play(level, "ladder_impact", victim.getBoundingBox().getCenter(), new Vec3(0, 1, 0), 1f, victim.getId());
            Fx.shake(level, victim.position(), 30, 1.0f, 14);
            if (victim.getHealth() <= cfg.ladderDamage + 0.01f || YutaCombat.finishable(victim)) {
                // Their soul keeps rising while the body falls.
                Fx.play(level, "jacobs_ladder_finisher", victim.position(), new Vec3(0, 1, 0), 1f, victim.getId());
                YutaCombat.execute(user, victim, ID, "jacobs_ladder_finisher");
            } else {
                // It bypasses every i-frame.
                Hit hit = Hit.builder(user, ID).type(ModDamageTypes.SURE_HIT).damage(cfg.ladderDamage)
                        .tag(AttackTag.SURE_HIT, AttackTag.UNBLOCKABLE, AttackTag.TECHNIQUE, AttackTag.BYPASS_INFINITY, AttackTag.OTG, AttackTag.NO_METER)
                        .origin(victim.position().add(0, 6, 0)).knockback(Knockback.set(new Vec3(0, -0.8, 0))).hitstun(30)
                        .status(CombatStatus.KNOCKDOWN, 30).noComboScaling().fx("ladder_hit", 2f).build();
                HitResolver.resolve(hit, victim);
            }
            shatter();
            YutaSync.send(user);
        }

        /** It takes its toll on the domain: it shatters. */
        private void shatter() {
            DomainInstance d = DomainManager.ownedBy(user);
            if (d == null || d.definition != AuthenticMutualLove.INSTANCE) return;
            Fx.play(level, "aml_shatter", d.center, Vec3.ZERO, (float) d.radius, user.getId());
            DomainManager.cancel(d, DomainInstance.EndReason.CANCELLED);
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
