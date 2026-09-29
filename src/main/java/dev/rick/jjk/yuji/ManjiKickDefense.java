package dev.rick.jjk.yuji;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/** Manji Kick's window: a melee hit is countered, a bullet dodged (both harmlessly); anything else goes through. */
public final class ManjiKickDefense implements DefenseLayer {
    @Override
    public String id() {
        return ManjiKickAbility.ID;
    }

    @Override
    public int priority() {
        return 65;
    }

    @Override
    public boolean isActive(LivingEntity defender) {
        return ManjiKickAbility.raised(defender);
    }

    @Override
    public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
        if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.ENVIRONMENTAL)) return DefenseResult.PASS;
        boolean bullet = attack.has(AttackTag.PROJECTILE) && !attack.has(AttackTag.MELEE) || attack.direct instanceof Projectile;
        if (bullet) return DefenseResult.negate("manji_dodge");
        if (attack.has(AttackTag.MELEE) || attack.vanillaSource != null && attack.direct == attack.attacker) return DefenseResult.negate("manji_counter");
        return DefenseResult.PASS;
    }

    @Override
    public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
        LivingEntity from = attack.attacker instanceof LivingEntity l ? l : null;
        ManjiKickAbility.counter(defender, from, "manji_dodge".equals(result.reason()));
    }
}
