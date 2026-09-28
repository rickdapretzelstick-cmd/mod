package dev.rick.jjk.core.domain.structure;

import dev.rick.jjk.JJK;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One physical domain structure and the exact snapshot of everything it replaced.
 *
 * Lifecycle: snapshot everything first (and persist it to disk), then place blocks over a few ticks, then later
 * restore every recorded position to its original state and block-entity data, move entities out of any restored
 * blocks, verify no structure blocks remain, and delete the persisted snapshot.
 */
public final class DomainStructure {
    public enum State { BUILDING, BUILT, RESTORING, DONE }

    /** Place/restore without drops, neighbour cascades, onPlace logic or container side effects. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    public final int id;
    public final ServerLevel level;
    public final BlockPos center;
    @Nullable public final StructureSpec spec;
    // Recorded positions (packed), what was there, and what the structure puts there. Ordered from the center out.
    final long[] positions;
    final BlockState[] originals;
    @Nullable final BlockState[] targets;
    final Map<Long, CompoundTag> blockEntities;
    /** Originals taken over from another structure that collapsed while overlapping this one. */
    final Map<Long, Adopted> adopted = new HashMap<>();
    private State state;
    private int cursor;

    record Adopted(BlockState original, @Nullable CompoundTag blockEntity) {}

    DomainStructure(int id, ServerLevel level, BlockPos center, @Nullable StructureSpec spec, long[] positions, BlockState[] originals,
                    @Nullable BlockState[] targets, Map<Long, CompoundTag> blockEntities, State state) {
        this.id = id;
        this.level = level;
        this.center = center;
        this.spec = spec;
        this.positions = positions;
        this.originals = originals;
        this.targets = targets;
        this.blockEntities = blockEntities;
        this.state = state;
    }

    /** Captures everything the spec would replace. Positions claimed by {@code skip} (another structure) are left out. */
    static DomainStructure capture(int id, ServerLevel level, BlockPos center, StructureSpec spec, java.util.function.LongPredicate skip) {
        int r = (int) Math.ceil(spec.radius()) + 1;
        record Entry(long pos, BlockState original, BlockState target, double dist) {}
        List<Entry> entries = new ArrayList<>();
        Map<Long, CompoundTag> bes = new HashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minY = level.getMinY(), maxY = level.getMaxY();
        for (int dy = -r; dy <= r; dy++) {
            int y = center.getY() + dy;
            if (y < minY || y > maxY) continue;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockState target = spec.targetAt(dx, dy, dz);
                    if (target == null) continue;
                    p.set(center.getX() + dx, y, center.getZ() + dz);
                    long packed = p.asLong();
                    if (skip.test(packed)) continue;
                    BlockState original = level.getBlockState(p);
                    if (original == target) continue;
                    entries.add(new Entry(packed, original, target, Math.sqrt(dx * dx + dy * dy + dz * dz)));
                    if (original.hasBlockEntity()) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be != null) bes.put(packed, be.saveWithFullMetadata(level.registryAccess()));
                    }
                }
            }
        }
        // The void spreads outward from the center; the barrier closes last.
        entries.sort((a, b) -> Double.compare(a.dist, b.dist));
        long[] pos = new long[entries.size()];
        BlockState[] orig = new BlockState[entries.size()];
        BlockState[] tgt = new BlockState[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            pos[i] = entries.get(i).pos;
            orig[i] = entries.get(i).original;
            tgt[i] = entries.get(i).target;
        }
        return new DomainStructure(id, level, center, spec, pos, orig, tgt, bes, State.BUILDING);
    }

    public State state() {
        return state;
    }

    public int size() {
        return positions.length;
    }

    /** Places up to {@code budget} blocks. Returns true when fully built. */
    boolean buildStep(int budget) {
        if (state != State.BUILDING || targets == null) return state != State.BUILDING;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int end = Math.min(positions.length, cursor + budget);
        for (; cursor < end; cursor++) {
            p.set(positions[cursor]);
            level.setBlock(p, targets[cursor], FLAGS);
        }
        if (cursor >= positions.length) {
            state = State.BUILT;
            return true;
        }
        return false;
    }

    /** Starts restoring (from the outside in, so the barrier opens first). */
    void beginRestore() {
        if (state == State.RESTORING || state == State.DONE) return;
        state = State.RESTORING;
        // Only positions actually placed need restoring; unplaced ones still hold their originals.
        cursor = Math.min(cursor, positions.length) - 1;
        if (targets == null) cursor = positions.length - 1;
    }

    /**
     * Restores up to {@code budget} blocks. {@code adopt} lets another live structure claim a position it also covers
     * (it then records the original instead of it being restored here). Returns true when fully restored.
     */
    boolean restoreStep(int budget, Adopter adopt) {
        if (state != State.RESTORING) return state == State.DONE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int done = 0;
        for (; cursor >= 0 && done < budget; cursor--, done++) {
            long packed = positions[cursor];
            p.set(packed);
            CompoundTag be = blockEntities.get(packed);
            if (adopt.adopt(this, packed, originals[cursor], be)) continue;
            restoreOne(p.immutable(), originals[cursor], be);
        }
        if (cursor < 0) {
            for (Map.Entry<Long, Adopted> e : new ArrayList<>(adopted.entrySet())) {
                if (adopt.adopt(this, e.getKey(), e.getValue().original(), e.getValue().blockEntity())) continue;
                restoreOne(BlockPos.of(e.getKey()), e.getValue().original(), e.getValue().blockEntity());
            }
            adopted.clear();
            finish();
            return true;
        }
        return false;
    }

    /** Restores everything immediately (server stopping, crash recovery). */
    void restoreAll(Adopter adopt) {
        if (state == State.DONE) return;
        if (state != State.RESTORING) beginRestore();
        restoreStep(Integer.MAX_VALUE, adopt);
    }

    void restoreOne(BlockPos pos, BlockState original, @Nullable CompoundTag beTag) {
        level.setBlock(pos, original, FLAGS);
        if (beTag != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), beTag));
                be.setChanged();
                level.sendBlockUpdated(pos, original, original, Block.UPDATE_CLIENTS);
            }
        }
        FluidState fluid = original.getFluidState();
        if (!fluid.isEmpty()) level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
    }

    private void finish() {
        state = State.DONE;
        // Anyone left standing where a block came back is lifted to the nearest free space.
        double r = spec != null ? spec.radius() + 2 : 32;
        AABB box = new AABB(center).inflate(r);
        for (Entity e : level.getEntities((Entity) null, box, e -> !e.isSpectator())) {
            if (!level.noCollision(e)) liftToFreeSpace(e);
        }
        // Verification: nothing of the structure may survive.
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int leftovers = 0;
        for (int i = 0; i < positions.length; i++) {
            p.set(positions[i]);
            if (dev.rick.jjk.registry.ModBlocks.isDomainBlock(level.getBlockState(p)) && !dev.rick.jjk.registry.ModBlocks.isDomainBlock(originals[i])) {
                if (DomainStructures.ownerOf(level, positions[i]) == null) {
                    restoreOne(p.immutable(), originals[i], blockEntities.get(positions[i]));
                    leftovers++;
                }
            }
        }
        if (leftovers > 0) JJK.LOGGER.warn("Domain structure {} had {} leftover blocks after restore; fixed", id, leftovers);
    }

    static void liftToFreeSpace(Entity e) {
        for (int up = 1; up <= 64; up++) {
            AABB moved = e.getBoundingBox().move(0, up, 0);
            if (e.level().noCollision(e, moved)) {
                e.teleportTo(e.getX(), e.getY() + up, e.getZ());
                e.resetFallDistance();
                return;
            }
        }
    }

    public boolean isActive() {
        return state == State.BUILDING || state == State.BUILT;
    }

    /** Whether this structure currently holds its block at {@code packed}. */
    boolean holds(long packed) {
        if (!isActive() && state != State.RESTORING) return false;
        if (adopted.containsKey(packed)) return true;
        int idx = indexOf(packed);
        if (idx < 0) return false;
        return state == State.RESTORING ? idx <= cursor : (state == State.BUILT || idx < cursor);
    }

    private long[] sortedPositions;
    private int[] sortedIndex;

    int indexOf(long packed) {
        if (sortedPositions == null) {
            Integer[] order = new Integer[positions.length];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Long.compare(positions[a], positions[b]));
            sortedPositions = new long[positions.length];
            sortedIndex = new int[positions.length];
            for (int i = 0; i < order.length; i++) {
                sortedPositions[i] = positions[order[i]];
                sortedIndex[i] = order[i];
            }
        }
        int k = Arrays.binarySearch(sortedPositions, packed);
        return k < 0 ? -1 : sortedIndex[k];
    }

    // --- Persistence ---

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Dimension", level.dimension().identifier().toString());
        tag.putLong("Center", center.asLong());
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        int[] states = new int[originals.length];
        for (int i = 0; i < originals.length; i++) {
            states[i] = index.computeIfAbsent(originals[i], s -> {
                palette.add(s);
                return palette.size() - 1;
            });
        }
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        tag.put("Palette", pal);
        tag.putLongArray("Positions", positions);
        tag.putIntArray("States", states);
        ListTag bes = new ListTag();
        for (Map.Entry<Long, CompoundTag> e : blockEntities.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.put("Data", e.getValue());
            bes.add(t);
        }
        tag.put("BlockEntities", bes);
        ListTag ad = new ListTag();
        for (Map.Entry<Long, Adopted> e : adopted.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.put("State", NbtUtils.writeBlockState(e.getValue().original()));
            if (e.getValue().blockEntity() != null) t.put("Data", e.getValue().blockEntity());
            ad.add(t);
        }
        tag.put("Adopted", ad);
        return tag;
    }

    /** Rebuilds a structure from disk, in the RESTORING state (only restoration is possible after a restart). */
    static DomainStructure load(ServerLevel level, CompoundTag tag) {
        var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag pal = tag.getListOrEmpty("Palette");
        BlockState[] palette = new BlockState[pal.size()];
        for (int i = 0; i < pal.size(); i++) palette[i] = NbtUtils.readBlockState(blocks, pal.getCompoundOrEmpty(i));
        long[] pos = tag.getLongArray("Positions").orElse(new long[0]);
        int[] st = tag.getIntArray("States").orElse(new int[0]);
        BlockState[] orig = new BlockState[pos.length];
        for (int i = 0; i < pos.length; i++) orig[i] = palette[st[i]];
        Map<Long, CompoundTag> bes = new HashMap<>();
        for (net.minecraft.nbt.Tag t : tag.getListOrEmpty("BlockEntities")) {
            if (t instanceof CompoundTag c) bes.put(c.getLongOr("Pos", 0L), c.getCompoundOrEmpty("Data"));
        }
        DomainStructure s = new DomainStructure(tag.getIntOr("Id", -1), level, BlockPos.of(tag.getLongOr("Center", 0L)), null, pos, orig, null, bes,
                State.RESTORING);
        s.cursor = pos.length - 1;
        for (net.minecraft.nbt.Tag t : tag.getListOrEmpty("Adopted")) {
            if (t instanceof CompoundTag c) {
                s.adopted.put(c.getLongOr("Pos", 0L), new Adopted(NbtUtils.readBlockState(blocks, c.getCompoundOrEmpty("State")),
                        c.getCompound("Data").orElse(null)));
            }
        }
        return s;
    }

    @FunctionalInterface
    interface Adopter {
        boolean adopt(DomainStructure from, long pos, BlockState original, @Nullable CompoundTag blockEntity);
    }
}
