package dev.rick.jjk.core.domain.structure;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.fx.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Owns every physical domain structure: building, restoring, protecting and crash recovery.
 *
 * Crash safety: a structure's snapshot is written to {@code <world>/jjk_domains/} before a single block is changed and
 * deleted only after restoration is verified. On startup any leftover snapshot (from a crash or kill) is restored
 * immediately. On a normal shutdown every live structure is restored before the world saves.
 */
public final class DomainStructures {
    private static final LevelResource DIR = new LevelResource("jjk_domains");
    private static final Map<ServerLevel, List<DomainStructure>> ACTIVE = new IdentityHashMap<>();
    private static int nextId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);

    private DomainStructures() {}

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(DomainStructures::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(DomainStructures::recoverFromDisk);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> restoreEverythingNow());
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl && ownerOf(sl, pos.asLong()) != null) {
                Fx.play(sl, "domain_block", Vec3.atCenterOf(pos), Vec3.ZERO, 1f);
                return false;
            }
            return true;
        });
    }

    /** Snapshots and starts building a structure. Returns null if the spec covers nothing. */
    @Nullable
    public static DomainStructure create(ServerLevel level, BlockPos center, StructureSpec spec) {
        DomainStructure s = DomainStructure.capture(nextId++, level, center, spec, packed -> ownerOf(level, packed) != null);
        if (s.size() == 0) return null;
        persist(s);
        ACTIVE.computeIfAbsent(level, l -> new ArrayList<>()).add(s);
        return s;
    }

    public static void beginRestore(DomainStructure s) {
        s.beginRestore();
    }

    /** The live structure holding its block at this position, if any. */
    @Nullable
    public static DomainStructure ownerOf(ServerLevel level, long packed) {
        List<DomainStructure> list = ACTIVE.get(level);
        if (list == null) return null;
        for (DomainStructure s : list) if (s.holds(packed)) return s;
        return null;
    }

    public static List<DomainStructure> all(ServerLevel level) {
        return ACTIVE.getOrDefault(level, List.of());
    }

    private static void tick(ServerLevel level) {
        List<DomainStructure> list = ACTIVE.get(level);
        if (list == null || list.isEmpty()) return;
        int budget = Math.max(64, JJKConfig.get().domain.blocksPerTick);
        for (DomainStructure s : List.copyOf(list)) {
            switch (s.state()) {
                case BUILDING -> s.buildStep(budget);
                case RESTORING -> {
                    if (s.restoreStep(budget, DomainStructures::adopt)) {
                        list.remove(s);
                        deleteFile(s);
                    }
                }
                case DONE -> {
                    list.remove(s);
                    deleteFile(s);
                }
                default -> {}
            }
        }
    }

    /** A collapsing structure hands positions that another live structure also covers over to that structure. */
    private static boolean adopt(DomainStructure from, long packed, BlockState original, @Nullable CompoundTag be) {
        for (DomainStructure other : ACTIVE.getOrDefault(from.level, List.of())) {
            if (other == from || !other.isActive() || other.spec == null) continue;
            BlockPos p = BlockPos.of(packed);
            BlockState target = other.spec.targetAt(p.getX() - other.center.getX(), p.getY() - other.center.getY(), p.getZ() - other.center.getZ());
            if (target == null) continue;
            other.adopted.put(packed, new DomainStructure.Adopted(original, be));
            from.level.setBlock(p, target, DomainStructure.FLAGS);
            persist(other);
            return true;
        }
        return false;
    }

    /** Restores every live structure synchronously (server stopping). */
    public static void restoreEverythingNow() {
        for (Map.Entry<ServerLevel, List<DomainStructure>> e : ACTIVE.entrySet()) {
            for (DomainStructure s : List.copyOf(e.getValue())) {
                s.restoreAll((from, pos, original, be) -> false);
                deleteFile(s);
            }
        }
        ACTIVE.clear();
    }

    // --- Persistence ---

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(DIR);
    }

    private static Path file(DomainStructure s) {
        return dir(s.level.getServer()).resolve("domain_" + Integer.toUnsignedString(s.id) + ".dat");
    }

    static void persist(DomainStructure s) {
        try {
            Files.createDirectories(dir(s.level.getServer()));
            Path tmp = file(s).resolveSibling(file(s).getFileName() + ".tmp");
            NbtIo.writeCompressed(s.save(), tmp);
            Files.move(tmp, file(s), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            JJK.LOGGER.error("Could not persist domain snapshot {}", s.id, e);
        }
    }

    private static void deleteFile(DomainStructure s) {
        try {
            Files.deleteIfExists(file(s));
        } catch (IOException e) {
            JJK.LOGGER.error("Could not delete domain snapshot {}", s.id, e);
        }
    }

    /** A snapshot that belongs to a structure still running in this session is not a leftover. */
    private static boolean isLive(MinecraftServer server, Path f) {
        for (List<DomainStructure> list : ACTIVE.values()) {
            for (DomainStructure s : list) if (file(s).equals(f)) return true;
        }
        return false;
    }

    public static boolean hasSnapshotOnDisk(DomainStructure s) {
        return Files.exists(file(s));
    }

    /** Restores any snapshot left on disk (the server stopped or crashed while a domain was up). */
    public static int recoverFromDisk(MinecraftServer server) {
        Path d = dir(server);
        if (!Files.isDirectory(d)) return 0;
        int restored = 0;
        try (Stream<Path> files = Files.list(d)) {
            for (Path f : files.filter(f -> f.toString().endsWith(".dat")).toList()) {
                if (isLive(server, f)) continue;
                try {
                    CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
                    ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("Dimension", "minecraft:overworld")));
                    ServerLevel level = server.getLevel(dim);
                    if (level == null) {
                        JJK.LOGGER.warn("Domain snapshot {} is for missing dimension {}; keeping it", f, dim);
                        continue;
                    }
                    DomainStructure s = DomainStructure.load(level, tag);
                    s.restoreAll((from, pos, original, be) -> false);
                    Files.deleteIfExists(f);
                    restored++;
                    JJK.LOGGER.info("Recovered world blocks from interrupted domain {} ({} blocks)", s.id, s.size());
                } catch (Exception e) {
                    JJK.LOGGER.error("Failed to recover domain snapshot {}", f, e);
                }
            }
        } catch (IOException e) {
            JJK.LOGGER.error("Could not list domain snapshots", e);
        }
        return restored;
    }

    /** Test hook: forget a live structure without restoring it (simulates a crash). */
    public static void forgetForTesting(DomainStructure s) {
        List<DomainStructure> list = ACTIVE.get(s.level);
        if (list != null) list.remove(s);
    }
}
