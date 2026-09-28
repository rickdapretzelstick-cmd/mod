package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Infinity as an interception rule: attacks never reach the defender, but every stopped attack costs cursed energy
 * (techniques cost in proportion to their power). If the energy can't be paid, Infinity collapses and the attack lands.
 *
 * Reactions by attack type:
 * <ul>
 *   <li>Sure-hit, "bypass infinity" and technique-neutralising attacks pass straight through.</li>
 *   <li>Environmental damage (falling, fire, drowning...) isn't an attack; it passes.</li>
 *   <li>Explosions are mostly stopped; a fraction of the blast still gets through.</li>
 *   <li>Melee, projectiles and techniques stop at the boundary.</li>
 * </ul>
 */
public final class InfinityDefense implements DefenseLayer {
    public static final String ID = "infinity";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean isActive(LivingEntity defender) {
        AbilityCaster c = Casters.getOrNull(defender);
        return c != null && c.toggled(InfinityAbility.ID) && !Combat.techniquesLocked(defender);
    }

    @Override
    public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
        if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.BYPASS_INFINITY) || attack.has(AttackTag.NEUTRALIZES_TECHNIQUES)
                || attack.has(AttackTag.ENVIRONMENTAL)) {
            return DefenseResult.PASS;
        }
        JJKConfig.Infinity cfg = JJKConfig.get().infinity;
        AbilityCaster caster = Casters.getOrNull(defender);
        if (caster == null) return DefenseResult.PASS;
        float cost = cost(attack, cfg);
        if (!caster.canAfford(cost)) {
            caster.setEnergy(0);
            if (caster.character() instanceof GojoCharacter gojo) gojo.infinity.toggleOff(caster, "overwhelmed");
            return DefenseResult.PASS;
        }
        caster.spend(cost);
        if (attack.has(AttackTag.EXPLOSION) && !attack.has(AttackTag.TECHNIQUE)) {
            return DefenseResult.reduce(cfg.explosionPassThrough, "infinity");
        }
        return DefenseResult.negate("infinity");
    }

    static float cost(IncomingAttack attack, JJKConfig.Infinity cfg) {
        if (attack.has(AttackTag.TECHNIQUE)) return Math.max(2f, attack.damage * cfg.costPerTechniqueDamage);
        if (attack.has(AttackTag.PROJECTILE)) return cfg.costPerBlockedProjectile;
        if (attack.has(AttackTag.MELEE)) return cfg.costPerBlockedMelee * (attack.has(AttackTag.HEAVY) ? 2.5f : 1f);
        return Math.max(cfg.costPerBlockedMelee, attack.damage * 2f);
    }

    @Override
    public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
        if (!(defender.level() instanceof ServerLevel level)) return;
        JJKConfig.Infinity cfg = JJKConfig.get().infinity;
        Vec3 center = defender.getBoundingBox().getCenter();
        Vec3 from = attack.origin.subtract(center);
        Vec3 dir = from.lengthSqr() < 1e-4 ? defender.getLookAngle() : from.normalize();
        // The ripple appears where the attack stopped: at the edge of Infinity, between attacker and defender.
        Vec3 at = center.add(dir.scale(defender.getBbWidth() / 2 + cfg.stopDistance));
        at = new Vec3(at.x, Math.max(defender.getY() + 0.3, Math.min(defender.getY() + defender.getBbHeight() - 0.2, at.y)), at.z);
        Fx.play(level, attack.has(AttackTag.MELEE) ? "infinity_ripple" : "infinity_ripple_small", at, dir,
                attack.has(AttackTag.HEAVY) || attack.has(AttackTag.TECHNIQUE) ? 1.6f : 1f, defender.getId());
        // A fist that meets Infinity just... stops. The attacker is left hanging in front of it.
        if (attack.has(AttackTag.MELEE) && attack.attacker instanceof LivingEntity a && a != defender) {
            Combat.state(a).apply(CombatStatus.INFINITY_SLOWED, 12);
            Vec3 v = a.getDeltaMovement();
            Motion.set(a, new Vec3(v.x * 0.1, Math.min(v.y, 0.05), v.z * 0.1));
        }
    }
}
