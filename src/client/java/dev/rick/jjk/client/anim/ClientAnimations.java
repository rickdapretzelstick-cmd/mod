package dev.rick.jjk.client.anim;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The client's animation entry point: gameplay asks for a clip by name ({@link #play}), the renderer asks for this
 * frame's pose ({@link #compute}). Hit reactions (hitstun, launch, knockdown, guard break...) are clips too, chosen
 * from the entity's combat statuses and played over everything at {@link Priority#RAGDOLL}.
 */
public final class ClientAnimations {
    private static final Map<Integer, AnimPlayer> PLAYERS = new HashMap<>();
    private static final Map<Integer, Integer> LAST_HITSTUN = new HashMap<>();
    /** The entity each player of clips belongs to (an id reused by a respawned or reloaded entity starts clean). */
    private static final Map<Integer, java.lang.ref.WeakReference<net.minecraft.world.entity.Entity>> OWNERS = new HashMap<>();
    /** Since when (ticks) a player's held pose has shown with nothing on the server behind it. */
    private static final Map<Integer, Float> STALE_SINCE = new HashMap<>();
    /**
     * A held or looping pose on a player whose server says they are doing nothing (no cast, no guard, no clash) is let
     * go after this long: the fallback for a release that never arrived. The server releases stale poses itself first.
     */
    public static final float STALE_TICKS = 60;
    /** Poses this client let go of itself (tests, debugging). */
    public static int recovered;
    private static final Set<String> MISSING = new HashSet<>();

    private ClientAnimations() {}

    /** Plays {@code name} on an entity ({@code ""} lets go of a held or looping pose). {@code now} is in ticks. */
    public static void play(int entityId, String name, float speed, float now) {
        if (name.isEmpty()) {
            AnimPlayer p = PLAYERS.get(entityId);
            if (p != null) p.release();
            return;
        }
        Clip clip = AnimLibrary.get(name);
        if (clip == null) {
            if (MISSING.add(name)) JJK.LOGGER.warn("[animations] no clip named {}", name);
            return;
        }
        AnimPlayer p = PLAYERS.computeIfAbsent(entityId, k -> new AnimPlayer());
        advance(entityId, p, now);
        p.play(clip, speed <= 0 ? 1f : speed);
    }

    public static void clear() {
        PLAYERS.clear();
        LAST_HITSTUN.clear();
        OWNERS.clear();
        STALE_SINCE.clear();
    }

    public static void forget(int entityId) {
        PLAYERS.remove(entityId);
        LAST_HITSTUN.remove(entityId);
        OWNERS.remove(entityId);
        STALE_SINCE.remove(entityId);
    }

    /**
     * Keeps a player's clips honest: a new entity under an old id (respawn, dimension change, reload) starts clean, and
     * a held pose left with nothing behind it is released (blending back to idle).
     */
    private static void recover(LivingEntity e, AnimPlayer p, float now) {
        int id = e.getId();
        var owner = OWNERS.get(id);
        if (owner == null || owner.get() != e) {
            if (owner != null && owner.get() != null) p.stopAll();
            OWNERS.put(id, new java.lang.ref.WeakReference<>(e));
            STALE_SINCE.remove(id);
        }
        if (!(e instanceof net.minecraft.world.entity.player.Player)) return;
        boolean held = false;
        for (AnimPlayer.Instance i : p.instances) if (!i.stopping && i.clip.endless()) held = true;
        if (!held || busy(e)) {
            STALE_SINCE.remove(id);
            return;
        }
        float since = STALE_SINCE.computeIfAbsent(id, k -> now);
        if (now - since >= STALE_TICKS) {
            p.release();
            STALE_SINCE.remove(id);
            recovered++;
        }
    }

    /** What the server has told this client the entity is doing that a held pose may belong to. */
    private static boolean busy(LivingEntity e) {
        if (!e.isAlive()) return false;
        if (dev.rick.jjk.client.ClientState.CASTS.containsKey(e.getId())) return true;
        CombatState s = Combat.stateOrNull(e);
        if (s != null && (s.has(CombatStatus.CLASHING) || s.isGuarding())) return true;
        for (var v : dev.rick.jjk.client.clash.BeamClashClient.views()) {
            if (v.phase != dev.rick.jjk.client.clash.BeamClashClient.ENDED && (v.aId == e.getId() || v.bId == e.getId())) return true;
        }
        return false;
    }

    @Nullable
    public static AnimPlayer player(int entityId) {
        return PLAYERS.get(entityId);
    }

    /** Ticks since {@code name} started on this entity, or -1 if it isn't playing. */
    public static float elapsed(int entityId, String name, float now) {
        AnimPlayer p = PLAYERS.get(entityId);
        if (p == null) return -1;
        advance(entityId, p, now);
        for (AnimPlayer.Instance i : p.instances) if (!i.stopping && i.clip.name.equals(name)) return i.time / 50f;
        return -1;
    }

    @Nullable
    public static String current(int entityId) {
        AnimPlayer p = PLAYERS.get(entityId);
        AnimPlayer.Instance top = p == null ? null : p.top();
        return top == null ? null : top.clip.name;
    }

    /** This frame's pose for an entity at render time {@code now} (ticks + partial), or null when nothing is playing. */
    @Nullable
    public static PoseFrame compute(LivingEntity e, float now) {
        int id = e.getId();
        String reaction = reaction(e);
        AnimPlayer p = PLAYERS.get(id);
        if (p == null) {
            if (reaction == null) return null;
            p = new AnimPlayer();
            PLAYERS.put(id, p);
        }
        CombatState s = Combat.stateOrNull(e);
        int hitstun = s == null ? 0 : s.get(CombatStatus.HITSTUN);
        // A fresh hit restarts the flinch.
        boolean newHit = hitstun > LAST_HITSTUN.getOrDefault(id, 0);
        LAST_HITSTUN.put(id, hitstun);
        advance(id, p, now);
        recover(e, p, now);
        p.setReaction(reaction, newHit && reaction != null && reaction.startsWith("reaction_hit"));
        if (p.idle()) {
            PLAYERS.remove(id);
            STALE_SINCE.remove(id);
            return null;
        }
        PoseFrame f = p.sample(new PoseFrame());
        return f.any() ? f : null;
    }

    /**
     * This frame's pose for any entity (a model like Rika, which has no hit reactions of its own), or null when nothing
     * is playing on it.
     */
    @Nullable
    public static PoseFrame computeModel(net.minecraft.world.entity.Entity e, float now) {
        AnimPlayer p = PLAYERS.get(e.getId());
        if (p == null) return null;
        advance(e.getId(), p, now);
        if (p.idle()) {
            PLAYERS.remove(e.getId());
            return null;
        }
        PoseFrame f = p.sample(new PoseFrame());
        return f.any() ? f : null;
    }

    private static void advance(int id, AnimPlayer p, float now) {
        float ms = now * 50f;
        if (Float.isNaN(p.lastNow)) p.lastNow = ms;
        // Only forward: callers asking about an earlier moment of the same frame see the current pose.
        float dt = Math.min(1000f, Math.max(0, ms - p.lastNow));
        p.lastNow = Math.max(p.lastNow, ms);
        if (AnimDebug.affects(id)) {
            float step = AnimDebug.takeStep(id, p);
            if (step != 0) p.scrub(step);
            dt = AnimDebug.paused ? 0 : dt * AnimDebug.speed;
        }
        if (dt > 0) p.advance(dt);
    }

    @Nullable
    private static String reaction(LivingEntity e) {
        CombatState s = Combat.stateOrNull(e);
        if (s == null) return null;
        if (s.isDowned()) return "reaction_knockdown";
        if (s.has(CombatStatus.OVERLOAD)) return "reaction_overload";
        if (s.has(CombatStatus.GUARD_BROKEN)) return "reaction_guard_broken";
        if (s.has(CombatStatus.PULLED)) return "reaction_pulled";
        if (s.has(CombatStatus.HITSTUN)) return s.has(CombatStatus.LAUNCHED) ? "reaction_hit_launched" : "reaction_hitstun";
        return null;
    }
}
