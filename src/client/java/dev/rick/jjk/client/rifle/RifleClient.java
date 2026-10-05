package dev.rick.jjk.client.rifle;

import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.core.net.RifleStatePayload;
import dev.rick.jjk.core.net.ScopeViewPayload;
import dev.rick.jjk.progression.tool.rifle.RifleAim;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What this client knows about every Cursed Rifle in view: its phase (from the server, for the shooter and everyone
 * tracking them) and the model's clips, played on their own key (so a rifle's pose never mixes with its holder's body
 * clips). Each phase plays its clip from the rifle's animation file at the speed that makes it last exactly as long as
 * the server's phase does: deploy, charge, charged_hold (looping while ready), beam_fire (held for as long as the beam is,
 * a clash included), cooldown, retract; a normal shot plays normal_shot over the idle. Anything that ends early (a
 * cancel, switching items, the reserve running dry, death, leaving) arrives as a phase change, and the pose follows it.
 * {@code full_sequence} is an authoring showcase only and is never played here.
 */
public final class RifleClient {
    public static final String RIG = "cursed_rifle";

    public static final class State {
        public RifleServer.Phase phase = RifleServer.Phase.IDLE;
        public int duration;
        public float output = 0.45f;
        public float energy, capacity = 100;
        public boolean beam;
        public float sway = 1f;
        public int settle = 16;
        /** Game time the phase began (for its progress, and for the aim's settle). */
        public long since;
        /** Game time of the last normal shot (the recoil kick). */
        public long shotAt = -1000;
        String playing = "";

        public float progress(float time) {
            return duration <= 0 ? 1f : Mth.clamp((time - since) / duration, 0f, 1f);
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    /** The lodge's mounted scope, for the local player. */
    private static boolean scopeOn, scopeRevealed;
    private static float scopeProgress;
    private static float zoom = 1f;

    private RifleClient() {}

    /** The key the rifle's clips play under: apart from the holder's own (entity ids are never negative). */
    public static int animKey(int entityId) {
        return -1_000_000 - entityId;
    }

    @Nullable
    public static State state(int entityId) {
        return STATES.get(entityId);
    }

    public static State orIdle(int entityId) {
        State s = STATES.get(entityId);
        return s == null ? new State() : s;
    }

    public static void apply(RifleStatePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), k -> new State());
        RifleServer.Phase phase = RifleServer.Phase.values()[Mth.clamp(p.phase(), 0, RifleServer.Phase.values().length - 1)];
        long now = mc.level.getGameTime();
        if (phase != s.phase) {
            s.phase = phase;
            s.since = now;
        }
        s.duration = p.duration();
        s.output = p.output();
        s.energy = p.energy();
        s.capacity = p.capacity();
        s.beam = p.beam();
        s.sway = p.sway();
        s.settle = p.settle();
        clips(p.entity(), s, now);
    }

    /** The clip for the phase now. */
    private static void clips(int id, State s, float now) {
        String want = switch (s.phase) {
            case DEPLOY -> "deploy";
            case CHARGE -> "charge";
            case READY -> "charged_hold";
            case FIRE -> "beam_fire";
            case COOLDOWN -> "cooldown";
            case RETRACT -> "retract";
            default -> "idle";
        };
        if (want.equals(s.playing)) return;
        s.playing = want;
        int key = animKey(id);
        String name = RIG + "_" + want;
        Clip c = AnimLibrary.get(name);
        if (want.equals("idle")) {
            // Let the beam sequence go (blending back to rest), and the idle sway runs underneath.
            ClientAnimations.play(key, "", 1f, now);
            ClientAnimations.play(key, name, 1f, now);
            return;
        }
        float speed = 1f;
        // Stretched to the server's phase, so the arms are fully out exactly when it says they are.
        // (Looping charged_hold runs at its own pace; beam_fire plays once and holds its last frame for as long as the beam.)
        if (c != null && s.duration > 0 && !c.loop && !want.equals("beam_fire")) {
            speed = c.duration / (s.duration * 50f);
        }
        ClientAnimations.play(key, name, Math.max(0.2f, speed), now);
    }

    /** A normal shot was fired by {@code id} (the server's "rifle_shot"). */
    public static void shot(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.computeIfAbsent(id, k -> new State());
        s.shotAt = mc.level.getGameTime();
        if (s.phase == RifleServer.Phase.IDLE || s.phase == RifleServer.Phase.AIM) {
            ClientAnimations.play(animKey(id), RIG + "_normal_shot", 1f, s.shotAt);
            s.playing = "normal_shot";
        }
    }

    /** Every client tick: shots finish into the idle again, and rifles whose holders are gone are forgotten. */
    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            STATES.clear();
            scopeOn = false;
            return;
        }
        long now = mc.level.getGameTime();
        STATES.entrySet().removeIf(e -> {
            Entity ent = mc.level.getEntity(e.getKey());
            boolean gone = !(ent instanceof LivingEntity l) || !l.isAlive();
            if (gone) ClientAnimations.forget(animKey(e.getKey()));
            return gone;
        });
        for (Map.Entry<Integer, State> e : STATES.entrySet()) {
            State s = e.getValue();
            if (s.playing.equals("normal_shot") && now - s.shotAt > 10) {
                s.playing = "";
                clips(e.getKey(), s, now);
            }
        }
        float want = 1f;
        if (scopeOn) want = 0.28f;
        else if (scoped(mc)) want = 0.42f;
        zoom += (want - zoom) * 0.35f;
        if (Math.abs(zoom - want) < 0.002f) zoom = want;
    }

    /** The rifle's own pose on {@code e} at render time {@code now}, or null at rest. */
    @Nullable
    public static PoseFrame pose(LivingEntity e, float now) {
        int key = animKey(e.getId());
        if (ClientAnimations.player(key) == null) ClientAnimations.play(key, RIG + "_idle", 1f, now);
        return ClientAnimations.computeKey(key, now);
    }

    // --- The local shooter ---

    /** Whether the local player is looking down the rifle's scope (held aim). */
    public static boolean scoped(Minecraft mc) {
        if (mc.player == null) return false;
        State s = STATES.get(mc.player.getId());
        return s != null && s.phase == RifleServer.Phase.AIM && mc.level != null && mc.level.getGameTime() - s.since >= 4;
    }

    /** The reticle's drift right now (the same as the server's aim), in degrees {yaw, pitch}. */
    public static float[] drift(Minecraft mc) {
        State s = mc.player == null ? null : STATES.get(mc.player.getId());
        if (s == null || mc.level == null) return new float[] {0, 0};
        long now = mc.level.getGameTime();
        int aim = (int) (now - s.since);
        return RifleAim.drift(now, mc.player.getId(), aim, s.sway, s.settle, aim >= 4,
                dev.rick.jjk.config.JJKConfig.get().rifle.hipSwayDegrees);
    }

    public static float zoom() {
        return zoom;
    }

    public static void scope(ScopeViewPayload p) {
        scopeOn = p.on();
        scopeProgress = p.progress();
        scopeRevealed = p.revealed();
    }

    public static boolean scopeOn() {
        return scopeOn;
    }

    public static float scopeProgress() {
        return scopeProgress;
    }

    public static boolean scopeRevealed() {
        return scopeRevealed;
    }

    public static void clear() {
        for (int id : STATES.keySet()) ClientAnimations.forget(animKey(id));
        STATES.clear();
        scopeOn = false;
        zoom = 1f;
    }

    /** Where a rifle's beam_origin was last drawn in the world (third person), by holder, for the beam to start there. */
    private static final Map<Integer, Vec3> MUZZLES = new HashMap<>();
    private static final Map<Integer, Long> MUZZLE_AT = new HashMap<>();

    public static void muzzleDrawn(int id, Vec3 at, long frame) {
        MUZZLES.put(id, at);
        MUZZLE_AT.put(id, frame);
    }

    @Nullable
    public static Vec3 muzzle(int id, long frame) {
        Long at = MUZZLE_AT.get(id);
        return at == null || frame - at > 3 ? null : MUZZLES.get(id);
    }
}
