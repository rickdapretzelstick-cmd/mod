package dev.rick.jjk.core.character;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.registry.ModDamageTypes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * How the Awakening meter fills: fighting fills it. Landing hits and dealing damage fill it most (combo hits on
 * launched/downed/guard-broken targets count extra), taking damage, blocking and especially parrying fill it too.
 */
public final class AwakeningGain {
    private AwakeningGain() {}

    public static void init() {
        CombatEvents.HIT_RESOLVED.add(AwakeningGain::onHit);
        // Damage from outside this mod's hit system (mobs, arrows, falling...) still builds the victim's meter.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!ModDamageTypes.isOurs(source) && taken > 0) gain(entity, taken * JJKConfig.get().awakening.gainPerDamageTaken);
        });
    }

    private static void onHit(HitResult r) {
        JJKConfig.Awakening cfg = JJKConfig.get().awakening;
        if (r.hit().has(dev.rick.jjk.core.combat.AttackTag.NO_METER)) return;
        LivingEntity attacker = r.hit().attacker;
        LivingEntity target = r.target();
        switch (r.outcome()) {
            case HIT, GUARD_BROKEN -> {
                float bonus = Combat.has(target, CombatStatus.LAUNCHED) || Combat.isDowned(target) || Combat.has(target, CombatStatus.GUARD_BROKEN)
                        ? cfg.comboBonusMultiplier : 1f;
                gain(attacker, (cfg.gainPerHitLanded + r.damageDealt() * cfg.gainPerDamageDealt) * bonus);
                gain(target, r.damageDealt() * cfg.gainPerDamageTaken);
            }
            case BLOCKED -> gain(target, cfg.gainPerBlock);
            case PARRIED -> gain(target, cfg.gainPerParry);
            default -> {}
        }
    }

    public static void gain(LivingEntity e, float amount) {
        AbilityCaster c = Casters.active(e);
        if (c != null) c.gainAwakening(amount);
    }
}
