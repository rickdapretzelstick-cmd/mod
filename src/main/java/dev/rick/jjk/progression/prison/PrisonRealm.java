package dev.rick.jjk.progression.prison;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.PrisonPayload;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.util.Motion;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Prison Realm: forging the world's one realm, sealing someone in it, the cell they are held in, the two ways out
 * (a solo escape from inside, or a rescue from outside) and the release that may make them the world's Gojo. Every
 * decision is made here, on the server, against {@link PrisonRealmState}; the entity, the blocks and the client only
 * show it.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li><b>One realm.</b> A Dormant Prison Realm (four shulker shells round a nether star) dipped into a full cauldron
 *   of cursed energy wakes into the Prison Realm, but only while the world has none: the world gives it an id, and any
 *   other cube is inert. It is fire-proof and never despawns; destroying it (a cactus, an explosion, the void) frees the
 *   world to forge another.</li>
 *   <li><b>Sealing.</b> Used on a player in front of you (within {@value #RANGE} blocks; sneak-use with nobody there to
 *   seal yourself) it lands at their feet and plays its whole sequence. Until the restraints reach out (2.6 s) they can
 *   escape it by getting more than {@value #DODGE} blocks away; after that they are held and drawn in, and at 5.15 s
 *   the seal closes. Dying, logging out or changing dimension before then fails the seal and the cube drops.</li>
 *   <li><b>Inside.</b> The captive is held in a cell (built far above the realm, in the same dimension and chunk column,
 *   from blocks nothing can break, and given back to the world exactly on release). Leaving it any way at all (a
 *   pearl, a command, a portal, dying and respawning, logging out and back in) puts them straight back. Their
 *   techniques are sealed; their inventory follows the game's normal rules (deaths drop in the cell; those items are
 *   kept from despawning and come out with them).</li>
 *   <li><b>Escaping alone.</b> Four seal locks, one in each wall, glow open in turn on a fixed rhythm (every
 *   {@value #PERIOD} ticks, each a quarter-beat after the last). Use a lock while it glows to break it; using one while
 *   it is dark lashes back and re-forms that stage's broken locks. Break all four to clear a stage: three stages, the
 *   glow shorter each time (0.7 s, 0.45 s, 0.3 s). Then the core in the floor opens: use it. Progress is saved, so a
 *   death, a logout or a restart keeps it.</li>
 *   <li><b>Rescue.</b> Anyone outside can open the grounded realm: sneak and hold use on it, staying within
 *   {@value #RESCUE_REACH} blocks, for 5 s; taking damage, letting go or walking off interrupts it.</li>
 *   <li><b>Release.</b> The cube opens, the captive steps out beside it and the cube is an item again (the same realm).
 *   If the seal was genuine and the release was an escape or a rescue, the captive claims Gojo through the atomic kit
 *   claim: the first player in the world to get there becomes Gojo, nobody after them does. A captive who is offline
 *   or dead at that moment gets their release (and the claim) the moment they are back. An admin freeing someone
 *   (/jjk prison free) never grants anything.</li>
 * </ul>
 */
public final class PrisonRealm {
    // --- The sealing sequence (ticks), timed to the model's full_sequence clip ---
    /** The restraints reach out: from here the target can't get away. */
    public static final int RESTRAIN_AT = 52;
    /** The seal closes (5.15 s). */
    public static final int CAPTURE_AT = 103;
    /** The opening clip's length: the release completes then. */
    public static final int OPEN_TICKS = 24;
    public static final double RANGE = 8, DODGE = 6;

    // --- Escape ---
    public static final int PERIOD = 40, OFFSET = 10;
    /** How long each lock glows, by stage. */
    public static final int[] WINDOWS = {14, 9, 6};
    /** Ticks of grace after a lock goes dark (the client saw it glowing). */
    static final int LATENCY_GRACE = 2;
    public static final int STAGES = 3;

    // --- Rescue ---
    public static final int RESCUE_TICKS = 100;
    public static final double RESCUE_REACH = 3.5;

    // --- The cell: 13 x 8 x 13 outside, 11 x 6 x 11 inside ---
    static final int CELL_W = 13, CELL_H = 8;
    /** Lock positions in the cell (north, east, south, west walls) and the core in the floor. */
    static final int[][] LOCKS = {{6, 3, 0}, {12, 3, 6}, {6, 3, 12}, {0, 3, 6}};
    static final int[] CORE = {6, 0, 6};

    public enum Release { ESCAPE, RESCUE, ADMIN }

    // Runtime only: a rescue under way, and the clock of an opening.
    @Nullable private static UUID rescuer;
    private static int rescueTicks;
    private static long rescueLastUse;
    private static boolean rescueHurt;
    private static long releaseStartedAt = -1;
    private static int missingChecks;

    private PrisonRealm() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PrisonRealm::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(PrisonRealm::recover);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            resetRuntime();
            PrisonRealmState.unload();
        });
        // Nothing in the cell is broken by anyone, even in Creative.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !(level instanceof ServerLevel sl) || !inCell(sl, pos));
        // No building inside (the locks and the core still work), no pouring buckets in.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
            BlockState at = sl.getBlockState(hit.getBlockPos());
            if (at.is(ProgressionBlocks.SEAL_LOCK) || at.is(ProgressionBlocks.PRISON_CORE)) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            if ((held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem) && (inCell(sl, hit.getBlockPos()) || inCell(sl, hit.getBlockPos().relative(hit.getDirection())))) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer sp && isCaptive(sp) && player.getItemInHand(hand).getItem() instanceof BucketItem) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (rescuer != null && entity.getUUID().equals(rescuer) && taken > 0) rescueHurt = true;
        });
        // The cube never despawns on the ground.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.getItem().is(ProgressionItems.PRISON_REALM)) item.setUnlimitedLifetime();
        });
    }

    private static void resetRuntime() {
        rescuer = null;
        rescueTicks = 0;
        rescueHurt = false;
        releaseStartedAt = -1;
        missingChecks = 0;
    }

    public static PrisonRealmState state(MinecraftServer server) {
        return PrisonRealmState.get(server);
    }

    // --- Forging and destruction ---

    /** Whether a Prison Realm may be forged in this world now (none exists). */
    public static boolean canForge(MinecraftServer server) {
        return state(server).realmId == null;
    }

    /** Forges the world's realm: its id, or null if one already exists (or it couldn't be recorded). */
    @Nullable
    public static UUID forge(MinecraftServer server) {
        PrisonRealmState st = state(server);
        if (st.realmId != null) return null;
        st.realmId = UUID.randomUUID();
        st.phase = PrisonRealmState.Phase.ITEM;
        st.forged++;
        if (!st.save()) {
            st.realmId = null;
            st.phase = PrisonRealmState.Phase.NONE;
            return null;
        }
        JJK.LOGGER.info("Prison Realm forged ({})", st.realmId);
        return st.realmId;
    }

    /** The world's realm (as an item) was destroyed: another may be forged. */
    public static void itemDestroyed(ServerLevel level, ItemStack stack) {
        if (!stack.is(ProgressionItems.PRISON_REALM)) return;
        PrisonRealmState st = state(level.getServer());
        UUID id = PrisonRealmItem.realmId(stack);
        if (id == null || !id.equals(st.realmId) || st.phase != PrisonRealmState.Phase.ITEM) return;
        st.realmId = null;
        st.phase = PrisonRealmState.Phase.NONE;
        st.save();
        JJK.LOGGER.info("The Prison Realm ({}) was destroyed; another may be forged", id);
    }

    // --- Sealing ---

    /** The cube used: opens on the player in front of the user (or the user, sneaking). True if it was thrown. */
    public static boolean use(ServerPlayer user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        PrisonRealmState st = state(user.level().getServer());
        UUID id = PrisonRealmItem.realmId(stack);
        if (id == null || !id.equals(st.realmId) || st.phase != PrisonRealmState.Phase.ITEM) {
            user.sendOverlayMessage(Component.literal("The cube is lifeless: this isn't the world's Prison Realm.").withStyle(ChatFormatting.GRAY));
            return false;
        }
        ServerPlayer target = aimed(user);
        if (target == null) {
            if (!user.isShiftKeyDown()) {
                user.sendOverlayMessage(Component.literal("Nobody in front of you to seal (sneak and use it to seal yourself).").withStyle(ChatFormatting.GRAY));
                return false;
            }
            target = user;
        }
        if (!target.isAlive() || target.isSpectator() || isCaptive(target)) return false;
        ServerLevel level = target.level();
        Vec3 at = ground(level, target.position());
        PrisonRealmEntity e = ModEntities.PRISON_REALM.create(level, EntitySpawnReason.TRIGGERED);
        if (e == null) return false;
        Vec3 face = user == target ? target.getLookAngle().scale(-1) : user.position().subtract(at);
        float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90f;
        e.snapTo(at.x, at.y, at.z, yaw, 0f);
        e.realmId = id;
        e.target = target.getUUID();
        if (!level.addFreshEntity(e)) return false;
        st.phase = PrisonRealmState.Phase.SEALING;
        st.dimension = level.dimension().identifier().toString();
        st.pos = BlockPos.containing(at);
        st.entity = e.getUUID();
        st.captive = target.getUUID();
        st.captiveName = target.getName().getString();
        st.capturedAt = -1;
        if (!st.save()) {
            e.discard();
            st.toItem();
            return false;
        }
        // The cube leaves the hand for good (Creative too: no copy stays behind).
        stack.shrink(1);
        Fx.sound(level, at, SoundEvents.ENDER_EYE_DEATH, 1.2f, 0.4f);
        Fx.sound(level, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8f, 0.5f);
        Fx.play(level, "prog_prison_throw", at.add(0, 0.6, 0), Vec3.ZERO, 1f, e.getId());
        target.sendSystemMessage(Component.literal("The Prison Realm opens at your feet. Get away before its restraints reach you!")
                .withStyle(ChatFormatting.DARK_RED));
        JJK.LOGGER.info("{} opened the Prison Realm on {} at {} {}", user.getName().getString(), st.captiveName, st.dimension, st.pos.toShortString());
        return true;
    }

    /** The player in front of the user within range and in sight, or null. */
    @Nullable
    private static ServerPlayer aimed(ServerPlayer user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        ServerPlayer best = null;
        double bestDot = 0.965;
        for (ServerPlayer p : user.level().players()) {
            if (p == user || !p.isAlive() || p.isSpectator()) continue;
            Vec3 to = p.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > RANGE || d < 1e-3) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > bestDot && user.hasLineOfSight(p)) {
                bestDot = dot;
                best = p;
            }
        }
        return best;
    }

    /** Where the cube lands: on the floor under {@code at} (up to 8 blocks down), or where it is. */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        for (int i = 0; i < 8; i++) {
            BlockPos below = p.below(i + 1);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return new Vec3(at.x, below.getY() + 1, at.z);
        }
        return at;
    }

    /** Each tick of the realm's entity: checks it is the world's realm, then runs its sequence. */
    static void entityTick(ServerLevel level, PrisonRealmEntity e) {
        PrisonRealmState st = state(level.getServer());
        boolean mine = e.realmId != null && e.realmId.equals(st.realmId) && e.getUUID().equals(st.entity) && st.pos != null
                && (st.phase == PrisonRealmState.Phase.SEALING || st.phase == PrisonRealmState.Phase.SEALED)
                && level.dimension().identifier().toString().equals(st.dimension);
        if (!mine) {
            // A stale copy (an old save, a duplicate, a seal that already ended): it isn't the realm.
            e.discard();
            return;
        }
        Vec3 anchor = Vec3.atBottomCenterOf(st.pos);
        if (e.position().distanceToSqr(anchor) > 1e-4) e.setPos(anchor.x, anchor.y, anchor.z);
        if (st.phase == PrisonRealmState.Phase.SEALING) sequenceTick(level, e, st);
        else if (!st.releasing.isEmpty() && e.realmPhase() == PrisonRealmEntity.OPENING && e.phaseAge >= OPEN_TICKS) finishRelease(level.getServer(), st);
        else if (st.releasing.isEmpty() && e.realmPhase() != PrisonRealmEntity.SEALED) {
            e.setRealmPhase(PrisonRealmEntity.SEALED);
        }
        if (st.phase == PrisonRealmState.Phase.SEALED && !e.hasGlowingTag()) e.setGlowingTag(true);
    }

    private static void sequenceTick(ServerLevel level, PrisonRealmEntity e, PrisonRealmState st) {
        ServerPlayer t = st.captive == null ? null : level.getServer().getPlayerList().getPlayer(st.captive);
        int a = e.phaseAge;
        if (t == null || !t.isAlive() || t.isSpectator() || t.level() != level) {
            failSeal(level, e, st, t, "The seal found nobody to hold and closed on nothing.");
            return;
        }
        Vec3 c = e.position();
        if (a < RESTRAIN_AT) {
            double dx = t.getX() - c.x, dz = t.getZ() - c.z;
            if (dx * dx + dz * dz > DODGE * DODGE) failSeal(level, e, st, t, "You got clear of the Prison Realm.");
            if (a == 11) Fx.sound(level, c, SoundEvents.SHULKER_BOX_OPEN, 1.4f, 0.4f);
            return;
        }
        if (e.caughtAt == null) {
            e.caughtAt = t.position();
            Fx.sound(level, c, SoundEvents.CHAIN_PLACE, 1.4f, 0.5f);
            Fx.play(level, "prog_prison_restrain", c.add(0, 0.7, 0), t.position().subtract(c), 1f, e.getId());
            t.sendSystemMessage(Component.literal("The restraints have you.").withStyle(ChatFormatting.DARK_RED));
        }
        // Held, and drawn into the cube.
        double k = Mth.clamp((a - RESTRAIN_AT) / (double) (CAPTURE_AT - RESTRAIN_AT), 0, 1);
        k = k * k * (3 - 2 * k);
        Vec3 to = e.caughtAt.lerp(c.add(0, 0.1, 0), k);
        t.teleportTo(level, to.x, to.y, to.z, Set.of(), t.getYRot(), t.getXRot(), false);
        Motion.set(t, Vec3.ZERO);
        t.resetFallDistance();
        if (a >= CAPTURE_AT) capture(level, e, st, t);
    }

    private static void failSeal(ServerLevel level, PrisonRealmEntity e, PrisonRealmState st, @Nullable ServerPlayer t, String why) {
        Vec3 at = e.position();
        e.discard();
        dropRealm(level, at, st.realmId);
        st.toItem();
        st.save();
        if (t != null) t.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
        Fx.sound(level, at, SoundEvents.SHULKER_BOX_CLOSE, 1f, 0.6f);
        JJK.LOGGER.info("The Prison Realm's seal failed at {}: {}", BlockPos.containing(at).toShortString(), why);
    }

    private static void capture(ServerLevel level, PrisonRealmEntity e, PrisonRealmState st, ServerPlayer t) {
        BlockPos cell = cellOrigin(level, st.pos);
        List<PrisonRealmState.Saved> saved = buildCell(level, cell);
        st.cell = cell;
        st.replaced.clear();
        st.replaced.addAll(saved);
        st.phase = PrisonRealmState.Phase.SEALED;
        st.capturedAt = level.getGameTime();
        st.stage = 0;
        st.broken = 0;
        st.releasing = "";
        if (!st.save()) {
            restoreCell(level, cell, saved);
            failSeal(level, e, st, t, "The seal faltered.");
            return;
        }
        e.setRealmPhase(PrisonRealmEntity.SEALED);
        e.setGlowingTag(true);
        Fx.sound(level, e.position(), SoundEvents.SHULKER_BOX_CLOSE, 1.6f, 0.3f);
        Fx.sound(level, e.position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1f, 0.5f);
        Fx.shake(level, e.position(), 16, 0.6f, 10);
        putInCell(t, level, st);
        explain(t);
        JJK.LOGGER.info("{} sealed in the Prison Realm at {} {}", st.captiveName, st.dimension, st.pos.toShortString());
    }

    /** The cell's lower corner: high above the realm in the same column, clear of anything with block data. */
    static BlockPos cellOrigin(ServerLevel level, BlockPos realm) {
        int top = level.getMaxY() - CELL_H;
        for (int y = top; y > realm.getY() + 12; y -= CELL_H + 1) {
            BlockPos o = new BlockPos(realm.getX() - CELL_W / 2, y, realm.getZ() - CELL_W / 2);
            if (clearOfBlockEntities(level, o)) return o;
        }
        // Built up to the sky already: below the world's floor instead (still given back exactly).
        return new BlockPos(realm.getX() - CELL_W / 2, level.getMinY() + 1, realm.getZ() - CELL_W / 2);
    }

    private static boolean clearOfBlockEntities(ServerLevel level, BlockPos o) {
        for (int x = 0; x < CELL_W; x++)
            for (int y = 0; y < CELL_H; y++)
                for (int z = 0; z < CELL_W; z++) if (level.getBlockEntity(o.offset(x, y, z)) != null) return false;
        return true;
    }

    private static void loadArea(ServerLevel level, BlockPos o) {
        for (int cx = o.getX() >> 4; cx <= (o.getX() + CELL_W) >> 4; cx++)
            for (int cz = o.getZ() >> 4; cz <= (o.getZ() + CELL_W) >> 4; cz++) level.getChunk(cx, cz);
    }

    private static List<PrisonRealmState.Saved> buildCell(ServerLevel level, BlockPos o) {
        loadArea(level, o);
        List<PrisonRealmState.Saved> saved = new ArrayList<>();
        BlockState wall = ProgressionBlocks.PRISON_WALL.defaultBlockState();
        for (int x = 0; x < CELL_W; x++) {
            for (int y = 0; y < CELL_H; y++) {
                for (int z = 0; z < CELL_W; z++) {
                    BlockPos p = o.offset(x, y, z);
                    BlockState prev = level.getBlockState(p);
                    if (!prev.isAir()) saved.add(new PrisonRealmState.Saved(p, prev));
                    boolean shell = x == 0 || y == 0 || z == 0 || x == CELL_W - 1 || y == CELL_H - 1 || z == CELL_W - 1;
                    level.setBlock(p, shell ? wall : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        for (int[] l : LOCKS) level.setBlock(o.offset(l[0], l[1], l[2]), ProgressionBlocks.SEAL_LOCK.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(o.offset(CORE[0], CORE[1], CORE[2]), ProgressionBlocks.PRISON_CORE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return saved;
    }

    private static void restoreCell(ServerLevel level, BlockPos o, List<PrisonRealmState.Saved> saved) {
        loadArea(level, o);
        for (int x = 0; x < CELL_W; x++)
            for (int y = 0; y < CELL_H; y++)
                for (int z = 0; z < CELL_W; z++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (PrisonRealmState.Saved s : saved) level.setBlock(s.pos(), s.state(), Block.UPDATE_ALL);
    }

    static AABB cellBox(BlockPos o) {
        return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + CELL_W, o.getY() + CELL_H, o.getZ() + CELL_W);
    }

    /** Whether {@code pos} is part of the current cell. */
    public static boolean inCell(ServerLevel level, BlockPos pos) {
        PrisonRealmState st = state(level.getServer());
        if (st.phase != PrisonRealmState.Phase.SEALED || st.cell == null || !level.dimension().identifier().toString().equals(st.dimension)) return false;
        BlockPos o = st.cell;
        return pos.getX() >= o.getX() && pos.getX() < o.getX() + CELL_W && pos.getY() >= o.getY() && pos.getY() < o.getY() + CELL_H
                && pos.getZ() >= o.getZ() && pos.getZ() < o.getZ() + CELL_W;
    }

    /** Where the captive stands in the cell. */
    static Vec3 cellSpawn(BlockPos o) {
        return new Vec3(o.getX() + 6.5, o.getY() + 1, o.getZ() + 4.5);
    }

    private static void putInCell(ServerPlayer p, ServerLevel level, PrisonRealmState st) {
        Vec3 s = cellSpawn(st.cell);
        p.teleportTo(level, s.x, s.y, s.z, Set.of(), p.getYRot(), 10f, false);
        Motion.set(p, Vec3.ZERO);
        p.resetFallDistance();
        sync(p, st);
    }

    @Nullable
    private static ServerLevel level(MinecraftServer server, String dimension) {
        if (dimension == null || dimension.isEmpty()) return null;
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** Whether this player is the one sealed in the realm right now. */
    public static boolean isCaptive(ServerPlayer p) {
        PrisonRealmState st = state(p.level().getServer());
        return st.phase == PrisonRealmState.Phase.SEALED && p.getUUID().equals(st.captive);
    }

    // --- Every tick: the cell holds, the locks keep their rhythm, a rescue progresses ---

    private static void tick(MinecraftServer server) {
        PrisonRealmState st = state(server);
        applyPending(server, st);
        if (st.phase != PrisonRealmState.Phase.SEALED || st.cell == null || st.pos == null) return;
        ServerLevel level = level(server, st.dimension);
        if (level == null) return;
        long now = level.getGameTime();
        if (!st.releasing.isEmpty()) {
            // The opening never hangs: if the realm's body isn't there to play it, the release completes anyway.
            if (releaseStartedAt < 0) releaseStartedAt = now;
            if (now - releaseStartedAt > OPEN_TICKS + 40) finishRelease(server, st);
            return;
        }
        ServerPlayer captive = st.captive == null ? null : server.getPlayerList().getPlayer(st.captive);
        AABB box = cellBox(st.cell);
        if (captive != null && captive.isAlive()) {
            // Held: anywhere but the cell (another dimension, a pearl out, a command, a respawn) puts them back.
            if (captive.level() != level || !box.contains(captive.position())) putInCell(captive, level, st);
            if (now % 10 == 0) progressHud(captive, st, now);
            if (now % 20 == 0) sync(captive, st);
        }
        if (now % 10 == 0) {
            for (ServerPlayer p : level.players()) {
                if (p != captive && box.contains(p.position())) {
                    Vec3 out = safeSpot(level, st.pos);
                    p.teleportTo(level, out.x, out.y, out.z, Set.of(), p.getYRot(), p.getXRot(), false);
                    p.sendOverlayMessage(Component.literal("The Prison Realm admits only its captive.").withStyle(ChatFormatting.GRAY));
                }
            }
            // Whatever the captive drops in there is kept for them (nothing despawns inside).
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) item.setUnlimitedLifetime();
        }
        if (level.isLoaded(st.cell)) updateLocks(level, st, now);
        if (rescuer != null) rescueTick(server, level, st, captive, now);
        if (now % 40 == 0) checkBody(level, st);
    }

    /** Lock i glows at game time {@code t} in this stage. */
    public static boolean lockOpen(long t, int i, int stage) {
        int w = WINDOWS[Math.min(stage, WINDOWS.length - 1)];
        return Math.floorMod(t - (long) OFFSET * i, PERIOD) < w;
    }

    private static void updateLocks(ServerLevel level, PrisonRealmState st, long now) {
        for (int i = 0; i < 4; i++) {
            int want = (st.broken & (1 << i)) != 0 || st.stage >= STAGES ? 2 : lockOpen(now, i, st.stage) ? 1 : 0;
            BlockPos p = st.cell.offset(LOCKS[i][0], LOCKS[i][1], LOCKS[i][2]);
            BlockState s = level.getBlockState(p);
            if (!s.is(ProgressionBlocks.SEAL_LOCK)) {
                level.setBlock(p, ProgressionBlocks.SEAL_LOCK.defaultBlockState().setValue(PrisonCellBlocks.SealLock.STATE, want), Block.UPDATE_CLIENTS);
                continue;
            }
            if (s.getValue(PrisonCellBlocks.SealLock.STATE) != want) {
                level.setBlock(p, s.setValue(PrisonCellBlocks.SealLock.STATE, want), Block.UPDATE_CLIENTS);
                if (want == 1) Fx.sound(level, Vec3.atCenterOf(p), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.8f + 0.25f * st.stage);
            }
        }
        BlockPos core = st.cell.offset(CORE[0], CORE[1], CORE[2]);
        BlockState c = level.getBlockState(core);
        boolean open = st.stage >= STAGES;
        if (!c.is(ProgressionBlocks.PRISON_CORE) || c.getValue(PrisonCellBlocks.Core.OPEN) != open) {
            level.setBlock(core, ProgressionBlocks.PRISON_CORE.defaultBlockState().setValue(PrisonCellBlocks.Core.OPEN, open), Block.UPDATE_CLIENTS);
        }
    }

    /** The realm's body went missing (killed by a command, a lost chunk): raised again after a few checks. */
    private static void checkBody(ServerLevel level, PrisonRealmState st) {
        if (!level.isLoaded(st.pos) || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.containing(st.pos).pack())) return;
        Entity e = st.entity == null ? null : level.getEntity(st.entity);
        if (e instanceof PrisonRealmEntity && !e.isRemoved()) {
            missingChecks = 0;
            return;
        }
        if (++missingChecks < 3) return;
        missingChecks = 0;
        PrisonRealmEntity body = ModEntities.PRISON_REALM.create(level, EntitySpawnReason.TRIGGERED);
        if (body == null) return;
        Vec3 at = Vec3.atBottomCenterOf(st.pos);
        body.snapTo(at.x, at.y, at.z, 0f, 0f);
        body.realmId = st.realmId;
        body.target = st.captive;
        body.setRealmPhase(PrisonRealmEntity.SEALED);
        body.setGlowingTag(true);
        st.entity = body.getUUID();
        st.save();
        level.addFreshEntity(body);
        JJK.LOGGER.warn("The Prison Realm's body was missing at {}; raised it again", st.pos.toShortString());
    }

    /** Where lock i (0 north, 1 east, 2 south, 3 west) is, or null with nobody sealed. */
    @Nullable
    public static BlockPos lockPos(MinecraftServer server, int i) {
        PrisonRealmState st = state(server);
        return st.cell == null ? null : st.cell.offset(LOCKS[i][0], LOCKS[i][1], LOCKS[i][2]);
    }

    /** The realm's grounded body, if loaded. */
    @Nullable
    public static PrisonRealmEntity body(ServerLevel level) {
        return body(level, state(level.getServer()));
    }

    @Nullable
    private static PrisonRealmEntity body(ServerLevel level, PrisonRealmState st) {
        return st.entity != null && level.getEntity(st.entity) instanceof PrisonRealmEntity e && !e.isRemoved() ? e : null;
    }

    // --- The solo escape ---

    /** A seal lock used. */
    public static void useLock(ServerPlayer p, BlockPos pos) {
        PrisonRealmState st = state(p.level().getServer());
        if (!isCaptive(p) || !st.releasing.isEmpty() || st.cell == null) return;
        int idx = -1;
        for (int i = 0; i < 4; i++) if (st.cell.offset(LOCKS[i][0], LOCKS[i][1], LOCKS[i][2]).equals(pos)) idx = i;
        if (idx < 0) return;
        ServerLevel level = p.level();
        long now = level.getGameTime();
        Vec3 at = Vec3.atCenterOf(pos);
        if (st.stage >= STAGES) {
            p.sendOverlayMessage(Component.literal("Every seal is broken: use the core in the floor.").withStyle(ChatFormatting.AQUA));
            return;
        }
        int bit = 1 << idx;
        if ((st.broken & bit) != 0) {
            p.sendOverlayMessage(Component.literal("That seal is already broken this stage.").withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean open = false;
        for (int g = 0; g <= LATENCY_GRACE && !open; g++) open = lockOpen(now - g, idx, st.stage);
        if (open) {
            st.broken |= bit;
            Fx.sound(level, at, SoundEvents.CHAIN_BREAK, 1.2f, 0.7f + 0.2f * st.stage);
            Fx.play(level, "prog_prison_lock", at, Vec3.ZERO, 1f, p.getId());
            if (st.broken == 15) {
                st.stage++;
                st.broken = 0;
                if (st.stage >= STAGES) {
                    Fx.sound(level, at, SoundEvents.END_PORTAL_FRAME_FILL, 1.2f, 0.6f);
                    p.sendSystemMessage(Component.literal("The last seal breaks. The core in the floor is open: use it to get out.").withStyle(ChatFormatting.AQUA));
                } else {
                    Fx.sound(level, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 0.6f + 0.2f * st.stage);
                    p.sendSystemMessage(Component.literal("Stage " + st.stage + " of " + STAGES + " broken. The seals re-form, and glow for less time.")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        } else {
            // Too soon: it lashes back and this stage's seals re-form.
            st.broken = 0;
            Fx.sound(level, at, SoundEvents.SHULKER_HURT_CLOSED, 1.2f, 0.5f);
            Fx.play(level, "prog_prison_backlash", at, p.position().subtract(at), 1f, p.getId());
            p.hurtServer(level, level.damageSources().magic(), 2f);
            Vec3 push = p.position().subtract(at).multiply(1, 0, 1);
            if (push.lengthSqr() > 1e-4) Motion.set(p, push.normalize().scale(0.6).add(0, 0.25, 0));
            p.sendOverlayMessage(Component.literal("Too soon: the seal lashes back and this stage's seals re-form.").withStyle(ChatFormatting.RED));
        }
        st.save();
        updateLocks(level, st, now);
        sync(p, st);
    }

    /** The core used: the way out once every stage is broken. */
    public static void useCore(ServerPlayer p) {
        PrisonRealmState st = state(p.level().getServer());
        if (!isCaptive(p) || !st.releasing.isEmpty()) return;
        if (st.stage < STAGES) {
            p.sendOverlayMessage(Component.literal("The core is sealed: break the four glowing seals, three times over (stage "
                    + (st.stage + 1) + " of " + STAGES + ").").withStyle(ChatFormatting.GRAY));
            return;
        }
        release(p.level().getServer(), Release.ESCAPE);
    }

    // --- The rescue from outside ---

    /** Someone outside used the grounded realm. */
    public static void interact(ServerPlayer p, PrisonRealmEntity e) {
        PrisonRealmState st = state(p.level().getServer());
        if (st.phase != PrisonRealmState.Phase.SEALED || !e.getUUID().equals(st.entity) || isCaptive(p)) return;
        if (!st.releasing.isEmpty()) return;
        if (!p.isShiftKeyDown()) {
            hint(p);
            return;
        }
        long now = p.level().getGameTime();
        if (rescuer != null && !rescuer.equals(p.getUUID())) {
            p.sendOverlayMessage(Component.literal("Someone is already opening it.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (rescuer == null) {
            rescuer = p.getUUID();
            rescueTicks = 0;
            rescueHurt = false;
            Fx.sound(p.level(), e.position(), SoundEvents.SHULKER_BOX_OPEN, 1f, 0.5f);
        }
        rescueLastUse = now;
    }

    /** What the grounded realm is and how to open it. */
    static void hint(ServerPlayer p) {
        PrisonRealmState st = state(p.level().getServer());
        if (st.phase != PrisonRealmState.Phase.SEALED) return;
        p.sendOverlayMessage(Component.literal("Prison Realm: " + st.captiveName + " is sealed inside. Sneak and hold use on it for 5 s to open it.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void rescueTick(MinecraftServer server, ServerLevel level, PrisonRealmState st, @Nullable ServerPlayer captive, long now) {
        ServerPlayer r = server.getPlayerList().getPlayer(rescuer);
        PrisonRealmEntity e = body(level, st);
        String why = null;
        if (r == null || !r.isAlive() || r.level() != level || e == null) why = "";
        else if (r.distanceToSqr(e) > RESCUE_REACH * RESCUE_REACH) why = "You moved away from the realm.";
        else if (!r.isShiftKeyDown() || now - rescueLastUse > 8) why = "You let go of the realm.";
        else if (rescueHurt) why = "Interrupted: you were hit.";
        if (why != null) {
            if (r != null && !why.isEmpty()) r.sendOverlayMessage(Component.literal(why + " The realm closes again.").withStyle(ChatFormatting.GRAY));
            if (e != null) e.setRescue(0);
            rescuer = null;
            rescueTicks = 0;
            if (captive != null) sync(captive, st);
            return;
        }
        rescueTicks++;
        int pct = rescueTicks * 100 / RESCUE_TICKS;
        e.setRescue(pct);
        if (rescueTicks % 5 == 0) {
            r.sendOverlayMessage(bar("Opening the Prison Realm ", pct, ChatFormatting.LIGHT_PURPLE));
            if (captive != null) captive.sendOverlayMessage(bar("Someone outside is opening the realm ", pct, ChatFormatting.AQUA));
            Fx.sound(level, e.position(), SoundEvents.SCULK_BLOCK_SPREAD, 0.8f, 0.6f + pct / 120f);
        }
        if (rescueTicks >= RESCUE_TICKS) release(server, Release.RESCUE);
    }

    private static MutableComponent bar(String label, int pct, ChatFormatting colour) {
        int filled = Math.round(pct / 10f);
        return Component.literal(label).withStyle(colour).append(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("|".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(" " + pct + "%"));
    }

    // --- Release ---

    /**
     * Decides a release (saved at once) and starts the cube opening; {@link #finishRelease} lets the captive out when the
     * opening ends (or right away when the realm's body isn't loaded). False if nobody is sealed or a release is under way.
     */
    public static boolean release(MinecraftServer server, Release kind) {
        PrisonRealmState st = state(server);
        if (st.phase != PrisonRealmState.Phase.SEALED || !st.releasing.isEmpty()) return false;
        st.releasing = kind.name();
        if (!st.save()) {
            st.releasing = "";
            return false;
        }
        rescuer = null;
        rescueTicks = 0;
        ServerLevel level = level(server, st.dimension);
        PrisonRealmEntity e = level == null ? null : body(level, st);
        releaseStartedAt = level == null ? -1 : level.getGameTime();
        if (e == null) {
            finishRelease(server, st);
            return true;
        }
        e.setRescue(0);
        e.setRealmPhase(PrisonRealmEntity.OPENING);
        Fx.sound(level, e.position(), SoundEvents.SHULKER_BOX_OPEN, 1.6f, 0.35f);
        Fx.sound(level, e.position(), SoundEvents.BEACON_DEACTIVATE, 1f, 0.6f);
        Fx.play(level, "prog_prison_open", e.position().add(0, 0.7, 0), Vec3.ZERO, 1f, e.getId());
        return true;
    }

    /** The captive steps out beside the realm, the cell is given back, the cube is an item again; Gojo if earned. */
    static void finishRelease(MinecraftServer server, PrisonRealmState st) {
        if (st.phase != PrisonRealmState.Phase.SEALED || st.releasing.isEmpty()) return;
        Release kind;
        try {
            kind = Release.valueOf(st.releasing);
        } catch (IllegalArgumentException e) {
            kind = Release.ADMIN;
        }
        boolean genuine = kind != Release.ADMIN && st.capturedAt >= 0;
        ServerLevel level = level(server, st.dimension);
        UUID who = st.captive;
        String name = st.captiveName;
        if (level == null || st.pos == null || who == null) {
            st.toItem();
            st.save();
            return;
        }
        level.getChunk(st.pos.getX() >> 4, st.pos.getZ() >> 4);
        Vec3 spot = safeSpot(level, st.pos);
        ServerPlayer captive = server.getPlayerList().getPlayer(who);
        boolean present = captive != null && captive.isAlive();
        if (st.cell != null) {
            loadArea(level, st.cell);
            AABB box = cellBox(st.cell);
            // Everything that was in the cell comes out with them (their dropped items, orbs, anything else).
            for (Entity x : level.getEntitiesOfClass(Entity.class, box, x -> !(x instanceof ServerPlayer))) {
                if (x instanceof ItemEntity || x instanceof ExperienceOrb || !(x instanceof PrisonRealmEntity)) x.teleportTo(spot.x, spot.y + 0.3, spot.z);
            }
            if (present) leaveCell(captive, level, spot);
            restoreCell(level, st.cell, st.replaced);
        } else if (present) {
            leaveCell(captive, level, spot);
        }
        PrisonRealmEntity body = body(level, st);
        if (body != null) body.discard();
        dropRealm(level, Vec3.atBottomCenterOf(st.pos).add(0, 0.3, 0), st.realmId);
        st.toItem();
        if (!present) st.pending.add(new PrisonRealmState.Pending(who, level.dimension().identifier().toString(), spot, genuine));
        st.save();
        releaseStartedAt = -1;
        Fx.sound(level, spot, SoundEvents.SHULKER_BOX_CLOSE, 1f, 1.2f);
        JJK.LOGGER.info("{} released from the Prison Realm ({}{})", name, kind, present ? "" : ", applied when they are back");
        if (present) arrive(captive, genuine);
    }

    private static void leaveCell(ServerPlayer p, ServerLevel level, Vec3 spot) {
        p.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), p.getYRot(), p.getXRot(), false);
        Motion.set(p, Vec3.ZERO);
        p.resetFallDistance();
        ServerPlayNetworking.send(p, new PrisonPayload(false, 0L, 0, 0, 0));
    }

    /** Out: Gojo if this was a genuine seal and release (the claim decides whether he is still free). */
    private static void arrive(ServerPlayer p, boolean genuine) {
        Fx.play(p.level(), "prog_prison_release", p.position().add(0, 1, 0), Vec3.ZERO, 1f, p.getId());
        if (genuine) TechniqueProgression.acquire(p, PrisonRealmAcquisition.INSTANCE);
        else p.sendOverlayMessage(Component.literal("Freed from the Prison Realm.").withStyle(ChatFormatting.GRAY));
    }

    /** Releases owed to captives who were offline or dead: applied once they are back and alive. */
    private static void applyPending(MinecraftServer server, PrisonRealmState st) {
        if (st.pending.isEmpty()) return;
        boolean changed = false;
        for (PrisonRealmState.Pending p : List.copyOf(st.pending)) {
            ServerPlayer player = server.getPlayerList().getPlayer(p.captive());
            if (player == null || !player.isAlive()) continue;
            st.pending.remove(p);
            changed = true;
            ServerLevel level = level(server, p.dimension());
            if (level != null) leaveCell(player, level, p.at());
            else ServerPlayNetworking.send(player, new PrisonPayload(false, 0L, 0, 0, 0));
            // Saved before the claim: a crash mid-claim can't hand the same release out twice.
            st.save();
            arrive(player, p.genuine());
        }
        if (changed) st.save();
    }

    /** A spot beside the realm with room to stand and a floor (or the realm's own spot, one up). */
    static Vec3 safeSpot(ServerLevel level, BlockPos realm) {
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] d : ring) {
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos feet = realm.offset(d[0], dy, d[1]);
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                        && level.getFluidState(feet).isEmpty()) {
                    return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        return Vec3.atBottomCenterOf(realm.above());
    }

    private static void dropRealm(ServerLevel level, Vec3 at, @Nullable UUID id) {
        if (id == null) return;
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, PrisonRealmItem.create(id));
        item.setDeltaMovement(0, 0.15, 0);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(20);
        level.addFreshEntity(item);
    }

    // --- Start-up: nothing left half-done ---

    private static void recover(MinecraftServer server) {
        resetRuntime();
        PrisonRealmState st = state(server);
        if (st.phase == PrisonRealmState.Phase.SEALED && !st.releasing.isEmpty()) {
            JJK.LOGGER.info("Finishing the Prison Realm release that was under way when the server stopped");
            finishRelease(server, st);
        } else if (st.phase == PrisonRealmState.Phase.SEALING) {
            // A seal can't survive a restart half-closed: it fails, the cube drops where it lay.
            ServerLevel level = level(server, st.dimension);
            if (level != null && st.pos != null) {
                level.getChunk(st.pos.getX() >> 4, st.pos.getZ() >> 4);
                dropRealm(level, Vec3.atBottomCenterOf(st.pos).add(0, 0.3, 0), st.realmId);
            }
            JJK.LOGGER.info("A Prison Realm seal was mid-sequence at shutdown; it failed and the cube was dropped");
            st.toItem();
            st.save();
        }
    }

    // --- The captive's screen ---

    private static void sync(ServerPlayer p, PrisonRealmState st) {
        ServerPlayNetworking.send(p, new PrisonPayload(true, st.pos == null ? 0L : st.pos.asLong(), st.stage, st.broken,
                rescuer == null ? 0 : rescueTicks * 100 / RESCUE_TICKS));
    }

    private static void progressHud(ServerPlayer p, PrisonRealmState st, long now) {
        if (rescuer != null) return;
        MutableComponent m;
        if (st.stage >= STAGES) {
            m = Component.literal("Every seal is broken: use the core in the floor").withStyle(ChatFormatting.AQUA);
        } else {
            m = Component.literal("Prison Realm  stage " + (st.stage + 1) + "/" + STAGES + "  seals " + Integer.bitCount(st.broken) + "/4")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("  use a seal while it glows  ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.keybind("key.jjk.prison_view").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("] look outside").withStyle(ChatFormatting.DARK_GRAY));
        }
        p.sendOverlayMessage(m);
    }

    private static void explain(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("You are sealed in the Prison Realm.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("To escape alone: the four seals in the walls glow open in turn, on a steady rhythm. Use a seal "
                + "while it glows to break it; using one while it's dark lashes back and the stage's seals re-form. Break all four, three times "
                + "(the glow gets shorter each time), then use the core in the floor.").withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("Anyone outside can open the realm: sneak and hold use on it for 5 seconds.").withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("Press ").withStyle(ChatFormatting.GRAY).append(Component.keybind("key.jjk.prison_view").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" to look outside (watching only).").withStyle(ChatFormatting.GRAY)));
    }

    // --- Administration ---

    /** Frees the captive without granting anything (admin). */
    public static boolean adminFree(MinecraftServer server) {
        return release(server, Release.ADMIN);
    }

    /** Forgets the world's realm entirely (admin, for a cube lost beyond recovery). Frees a captive first. */
    public static void adminReset(MinecraftServer server) {
        PrisonRealmState st = state(server);
        if (st.phase == PrisonRealmState.Phase.SEALED) {
            st.releasing = Release.ADMIN.name();
            finishRelease(server, st);
        }
        st.realmId = null;
        st.toItem();
        st.save();
        resetRuntime();
    }

    /** Applies a sealed player's techniques lock: they can't cast while sealed. */
    public static boolean techniquesSealed(Entity e) {
        return e instanceof ServerPlayer p && isCaptive(p);
    }

    /** True if {@code level} is a server level and the realm's world entity is {@code e}. */
    public static boolean isRealmBody(Level level, Entity e) {
        return level instanceof ServerLevel sl && e.getUUID().equals(state(sl.getServer()).entity);
    }
}
