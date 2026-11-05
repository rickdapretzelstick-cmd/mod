package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The world's answer to "who owns this kit?". Each kit (a character id) belongs to at most one player in the whole save:
 * not per dimension, not per team, not per who is online. It is written to {@code <world>/jjk_progression/kit_ownership.dat}
 * the moment it changes, and nothing in normal play (death, logout, dimension changes) releases a kit.
 *
 * <p>Every acquisition path goes through {@link #tryClaim}: claims run on the server thread and are also serialised on
 * this object, so two players finishing a claim on the same tick resolve one after the other and only the first can win.
 * Release, transfer and repair exist for administration ({@code /jjk kit ...}).
 */
public final class KitOwnership {
    private static final LevelResource DIR = new LevelResource("jjk_progression");
    private static final String FILE = "kit_ownership.dat";

    @Nullable private static KitOwnership instance;

    /** One kit's owner. {@code name} is the owner's name when they claimed it, for admin readouts only. */
    public record Owner(UUID uuid, String name, String source, long claimedAt) {}

    public enum ClaimResult {
        /** The kit was free and now belongs to the player. */
        CLAIMED,
        /** The player already owned it (nothing changed). */
        ALREADY_OWNER,
        /** Someone else owns it. */
        TAKEN,
        /** It could not be written to disk, so nothing was claimed. */
        FAILED
    }

    private final MinecraftServer server;
    private final Map<String, Owner> owners = new LinkedHashMap<>();

    private KitOwnership(MinecraftServer server) {
        this.server = server;
    }

    /** The registry of this server's world, loaded from disk on first use. */
    public static synchronized KitOwnership get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new KitOwnership(server);
            instance.load();
        }
        return instance;
    }

    /** Forgets the in-memory registry (server stopped; the next world loads its own). */
    public static synchronized void unload() {
        instance = null;
    }

    // --- Queries ---

    @Nullable
    public synchronized Owner owner(String kit) {
        return owners.get(kit);
    }

    public synchronized boolean isClaimed(String kit) {
        return owners.containsKey(kit);
    }

    public synchronized boolean isOwner(String kit, UUID player) {
        Owner o = owners.get(kit);
        return o != null && o.uuid.equals(player);
    }

    /** Whether this player legitimately owns any kit at all. */
    public synchronized boolean ownsAny(UUID player) {
        for (Owner o : owners.values()) if (o.uuid.equals(player)) return true;
        return false;
    }

    /** Every kit this player owns, in claim order. */
    public synchronized List<String> kitsOwnedBy(UUID player) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Owner> e : owners.entrySet()) if (e.getValue().uuid.equals(player)) out.add(e.getKey());
        return out;
    }

    public synchronized Map<String, Owner> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(owners));
    }

    // --- Changes (each one is persisted before it returns) ---

    /**
     * Atomically claims {@code kit} for {@code player}: free → theirs (and saved), already theirs → unchanged, anyone
     * else's → refused. Callers validate eligibility first; this only decides ownership.
     */
    public synchronized ClaimResult tryClaim(UUID player, String name, String kit, String source) {
        Owner current = owners.get(kit);
        if (current != null) return current.uuid.equals(player) ? ClaimResult.ALREADY_OWNER : ClaimResult.TAKEN;
        owners.put(kit, new Owner(player, name, source, System.currentTimeMillis()));
        if (!save()) {
            owners.remove(kit);
            return ClaimResult.FAILED;
        }
        JJK.LOGGER.info("Kit {} claimed by {} ({}) via {}", kit, name, player, source);
        return ClaimResult.CLAIMED;
    }

    /** Frees a kit (admin). Returns the previous owner, or null if it was unclaimed. */
    @Nullable
    public synchronized Owner release(String kit) {
        Owner old = owners.remove(kit);
        if (old != null) {
            if (!save()) {
                owners.put(kit, old);
                return null;
            }
            JJK.LOGGER.info("Kit {} released (was {} / {})", kit, old.name, old.uuid);
        }
        return old;
    }

    /** Gives a kit to another player whoever held it (admin). */
    public synchronized boolean transfer(String kit, UUID to, String name, String source) {
        Owner old = owners.put(kit, new Owner(to, name, source, System.currentTimeMillis()));
        if (!save()) {
            if (old == null) owners.remove(kit);
            else owners.put(kit, old);
            return false;
        }
        JJK.LOGGER.info("Kit {} transferred to {} ({}) from {}", kit, name, to, old == null ? "nobody" : old.uuid);
        return true;
    }

    // --- Persistence ---

    private Path file() {
        return server.getWorldPath(DIR).resolve(FILE);
    }

    private boolean save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            ListTag list = new ListTag();
            for (Map.Entry<String, Owner> e : owners.entrySet()) {
                Owner o = e.getValue();
                CompoundTag t = new CompoundTag();
                t.putString("Kit", e.getKey());
                t.putLongArray("Owner", new long[] {o.uuid.getMostSignificantBits(), o.uuid.getLeastSignificantBits()});
                t.putString("Name", o.name);
                t.putString("Source", o.source);
                t.putLong("ClaimedAt", o.claimedAt);
                list.add(t);
            }
            CompoundTag root = new CompoundTag();
            root.putInt("Version", 1);
            root.put("Kits", list);
            Path tmp = f.resolveSibling(FILE + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            JJK.LOGGER.error("Could not save kit ownership to {}", f, e);
            return false;
        }
    }

    private void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            for (net.minecraft.nbt.Tag tag : root.getListOrEmpty("Kits")) {
                if (!(tag instanceof CompoundTag t)) continue;
                String kit = t.getStringOr("Kit", "");
                long[] u = t.getLongArray("Owner").orElse(new long[0]);
                if (kit.isEmpty() || u.length != 2) continue;
                // A kit can only be listed once; a damaged file with a duplicate keeps the first (earliest) claim.
                owners.putIfAbsent(kit, new Owner(new UUID(u[0], u[1]), t.getStringOr("Name", "?"), t.getStringOr("Source", "unknown"),
                        t.getLongOr("ClaimedAt", 0L)));
            }
            JJK.LOGGER.info("Loaded kit ownership: {}", owners.keySet());
        } catch (Exception e) {
            // Keep the unreadable file aside so the next save can't silently erase who owned what.
            Path backup = f.resolveSibling(FILE + ".corrupt-" + System.currentTimeMillis());
            JJK.LOGGER.error("Could not read kit ownership {}; kept a copy at {}", f, backup, e);
            try {
                Files.copy(f, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }
}
