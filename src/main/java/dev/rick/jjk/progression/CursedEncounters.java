package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cursed battle rooms: where they are, what waits in each, and how far each encounter has got. Rooms are generated under
 * Woodland Mansions and Igloos ({@link dev.rick.jjk.progression.worldgen.CursedBattleRooms}); each has a seal that
 * registers it here the first time it ticks. The registry is saved in {@code <world>/jjk_progression/encounters.dat}.
 *
 * <p>An encounter is code registered under an id ({@link #registerEncounter}): it is handed the room and the players in
 * it every couple of seconds and decides when to spawn its curse, and marks the room ACTIVE or CLEARED. Every room waits
 * for the Finger Bearer ({@link dev.rick.jjk.progression.curse.FingerBearerEncounter}).
 */
public final class CursedEncounters {
    /** The structures battle rooms are built under (the seal's {@code site} property indexes this). */
    public static final List<String> SITES = List.of("woodland_mansion", "igloo");
    /** What every room currently waits for. */
    public static final String FINGER_BEARER = "finger_bearer";

    public enum State { DORMANT, ACTIVE, CLEARED }

    /** One room. {@code radius} is how far from the seal counts as inside it. */
    public static final class Room {
        public final String dimension;
        public final BlockPos seal;
        public final String site;
        public final String encounter;
        public final int radius;
        public State state;
        /** Game time someone first entered it (-1: undiscovered). */
        public long discoveredAt;
        /** The curse this room spawned (saved), while the encounter is ACTIVE. */
        @Nullable public UUID spirit;
        /** Checks in a row the spirit wasn't found in the world (not saved: its chunk may simply still be loading). */
        public int missing;

        Room(String dimension, BlockPos seal, String site, String encounter, int radius, State state, long discoveredAt) {
            this.dimension = dimension;
            this.seal = seal;
            this.site = site;
            this.encounter = encounter;
            this.radius = radius;
            this.state = state;
            this.discoveredAt = discoveredAt;
        }
    }

    /** An encounter's behaviour: given the room and who is inside, every couple of seconds. */
    @FunctionalInterface
    public interface Encounter {
        void tick(ServerLevel level, Room room, List<ServerPlayer> inside);
    }

    private static final LevelResource DIR = new LevelResource("jjk_progression");
    private static final String FILE = "encounters.dat";
    private static final Map<String, Encounter> ENCOUNTERS = new HashMap<>();
    private static final Map<String, Room> ROOMS = new LinkedHashMap<>();
    @Nullable private static MinecraftServer loadedFor;

    private CursedEncounters() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ROOMS.clear();
            loadedFor = null;
        });
    }

    /** Forgets the registry in memory; the next access reads it back from disk (a reload, for tests). */
    public static void unload() {
        ROOMS.clear();
        loadedFor = null;
    }

    /** Registers what happens in rooms that wait for {@code id} (the Finger Bearer will register itself here). */
    public static void registerEncounter(String id, Encounter encounter) {
        ENCOUNTERS.put(id, encounter);
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (loadedFor == server) return;
        ROOMS.clear();
        loadedFor = server;
        load(server);
    }

    /** A seal ticked: make sure its room is known, note who is in it, and run its encounter if one is registered. */
    public static void tickSeal(ServerLevel level, BlockPos seal, String site) {
        MinecraftServer server = level.getServer();
        ensureLoaded(server);
        String dim = level.dimension().identifier().toString();
        String k = key(dim, seal);
        Room room = ROOMS.get(k);
        if (room == null) {
            room = new Room(dim, seal.immutable(), site, FINGER_BEARER, 9, State.DORMANT, -1);
            ROOMS.put(k, room);
            save(server);
            JJK.LOGGER.info("Cursed battle room under a {} registered at {} {}", site, dim, seal.toShortString());
        }
        double r = room.radius;
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(seal.getX() + 0.5, seal.getY() + 1, seal.getZ() + 0.5) <= r * r) inside.add(p);
        }
        if (!inside.isEmpty() && room.discoveredAt < 0) {
            room.discoveredAt = level.getGameTime();
            save(server);
        }
        Encounter e = ENCOUNTERS.get(room.encounter);
        if (e != null && room.state != State.CLEARED) e.tick(level, room, inside);
    }

    public static void setState(MinecraftServer server, Room room, State state) {
        ensureLoaded(server);
        room.state = state;
        save(server);
    }

    /** Saves the registry after a room's fields changed (its spirit, say). */
    public static void changed(MinecraftServer server) {
        ensureLoaded(server);
        save(server);
    }

    /** The room whose seal is at {@code seal} in {@code dimension}, or null. */
    @Nullable
    public static Room find(MinecraftServer server, String dimension, BlockPos seal) {
        ensureLoaded(server);
        return ROOMS.get(key(dimension, seal));
    }

    public static Collection<Room> rooms(MinecraftServer server) {
        ensureLoaded(server);
        return List.copyOf(ROOMS.values());
    }

    // --- Persistence ---

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(DIR).resolve(FILE);
    }

    private static void save(MinecraftServer server) {
        Path f = file(server);
        try {
            Files.createDirectories(f.getParent());
            ListTag list = new ListTag();
            for (Room r : ROOMS.values()) {
                CompoundTag t = new CompoundTag();
                t.putString("Dim", r.dimension);
                t.putIntArray("Seal", new int[] {r.seal.getX(), r.seal.getY(), r.seal.getZ()});
                t.putString("Site", r.site);
                t.putString("Encounter", r.encounter);
                t.putInt("Radius", r.radius);
                t.putString("State", r.state.name());
                t.putLong("Discovered", r.discoveredAt);
                if (r.spirit != null) t.putString("Spirit", r.spirit.toString());
                list.add(t);
            }
            CompoundTag root = new CompoundTag();
            root.put("Rooms", list);
            Path tmp = f.resolveSibling(FILE + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            JJK.LOGGER.error("Could not save cursed encounters to {}", f, e);
        }
    }

    private static void load(MinecraftServer server) {
        Path f = file(server);
        if (!Files.exists(f)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            for (net.minecraft.nbt.Tag tag : root.getListOrEmpty("Rooms")) {
                if (!(tag instanceof CompoundTag t)) continue;
                int[] p = t.getIntArray("Seal").orElse(new int[0]);
                if (p.length != 3) continue;
                State state;
                try {
                    state = State.valueOf(t.getStringOr("State", "DORMANT"));
                } catch (IllegalArgumentException ex) {
                    state = State.DORMANT;
                }
                String dim = t.getStringOr("Dim", "minecraft:overworld");
                BlockPos pos = new BlockPos(p[0], p[1], p[2]);
                Room room = new Room(dim, pos, t.getStringOr("Site", "?"), t.getStringOr("Encounter", FINGER_BEARER),
                        t.getIntOr("Radius", 9), state, t.getLongOr("Discovered", -1L));
                try {
                    String spirit = t.getStringOr("Spirit", "");
                    if (!spirit.isEmpty()) room.spirit = UUID.fromString(spirit);
                } catch (IllegalArgumentException ignored) {
                    // A damaged id: the encounter spawns its curse again.
                }
                ROOMS.put(key(dim, pos), room);
            }
        } catch (Exception e) {
            JJK.LOGGER.error("Could not read cursed encounters {}", f, e);
        }
    }
}
