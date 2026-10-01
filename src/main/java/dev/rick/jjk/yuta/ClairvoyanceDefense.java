package dev.rick.jjk.yuta;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Clairvoyance's mark: whoever Yuta marked can't touch him while it lasts; he sees every attack coming and sidesteps. */
public final class ClairvoyanceDefense implements DefenseLayer {
    @Override
    public String id() {
        return "clairvoyance";
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public boolean isActive(LivingEntity defender) {
        YutaState s = YutaState.get(defender);
        return s != null && !s.marked.isEmpty();
    }

    @Override
    public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
        if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.ENVIRONMENTAL)) return DefenseResult.PASS;
        if (!(attack.attacker instanceof LivingEntity a)) return DefenseResult.PASS;
        YutaState s = YutaState.get(defender);
        Long until = s == null ? null : s.marked.get(a);
        if (until == null) return DefenseResult.PASS;
        if (defender.level().getGameTime() > until || !Combat.has(a, CombatStatus.CLAIRVOYANCE)) {
            s.marked.remove(a);
            return DefenseResult.PASS;
        }
        return DefenseResult.negate("clairvoyance_dodge");
    }

    @Override
    public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
        if (defender.level() instanceof ServerLevel sl) {
            Fx.play(sl, "evade", defender.position().add(0, defender.getBbHeight() / 2, 0), Vec3.ZERO, 1f, defender.getId());
        }
    }
}
