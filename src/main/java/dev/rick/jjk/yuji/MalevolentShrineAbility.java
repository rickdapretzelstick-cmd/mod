package dev.rick.jjk.yuji;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.domain.DomainCinematics;
import dev.rick.jjk.core.domain.DomainCounter;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The hand sign, then Malevolent Shrine expands around him (JJS King of Curses, 120s). */
public final class MalevolentShrineAbility extends Ability implements dev.rick.jjk.core.domain.DomainAbility {
    public static final String ID = "malevolent_shrine";

    public MalevolentShrineAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public DomainDefinition domain() {
        return MalevolentShrine.INSTANCE;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YujiCombat.cfg().shrineCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().shrineCooldown;
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
                int startup = YujiCombat.cfg().shrineStartup;
                Anim.play(user, "shrine_sign");
                setPhase(0, startup);
                Fx.play(level, "shrine_charge", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                DomainCinematics.opening(user, MalevolentShrine.INSTANCE, startup);
                DomainCounter.opening(user, MalevolentShrine.INSTANCE);
            }

            @Override
            public void tick() {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.3, 0));
                if (age >= YujiCombat.cfg().shrineStartup) {
                    expanded = DomainManager.expand(user, MalevolentShrine.INSTANCE) != null;
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
                    caster.setEnergy(caster.energy() + YujiCombat.cfg().shrineCost * 0.75f);
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
