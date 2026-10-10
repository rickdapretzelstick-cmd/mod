package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything investigations remember, in {@code <world>/jjk_progression/investigations.dat} (an atomic replace on every
 * save, like the kit registry): the villages that have a news board and what is pinned to it, every incident, the
 * cursed-realm arenas in use, and where each player inside a realm came from (so a crash, a disconnect or a lost arena
 * always sends them home). Only the server thread touches it.
 */
public final class InvestigationState {
    private static final LevelResource DIR = new LevelResource("jjk_progression");
    private static final String FILE = "investigations.dat";

    /** A village with a news board: its bell, its board, and the incidents reported there (newest last). */
    public static final class Village {
        public final String dimension;
        public final BlockPos bell;
        @Nullable BlockPos board;
        int boardTries;
        final List<String> incidents = new ArrayList<>();
        long lastGenerated;
        /**
         * The character storyline this village holds (a character id, or "" for none), decided once from the village
         * itself and the world, never from who visits ({@link StoryChains}); and how far along it is: 1-4 the event now
         * reported, 5 finished.
         */
        String story = "";
        boolean storyDecided;
        int storyStage;

        Village(String dimension, BlockPos bell) {
            this.dimension = dimension;
            this.bell = bell.immutable();
        }

        @Nullable
        public BlockPos board() {
            return board;
        }

        public List<String> incidents() {
            return List.copyOf(incidents);
        }

        public String story() {
            return story;
        }

        public int storyStage() {
            return storyStage;
        }

        public void setBoardForTest(BlockPos at) {
            board = at.immutable();
        }
    }

    /** A cursed-realm arena in use: its slot in the realm dimension, its incident, and who is in it. */
    public static final class Arena {
        public final int slot;
        public final String incident;
        public final String layout;
        public final BlockPos origin;
        final Set<UUID> inside = new LinkedHashSet<>();
        long openedAt;
        /** Ticks since nobody was inside (the arena is abandoned after a grace period). */
        int emptyTicks;
        /** Ticks since the last curse fell (the walk home waits a moment). */
        int wonTicks = -1;

        Arena(int slot, String incident, String layout, BlockPos origin) {
            this.slot = slot;
            this.incident = incident;
            this.layout = layout;
            this.origin = origin.immutable();
        }

        public Set<UUID> inside() {
            return Set.copyOf(inside);
        }
    }

    /** Where a player in a realm goes back to. */
    public record Return(String dimension, Vec3 pos, float yaw) {}

    @Nullable private static InvestigationState instance;
    private final MinecraftServer server;

    final Map<Long, Village> villages = new LinkedHashMap<>();
    final Map<String, Incident> incidents = new LinkedHashMap<>();
    final Map<Integer, Arena> arenas = new LinkedHashMap<>();
    final Map<UUID, Return> returns = new LinkedHashMap<>();
    /** Every Cursed Rifle issued and still live: its claim id → whose it is (a recovered rifle replaces its old id). */
    final Map<String, UUID> rifleClaims = new LinkedHashMap<>();
    int nextId;
    private boolean dirty;

    private InvestigationState(MinecraftServer server) {
        this.server = server;
    }

    public static InvestigationState get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new InvestigationState(server);
            instance.load();
        }
        return instance;
    }

    public static void unload() {
        instance = null;
    }

    public Map<String, Incident> incidents() {
        return java.util.Collections.unmodifiableMap(incidents);
    }

    public Map<Long, Village> villages() {
        return java.util.Collections.unmodifiableMap(villages);
    }

    public Map<Integer, Arena> arenas() {
        return java.util.Collections.unmodifiableMap(arenas);
    }

    @Nullable
    public Return returnOf(UUID player) {
        return returns.get(player);
    }

    /** Rifle claims: issuing one retires the owner's previous id (see RifleClaims). */
    public static final class Claims {
        private Claims() {}

        public static void issue(InvestigationState st, @Nullable String old, String id, UUID owner) {
            if (old != null) st.rifleClaims.remove(old);
            // Every earlier claim of theirs goes, not just the one their player data remembers (it may have rolled back).
            st.rifleClaims.values().removeIf(owner::equals);
            // One rifle per world: issuing a claim retires anyone else's.
            st.rifleClaims.clear();
            st.rifleClaims.put(id, owner);
            st.save();
        }

        /** Tests: forget every rifle claim (tests share one world). */
        public static void clearForTest(InvestigationState st) {
            st.rifleClaims.clear();
        }
    }

    public Map<String, UUID> rifleClaims() {
        return java.util.Collections.unmodifiableMap(rifleClaims);
    }

    @Nullable
    public Incident incident(String id) {
        return incidents.get(id);
    }

    String newId() {
        return Integer.toString(++nextId, 36) + Long.toString(server.overworld().getGameTime() % 46656, 36);
    }

    void markDirty() {
        dirty = true;
    }

    /** Test hook: forces the next flush to write. */
    public void markDirtyForTest() {
        dirty = true;
    }

    /** Writes if anything changed. */
    public void flush() {
        if (dirty) save();
    }

    // --- Persistence ---

    private Path file() {
        return server.getWorldPath(DIR).resolve(FILE);
    }

    boolean save() {
        dirty = false;
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            CompoundTag t = new CompoundTag();
            t.putInt("Version", 1);
            t.putInt("NextId", nextId);
            ListTag vs = new ListTag();
            for (Village v : villages.values()) {
                CompoundTag c = new CompoundTag();
                c.putString("Dimension", v.dimension);
                c.putLong("Bell", v.bell.asLong());
                if (v.board != null) c.putLong("Board", v.board.asLong());
                c.putInt("BoardTries", v.boardTries);
                c.putLong("LastGenerated", v.lastGenerated);
                c.putString("Story", v.story);
                c.putBoolean("StoryDecided", v.storyDecided);
                c.putInt("StoryStage", v.storyStage);
                ListTag ids = new ListTag();
                for (String s : v.incidents) ids.add(StringTag.valueOf(s));
                c.put("Incidents", ids);
                vs.add(c);
            }
            t.put("Villages", vs);
            ListTag is = new ListTag();
            for (Incident i : incidents.values()) is.add(i.save());
            t.put("IncidentList", is);
            ListTag as = new ListTag();
            for (Arena a : arenas.values()) {
                CompoundTag c = new CompoundTag();
                c.putInt("Slot", a.slot);
                c.putString("Incident", a.incident);
                c.putString("Layout", a.layout);
                c.putLong("Origin", a.origin.asLong());
                as.add(c);
            }
            t.put("Arenas", as);
            ListTag rs = new ListTag();
            for (Map.Entry<UUID, Return> e : returns.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("Player", e.getKey().toString());
                c.putString("Dimension", e.getValue().dimension());
                c.putDouble("X", e.getValue().pos().x);
                c.putDouble("Y", e.getValue().pos().y);
                c.putDouble("Z", e.getValue().pos().z);
                c.putFloat("Yaw", e.getValue().yaw());
                rs.add(c);
            }
            t.put("Returns", rs);
            ListTag rc = new ListTag();
            for (Map.Entry<String, UUID> e : rifleClaims.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("Claim", e.getKey());
                c.putString("Owner", e.getValue().toString());
                rc.add(c);
            }
            t.put("RifleClaims", rc);
            Path tmp = f.resolveSibling(FILE + ".tmp");
            NbtIo.writeCompressed(t, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            JJK.LOGGER.error("Could not save investigations to {}", f, e);
            return false;
        }
    }

    private void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            CompoundTag t = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            nextId = t.getIntOr("NextId", 0);
            for (Tag x : t.getListOrEmpty("Villages")) {
                if (!(x instanceof CompoundTag c)) continue;
                Village v = new Village(c.getStringOr("Dimension", "minecraft:overworld"), BlockPos.of(c.getLongOr("Bell", 0L)));
                if (c.contains("Board")) v.board = BlockPos.of(c.getLongOr("Board", 0L));
                v.boardTries = c.getIntOr("BoardTries", 0);
                v.lastGenerated = c.getLongOr("LastGenerated", 0L);
                v.story = c.getStringOr("Story", "");
                v.storyDecided = c.getBooleanOr("StoryDecided", false);
                v.storyStage = c.getIntOr("StoryStage", 0);
                for (Tag s : c.getListOrEmpty("Incidents")) if (s instanceof StringTag st) v.incidents.add(st.value());
                villages.put(v.bell.asLong(), v);
            }
            for (Tag x : t.getListOrEmpty("IncidentList")) {
                if (!(x instanceof CompoundTag c)) continue;
                Incident i = Incident.load(c);
                if (i != null) incidents.put(i.id, i);
            }
            for (Tag x : t.getListOrEmpty("Arenas")) {
                if (!(x instanceof CompoundTag c)) continue;
                Arena a = new Arena(c.getIntOr("Slot", 0), c.getStringOr("Incident", ""), c.getStringOr("Layout", ""), BlockPos.of(c.getLongOr("Origin", 0L)));
                arenas.put(a.slot, a);
            }
            for (Tag x : t.getListOrEmpty("Returns")) {
                if (!(x instanceof CompoundTag c)) continue;
                try {
                    returns.put(UUID.fromString(c.getStringOr("Player", "")), new Return(c.getStringOr("Dimension", "minecraft:overworld"),
                            new Vec3(c.getDoubleOr("X", 0), c.getDoubleOr("Y", 64), c.getDoubleOr("Z", 0)), c.getFloatOr("Yaw", 0f)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            for (Tag x : t.getListOrEmpty("RifleClaims")) {
                if (!(x instanceof CompoundTag c)) continue;
                try {
                    rifleClaims.put(c.getStringOr("Claim", ""), UUID.fromString(c.getStringOr("Owner", "")));
                } catch (IllegalArgumentException ignored) {
                }
            }
            JJK.LOGGER.info("Loaded investigations: {} villages, {} incidents, {} arenas", villages.size(), incidents.size(), arenas.size());
        } catch (Exception e) {
            Path backup = f.resolveSibling(FILE + ".corrupt-" + System.currentTimeMillis());
            JJK.LOGGER.error("Could not read investigations {}; kept a copy at {}", f, backup, e);
            try {
                Files.copy(f, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }
}
