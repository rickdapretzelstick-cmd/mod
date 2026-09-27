package dev.rick.jjk.core.ability.common;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Physical guard. Frontal melee is blocked (guard wears down per hit), techniques are halved,
 * guard-break attacks shatter it, and blocking in the first few ticks is a parry that stuns the attacker.
 */
public final class GuardDefense implements DefenseLayer {
    @Override
    public String id() {
        return "guard";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean isActive(LivingEntity defender) {
        return Combat.isGuarding(defender);
    }

    @Override
    public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
        if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.UNBLOCKABLE) || attack.has(AttackTag.ENVIRONMENTAL)) return DefenseResult.PASS;
        Vec3 from = attack.directionFromTarget();
        Vec3 look = defender.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0, look.z);
        if (from.lengthSqr() > 1e-4 && flatLook.lengthSqr() > 1e-4 && from.normalize().dot(flatLook.normalize()) < -0.1) {
            return DefenseResult.PASS; // hit from behind
        }
        JJKConfig.Guard cfg = JJKConfig.get().guard;
        CombatState state = Combat.state(defender);
        if (attack.has(AttackTag.GUARD_BREAK)) return new DefenseResult(DefenseResult.Kind.BREAK, 1f, "guard_break");
        long now = defender.level().getGameTime();
        if (attack.has(AttackTag.MELEE) && now - state.guardStartTime() <= cfg.perfectBlockWindow) {
            return new DefenseResult(DefenseResult.Kind.PARRY, 0f, "parry");
        }
        int wear = attack.hit != null ? attack.hit.guardDamage : 1;
        if (!attack.has(AttackTag.MELEE)) wear = 2;
        if (state.guardHitsTaken() + wear >= cfg.maxGuardHits) return new DefenseResult(DefenseResult.Kind.BREAK, 1f, "guard_worn");
        return DefenseResult.block(attack.has(AttackTag.MELEE) ? 0f : cfg.blockedTechniqueDamageScale);
    }

    @Override
    public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
        if (!(defender.level() instanceof ServerLevel level)) return;
        JJKConfig.Guard cfg = JJKConfig.get().guard;
        CombatState state = Combat.state(defender);
        Vec3 fxPos = defender.getEyePosition().add(defender.getLookAngle().scale(0.6)).subtract(0, 0.4, 0);
        switch (result.kind()) {
            case BLOCK -> {
                state.addGuardHit();
                if (attack.hit != null && attack.hit.guardDamage > 1) for (int i = 1; i < attack.hit.guardDamage; i++) state.addGuardHit();
                Fx.play(level, "block", fxPos, defender.getLookAngle(), 1f, defender.getId());
            }
            case PARRY -> {
                Fx.play(level, "parry", fxPos, defender.getLookAngle(), 1f, defender.getId());
                if (attack.attacker instanceof LivingEntity a) {
                    Statuses.apply(a, CombatStatus.GUARD_BROKEN, cfg.parryStun);
                    Vec3 push = a.position().subtract(defender.position());
                    Motion.set(a, new Vec3(push.x, 0, push.z).normalize().scale(0.6).add(0, 0.15, 0));
                }
                state.resetGuard();
            }
            case BREAK -> {
                state.stopGuard();
                state.resetGuard();
                Statuses.apply(defender, CombatStatus.GUARD_BROKEN, cfg.guardBreakStun);
                Fx.play(level, "guard_break", fxPos, defender.getLookAngle(), 1f, defender.getId());
                Fx.shake(level, defender.position(), 8, 0.6f, 8);
            }
            default -> {}
        }
    }
}
