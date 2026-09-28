package dev.rick.jjk.client.anim;

import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Tracks which animation each entity is playing and composes the final pose override per frame. */
public final class ClientAnimations {
    private static final Map<Integer, Playing> PLAYING = new HashMap<>();
    private static final Map<Integer, Float> LIE = new HashMap<>();

    private record Playing(AnimDef def, float startTime, float speed) {}

    private ClientAnimations() {}

    public static void play(int entityId, String name, float speed, float now) {
        if (name.isEmpty()) {
            Playing p = PLAYING.get(entityId);
            // Stopping a held pose lets it blend out from where it is.
            if (p != null && p.def.hold) PLAYING.put(entityId, new Playing(p.def, now - p.def.duration - 1000, 1f));
            return;
        }
        AnimDef def = PoseLibrary.get(name);
        if (def != null) PLAYING.put(entityId, new Playing(def, now, speed <= 0 ? 1f : speed));
    }

    public static void clear() {
        PLAYING.clear();
        LIE.clear();
    }

    /** Ticks since {@code name} started on this entity, or -1 if it isn't playing. */
    public static float elapsed(int entityId, String name, float now) {
        Playing p = PLAYING.get(entityId);
        return p != null && p.def.name.equals(name) ? (now - p.startTime) * p.speed : -1;
    }

    @Nullable
    public static String current(int entityId) {
        Playing p = PLAYING.get(entityId);
        return p == null ? null : p.def.name;
    }

    /** Builds the pose for an entity at render time {@code now} (ticks + partial). Null when nothing overrides it. */
    @Nullable
    public static PoseFrame compute(LivingEntity e, float now) {
        CombatState s = Combat.stateOrNull(e);
        Playing p = PLAYING.get(e.getId());
        PoseFrame f = null;

        if (s != null) {
            float jitter = (float) Math.sin(now * 2.7 + e.getId()) * 4f;
            if (s.has(CombatStatus.OVERLOAD)) {
                f = frame(f);
                // Rigid, trembling, eyes up: the mind is somewhere else.
                f.blend(Part.HEAD, -28 + jitter * 0.5f, jitter, 0, 1);
                f.blend(Part.RIGHT_ARM, 8 + jitter, 0, 12, 1);
                f.blend(Part.LEFT_ARM, 8 - jitter, 0, -12, 1);
                f.blend(Part.BODY, -6, 0, jitter * 0.3f, 1);
            } else if (s.has(CombatStatus.GUARD_BROKEN)) {
                f = frame(f);
                f.blend(Part.BODY, -16, 0, 0, 1);
                f.blend(Part.HEAD, -20, 0, 0, 1);
                f.blend(Part.RIGHT_ARM, -35, 0, 60, 1);
                f.blend(Part.LEFT_ARM, -35, 0, -60, 1);
            } else if (s.has(CombatStatus.PULLED)) {
                f = frame(f);
                f.blend(Part.RIGHT_ARM, -150, 0, 35 + jitter, 0.9f);
                f.blend(Part.LEFT_ARM, -150, 0, -35 - jitter, 0.9f);
                f.blend(Part.RIGHT_LEG, -20, 0, 10, 0.8f);
                f.blend(Part.LEFT_LEG, 15, 0, -10, 0.8f);
            } else if (s.has(CombatStatus.HITSTUN)) {
                f = frame(f);
                float fl = Math.min(1f, s.get(CombatStatus.HITSTUN) / 6f);
                f.blend(Part.HEAD, -18 * fl, jitter, 0, fl);
                f.blend(Part.BODY, -12 * fl, 0, jitter * 0.5f, fl);
                f.blend(Part.RIGHT_ARM, 20, 0, 25, fl);
                f.blend(Part.LEFT_ARM, 20, 0, -25, fl);
                if (s.has(CombatStatus.LAUNCHED)) {
                    f.blend(Part.RIGHT_LEG, -30, 0, 8, fl);
                    f.blend(Part.LEFT_LEG, 10, 0, -8, fl);
                }
            }
        }

        // Knockdown: ease into lying flat, ease back up.
        float targetLie = s != null && s.isDowned() ? 1f : 0f;
        float lie = LIE.getOrDefault(e.getId(), 0f);
        lie = Mth.lerp(0.25f, lie, targetLie);
        if (lie < 0.01f && targetLie == 0) LIE.remove(e.getId());
        else LIE.put(e.getId(), lie);
        if (lie > 0.01f) {
            f = frame(f);
            f.lieDown = lie;
            f.blend(Part.RIGHT_ARM, -10, 0, 40, lie);
            f.blend(Part.LEFT_ARM, -10, 0, -40, lie);
        }

        if (p != null) {
            float t = (now - p.startTime) * p.speed;
            AnimDef def = p.def;
            float w;
            if (def.hold && t > def.duration + 1000) {
                // A stopped held pose blends out over blendOut ticks.
                float out = t - def.duration - 1000;
                w = out < def.blendOut ? 1 - out / def.blendOut : 0;
            } else if (t < 0) {
                w = 0;
            } else if (t < def.blendIn) {
                w = t / def.blendIn;
            } else if (def.hold || t < def.duration) {
                w = 1;
            } else if (t < def.duration + def.blendOut) {
                w = 1 - (t - def.duration) / def.blendOut;
            } else {
                w = 0;
            }
            if (w <= 0 && t > def.duration) {
                PLAYING.remove(e.getId());
            } else if (w > 0) {
                f = frame(f);
                float sampleT = Math.min(t, def.duration);
                for (Part part : Part.values()) {
                    float[] r = def.sample(part, sampleT);
                    if (r != null) f.blend(part, r[0], r[1], r[2], w);
                }
            }
        }
        return f != null && f.any() ? f : null;
    }

    private static PoseFrame frame(@Nullable PoseFrame f) {
        return f != null ? f : new PoseFrame();
    }

    public static void forget(int entityId) {
        PLAYING.remove(entityId);
        LIE.remove(entityId);
    }
}
