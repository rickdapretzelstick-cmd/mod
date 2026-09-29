package dev.rick.jjk.gojo;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gojo's short-lived combat memory, keyed to the living entity (lost on death): what the Special key can follow up
 * right now (Face Grater after Rapid Punches).
 */
public final class GojoState {
    private static final Map<LivingEntity, GojoState> STATES = new WeakHashMap<>();

    /** Rapid Punches just landed on this target: Limitless becomes Face Grater until {@link #faceGraterUntil}. */
    @Nullable public LivingEntity punched;
    public long faceGraterUntil = Long.MIN_VALUE;
    /** The Rapid Punches finisher's Black Flash landed: no Face Grater after it. */
    public boolean punchesKilled;

    private GojoState() {}

    public static GojoState of(LivingEntity e) {
        return STATES.computeIfAbsent(e, k -> new GojoState());
    }

    public static void clear(LivingEntity e) {
        STATES.remove(e);
    }

    /** The Rapid Punches victim Face Grater would take, or null. */
    @Nullable
    public LivingEntity faceGraterTarget(long now) {
        if (punched == null || !punched.isAlive() || now > faceGraterUntil || punchesKilled) return null;
        return punched;
    }
}
