package dev.rick.jjk.core.world;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.domain.structure.DomainStructures;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Temporary battle damage. Every block a technique destroys or alters is recorded with its exact state and
 * block-entity data and put back {@link JJKConfig.Restoration#delayTicks} after <i>that block</i> was damaged.
 *
 * <ul>
 *   <li><b>What is recorded.</b> The block a technique destroys, plus everything that changes as a direct result: in
 *   the same instant (torches, flowers, rails, door halves, redstone, fence connections, chest halves) and in the
 *   following seconds (falling sand, sugar cane, water flowing into the hole) — neighbours of every change are watched
 *   for a short time. Paintings and item frames hanging on destroyed blocks are recorded as entities.</li>
 *   <li><b>First damage wins.</b> A position damaged again keeps its original snapshot; restoration never brings back an
 *   intermediate state.</li>
 *   <li><b>Dependency order.</b> Due blocks restore solid blocks first, then blocks that hang off them (bottom up),
 *   then block-entity data, then hanging entities; a dependent whose support is still pending waits for it.</li>
 *   <li><b>No duplication.</b> Tracked destruction never drops items: containers are emptied into the snapshot before
 *   the block is removed, cascaded drops and falling-block entities are suppressed. A block that ends up not being
 *   restored (conflict policy) is handed back as drops once instead.</li>
 *   <li><b>Conflicts.</b> If something else changed a damaged position before it was due (a player built there), the
 *   "preserve" policy keeps that change.</li>
 *   <li><b>Performance.</b> Only touched positions are tracked; due blocks sit in a time-ordered queue, restore in
 *   capped batches, and wait for their chunk to be loaded.</li>
 *   <li><b>Restarts.</b> Pending restorations are persisted per dimension and resume after a restart.</li>
 * </ul>
 *
 * Domain structures are separate ({@link DomainStructures}): they restore when their domain ends. A position held by a
 * domain structure is simply postponed until the domain has given it back.
 */
public final class WorldRestoration {
    private static final LevelResource DIR = new LevelResource("jjk_restoration");
    private static final Map<ServerLevel, Store> STORES = new IdentityHashMap<>();

    // What is happening right now on the server thread.
    @Nullable private static ServerLevel scopeLevel;
    private static int scopeDepth;
    private static String scopeSource = "";
    private static boolean scopeHoldsDrops;
    private static int suspended;
    private static int playerBreaking;

    private WorldRestoration() {}

    /** One damaged position. */
    public static final class Record {
        public final long pos;
        public final BlockState original;
        @Nullable public final CompoundTag blockEntity;
        public final long damagedAt;
        public final String source;
        long restoreAt;
        /** What the damage left here (compared at restore time to detect other changes). */
        BlockState left;
        /** The original's drops were held back, so if it is not restored it is handed back as drops. */
        final boolean dropsHeld;

        Record(long pos, BlockState original, @Nullable CompoundTag blockEntity, long damagedAt, String source, boolean dropsHeld) {
            this.pos = pos;
            this.original = original;
            this.blockEntity = blockEntity;
            this.damagedAt = damagedAt;
            this.source = source;
            this.left = original;
            this.dropsHeld = dropsHeld;
        }

        public long restoreAt() {
            return restoreAt;
        }
    }

    /** A painting or item frame that hung on a destroyed block. */
    static final class HangingRecord {
        final CompoundTag data;
        final AABB support;
        final UUID uuid;
        long restoreAt;

        HangingRecord(CompoundTag data, AABB support, UUID uuid, long restoreAt) {
            this.data = data;
            this.support = support;
            this.uuid = uuid;
            this.restoreAt = restoreAt;
        }
    }

    private record Watch(long expires, int generation, String source) {}

    static final class Store {
        final ServerLevel level;
        final Long2ObjectOpenHashMap<Record> records = new Long2ObjectOpenHashMap<>();
        /** Restore tick → positions due then. Entries whose record was rescheduled are skipped when popped. */
        final TreeMap<Long, LongArrayList> due = new TreeMap<>();
        final List<HangingRecord> hanging = new ArrayList<>();
        final Long2ObjectOpenHashMap<Watch> watches = new Long2ObjectOpenHashMap<>();
        /** Due positions whose chunk is unloaded, by chunk. */
        final Long2ObjectOpenHashMap<LongArrayList> waitingChunks = new Long2ObjectOpenHashMap<>();
        boolean dirty;
        boolean urgent;
        long lastSave;

        Store(ServerLevel level) {
            this.level = level;
        }
    }

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(WorldRestoration::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(WorldRestoration::loadAll);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STORES.clear());
        // A player breaking blocks is legitimate gameplay: never mistaken for technique damage, even next to a crater.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            playerBreaking++;
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> playerBreaking = Math.max(0, playerBreaking - 1));
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, be) -> playerBreaking = Math.max(0, playerBreaking - 1));
    }

    public static boolean enabled() {
        return JJKConfig.get().restoration.enabled;
    }

    private static JJKConfig.Restoration cfg() {
        return JJKConfig.get().restoration;
    }

    static Store store(ServerLevel level) {
        return STORES.computeIfAbsent(level, Store::new);
    }

    // --- Recording ---

    /**
     * Destroys a block as technique damage: records it (and whatever hangs off it), keeps container contents in the
     * snapshot instead of spilling them, and removes it without drops.
     */
    public static boolean destroy(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker, String source) {
        Store st = store(level);
        long now = level.getGameTime();
        boolean fresh = !st.records.containsKey(pos.asLong());
        // A block placed into a crater before it was restored is not part of the original world: it breaks normally.
        record(st, pos, state, source, now, fresh);
        if (cfg().restoreHangingEntities) captureHanging(st, pos, now);
        if (fresh && state.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Clearable c) c.clearContent();
        }
        beginScope(level, source, fresh);
        try {
            return level.destroyBlock(pos, !fresh && JJKConfig.get().general.destroyedBlocksDropItems, breaker, 512);
        } finally {
            endScope();
        }
    }

    private static Record record(Store st, BlockPos pos, BlockState old, String source, long now, boolean dropsHeld) {
        long p = pos.asLong();
        Record r = st.records.get(p);
        if (r != null) {
            if (cfg().rearmOnRepeatDamage) schedule(st, r, now + cfg().delayTicks);
            return r;
        }
        r = new Record(p, old, BlockSnapshots.blockEntity(st.level, pos, old), now, source, dropsHeld);
        st.records.put(p, r);
        schedule(st, r, now + cfg().delayTicks);
        st.dirty = true;
        // Snapshots holding items are written straight away so a crash can't lose them.
        if (r.blockEntity != null) st.urgent = true;
        return r;
    }

    private static void schedule(Store st, Record r, long at) {
        r.restoreAt = at;
        st.due.computeIfAbsent(at, k -> new LongArrayList()).add(r.pos);
        st.dirty = true;
    }

    private static void watchAround(Store st, BlockPos pos, int generation, String source, long now) {
        long expires = now + cfg().cascadeWatchTicks;
        for (Direction d : BlockSnapshots.DIRECTIONS) {
            long n = pos.relative(d).asLong();
            Watch w = st.watches.get(n);
            if (w == null || w.expires < now || w.generation > generation) st.watches.put(n, new Watch(expires, generation, source));
        }
    }

    /** Called (via mixin) before any block changes on the server. */
    public static void beforeSetBlock(ServerLevel level, BlockPos pos, BlockState newState) {
        if (suspended > 0 || playerBreaking > 0 || !enabled()) return;
        long now = level.getGameTime();
        if (scopeDepth > 0 && scopeLevel == level) {
            BlockState old = level.getBlockState(pos);
            if (old == newState) return;
            Store st = store(level);
            record(st, pos, old, scopeSource, now, scopeHoldsDrops).left = newState;
            watchAround(st, pos, 1, scopeSource, now);
            return;
        }
        Store st = STORES.get(level);
        if (st == null || st.watches.isEmpty()) return;
        Watch w = st.watches.get(pos.asLong());
        if (w == null || w.expires < now) return;
        BlockState old = level.getBlockState(pos);
        if (old == newState) return;
        // Something placed into empty space is somebody building, not damage (water flowing in is the exception).
        if (old.isAir() && newState.getFluidState().isEmpty()) return;
        record(st, pos, old, w.source, now, true).left = newState;
        if (w.generation < cfg().cascadeDepth) watchAround(st, pos, w.generation + 1, w.source, now);
    }

    /** Called (via mixin) before a block pops its drops: drops of tracked damage are held back. */
    public static boolean holdDrops(ServerLevel level, BlockPos pos) {
        if (suspended > 0 || !enabled()) return false;
        if (scopeDepth > 0 && scopeLevel == level) return scopeHoldsDrops;
        Store st = STORES.get(level);
        if (st == null) return false;
        Watch w = st.watches.get(pos.asLong());
        return w != null && w.expires >= level.getGameTime() && st.records.containsKey(pos.asLong()) && playerBreaking == 0;
    }

    /** Called (via mixin) before an entity is added: items from tracked damage and falling blocks it set loose are cancelled. */
    public static boolean cancelSpawn(ServerLevel level, Entity e) {
        if (suspended > 0 || !enabled()) return false;
        if (scopeDepth > 0 && scopeLevel == level && scopeHoldsDrops && (e instanceof ItemEntity || e instanceof ExperienceOrb)) return true;
        if (e instanceof FallingBlockEntity) {
            Store st = STORES.get(level);
            if (st == null) return false;
            long p = e.blockPosition().asLong();
            Watch w = st.watches.get(p);
            // The block it came from is recorded and will come back where it was.
            return w != null && w.expires >= level.getGameTime() && st.records.containsKey(p);
        }
        return false;
    }

    private static void captureHanging(Store st, BlockPos pos, long now) {
        AABB block = new AABB(pos);
        for (HangingEntity e : st.level.getEntitiesOfClass(HangingEntity.class, block.inflate(1.5), Entity::isAlive)) {
            AABB support = e.getBoundingBox().move(e.getDirection().getUnitVec3().scale(-0.5)).deflate(1.0E-7);
            if (!support.intersects(block)) continue;
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, st.level.registryAccess());
            if (!e.save(out)) continue;
            st.hanging.add(new HangingRecord(out.buildResult(), support, e.getUUID(), now + cfg().delayTicks));
            // Taken down whole, with whatever it holds, so nothing drops and nothing is duplicated.
            e.discard();
            st.dirty = true;
            st.urgent = true;
        }
    }

    private static void beginScope(ServerLevel level, String source, boolean holdDrops) {
        if (scopeDepth++ == 0) {
            scopeLevel = level;
            scopeSource = source;
            scopeHoldsDrops = holdDrops;
        }
    }

    private static void endScope() {
        if (--scopeDepth == 0) scopeLevel = null;
    }

    /** Runs world edits that are not damage (domain structures building/restoring, restoration itself). */
    public static void pause() {
        suspended++;
    }

    public static void resume() {
        suspended = Math.max(0, suspended - 1);
    }

    // --- Restoring ---

    private static void tick(ServerLevel level) {
        Store st = STORES.get(level);
        if (st == null) return;
        long now = level.getGameTime();
        if (now % 20 == 0 && !st.watches.isEmpty()) st.watches.values().removeIf(w -> w.expires < now);
        if (now % 10 == 0 && !st.waitingChunks.isEmpty()) requeueLoadedChunks(st, now);
        if (!st.due.isEmpty() && st.due.firstKey() <= now) processDue(st, now, Math.max(16, cfg().blocksPerTick));
        if (!st.hanging.isEmpty()) processHanging(st, now, false);
        if (st.urgent || st.dirty && now - st.lastSave >= 600) save(st);
    }

    private static void requeueLoadedChunks(Store st, long now) {
        var it = st.waitingChunks.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            ChunkPos cp = ChunkPos.unpack(e.getLongKey());
            if (!st.level.hasChunk(cp.x(), cp.z())) continue;
            for (long p : e.getValue()) {
                Record r = st.records.get(p);
                if (r != null) schedule(st, r, now);
            }
            it.remove();
        }
    }

    private static void processDue(Store st, long now, int budget) {
        List<Record> batch = new ArrayList<>();
        while (!st.due.isEmpty() && batch.size() < budget) {
            var e = st.due.firstEntry();
            if (e.getKey() > now) break;
            st.due.pollFirstEntry();
            for (long p : e.getValue()) {
                Record r = st.records.get(p);
                if (r != null && r.restoreAt == e.getKey()) batch.add(r);
            }
        }
        if (batch.size() > budget) {
            for (Record r : batch.subList(budget, batch.size())) schedule(st, r, now + 1);
            batch = new ArrayList<>(batch.subList(0, budget));
        }
        restoreBatch(st, batch, now);
    }

    private static void restoreBatch(Store st, List<Record> batch, long now) {
        ServerLevel level = st.level;
        List<Record> solid = new ArrayList<>(), dependent = new ArrayList<>();
        for (Record r : batch) {
            BlockPos pos = BlockPos.of(r.pos);
            if (!level.isLoaded(pos)) {
                // Never force-load a chunk to repair it; it is repaired as soon as it loads.
                r.restoreAt = -1;
                st.waitingChunks.computeIfAbsent(ChunkPos.pack(pos), k -> new LongArrayList()).add(r.pos);
                continue;
            }
            if (DomainStructures.ownerOf(level, r.pos) != null) {
                // A domain is standing here: it gives the block back when it ends, then this restores the original.
                schedule(st, r, now + 20);
                continue;
            }
            (BlockSnapshots.dependsOnSupport(r.original) ? dependent : solid).add(r);
        }
        Comparator<Record> bottomUp = Comparator.comparingInt((Record r) -> BlockPos.getY(r.pos)).thenComparingLong(r -> r.pos);
        solid.sort(bottomUp);
        dependent.sort(bottomUp);
        List<BlockPos> placed = new ArrayList<>();
        pause();
        try {
            // Phase 1: terrain and other solid blocks.
            for (Record r : solid) apply(st, r, placed);
            // Phase 2/3: blocks resting on or attached to them, bottom up (door halves, plants, torches, rails...).
            for (Record r : dependent) {
                BlockPos pos = BlockPos.of(r.pos);
                if (!r.original.canSurvive(level, pos)) {
                    long support = pendingSupport(st, pos, now);
                    if (support > 0) {
                        schedule(st, r, support + 1);
                        continue;
                    }
                }
                apply(st, r, placed);
            }
        } finally {
            resume();
        }
        // Phase 5: nobody is left stuck inside something that came back.
        for (BlockPos pos : placed) {
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) continue;
            for (Entity e : level.getEntities((Entity) null, new AABB(pos), e -> !e.isSpectator())) {
                if (!level.noCollision(e)) BlockSnapshots.liftToFreeSpace(e);
            }
        }
    }

    /** The latest pending restore among the neighbours of {@code pos}, or 0 if none is pending. */
    private static long pendingSupport(Store st, BlockPos pos, long now) {
        long latest = 0;
        for (Direction d : BlockSnapshots.DIRECTIONS) {
            Record n = st.records.get(pos.relative(d).asLong());
            if (n != null && n.restoreAt >= now) latest = Math.max(latest, n.restoreAt);
            if (n != null && n.restoreAt < 0) latest = Math.max(latest, now + 20);
        }
        return latest;
    }

    private static void apply(Store st, Record r, List<BlockPos> placed) {
        ServerLevel level = st.level;
        BlockPos pos = BlockPos.of(r.pos);
        st.records.remove(r.pos);
        st.dirty = true;
        BlockState current = level.getBlockState(pos);
        if (current == r.original) return;
        boolean untouched = current == r.left || BlockSnapshots.isDebris(current);
        if (!untouched) {
            if (!"force".equalsIgnoreCase(cfg().conflictPolicy)) {
                // Someone changed this spot since: their change stays, and the original comes back as items instead.
                if (r.dropsHeld) BlockSnapshots.dropInstead(level, pos, r.original, r.blockEntity);
                return;
            }
            // Forced: whatever is there now is handed back as items rather than deleted.
            BlockSnapshots.dropInstead(level, pos, current, BlockSnapshots.blockEntity(level, pos, current));
        }
        // The same block with a live block entity was only reshaped (the other half of a double chest): keep its current
        // contents, restoring the snapshot's would duplicate anything taken out since.
        boolean keepLiveData = current.getBlock() == r.original.getBlock() && current.hasBlockEntity();
        BlockSnapshots.place(level, pos, r.original, keepLiveData ? null : r.blockEntity);
        placed.add(pos);
    }

    private static void processHanging(Store st, long now, boolean all) {
        Iterator<HangingRecord> it = st.hanging.iterator();
        while (it.hasNext()) {
            HangingRecord h = it.next();
            if (!all && h.restoreAt > now) continue;
            if (!all && supportPending(st, h.support)) {
                h.restoreAt = now + 20;
                continue;
            }
            it.remove();
            st.dirty = true;
            if (st.level.getEntity(h.uuid) != null) continue;
            EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, st.level.registryAccess(), h.data), st.level,
                    new EntitySpawnRequest(EntitySpawnReason.LOAD, true)).ifPresent(e -> st.level.addFreshEntity(e));
        }
    }

    private static boolean supportPending(Store st, AABB support) {
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(support.minX, support.minY, support.minZ),
                BlockPos.containing(support.maxX, support.maxY, support.maxZ))) {
            if (st.records.containsKey(p.asLong())) return true;
        }
        return false;
    }

    // --- Queries and admin ---

    public static int pending(ServerLevel level) {
        Store st = STORES.get(level);
        return st == null ? 0 : st.records.size() + st.hanging.size();
    }

    @Nullable
    public static Record recordAt(ServerLevel level, BlockPos pos) {
        Store st = STORES.get(level);
        return st == null ? null : st.records.get(pos.asLong());
    }

    /** Restores everything pending in this level immediately (admin command, tests). */
    public static int restoreAllNow(ServerLevel level) {
        Store st = STORES.get(level);
        if (st == null) return 0;
        int n = st.records.size() + st.hanging.size();
        long now = level.getGameTime();
        for (int pass = 0; pass < 8 && !st.records.isEmpty(); pass++) {
            List<Record> all = new ArrayList<>(st.records.values());
            st.due.clear();
            st.waitingChunks.clear();
            for (Record r : all) r.restoreAt = now;
            restoreBatch(st, all, now);
            // Dependents deferred for a support are due again right away.
            for (Record r : st.records.values()) r.restoreAt = now;
        }
        // Anything that still could not go back (its support was kept out by a conflict) stays queued normally.
        for (Record r : new ArrayList<>(st.records.values())) schedule(st, r, now + 1);
        processHanging(st, now, true);
        st.watches.clear();
        save(st);
        return n;
    }

    /** Drops every pending record without restoring (admin command). */
    public static int forgetAll(ServerLevel level) {
        Store st = STORES.get(level);
        if (st == null) return 0;
        int n = st.records.size() + st.hanging.size();
        st.records.clear();
        st.due.clear();
        st.hanging.clear();
        st.waitingChunks.clear();
        st.watches.clear();
        save(st);
        return n;
    }

    // --- Persistence ---

    private static Path file(ServerLevel level) {
        String dim = level.dimension().identifier().toString().replace(':', '_').replace('/', '_');
        return level.getServer().getWorldPath(DIR).resolve(dim + ".dat");
    }

    static void save(Store st) {
        st.dirty = false;
        st.urgent = false;
        st.lastSave = st.level.getGameTime();
        Path f = file(st.level);
        try {
            if (st.records.isEmpty() && st.hanging.isEmpty()) {
                Files.deleteIfExists(f);
                return;
            }
            Files.createDirectories(f.getParent());
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> index = new HashMap<>();
            ListTag recs = new ListTag();
            for (Record r : st.records.values()) {
                CompoundTag t = new CompoundTag();
                t.putLong("P", r.pos);
                t.putInt("O", index.computeIfAbsent(r.original, s -> { palette.add(s); return palette.size() - 1; }));
                t.putInt("L", index.computeIfAbsent(r.left, s -> { palette.add(s); return palette.size() - 1; }));
                t.putLong("D", r.damagedAt);
                t.putLong("R", r.restoreAt);
                t.putString("S", r.source);
                t.putBoolean("H", r.dropsHeld);
                if (r.blockEntity != null) t.put("BE", r.blockEntity);
                recs.add(t);
            }
            ListTag pal = new ListTag();
            for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
            ListTag hang = new ListTag();
            for (HangingRecord h : st.hanging) {
                CompoundTag t = new CompoundTag();
                t.put("Data", h.data);
                t.putLong("R", h.restoreAt);
                t.putLongArray("U", new long[] {h.uuid.getMostSignificantBits(), h.uuid.getLeastSignificantBits()});
                t.putIntArray("Box", new int[] {(int) Math.floor(h.support.minX * 64), (int) Math.floor(h.support.minY * 64), (int) Math.floor(h.support.minZ * 64),
                        (int) Math.ceil(h.support.maxX * 64), (int) Math.ceil(h.support.maxY * 64), (int) Math.ceil(h.support.maxZ * 64)});
                hang.add(t);
            }
            CompoundTag root = new CompoundTag();
            root.put("Palette", pal);
            root.put("Records", recs);
            root.put("Hanging", hang);
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            JJK.LOGGER.error("Could not save pending world restoration for {}", st.level.dimension(), e);
        }
    }

    public static void saveAll() {
        for (Store st : STORES.values()) save(st);
    }

    private static void loadAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) load(level);
    }

    /** Reads this level's pending restorations from disk (after a restart). Returns how many were loaded. */
    public static int load(ServerLevel level) {
        Path f = file(level);
        if (!Files.exists(f)) return 0;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
            ListTag pal = root.getListOrEmpty("Palette");
            BlockState[] palette = new BlockState[pal.size()];
            for (int i = 0; i < pal.size(); i++) palette[i] = NbtUtils.readBlockState(blocks, pal.getCompoundOrEmpty(i));
            Store st = store(level);
            long now = level.getGameTime();
            int n = 0;
            for (net.minecraft.nbt.Tag tag : root.getListOrEmpty("Records")) {
                if (!(tag instanceof CompoundTag t)) continue;
                long p = t.getLongOr("P", 0L);
                if (st.records.containsKey(p)) continue;
                Record r = new Record(p, palette[t.getIntOr("O", 0)], t.getCompound("BE").orElse(null), t.getLongOr("D", now),
                        t.getStringOr("S", "unknown"), t.getBooleanOr("H", true));
                r.left = palette[t.getIntOr("L", 0)];
                st.records.put(p, r);
                long at = t.getLongOr("R", now);
                schedule(st, r, at < 0 ? now : at);
                n++;
            }
            for (net.minecraft.nbt.Tag tag : root.getListOrEmpty("Hanging")) {
                if (!(tag instanceof CompoundTag t)) continue;
                long[] u = t.getLongArray("U").orElse(new long[2]);
                int[] b = t.getIntArray("Box").orElse(new int[6]);
                AABB box = new AABB(b[0] / 64.0, b[1] / 64.0, b[2] / 64.0, b[3] / 64.0, b[4] / 64.0, b[5] / 64.0);
                st.hanging.add(new HangingRecord(t.getCompoundOrEmpty("Data"), box, new UUID(u[0], u[1]), t.getLongOr("R", now)));
                n++;
            }
            st.dirty = false;
            st.lastSave = now;
            JJK.LOGGER.info("Resuming {} pending world restorations in {}", n, level.dimension().identifier());
            return n;
        } catch (Exception e) {
            JJK.LOGGER.error("Could not read pending world restoration {}", f, e);
            return 0;
        }
    }

    /** Test hook: forget the in-memory state of a level without restoring or saving (simulates a restart). */
    public static void forgetInMemoryForTesting(ServerLevel level) {
        STORES.remove(level);
    }

    /** Test hook: whether this level has a restoration file on disk. */
    public static boolean hasFileForTesting(ServerLevel level) {
        return Files.exists(file(level));
    }

    /** Test hook: write the level's state to disk now. */
    public static void saveForTesting(ServerLevel level) {
        Store st = STORES.get(level);
        if (st != null) save(st);
    }
}
