package dev.rick.jjk.client.camera;

import dev.rick.jjk.client.fx.ScreenEffects;
import dev.rick.jjk.config.JJKConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Who owns the camera, frame by frame. Every cinematic camera in the mod (domain clash, beam clash, strength contest,
 * training, the Prison Realm's outside view, and the moments still to come) is a {@link Stage} here, applied in order:
 * each may move the view the previous ones left; a {@link Stage#last() last} stage ends the chain (and no shake is added
 * on top of it). Then, unless something holds the view steady, the screen shake. The field of view is decided the same
 * way: the first stage that sets one wins, else the screen effects' punches and the rifle's zoom.
 *
 * <p>Stages that need the third-person view (so the player's own body is drawn) ask for it with {@link #wantsThirdPerson};
 * the director switches to it while any of them is on and gives the player's own choice back after.
 *
 * <p><b>Reduced Camera Motion</b> ({@code client.reducedCameraMotion}) is read from {@link #reducedMotion()} by every
 * cinematic shot: still shots and hard cuts instead of orbits and push-ins, no FOV curves, no shake inside a shot.
 */
public final class CameraDirector {
    /** One camera in the chain. */
    public interface Stage {
        /** {x, y, z, yaw, pitch} for this frame given what the earlier stages left, or null when not in use. */
        @Nullable
        double[] apply(Vec3 pos, float yaw, float pitch);

        /** Whether nothing after it (later stages, the shake) touches the view while it is in use. */
        default boolean last() {
            return false;
        }

        /** The field of view it wants (degrees), or NaN for none. */
        default float fov() {
            return Float.NaN;
        }
    }

    private record Entry(String id, int order, Stage stage) {}

    private static final List<Entry> STAGES = new ArrayList<>();
    private static final List<BooleanSupplier> THIRD_PERSON = new ArrayList<>();
    @Nullable private static CameraType restore;
    @Nullable private static String lastOwner;

    private CameraDirector() {}

    /** Adds a stage; lower {@code order} runs first. */
    public static void register(String id, int order, Stage stage) {
        STAGES.removeIf(e -> e.id.equals(id));
        STAGES.add(new Entry(id, order, stage));
        STAGES.sort(Comparator.comparingInt(Entry::order));
    }

    public static void unregister(String id) {
        STAGES.removeIf(e -> e.id.equals(id));
    }

    /** While {@code on} is true the view is third person (the player's own setting comes back afterwards). */
    public static void wantsThirdPerson(BooleanSupplier on) {
        THIRD_PERSON.add(on);
    }

    public static boolean reducedMotion() {
        return JJKConfig.get().client.reducedCameraMotion;
    }

    /** The stage that last moved the view (tests, debugging), or null. */
    @Nullable
    public static String owner() {
        return lastOwner;
    }

    /**
     * The final view for this frame: {x, y, z, yaw, pitch, moved} where {@code moved} is 1 if the position changed,
     * or null if nothing touched the view at all.
     */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch, float partialTicks, boolean steady) {
        Vec3 pos = vanillaPos;
        float yaw = vanillaYaw, pitch = vanillaPitch;
        boolean moved = false, touched = false;
        lastOwner = null;
        for (Entry e : STAGES) {
            double[] r = e.stage.apply(pos, yaw, pitch);
            if (r == null) continue;
            touched = true;
            moved |= r[0] != pos.x || r[1] != pos.y || r[2] != pos.z;
            pos = new Vec3(r[0], r[1], r[2]);
            yaw = (float) r[3];
            pitch = (float) r[4];
            lastOwner = e.id;
            if (e.stage.last()) return new double[] {pos.x, pos.y, pos.z, yaw, pitch, moved ? 1 : 0};
        }
        if (!steady) {
            float dy = ScreenEffects.yawOffset(partialTicks), dx = ScreenEffects.pitchOffset(partialTicks);
            if (dy != 0 || dx != 0) {
                touched = true;
                yaw += dy;
                pitch += dx;
            }
        }
        return touched ? new double[] {pos.x, pos.y, pos.z, yaw, pitch, moved ? 1 : 0} : null;
    }

    /** The field of view for this frame, from {@code vanilla}. */
    public static float fov(float vanilla, float partialTicks, float zoom) {
        for (Entry e : STAGES) {
            float f = e.stage.fov();
            if (!Float.isNaN(f)) return f;
        }
        float m = ScreenEffects.fovMultiplier(partialTicks) * zoom;
        return m != 1f ? vanilla * m : vanilla;
    }

    /** Each client tick: third person while a stage needs it, the player's own view back when none does. */
    public static void tick(Minecraft mc) {
        if (mc.options == null) return;
        boolean want = false;
        if (mc.player != null && mc.player.isAlive()) for (BooleanSupplier s : THIRD_PERSON) want |= s.getAsBoolean();
        if (want && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            if (restore == null) restore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!want && restore != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(restore);
            restore = null;
        }
    }
}
