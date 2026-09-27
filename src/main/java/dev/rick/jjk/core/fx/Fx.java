package dev.rick.jjk.core.fx;

import dev.rick.jjk.core.net.CameraPayload;
import dev.rick.jjk.core.net.FxPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side entry point for visual/audio feedback. Visual effects are sent as compact events and
 * spawned client-side (respecting each player's particle quality) instead of streaming particles.
 */
public final class Fx {
    public static final double DEFAULT_RANGE = 96;

    private Fx() {}

    public static void play(ServerLevel level, String id, Vec3 pos) {
        play(level, id, pos, Vec3.ZERO, 1f, -1);
    }

    public static void play(ServerLevel level, String id, Vec3 pos, Vec3 dir, float scale) {
        play(level, id, pos, dir, scale, -1);
    }

    public static void play(ServerLevel level, String id, Vec3 pos, Vec3 dir, float scale, int entityId) {
        FxPayload payload = new FxPayload(id, pos, dir, scale, entityId);
        double r = DEFAULT_RANGE * Math.max(1, scale / 2);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos) <= r * r) ServerPlayNetworking.send(p, payload);
        }
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound.value(), SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        if (at.level() instanceof ServerLevel sl) sound(sl, at.position(), sound, volume, pitch);
    }

    /** Screen shake for every player within {@code radius}, fading with distance. */
    public static void shake(ServerLevel level, Vec3 pos, double radius, float intensity, int duration) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(pos);
            if (d > radius) continue;
            float falloff = (float) (1 - d / radius);
            ServerPlayNetworking.send(p, new CameraPayload(CameraPayload.SHAKE, intensity * (0.35f + 0.65f * falloff), duration, 0));
        }
    }

    public static void camera(ServerPlayer player, int type, float intensity, int duration, int color) {
        ServerPlayNetworking.send(player, new CameraPayload(type, intensity, duration, color));
    }

    /** Colored screen flash for players within radius. */
    public static void flash(ServerLevel level, Vec3 pos, double radius, int argb, int duration) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos) <= radius * radius) ServerPlayNetworking.send(p, new CameraPayload(CameraPayload.FLASH, 1f, duration, argb));
        }
    }

    public static void toTrackers(Entity entity, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload, boolean includeSelf) {
        for (ServerPlayer p : PlayerLookup.tracking(entity)) ServerPlayNetworking.send(p, payload);
        if (includeSelf && entity instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, payload);
    }
}
