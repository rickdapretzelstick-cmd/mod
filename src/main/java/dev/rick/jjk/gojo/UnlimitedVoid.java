package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Domain Expansion: Unlimited Void. The sure-hit floods everyone inside with infinite information:
 * they're overloaded (can't move, attack or use techniques), their casts are interrupted, and they
 * hang in place. Damage is deliberately small: the domain is a control tool that sets up Gojo's own attacks.
 */
public final class UnlimitedVoid implements DomainDefinition {
    public static final UnlimitedVoid INSTANCE = new UnlimitedVoid();
    public static final String ID = "unlimited_void";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public float refinement() {
        return 1.3f;
    }

    @Override
    public double radius(LivingEntity owner) {
        return JJKConfig.get().domain.radius;
    }

    @Override
    public int duration(LivingEntity owner) {
        return JJKConfig.get().domain.duration;
    }

    @Override
    public boolean closedBarrier() {
        return true;
    }

    @Override
    public void applySureHit(DomainInstance domain, LivingEntity target, int ticksInside) {
        JJKConfig.Domain cfg = JJKConfig.get().domain;
        boolean first = ticksInside == 1;
        Statuses.apply(target, CombatStatus.OVERLOAD, 8);
        if (first) {
            Fx.play(domain.level, "domain_surehit", target.getBoundingBox().getCenter(), Vec3.ZERO, 1f, target.getId());
            // Whatever they were doing stops.
            AbilityCaster c = Casters.getOrNull(target);
            if (c != null) c.interrupt("overload");
        }
        // Drifting in the void: bleed off momentum so victims hang where they are.
        if (ticksInside % 4 == 0) Motion.set(target, target.getDeltaMovement().scale(0.4));
        if (cfg.sureHitDamage > 0 && ticksInside % Math.max(1, cfg.sureHitDamageInterval) == 0) {
            HitResolver.resolve(Hit.builder(domain.owner, "unlimited_void").type(ModDamageTypes.SURE_HIT).damage(cfg.sureHitDamage)
                    .tag(AttackTag.SURE_HIT, AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.BYPASS_INFINITY)
                    .origin(domain.center).knockback(Knockback.NONE).noComboScaling().fx("domain_surehit_tick", 0.6f).build(), target);
        }
    }

    @Override
    public void onRelease(DomainInstance domain, LivingEntity target) {
        Statuses.apply(target, CombatStatus.OVERLOAD, JJKConfig.get().domain.lingeringOverload);
    }
}
