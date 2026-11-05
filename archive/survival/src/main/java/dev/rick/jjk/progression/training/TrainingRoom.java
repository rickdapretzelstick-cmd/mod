package dev.rick.jjk.progression.training;

import dev.rick.jjk.progression.investigation.CursedRealms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A character's training room, as a realm layout: its {@link TrialRooms} plan (the screening room, the hall of
 * precision, the shrine, the crucible, the jackpot palace) built in the trial's own arena. The scene adds its props and
 * working parts ({@link TrainingScene#build}); the realm clears it all when the session ends.
 *
 * <p>{@link #ID} is the old shared room's id, still registered so an arena saved under it before this version (a session
 * that was running at a restart) resolves and is closed and cleared like any other: new sessions use {@link #idFor}.
 */
public final class TrainingRoom implements CursedRealms.Layout {
    public static final String ID = "training_room";
    private final String kit;

    public TrainingRoom(String kit) {
        this.kit = kit;
    }

    public static String idFor(String kit) {
        return ID + "_" + (TrialRooms.KITS.contains(kit) ? kit : "yuji");
    }

    @Nullable
    private TrialRooms.Plan plan() {
        return TrialRooms.plan(kit);
    }

    @Override
    public void build(ServerLevel l, BlockPos o, RandomSource r) {
        TrialRooms.Plan p = plan();
        if (p != null) TrialRooms.build(l, o, p);
        else TrialRooms.buildFallback(l, o);
    }

    static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** On the floor where the participant first stands (the scene seats or places them from there). */
    @Override
    public Vec3 arrival() {
        TrialRooms.Plan p = plan();
        if (p == null) return Vec3.ZERO;
        Vec3 m = p.marker("player_floor") != null ? p.marker("player_floor") : p.marker("player");
        return new Vec3(m.x - 0.5, Math.max(0, m.y), m.z - 0.5);
    }

    @Override
    public List<Vec3> curseSpots() {
        return List.of();
    }

    @Override
    public float arrivalYaw() {
        return 0f;
    }

    @Override
    public int radius() {
        TrialRooms.Plan p = plan();
        if (p == null) return 12;
        int[] b = p.bounds();
        return Math.max(Math.max(-b[0], b[3]), Math.max(-b[2], b[5]));
    }

    @Override
    public String entryLine() {
        TrialRooms.Plan p = plan();
        return p != null ? p.title() + ". A room made for you alone." : "A quiet room, made for you alone.";
    }
}
