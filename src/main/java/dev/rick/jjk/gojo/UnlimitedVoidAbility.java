package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hand sign, startup, then Unlimited Void expands around Gojo. The startup is committed and can be interrupted by
 * stuns; if interrupted, most of the energy is refunded and the cooldown is short.
 */
public final class UnlimitedVoidAbility extends Ability implements dev.rick.jjk.core.domain.DomainAbility {
    public static final String ID = "unlimited_void";

    public UnlimitedVoidAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public dev.rick.jjk.core.domain.DomainDefinition domain() {
        return UnlimitedVoid.INSTANCE;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().domain.cost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().gojo.infiniteVoidCooldown;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        return DomainManager.ownedBy(ctx.user()) != null ? "domain_active" : null;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private boolean expanded;

            @Override
            public void start() {
                Anim.play(user, "domain_sign");
                setPhase(0, JJKConfig.get().domain.startup);
                Fx.play(level, "domain_charge", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                // Everyone around sees it coming; anyone who can answer gets their counter window.
                dev.rick.jjk.core.domain.DomainCinematics.opening(user, UnlimitedVoid.INSTANCE, JJKConfig.get().domain.startup);
                dev.rick.jjk.core.domain.DomainCounter.opening(user, UnlimitedVoid.INSTANCE);
            }

            @Override
            public void tick() {
                // Rooted in place while forming the sign.
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.3, 0));
                if (age >= JJKConfig.get().domain.startup) {
                    expanded = DomainManager.expand(user, UnlimitedVoid.INSTANCE) != null;
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
                    caster.setEnergy(caster.energy() + JJKConfig.get().domain.cost * 0.75f);
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
}
