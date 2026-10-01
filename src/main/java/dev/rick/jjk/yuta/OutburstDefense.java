package dev.rick.jjk.yuta;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.entity.TechniqueEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/** Outburst's first quarter second: a melee hit is parried, a bullet sent back; anything else goes through. */
public final class OutburstDefense implements DefenseLayer {
    @Override
    public String id() {
        return OutburstAbility.ID;
    }

    @Override
    public int priority() {
        return 64;
    }

    @Override
    public boolean isActive(LivingEntity defender) {
        return OutburstAbility.parrying(defender);
    }

    @Override
    public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
        if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.ENVIRONMENTAL)) return DefenseResult.PASS;
        if (bullet(attack)) return DefenseResult.negate("outburst_reflect");
        if (attack.has(AttackTag.MELEE) || attack.vanillaSource != null && attack.direct == attack.attacker) return DefenseResult.negate("outburst_parry");
        return DefenseResult.PASS;
    }

    private static boolean bullet(IncomingAttack attack) {
        return attack.has(AttackTag.PROJECTILE) && !attack.has(AttackTag.MELEE) || attack.direct instanceof Projectile
                || attack.direct instanceof TechniqueEntity && !attack.has(AttackTag.MELEE);
    }

    @Override
    public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
        OutburstAbility.parried(defender, attack.attacker, attack.direct, "outburst_reflect".equals(result.reason()));
    }
}
