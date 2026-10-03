package dev.rick.jjk.core.clash;

import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Set;

/**
 * What every clash shares, domain or beam: the lock that holds its duellists (the {@link CombatStatus#CLASHING} status,
 * re-applied every tick for a few ticks, so a clash that vanishes for any reason lets go of them on its own within
 * three ticks), who still counts as playing, who gets to watch, and turning people to face each other.
 */
public final class ClashCommon {
    /** How far away players still see (and hear) a clash. */
    public static final double VIEW_RANGE = 160;

    private ClashCommon() {}

    /** Holds them for the next few ticks (renewed each tick while the clash lasts). */
    public static void hold(LivingEntity e) {
        Statuses.apply(e, CombatStatus.CLASHING, 3);
    }

    public static void release(LivingEntity e) {
        Statuses.remove(e, CombatStatus.CLASHING);
    }

    /** In some clash already (or in its lock). */
    public static boolean clashing(LivingEntity e) {
        return Combat.state(e).has(CombatStatus.CLASHING);
    }

    /** Can't go on: dead, gone, left the game, or turned spectator. */
    public static boolean forfeit(LivingEntity e, ServerLevel level) {
        return !e.isAlive() || e.isRemoved() || e.level() != level || e.isSpectator()
                || e instanceof ServerPlayer sp && (sp.hasDisconnected() || sp.isSpectator());
    }

    /** Sends {@code payload} to everyone taking part and everyone near enough to watch. */
    public static void broadcast(ServerLevel level, Vec3 center, Collection<? extends LivingEntity> participants, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (participants.contains(player) || player.position().distanceTo(center) < VIEW_RANGE) ServerPlayNetworking.send(player, payload);
        }
    }

    /** Turns {@code e} to look at {@code point} (for players, their own camera too). */
    public static void face(LivingEntity e, Vec3 point) {
        Vec3 d = point.subtract(e.getEyePosition());
        if (d.lengthSqr() < 1e-6) return;
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        pitch = Mth.clamp(pitch, -40f, 40f);
        if (e instanceof ServerPlayer sp && sp.level() instanceof ServerLevel level) {
            sp.teleportTo(level, sp.getX(), sp.getY(), sp.getZ(), Set.of(), yaw, pitch, false);
        } else {
            e.setYRot(yaw);
            e.setXRot(pitch);
        }
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
    }
}
