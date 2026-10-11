package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an incident's site changed in the overworld, so it can be taken down again once the incident is over. While a
 * site is built ({@link Sites#build}) every block change goes through {@link #before} (from the server's
 * {@code setBlock} mixin), which keeps the first original of each position; the built state is read straight after.
 *
 * <p>Taking it down ({@link #demolish}) works through the recorded positions top-down, a batch per call, putting back the
 * original block only where the site's own block is still there (or where it has been broken to air): anything a player
 * has built or changed since, and everything the site never touched, is left alone. A block whose space a player is
 * standing in waits. It is saved with the incident, so a restart, a relog or an unloaded chunk just resumes it.
 */
public final class SiteFootprint {
    /** One changed position: what was there, and what the site put there. */
    public record Change(long pos, BlockState original, BlockState built) {}

    @Nullable private static Map<Long, BlockState> recording;
    @Nullable private static ServerLevel recordingLevel;

    /** Blocks put back per call (a call every few ticks: the site comes down over a few seconds). */
    static final int BATCH = 48;

    private SiteFootprint() {}

    /** Called (via mixin) before any block changes on the server: while a site is being built, keep the original. */
    public static void before(ServerLevel level, BlockPos pos, BlockState newState) {
        if (recording != null && level == recordingLevel) recording.putIfAbsent(pos.asLong(), level.getBlockState(pos));
    }

    /** Runs {@code build} and returns every position it changed, with the original and the built state. */
    static List<Change> record(ServerLevel level, Runnable build) {
        Map<Long, BlockState> originals = new LinkedHashMap<>();
        Map<Long, BlockState> outer = recording;
        ServerLevel outerLevel = recordingLevel;
        recording = originals;
        recordingLevel = level;
        try {
            build.run();
        } finally {
            recording = outer;
            recordingLevel = outerLevel;
        }
        List<Change> out = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, BlockState> e : originals.entrySet()) {
            m.set(e.getKey());
            BlockState built = level.getBlockState(m);
            if (built != e.getValue()) out.add(new Change(e.getKey(), e.getValue(), built));
        }
        // Top-down: what stands on something comes off before it.
        out.sort(Comparator.comparingInt((Change c) -> BlockPos.getY(c.pos())).reversed());
        return out;
    }

    /**
     * Puts back up to {@link #BATCH} more of the site's originals. Returns true once nothing is left to take down. Waits
     * (returns false, nothing done) while the site's ground isn't loaded.
     */
    static boolean demolish(ServerLevel level, List<Change> left) {
        if (left.isEmpty()) return true;
        int done = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < left.size() && done < BATCH; ) {
            Change c = left.get(i);
            m.set(c.pos());
            if (!level.isLoaded(m)) return false;
            BlockState now = level.getBlockState(m);
            boolean ours = now == c.built() || (now.isAir() && !c.original().isAir());
            if (ours && !c.original().isAir() && !level.getEntitiesOfClass(Player.class, new AABB(m)).isEmpty()) {
                i++; // someone is standing in that space: it waits for the next pass
                continue;
            }
            if (ours && now != c.original()) {
                if (!now.isAir() && level.getRandom().nextInt(3) == 0) level.levelEvent(2001, m, Block.getId(now));
                level.setBlock(m, c.original(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            left.remove(i);
            done++;
        }
        if (done > 0 && !left.isEmpty()) {
            Vec3 at = Vec3.atCenterOf(BlockPos.of(left.get(0).pos()));
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 0.8f);
        }
        return left.isEmpty();
    }

    /**
     * Everyone who would be shut in the ground once what is left comes down: standing in a space the site carved out of
     * it (a mine's tunnel), or (after a pass) sealed in a pocket of it. Spectators never are.
     */
    static List<net.minecraft.server.level.ServerPlayer> trapped(ServerLevel level, List<Change> left, boolean pocketsToo) {
        if (left.isEmpty()) return List.of();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Change c : left) {
            int x = BlockPos.getX(c.pos()), y = BlockPos.getY(c.pos()), z = BlockPos.getZ(c.pos());
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
            x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
        }
        AABB box = new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1).inflate(2);
        List<net.minecraft.server.level.ServerPlayer> near = level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator());
        if (near.isEmpty()) return List.of();
        java.util.Set<Long> carved = new java.util.HashSet<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Change c : left) {
            m.set(c.pos());
            if (!c.original().getCollisionShape(level, m).isEmpty()) carved.add(c.pos());
        }
        List<net.minecraft.server.level.ServerPlayer> out = new ArrayList<>();
        for (var p : near) {
            AABB b = p.getBoundingBox();
            boolean in = false;
            for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX - 1e-4, b.maxY - 1e-4, b.maxZ - 1e-4))) {
                if (carved.contains(q.asLong())) {
                    in = true;
                    break;
                }
            }
            if (in || pocketsToo && dev.rick.jjk.core.world.SafeGround.entombed(p)) out.add(p);
        }
        return out;
    }

    /** Whether {@code pos} is in a space the site carved out of the ground (it fills in again when the site comes down). */
    static boolean carved(ServerLevel level, List<Change> left, BlockPos pos) {
        long feet = pos.asLong(), head = pos.above().asLong();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Change c : left) {
            if (c.pos() != feet && c.pos() != head) continue;
            if (!c.original().getCollisionShape(level, m.set(c.pos())).isEmpty()) return true;
        }
        return false;
    }

    static ListTag save(List<Change> changes) {
        ListTag list = new ListTag();
        for (Change c : changes) {
            CompoundTag t = new CompoundTag();
            t.putLong("P", c.pos());
            t.put("O", NbtUtils.writeBlockState(c.original()));
            t.put("B", NbtUtils.writeBlockState(c.built()));
            list.add(t);
        }
        return list;
    }

    static List<Change> load(ServerLevel level, ListTag list) {
        var blocks = level.holderLookup(Registries.BLOCK);
        List<Change> out = new ArrayList<>();
        for (Tag x : list) {
            if (!(x instanceof CompoundTag t)) continue;
            out.add(new Change(t.getLongOr("P", 0L), NbtUtils.readBlockState(blocks, t.getCompoundOrEmpty("O")),
                    NbtUtils.readBlockState(blocks, t.getCompoundOrEmpty("B"))));
        }
        return out;
    }
}
