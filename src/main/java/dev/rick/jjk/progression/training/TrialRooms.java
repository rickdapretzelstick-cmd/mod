package dev.rick.jjk.progression.training;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rick.jjk.JJK;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The relic-trial rooms: one per character, built from the "Relic Trial Rooms V2" Blockbench pack by
 * {@code tools/install_trial_rooms.py}. A room is two things: a block plan ({@code data/jjk/trial_rooms/<kit>.json}: the
 * architecture that lines up with the grid, the floor, the lantern lights) placed when the trial's realm arena opens,
 * and its prop models (everything finer), drawn by a few {@link TrialPropEntity} per session.
 *
 * <p>Plan coordinates are room-local blocks from the arena origin's corner, the participant facing +Z; markers name the
 * places a scene uses (where the participant stands or sits, the television, the Blue, Rika's dais, the target...).
 * Everything lies inside the realm arena's cleared box, so closing the arena takes the whole room with it.
 */
public final class TrialRooms {
    public static final List<String> KITS = List.of("yuji", "gojo", "yuta", "ryu", "hakari");

    /**
     * A prop model and how it is drawn ({@link TrialPropEntity}'s kinds); where it stands (room-local, null: the room's
     * origin corner, its models drawn in room coordinates), which way it faces and its presentation scale.
     */
    public record Prop(String model, int kind, @Nullable Vec3 at, float yaw, float scale) {}

    public record Plan(String kit, String title, String line, String biome, int[] bounds, Map<String, Vec3> markers, List<Vec3> spots,
                       List<BlockState> palette, int[][] runs, int[][] lights, List<Prop> props) {
        /** A marker (room-local), or null if the room has no such place. */
        @Nullable
        public Vec3 marker(String name) {
            return markers.get(name);
        }

        /** Every block the plan places (room-local position to state). */
        public Map<BlockPos, BlockState> blocks() {
            Map<BlockPos, BlockState> out = new HashMap<>();
            for (int[] r : runs) for (int x = r[0]; x < r[3]; x++) out.put(new BlockPos(x, r[1], r[2]), palette.get(r[4]));
            return out;
        }
    }

    /** Loaded plans by classpath path (trial rooms and realm rooms alike). */
    private static final Map<String, Plan> PLANS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<>();

    private TrialRooms() {}

    /** The kit's room, or null if its plan is missing or broken (the caller falls back to a bare safe floor). */
    @Nullable
    public static Plan plan(String kit) {
        return cached("data/jjk/trial_rooms/" + kit + ".json", kit);
    }

    /** A room plan at {@code path} on the classpath (a trial room's or a realm's), loaded once; null if missing or broken. */
    @Nullable
    public static Plan cached(String path, String key) {
        Plan p = PLANS.get(path);
        if (p != null || MISSING.containsKey(path)) return p;
        p = load(path, key);
        if (p == null) MISSING.put(path, true);
        else PLANS.put(path, p);
        return p;
    }

    @Nullable
    private static Plan load(String path, String key) {
        try (InputStream in = TrialRooms.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                JJK.LOGGER.error("[rooms] no room plan at {}", path);
                return null;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<BlockState> palette = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("palette")) palette.add(state(e.getAsString()));
            Map<String, Vec3> markers = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("markers").entrySet()) markers.put(e.getKey(), vec(e.getValue().getAsJsonArray()));
            List<Vec3> spots = new ArrayList<>();
            if (o.has("spots")) for (JsonElement e : o.getAsJsonArray("spots")) spots.add(vec(e.getAsJsonArray()));
            List<Prop> props = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("props")) {
                JsonObject po = e.getAsJsonObject();
                props.add(new Prop(po.get("model").getAsString(), TrialPropEntity.kind(po.get("kind").getAsString()),
                        po.has("at") ? vec(po.getAsJsonArray("at")) : null, po.has("yaw") ? po.get("yaw").getAsFloat() : 0f,
                        po.has("scale") ? po.get("scale").getAsFloat() : 1f));
            }
            Plan p = new Plan(key, o.get("title").getAsString(), o.has("line") ? o.get("line").getAsString() : "", o.has("biome") ? o.get("biome").getAsString() : "",
                    ints(o.getAsJsonArray("bounds")), markers, spots, palette, table(o.getAsJsonArray("runs")), table(o.getAsJsonArray("lights")), props);
            if (p.marker("player") == null && p.marker("arrival") == null) throw new IllegalStateException("no player or arrival marker");
            for (int[] r : p.runs) if (r.length != 5 || r[4] < 0 || r[4] >= palette.size()) throw new IllegalStateException("bad run");
            return p;
        } catch (Exception e) {
            JJK.LOGGER.error("[rooms] could not read the room plan {}", path, e);
            return null;
        }
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    /** Raises a room's props (once; the caller owns them), each where the plan puts it. */
    public static List<TrialPropEntity> spawnProps(ServerLevel level, BlockPos origin, Plan p) {
        List<TrialPropEntity> out = new ArrayList<>();
        for (Prop pr : p.props()) {
            Vec3 local = pr.at() != null ? pr.at() : Vec3.ZERO;
            TrialPropEntity e = TrialPropEntity.create(level, pr.model(), pr.kind(), new Vec3(origin.getX() + local.x, origin.getY() + local.y, origin.getZ() + local.z));
            e.setYRot(pr.yaw());
            if (pr.kind() == TrialPropEntity.RIKA) e.setParam(pr.scale());
            level.addFreshEntity(e);
            out.add(e);
        }
        return out;
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    private static int[][] table(JsonArray a) {
        int[][] out = new int[a.size()][];
        for (int i = 0; i < out.length; i++) out[i] = ints(a.get(i).getAsJsonArray());
        return out;
    }

    /** "namespace:id" or "namespace:id[key=value,...]". */
    static BlockState state(String s) {
        int b = s.indexOf('[');
        Identifier id = Identifier.parse(b < 0 ? s : s.substring(0, b));
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == Blocks.AIR && !id.getPath().equals("air")) throw new IllegalArgumentException("unknown block " + id);
        BlockState st = block.defaultBlockState();
        if (b >= 0) {
            for (String kv : s.substring(b + 1, s.length() - 1).split(",")) {
                String[] p = kv.split("=");
                Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                if (prop == null) throw new IllegalArgumentException("no property " + p[0] + " on " + id);
                st = with(st, prop, p[1].trim());
            }
        }
        return st;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState st, Property<T> prop, String value) {
        return st.setValue(prop, prop.getValue(value).orElseThrow(() -> new IllegalArgumentException("bad value " + value + " for " + prop.getName())));
    }

    /** Biomes the rooms paint (each sets its own light colour and tint over the realm's crimson: see worldgen/biome). */
    public static boolean isTrialBiome(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> b) {
        return b.unwrapKey().map(k -> k.identifier().getNamespace().equals(JJK.MOD_ID)
                && (k.identifier().getPath().startsWith("trial_") || k.identifier().getPath().startsWith("realm_"))).orElse(false);
    }

    /**
     * Gives the room's box its own biome, so the realm's crimson ambient light gives way to the room's own (warm, cool,
     * purple...). Only in the realm dimension (never the overworld fallback's sky), and undone when the arena is cleared.
     */
    public static void paintBiome(ServerLevel level, BlockPos origin, Plan p) {
        if (p.biome().isEmpty() || level.dimension() != dev.rick.jjk.progression.investigation.CursedRealms.DIMENSION) return;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, Identifier.parse(p.biome()));
        var biome = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).get(key);
        if (biome.isEmpty()) {
            JJK.LOGGER.warn("[training] no biome {}", p.biome());
            return;
        }
        int[] b = p.bounds();
        fillBiome(level, origin.offset(b[0], b[1], b[2]), origin.offset(b[3], b[4], b[5]), biome.get(), x -> true);
    }

    /**
     * Sets the biome of every loaded chunk cell in a box (where {@code only} accepts the biome there now) and sends the
     * change to clients: what /fillbiome does, without its command-sized volume cap (a realm room is bigger), and only
     * ever on a room's own box.
     */
    public static void fillBiome(ServerLevel level, BlockPos from, BlockPos to, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
                                 java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> only) {
        int x0 = Math.min(from.getX(), to.getX()), y0 = Math.min(from.getY(), to.getY()), z0 = Math.min(from.getZ(), to.getZ());
        int x1 = Math.max(from.getX(), to.getX()), y1 = Math.max(from.getY(), to.getY()), z1 = Math.max(from.getZ(), to.getZ());
        List<net.minecraft.world.level.chunk.ChunkAccess> changed = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (chunk == null) continue;
                chunk.fillBiomesFromNoise((qx, qy, qz) -> {
                    int bx = net.minecraft.core.QuartPos.toBlock(qx), by = net.minecraft.core.QuartPos.toBlock(qy), bz = net.minecraft.core.QuartPos.toBlock(qz);
                    var now = chunk.getNoiseBiome(qx, qy, qz);
                    boolean in = bx + 3 >= x0 && bx <= x1 && by + 3 >= y0 && by <= y1 && bz + 3 >= z0 && bz <= z1;
                    return in && only.test(now) ? biome : now;
                });
                chunk.markUnsaved();
                changed.add(chunk);
            }
        }
        if (!changed.isEmpty()) level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
    }

    /** Places a room's blocks and lights at {@code origin} (its arena origin). */
    public static void build(ServerLevel level, BlockPos origin, Plan p) {
        paintBiome(level, origin, p);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int[] r : p.runs) {
            BlockState s = p.palette.get(r[4]);
            for (int x = r[0]; x < r[3]; x++) set(level, m.set(origin.getX() + x, origin.getY() + r[1], origin.getZ() + r[2]), s);
        }
        for (int[] l : p.lights) {
            m.set(origin.getX() + l[0], origin.getY() + l[1], origin.getZ() + l[2]);
            if (level.getBlockState(m).isAir()) set(level, m, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Math.max(1, Math.min(15, l[3]))));
        }
    }

    /**
     * The bare fallback (a plan that failed to load): a dark floor under the participant's spot, so the trial (and its
     * way out) still works.
     */
    public static void buildFallback(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                set(level, m.set(origin.getX() + x, origin.getY() - 1, origin.getZ() + z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        for (int x = -6; x <= 6; x += 6) {
            for (int z = -6; z <= 6; z += 6) set(level, m.set(origin.getX() + x, origin.getY() + 4, origin.getZ() + z), Blocks.LIGHT.defaultBlockState());
        }
    }

    static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
