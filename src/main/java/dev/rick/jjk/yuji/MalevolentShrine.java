package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.structure.StructureSpec;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModBlocks;
import dev.rick.jjk.registry.ModDamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Domain Expansion: Malevolent Shrine. A black void with the shrine standing in its middle over a pool of blood; its
 * sure hit is a ceaseless stream of Dismantles slicing everything inside — 109 slashes of 2 over the 18 seconds, 218 in
 * all. A guard cuts each to 0.5 and nobody guarding can be finished by it.
 */
public final class MalevolentShrine implements DomainDefinition {
    public static final MalevolentShrine INSTANCE = new MalevolentShrine();
    public static final String ID = "malevolent_shrine";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int clashColor() {
        return 0xFFE0283A;
    }

    @Override
    public double radius(LivingEntity owner) {
        return JJKConfig.get().yuji.shrineRadius;
    }

    @Override
    public int duration(LivingEntity owner) {
        return JJKConfig.get().yuji.shrineDuration;
    }

    @Override
    public String displayName() {
        return "Malevolent Shrine";
    }

    @Override
    public int formingTicks() {
        return JJKConfig.get().yuji.shrineFormationTicks;
    }

    @Override
    public StructureSpec structure(LivingEntity owner) {
        JJKConfig.Domain cfg = JJKConfig.get().domain;
        return new StructureSpec(JJKConfig.get().yuji.shrineRadius, Math.max(1, cfg.shellThickness), ModBlocks.SHRINE_BARRIER.defaultBlockState(),
                ModBlocks.SHRINE_FLOOR.defaultBlockState(), cfg.clearInterior);
    }

    @Override
    public boolean closedBarrier() {
        return true;
    }

    @Override
    public void applySureHit(DomainInstance domain, LivingEntity target, int ticksInside) {
        JJKConfig.Yuji cfg = JJKConfig.get().yuji;
        if (ticksInside % Math.max(1, cfg.shrineSlashInterval) != 0) return;
        boolean guarding = Combat.isGuarding(target);
        float damage = guarding ? cfg.shrineBlockedDamage : cfg.shrineSlashDamage;
        // Through a guard the slashes can't finish anyone.
        if (guarding && target.getHealth() <= damage + 0.5f) damage = Math.max(0, target.getHealth() - 1f);
        Vec3 at = target.getBoundingBox().getCenter();
        Fx.play(domain.level, "shrine_slash", at, Vec3.ZERO, guarding ? 0.6f : 1f, target.getId());
        if (damage <= 0) return;
        Hit.Builder b = Hit.builder(domain.owner, ID).type(ModDamageTypes.SURE_HIT).damage(damage)
                .tag(AttackTag.SURE_HIT, AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.BYPASS_INFINITY)
                .origin(domain.center).knockback(Knockback.NONE).noComboScaling().hitstun(guarding ? 0 : 4);
        if (!guarding) b.tag(AttackTag.ULTIMATE);
        HitResolver.resolve(b.build(), target);
    }
}
