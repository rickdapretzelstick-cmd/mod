package dev.rick.jjk.progression.prison;

import dev.rick.jjk.JJK;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The world's one Prison Realm, written to {@code <world>/jjk_progression/prison_realm.dat} every time it changes (an
 * atomic replace, like the kit registry). It says whether a realm exists at all (its id), what it is doing (an item
 * somewhere, sealing someone, or grounded with a captive inside), where it lies, who is inside and how far their escape
 * has got, what the cell replaced (so the world gets it back), and a release still owed to a captive who was offline.
 *
 * <p>Only the server thread touches it; every change goes through {@link PrisonRealm}.
 */
public final class PrisonRealmState {
    private static final LevelResource DIR = new LevelResource("jjk_progression");
    private static final String FILE = "prison_realm.dat";

    public enum Phase {
        /** No Prison Realm exists in the world: one may be infused. */
        NONE,
        /** The realm is an item somewhere (an inventory, a chest, on the ground). */
        ITEM,
        /** Thrown out and opening on someone: the sealing sequence is running. */
        SEALING,
        /** Closed on the ground with its captive inside. */
        SEALED
    }

    /** One block the cell replaced. */
    public record Saved(BlockPos pos, BlockState state) {}

    /** A release decided while the captive was offline: applied when they next join. */
    public record Pending(UUID captive, String dimension, Vec3 at, boolean genuine) {}

    @Nullable private static PrisonRealmState instance;

    private final MinecraftServer server;

    @Nullable UUID realmId;
    Phase phase = Phase.NONE;
    /** Where it lies (SEALING / SEALED) and in which dimension. */
    String dimension = "";
    @Nullable BlockPos pos;
    /** The world entity showing it (SEALING / SEALED). */
    @Nullable UUID entity;
    /** Who is (being) sealed. */
    @Nullable UUID captive;
    String captiveName = "";
    /** Game time the seal closed (SEALED), -1 otherwise: proof of a genuine capture. */
    long capturedAt = -1;
    /** Escape progress: the stage (0-2 locks, 3 = core open) and which of the four locks are broken this stage. */
    int stage;
    int broken;
    /** The cell's lower corner (SEALED) and what it replaced. */
    @Nullable BlockPos cell;
    final List<Saved> replaced = new ArrayList<>();
    final List<Pending> pending = new ArrayList<>();
    /**
     * A release decided and under way ("ESCAPE", "RESCUE", "ADMIN"; "" for none). Saved the moment it is decided, so a
     * restart during the opening still finishes it rather than leaving the captive sealed.
     */
    String releasing = "";
    /** Realms ever infused in this world (for the log and admin readouts). */
    int forged;

    private PrisonRealmState(MinecraftServer server) {
        this.server = server;
    }

    public static PrisonRealmState get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new PrisonRealmState(server);
            instance.load();
        }
        return instance;
    }

    public static void unload() {
        instance = null;
    }

    public Phase phase() {
        return phase;
    }

    @Nullable
    public UUID realmId() {
        return realmId;
    }

    @Nullable
    public UUID captive() {
        return captive;
    }

    public String captiveName() {
        return captiveName;
    }

    @Nullable
    public BlockPos pos() {
        return pos;
    }

    public String dimension() {
        return dimension;
    }

    @Nullable
    public BlockPos cell() {
        return cell;
    }

    public int stage() {
        return stage;
    }

    public int broken() {
        return broken;
    }

    public long capturedAt() {
        return capturedAt;
    }

    public List<Pending> pending() {
        return List.copyOf(pending);
    }

    /** Back to "an item somewhere": the captive, the cell and the escape forgotten (the id stays). */
    void toItem() {
        phase = realmId == null ? Phase.NONE : Phase.ITEM;
        dimension = "";
        pos = null;
        entity = null;
        captive = null;
        captiveName = "";
        capturedAt = -1;
        stage = 0;
        broken = 0;
        cell = null;
        replaced.clear();
        releasing = "";
    }

    public String releasing() {
        return releasing;
    }

    // --- Persistence ---

    private Path file() {
        return server.getWorldPath(DIR).resolve(FILE);
    }

    boolean save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            CompoundTag t = new CompoundTag();
            t.putInt("Version", 1);
            if (realmId != null) t.putString("Realm", realmId.toString());
            t.putString("Phase", phase.name());
            t.putString("Dimension", dimension);
            if (pos != null) t.putLong("Pos", pos.asLong());
            if (entity != null) t.putString("Entity", entity.toString());
            if (captive != null) t.putString("Captive", captive.toString());
            t.putString("CaptiveName", captiveName);
            t.putLong("CapturedAt", capturedAt);
            t.putInt("Stage", stage);
            t.putInt("Broken", broken);
            if (cell != null) t.putLong("Cell", cell.asLong());
            ListTag blocks = new ListTag();
            for (Saved s : replaced) {
                CompoundTag b = new CompoundTag();
                b.putLong("P", s.pos().asLong());
                b.put("S", NbtUtils.writeBlockState(s.state()));
                blocks.add(b);
            }
            t.put("Replaced", blocks);
            ListTag owed = new ListTag();
            for (Pending p : pending) {
                CompoundTag b = new CompoundTag();
                b.putString("Captive", p.captive().toString());
                b.putString("Dimension", p.dimension());
                b.putDouble("X", p.at().x);
                b.putDouble("Y", p.at().y);
                b.putDouble("Z", p.at().z);
                b.putBoolean("Genuine", p.genuine());
                owed.add(b);
            }
            t.put("Pending", owed);
            t.putInt("Forged", forged);
            t.putString("Releasing", releasing);
            Path tmp = f.resolveSibling(FILE + ".tmp");
            NbtIo.writeCompressed(t, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            JJK.LOGGER.error("Could not save the Prison Realm to {}", f, e);
            return false;
        }
    }

    private void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            CompoundTag t = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            realmId = uuid(t.getStringOr("Realm", ""));
            try {
                phase = Phase.valueOf(t.getStringOr("Phase", "NONE"));
            } catch (IllegalArgumentException e) {
                phase = Phase.NONE;
            }
            dimension = t.getStringOr("Dimension", "");
            pos = t.contains("Pos") ? BlockPos.of(t.getLongOr("Pos", 0L)) : null;
            entity = uuid(t.getStringOr("Entity", ""));
            captive = uuid(t.getStringOr("Captive", ""));
            captiveName = t.getStringOr("CaptiveName", "");
            capturedAt = t.getLongOr("CapturedAt", -1L);
            stage = Math.max(0, Math.min(3, t.getIntOr("Stage", 0)));
            broken = t.getIntOr("Broken", 0) & 15;
            cell = t.contains("Cell") ? BlockPos.of(t.getLongOr("Cell", 0L)) : null;
            var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
            for (net.minecraft.nbt.Tag tag : t.getListOrEmpty("Replaced")) {
                if (!(tag instanceof CompoundTag b)) continue;
                replaced.add(new Saved(BlockPos.of(b.getLongOr("P", 0L)), NbtUtils.readBlockState(blocks, b.getCompoundOrEmpty("S"))));
            }
            for (net.minecraft.nbt.Tag tag : t.getListOrEmpty("Pending")) {
                if (!(tag instanceof CompoundTag b)) continue;
                UUID who = uuid(b.getStringOr("Captive", ""));
                if (who == null) continue;
                pending.add(new Pending(who, b.getStringOr("Dimension", "minecraft:overworld"),
                        new Vec3(b.getDoubleOr("X", 0), b.getDoubleOr("Y", 0), b.getDoubleOr("Z", 0)), b.getBooleanOr("Genuine", false)));
            }
            forged = t.getIntOr("Forged", 0);
            releasing = t.getStringOr("Releasing", "");
            // Damaged data can't leave a seal with nobody in it, or a realm with no id: back to a safe state.
            if (realmId == null && phase != Phase.NONE) phase = Phase.NONE;
            if ((phase == Phase.SEALING || phase == Phase.SEALED) && (pos == null || captive == null)) {
                JJK.LOGGER.warn("Prison Realm data was {} without a position or captive; it is an item again", phase);
                phase = Phase.ITEM;
            }
            JJK.LOGGER.info("Loaded the Prison Realm: {} {}", phase, pos == null ? "" : dimension + " " + pos.toShortString());
        } catch (Exception e) {
            Path backup = f.resolveSibling(FILE + ".corrupt-" + System.currentTimeMillis());
            JJK.LOGGER.error("Could not read the Prison Realm {}; kept a copy at {}", f, backup, e);
            try {
                Files.copy(f, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    @Nullable
    private static UUID uuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
