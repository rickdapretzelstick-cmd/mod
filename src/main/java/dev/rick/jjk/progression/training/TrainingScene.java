package dev.rick.jjk.progression.training;

import com.mojang.math.Transformation;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A character's scene, set in its own {@link TrainingRoom} (the room's blocks are already standing when it begins): the
 * props it raises (the room's {@link TrialPropEntity} models and its own working parts), the fixed camera, and its hooks
 * (the intro, each check's cue and verdict, the failure and the victory). The session drives it; the scene only shows.
 * Everything it raises is owned by the session and removed when it ends (and the realm clears the room after).
 *
 * <p>Two frames: {@link #rel} is relative to where the participant stands or sits (feet), facing south (+Z);
 * {@link #room}/{@link #block}/{@link #light} are room-local, from the arena origin's corner, as the room plan and its
 * markers are.
 */
public abstract class TrainingScene {
    protected final TrainingSessions.Session session;
    protected ServerLevel realm;
    protected BlockPos origin;
    /** Where the participant stands (feet). */
    protected Vec3 at;
    /** The room's plan (null: it failed to load, and the scene stands on the bare fallback floor). */
    @Nullable protected TrialRooms.Plan plan;
    protected final List<Entity> owned = new ArrayList<>();
    private final List<BlockPos> lights = new ArrayList<>();

    protected TrainingScene(TrainingSessions.Session session) {
        this.session = session;
    }

    /** Dresses the room (once per session): the room's own prop models first, then the scene's parts. */
    final void setup(ServerLevel realm, BlockPos origin) {
        this.realm = realm;
        this.origin = origin;
        this.plan = TrialRooms.plan(session.story.kit());
        Vec3 spot = plan != null ? plan.marker("player") : null;
        this.at = spot != null ? room(spot) : Vec3.atBottomCenterOf(origin);
        if (plan != null) {
            for (TrialRooms.Prop pr : plan.props()) {
                TrialPropEntity e = TrialPropEntity.create(realm, pr.model(), pr.kind(), room(Vec3.ZERO));
                realm.addFreshEntity(e);
                owned.add(e);
                props.put(pr.model(), e);
            }
        }
        build();
    }

    private final java.util.Map<String, TrialPropEntity> props = new java.util.HashMap<>();

    /** The room's prop drawn from {@code model} (e.g. "trial_yuji_crt"), or null. */
    @Nullable
    protected TrialPropEntity prop(String model) {
        return props.get(model);
    }

    /** A room-local point (blocks from the arena origin's corner), in the world. */
    protected Vec3 room(Vec3 local) {
        return new Vec3(origin.getX() + local.x, origin.getY() + local.y, origin.getZ() + local.z);
    }

    /** A marker of the room, in the world, or {@code fallback} relative to the participant. */
    protected Vec3 marker(String name, Vec3 fallback) {
        Vec3 m = plan != null ? plan.marker(name) : null;
        return m != null ? room(m) : at.add(fallback);
    }

    protected abstract void build();

    /** A fresh attempt (the first, or a retry): props back where they start. */
    void reset(ServerPlayer p) {}

    /** Which way the participant faces (Minecraft yaw), and how far down they look. */
    float yaw() {
        return 0f;
    }

    float pitch() {
        return 8f;
    }

    /** Seated (on a seat the scene provides) rather than standing. */
    @Nullable
    Entity seat() {
        return null;
    }

    /** The fixed camera and what it looks at, relative to {@link #at}. */
    abstract Vec3 camera();

    abstract Vec3 look();

    void intro(ServerPlayer p, int tick) {}

    /** A check's cue: its arc has just appeared (and any disturbance it announces begins now). */
    void cue(ServerPlayer p, TrainingCheck c) {}

    /** Every tick of the timed challenge. */
    void active(ServerPlayer p, int tick, double clockMs, int misses) {}

    /** A check decided (PERFECT, GOOD, MISS) or a hold's press landing (HOLDING). */
    void judged(ServerPlayer p, TrainingCheck c, int result, int misses) {}

    void failure(ServerPlayer p, int tick) {}

    void victory(ServerPlayer p, int tick) {}

    /** The session is over: undo everything (also with no player, if they left). */
    void cleanup(@Nullable ServerPlayer p) {
        for (Entity e : owned) if (!e.isRemoved()) e.discard();
        owned.clear();
        props.clear();
    }

    // --- Helpers ---

    protected Vec3 rel(double x, double y, double z) {
        return at.add(x, y, z);
    }

    /** A room-local block position. */
    protected BlockPos bp(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    protected void block(int x, int y, int z, BlockState s) {
        TrainingRoom.set(realm, bp(x, y, z), s);
    }

    /** An invisible light the scene can turn up and down (phase by phase), room-local; never over a solid block. */
    protected void light(int x, int y, int z, int level) {
        BlockPos p = bp(x, y, z);
        if (!realm.getBlockState(p).isAir() && !realm.getBlockState(p).is(Blocks.LIGHT)) return;
        if (!lights.contains(p)) lights.add(p);
        TrainingRoom.set(realm, p, level <= 0 ? Blocks.AIR.defaultBlockState() : Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Mth.clamp(level, 0, 15)));
    }

    /** As {@link #light(int, int, int, int)}, at the block a world point lies in. */
    protected void lightAt(Vec3 world, int level) {
        BlockPos b = BlockPos.containing(world);
        light(b.getX() - origin.getX(), b.getY() - origin.getY(), b.getZ() - origin.getZ(), level);
    }

    protected void lighting(int level) {
        for (BlockPos p : lights) {
            TrainingRoom.set(realm, p, level <= 0 ? Blocks.AIR.defaultBlockState() : Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Mth.clamp(level, 0, 15)));
        }
    }

    /** A block shown as a free-floating prop (scaled about its centre), owned by the session. */
    protected Display.BlockDisplay display(BlockState state, Vec3 pos, float scale, boolean bright) {
        Display.BlockDisplay d = new Display.BlockDisplay(net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY, realm);
        d.setBlockState(state);
        d.setPos(pos.x, pos.y, pos.z);
        d.setPosRotInterpolationDuration(2);
        d.setTransformationInterpolationDuration(2);
        shape(d, scale, 0f, 0f);
        if (bright) d.setBrightnessOverride(Brightness.FULL_BRIGHT);
        realm.addFreshEntity(d);
        owned.add(d);
        return d;
    }

    /** Re-shapes a prop: scale about its centre, turned {@code yaw} and tipped {@code roll} degrees. */
    protected static void shape(Display.BlockDisplay d, float scale, float yaw, float roll) {
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateZ((float) Math.toRadians(roll));
        Vector3f half = new Vector3f(-scale / 2f, -scale / 2f, -scale / 2f);
        q.transform(half);
        d.setTransformationInterpolationDelay(0);
        d.setTransformation(new Transformation(half, q, new Vector3f(scale, scale, scale), null));
    }

    protected static void move(Entity e, Vec3 to) {
        e.setPos(to.x, to.y, to.z);
    }

    protected void sound(ServerPlayer p, SoundEvent s, float volume, float pitch) {
        Fx.sound(realm, p.position(), s, volume, pitch);
    }

    protected void sound(ServerPlayer p, Holder<SoundEvent> s, float volume, float pitch) {
        Fx.sound(realm, p.position(), s, volume, pitch);
    }

    /** Particles for the participant only. */
    protected void particles(ServerPlayer p, ParticleOptions o, Vec3 pos, int count, double spread, double speed) {
        realm.sendParticles(p, o, true, false, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    protected static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb & 0xFFFFFF, size);
    }

    /** A line of particles from {@code a} to {@code b}. */
    protected void stream(ServerPlayer p, ParticleOptions o, Vec3 a, Vec3 b, int steps) {
        for (int i = 0; i <= steps; i++) {
            Vec3 q = a.lerp(b, i / (double) steps);
            realm.sendParticles(p, o, true, false, q.x, q.y, q.z, 1, 0.01, 0.01, 0.01, 0);
        }
    }

    /** A ring of particles around {@code c} in the horizontal plane. */
    protected void ring(ServerPlayer p, ParticleOptions o, Vec3 c, double radius, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            realm.sendParticles(p, o, true, false, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    protected int phase(double clockMs) {
        return TrainingSchedule.phase(session.cfg, clockMs);
    }
}
