package dev.rick.jjk.client.rifle;

import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.anim.rig.Bone;
import dev.rick.jjk.client.anim.rig.Rig;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The Cursed Rifle's combat body, for whoever has it drawn: the whole figure fights with the gun, not just an arm.
 *
 * <p>Each frame, from what this client knows (the server's rifle phase and its timing, the last shot, the move clips
 * the server played, and how the holder is moving), it builds a full-body pose and places the rifle in model space:
 *
 * <ul>
 *   <li><b>Stance.</b> Bladed (hips and chest turned toward the trigger side, the support foot forward, knees soft), the
 *   rifle at a relaxed low ready with its stock in the shoulder pocket. Aim runs head, shoulders, torso, arms: the torso
 *   follows part of the pitch and yaw, the head looks exactly where the player looks, the rifle points along the aim.</li>
 *   <li><b>Aim (ADS).</b> The stock rises to the shoulder and the scope's eyepiece comes to the eye, wherever the eye is
 *   (it is placed from the head's own transform, so it stays on the eye looking up or down); the head leans in to the
 *   stock.</li>
 *   <li><b>Locomotion.</b> Walking, strafing and backing up swing the legs along the direction of travel under a steady
 *   upper body; sprinting lowers the rifle across the chest; jumping tucks the legs, falling reaches for the ground,
 *   landing sinks into the knees.</li>
 *   <li><b>Shots.</b> Every round kicks the rifle back into the shoulder and the muzzle up, absorbed by the chest; a
 *   braced or scoped shot kicks less.</li>
 *   <li><b>Unfolding Array.</b> The feet plant wide and the body sinks into a brace as the arms deploy; the charge
 *   vibrates through the rifle and the arms holding it, building; ready, it holds a live brace; firing, the body is
 *   driven back against the sustained recoil; then it straightens and the rifle comes back to the ready.</li>
 * </ul>
 *
 * <p>Both hands are then solved onto the rifle (two-bone reach for each arm: the dominant hand on the pistol grip, the
 * support hand under the fore-end, elbows down and out), so the grip never floats whatever the body does: hit
 * reactions, move clips and the draw all move the rifle, and the hands stay on it. The rifle model is drawn at the
 * placed transform by {@link dev.rick.jjk.client.gear.CursedGearLayer}, its own clips (arms, iris) playing on it, and its
 * beam_origin there is where shots and the beam are drawn from.
 *
 * <p>Nothing here decides anything: it all follows state the server synced, so every client draws the same action.
 */
public final class RifleStance {
    public static final RenderStateDataKey<View> VIEW = RenderStateDataKey.create(() -> "jjk:rifle_stance");

    /** Player pixels per rifle (Blockbench) pixel when it is in the hands. */
    public static float SCALE = 0.39f;
    // Points on the rifle model (Blockbench pixels: x right, y up, the muzzle toward -z).
    public static final Vector3f BUTT = new Vector3f(0, 12.6f, 22.9f);
    public static final Vector3f GRIP = new Vector3f(0, 9.0f, 5.6f);
    public static final Vector3f FORE = new Vector3f(0, 7.6f, -5.0f);
    public static final Vector3f EYEPIECE = new Vector3f(0, 21f, 13.4f);
    public static final Vector3f MUZZLE = new Vector3f(0, 14f, -19.4f);

    /** Tuning aid: extra offsets read by tests (pocket x/y/z, ready pitch, eye relief). */
    public static float[] TUNE = {0, 0, 0, 0, 0};

    /** Everything about one rifle holder for one frame, taken when the render state is extracted. */
    public static final class View {
        public int id;
        public float now;
        public RifleServer.Phase phase = RifleServer.Phase.IDLE;
        public float phaseAge, phaseLen;
        public float pitch, yaw;
        public float fwd, side, vy;
        public boolean ground = true, sprint, crouch;
        public float walkPos, walkSpeed;
        public float shotAge = 99;
        /** Ticks into each move clip, or -1. */
        public float snap = -1, volley = -1, flare = -1, bash = -1;
        public boolean leftHanded;
        // Smoothed blends (0..1).
        public float ads, brace, sprintW, air, land, weight, charge;
        /** The rifle's own clip pose (its arms, iris). */
        @Nullable public PoseFrame model;
        /** Rifle model pixels to model space pixels, once placed this frame. */
        public final Matrix4f rifle = new Matrix4f();
        public boolean placed;
        /** How far (pixels) each hand ended from its point on the rifle (0 when it reached). */
        public float gripError, foreError;
    }

    private static final class Smooth {
        float ads, brace, sprint, air, land, weight, fall;
        float lastNow = Float.NaN;
        boolean wasGround = true;
    }

    private static final Map<Integer, Smooth> SMOOTH = new HashMap<>();
    private static final Map<Integer, float[]> ERRORS = new HashMap<>();

    private RifleStance() {}

    public static void forget(int id) {
        SMOOTH.remove(id);
    }

    public static void clear() {
        SMOOTH.clear();
        ERRORS.clear();
    }

    /** {grip, fore-end} reach error (pixels) of the last frame drawn for {@code id} (tests). */
    @Nullable
    public static float[] errors(int id) {
        return ERRORS.get(id);
    }

    // --- Extract ---

    public static View extract(Player p, AvatarRenderState s, float partial) {
        View v = new View();
        float now = p.level().getGameTime() + partial;
        int id = p.getId();
        v.id = id;
        v.now = now;
        RifleClient.State rs = RifleClient.orIdle(id);
        v.phase = rs.phase;
        v.phaseAge = now - rs.since;
        v.phaseLen = rs.duration;
        v.shotAge = now - rs.shotAt;
        v.pitch = s.xRot;
        v.yaw = s.yRot;
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        float by = s.bodyRot * Mth.DEG_TO_RAD;
        v.fwd = (float) (-dx * Math.sin(by) + dz * Math.cos(by));
        v.side = (float) (-dx * Math.cos(by) - dz * Math.sin(by));
        v.vy = (float) (p.getY() - p.yo);
        v.ground = p.onGround() || p.isPassenger();
        v.sprint = p.isSprinting();
        v.crouch = s.isCrouching;
        v.walkPos = s.walkAnimationPos;
        v.walkSpeed = Math.min(1f, s.walkAnimationSpeed);
        v.snap = ClientAnimations.elapsed(id, "rf_snap", now);
        v.volley = ClientAnimations.elapsed(id, "rf_volley", now);
        v.flare = ClientAnimations.elapsed(id, "rf_flare", now);
        v.bash = ClientAnimations.elapsed(id, "rf_bash", now);
        v.leftHanded = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
        v.model = RifleClient.pose(p, now);
        smooth(v);
        return v;
    }

    static boolean array(RifleServer.Phase ph) {
        return ph == RifleServer.Phase.DEPLOY || ph == RifleServer.Phase.CHARGE || ph == RifleServer.Phase.READY || ph == RifleServer.Phase.FIRE;
    }

    private static void smooth(View v) {
        Smooth m = SMOOTH.computeIfAbsent(v.id, k -> new Smooth());
        float dt = Float.isNaN(m.lastNow) ? 0 : Mth.clamp(v.now - m.lastNow, 0, 5);
        m.lastNow = v.now;
        boolean snapping = v.snap >= 0 && v.snap < 11;
        boolean volley = v.volley >= 0;
        boolean aiming = v.phase == RifleServer.Phase.AIM || snapping || volley;
        boolean array = array(v.phase);
        float adsT = aiming ? 1f : array ? 0.85f : 0f;
        float braceT = array ? 1f : v.phase == RifleServer.Phase.COOLDOWN ? 0.7f : volley ? 0.55f : v.phase == RifleServer.Phase.AIM ? 0.25f : 0f;
        float sprintT = v.sprint && !aiming && !array && v.flare < 0 && v.bash < 0 ? 1f : 0f;
        float airT = v.ground ? 0f : 1f;
        // A snap shot raises the scope in its first three ticks, faster than a held aim settles.
        m.ads = approach(m.ads, adsT, snapping ? 0.6f : 0.42f, dt);
        m.brace = approach(m.brace, braceT, 0.16f, dt);
        m.sprint = approach(m.sprint, sprintT, 0.22f, dt);
        m.air = approach(m.air, airT, 0.3f, dt);
        if (!v.ground) m.fall = Math.min(m.fall, v.vy);
        if (v.ground && !m.wasGround) m.land = Mth.clamp(-m.fall * 2.2f, 0.25f, 1f);
        if (v.ground) m.fall = 0;
        m.wasGround = v.ground;
        m.land *= (float) Math.pow(0.72, dt);
        m.weight = approach(m.weight, 1f, 0.2f, dt);
        v.ads = m.ads;
        v.brace = m.brace;
        v.sprintW = m.sprint;
        v.air = m.air;
        v.land = m.land;
        v.weight = m.weight;
        v.charge = v.phase == RifleServer.Phase.CHARGE ? Mth.clamp(v.phaseAge / Math.max(1, v.phaseLen), 0, 1)
                : v.phase == RifleServer.Phase.READY || v.phase == RifleServer.Phase.FIRE ? 1f : 0f;
    }

    private static float approach(float x, float target, float rate, float dt) {
        return x + (target - x) * (1 - (float) Math.pow(1 - rate, dt));
    }

    // --- The body ---

    /** Degrees and pixels per bone, built up by the stance's parts, then layered into a frame. */
    private static final class Body {
        final float[][] rot = new float[Bone.COUNT][3];
        final float[][] pos = new float[Bone.COUNT][3];
        final boolean[] posed = new boolean[Bone.COUNT];
        final boolean[] moved = new boolean[Bone.COUNT];

        void r(Bone b, float x, float y, float z, float k) {
            float[] r = rot[b.ordinal()];
            r[0] += x * k;
            r[1] += y * k;
            r[2] += z * k;
            posed[b.ordinal()] = true;
        }

        void p(Bone b, float right, float up, float fwd, float k) {
            float[] q = pos[b.ordinal()];
            q[0] += right * k;
            q[1] += up * k;
            q[2] += fwd * k;
            moved[b.ordinal()] = true;
        }

        void into(PoseFrame f, float w) {
            float d = Mth.DEG_TO_RAD;
            for (Bone b : Bone.ALL) {
                int i = b.ordinal();
                if (posed[i]) f.layer(Clip.ROT, b, new float[] {rot[i][0] * d, rot[i][1] * d, rot[i][2] * d}, w);
                if (moved[i]) f.layer(Clip.POS, b, pos[i], w);
            }
        }
    }

    private static Body body(View v) {
        Body b = new Body();
        float ready = 1;
        // The bladed ready stance: support foot forward, trigger side back, knees soft, the torso into the gun.
        float blade = 1 - v.sprintW;
        b.r(Bone.HIPS, 0, 26, 0, blade);
        b.r(Bone.CHEST, 6, 12, 0, ready);
        b.r(Bone.LEFT_THIGH, -14, -18, -4, ready);
        b.r(Bone.LEFT_SHIN, 16, 0, 0, ready);
        b.r(Bone.LEFT_FOOT, -2, 0, 0, ready);
        b.r(Bone.RIGHT_THIGH, 10, -6, 7, ready);
        b.r(Bone.RIGHT_SHIN, 12, 0, 0, ready);
        b.r(Bone.RIGHT_FOOT, -6, 0, 0, ready);
        b.p(Bone.ROOT, 0, -0.45f, 0, ready);

        // Aim propagates: the torso takes part of the pitch and the turn; the head (look) takes the rest.
        float pitch = Mth.clamp(v.pitch, -70, 70), yaw = Mth.clamp(v.yaw, -50, 50);
        b.r(Bone.CHEST, pitch * 0.3f, yaw * 0.35f, 0, 1);
        b.r(Bone.HIPS, 0, yaw * 0.15f, 0, 1);

        // Scoped: deeper into the gun, the head leaning to the stock.
        b.r(Bone.CHEST, 4, 5, 0, v.ads);
        b.r(Bone.NECK, 6, 0, -9, v.ads);

        // Braced (the array, a volley): feet planted wide, sunk into the knees.
        b.r(Bone.LEFT_THIGH, -10, 0, -7, v.brace);
        b.r(Bone.LEFT_SHIN, 16, 0, 0, v.brace);
        b.r(Bone.LEFT_FOOT, -5, 0, 0, v.brace);
        b.r(Bone.RIGHT_THIGH, 12, 0, 9, v.brace);
        b.r(Bone.RIGHT_SHIN, 8, 0, 0, v.brace);
        b.r(Bone.RIGHT_FOOT, -8, 0, 0, v.brace);
        b.r(Bone.CHEST, 6, 0, 0, v.brace);
        b.p(Bone.ROOT, 0, -1.3f, 0, v.brace);

        // The beam's sustained recoil drives the body back; the cooldown lets it straighten with an exhale.
        if (v.phase == RifleServer.Phase.FIRE) {
            float k = Mth.clamp(v.phaseAge / 4f, 0, 1);
            b.r(Bone.CHEST, -7, 0, 0, k);
            b.p(Bone.ROOT, 0, 0, -0.9f, k);
            b.r(Bone.RIGHT_THIGH, 8, 0, 0, k);
            b.r(Bone.RIGHT_SHIN, -6, 0, 0, k);
            b.r(Bone.LEFT_SHIN, 6, 0, 0, k);
        } else if (v.phase == RifleServer.Phase.COOLDOWN) {
            float k = (float) Math.sin(Math.PI * Mth.clamp(v.phaseAge / Math.max(1, v.phaseLen), 0, 1));
            b.r(Bone.CHEST, -4, 0, 0, k);
            b.r(Bone.NECK, -5, 0, 0, k);
        }
        // The charge shudders through the torso as it builds.
        if (v.charge > 0) {
            float a = (0.3f + 1.2f * v.charge) * (v.phase == RifleServer.Phase.READY ? 0.6f : 1f);
            if (v.phase == RifleServer.Phase.FIRE) a = 1.1f;
            float t = v.now * 2.3f;
            b.r(Bone.CHEST, (float) Math.sin(t * 3.1f) * a, (float) Math.sin(t * 2.3f + 1) * a * 0.6f, (float) Math.sin(t * 4.1f + 2) * a * 0.5f, 1);
        }

        // Sprinting: the rifle comes down across the chest, the body leans into the run.
        b.r(Bone.CHEST, 14, -6, 0, v.sprintW);

        // Legs: swing along the direction of travel (walk, strafe, back up), planted while braced.
        float speed = v.walkSpeed * (1 - 0.8f * v.brace) * (1 - v.air);
        if (speed > 0.01f) {
            float phase = v.walkPos * 0.6662f;
            double dir = Math.atan2(v.side, Math.max(1e-4, Math.abs(v.fwd))) - 22 * Mth.DEG_TO_RAD * blade;
            if (v.fwd < -0.01f) dir = -dir;
            float amp = (v.sprintW > 0.5f ? 52 : 34) * speed;
            float s = (float) Math.cos(phase) * amp;
            float cx = (float) Math.cos(dir), sz = (float) Math.sin(dir);
            b.r(Bone.RIGHT_THIGH, s * cx, 0, s * sz * 0.8f, 1);
            b.r(Bone.LEFT_THIGH, -s * cx, 0, -s * sz * 0.8f, 1);
            // Knees lift as each leg comes through.
            float lift = (v.sprintW > 0.5f ? 70 : 40) * speed;
            b.r(Bone.RIGHT_SHIN, Math.max(0, (float) -Math.sin(phase)) * lift, 0, 0, 1);
            b.r(Bone.LEFT_SHIN, Math.max(0, (float) Math.sin(phase)) * lift, 0, 0, 1);
            // A steady platform: the body bobs a little, the rifle doesn't wave.
            b.p(Bone.ROOT, 0, -Math.abs((float) Math.sin(phase)) * 0.5f * speed, 0, 1);
            b.r(Bone.CHEST, 3 * speed, 0, 0, 1);
        }

        // In the air: legs tucked rising, reaching down falling.
        if (v.air > 0.01f) {
            float falling = Mth.clamp(-v.vy * 3f, 0, 1);
            b.r(Bone.LEFT_THIGH, Mth.lerp(falling, -32, -14), 0, 0, v.air);
            b.r(Bone.RIGHT_THIGH, Mth.lerp(falling, -10, -2), 0, 0, v.air);
            b.r(Bone.LEFT_SHIN, Mth.lerp(falling, 44, 16), 0, 0, v.air);
            b.r(Bone.RIGHT_SHIN, Mth.lerp(falling, 34, 12), 0, 0, v.air);
            b.r(Bone.LEFT_FOOT, 10, 0, 0, v.air);
            b.r(Bone.CHEST, 4, 0, 0, v.air);
        }
        // Landing sinks into the knees.
        if (v.land > 0.01f) {
            b.p(Bone.ROOT, 0, -2.2f, 0, v.land);
            b.r(Bone.LEFT_THIGH, -20, 0, 0, v.land);
            b.r(Bone.RIGHT_THIGH, -16, 0, 0, v.land);
            b.r(Bone.LEFT_SHIN, 38, 0, 0, v.land);
            b.r(Bone.RIGHT_SHIN, 34, 0, 0, v.land);
            b.r(Bone.LEFT_FOOT, -18, 0, 0, v.land);
            b.r(Bone.RIGHT_FOOT, -18, 0, 0, v.land);
            b.r(Bone.CHEST, 8, 0, 0, v.land);
        }
        // Crouched: low in the knees (the renderer already drops the model two pixels).
        if (v.crouch) {
            b.p(Bone.ROOT, 0, 0.8f, 0, 1);
            b.r(Bone.LEFT_THIGH, -30, 0, 0, 1);
            b.r(Bone.RIGHT_THIGH, -26, 0, 0, 1);
            b.r(Bone.LEFT_SHIN, 46, 0, 0, 1);
            b.r(Bone.RIGHT_SHIN, 46, 0, 0, 1);
            b.r(Bone.LEFT_FOOT, -16, 0, 0, 1);
            b.r(Bone.RIGHT_FOOT, -20, 0, 0, 1);
            b.r(Bone.CHEST, 12, 0, 0, 1);
        }
        // Stock Bash: the hips drive the stroke.
        if (v.bash >= 0) {
            float t = v.bash;
            float wind = t < 2 ? t / 2 : Math.max(0, 1 - (t - 2) / 1.5f);
            float strike = t < 2 ? 0 : t < 4 ? (t - 2) / 2 : Math.max(0, 1 - (t - 4) / 4f);
            b.r(Bone.HIPS, 0, 14, 0, wind);
            b.r(Bone.CHEST, -4, 18, 0, wind);
            b.r(Bone.HIPS, 0, -20, 0, strike);
            b.r(Bone.CHEST, 10, -22, 0, strike);
            b.r(Bone.LEFT_THIGH, -12, 0, 0, strike);
            b.r(Bone.LEFT_SHIN, 14, 0, 0, strike);
            b.p(Bone.ROOT, 0, -0.6f, 0.8f, strike);
        }
        return b;
    }

    // --- The rifle ---

    /** Where the rifle is this frame (model pixels), from the solved chest and head. */
    private static void place(View v, Rig.Parts parts, Matrix4f[] bones, Matrix4f out) {
        float pitch = Mth.clamp(v.pitch, -80, 80), yaw = Mth.clamp(v.yaw, -60, 60);
        float ready = 1 - v.ads;
        // Low ready: the muzzle down and in; sprinting, across the chest; scoped, exactly along the aim.
        // Sprinting it rides at high port: diagonal across the chest, the muzzle up by the support shoulder.
        float sw = v.sprintW * ready;
        float rp = Mth.lerp(sw, pitch + (22 + TUNE[3]) * ready, -42);
        float ry = Mth.lerp(sw, yaw - 8 * ready, -58);
        float rr = Mth.lerp(sw, 6 * ready, 35);
        // Recoil: back into the shoulder and the muzzle up, then settling (less braced or scoped).
        float kick = recoil(v.shotAge) * (1 - 0.35f * v.ads) * (1 - 0.4f * v.brace);
        rp -= 7 * kick;
        // The charge (and the beam) shake the rifle.
        if (v.charge > 0) {
            float a = v.phase == RifleServer.Phase.FIRE ? 1.6f : (0.25f + 1.3f * v.charge) * (v.phase == RifleServer.Phase.READY ? 0.5f : 1f);
            float t = v.now * 2.3f;
            rp += (float) Math.sin(t * 5.3f) * a;
            ry += (float) Math.sin(t * 4.7f + 1.3f) * a * 0.7f;
            rr += (float) Math.sin(t * 6.1f + 0.4f) * a;
        }
        // Lens Flare: canted on its side to aim the arms' lens, then back.
        if (v.flare >= 0) {
            float k = v.flare < 3 ? v.flare / 3 : Math.max(0, 1 - (v.flare - 6) / 5f);
            rr -= 70 * k;
            rp -= 6 * k;
        }
        // Stock Bash: the rifle turned and the stock driven forward.
        float bashWind = 0, bashStrike = 0;
        if (v.bash >= 0) {
            float t = v.bash;
            bashWind = t < 2 ? t / 2 : Math.max(0, 1 - (t - 2) / 1.5f);
            bashStrike = t < 2 ? 0 : t < 4 ? (t - 2) / 2 : Math.max(0, 1 - (t - 4) / 4f);
            // Wound back with the muzzle rising over the shoulder, then the butt thrust out at chest height.
            rp = Mth.lerp(bashWind, rp, -55);
            rp = Mth.lerp(bashStrike, rp, -100);
            ry = Mth.lerp(bashStrike, ry, -20);
        }
        Matrix3f rot = new Matrix3f().rotationYXZ(ry * Mth.DEG_TO_RAD, rp * Mth.DEG_TO_RAD, rr * Mth.DEG_TO_RAD).mul(BASE);
        Vector3f fwd = rot.transform(new Vector3f(0, 0, -1));

        // The stock in the shoulder pocket (follows the chest), lower and inboard sprinting.
        Matrix4f chest = bones[Bone.CHEST.ordinal()];
        Vector3f restChest = parts.rest(Bone.CHEST);
        Vector3f pocket = new Vector3f(-2.4f + TUNE[0], 1.4f + TUNE[1], -1.8f + TUNE[2]);
        pocket.lerp(new Vector3f(-2.6f, 8.5f, -3.0f), sw);
        Vector3f butt = chest.transformPosition(new Vector3f(pocket).sub(restChest));
        // Scoped: the eyepiece just in front of the eye (from the head's own transform, so it stays there).
        Matrix4f head = bones[Bone.HEAD.ordinal()];
        // The right eye (the left for a left-handed shooter), on the face.
        Vector3f eye = head.transformPosition(new Vector3f(v.leftHanded ? 1.6f : -1.6f, -3.8f, -4.1f).sub(parts.rest(Bone.HEAD)));
        Vector3f eyepiece = rot.transform(new Vector3f(EYEPIECE).sub(BUTT).mul(SCALE));
        Vector3f buttAds = new Vector3f(eye).add(new Vector3f(fwd).mul(1.6f + TUNE[4])).sub(eyepiece);
        butt.lerp(buttAds, v.ads);
        // Kick back along the barrel; the bash drives the stock out.
        butt.sub(new Vector3f(fwd).mul(1.1f * kick));
        // (The barrel now points up and back: out toward the target is the body's forward.)
        Vector3f ahead = chest.transformDirection(new Vector3f(0, 0, -1)).normalize();
        butt.add(new Vector3f(ahead).mul(-1.5f * bashWind + 5f * bashStrike));
        butt.add(0, 3.5f * bashStrike - 1f * bashWind, 0);
        out.identity().translate(butt).mul(new Matrix4f().set(rot)).scale(SCALE).translate(-BUTT.x, -BUTT.y, -BUTT.z);
    }

    /** Blockbench axes (x right, y up, z back) to model space (right -x, up -y, forward -z). */
    private static final Matrix3f BASE = new Matrix3f().rotationZ((float) Math.PI);

    /** 0..1 kick of a shot {@code age} ticks ago: sharp, then settling. */
    static float recoil(float age) {
        if (age < 0 || age > 14) return 0;
        if (age < 0.8f) return age / 0.8f;
        return (float) Math.exp(-(age - 0.8f) * 0.45f);
    }

    /** The rifle in the dominant hand (a clip has the arms: the draw), grip in the fist, pointing where the forearm does. */
    private static void inHand(Matrix4f hand, Matrix4f out) {
        Matrix3f g = new Matrix3f(-1, 0, 0, 0, 0, -1, 0, -1, 0);
        out.set(hand).translate(0, 1f, 0).mul(new Matrix4f().set(g)).scale(SCALE).translate(-GRIP.x, -GRIP.y, -GRIP.z);
    }

    // --- Solve ---

    /**
     * Poses the model for a rifle holder: the stance under any playing clips, the rifle placed, both hands solved onto
     * it. Writes each bone's model-space transform into {@code bones}.
     */
    public static void solve(Rig.Parts parts, View v, @Nullable PoseFrame clips, Matrix4f[] bones) {
        var base = Rig.base(parts);
        PoseFrame f = new PoseFrame();
        body(v).into(f, v.weight);
        if (clips != null) f.over(clips);
        f.look = Math.max(f.look, v.weight);
        Rig.solve(parts, f, bones);

        // The rifle: placed by the stance, or (while the draw brings it round) in the dominant hand.
        Matrix4f stance = new Matrix4f();
        place(v, parts, bones, stance);
        Bone mainHand = v.leftHanded ? Bone.LEFT_HAND : Bone.RIGHT_HAND;
        if (v.weight < 0.999f) {
            Matrix4f hand = new Matrix4f();
            inHand(bones[mainHand.ordinal()], hand);
            blend(hand, stance, v.weight, v.rifle);
        } else {
            v.rifle.set(stance);
        }
        v.placed = true;

        // Both hands onto it.
        Vector3f grip = v.rifle.transformPosition(new Vector3f(GRIP)), fore = v.rifle.transformPosition(new Vector3f(FORE));
        Matrix4f chest = bones[Bone.CHEST.ordinal()];
        Quaternionf chestRot = chest.getNormalizedRotation(new Quaternionf());
        boolean rightMain = !v.leftHanded;
        // Elbows: the trigger arm's out to the side and down (higher scoped), the support arm's down under the gun.
        Vector3f mainPole = chestRot.transform(new Vector3f(rightMain ? -0.75f : 0.75f, Mth.lerp(v.ads, 0.65f, 0.25f), 0.25f));
        Vector3f supportPole = chestRot.transform(new Vector3f(rightMain ? 0.3f : -0.3f, 1f, 0.1f));
        float[] e = new float[2];
        e[0] = arm(parts, bones, f, rightMain, grip, mainPole, v.weight, -12);
        e[1] = arm(parts, bones, f, !rightMain, fore, supportPole, v.weight, -24);
        v.gripError = e[0];
        v.foreError = e[1];
        ERRORS.put(v.id, e);
        Rig.restore(parts, base);
        Rig.solve(parts, f, bones);
    }

    /** Between two placements of the rifle: the butt's position lerped, the rotation slerped. */
    private static void blend(Matrix4f a, Matrix4f b, float t, Matrix4f out) {
        Vector3f buttA = a.transformPosition(new Vector3f(BUTT)), buttB = b.transformPosition(new Vector3f(BUTT));
        Quaternionf qa = a.getNormalizedRotation(new Quaternionf()), qb = b.getNormalizedRotation(new Quaternionf());
        out.translationRotateScale(buttA.lerp(buttB, t), qa.slerp(qb, t), SCALE).translate(-BUTT.x, -BUTT.y, -BUTT.z);
    }

    /**
     * Two-bone reach for one arm: the fist (the hand segment's middle) to {@code target}, the elbow toward
     * {@code pole}. Layers the result into {@code f} with weight {@code w}. Returns how far the fist is left from the
     * target (pixels).
     */
    private static float arm(Rig.Parts parts, Matrix4f[] bones, PoseFrame f, boolean right, Vector3f target, Vector3f pole, float w, float wrist) {
        Bone up = right ? Bone.RIGHT_ARM : Bone.LEFT_ARM, mid = right ? Bone.RIGHT_FOREARM : Bone.LEFT_FOREARM, end = right ? Bone.RIGHT_HAND : Bone.LEFT_HAND;
        Vector3f a = new Vector3f(parts.rest(mid)).sub(parts.rest(up));
        Vector3f fist = new Vector3f(parts.rest(end)).sub(parts.rest(mid)).add(0, 1f, 0);
        Matrix4f chest = bones[Bone.CHEST.ordinal()];
        Vector3f shoulder = chest.transformPosition(new Vector3f(parts.rest(up)).sub(parts.rest(Bone.CHEST)));
        Vector3f toT = new Vector3f(target).sub(shoulder);
        float d = toT.length();
        float la = a.length(), lf = fist.length();
        float max = new Vector3f(a).add(fist).length() - 0.05f;
        // Out of reach: the shoulder comes forward to it (protraction), up to a few pixels.
        if (d > max && d > 1e-4f) {
            float slide = Math.min(d - max + 0.05f, 3.5f);
            Vector3f shift = new Vector3f(toT).mul(slide / d);
            shoulder.add(shift);
            Quaternionf inv = chest.getNormalizedRotation(new Quaternionf()).conjugate();
            Vector3f local = inv.transform(new Vector3f(shift));
            f.layer(Clip.POS, up, new float[] {-local.x, -local.y, -local.z}, w);
            toT.set(target).sub(shoulder);
            d = toT.length();
        }
        float min = Math.abs(la - lf) + 0.3f;
        float dc = Mth.clamp(d, min, max);
        // |a + Rx(b) f|^2 = |a|^2 + |f|^2 + 2 a.y f.y cos(b) (f straight down the segment).
        float cos = Mth.clamp((dc * dc - la * la - lf * lf) / (2 * a.y * fist.y), -1, 1);
        float bend = -(float) Math.acos(cos);
        bend = Math.max(bend, -150 * Mth.DEG_TO_RAD);
        Vector3f q = new Vector3f(fist).rotateX(bend).add(a);
        // The arm's frame: q along the line to the target, the elbow toward the pole.
        Vector3f qh = new Vector3f(q).normalize();
        Vector3f el = new Vector3f(a).sub(new Vector3f(qh).mul(a.dot(qh)));
        if (el.lengthSquared() < 1e-6f) el.set(0, 0, 1).sub(new Vector3f(qh).mul(qh.z));
        el.normalize();
        Vector3f th = d < 1e-4f ? new Vector3f(0, 1, 0) : new Vector3f(toT).normalize();
        Vector3f ph = new Vector3f(pole).sub(new Vector3f(th).mul(pole.dot(th)));
        if (ph.lengthSquared() < 1e-6f) ph.set(0, 1, 0).sub(new Vector3f(th).mul(th.y));
        ph.normalize();
        Matrix3f local = new Matrix3f().setColumn(0, qh).setColumn(1, el).setColumn(2, new Vector3f(qh).cross(el));
        Matrix3f world = new Matrix3f().setColumn(0, th).setColumn(1, ph).setColumn(2, new Vector3f(th).cross(ph));
        Matrix3f armWorld = world.mul(local.transpose(new Matrix3f()), new Matrix3f());
        Matrix3f chestRot = new Matrix3f().set(chest.getNormalizedRotation(new Quaternionf()));
        Matrix3f armLocal = chestRot.transpose(new Matrix3f()).mul(armWorld);
        Vector3f eul = new Matrix4f().set(armLocal).getEulerAnglesZYX(new Vector3f());
        f.layer(Clip.ROT, up, new float[] {eul.x, eul.y, eul.z}, w);
        f.layer(Clip.ROT, mid, new float[] {bend, 0, 0}, w);
        f.layer(Clip.ROT, end, new float[] {wrist * Mth.DEG_TO_RAD, 0, 0}, w);
        return Math.max(0, d - max) + Math.max(0, min - d);
    }
}
