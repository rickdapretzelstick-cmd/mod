package dev.rick.jjk.core.combat;

import dev.rick.jjk.config.JJKConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/** Who is allowed to hit whom: teams, pvp, pets, spectators, creative players. */
public final class Targeting {
    private Targeting() {}

    public static boolean canTarget(LivingEntity attacker, Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        if (target == attacker || !target.isAlive() || target.isSpectator() || target.isRemoved()) return false;
        if (target instanceof ArmorStand stand && stand.isMarker()) return false;
        if (target instanceof Player p && p.getAbilities().invulnerable) return false;
        if (attacker.isAlliedTo(target)) return false;
        // A curse that needs perception can't harm a player who can't perceive it and gave it no reason (its blows,
        // shots, blasts and bursts all come through here).
        if (attacker instanceof net.minecraft.world.entity.Mob curse && !dev.rick.jjk.progression.CurseAggro.mayTarget(curse, living)) return false;
        if (target.isPassengerOfSameVehicle(attacker)) return false;
        if (attacker instanceof Player && target instanceof Player targetPlayer) {
            if (!JJKConfig.get().general.playerVsPlayer) return false;
            if (attacker instanceof ServerPlayer sp && !sp.canHarmPlayer(targetPlayer)) return false;
        }
        if (!JJKConfig.get().general.hitOwnPets && living instanceof OwnableEntity pet && pet.getOwnerReference() != null
                && pet.getOwnerReference().getUUID().equals(attacker.getUUID())) return false;
        return true;
    }
}
