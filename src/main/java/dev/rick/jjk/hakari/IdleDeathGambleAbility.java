package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.domain.DomainAbility;
import dev.rick.jjk.core.domain.DomainCinematics;
import dev.rick.jjk.core.domain.DomainCounter;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hakari's Ultimate: Domain Expansion — Idle Death Gamble. Needs a full Awakening meter, which it spends. The opening
 * runs through the shared domain pipeline: the universal cinematic, the counter window for anyone who can answer, and
 * (if they do) the domain clash. Pressed during someone else's opening with a full meter, it answers them instantly.
 */
public final class IdleDeathGambleAbility extends Ability implements DomainAbility {
    public static final String ID = "idle_death_gamble";

    public IdleDeathGambleAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public DomainDefinition domain() {
        return IdleDeathGamble.INSTANCE;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.domainCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.domainCooldown;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        AbilityCaster c = ctx.caster();
        if (DomainManager.ownedBy(ctx.user()) != null) return "domain_active";
        return c.noCost() || c.awakening() >= c.maxAwakening() ? null : "meter_not_full";
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private boolean expanded;

            @Override
            public void start() {
                // Someone nearby is opening a domain: answer it straight away (the shared counter).
                if (DomainCounter.tryCounter(caster)) {
                    expanded = true;
                    finish();
                    return;
                }
                if (!caster.noCost()) caster.setAwakening(0);
                int startup = JJKConfig.get().hakari.domainStartup;
                // Total invincibility through the hand sign.
                dev.rick.jjk.core.combat.Statuses.apply(user, dev.rick.jjk.core.combat.CombatStatus.EVADING, startup + 2);
                Anim.play(user, "idg_sign");
                setPhase(0, startup);
                Fx.play(level, "idg_charge", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                DomainCinematics.opening(user, IdleDeathGamble.INSTANCE, startup);
                DomainCounter.opening(user, IdleDeathGamble.INSTANCE);
            }

            @Override
            public void tick() {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.3, 0));
                if (age >= JJKConfig.get().hakari.domainStartup) {
                    expanded = DomainManager.expand(user, IdleDeathGamble.INSTANCE) != null;
                    if (expanded) user.heal(user.getMaxHealth() * JJKConfig.get().hakari.domainHealShare);
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
                    // Interrupted before it opened: most of what it cost comes back.
                    caster.setEnergy(caster.energy() + JJKConfig.get().hakari.domainCost * 0.75f);
                    caster.setAwakening(caster.awakening() + caster.maxAwakening() * 0.75f);
                    caster.resetSlot(AbilitySlot.ULTIMATE);
                    caster.startCooldown(AbilitySlot.ULTIMATE, 60);
                    Fx.play(level, "domain_fizzle", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                }
                super.interrupt(reason);
            }
        };
    }
}
