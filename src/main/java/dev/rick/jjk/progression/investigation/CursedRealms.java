package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.FxPayload;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cursed realms: pocket spaces a curse makes of the place it haunts, built as arenas in one shared void dimension
 * ({@code jjk:cursed_realm}). Each arena takes a slot (its own patch of the void, far from the others), is built from a
 * {@link Layout} when the first player is pulled in, and is cleared back to empty void when it closes: no new dimension
 * per incident, nothing left behind.
 *
 * <ul>
 *   <li><b>Entry</b>: whoever sets off the incident ({@link Investigations}) is pulled in; anyone setting it off while it
 *   is open joins the same arena. Where they came from is saved.</li>
 *   <li><b>Victory</b>: the last curse falls; a few seconds later everyone inside is sent back where they came from.</li>
 *   <li><b>Death</b>: the player respawns as usual (the realm is not a respawn point); the arena carries on for the rest.</li>
 *   <li><b>Abandoned</b>: nobody inside for a minute (everyone died, fled, logged out), or the server restarted: the arena
 *   is cleared, its curses go, and the incident is open again to retry.</li>
 *   <li><b>Safety</b>: falling out of the arena puts you back on its ground; a player who logs in inside the realm without
 *   an arena to be in is sent home.</li>
 * </ul>
 */
public final class CursedRealms {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, JJK.id("cursed_realm"));
    /** Slots are this far apart in the void. */
    static final int SPACING = 512;
    static final int BASE_Y = 80;
    /** Half-width and height of the box an arena may use (and that is cleared). */
    static final int RADIUS = 26, HEIGHT = 40;
    static final int WIN_DELAY = 60, ABANDON_AFTER = 1200;

    /** How an arena looks, where players land and where its curses wait. */
    public interface Layout {
        void build(ServerLevel level, BlockPos origin, RandomSource random);

        /** Where players arrive, relative to the origin. */
        Vec3 arrival();

        /** Where the curses appear, relative to the origin. */
        List<Vec3> curseSpots();

        /** Pulled in from above (and so given a slow fall), or set down. */
        default boolean fromAbove() {
            return false;
        }

        /** Arena radius for leashing its curses. */
        default int radius() {
            return 14;
        }

        String entryLine();
    }

    private static final Map<String, Layout> LAYOUTS = Map.ofEntries(Map.entry("cliff_realm", new CliffRealm()), Map.entry("mine_realm", new MineRealm()),
            Map.entry("forest_realm", new ForestRealm()), Map.entry("pasture_realm", new PastureRealm()), Map.entry("house_realm", new HouseRealm()),
            Map.entry("hollow_realm", new HollowRealm()), Map.entry("finger_bearer_realm", new BattleRealm()),
            // The character storylines' realms (StoryRealms): their village events and their personal trials.
            Map.entry("distance_realm", new StoryRealms.Distance()), Map.entry("theater_realm", new StoryRealms.Theater()),
            Map.entry("crater_realm", new StoryRealms.Crater()), Map.entry("fight_club_realm", new StoryRealms.FightClub()),
            Map.entry("chapel_realm", new StoryRealms.Chapel()));
    /** Arenas that belong to a cursed battle room (the Finger Bearer's), not an incident: their key starts with this. */
    public static final String ROOM = "room:";
    /** Arenas that belong to a personal character trial ({@link dev.rick.jjk.progression.story.PersonalTrials}). */
    public static final String TRIAL = "trial:";

    /** The layout for an id, or the plain hollow realm (an incident always has somewhere to fight). */
    static Layout layout(String id) {
        Layout l = LAYOUTS.get(id);
        return l != null ? l : LAYOUTS.get("hollow_realm");
    }

    // --- The pull ---

    /** Someone being taken: held where the pull caught them, for {@link JJKConfig.Realms#transitionTicks}, then {@code arrive}. */
    private static final class Pull {
        final Vec3 hold;
        final Vec3 safe;
        final String safeDim;
        final Runnable arrive;
        int age;

        Pull(Vec3 hold, Vec3 safe, String safeDim, Runnable arrive) {
            this.hold = hold;
            this.safe = safe;
            this.safeDim = safeDim;
            this.arrive = arrive;
        }
    }

    private static final Map<UUID, Pull> PULLS = new java.util.HashMap<>();

    public static boolean pulling(Entity e) {
        return PULLS.containsKey(e.getUUID());
    }

    /**
     * Starts taking a player into a realm: they are held still where they are (mid-fall, if that's where it caught them:
     * the cliff's drop never lands), sound dulls, the world darkens and pulses, and after the transition they arrive
     * ({@code arrive}). Nothing hurts them meanwhile. {@code safe} is where they go if the pull is cut short.
     */
    public static void pull(ServerPlayer p, Vec3 safe, Runnable arrive) {
        if (PULLS.containsKey(p.getUUID())) return;
        PULLS.put(p.getUUID(), new Pull(p.position(), safe, p.level().dimension().identifier().toString(), arrive));
        ServerLevel level = (ServerLevel) p.level();
        p.setDeltaMovement(Vec3.ZERO);
        p.needsSync = true;
        p.fallDistance = 0;
        int ticks = Math.max(20, dev.rick.jjk.config.JJKConfig.get().realms.transitionTicks);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks + 30, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false));
        Fx.play(level, "curse_realm_pull", p.position().add(0, 1, 0), Vec3.ZERO, 1f);
        ServerPlayNetworking.send(p, new FxPayload("realm_transition", p.position(), Vec3.ZERO, ticks, -1));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.AMBIENT, 1.6f, 0.5f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMBIENT_CAVE.value(), net.minecraft.sounds.SoundSource.AMBIENT, 1.2f, 0.6f);
    }

    /** Cuts a pull short (logging out mid-pull): set down at its safe spot. */
    public static void cancelPull(ServerPlayer p) {
        Pull pull = PULLS.remove(p.getUUID());
        if (pull == null) return;
        p.teleportTo(pull.safe.x, pull.safe.y, pull.safe.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    /** Test hook: finishes a pull now. */
    public static void finishPullForTest(ServerPlayer p) {
        Pull pull = PULLS.remove(p.getUUID());
        if (pull != null) pull.arrive.run();
    }

    private static void tickPulls(MinecraftServer server) {
        if (PULLS.isEmpty()) return;
        int ticks = Math.max(20, dev.rick.jjk.config.JJKConfig.get().realms.transitionTicks);
        for (Map.Entry<UUID, Pull> e : List.copyOf(PULLS.entrySet())) {
            Pull pull = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || p.isSpectator()) {
                PULLS.remove(e.getKey());
                continue;
            }
            pull.age++;
            // Held: no falling, no walking off.
            if (p.position().distanceToSqr(pull.hold) > 0.0025) p.teleportTo(pull.hold.x, pull.hold.y, pull.hold.z);
            p.setDeltaMovement(Vec3.ZERO);
            p.needsSync = true;
            p.fallDistance = 0;
            ServerLevel level = (ServerLevel) p.level();
            if (pull.age == ticks / 2) {
                // The pulse: a second, closer heartbeat, and the light goes.
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.AMBIENT, 2f, 0.4f);
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks - pull.age + 12, 0, false, false));
            }
            if (pull.age % 6 == 0) Fx.play(level, "curse_realm_pull", p.position().add(0, 1, 0), Vec3.ZERO, 0.4f + 0.6f * pull.age / ticks);
            if (pull.age >= ticks) {
                PULLS.remove(e.getKey());
                pull.arrive.run();
                // If nothing took them (the incident ended meanwhile), they are set down safely.
                if (!inRealm(p)) {
                    p.teleportTo(pull.safe.x, pull.safe.y, pull.safe.z);
                    p.fallDistance = 0;
                }
            }
        }
    }

    /** Solid footing within a block and a half under a spot (standing, or a step off the ground). */
    private static boolean groundUnder(ServerLevel level, Vec3 at) {
        BlockPos b = BlockPos.containing(at.x, at.y - 0.01, at.z);
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos q = b.below(dy);
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return true;
        }
        return false;
    }

    /**
     * Where a player taken from an incident comes back: where they stood if that was solid ground, else (mid-jump off the
     * cliff, mid-fall) the ground back from the edge, by the site.
     */
    public static Vec3 safeReturn(ServerLevel level, ServerPlayer p, Incident in) {
        if (p.onGround() || groundUnder(level, p.position())) return p.position();
        BlockPos back = in.site.offset(-in.dirX * 2, 0, -in.dirZ * 2);
        // The first footing under the edge's height there (not the sky's heightmap: an overhang or a roof isn't ground).
        int from = Math.max(back.getY() + 2, (int) Math.ceil(p.getY()));
        for (int y = from; y > from - 48 && y > level.getMinY(); y--) {
            BlockPos q = new BlockPos(back.getX(), y - 1, back.getZ());
            BlockPos head = q.above();
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                    && level.getBlockState(head.above()).getCollisionShape(level, head.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(head);
            }
        }
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, back);
        return Vec3.atBottomCenterOf(top);
    }

    private CursedRealms() {}

    public static boolean hasLayout(String id) {
        return LAYOUTS.containsKey(id);
    }

    /**
     * Where arenas are built when the world has no realm dimension (a world whose dimensions were fixed before the mod,
     * or a test server): far out over the overworld, high in the sky, still one slot per arena and cleared after.
     */
    static final int FALLBACK_X = 12_000_000, FALLBACK_Z = 12_000_000, FALLBACK_Y = 272;

    /** The level arenas live in: the realm dimension, or the overworld's far sky if the world has none. */
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel l = server.getLevel(DIMENSION);
        return l != null ? l : server.overworld();
    }

    private static boolean dedicated(MinecraftServer server) {
        return server.getLevel(DIMENSION) != null;
    }

    /** Whether an entity is in a cursed realm (the dimension, or the overworld fallback's region). */
    public static boolean inRealm(Entity e) {
        if (e.level().dimension() == DIMENSION) return true;
        MinecraftServer s = e.level().getServer();
        return s != null && !dedicated(s) && e.level().dimension() == Level.OVERWORLD && e.getX() > FALLBACK_X - 4000 && e.getZ() > FALLBACK_Z - 4000;
    }

    static BlockPos origin(MinecraftServer server, int slot) {
        return dedicated(server) ? new BlockPos(slot * SPACING, BASE_Y, 0) : new BlockPos(FALLBACK_X + slot * SPACING, FALLBACK_Y, FALLBACK_Z);
    }

    @Nullable
    public static InvestigationState.Arena arenaOf(InvestigationState st, String incident) {
        for (InvestigationState.Arena a : st.arenas.values()) if (a.incident.equals(incident)) return a;
        return null;
    }

    @Nullable
    static InvestigationState.Arena arenaAt(InvestigationState st, Vec3 pos) {
        for (InvestigationState.Arena a : st.arenas.values()) {
            if (Math.abs(pos.x - a.origin.getX()) <= RADIUS + 8 && Math.abs(pos.z - a.origin.getZ()) <= RADIUS + 8) return a;
        }
        return null;
    }

    // --- Entering ---

    /**
     * Takes a player into the incident's realm now (building it if it isn't open yet), skipping the pull. Everyone who
     * sets it off while it is open joins the same arena: one arena per incident, however many come.
     */
    public static boolean enter(ServerPlayer p, Incident in, InvestigationState st) {
        IncidentTemplate t = in.def();
        MinecraftServer server = p.level().getServer();
        ServerLevel realm = level(server);
        if (t == null) return false;
        String layoutId = LAYOUTS.containsKey(t.realm()) ? t.realm() : "hollow_realm";
        Layout layout = layout(layoutId);
        InvestigationState.Arena a = arenaOf(st, in.id);
        if (a == null) {
            int slot = 0;
            while (st.arenas.containsKey(slot)) slot++;
            a = new InvestigationState.Arena(slot, in.id, layoutId, origin(server, slot));
            if (!dedicated(server)) JJK.LOGGER.warn("[investigations] this world has no {} dimension: the realm is built in the overworld's far sky", DIMENSION.identifier());
            a.openedAt = server.overworld().getGameTime();
            st.arenas.put(slot, a);
            forceLoad(realm, a.origin, true);
            clear(realm, a.origin);
            layout.build(realm, a.origin, RandomSource.create(in.id.hashCode()));
            Investigations.spawnCurses(realm, in, a.origin, layout.curseSpots(), layout.radius());
            in.state = Incident.State.ACTIVE;
            in.changedAt = server.overworld().getGameTime();
            JJK.LOGGER.info("[investigations] realm {} opened for incident {} ({})", slot, in.id, t.realm());
        }
        if (!inRealm(p)) {
            Vec3 back = p.level() instanceof ServerLevel from && in.dimension.equals(from.dimension().identifier().toString()) ? safeReturn(from, p, in) : p.position();
            st.returns.put(p.getUUID(), new InvestigationState.Return(p.level().dimension().identifier().toString(), back, p.getYRot()));
        }
        in.participants.add(p.getUUID());
        a.inside.add(p.getUUID());
        a.emptyTicks = 0;
        st.markDirty();
        arrive(p, realm, Vec3.atBottomCenterOf(a.origin).add(layout.arrival()), layout);
        return true;
    }

    /**
     * Takes a player into a cursed battle room's realm now (the Finger Bearer's): one arena per room, shared by everyone
     * who walks into the room while it's open. They come back to where they stood in the room.
     */
    public static void enterRoom(ServerPlayer p, String roomKey, InvestigationState st) {
        MinecraftServer server = p.level().getServer();
        ServerLevel realm = level(server);
        String key = ROOM + roomKey;
        Layout layout = layout("finger_bearer_realm");
        InvestigationState.Arena a = arenaOf(st, key);
        if (a == null) {
            int slot = 0;
            while (st.arenas.containsKey(slot)) slot++;
            a = new InvestigationState.Arena(slot, key, "finger_bearer_realm", origin(server, slot));
            a.openedAt = server.overworld().getGameTime();
            st.arenas.put(slot, a);
            forceLoad(realm, a.origin, true);
            clear(realm, a.origin);
            layout.build(realm, a.origin, RandomSource.create(roomKey.hashCode()));
            JJK.LOGGER.info("[encounters] realm {} opened for battle room {}", slot, roomKey);
        }
        if (!inRealm(p)) st.returns.put(p.getUUID(), new InvestigationState.Return(p.level().dimension().identifier().toString(), p.position(), p.getYRot()));
        a.inside.add(p.getUUID());
        a.emptyTicks = 0;
        st.markDirty();
        arrive(p, realm, Vec3.atBottomCenterOf(a.origin).add(layout.arrival()), layout);
    }

    /**
     * Takes a player into a personal trial's realm now (one arena per trial, {@code key} without the prefix): built from
     * {@code layoutId}, its rules run by the trial itself. They come back to where they stood.
     */
    public static InvestigationState.Arena enterTrial(ServerPlayer p, String trialKey, String layoutId, InvestigationState st) {
        MinecraftServer server = p.level().getServer();
        ServerLevel realm = level(server);
        String key = TRIAL + trialKey;
        String id = LAYOUTS.containsKey(layoutId) ? layoutId : "hollow_realm";
        Layout layout = layout(id);
        InvestigationState.Arena a = arenaOf(st, key);
        if (a == null) {
            int slot = 0;
            while (st.arenas.containsKey(slot)) slot++;
            a = new InvestigationState.Arena(slot, key, id, origin(server, slot));
            a.openedAt = server.overworld().getGameTime();
            st.arenas.put(slot, a);
            forceLoad(realm, a.origin, true);
            clear(realm, a.origin);
            layout.build(realm, a.origin, RandomSource.create(trialKey.hashCode()));
            JJK.LOGGER.info("[storylines] realm {} opened for trial {}", slot, trialKey);
        }
        if (!inRealm(p)) st.returns.put(p.getUUID(), new InvestigationState.Return(p.level().dimension().identifier().toString(), p.position(), p.getYRot()));
        a.inside.add(p.getUUID());
        a.emptyTicks = 0;
        st.markDirty();
        arrive(p, realm, Vec3.atBottomCenterOf(a.origin).add(layout.arrival()), layout);
        return a;
    }

    /** Where players arrive in an arena (its layout's arrival, in the world). */
    public static Vec3 arrivalOf(InvestigationState.Arena a) {
        return Vec3.atBottomCenterOf(a.origin).add(layout(a.layout).arrival());
    }

    /** Where an arena's layout puts its curses, in the world. */
    public static List<Vec3> curseSpotsOf(InvestigationState.Arena a) {
        List<Vec3> out = new ArrayList<>();
        for (Vec3 v : layout(a.layout).curseSpots()) out.add(Vec3.atBottomCenterOf(a.origin).add(v));
        return out;
    }

    private static void arrive(ServerPlayer p, ServerLevel realm, Vec3 at, Layout layout) {
        p.teleport(new TeleportTransition(realm, at, Vec3.ZERO, p.getYRot(), 10f, Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        p.fallDistance = 0;
        // The fade in: the dark lifts over a few seconds.
        p.removeEffect(MobEffects.BLINDNESS);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
        if (layout.fromAbove()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
        realm.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.AMBIENT, 2f, 0.6f);
        realm.playSound(null, at.x, at.y, at.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), net.minecraft.sounds.SoundSource.AMBIENT, 1.5f, 0.7f);
        ServerPlayNetworking.send(p, new FxPayload("curse_realm_pull", at.add(0, 1, 0), Vec3.ZERO, 1f, -1));
        p.sendOverlayMessage(Component.literal(layout.entryLine()).withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC));
    }

    /** The arena an entity in the realm stands in, or null (in the realm with no arena there: it shouldn't exist). */
    @Nullable
    public static InvestigationState.Arena arenaHere(Entity e) {
        MinecraftServer s = e.level().getServer();
        if (s == null || !inRealm(e)) return null;
        return arenaAt(InvestigationState.get(s), e.position());
    }

    /** The arena's origin (its centre on the ground), for leashing what lives in it. */
    public static int arenaRadius(InvestigationState.Arena a) {
        return layout(a.layout).radius();
    }

    // --- Running ---

    static void tick(MinecraftServer server, InvestigationState st) {
        tickPulls(server);
        ServerLevel realm = level(server);
        if (st.arenas.isEmpty()) return;
        for (InvestigationState.Arena a : List.copyOf(st.arenas.values())) {
            Incident in = st.incidents.get(a.incident);
            boolean room = a.incident.startsWith(ROOM);
            boolean trial = a.incident.startsWith(TRIAL);
            Layout layout = LAYOUTS.get(a.layout);
            if ((in == null && !room && !trial) || layout == null) {
                close(server, st, a, false);
                continue;
            }
            // Who is actually inside (alive, in the realm, within this arena).
            a.inside.removeIf(id -> {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                return p == null || !p.isAlive() || !inRealm(p) || arenaAt(st, p.position()) != a;
            });
            for (ServerPlayer p : realm.players()) {
                if (inRealm(p) && arenaAt(st, p.position()) == a && p.isAlive()) {
                    a.inside.add(p.getUUID());
                    keepInside(p, a, layout);
                }
            }
            if (trial) {
                // A personal trial's own rules: it says when it is won (the claim already made) or lost.
                int r = dev.rick.jjk.progression.story.PersonalTrials.tickArena(server, a, a.incident.substring(TRIAL.length()), realm);
                if (r > 0) {
                    if (a.wonTicks < 0) a.wonTicks = 0;
                    if (++a.wonTicks >= WIN_DELAY) close(server, st, a, true);
                    continue;
                }
                if (r < 0) {
                    close(server, st, a, false);
                    continue;
                }
            } else if (room) {
                // The battle room's own rules: done when its spirit falls; its spirit raised again if lost.
                int r = dev.rick.jjk.progression.curse.FingerBearerEncounter.tickArena(server, a, a.incident.substring(ROOM.length()), realm);
                if (r > 0) {
                    if (a.wonTicks < 0) a.wonTicks = 0;
                    if (++a.wonTicks >= WIN_DELAY) close(server, st, a, true);
                    continue;
                }
                if (r < 0) {
                    close(server, st, a, false);
                    continue;
                }
            } else if (in.state == Incident.State.COMPLETE) {
                if (a.wonTicks < 0) a.wonTicks = 0;
                if (++a.wonTicks >= WIN_DELAY) close(server, st, a, true);
                continue;
            }
            if (a.inside.isEmpty()) {
                if (++a.emptyTicks >= ABANDON_AFTER) close(server, st, a, false);
            } else {
                a.emptyTicks = 0;
            }
        }
    }

    /** Fell off the edge of the realm: back onto its ground, a little hurt, never killed by it. */
    private static void keepInside(ServerPlayer p, InvestigationState.Arena a, Layout layout) {
        Vec3 o = Vec3.atBottomCenterOf(a.origin);
        if (p.getY() < o.y - 18 || Math.abs(p.getX() - o.x) > RADIUS + 4 || Math.abs(p.getZ() - o.z) > RADIUS + 4) {
            Vec3 at = o.add(layout.arrival());
            p.teleportTo(at.x, at.y, at.z);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            if (p.getHealth() > 3) p.hurtServer((ServerLevel) p.level(), p.damageSources().fellOutOfWorld(), 2f);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
        }
    }

    /** Ends an arena: everyone inside goes home, its curses vanish (if any are left), its blocks are cleared. */
    public static void close(MinecraftServer server, InvestigationState st, InvestigationState.Arena a, boolean won) {
        ServerLevel realm = level(server);
        Incident in = st.incidents.get(a.incident);
        for (ServerPlayer p : List.copyOf(realm.players())) {
            if (inRealm(p) && arenaAt(st, p.position()) == a) sendHome(p, st);
        }
        clear(realm, a.origin);
        forceLoad(realm, a.origin, false);
        st.arenas.remove(a.slot);
        if (!won && a.incident.startsWith(ROOM)) dev.rick.jjk.progression.curse.FingerBearerEncounter.arenaAbandoned(server, a.incident.substring(ROOM.length()));
        if (a.incident.startsWith(TRIAL)) dev.rick.jjk.progression.story.PersonalTrials.arenaClosed(server, a.incident.substring(TRIAL.length()), won);
        if (!won && in != null && in.state == Incident.State.ACTIVE) {
            // Nobody finished it: the place goes quiet again, and can be tried again.
            in.state = Incident.State.OPEN;
            in.curses.clear();
            in.changedAt = server.overworld().getGameTime();
        }
        st.markDirty();
        JJK.LOGGER.info("[investigations] realm {} closed ({})", a.slot, won ? "won" : "abandoned");
    }

    /** Sends a player back where they came from (or to the world spawn if that's lost). */
    public static void sendHome(ServerPlayer p, InvestigationState st) {
        MinecraftServer server = p.level().getServer();
        InvestigationState.Return r = st.returns.remove(p.getUUID());
        st.markDirty();
        ServerLevel to = null;
        Vec3 at = null;
        float yaw = p.getYRot();
        if (r != null) {
            to = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(r.dimension())));
            at = r.pos();
            yaw = r.yaw();
        }
        if (to == null || at == null) {
            TeleportTransition t = p.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
            p.teleport(t);
        } else {
            p.teleport(new TeleportTransition(to, at, Vec3.ZERO, yaw, p.getXRot(), Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        }
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
    }

    /** Keeps an open arena's ground loaded and ticking (its curses fight on whoever is there), and lets it go after. */
    private static void forceLoad(ServerLevel realm, BlockPos origin, boolean on) {
        for (int cx = (origin.getX() - RADIUS) >> 4; cx <= (origin.getX() + RADIUS) >> 4; cx++) {
            for (int cz = (origin.getZ() - RADIUS) >> 4; cz <= (origin.getZ() + RADIUS) >> 4; cz++) realm.setChunkForced(cx, cz, on);
        }
    }

    /** Clears an arena's box back to void, and anything left standing in it. */
    static void clear(ServerLevel realm, BlockPos origin) {
        AABB box = new AABB(origin.getX() - RADIUS, origin.getY() - 20, origin.getZ() - RADIUS, origin.getX() + RADIUS + 1, origin.getY() + HEIGHT, origin.getZ() + RADIUS + 1);
        for (int cx = (origin.getX() - RADIUS) >> 4; cx <= (origin.getX() + RADIUS) >> 4; cx++) {
            for (int cz = (origin.getZ() - RADIUS) >> 4; cz <= (origin.getZ() + RADIUS) >> 4; cz++) realm.getChunk(cx, cz);
        }
        for (Entity e : realm.getEntities((Entity) null, box, e -> !(e instanceof ServerPlayer))) e.discard();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = origin.getY() - 20; y < origin.getY() + HEIGHT; y++) {
            for (int x = origin.getX() - RADIUS; x <= origin.getX() + RADIUS; x++) {
                for (int z = origin.getZ() - RADIUS; z <= origin.getZ() + RADIUS; z++) {
                    m.set(x, y, z);
                    if (!realm.getBlockState(m).isAir()) realm.setBlock(m, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    // --- Layouts ---

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /**
     * The cliff, from the other side: the edge they jumped from, but broken off and hanging in a dark red void. Ragged
     * stone, a cracked face rising on one side, dead brush, a few cold soul lanterns. They fall into it from above.
     */
    static final class CliffRealm implements Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    double d = Math.sqrt(x * x + z * z) + r.nextDouble() * 2.2;
                    if (d > 14.5) continue;
                    int depth = (int) Math.max(1, 6 - d / 2.5) + r.nextInt(2);
                    for (int y = 0; y < depth; y++) {
                        BlockState s = y == 0 ? (r.nextInt(7) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState())
                                : Blocks.BLACKSTONE.defaultBlockState();
                        set(l, o.offset(x, -y, z), s);
                    }
                    if (r.nextInt(18) == 0 && d < 12) set(l, o.offset(x, 1, z), Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
            // The cliff face: a jagged wall along the north side.
            for (int x = -12; x <= 12; x++) {
                int h = 6 + r.nextInt(6) - Math.abs(x) / 3;
                for (int y = 1; y <= h; y++) for (int z = -14; z <= -12 + (y < 3 ? 1 : 0); z++) {
                    if (Math.sqrt(x * x + z * z) <= 14.5) set(l, o.offset(x, y, z), y % 4 == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
                }
            }
            // Cold light.
            for (int[] c : new int[][] {{8, 6}, {-9, 4}, {3, -9}, {-4, 10}}) {
                set(l, o.offset(c[0], 1, c[1]), Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState());
                set(l, o.offset(c[0], 2, c[1]), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 14, 4);
        }

        @Override
        public List<Vec3> curseSpots() {
            List<Vec3> out = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                double a = Mth.TWO_PI * i / 6;
                out.add(new Vec3(Math.cos(a) * 7, 1.2, Math.sin(a) * 7 - 2));
            }
            return out;
        }

        @Override
        public boolean fromAbove() {
            return true;
        }

        @Override
        public String entryLine() {
            return "You never hit the ground. The air is thick, and wrong.";
        }
    }

    /** The mine, as the curse remembers it: a low cavern of wet rock, rails that go nowhere, webs. */
    static final class MineRealm implements Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            int hx = 12, hz = 9, h = 7;
            for (int x = -hx - 1; x <= hx + 1; x++) {
                for (int z = -hz - 1; z <= hz + 1; z++) {
                    for (int y = -1; y <= h; y++) {
                        boolean shell = x == -hx - 1 || x == hx + 1 || z == -hz - 1 || z == hz + 1 || y == -1 || y == h;
                        // Uneven walls and ceiling.
                        boolean rough = !shell && (y >= h - 1 - r.nextInt(2)) && r.nextInt(3) == 0;
                        if (shell || rough) set(l, o.offset(x, y, z), r.nextInt(5) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
                    }
                }
            }
            for (int x = -hx; x <= hx; x++) set(l, o.offset(x, 0, 0), Blocks.RAIL.defaultBlockState());
            for (int i = 0; i < 6; i++) {
                int x = -hx + 2 + r.nextInt(2 * hx - 3), z = (r.nextBoolean() ? 1 : -1) * (3 + r.nextInt(hz - 3));
                for (int y = 0; y < h; y++) set(l, o.offset(x, y, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            }
            for (int i = 0; i < 10; i++) set(l, o.offset(-hx + r.nextInt(2 * hx), h - 1, -hz + r.nextInt(2 * hz)), Blocks.COBWEB.defaultBlockState());
            for (int[] c : new int[][] {{-hx, -hz}, {-hx, hz}, {hx, -hz}, {hx, hz}, {0, -hz}, {0, hz}}) {
                set(l, o.offset(c[0], 3, c[1]), Blocks.SOUL_LANTERN.defaultBlockState());
                set(l, o.offset(c[0], 4, c[1]), Blocks.IRON_CHAIN.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(-10, 0.1, 0);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(8, 0.2, 3), new Vec3(9, 0.2, -3), new Vec3(6, 0.2, 0), new Vec3(4, 0.2, 5), new Vec3(4, 0.2, -5));
        }

        @Override
        public int radius() {
            return 13;
        }

        @Override
        public String entryLine() {
            return "The tunnel behind you is gone. Something is scraping in the dark.";
        }
    }

    /**
     * The woods through the lodge's scope, as the curse keeps them: a dark glade ringed by trunks that lean and kink the
     * wrong way, the same hunting stand standing in it again and again, a coarse path that doubles back on itself, a few
     * cold lanterns. Plenty of trunks to break line of sight (the curse uses them; so can you), and open ground between.
     */
    static final class ForestRealm implements Layout {
        static final int R = 22;

        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            BlockState[] floor = {Blocks.PODZOL.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
                    Blocks.PODZOL.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState()};
            for (int x = -R; x <= R; x++) {
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + z * z) + r.nextDouble() * 1.5;
                    if (d > R + 0.5) continue;
                    set(l, o.offset(x, 0, z), floor[r.nextInt(floor.length)]);
                    for (int y = 1; y <= 3; y++) set(l, o.offset(x, -y, z), Blocks.DIRT.defaultBlockState());
                    if (r.nextInt(9) == 0) set(l, o.offset(x, 1, z), r.nextBoolean() ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_DRY_GRASS.defaultBlockState());
                }
            }
            // The crooked path: from where you arrive, kinking back and forth across the glade.
            int px = 0, pz = 16;
            for (int step = 0; step < 60 && pz > -18; step++) {
                set(l, o.offset(px, 0, pz), Blocks.DIRT_PATH.defaultBlockState());
                set(l, o.offset(px + 1, 0, pz), Blocks.COARSE_DIRT.defaultBlockState());
                if (step % 5 == 4) px += (step / 5) % 2 == 0 ? 3 : -4;
                else pz--;
                px = Mth.clamp(px, -12, 12);
            }
            // The hunter's stand, again and again, always facing the path.
            LodgeSite.stand(l, o.offset(-8, 1, 6), net.minecraft.core.Direction.EAST);
            LodgeSite.stand(l, o.offset(7, 1, -3), net.minecraft.core.Direction.WEST);
            LodgeSite.stand(l, o.offset(-6, 1, -12), net.minecraft.core.Direction.EAST);
            // Trunks: a wall of them round the edge, and enough inside to hide behind.
            List<int[]> trunks = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                double a = Mth.TWO_PI * i / 40 + r.nextDouble() * 0.1;
                trunks.add(new int[] {(int) Math.round(Math.cos(a) * (R - 1)), (int) Math.round(Math.sin(a) * (R - 1))});
            }
            for (int tries = 0; tries < 400 && trunks.size() < 40 + 26; tries++) {
                int x = r.nextInt(2 * R - 7) - R + 3, z = r.nextInt(2 * R - 7) - R + 3;
                if (x * x + z * z > (R - 4) * (R - 4)) continue;
                if (Math.abs(x) < 4 && z > 10) continue; // the arrival stays clear
                if (Math.abs(x) < 3 && Math.abs(z + 12) < 3) continue; // and where it waits
                boolean close = false;
                for (int[] t : trunks) if ((t[0] - x) * (t[0] - x) + (t[1] - z) * (t[1] - z) < 16) close = true;
                if (!close) trunks.add(new int[] {x, z});
            }
            for (int[] t : trunks) crooked(l, o, r, t[0], t[1], r.nextInt(5) == 0);
            // Cold light along the path.
            for (int[] c : new int[][] {{3, 12}, {-4, 4}, {5, -6}, {-3, -14}, {9, 8}, {-11, -3}}) {
                set(l, o.offset(c[0], 1, c[1]), Blocks.DARK_OAK_FENCE.defaultBlockState());
                set(l, o.offset(c[0], 2, c[1]), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

        /** A trunk that leans and kinks as it rises (thick ones are 2x2), with a dark crown. */
        private void crooked(ServerLevel l, BlockPos o, RandomSource r, int x, int z, boolean thick) {
            BlockState log = r.nextInt(3) == 0 ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.DARK_OAK_LOG.defaultBlockState();
            int h = 10 + r.nextInt(8);
            int cx = x, cz = z;
            int lx = r.nextInt(3) - 1, lz = r.nextInt(3) - 1;
            for (int y = 1; y <= h; y++) {
                if (y > 3 && y % 3 == 0) {
                    cx += lx;
                    cz += lz;
                    if (r.nextInt(3) == 0) {
                        lx = -lx;
                        lz = r.nextInt(3) - 1;
                    }
                }
                if (cx * cx + cz * cz > R * R) break;
                set(l, o.offset(cx, y, cz), log);
                if (thick) {
                    set(l, o.offset(cx + 1, y, cz), log);
                    set(l, o.offset(cx, y, cz + 1), log);
                    set(l, o.offset(cx + 1, y, cz + 1), log);
                }
            }
            BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++) {
                if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 3 || r.nextInt(4) == 0) continue;
                BlockPos p = o.offset(cx + dx, h + dy, cz + dz);
                if (l.getBlockState(p).isAir()) set(l, p, leaves);
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 1, 16);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(0, 1, -12), new Vec3(-6, 1, -8), new Vec3(6, 1, -10));
        }

        @Override
        public int radius() {
            return 21;
        }

        @Override
        public String entryLine() {
            return "The view through the scope, from the inside. The trees lean wrong, and something is moving between them.";
        }
    }

    /**
     * The pasture after dark, as the curse keeps it: a field of dead grass under no sky, broken fencing in rings that
     * don't close, bones, a lone crooked tree, hay bales gone black. Open ground: the fly heads come from everywhere.
     */
    static final class PastureRealm implements Layout {
        static final int R = 18;

        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            for (int x = -R; x <= R; x++) {
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + z * z) + r.nextDouble() * 1.8;
                    if (d > R) continue;
                    BlockState top = r.nextInt(6) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : r.nextInt(5) == 0 ? Blocks.PODZOL.defaultBlockState()
                            : Blocks.GRASS_BLOCK.defaultBlockState();
                    set(l, o.offset(x, 0, z), top);
                    for (int y = 1; y <= 3; y++) set(l, o.offset(x, -y, z), Blocks.DIRT.defaultBlockState());
                    if (r.nextInt(4) == 0 && d < R - 1) set(l, o.offset(x, 1, z), r.nextBoolean() ? Blocks.SHORT_DRY_GRASS.defaultBlockState() : Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
            // Fences in broken rings.
            for (int ring = 0; ring < 2; ring++) {
                int rad = 7 + ring * 6;
                for (int i = 0; i < rad * 6; i++) {
                    double a = Mth.TWO_PI * i / (rad * 6);
                    if (r.nextInt(3) == 0) continue;
                    BlockPos fp = o.offset((int) Math.round(Math.cos(a) * rad), 1, (int) Math.round(Math.sin(a) * rad));
                    if (l.getBlockState(fp.below()).isAir()) continue;
                    set(l, fp, Blocks.DARK_OAK_FENCE.defaultBlockState());
                }
            }
            for (int i = 0; i < 6; i++) {
                int x = r.nextInt(2 * R - 8) - R + 4, z = r.nextInt(2 * R - 8) - R + 4;
                if (x * x + z * z > (R - 3) * (R - 3)) continue;
                set(l, o.offset(x, 1, z), i % 2 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState());
            }
            // The one tree, dead.
            for (int y = 1; y <= 7; y++) set(l, o.offset(-9, y, -6 + y / 3), Blocks.DARK_OAK_LOG.defaultBlockState());
            for (int[] c : new int[][] {{10, 4}, {-4, 12}, {3, -12}, {-13, 2}}) {
                set(l, o.offset(c[0], 1, c[1]), Blocks.DARK_OAK_FENCE.defaultBlockState());
                set(l, o.offset(c[0], 2, c[1]), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 1, 12);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(0, 1.5, -8), new Vec3(-6, 2, -4), new Vec3(6, 2, -5), new Vec3(-3, 1, -11), new Vec3(4, 1, -10));
        }

        @Override
        public int radius() {
            return 17;
        }

        @Override
        public String entryLine() {
            return "The field goes on, and the night with it. Something is buzzing in the grass.";
        }
    }

    /**
     * The house, from the inside out: its rooms laid end to end and too long, doors that open onto more of the same
     * hall, the bed it happened in at the far end. Close quarters: walls everywhere, corners to be caught in.
     */
    static final class HouseRealm implements Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            int hx = 14, hz = 6, h = 5;
            for (int x = -hx - 1; x <= hx + 1; x++) {
                for (int z = -hz - 1; z <= hz + 1; z++) {
                    set(l, o.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
                    set(l, o.offset(x, 0, z), (x + z) % 2 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    set(l, o.offset(x, h, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    boolean wall = x == -hx - 1 || x == hx + 1 || z == -hz - 1 || z == hz + 1;
                    if (wall) for (int y = 1; y < h; y++) set(l, o.offset(x, y, z), y == 2 && x % 4 == 0 ? Blocks.TINTED_GLASS.defaultBlockState()
                            : Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
                }
            }
            // Partition walls with a doorway each, never in the same place twice.
            for (int x = -hx + 6; x < hx; x += 7) {
                int door = -hz + 1 + r.nextInt(2 * hz - 2);
                for (int z = -hz; z <= hz; z++) for (int y = 1; y < h; y++) {
                    if (Math.abs(z - door) <= 0 && y <= 2) continue;
                    set(l, o.offset(x, y, z), Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
                }
            }
            // Furniture knocked about: chairs (stairs), a table, a cold hearth, cobwebs high.
            for (int i = 0; i < 8; i++) {
                int x = -hx + 1 + r.nextInt(2 * hx - 2), z = -hz + 1 + r.nextInt(2 * hz - 2);
                if (!l.getBlockState(o.offset(x, 1, z)).isAir()) continue;
                set(l, o.offset(x, 1, z), i % 3 == 0 ? Blocks.SPRUCE_FENCE.defaultBlockState() : i % 3 == 1 ? Blocks.SPRUCE_STAIRS.defaultBlockState() : Blocks.BARREL.defaultBlockState());
            }
            for (int i = 0; i < 10; i++) set(l, o.offset(-hx + r.nextInt(2 * hx), h - 1, -hz + r.nextInt(2 * hz)), Blocks.COBWEB.defaultBlockState());
            // The bed at the far end, and a light that barely reaches it.
            set(l, o.offset(hx - 1, 1, 0), Blocks.BED.red().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.WEST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            set(l, o.offset(hx - 2, 1, 0), Blocks.BED.red().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.WEST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
            for (int x = -hx + 3; x < hx; x += 7) set(l, o.offset(x, h - 1, 0), Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(-12, 1, 0);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(10, 1, 2), new Vec3(10, 1, -2), new Vec3(3, 1, 3), new Vec3(4, 1, -3));
        }

        @Override
        public int radius() {
            return 15;
        }

        @Override
        public String entryLine() {
            return "You never fall asleep. The hall goes on further than the house did.";
        }
    }

    /** Any other place, hollowed out: a ragged island of grey stone in the red dark, a few broken pillars. */
    static final class HollowRealm implements Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            for (int x = -13; x <= 13; x++) {
                for (int z = -13; z <= 13; z++) {
                    double d = Math.sqrt(x * x + z * z) + r.nextDouble() * 2;
                    if (d > 13) continue;
                    for (int y = 0; y < 3; y++) set(l, o.offset(x, -y, z), y == 0 ? (r.nextInt(5) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState())
                            : Blocks.DEEPSLATE.defaultBlockState());
                }
            }
            for (int i = 0; i < 6; i++) {
                double a = Mth.TWO_PI * i / 6;
                int x = (int) Math.round(Math.cos(a) * 9), z = (int) Math.round(Math.sin(a) * 9);
                int h = 2 + r.nextInt(5);
                for (int y = 1; y <= h; y++) set(l, o.offset(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                if (i % 2 == 0) set(l, o.offset(x, h + 1, z), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 1, 8);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(0, 1, -6), new Vec3(-5, 1, -3), new Vec3(5, 1, -3), new Vec3(-3, 1, -8), new Vec3(3, 1, -8));
        }

        @Override
        public int radius() {
            return 12;
        }

        @Override
        public String entryLine() {
            return "The place folds in on itself. You are somewhere it keeps.";
        }
    }

    /**
     * The Finger Bearer's realm: the battle room's seal, laid bare in a round hall of black stone, chains hanging from
     * nothing, the seal itself in the middle where it takes shape. Room enough for its leaps and pools.
     */
    static final class BattleRealm implements Layout {
        static final int R = 14;

        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            for (int x = -R - 1; x <= R + 1; x++) {
                for (int z = -R - 1; z <= R + 1; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > R + 1) continue;
                    set(l, o.offset(x, -1, z), Blocks.DEEPSLATE.defaultBlockState());
                    BlockState floor = d < 2.5 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                            : (int) d % 4 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                            : r.nextInt(6) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    set(l, o.offset(x, 0, z), floor);
                    if (d > R) for (int y = 1; y <= 7; y++) set(l, o.offset(x, y, z), y == 7 ? Blocks.BLACKSTONE_WALL.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                }
            }
            for (int i = 0; i < 8; i++) {
                double a = Mth.TWO_PI * i / 8;
                int x = (int) Math.round(Math.cos(a) * (R - 2)), z = (int) Math.round(Math.sin(a) * (R - 2));
                for (int y = 1; y <= 6; y++) set(l, o.offset(x, y, z), Blocks.POLISHED_BASALT.defaultBlockState());
                set(l, o.offset(x, 7, z), Blocks.SOUL_LANTERN.defaultBlockState());
                for (int y = 9; y <= 12; y++) set(l, o.offset(x / 2, y, z / 2), Blocks.IRON_CHAIN.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 1, 10);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(0, 1, 0));
        }

        @Override
        public int radius() {
            return R - 1;
        }

        @Override
        public String entryLine() {
            return "The seal opens under you. Something has been waiting a long time.";
        }
    }
}
