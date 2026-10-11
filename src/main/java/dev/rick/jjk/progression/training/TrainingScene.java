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
 * A character's scene, set in the shared {@link TrainingRoom}: the props it adds, the fixed camera, and its hooks (the
 * intro, each check's cue and verdict, the failure and the victory). The session drives it; the scene only shows.
 * Everything it raises is owned by the session and removed when it ends (and the realm clears the room after).
 * Coordinates are relative to where the participant stands, facing south (+Z).
 */
public abstract class TrainingScene {
    protected final TrainingSessions.Session session;
    protected ServerLevel realm;
    protected BlockPos origin;
    /** Where the participant stands (feet). */
    protected Vec3 at;
    protected final List<Entity> owned = new ArrayList<>();
    private final List<BlockPos> lights = new ArrayList<>();

    protected TrainingScene(TrainingSessions.Session session) {
        this.session = session;
    }

    /** Dresses the room (once per session). */
    final void setup(ServerLevel realm, BlockPos origin) {
        this.realm = realm;
        this.origin = origin;
        this.at = Vec3.atBottomCenterOf(origin);
        build();
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
    }

    // --- Helpers ---

    protected Vec3 rel(double x, double y, double z) {
        return at.add(x, y, z);
    }

    protected BlockPos bp(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    protected void block(int x, int y, int z, BlockState s) {
        TrainingRoom.set(realm, bp(x, y, z), s);
    }

    /** An invisible light the scene can turn up and down (phase by phase). */
    protected void light(int x, int y, int z, int level) {
        BlockPos p = bp(x, y, z);
        if (!lights.contains(p)) lights.add(p);
        TrainingRoom.set(realm, p, level <= 0 ? Blocks.AIR.defaultBlockState() : Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Mth.clamp(level, 0, 15)));
    }

    protected void lighting(int level) {
        for (BlockPos p : lights) {
            TrainingRoom.set(realm, p, level <= 0 ? Blocks.AIR.defaultBlockState() : Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Mth.clamp(level, 0, 15)));
        }
    }

    /** A block shown as a free-floating prop (scaled about its centre), owned by the session. */
    protected Display.BlockDisplay display(BlockState state, Vec3 pos, float scale, boolean bright) {
        Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, realm);
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
