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
 * defeat clears the room for good and leaves the room's one Cursed Finger where it fell; eating that finger is the same
 * claim as any other ({@link dev.rick.jjk.progression.CursedFingerAcquisition}: the first eater in the world becomes its
 * Yuji, a stranger after that dies of it). A cleared room never spawns again, so a room gives one finger, ever.
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

    static void tick(ServerLevel level, CursedEncounters.Room room, List<ServerPlayer> inside) {
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
     * The spirit died. If it is its room's spirit and the room isn't cleared yet, the room clears and the finger drops:
     * exactly once, whoever (or whatever) landed the last blow, however many players were in the fight.
     */
    static void defeated(ServerLevel level, FingerBearerEntity fb) {
        BlockPos home = fb.home();
        if (home == null || fb.rewarded()) return;
        String dim = level.dimension().identifier().toString();
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
}
