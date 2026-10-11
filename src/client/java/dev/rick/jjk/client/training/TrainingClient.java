package dev.rick.jjk.client.training;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingEvaluator;
import dev.rick.jjk.progression.training.TrainingNet;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The participant's side of a training session. It shows what the server decided and reports key timings: the three
 * training keys are taken the instant their key events arrive (not on the next tick), stamped with this client's own
 * challenge clock, and sent with the check they were meant for. A local copy of the server's evaluator answers each
 * press at once; the server's verdict replaces it when it arrives.
 *
 * <p>While a session runs every other game input is held back (movement, the mouse, inventory, abilities) and given
 * back afterwards; only Escape (the pause menu, with "Leave Training"), chat and the function keys pass.
 */
public final class TrainingClient {
    /** The three training keys: left, centre, right gauge, the same for every character. */
    private static final KeyMapping[] KEYS = new KeyMapping[3];

    /** One attempt as this client sees it. */
    public static final class Attempt {
        public final TrainingNet.Start start;
        public final TrainingEvaluator eval;
        public int state = TrainingNet.INTRO, misses;
        public long stateSince = System.nanoTime();
        /** The challenge clock (ms; negative through the intro), advanced only while the game isn't paused. */
        public double clock;
        long lastNanos = System.nanoTime();
        int seq;
        /** Per gauge: the last verdict shown, its check, and when (ms of the challenge). */
        public final int[] lastVerdict = {-1, -1, -1}, lastCheck = {-1, -1, -1};
        public final double[] lastAt = new double[3];
        /** Per gauge: down (as far as this client reported). */
        public final boolean[] down = new boolean[3];
        /** The Retry/Leave screen has been shown for this failure (it isn't forced open again). */
        boolean offeredRetry;

        Attempt(TrainingNet.Start start) {
            this.start = start;
            this.eval = new TrainingEvaluator(start.checks(), start.maxMisses(), 0, 1_000_000);
            this.clock = -start.introMs();
        }

        public List<TrainingCheck> checks() {
            return start.checks();
        }

        public double stateSeconds() {
            return (System.nanoTime() - stateSince) / 1e9;
        }
    }

    @Nullable private static Attempt attempt;
    private static long lastHeard;

    private TrainingClient() {}

    public static void init() {
        // J/K/L would be the natural defaults, but this mod already uses J (mastery) and K (characters), and Minecraft
        // L (advancements); V/B/N are free in both. Rebind them in Controls under the JJK category.
        int[] defaults = {InputConstants.KEY_V, InputConstants.KEY_B, InputConstants.KEY_N};
        String[] names = {"left", "center", "right"};
        for (int i = 0; i < 3; i++) {
            KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jjk.training_" + names[i], InputConstants.Type.KEYBOARD, defaults[i], InputHandler.category()));
        }
    }

    // --- Queries ---

    @Nullable
    public static Attempt attempt() {
        return attempt;
    }

    /** A session is running here (any state before it ends). */
    public static boolean active() {
        return attempt != null;
    }

    /** Game input is held back (the session's intro, challenge and endings). */
    public static boolean capturing() {
        Minecraft mc = Minecraft.getInstance();
        return attempt != null && mc.player != null;
    }

    /** A strength contest (the same skill check, taps only, with its tug-of-war meter and cinematic camera). */
    public static boolean contest() {
        return attempt != null && "contest".equals(attempt.start.kit());
    }

    /** The contest's meter as the server last sent it (0-100), the line it must end above, and who it is against. */
    public static float meter = 50, threshold = 50;
    public static String foe = "";

    public static void meter(TrainingNet.Meter m) {
        Attempt a = attempt;
        if (a == null || a.start.session() != m.session()) return;
        meter = m.meter();
        threshold = m.threshold();
        foe = m.foe();
    }

    /** A seal's containment (the same skill check, no scene: the world stays in view). */
    public static boolean containment() {
        return attempt != null && "capture".equals(attempt.start.kit());
    }

    /** Yuta's training: the practice katana is drawn (shown on the participant only, owned by the session). */
    public static boolean katana() {
        return attempt != null && "yuta".equals(attempt.start.kit());
    }

    /** The key bound to a gauge, as a short label. */
    public static String keyLabel(int gauge) {
        KeyMapping k = KEYS[gauge];
        if (k == null || k.isUnbound()) return "-";
        String s = k.getTranslatedKeyMessage().getString();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    // --- Network ---

    public static void offer(TrainingNet.Offer o) {
        Minecraft mc = Minecraft.getInstance();
        if (attempt == null) mc.gui.setScreen(new TrainingScreens.OfferScreen(o));
    }

    public static void start(TrainingNet.Start s) {
        Minecraft mc = Minecraft.getInstance();
        attempt = new Attempt(s);
        lastHeard = System.nanoTime();
        send(TrainingNet.READY, -1, -1, 0);
        if (mc.gui.screen() instanceof TrainingScreens.ResultScreen) mc.gui.setScreen(null);
        if ("contest".equals(s.kit())) {
            meter = 50;
            ContestCamera.begin();
        } else if (!"capture".equals(s.kit())) TrainingCamera.begin();
    }

    public static void state(TrainingNet.State s) {
        Attempt a = attempt;
        lastHeard = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (s.state() == TrainingNet.ENDED) {
            end();
            return;
        }
        if (a == null || a.start.session() != s.session() || a.start.attempt() != s.attempt()) return;
        if (a.state != s.state()) {
            a.state = s.state();
            a.stateSince = System.nanoTime();
            a.offeredRetry = false;
            if (s.state() != TrainingNet.ACTIVE) releaseAll(a);
            if (s.state() != TrainingNet.FAILURE && s.state() != TrainingNet.SYNC_LOST && mc.gui.screen() instanceof TrainingScreens.ResultScreen) mc.gui.setScreen(null);
            if (s.state() == TrainingNet.SYNC_LOST) mc.gui.setScreen(new TrainingScreens.ResultScreen(true));
        }
        a.misses = s.misses();
        // Keep the clocks together: this one runs a one-way trip behind the server's, which is what the server allows for.
        if ((s.state() == TrainingNet.INTRO || s.state() == TrainingNet.ACTIVE) && Math.abs(a.clock - s.clockMs()) > 200) a.clock = s.clockMs();
    }

    public static void judge(TrainingNet.Judge j) {
        Attempt a = attempt;
        if (a == null || a.start.session() != j.session() || a.start.attempt() != j.attempt()) return;
        if (j.check() < 0 || j.check() >= a.checks().size()) return;
        a.eval.force(j.check(), j.result(), j.misses());
        a.misses = j.misses();
        int g = a.checks().get(j.check()).gauge();
        if (a.lastCheck[g] != j.check() || a.lastVerdict[g] != j.result()) {
            a.lastCheck[g] = j.check();
            a.lastVerdict[g] = j.result();
            a.lastAt[g] = a.clock;
        }
    }

    // --- Clock and input ---

    /** Advances the challenge clock by real time (not frames), only while the game runs. */
    public static void advance() {
        Attempt a = attempt;
        if (a == null) return;
        long now = System.nanoTime();
        double d = Math.min(100.0, (now - a.lastNanos) / 1_000_000.0);
        a.lastNanos = now;
        if (Minecraft.getInstance().isPaused()) return;
        if (a.state == TrainingNet.INTRO || a.state == TrainingNet.ACTIVE) a.clock += d;
    }

    /**
     * A key event, as it arrives. Returns true if training took it (the caller cancels it): the training keys, and every
     * other gameplay key while a session runs.
     */
    public static boolean key(KeyEvent event, int action) {
        Attempt a = attempt;
        Minecraft mc = Minecraft.getInstance();
        if (a == null || mc.player == null || mc.gui.screen() != null) return false;
        int k = event.key();
        if (k == InputConstants.KEY_ESCAPE || k == InputConstants.KEY_F1 || k == InputConstants.KEY_F2 || k == InputConstants.KEY_F3
                || k == InputConstants.KEY_F11 || mc.options.keyChat.matches(event) || mc.options.keyCommand.matches(event)
                || mc.options.keyPlayerList.matches(event) || mc.options.keyScreenshot.matches(event)) {
            return false;
        }
        for (int g = 0; g < 3; g++) {
            if (KEYS[g] != null && !KEYS[g].isUnbound() && KEYS[g].matches(event)) {
                if (action == 1) press(a, g);
                else if (action == 0) release(a, g);
                return true;
            }
        }
        return true;
    }

    private static void press(Attempt a, int g) {
        advance();
        if (a.state != TrainingNet.ACTIVE || a.down[g]) return;
        a.down[g] = true;
        double t = a.clock;
        TrainingCheck c = a.eval.candidate(g, t);
        int id = c == null ? -1 : c.id();
        int seq = ++a.seq;
        send(TrainingNet.PRESS, g, id, (int) Math.round(t), seq);
        predict(a, a.eval.input(seq, TrainingEvaluator.PRESS, g, id, t, t), t);
    }

    private static void release(Attempt a, int g) {
        advance();
        if (!a.down[g]) return;
        a.down[g] = false;
        double t = a.clock;
        TrainingCheck c = a.eval.holding(g);
        int id = c == null ? -1 : c.id();
        int seq = ++a.seq;
        send(TrainingNet.RELEASE, g, id, (int) Math.round(t), seq);
        predict(a, a.eval.input(seq, TrainingEvaluator.RELEASE, g, id, t, t), t);
    }

    private static void predict(Attempt a, @Nullable TrainingEvaluator.Outcome o, double t) {
        if (o == null) return;
        int g = a.checks().get(o.check()).gauge();
        a.lastCheck[g] = o.check();
        a.lastVerdict[g] = o.result();
        a.lastAt[g] = t;
        if (o.result() == TrainingCheck.MISS) a.misses = Math.max(a.misses, a.eval.misses());
    }

    /** Lets go of every held training key (focus lost, a screen opened, the challenge over). */
    private static void releaseAll(Attempt a) {
        for (int g = 0; g < 3; g++) if (a.down[g]) release(a, g);
        a.eval.forgetKeys();
    }

    private static void send(int action, int gauge, int check, int ms) {
        Attempt a = attempt;
        if (a == null) return;
        send(action, gauge, check, ms, ++a.seq);
    }

    private static void send(int action, int gauge, int check, int ms, int seq) {
        Attempt a = attempt;
        if (a == null || Minecraft.getInstance().getConnection() == null) return;
        ClientPlayNetworking.send(new TrainingNet.Input(a.start.session(), a.start.attempt(), seq, action, gauge, check, ms));
    }

    /** Answers from the screens. */
    public static void retry() {
        send(TrainingNet.RETRY, -1, -1, 0);
    }

    public static void leave() {
        send(TrainingNet.LEAVE, -1, -1, 0);
    }

    public static void cancel() {
        send(TrainingNet.CANCEL, -1, -1, 0);
    }

    // --- Lifecycle ---

    public static void tick(Minecraft mc) {
        Attempt a = attempt;
        if (a == null) return;
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        advance();
        // Focus lost or a screen open: held keys are let go (a hold being held is released as of now).
        if (!mc.isWindowActive() || mc.gui.screen() != null) {
            boolean any = false;
            for (boolean d : a.down) any |= d;
            if (any) releaseAll(a);
        }
        // Training keys never leak into anything else.
        for (KeyMapping k : KEYS) if (k != null) while (k.consumeClick()) {}
        if (a.state == TrainingNet.ACTIVE) {
            for (TrainingEvaluator.Outcome o : a.eval.expire(a.clock - 60)) predict(a, o, a.clock);
        }
        // A containment has no Retry/Leave: the remnant waits, and another seal use starts again.
        if (a.state == TrainingNet.FAILURE && a.stateSeconds() > 2.0 && !a.offeredRetry && mc.gui.screen() == null && !containment() && !contest()) {
            a.offeredRetry = true;
            mc.gui.setScreen(new TrainingScreens.ResultScreen(false));
        }
        // Nothing heard for a long while (the server lost the session): let go here too.
        if ((System.nanoTime() - lastHeard) / 1e9 > 8) end();
    }

    private static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TrainingScreens.ResultScreen) mc.gui.setScreen(null);
        attempt = null;
        TrainingCamera.reset();
        ContestCamera.reset();
    }

    public static void reset() {
        attempt = null;
        TrainingCamera.reset();
        ContestCamera.reset();
    }
}
