package dev.rick.jjk.core.clash;

import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.BeamCounterPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every beam clash. The server alone decides them: when an ultimate beam's path is locked (its caster committed),
 * the one opponent who could answer it (the closest that is in its path, can act, and has their Ultimate ready) gets a
 * short window in which their Ultimate key fires their own beam back at it. When that answer fires the two meet and a
 * {@link BeamClashSession} decides the rest. Two such beams fired at each other without a counter clash too.
 *
 * <p>A beam takes part in one clash at most; a sorcerer in any clash (domain or beam) can't join another; one window is
 * open per beam.
 */
public final class BeamClashManager {
    /** How far an answer can come from, and how near the beam's path it must stand. */
    public static final double RANGE = 64, PATH_WIDTH = 7;
    /** The window stays open this long past the beam firing (a moment to react to it actually coming). */
    public static final int GRACE = 5;

    private static final Map<Integer, BeamClashSession> SESSIONS = new LinkedHashMap<>();
    /** Open windows: whoever can answer → the beam they would answer, and until when. */
    private static final Map<UUID, Window> WINDOWS = new LinkedHashMap<>();
    /** Beams firing right now that aren't in a clash (two fired at each other meet). */
    private static final List<ClashBeam> LIVE = new ArrayList<>();
    private static int nextId = 1;

    private record Window(ClashBeam beam, LivingEntity attacker, long expires) {}

    private BeamClashManager() {}

    // --- From the beams ---

    /**
     * {@code beam}'s path is locked and it will fire at {@code fireTick} (game time): offer the answer to whoever
     * could give one.
     */
    public static void threaten(ClashBeam beam, long fireTick) {
        LivingEntity attacker = beam.beamOwner();
        if (!(attacker.level() instanceof ServerLevel level) || sessionOf(attacker) != null) return;
        LivingEntity who = pickAnswer(beam, level);
        if (who == null) return;
        long expires = Math.max(fireTick, level.getGameTime()) + GRACE;
        WINDOWS.put(who.getUUID(), new Window(beam, attacker, expires));
        BeamCounters.Counter c = BeamCounters.of(who);
        if (who instanceof ServerPlayer sp && c != null) {
            ServerPlayNetworking.send(sp, new BeamCounterPayload(attacker.getId(), (int) (expires - level.getGameTime()),
                    beam.beamKind().equals("tlb") ? "TRUE LOVE BEAM" : "EVERY LAST DROP", c.name()));
        }
        Fx.play(level, "bclash_threat", who.getEyePosition(), beam.beamDir(), 1f, who.getId());
    }

    /** {@code beam} has fired. */
    public static void fired(ClashBeam beam) {
        LivingEntity owner = beam.beamOwner();
        // The answer to a clash already under way?
        BeamClashSession s = sessionOf(owner);
        if (s != null) {
            s.beamOut(beam);
            return;
        }
        // The attacker's own clash (answered while it was still charging)?
        for (BeamClashSession other : SESSIONS.values()) {
            if (other.phase == BeamClashSession.Phase.COUNTER && other.sides[0].beam == beam) {
                other.beamOut(beam);
                return;
            }
        }
        // Two beams fired straight at each other: they clash with no counter needed.
        for (ClashBeam o : List.copyOf(LIVE)) {
            if (!o.beamLive() || o.beamOwner() == owner) {
                if (!o.beamLive()) LIVE.remove(o);
                continue;
            }
            if (o.beamKind().equals(beam.beamKind()) || !headOn(beam, o)) continue;
            LIVE.remove(o);
            BeamClashSession ns = create(o.beamOwner(), o, owner, beam, beam.beamOrigin());
            ns.beamOut(o);
            ns.beamOut(beam);
            return;
        }
        LIVE.add(beam);
        // And whoever was offered the answer still has a moment after it actually fires.
    }

    /** A beam ended some other way (its caster stopped, died, left): its clash, if any, can't go on. */
    public static void beamGone(ClashBeam beam) {
        LIVE.remove(beam);
        for (BeamClashSession s : List.copyOf(SESSIONS.values())) {
            if (s.sideOf(beam) != null) s.beamGone(beam);
        }
        WINDOWS.values().removeIf(w -> w.beam == beam);
    }

    // --- The answer ---

    /**
     * The Ultimate key, pressed: if a window is open for this sorcerer, it is their answer (the beam fires back at once)
     * and true is returned; otherwise false, and the key does what it normally does.
     */
    public static boolean tryCounter(AbilityCaster caster) {
        LivingEntity user = caster.owner;
        Window w = WINDOWS.get(user.getUUID());
        if (w == null || !(user.level() instanceof ServerLevel level)) return false;
        if (level.getGameTime() > w.expires || !valid(w.beam, user, level)) {
            WINDOWS.remove(user.getUUID());
            return false;
        }
        BeamCounters.Counter counter = BeamCounters.of(user);
        if (counter == null || !counter.answers().equals(w.beam.beamKind()) || !counter.ready(caster)) return false;
        WINDOWS.remove(user.getUUID());
        // Aim straight at the incoming beam's source; the session lines both up exactly once both are out.
        Vec3 at = w.beam.beamOrigin();
        Vec3 origin = counter.origin(user, at);
        BeamClashSession s = create(w.attacker, w.beam, user, null, origin);
        ClashCommon.face(user, at);
        if (!counter.fire(caster, at)) {
            SESSIONS.remove(s.id);
            return false;
        }
        WINDOWS.values().removeIf(x -> x.beam == w.beam);
        Fx.play(level, "bclash_answer", user.getEyePosition(), at.subtract(user.getEyePosition()), 1f, user.getId());
        if (user instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new BeamCounterPayload(w.attacker.getId(), 0, "", ""));
        // If the attacker had already fired, it is now held short of the answer as it comes.
        if (w.beam.beamLive()) {
            LIVE.remove(w.beam);
            s.beamOut(w.beam);
        }
        return true;
    }

    /** Whether {@code who} can answer {@code beam} right now. */
    static boolean valid(ClashBeam beam, LivingEntity who, ServerLevel level) {
        return why(beam, who, level) == null;
    }

    /** Why {@code who} can't answer {@code beam} right now, or null if they can. */
    @Nullable
    static String why(ClashBeam beam, LivingEntity who, ServerLevel level) {
        LivingEntity attacker = beam.beamOwner();
        if (who == attacker || !who.isAlive() || who.isSpectator() || attacker.isRemoved() || !attacker.isAlive()) return "not alive";
        if (who.level() != level || attacker.level() != level) return "another level";
        if (ClashCommon.clashing(who) || ClashCommon.clashing(attacker) || sessionOf(who) != null) return "already clashing";
        if (Combat.actionsLocked(who)) return "can't act";
        // In the line of it: ahead of its source, within reach, near its path.
        Vec3 o = beam.beamOrigin(), d = beam.beamDir();
        Vec3 rel = who.getBoundingBox().getCenter().subtract(o);
        double along = rel.dot(d);
        if (along < 3 || along > Math.min(RANGE, beam.beamRange() + 12)) return "out of reach (" + Math.round(along) + ")";
        if (rel.subtract(d.scale(along)).length() > PATH_WIDTH) return "off its path";
        // Nothing solid between them (a wall takes it; the answer must see what it answers).
        var clip = level.clip(new ClipContext(who.getEyePosition(), o, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        return clip.getType() == HitResult.Type.MISS || clip.getLocation().distanceTo(o) < 2.5 ? null : "no line of sight";
    }

    /** Why the last threatened beam found no answer (tests, debugging). */
    public static String lastMiss = "";

    /** The one sorcerer who may answer: the closest valid, with an answer of the right kind ready (ties by id). */
    @Nullable
    static LivingEntity pickAnswer(ClashBeam beam, ServerLevel level) {
        Vec3 o = beam.beamOrigin();
        List<LivingEntity> found = new ArrayList<>();
        StringBuilder miss = new StringBuilder();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(RANGE), Entity::isAlive)) {
            BeamCounters.Counter c = BeamCounters.of(e);
            AbilityCaster caster = Casters.getOrNull(e);
            if (c == null || caster == null || !c.answers().equals(beam.beamKind())) continue;
            if (!c.ready(caster)) {
                miss.append(e.getId()).append(": answer not ready; ");
                continue;
            }
            String why = why(beam, e, level);
            if (why == null) found.add(e);
            else miss.append(e.getId()).append(": ").append(why).append("; ");
        }
        lastMiss = miss.toString();
        found.sort(Comparator.<LivingEntity>comparingDouble(e -> e.distanceToSqr(o)).thenComparingInt(Entity::getId));
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Aimed at each other: each source near the other's path, pointing roughly at it. */
    static boolean headOn(ClashBeam a, ClashBeam b) {
        Vec3 ab = b.beamOrigin().subtract(a.beamOrigin());
        double dist = ab.length();
        if (dist < 4 || dist > Math.min(a.beamRange(), b.beamRange()) + 6) return false;
        Vec3 n = ab.scale(1 / dist);
        return a.beamDir().dot(n) > 0.8 && b.beamDir().dot(n.reverse()) > 0.8;
    }

    private static BeamClashSession create(LivingEntity a, ClashBeam aBeam, LivingEntity b, @Nullable ClashBeam bBeam, Vec3 bOrigin) {
        BeamClashSession s = new BeamClashSession(nextId++, (ServerLevel) a.level(), a, aBeam, b, bBeam, bOrigin);
        SESSIONS.put(s.id, s);
        s.sync();
        return s;
    }

    // --- Running ---

    public static void tick(ServerLevel level) {
        if (!WINDOWS.isEmpty()) {
            long now = level.getGameTime();
            WINDOWS.entrySet().removeIf(e -> e.getValue().attacker.level() == level && now > e.getValue().expires);
        }
        LIVE.removeIf(b -> b.beamLevel() == level && !b.beamLive());
        if (SESSIONS.isEmpty()) return;
        for (BeamClashSession s : List.copyOf(SESSIONS.values())) {
            if (s.level != level) continue;
            s.tick();
            if (s.phase == BeamClashSession.Phase.ENDED) SESSIONS.remove(s.id);
        }
    }

    public static void input(ServerPlayer player, int session, int check, int elapsedMs) {
        BeamClashSession s = SESSIONS.get(session);
        if (s != null && s.level == player.level()) s.input(player, check, elapsedMs);
    }

    @Nullable
    public static BeamClashSession sessionOf(LivingEntity e) {
        for (BeamClashSession s : SESSIONS.values()) if (s.phase != BeamClashSession.Phase.ENDED && s.sideOf(e) != null) return s;
        return null;
    }

    /** Whether a reaction window is open for {@code e} (tests, HUD). */
    public static boolean windowOpen(LivingEntity e) {
        return WINDOWS.containsKey(e.getUUID());
    }

    public static List<BeamClashSession> sessions() {
        return List.copyOf(SESSIONS.values());
    }

    /** Everything off: the server is stopping, or a test resets. Every beam lets go and every duellist is released. */
    public static void clear() {
        for (BeamClashSession s : List.copyOf(SESSIONS.values())) s.cancel(false);
        SESSIONS.clear();
        WINDOWS.clear();
        LIVE.clear();
    }
}
