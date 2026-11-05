package dev.rick.jjk.progression.story;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The world's answer to "which one-of-a-kind relics exist?". Each key (a character relic: {@code relic:gojo}...) has at
 * most one live <i>token</i> in the whole save, written to {@code <world>/jjk_progression/unique_relics.dat} the moment it
 * changes (an atomic replace, like {@link dev.rick.jjk.progression.KitOwnership}). Only the item stamped with the live
 * token is functional, so a copied, stale or re-forged-over item is inert wherever it is.
 *
 * <p>Every change runs on the server thread and is serialised on this object, so two players finishing an infusion on
 * the same tick resolve one after the other: exactly one {@link #forge} succeeds. A relic is freed again only when its
 * item is destroyed while it is not yet bound to an owner ({@link #destroyed}), when its keeper re-forges it (a new
 * token that retires the lost one: {@link #reforge}), or by an admin ({@link #reset}). Death, logout, dimension changes
 * and restarts never free one.
 *
 * <p>General on purpose: any later one-of-a-kind item (a relic, a special weapon) is one more key here, not another
 * registry.
 */
public final class UniqueRelics {
    private static final LevelResource DIR = new LevelResource("jjk_progression");
    private static final String FILE = "unique_relics.dat";

    @Nullable private static UniqueRelics instance;

    /**
     * One live relic: its token, who forged it and who keeps it now (the last player to use it), when, and the player it
     * is bound to once its storyline is complete (null until then).
     */
    public record Relic(String key, UUID token, UUID keeper, String keeperName, long forgedAt, @Nullable UUID boundTo) {
        Relic withKeeper(UUID k, String name) {
            return new Relic(key, token, k, name, forgedAt, boundTo);
        }

        Relic bound(UUID owner) {
            return new Relic(key, token, keeper, keeperName, forgedAt, owner);
        }
    }

    private final MinecraftServer server;
    private final Map<String, Relic> relics = new LinkedHashMap<>();

    private UniqueRelics(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized UniqueRelics get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new UniqueRelics(server);
            instance.load();
        }
        return instance;
    }

    public static synchronized void unload() {
        instance = null;
    }

    // --- Queries ---

    @Nullable
    public synchronized Relic relic(String key) {
        return relics.get(key);
    }

    public synchronized boolean exists(String key) {
        return relics.containsKey(key);
    }

    public synchronized boolean isLive(String key, @Nullable UUID token) {
        Relic r = relics.get(key);
        return r != null && token != null && r.token.equals(token);
    }

    public synchronized Map<String, Relic> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(relics));
    }

    // --- Changes (each persisted before it returns; a failed save changes nothing) ---

    /** Atomically makes the one live relic for {@code key}, or returns null if one already exists (or can't be saved). */
    @Nullable
    public synchronized UUID forge(String key, UUID player, String name) {
        if (relics.containsKey(key)) return null;
        Relic r = new Relic(key, UUID.randomUUID(), player, name, System.currentTimeMillis(), null);
        relics.put(key, r);
        if (!save()) {
            relics.remove(key);
            return null;
        }
        JJK.LOGGER.info("Relic {} forged by {} ({})", key, name, player);
        return r.token;
    }

    /**
     * The keeper of a relic that was lost (it isn't on them any more) forges it again: a new token replaces the old one,
     * so the lost item goes cold wherever it is. Never a second live relic. Null if they aren't its keeper.
     */
    @Nullable
    public synchronized UUID reforge(String key, UUID player, String name) {
        Relic old = relics.get(key);
        if (old == null || !old.keeper.equals(player)) return null;
        Relic r = new Relic(key, UUID.randomUUID(), player, name, System.currentTimeMillis(), old.boundTo);
        relics.put(key, r);
        if (!save()) {
            relics.put(key, old);
            return null;
        }
        JJK.LOGGER.info("Relic {} re-forged by its keeper {} ({}); the old token is retired", key, name, player);
        return r.token;
    }

    /** Whoever uses a live relic becomes its keeper (the one who may re-forge it if it is lost). */
    public synchronized void keep(String key, UUID token, UUID player, String name) {
        Relic r = relics.get(key);
        if (r == null || !r.token.equals(token) || r.keeper.equals(player)) return;
        relics.put(key, r.withKeeper(player, name));
        if (!save()) relics.put(key, r);
    }

    /** Its storyline is complete: the relic is bound to its owner and stays the world's one, whatever happens to it. */
    public synchronized void bind(String key, UUID owner) {
        Relic r = relics.get(key);
        if (r == null || owner.equals(r.boundTo)) return;
        relics.put(key, r.bound(owner));
        if (!save()) relics.put(key, r);
    }

    /** The live item was destroyed (lava, a cactus, the void): an unbound relic may be forged again. True if freed. */
    public synchronized boolean destroyed(String key, @Nullable UUID token) {
        Relic r = relics.get(key);
        if (r == null || token == null || !r.token.equals(token) || r.boundTo != null) return false;
        relics.remove(key);
        if (!save()) {
            relics.put(key, r);
            return false;
        }
        JJK.LOGGER.info("Relic {} was destroyed; it may be forged again", key);
        return true;
    }

    /** Forgets a relic (admin): its item goes cold and another may be forged. */
    @Nullable
    public synchronized Relic reset(String key) {
        Relic r = relics.remove(key);
        if (r != null && !save()) {
            relics.put(key, r);
            return null;
        }
        return r;
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
            for (Relic r : relics.values()) {
                CompoundTag t = new CompoundTag();
                t.putString("Key", r.key);
                t.putString("Token", r.token.toString());
                t.putString("Keeper", r.keeper.toString());
                t.putString("KeeperName", r.keeperName);
                t.putLong("ForgedAt", r.forgedAt);
                if (r.boundTo != null) t.putString("BoundTo", r.boundTo.toString());
                list.add(t);
            }
            CompoundTag root = new CompoundTag();
            root.putInt("Version", 1);
            root.put("Relics", list);
            Path tmp = f.resolveSibling(FILE + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            JJK.LOGGER.error("Could not save unique relics to {}", f, e);
            return false;
        }
    }

    private void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            for (net.minecraft.nbt.Tag tag : root.getListOrEmpty("Relics")) {
                if (!(tag instanceof CompoundTag t)) continue;
                try {
                    String key = t.getStringOr("Key", "");
                    if (key.isEmpty()) continue;
                    String bound = t.getStringOr("BoundTo", "");
                    relics.putIfAbsent(key, new Relic(key, UUID.fromString(t.getStringOr("Token", "")), UUID.fromString(t.getStringOr("Keeper", "")),
                            t.getStringOr("KeeperName", "?"), t.getLongOr("ForgedAt", 0L), bound.isEmpty() ? null : UUID.fromString(bound)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            JJK.LOGGER.info("Loaded unique relics: {}", relics.keySet());
        } catch (Exception e) {
            Path backup = f.resolveSibling(FILE + ".corrupt-" + System.currentTimeMillis());
            JJK.LOGGER.error("Could not read unique relics {}; kept a copy at {}", f, backup, e);
            try {
                Files.copy(f, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }
}
