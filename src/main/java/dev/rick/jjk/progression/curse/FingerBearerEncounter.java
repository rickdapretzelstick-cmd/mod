package dev.rick.jjk.progression.curse;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.CursedEncounters;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What waits in a cursed battle room ({@link CursedEncounters#FINGER_BEARER}). The first time anyone sets foot in the
 * room, the Finger Bearer takes shape over the seal (unseen, unless they perceive curses), and the room is ACTIVE. Its
 * defeat clears the room for good and leaves the room's one Cursed Finger where it fell (eating it: {@link
 * dev.rick.jjk.progression.CursedFingerAcquisition}; it no longer makes Yuji). A cleared room never spawns again, so a
 * room gives one finger, ever.
 *
 * <p>The spirit is saved with its chunk and the room remembers its id. If it is ever lost without dying (a command, a
 * mod removing it), the room notices after a few checks with someone inside and raises it again; a chunk that is merely
 * still loading is given that time.
 */
public final class FingerBearerEncounter {
    /** Seal checks (two seconds apart) the spirit may be missing before the room raises it again. */
    static final int MISSING_CHECKS = 3;

    private FingerBearerEncounter() {}

    public static void init() {
        CursedEncounters.registerEncounter(CursedEncounters.FINGER_BEARER, FingerBearerEncounter::tick);
    }

    /** Fights happen in a cursed realm (the room's seal pulls in whoever steps into the room). Tests may turn it off. */
    public static boolean inRealm = true;

    static void tick(ServerLevel level, CursedEncounters.Room room, List<ServerPlayer> inside) {
        if (inRealm) {
            // The room itself stays empty: whoever walks in is taken to its realm, where it waits.
            for (ServerPlayer p : inside) {
                if (dev.rick.jjk.progression.investigation.CursedRealms.pulling(p) || dev.rick.jjk.progression.investigation.CursedRealms.inRealm(p)) continue;
                String key = CursedEncounters.keyOf(room);
                dev.rick.jjk.progression.investigation.CursedRealms.pull(p, p.position(), () -> {
                    CursedEncounters.Room live = CursedEncounters.byKey(level.getServer(), key);
                    if (live != null && live.state != CursedEncounters.State.CLEARED) {
                        dev.rick.jjk.progression.investigation.CursedRealms.enterRoom(p, key,
                                dev.rick.jjk.progression.investigation.InvestigationState.get(level.getServer()));
                    }
                });
            }
            return;
        }
        Entity existing = room.spirit == null ? null : level.getEntity(room.spirit);
        if (existing instanceof FingerBearerEntity fb && !fb.isRemoved()) {
            room.missing = 0;
            return;
        }
        if (inside.isEmpty()) return;
        if (room.state == CursedEncounters.State.ACTIVE && room.spirit != null && ++room.missing < MISSING_CHECKS) return;
        spawn(level, room);
    }

    /** Raises the room's Finger Bearer over its seal. */
    @Nullable
    public static FingerBearerEntity spawn(ServerLevel level, CursedEncounters.Room room) {
        FingerBearerEntity fb = ModEntities.FINGER_BEARER.create(level, EntitySpawnReason.STRUCTURE);
        if (fb == null) return null;
        Vec3 at = Vec3.atBottomCenterOf(room.seal).add(0, 1, 0);
        fb.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0f);
        fb.setHome(room.seal, room.radius);
        double health = Math.max(20, JJKConfig.get().progression.fingerBearerHealth);
        var attr = fb.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(health);
        fb.setHealth((float) health);
        if (!level.addFreshEntity(fb)) return null;
        room.spirit = fb.getUUID();
        room.missing = 0;
        CursedEncounters.setState(level.getServer(), room, CursedEncounters.State.ACTIVE);
        Fx.play(level, "fb_manifest", at.add(0, 1.5, 0), Vec3.ZERO, 1f, fb.getId());
        Fx.sound(level, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
        JJK.LOGGER.info("Finger Bearer raised in the battle room at {} {}", room.dimension, room.seal.toShortString());
        return fb;
    }

    /**
     * A battle room's arena, every tick: raises the spirit when someone is inside and it isn't there (first arrival, or
     * lost without dying), and reports how the fight stands: 1 won (the room is cleared), -1 the room is gone, 0 going on.
     */
    public static int tickArena(net.minecraft.server.MinecraftServer server, dev.rick.jjk.progression.investigation.InvestigationState.Arena a, String roomKey,
                                ServerLevel realm) {
        CursedEncounters.Room room = CursedEncounters.byKey(server, roomKey);
        if (room == null) return -1;
        if (room.state == CursedEncounters.State.CLEARED) return 1;
        if (a.inside().isEmpty() || server.getTickCount() % 40 != 0) return 0;
        Entity existing = room.spirit == null ? null : realm.getEntity(room.spirit);
        if (existing instanceof FingerBearerEntity fb && !fb.isRemoved()) {
            room.missing = 0;
            return 0;
        }
        if (room.spirit != null && ++room.missing < MISSING_CHECKS) return 0;
        FingerBearerEntity fb = ModEntities.FINGER_BEARER.create(realm, EntitySpawnReason.EVENT);
        if (fb == null) return 0;
        Vec3 at = Vec3.atBottomCenterOf(a.origin).add(0, 1, 0);
        fb.snapTo(at.x, at.y, at.z, realm.getRandom().nextFloat() * 360f, 0f);
        fb.setHome(a.origin, dev.rick.jjk.progression.investigation.CursedRealms.arenaRadius(a));
        double health = Math.max(20, JJKConfig.get().progression.fingerBearerHealth);
        var attr = fb.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(health);
        fb.setHealth((float) health);
        if (!realm.addFreshEntity(fb)) return 0;
        room.spirit = fb.getUUID();
        room.missing = 0;
        CursedEncounters.setState(server, room, CursedEncounters.State.ACTIVE);
        Fx.play(realm, "fb_manifest", at.add(0, 1.5, 0), Vec3.ZERO, 1f, fb.getId());
        Fx.sound(realm, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
        JJK.LOGGER.info("Finger Bearer raised in the realm of the battle room {}", roomKey);
        return 0;
    }

    /** A battle room's arena was abandoned (everyone died or left): the room waits again, its spirit gone with the arena. */
    public static void arenaAbandoned(net.minecraft.server.MinecraftServer server, String roomKey) {
        CursedEncounters.Room room = CursedEncounters.byKey(server, roomKey);
        if (room == null || room.state == CursedEncounters.State.CLEARED) return;
        room.spirit = null;
        room.missing = 0;
        CursedEncounters.setState(server, room, CursedEncounters.State.DORMANT);
    }

    /**
     * The spirit died. If it is its room's spirit and the room isn't cleared yet, the room clears and the finger drops:
     * exactly once, whoever (or whatever) landed the last blow, however many players were in the fight.
     */
    static void defeated(ServerLevel level, FingerBearerEntity fb) {
        BlockPos home = fb.home();
        if (home == null || fb.rewarded()) return;
        String dim = level.dimension().identifier().toString();
        var arena = dev.rick.jjk.progression.investigation.CursedRealms.arenaHere(fb);
        if (arena != null) {
            if (arena.incident.startsWith(dev.rick.jjk.progression.investigation.CursedRealms.ROOM)) {
                defeatedInRealm(level, fb, arena, arena.incident.substring(dev.rick.jjk.progression.investigation.CursedRealms.ROOM.length()));
            }
            return;
        }
        CursedEncounters.Room room = CursedEncounters.find(level.getServer(), dim, home);
        if (room == null || room.state == CursedEncounters.State.CLEARED) return;
        if (room.spirit != null && !room.spirit.equals(fb.getUUID())) return;
        fb.markRewarded();
        room.spirit = null;
        CursedEncounters.setState(level.getServer(), room, CursedEncounters.State.CLEARED);
        Vec3 at = fb.position().add(0, 0.6, 0);
        ItemEntity finger = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ProgressionItems.CURSED_FINGER));
        finger.setDeltaMovement(0, 0.25, 0);
        finger.setUnlimitedLifetime();
        finger.setPickUpDelay(30);
        level.addFreshEntity(finger);
        Fx.play(level, "fb_finger", at, Vec3.ZERO, 1f, -1);
        JJK.LOGGER.info("Finger Bearer defeated: battle room at {} {} cleared", dim, home.toShortString());
    }

    /**
     * The realm fight is won: the room clears for good, and its one finger goes to whoever landed the last blow (into
     * their inventory, or at their feet when they come home if it is full), else to whoever is inside. Never left lying in
     * the arena, which is cleared when everyone leaves.
     */
    private static void defeatedInRealm(ServerLevel level, FingerBearerEntity fb, dev.rick.jjk.progression.investigation.InvestigationState.Arena arena, String roomKey) {
        CursedEncounters.Room room = CursedEncounters.byKey(level.getServer(), roomKey);
        if (room == null || room.state == CursedEncounters.State.CLEARED) return;
        if (room.spirit != null && !room.spirit.equals(fb.getUUID())) return;
        fb.markRewarded();
        room.spirit = null;
        CursedEncounters.setState(level.getServer(), room, CursedEncounters.State.CLEARED);
        ServerPlayer to = fb.getLastHurtByMob() instanceof ServerPlayer sp && arena.inside().contains(sp.getUUID()) ? sp : null;
        if (to == null) {
            for (java.util.UUID id : arena.inside()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null && p.isAlive()) {
                    to = p;
                    break;
                }
            }
        }
        Vec3 at = fb.position().add(0, 0.6, 0);
        Fx.play(level, "fb_finger", at, Vec3.ZERO, 1f, -1);
        ItemStack finger = new ItemStack(ProgressionItems.CURSED_FINGER);
        if (to != null && to.getInventory().add(finger)) {
            to.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Something small and dry is in your hand. A finger.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC));
        } else {
            // Nobody could take it: it is left at the room's seal, in the world.
            ServerLevel home = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.Identifier.parse(room.dimension)));
            if (home != null) {
                ItemEntity e = new ItemEntity(home, room.seal.getX() + 0.5, room.seal.getY() + 1.2, room.seal.getZ() + 0.5, finger);
                e.setUnlimitedLifetime();
                home.addFreshEntity(e);
            }
        }
        JJK.LOGGER.info("Finger Bearer defeated in its realm: battle room {} cleared", roomKey);
    }
}
