package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.HakariDoorEntity;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Door Guard as a defense layer. It sits outside the physical guard: anything from the front is stopped by the door
 * (melee, techniques, projectiles, explosions), each stop costing a little cursed energy; attacks from behind, sure-hits
 * and unblockables go around it. A melee hit caught in the counter window swings the door open into the attacker.
 */
public final class DoorGuardDefense implements DefenseLayer {
    @Override
    public String id() {
        return DoorGuardAbility.ID;
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public boolean isActive(LivingEntity defender) {
        return DoorGuardAbility.isRaised(defender);
    }

    @Override
    public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
        if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.UNBLOCKABLE) || attack.has(AttackTag.ENVIRONMENTAL)) return DefenseResult.PASS;
        Vec3 from = attack.directionFromTarget();
        Vec3 look = HakariCombat.flat(defender);
        if (from.lengthSqr() > 1e-4 && new Vec3(from.x, 0, from.z).normalize().dot(look) < 0.1) return DefenseResult.PASS; // side or back
        long raised = DoorGuardAbility.RAISED.getOrDefault(defender, 0L);
        if (attack.has(AttackTag.MELEE) && defender.level().getGameTime() - raised <= JJKConfig.get().hakari.doorGuardCounterWindow) {
            return new DefenseResult(DefenseResult.Kind.PARRY, 0f, "door_counter");
        }
        return DefenseResult.negate("door_guard");
    }

    @Override
    public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
        if (!(defender.level() instanceof ServerLevel level)) return;
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        Vec3 at = DoorGuardAbility.doorSpot(defender).add(0, 1.2, 0);
        var caster = Casters.getOrNull(defender);
        if (result.kind() == DefenseResult.Kind.PARRY) {
            HakariDoorEntity door = DoorGuardAbility.DOORS.get(defender);
            if (door != null) door.setOpen(0.01f);
            Fx.play(level, "door_guard_counter", at, HakariCombat.flat(defender), 1f, defender.getId());
            Fx.shake(level, at, 12, 0.6f, 8);
            if (attack.attacker instanceof LivingEntity a) {
                Statuses.apply(a, CombatStatus.GUARD_BROKEN, cfg.doorGuardCounterStun);
                Vec3 push = a.position().subtract(defender.position());
                Motion.set(a, new Vec3(push.x, 0, push.z).normalize().scale(1.1).add(0, 0.35, 0));
            }
        } else {
            Fx.play(level, "door_guard_block", at, HakariCombat.flat(defender), 1f, defender.getId());
            if (caster != null) caster.drain(cfg.doorGuardBlockCost);
        }
    }
}
