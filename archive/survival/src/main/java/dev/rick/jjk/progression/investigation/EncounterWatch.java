package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.EncounterPayload;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps every realm fight finishable. A fight (an {@link InvestigationState.Arena}: its own encounter id, its members,
 * its slot of the void) counts only the enemies it raised itself, never another fight's, a summon, or anything else in
 * the dimension, and nobody is ever left trapped in it:
 *
 * <ol>
 *   <li><b>Tracking</b>: an incident's curses are its UUID list. A curse that leaves without dying (discarded, moved to
 *   another dimension) is dropped from it at once; the list is also checked against the world every second, and a
 *   curse missing from ground whose entities are loaded for {@link JJKConfig.Encounters#staleChecks} checks in a row is
 *   dropped as a stale record (an unloaded curse is never taken for dead: the arena keeps a margin round it loaded). A
 *   held-back wave is brought in whenever nothing of the first is left, not only when one dies. The last record going
 *   completes the incident, once.</li>
 *   <li><b>Reveal</b>: a few curses left and no combat for a while: everyone in the fight sees how many remain, each
 *   outlined through walls, and where the nearest is.</li>
 *   <li><b>Recovery</b>: a revealed curse that has gone nowhere for a long while, isn't fighting anyone and can't path to
 *   anyone (or is somewhere it can't be: in a wall, in lava, off the arena) is brought back: the same curse, same
 *   health, set down on safe ground well away from every player and where one of them can see it, with a flash and a
 *   sound, and a moment before it may attack.</li>
 *   <li><b>Emergency Return</b>: if recovery keeps failing, or the fight still goes nowhere long after the reveal, a
 *   pale breach opens by each player in it. Using it and holding still, unhurt, for a few seconds takes that player home
 *   and abandons the fight for them: no completion rewards. Once open it stays open; the rest fight on, and the arena
 *   closes (the incident open to retry) when nobody is left.</li>
 * </ol>
 */
public final class EncounterWatch {
    /** An Emergency Return breach's target: {@code exit:<slot>}. */
    public static final String EXIT = "exit:";

    private EncounterWatch() {}

    /** One fight's working state. */
    public static final class Watch {
        long lastCombat, lastProgress, revealedAt = -1, emergencyAt = -1;
        boolean started;
        int remaining = -1, failures;
        final Map<UUID, Integer> missing = new HashMap<>();
        final Map<UUID, Track> tracks = new HashMap<>();
        final Map<UUID, Integer> recoveries = new HashMap<>();
        final Map<UUID, Channel> channels = new HashMap<>();
        final Set<UUID> told = new HashSet<>();
        /** Curses found somewhere they can't be at the last check (twice in a row and they are brought back). */
        final Set<UUID> suspect = new HashSet<>();

        boolean revealed, emergency;

        public boolean revealed() {
            return revealed;
        }

        public boolean emergency() {
            return emergency;
        }

        public int failures() {
            return failures;
        }

        public boolean channelling(UUID player) {
            return channels.containsKey(player);
        }
    }

    /** Where a curse was and since when (to tell a curse that has gone nowhere from one moving about). */
    static final class Track {
        Vec3 anchor;
        long since;

        Track(Vec3 anchor, long since) {
            this.anchor = anchor;
            this.since = since;
        }
    }

    /** A player channelling the Emergency Return: where they stood, when they began, and their health then. */
    record Channel(Vec3 at, long started, float health) {}

    private static JJKConfig.Encounters cfg() {
        return JJKConfig.get().encounters;
    }

    // --- Events ---

    /** The arena being closed right now (its leftovers going don't count as curses leaving the fight). */
    @Nullable private static InvestigationState.Arena closing;

    static void closing(MinecraftServer server, InvestigationState.Arena a, boolean on) {
        closing = on ? a : null;
        if (!on) {
            // Everyone who fought in it: their fight HUD clears.
            for (UUID id : a.members) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) ServerPlayNetworking.send(p, EncounterPayload.clear());
            }
        }
    }

    static void init() {
        // A curse that leaves without dying (discarded, sent to another dimension) is no longer part of its fight.
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!(entity instanceof CommonCurseEntity c) || c.incidentId().isEmpty() || level.getServer() == null) return;
            Entity.RemovalReason why = entity.getRemovalReason();
            if (why == null || !(why.shouldDestroy() || why == Entity.RemovalReason.CHANGED_DIMENSION)) return;
            resolve(level.getServer(), c.incidentId(), c.getUUID(), why.name().toLowerCase(java.util.Locale.ROOT));
        });
        // Combat between a fight's players and anything in it: what the reveal waits on.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken <= 0 && !blocked) return;
            Entity by = source.getEntity();
            ServerPlayer p = entity instanceof ServerPlayer sp ? sp : by instanceof ServerPlayer sp2 ? sp2 : null;
            Entity other = p == entity ? by : entity;
            if (p == null || other == null || other instanceof Player || !CursedRealms.inRealm(p) || p.level().getServer() == null) return;
            InvestigationState st = InvestigationState.get(p.level().getServer());
            InvestigationState.Arena a = CursedRealms.arenaAt(st, p.position());
            if (a != null) a.watch.lastCombat = p.level().getServer().overworld().getGameTime();
        });
    }

    /**
     * One of an incident's curses has left its fight without dying: its record goes, and the fight goes on without it
     * (or, if it was the last, is won). Nothing happens if it wasn't counted (a leftover, a duplicate event).
     */
    static void resolve(MinecraftServer server, String incident, UUID curse, String why) {
        InvestigationState st = InvestigationState.get(server);
        InvestigationState.Arena a = CursedRealms.arenaOf(st, incident);
        if (a != null && a == closing) return;
        Incident in = st.incidents.get(incident);
        if (in == null || in.state != Incident.State.ACTIVE || !in.curses.remove(curse)) return;
        st.markDirty();
        JJK.LOGGER.info("[encounters] incident {}: curse {} left the fight without dying ({}); its record is dropped", incident, curse, why);
        afterLoss(server, st, in, curse);
    }

    /** A curse's record is gone: the last completes the incident; otherwise a held-back wave may come in. */
    private static void afterLoss(MinecraftServer server, InvestigationState st, Incident in, @Nullable UUID lost) {
        // The contract's own objective decides (a designated curse or an anchor isn't just "the last record").
        Investigations.recordsChanged(server, in, st, lost);
    }

    // --- Every tick, for each running fight ---

    static void tick(MinecraftServer server, InvestigationState st, InvestigationState.Arena a, ServerLevel realm, CursedRealms.Layout layout,
                     @Nullable Incident in) {
        Watch w = a.watch;
        long now = server.overworld().getGameTime();
        if (!w.started) {
            w.started = true;
            w.lastCombat = now;
            w.lastProgress = now;
        }
        boolean incident = in != null && !a.incident.startsWith(CursedRealms.ROOM) && !a.incident.startsWith(CursedRealms.TRIAL);
        if (incident && now % Math.max(1, cfg().reconcileInterval) == 0) {
            reconcile(server, st, a, in, realm);
            if (in.state != Incident.State.ACTIVE) return;
        }
        List<UUID> tracked = tracked(server, st, a);
        List<Mob> live = live(realm, tracked);
        int remaining = tracked.size();
        if (w.remaining < 0 || remaining < w.remaining) {
            // Progress: one fewer. The reveal and the stuck checks start over (the Emergency Return, once open, stays).
            w.lastProgress = now;
            w.revealed = false;
            w.tracks.clear();
        }
        w.remaining = remaining;
        if (incident && now % 20 == 0) scale(a, live);
        if (a.inside.isEmpty()) {
            sync(server, a, w, live, false);
            return;
        }
        var c = cfg();
        long quiet = now - Math.max(w.lastCombat, w.lastProgress);
        if (!w.revealed() && remaining > 0 && (remaining <= c.revealMaxRemaining && quiet >= c.revealAfter || quiet >= 2L * c.revealAfter)) {
            w.revealedAt = now;
            w.revealed = true;
            JJK.LOGGER.info("[encounters] encounter {} ({}): {} enemies left and no combat for {} ticks: revealed", a.encounter, a.incident, remaining, quiet);
            tell(server, a, remaining == 1 ? "One curse remains. You can sense where it is." : remaining + " curses remain. You can sense where they are.",
                    ChatFormatting.LIGHT_PURPLE);
        }
        if (w.revealed() && now - w.revealedAt >= c.recoverAfter && now % 20 == 0) {
            for (Mob m : live) checkStuck(server, a, w, realm, layout, m, now);
        }
        // Anything out where it can't be (in a wall, in lava, off the arena) comes back whatever the stage.
        if (now % 20 == 10) {
            for (Mob m : live) {
                // Twice in a row (a leap grazing a wall for a moment is not stuck).
                if (!invalidSpot(realm, a, m)) {
                    w.suspect.remove(m.getUUID());
                } else if (!w.suspect.add(m.getUUID())) {
                    w.suspect.remove(m.getUUID());
                    relocate(server, a, w, realm, layout, m, "out of bounds or inside terrain");
                }
            }
        }
        if (!w.emergency() && (w.failures >= c.emergencyAfterFailures
                || w.revealed() && now - w.revealedAt >= c.recoverAfter && now - w.lastProgress >= c.emergencyAfter)) {
            w.emergencyAt = now;
            w.emergency = true;
            JJK.LOGGER.warn("[encounters] encounter {} ({}) has stalled ({} enemies left, {} failed recoveries, {} ticks without progress): Emergency Return open",
                    a.encounter, a.incident, remaining, w.failures, now - w.lastProgress);
            tell(server, a, "The fight has stalled. An Emergency Return has opened beside you: use it and hold still to leave. "
                    + "Leaving abandons the fight, and its rewards.", ChatFormatting.GOLD);
        }
        if (w.emergency()) {
            if (now % 20 == 0) {
                for (UUID id : a.inside) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) ensureExit(realm, a, p);
                }
            }
            tickChannels(server, st, a, w, now);
            if (st.arenas.get(a.slot) != a) return;
        }
        if (now % 10 == 0) sync(server, a, w, live, true);
    }

    /** The enemies a fight counts: an incident's curses, a trial's, a battle room's spirit. */
    static List<UUID> tracked(MinecraftServer server, InvestigationState st, InvestigationState.Arena a) {
        if (a.incident.startsWith(CursedRealms.TRIAL)) {
            return dev.rick.jjk.progression.story.PersonalTrials.curses(a.incident.substring(CursedRealms.TRIAL.length()));
        }
        if (a.incident.startsWith(CursedRealms.ROOM)) {
            var room = dev.rick.jjk.progression.CursedEncounters.byKey(server, a.incident.substring(CursedRealms.ROOM.length()));
            return room == null || room.spirit == null || room.state == dev.rick.jjk.progression.CursedEncounters.State.CLEARED ? List.of() : List.of(room.spirit);
        }
        Incident in = st.incidents.get(a.incident);
        return in == null ? List.of() : List.copyOf(in.curses);
    }

    /** The tracked enemies that are there now (loaded, alive, not a held-back wave). */
    private static List<Mob> live(ServerLevel realm, List<UUID> tracked) {
        List<Mob> out = new ArrayList<>();
        for (UUID id : tracked) {
            if (realm.getEntity(id) instanceof Mob m && m.isAlive() && !(m instanceof CommonCurseEntity c && c.dormant())) out.add(m);
        }
        return out;
    }

    /**
     * The incident's records against the world: missing curses (on loaded ground, for several checks) are dropped as
     * stale; a held-back wave comes in if nothing of the first is left; an empty list completes it.
     */
    private static void reconcile(MinecraftServer server, InvestigationState st, InvestigationState.Arena a, Incident in, ServerLevel realm) {
        Watch w = a.watch;
        if (in.state != Incident.State.ACTIVE) return;
        boolean loaded = CursedRealms.entitiesLoaded(realm, a.origin);
        boolean changed = false;
        for (UUID id : List.copyOf(in.curses)) {
            Entity e = realm.getEntity(id);
            if (e != null) {
                // There (alive, or dying: its death will settle it).
                w.missing.remove(id);
                continue;
            }
            if (!loaded) continue;
            int n = w.missing.merge(id, 1, Integer::sum);
            if (n >= cfg().staleChecks) {
                in.curses.remove(id);
                w.missing.remove(id);
                changed = true;
                JJK.LOGGER.warn("[encounters] encounter {} ({}): curse {} is gone from loaded ground ({} checks); stale record dropped", a.encounter, in.id, id, n);
            }
        }
        if (changed) st.markDirty();
        if (in.curses.isEmpty()) JJK.LOGGER.info("[encounters] encounter {} ({}): no curses left on record", a.encounter, in.id);
        Investigations.recordsChanged(server, in, st, null);
    }

    /** Multiplayer: the curses' health scales with how many are fighting them (capped). */
    private static void scale(InvestigationState.Arena a, List<Mob> live) {
        var t = JJKConfig.get().curses;
        double s = Math.min(Math.max(1.0, t.maxPlayerScaling), 1.0 + t.healthPerExtraPlayer * Math.max(0, a.inside.size() - 1));
        for (Mob m : live) if (m instanceof CommonCurseEntity c) c.setPlayerScale(s);
    }

    // --- Recovery ---

    /**
     * Whether a revealed curse is stuck: somewhere it can't be, or gone nowhere for a long while while fighting no one,
     * mid-move in nothing, and unable to path to any player. A brief loss of sight, an attack, or players keeping away
     * from something that is still moving are none of these.
     */
    private static void checkStuck(MinecraftServer server, InvestigationState.Arena a, Watch w, ServerLevel realm, CursedRealms.Layout layout, Mob m, long now) {
        Track t = w.tracks.computeIfAbsent(m.getUUID(), k -> new Track(m.position(), now));
        if (m.position().distanceToSqr(t.anchor) > 2.25) {
            t.anchor = m.position();
            t.since = now;
            return;
        }
        if (now - t.since < cfg().stuckStillTicks) return;
        ServerPlayer near = nearest(server, a, m.position());
        if (near == null || m.distanceTo(near) < 4.5 || now - w.lastCombat < 100) return;
        if (m instanceof CommonCurseEntity c && c.action() != 0 && !c.isStaggered()) return;
        if (canPath(m, near)) return;
        relocate(server, a, w, realm, layout, m, "no path to anyone for " + (now - t.since) + " ticks");
    }

    private static boolean canPath(Mob m, ServerPlayer p) {
        Path path = m.getNavigation().createPath(p, 1);
        return path != null && path.canReach();
    }

    /** Out of the arena, under it, in a wall or in lava. */
    private static boolean invalidSpot(ServerLevel realm, InvestigationState.Arena a, Mob m) {
        Vec3 o = Vec3.atBottomCenterOf(a.origin);
        if (Math.abs(m.getX() - o.x) > CursedRealms.RADIUS || Math.abs(m.getZ() - o.z) > CursedRealms.RADIUS) return true;
        if (m.getY() < o.y - 16 || m.getY() > o.y + CursedRealms.HEIGHT) return true;
        return m.isInWall() || m.isInLava();
    }

    /** Brings a stuck curse back into the fight: same curse, same health, on safe ground in view of a player. */
    static boolean relocate(MinecraftServer server, InvestigationState.Arena a, Watch w, ServerLevel realm, CursedRealms.Layout layout, Mob m, String why) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : a.inside) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.isAlive()) players.add(p);
        }
        Vec3 to = safeSpot(realm, a, layout, m, players);
        int n = w.recoveries.merge(m.getUUID(), 1, Integer::sum);
        if (to == null) {
            w.failures++;
            JJK.LOGGER.warn("[encounters] encounter {} ({}): {} is stuck ({}) but no safe place was found (failure {})", a.encounter, a.incident,
                    m.getType().getDescriptionId(), why, w.failures);
            return false;
        }
        if (n > 2) w.failures++;
        Vec3 from = m.position();
        realm.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + m.getBbHeight() * 0.5, from.z, 30, 0.4, 0.6, 0.4, 0.05);
        m.teleportTo(to.x, to.y, to.z);
        m.setDeltaMovement(Vec3.ZERO);
        m.fallDistance = 0;
        ServerPlayer near = nearest(server, a, to);
        if (near != null) {
            float yaw = (float) (Math.toDegrees(Math.atan2(near.getZ() - to.z, near.getX() - to.x))) - 90f;
            m.setYRot(yaw);
            m.setYHeadRot(yaw);
            m.setYBodyRot(yaw);
        }
        if (m instanceof CommonCurseEntity c) c.returnedToFight(cfg().recoverAttackDelay);
        else m.getNavigation().stop();
        w.tracks.remove(m.getUUID());
        Fx.play(realm, "fb_manifest", to.add(0, m.getBbHeight() * 0.5, 0), Vec3.ZERO, Math.max(0.6f, m.getBbWidth()), m.getId());
        realm.sendParticles(ParticleTypes.SCULK_SOUL, to.x, to.y + 0.5, to.z, 16, 0.4, 0.5, 0.4, 0.02);
        realm.playSound(null, to.x, to.y, to.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.8f, 1.5f);
        realm.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.6f);
        JJK.LOGGER.info("[encounters] encounter {} ({}): recovered {} ({}): {} -> {} (recovery {} for it)", a.encounter, a.incident,
                m.getType().getDescriptionId(), why, BlockPos.containing(from), BlockPos.containing(to), n);
        for (ServerPlayer p : players) p.sendOverlayMessage(Component.literal("A curse claws its way back into the fight.").withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    /**
     * Safe ground for a curse coming back: inside the arena, solid footing, room for its body, nothing that burns or
     * drowns, at least {@link JJKConfig.Encounters#recoverMinDistance} from every player and within
     * {@link JJKConfig.Encounters#recoverMaxDistance} of one, preferably in front of a player and in their sight (never
     * on them, in a block, or behind them for a hit they can't see coming).
     */
    @Nullable
    static Vec3 safeSpot(ServerLevel realm, InvestigationState.Arena a, CursedRealms.Layout layout, Mob m, List<ServerPlayer> players) {
        Vec3 o = Vec3.atBottomCenterOf(a.origin);
        var c = cfg();
        List<Vec3> candidates = new ArrayList<>();
        var r = realm.getRandom();
        for (ServerPlayer p : players) {
            Vec3 look = p.getLookAngle().multiply(1, 0, 1);
            look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
            for (int i = 0; i < 24; i++) {
                double ang = Math.toRadians((i % 2 == 0 ? 1 : -1) * (i / 2) * 15.0);
                Vec3 dir = new Vec3(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang));
                double dist = c.recoverMinDistance + 1 + r.nextDouble() * Math.max(0.5, c.recoverMaxDistance - c.recoverMinDistance - 2);
                candidates.add(p.position().add(dir.scale(dist)));
            }
        }
        for (Vec3 s : layout.curseSpots()) candidates.add(o.add(s));
        Vec3 fallback = null;
        for (Vec3 cand : candidates) {
            Vec3 ground = groundAt(realm, a, m, cand);
            if (ground == null) continue;
            double nearest = Double.MAX_VALUE;
            boolean tooClose = false, seen = false;
            for (ServerPlayer p : players) {
                double d = p.position().distanceTo(ground);
                nearest = Math.min(nearest, d);
                if (d < c.recoverMinDistance) tooClose = true;
                Vec3 to = ground.subtract(p.position()).multiply(1, 0, 1).normalize();
                Vec3 look = p.getLookAngle().multiply(1, 0, 1);
                if (look.lengthSqr() > 1e-4 && look.normalize().dot(to) > 0.25 && dev.rick.jjk.core.hitbox.HitboxQuery.hasLineOfSight(realm, ground.add(0, m.getBbHeight() * 0.7, 0), p)) {
                    seen = true;
                }
            }
            if (tooClose || (!players.isEmpty() && nearest > c.recoverMaxDistance)) continue;
            if (seen || players.isEmpty()) return ground;
            if (fallback == null) fallback = ground;
        }
        return fallback;
    }

    /** The standing spot in the column at {@code at} (scanning down from above it), or null if none is safe. */
    @Nullable
    private static Vec3 groundAt(ServerLevel realm, InvestigationState.Arena a, Entity body, Vec3 at) {
        Vec3 o = Vec3.atBottomCenterOf(a.origin);
        int edge = CursedRealms.RADIUS - 2;
        if (Math.abs(at.x - o.x) > edge || Math.abs(at.z - o.z) > edge) return null;
        int top = (int) Math.floor(at.y) + 6, bottom = a.origin.getY() - 12;
        for (int y = top; y >= bottom; y--) {
            BlockPos feet = BlockPos.containing(at.x, y, at.z);
            BlockPos below = feet.below();
            BlockState under = realm.getBlockState(below);
            if (under.getCollisionShape(realm, below).isEmpty()) continue;
            if (harmful(under) || !under.getFluidState().isEmpty()) return null;
            Vec3 stand = new Vec3(at.x, y, at.z);
            AABB box = body.getDimensions(body.getPose()).makeBoundingBox(stand);
            if (!realm.noCollision(body, box) || realm.containsAnyLiquid(box)) return null;
            for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                if (harmful(realm.getBlockState(q))) return null;
            }
            return stand;
        }
        return null;
    }

    private static boolean harmful(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE) || s.getFluidState().is(FluidTags.LAVA);
    }

    @Nullable
    private static ServerPlayer nearest(MinecraftServer server, InvestigationState.Arena a, Vec3 at) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (UUID id : a.inside) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) continue;
            double d = p.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // --- Emergency Return ---

    /** Each player in a stalled fight has an Emergency Return breach near them (moved along if they've gone far from it). */
    private static void ensureExit(ServerLevel realm, InvestigationState.Arena a, ServerPlayer p) {
        String target = EXIT + a.slot + ":" + p.getUUID();
        Vec3 o = Vec3.atBottomCenterOf(a.origin);
        AABB box = new AABB(o.x - CursedRealms.LOADED, o.y - 20, o.z - CursedRealms.LOADED, o.x + CursedRealms.LOADED, o.y + CursedRealms.HEIGHT, o.z + CursedRealms.LOADED);
        CursedBreachEntity mine = null;
        for (CursedBreachEntity e : realm.getEntitiesOfClass(CursedBreachEntity.class, box, e -> target.equals(e.target))) {
            if (mine == null && e.distanceTo(p) <= 14) mine = e;
            else if (!a.watch.channels.containsKey(p.getUUID())) e.discard();
        }
        if (mine != null) return;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 at = null;
        for (double d : new double[] {2.5, 2.0, 3.5, 1.5}) {
            for (double turn : new double[] {0, 45, -45, 90, -90, 180}) {
                double ang = Math.toRadians(turn);
                Vec3 dir = new Vec3(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang));
                Vec3 c = p.position().add(dir.scale(d));
                if (realm.noCollision(new AABB(c.x - 0.55, c.y, c.z - 0.55, c.x + 0.55, c.y + 1.9, c.z + 0.55))) {
                    at = c;
                    break;
                }
            }
            if (at != null) break;
        }
        if (at == null) at = p.position();
        CursedBreachEntity e = ModEntities.CURSED_BREACH.create(realm, EntitySpawnReason.EVENT);
        if (e == null) return;
        e.target = target;
        e.setBreachState(2);
        e.snapTo(at.x, at.y + 0.25, at.z, 0, 0);
        realm.addFreshEntity(e);
        realm.playSound(null, at.x, at.y + 1, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.7f, 1.6f);
    }

    /** An exit breach: 2 while its fight is open and stalled, -1 once there's nothing to leave. */
    static int exitStatus(ServerLevel level, CursedBreachEntity e) {
        InvestigationState.Arena a = exitArena(level.getServer(), e.target);
        return a == null || !a.watch.emergency() ? -1 : 2;
    }

    @Nullable
    private static InvestigationState.Arena exitArena(MinecraftServer server, String target) {
        if (!target.startsWith(EXIT)) return null;
        String rest = target.substring(EXIT.length());
        int colon = rest.indexOf(':');
        try {
            int slot = Integer.parseInt(colon < 0 ? rest : rest.substring(0, colon));
            return InvestigationState.get(server).arenas.get(slot);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** A player reaches into an Emergency Return: they must now hold still, unhurt, for the channel. */
    static void startChannel(ServerPlayer p, CursedBreachEntity e) {
        MinecraftServer server = p.level().getServer();
        InvestigationState.Arena a = exitArena(server, e.target);
        if (a == null || !a.watch.emergency() || !a.inside.contains(p.getUUID()) || p.position().distanceToSqr(e.position()) > 6 * 6) return;
        if (a.watch.channels.containsKey(p.getUUID())) return;
        a.watch.channels.put(p.getUUID(), new Channel(p.position(), server.overworld().getGameTime(), p.getHealth()));
        p.sendSystemMessage(Component.literal("Emergency Return: hold still. Leaving now abandons the fight; you will get nothing from it.")
                .withStyle(ChatFormatting.GOLD));
        ((ServerLevel) p.level()).playSound(null, e.getX(), e.getY() + 1, e.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    private static void tickChannels(MinecraftServer server, InvestigationState st, InvestigationState.Arena a, Watch w, long now) {
        for (Map.Entry<UUID, Channel> en : List.copyOf(w.channels.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            Channel ch = en.getValue();
            if (p == null || !p.isAlive() || !a.inside.contains(p.getUUID())) {
                w.channels.remove(en.getKey());
                continue;
            }
            String broke = p.position().distanceToSqr(ch.at()) > 1.5 * 1.5 ? "you moved" : p.getHealth() < ch.health() - 0.01f ? "you were hit" : null;
            if (broke != null) {
                // Interrupted: start the channel again (the Emergency Return itself stays open).
                w.channels.remove(en.getKey());
                p.sendOverlayMessage(Component.literal("Emergency Return interrupted: " + broke + ".").withStyle(ChatFormatting.RED));
                continue;
            }
            if (now - ch.started() >= cfg().emergencyChannelTicks) {
                w.channels.remove(en.getKey());
                emergencyReturn(server, st, a, p);
                if (st.arenas.get(a.slot) != a) return;
            }
        }
    }

    /** Takes a player out of a stalled fight: home, nothing gained; the fight closes once nobody is left in it. */
    static void emergencyReturn(MinecraftServer server, InvestigationState st, InvestigationState.Arena a, ServerPlayer p) {
        a.forfeited.add(p.getUUID());
        a.inside.remove(p.getUUID());
        Incident in = st.incidents.get(a.incident);
        if (in != null) in.participants.remove(p.getUUID());
        st.markDirty();
        ServerPlayNetworking.send(p, EncounterPayload.clear());
        CursedRealms.sendHome(p, st);
        p.sendSystemMessage(Component.literal("You tear your way out. The fight is abandoned; nothing is gained from it.").withStyle(ChatFormatting.GRAY));
        JJK.LOGGER.info("[encounters] {} left encounter {} ({}) by Emergency Return: aborted for them", p.getName().getString(), a.encounter, a.incident);
        boolean anyone = false;
        for (UUID id : a.inside) {
            ServerPlayer o = server.getPlayerList().getPlayer(id);
            if (o != null && o.isAlive()) anyone = true;
        }
        if (!anyone) {
            JJK.LOGGER.info("[encounters] encounter {} ({}) aborted: nobody left in it", a.encounter, a.incident);
            CursedRealms.close(server, st, a, false);
        }
    }

    // --- The fight's HUD ---

    private static void tell(MinecraftServer server, InvestigationState.Arena a, String line, ChatFormatting color) {
        for (UUID id : a.inside) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal(line).withStyle(color));
        }
    }

    /** Each player in the fight (and only they) is told how it stands. */
    private static void sync(MinecraftServer server, InvestigationState.Arena a, Watch w, List<Mob> live, boolean send) {
        for (UUID id : List.copyOf(w.told)) {
            if (a.inside.contains(id)) continue;
            w.told.remove(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) ServerPlayNetworking.send(p, EncounterPayload.clear());
        }
        if (!send) return;
        int stage = w.emergency() ? EncounterPayload.EMERGENCY : w.revealed() ? EncounterPayload.REVEALED : EncounterPayload.NORMAL;
        List<EncounterPayload.Mark> marks = new ArrayList<>();
        if (w.revealed() || w.emergency()) {
            for (Mob m : live) {
                if (marks.size() >= 64) break;
                marks.add(new EncounterPayload.Mark(m.getId(), m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ()));
            }
        }
        long now = server.overworld().getGameTime();
        for (UUID id : a.inside) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            Channel ch = w.channels.get(id);
            float progress = ch == null ? -1f : Math.min(1f, (now - ch.started()) / (float) Math.max(1, cfg().emergencyChannelTicks));
            ServerPlayNetworking.send(p, new EncounterPayload(w.remaining, stage, marks, progress));
            w.told.add(id);
        }
    }

    // --- Test hooks ---

    /** Test hook: the arena of an incident's fight, or null. */
    @Nullable
    public static InvestigationState.Arena arenaForTest(MinecraftServer server, String incident) {
        return CursedRealms.arenaOf(InvestigationState.get(server), incident);
    }

    /** Test hook: pretend nothing has happened in a fight for {@code ticks}. */
    public static void ageForTest(InvestigationState.Arena a, long ticks) {
        a.watch.lastCombat -= ticks;
        a.watch.lastProgress -= ticks;
        if (a.watch.revealed) a.watch.revealedAt -= ticks;
        for (Track t : a.watch.tracks.values()) t.since -= ticks;
    }

    /** Test hook: reach into an exit breach. */
    public static void useExitForTest(ServerPlayer p, CursedBreachEntity e) {
        startChannel(p, e);
    }

    /** Test hook: the exit breach nearest a player in a fight, or null. */
    @Nullable
    public static CursedBreachEntity exitNear(ServerPlayer p) {
        return ((ServerLevel) p.level()).getEntitiesOfClass(CursedBreachEntity.class, p.getBoundingBox().inflate(16), e -> e.target.startsWith(EXIT)).stream()
                .findFirst().orElse(null);
    }

    /** Test hook: whether a curse is somewhere it can't be (for asserting a recovery happened). */
    public static boolean invalidForTest(InvestigationState.Arena a, Mob m) {
        return invalidSpot((ServerLevel) m.level(), a, m);
    }

    /** Test hook: a record for a curse that never made it into the world (a failed spawn, a lost entity). */
    public static void trackForTest(Incident in, UUID ghost) {
        in.curses.add(ghost);
    }

    /** Test hook: the completion arriving a second time (a duplicate event). */
    public static void completeAgainForTest(MinecraftServer server, Incident in) {
        Investigations.complete(server, in, InvestigationState.get(server));
    }

    /** Test hook: whether a fight is still being tracked as open. */
    public static boolean openForTest(MinecraftServer server, InvestigationState.Arena a) {
        return InvestigationState.get(server).arenas.get(a.slot) == a;
    }

    /** Test hook: a fight's {@link LivingEntity} count on record. */
    public static int remainingForTest(InvestigationState.Arena a) {
        return a.watch.remaining;
    }
}
