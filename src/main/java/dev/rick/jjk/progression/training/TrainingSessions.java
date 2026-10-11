package dev.rick.jjk.progression.training;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.CharacterStory;
import dev.rick.jjk.progression.story.RelicAcquisition;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The training controller: one private session per participant, in its own copy of the {@link TrainingRoom} (a realm
 * arena, so the participant is isolated and returned home afterwards by the realm's own return records).
 *
 * <pre>
 * offer (the relic) → pull → INTRO (2 s) → ACTIVE (45 s) → SUCCESS → return
 *                                                       ↘ FAILURE / SYNC_LOST → Retry (new attempt) | Leave → return
 * cancel, disconnect, death, a restart: cleanup, never a reward
 * </pre>
 *
 * The server owns everything: it generates each attempt's schedule from a fresh seed, scores the participant's key
 * timings ({@link TrainingEvaluator}) against its own clock, and grants the base kit once, on the first success of a
 * real (non-practice) session. During a session the participant is held still, can't be hurt, and spends nothing.
 */
public final class TrainingSessions {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Offers made by a relic, answerable for a while (a client can't start a session nobody offered). */
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private static int nextId = 1;
    /** Test hook: a hand-driven clock (milliseconds advance only through {@link #advanceForTest}). */
    private static boolean manualClock;
    /** Ticks a failed attempt waits for Retry/Leave before the participant is sent home. */
    static final int IDLE_TICKS = 2400;

    private record Offer(CharacterStory story, boolean practice, boolean test, long until) {}

    private TrainingSessions() {}

    // --- A session ---

    public static final class Session {
        public final int id;
        public final UUID player;
        public final CharacterStory story;
        public final boolean practice, test;
        final String key;
        final JJKConfig.Training cfg;
        @Nullable InvestigationState.Arena arena;
        @Nullable TrainingScene scene;
        int attempt;
        long seed;
        List<TrainingCheck> checks = List.of();
        TrainingEvaluator eval = new TrainingEvaluator(List.of(), 5, 0, 0);
        boolean[] cued = new boolean[0];
        int state = -1, stateTicks, activeTicks;
        /** Milliseconds of the challenge (negative through the intro). */
        double clock;
        long lastNanos;
        boolean ready, granted, arrived;

        Session(int id, UUID player, CharacterStory story, boolean practice, boolean test, JJKConfig.Training cfg) {
            this.id = id;
            this.player = player;
            this.story = story;
            this.practice = practice;
            this.test = test;
            this.cfg = cfg;
            this.key = "training:" + story.kit() + ":" + player;
        }

        public int state() {
            return state;
        }

        public int attempt() {
            return attempt;
        }

        public double clock() {
            return clock;
        }

        public TrainingEvaluator evaluator() {
            return eval;
        }

        public List<TrainingCheck> checks() {
            return checks;
        }

        public boolean granted() {
            return granted;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TrainingSessions::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            SESSIONS.clear();
            OFFERS.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            OFFERS.remove(handler.player.getUUID());
            Session s = SESSIONS.get(handler.player.getUUID());
            if (s != null) finish(server, s, handler.player, null);
        });
        // Nothing hurts someone in training.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && SESSIONS.containsKey(p.getUUID())));
    }

    // --- Queries ---

    @Nullable
    public static Session of(ServerPlayer p) {
        return SESSIONS.get(p.getUUID());
    }

    public static boolean inTraining(ServerPlayer p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    /** Whether someone is in a real (claiming) session of this character's training right now. */
    public static boolean claimingSessionOf(CharacterStory story) {
        for (Session s : SESSIONS.values()) if (s.story == story && !s.practice && !s.test) return true;
        return false;
    }

    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Session s : SESSIONS.values()) {
            out.add(s.story.kit() + " #" + s.id + " attempt " + s.attempt + " state " + stateName(s.state) + " misses " + s.eval.misses()
                    + (s.practice ? " [practice]" : "") + (s.test ? " [test]" : ""));
        }
        return out;
    }

    static String stateName(int state) {
        return switch (state) {
            case TrainingNet.INTRO -> "INTRO";
            case TrainingNet.ACTIVE -> "ACTIVE";
            case TrainingNet.SUCCESS -> "SUCCESS";
            case TrainingNet.FAILURE -> "FAILURE";
            case TrainingNet.SYNC_LOST -> "SYNC_LOST";
            case TrainingNet.ENDED -> "ENDED";
            default -> "ARRIVING";
        };
    }

    // --- Offers ---

    /** The relic offers its training ("Begin Training", or "Practice Again" to its owner). */
    public static void offer(ServerPlayer p, CharacterStory story, boolean practice, boolean test, String line) {
        long now = p.level().getServer().overworld().getGameTime();
        OFFERS.put(p.getUUID(), new Offer(story, practice, test, now + JJKConfig.get().training.offerTicks));
        ServerPlayNetworking.send(p, new TrainingNet.Offer(story.kit(), story.name(), practice, story.color(), line));
    }

    /** The participant answered an offer. Eligibility is checked again now (the relic may have moved meanwhile). */
    static void answer(ServerPlayer p, String kit, boolean accept) {
        Offer o = OFFERS.remove(p.getUUID());
        if (!accept || o == null || !o.story.kit().equals(kit)) return;
        if (p.level().getServer().overworld().getGameTime() > o.until) return;
        String why = dev.rick.jjk.progression.story.PersonalTrials.recheck(p, o.story, o.practice, o.test);
        if (why != null) {
            if (!why.isEmpty()) p.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
            return;
        }
        begin(p, o.story, o.practice, o.test);
    }

    // --- Starting ---

    /** Starts a session (the pull into its room first). Returns false if the participant is busy. */
    public static boolean begin(ServerPlayer p, CharacterStory story, boolean practice, boolean test) {
        if (SESSIONS.containsKey(p.getUUID()) || CursedRealms.inRealm(p) || CursedRealms.pulling(p)) return false;
        Session s = new Session(nextId++, p.getUUID(), story, practice, test, JJKConfig.get().training);
        SESSIONS.put(p.getUUID(), s);
        CursedRealms.pull(p, p.position(), () -> arrive(s, p));
        JJK.LOGGER.info("[training] {} begins {} training{}{}", p.getName().getString(), story.kit(), practice ? " (practice)" : "", test ? " (test)" : "");
        return true;
    }

    private static void arrive(Session s, ServerPlayer p) {
        if (SESSIONS.get(p.getUUID()) != s) return;
        MinecraftServer server = p.level().getServer();
        InvestigationState st = InvestigationState.get(server);
        s.arena = CursedRealms.enterTrial(p, s.key, TrainingRoom.ID, st);
        s.arrived = true;
        ServerLevel realm = CursedRealms.level(server);
        s.scene = TrainingScenes.create(s);
        s.scene.setup(realm, s.arena.origin);
        newAttempt(s, p);
    }

    /** A fresh attempt: a new seed and schedule, the scene reset, the intro begins. */
    private static void newAttempt(Session s, ServerPlayer p) {
        s.attempt++;
        JJKConfig.Training cfg = s.cfg;
        List<TrainingCheck> checks = List.of();
        for (int tries = 0; tries < 6; tries++) {
            s.seed = p.getRandom().nextLong();
            checks = TrainingSchedule.generate(s.story.kit(), s.seed, cfg);
            List<String> problems = TrainingSchedule.problems(checks, cfg);
            if (problems.isEmpty()) break;
            JJK.LOGGER.warn("[training] {} schedule (seed {}) rejected: {}", s.story.kit(), s.seed, problems.get(0));
        }
        s.checks = checks;
        s.eval = new TrainingEvaluator(checks, cfg.maxMisses, cfg.maxLatencyMs, cfg.clockSlackMs);
        s.cued = new boolean[checks.size()];
        s.clock = -cfg.introMs;
        s.ready = false;
        s.lastNanos = System.nanoTime();
        s.activeTicks = 0;
        ClashCommon.hold(p);
        if (s.scene != null) {
            place(s, p);
            s.scene.reset(p);
        }
        TrainingPreset preset = TrainingPreset.of(s.story.kit());
        Vec3 cam = s.scene != null ? s.scene.at.add(s.scene.camera()) : p.position();
        Vec3 look = s.scene != null ? s.scene.at.add(s.scene.look()) : p.getEyePosition();
        send(p, new TrainingNet.Start(s.id, s.attempt, s.story.kit(), preset.title(), s.practice, s.seed, cfg.introMs, cfg.durationMs,
                cfg.phase2AtMs, cfg.phase3AtMs, cfg.maxMisses, cam, look, preset.gauges(), preset.colors(), checks));
        setState(s, p, TrainingNet.INTRO);
    }

    /** Puts the participant where the scene wants them (seated if it has a seat), facing its way. */
    private static void place(Session s, ServerPlayer p) {
        TrainingScene sc = s.scene;
        if (sc == null) return;
        Entity seat = sc.seat();
        if (seat != null) {
            if (p.getVehicle() != seat) {
                p.stopRiding();
                p.teleportTo((ServerLevel) p.level(), sc.at.x, sc.at.y, sc.at.z, java.util.Set.of(), sc.yaw(), sc.pitch(), false);
                p.startRiding(seat, true, true);
            }
        } else {
            p.teleportTo((ServerLevel) p.level(), sc.at.x, sc.at.y, sc.at.z, java.util.Set.of(), sc.yaw(), sc.pitch(), false);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // --- Running ---

    private static void tick(MinecraftServer server) {
        if (!OFFERS.isEmpty()) {
            long now = server.overworld().getGameTime();
            OFFERS.values().removeIf(o -> now > o.until);
        }
        if (SESSIONS.isEmpty()) return;
        for (Session s : List.copyOf(SESSIONS.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.player);
            if (p == null) {
                finish(server, s, null, null);
                continue;
            }
            if (!s.arrived) {
                // The pull was cut short (or never took them): nothing to clean up but the session itself.
                if (!CursedRealms.pulling(p) && !CursedRealms.inRealm(p)) SESSIONS.remove(s.player);
                continue;
            }
            if (!p.isAlive() || !CursedRealms.inRealm(p)) {
                finish(server, s, p, "The training lets you go. The relic will wait.");
                continue;
            }
            step(server, s, p);
        }
    }

    private static void step(MinecraftServer server, Session s, ServerPlayer p) {
        long nanos = System.nanoTime();
        double delta = manualClock ? 0 : Math.min(100.0, (nanos - s.lastNanos) / 1_000_000.0);
        s.lastNanos = nanos;
        s.stateTicks++;
        ClashCommon.hold(p);
        keepPlaced(s, p);
        TrainingScene sc = s.scene;
        switch (s.state) {
            case TrainingNet.INTRO -> {
                s.clock += delta;
                if (sc != null) sc.intro(p, s.stateTicks);
                if (s.clock >= 0) setState(s, p, TrainingNet.ACTIVE);
            }
            case TrainingNet.ACTIVE -> {
                s.clock += delta;
                s.activeTicks++;
                runActive(s, p);
            }
            case TrainingNet.SUCCESS -> {
                if (sc != null) sc.victory(p, s.stateTicks);
                if (s.stateTicks >= s.cfg.victoryTicks) finish(server, s, p, null);
            }
            case TrainingNet.FAILURE, TrainingNet.SYNC_LOST -> {
                if (sc != null && s.state == TrainingNet.FAILURE) sc.failure(p, s.stateTicks);
                if (s.stateTicks >= IDLE_TICKS) finish(server, s, p, "The training lets you go. The relic will wait.");
            }
            default -> {}
        }
        if (s.state != TrainingNet.ENDED && s.stateTicks % 20 == 0) heartbeat(s, p);
    }

    private static void runActive(Session s, ServerPlayer p) {
        TrainingScene sc = s.scene;
        for (int i = 0; i < s.checks.size(); i++) {
            TrainingCheck c = s.checks.get(i);
            if (!s.cued[i] && c.cueMs() <= s.clock) {
                s.cued[i] = true;
                if (sc != null) sc.cue(p, c);
            }
        }
        for (TrainingEvaluator.Outcome o : s.eval.expire(s.clock)) judged(s, p, o);
        if (s.state != TrainingNet.ACTIVE) return;
        if (!s.ready && s.clock > s.cfg.syncTimeoutMs) {
            // The client never acknowledged its schedule: nothing it did could be trusted, and nothing counts.
            setState(s, p, TrainingNet.SYNC_LOST);
            p.sendSystemMessage(Component.literal("The training lost track of you (connection). Retry or leave.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (sc != null) sc.active(p, s.activeTicks, s.clock, s.eval.misses());
        if (s.clock >= s.cfg.durationMs && s.eval.allDecided()) succeed(s, p);
    }

    private static void judged(Session s, ServerPlayer p, TrainingEvaluator.Outcome o) {
        send(p, new TrainingNet.Judge(s.id, s.attempt, o.check(), o.result(), s.eval.misses()));
        if (s.scene != null) s.scene.judged(p, s.checks.get(o.check()), o.result(), s.eval.misses());
        if (s.eval.failed() && s.state == TrainingNet.ACTIVE) setState(s, p, TrainingNet.FAILURE);
    }

    private static void succeed(Session s, ServerPlayer p) {
        setState(s, p, TrainingNet.SUCCESS);
        grant(s, p);
    }

    /** The first success of a real session claims the base kit; practice and test sessions never do. Once only. */
    private static void grant(Session s, ServerPlayer p) {
        if (s.granted) return;
        s.granted = true;
        JJK.LOGGER.info("[training] {} completed {} training{}{}", p.getName().getString(), s.story.kit(), s.practice ? " (practice)" : "", s.test ? " (test)" : "");
        if (s.practice) {
            p.sendSystemMessage(Component.literal("Practice complete. Nothing new to learn, but your control holds.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        if (s.test && !TechniqueProgression.isSandbox(p)) {
            p.sendSystemMessage(Component.literal("It was only an echo of the real thing. Nothing of it stays with you.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        TechniqueProgression.acquire(p, new RelicAcquisition(s.story));
    }

    /** Held where the scene put them (seated or standing); no walking off, no turning away. */
    private static void keepPlaced(Session s, ServerPlayer p) {
        TrainingScene sc = s.scene;
        if (sc == null) return;
        Entity seat = sc.seat();
        if (seat != null) {
            if (p.getVehicle() != seat) place(s, p);
            return;
        }
        if (p.position().distanceToSqr(sc.at) > 0.09) place(s, p);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    private static void setState(Session s, ServerPlayer p, int state) {
        s.state = state;
        s.stateTicks = 0;
        heartbeat(s, p);
    }

    private static void heartbeat(Session s, ServerPlayer p) {
        send(p, new TrainingNet.State(s.id, s.attempt, s.state, s.eval.misses(), (int) Math.round(s.clock), s.stateTicks));
    }

    private static void send(ServerPlayer p, CustomPacketPayload payload) {
        if (p.connection != null) ServerPlayNetworking.send(p, payload);
    }

    // --- Input ---

    /** A key timing or an answer from the participant's client. Stale sessions/attempts are ignored. */
    static void input(ServerPlayer p, TrainingNet.Input in) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || in.session() != s.id) return;
        MinecraftServer server = p.level().getServer();
        if (in.action() == TrainingNet.CANCEL) {
            if (s.state == TrainingNet.INTRO || s.state == TrainingNet.ACTIVE || s.state == TrainingNet.FAILURE || s.state == TrainingNet.SYNC_LOST) {
                finish(server, s, p, "You step out of the training. Nothing gained, nothing lost; the relic will wait.");
            }
            return;
        }
        if (in.attempt() != s.attempt) return;
        switch (in.action()) {
            case TrainingNet.READY -> s.ready = true;
            case TrainingNet.PRESS, TrainingNet.RELEASE -> {
                if (s.state != TrainingNet.ACTIVE) return;
                TrainingEvaluator.Outcome o = s.eval.input(in.seq(), in.action(), in.gauge(), in.check(), in.clientMs(), s.clock);
                if (o != null) judged(s, p, o);
            }
            case TrainingNet.RETRY -> {
                if (s.state == TrainingNet.FAILURE || s.state == TrainingNet.SYNC_LOST) newAttempt(s, p);
            }
            case TrainingNet.LEAVE -> {
                if (s.state == TrainingNet.FAILURE || s.state == TrainingNet.SYNC_LOST || s.state == TrainingNet.SUCCESS) finish(server, s, p, null);
            }
            default -> {}
        }
    }

    // --- Ending ---

    /** Ends a session on any path: the scene undone, the participant told, the room closed (which sends them home). */
    static void finish(MinecraftServer server, Session s, @Nullable ServerPlayer p, @Nullable String message) {
        if (SESSIONS.get(s.player) != s) return;
        SESSIONS.remove(s.player);
        s.state = TrainingNet.ENDED;
        if (s.scene != null) s.scene.cleanup(p);
        if (p != null) {
            ClashCommon.release(p);
            send(p, new TrainingNet.State(s.id, s.attempt, TrainingNet.ENDED, s.eval.misses(), (int) Math.round(s.clock), 0));
            if (message != null) p.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            if (!s.arrived) CursedRealms.cancelPull(p);
        }
        InvestigationState st = InvestigationState.get(server);
        if (s.arena != null && st.arenas().containsValue(s.arena)) CursedRealms.close(server, st, s.arena, s.granted);
    }

    /** The realm ticks the room's arena: running while its session lives. */
    public static int tickArena(String key) {
        for (Session s : SESSIONS.values()) if (s.key.equals(key)) return 0;
        return -1;
    }

    /** The realm closed the room (a restart, abandonment, or the session's own finish). */
    public static void arenaClosed(MinecraftServer server, String key) {
        for (Session s : List.copyOf(SESSIONS.values())) {
            if (s.key.equals(key)) {
                s.arena = null;
                finish(server, s, server.getPlayerList().getPlayer(s.player), "The training lets you go. The relic will wait.");
            }
        }
    }

    public static boolean isTrainingKey(String key) {
        return key.startsWith("training:");
    }

    // --- Admin and tests ---

    /** Completes the running attempt now (as a success). */
    public static boolean completeNow(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !s.arrived || s.state == TrainingNet.SUCCESS || s.state == TrainingNet.ENDED) return false;
        succeed(s, p);
        return true;
    }

    /** Fails the running attempt now. */
    public static boolean failNow(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !s.arrived || s.state != TrainingNet.ACTIVE && s.state != TrainingNet.INTRO) return false;
        setState(s, p, TrainingNet.FAILURE);
        return true;
    }

    public static boolean leaveNow(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return false;
        finish(p.level().getServer(), s, p, "You step out of the training.");
        return true;
    }

    public static void setManualClockForTest(boolean on) {
        manualClock = on;
    }

    /** Moves a session's clock on by {@code ms} and runs one step (manual clock). */
    public static void advanceForTest(ServerPlayer p, double ms) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !s.arrived) return;
        s.clock += ms;
        step(p.level().getServer(), s, p);
    }

    /** Sends an input as if from the client. */
    public static void inputForTest(ServerPlayer p, TrainingNet.Input in) {
        input(p, in);
    }

    public static void answerForTest(ServerPlayer p, String kit, boolean accept) {
        answer(p, kit, accept);
    }

    /** The pending offer's mode (practice?) for tests, or null. */
    @Nullable
    public static Boolean offeredPracticeForTest(ServerPlayer p) {
        Offer o = OFFERS.get(p.getUUID());
        return o == null ? null : o.practice;
    }

    static CharacterStory story(String kit) {
        CharacterStory s = CharacterStories.byKit(kit);
        return s != null ? s : CharacterStories.YUJI;
    }
}
