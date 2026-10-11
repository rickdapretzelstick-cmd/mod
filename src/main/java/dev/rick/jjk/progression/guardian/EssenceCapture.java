package dev.rick.jjk.progression.guardian;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.story.Vessels;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingEvaluator;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSchedule;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capturing a defeated guardian's essence: its challenger uses an Exorcism Seal on its remnant and holds the essence
 * while it fights the seal, a short containment challenge on the shared skill check ({@link TrainingSchedule#containment}):
 * about seven seconds, three overlapping circles, one mistake allowed, the second fails. Failure leaves the remnant
 * where it is for another try at once (no seal is used, nothing is fought again). Success uses exactly one seal, binds
 * the essence into exactly one Sealed Grade 1 Essence (into the inventory, or an owner-bound claim if it's full), and
 * closes that contract's essence for good. The server decides everything.
 */
public final class EssenceCapture {
    /** The skill check's kit name on the client (the HUD and keys are the training's). */
    public static final String KIT = "capture";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextId = 1_000_000;
    private static boolean manualClock;

    private EssenceCapture() {}

    public static final class Session {
        public final int id;
        public final UUID player;
        public final String contract;
        final UUID remnant;
        int attempt = 1;
        int state = TrainingNet.INTRO, stateTicks;
        double clock;
        long lastNanos = System.nanoTime();
        List<TrainingCheck> checks;
        TrainingEvaluator eval;
        boolean ready;

        Session(int id, UUID player, String contract, UUID remnant) {
            this.id = id;
            this.player = player;
            this.contract = contract;
            this.remnant = remnant;
        }

        public int state() {
            return state;
        }

        public double clock() {
            return clock;
        }

        public List<TrainingCheck> checks() {
            return checks;
        }

        public TrainingEvaluator evaluator() {
            return eval;
        }

        public int attempt() {
            return attempt;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EssenceCapture::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> SESSIONS.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            Session s = SESSIONS.get(h.player.getUUID());
            if (s != null) end(server, s, h.player, null);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && SESSIONS.containsKey(p.getUUID())));
    }

    public static boolean active(ServerPlayer p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    @Nullable
    public static Session of(ServerPlayer p) {
        return SESSIONS.get(p.getUUID());
    }

    /** Whether a player carries an Exorcism Seal. */
    public static boolean hasSeal(ServerPlayer p) {
        return p.getInventory().contains(s -> s.is(ProgressionItems.EXORCISM_SEAL));
    }

    /** A seal used on a remnant. True if a containment began. */
    public static boolean begin(ServerPlayer p, GuardianRemnantEntity remnant) {
        if (SESSIONS.containsKey(p.getUUID()) || remnant.containing() || dev.rick.jjk.progression.training.TrainingSessions.inTraining(p)) return false;
        Incident in = Investigations.incident(p.level().getServer(), remnant.contract());
        String why = Investigations.captureRefusal(in, p);
        if (why != null) {
            p.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!hasSeal(p)) {
            p.sendOverlayMessage(Component.literal("You need an Exorcism Seal to contain it. It will wait.").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Session s = new Session(nextId++, p.getUUID(), remnant.contract(), remnant.getUUID());
        SESSIONS.put(p.getUUID(), s);
        remnant.setContaining(true);
        start(s, p);
        return true;
    }

    private static void start(Session s, ServerPlayer p) {
        JJKConfig.Training cfg = JJKConfig.get().training;
        JJKConfig.Training rules = TrainingSchedule.containmentRules(cfg);
        long seed = p.getRandom().nextLong();
        s.checks = TrainingSchedule.containment(seed, cfg);
        // Never a schedule that breaks the fairness rules (a bad seed is simply drawn again).
        for (int tries = 0; tries < 8 && !TrainingSchedule.problems(s.checks, rules).isEmpty(); tries++) {
            seed = p.getRandom().nextLong();
            s.checks = TrainingSchedule.containment(seed, cfg);
        }
        s.eval = new TrainingEvaluator(s.checks, cfg.captureMaxMisses, cfg.maxLatencyMs, cfg.clockSlackMs);
        s.clock = -cfg.captureIntroMs;
        s.state = TrainingNet.INTRO;
        s.stateTicks = 0;
        s.ready = false;
        s.lastNanos = System.nanoTime();
        ServerPlayNetworking.send(p, new TrainingNet.Start(s.id, s.attempt, KIT, "CONTAIN THE ESSENCE", false, seed, cfg.captureIntroMs, cfg.captureDurationMs,
                rules.phase2AtMs, rules.phase3AtMs, cfg.captureMaxMisses, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle()),
                List.of("BIND", "SEAL", "DRAW"), List.of(0xFFB98BFF, 0xFFFFE27A, 0xFF7FD3FF), s.checks));
        state(s, p, TrainingNet.INTRO);
        Fx.sound((ServerLevel) p.level(), p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 0.6f);
    }

    private static void state(Session s, ServerPlayer p, int state) {
        s.state = state;
        s.stateTicks = 0;
        ServerPlayNetworking.send(p, new TrainingNet.State(s.id, s.attempt, state, s.eval.misses(), (int) Math.round(s.clock), 0));
    }

    /** The containment's settings, for tests. */
    public static JJKConfig.Training rules() {
        return TrainingSchedule.containmentRules(JJKConfig.get().training);
    }

    /** A key timing (or READY) from the capturing player's client. False if they aren't containing anything. */
    public static boolean input(ServerPlayer p, TrainingNet.Input in) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return false;
        if (in.session() != s.id || in.attempt() != s.attempt) return true;
        if (in.action() == TrainingNet.READY) s.ready = true;
        else if (in.action() == TrainingNet.CANCEL || in.action() == TrainingNet.LEAVE) end(p.level().getServer(), s, p, "You let the remnant be. It will wait.");
        else if ((in.action() == TrainingNet.PRESS || in.action() == TrainingNet.RELEASE) && s.state == TrainingNet.ACTIVE) {
            TrainingEvaluator.Outcome o = s.eval.input(in.seq(), in.action(), in.gauge(), in.check(), in.clientMs(), s.clock);
            if (o != null) judged(s, p, o);
        }
        return true;
    }

    private static void judged(Session s, ServerPlayer p, TrainingEvaluator.Outcome o) {
        ServerPlayNetworking.send(p, new TrainingNet.Judge(s.id, s.attempt, o.check(), o.result(), s.eval.misses()));
        Entity r = remnant(p, s);
        if (r != null && p.level() instanceof ServerLevel l) {
            if (o.result() == TrainingCheck.MISS) {
                l.sendParticles(p, ParticleTypes.SMOKE, true, false, r.getX(), r.getY() + 1, r.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
                Fx.sound(l, r.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 1.6f);
            } else if (o.result() != TrainingCheck.HOLDING) {
                Vec3 a = r.position().add(0, 1, 0), b = p.position().add(0, 1, 0);
                for (int i = 0; i <= 8; i++) {
                    Vec3 q = a.lerp(b, i / 8.0);
                    l.sendParticles(p, new DustParticleOptions(0xB98BFF, 0.8f), true, false, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
                Fx.sound(l, r.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, o.result() == TrainingCheck.PERFECT ? 1.6f : 1.1f);
            }
        }
        if (s.eval.failed() && s.state == TrainingNet.ACTIVE) fail(s, p);
    }

    @Nullable
    private static Entity remnant(ServerPlayer p, Session s) {
        return p.level() instanceof ServerLevel l ? l.getEntity(s.remnant) : null;
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (Session s : List.copyOf(SESSIONS.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.player);
            if (p == null || !p.isAlive()) {
                end(server, s, p, null);
                continue;
            }
            Entity r = remnant(p, s);
            if (r == null || p.position().distanceToSqr(r.position()) > 12 * 12) {
                end(server, s, p, "The remnant slips out of reach.");
                continue;
            }
            step(server, s, p);
        }
    }

    private static void step(MinecraftServer server, Session s, ServerPlayer p) {
        long n = System.nanoTime();
        double d = manualClock ? 0 : Math.min(100.0, (n - s.lastNanos) / 1_000_000.0);
        s.lastNanos = n;
        s.stateTicks++;
        ClashCommon.hold(p);
        // A heartbeat, so the client knows the containment is still going.
        if (s.stateTicks % 20 == 0) ServerPlayNetworking.send(p, new TrainingNet.State(s.id, s.attempt, s.state, s.eval.misses(), (int) Math.round(s.clock), s.stateTicks));
        JJKConfig.Training cfg = JJKConfig.get().training;
        switch (s.state) {
            case TrainingNet.INTRO -> {
                s.clock += d;
                if (s.clock >= 0) state(s, p, TrainingNet.ACTIVE);
            }
            case TrainingNet.ACTIVE -> {
                s.clock += d;
                for (TrainingEvaluator.Outcome o : s.eval.expire(s.clock)) judged(s, p, o);
                if (s.state != TrainingNet.ACTIVE) return;
                if (!s.ready && s.clock > cfg.syncTimeoutMs) {
                    end(server, s, p, "The containment lost track of you (connection). Try again.");
                    return;
                }
                if (s.clock >= cfg.captureDurationMs && s.eval.allDecided()) succeed(s, p);
            }
            case TrainingNet.SUCCESS -> {
                if (s.stateTicks >= 30) end(server, s, p, null);
            }
            case TrainingNet.FAILURE -> {
                if (s.stateTicks >= 40) end(server, s, p, "It slips loose. The remnant is still there: use a seal on it again.");
            }
            default -> {}
        }
    }

    private static void fail(Session s, ServerPlayer p) {
        state(s, p, TrainingNet.FAILURE);
        Entity r = remnant(p, s);
        if (r != null && p.level() instanceof ServerLevel l) {
            l.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, false, r.getX(), r.getY() + 1, r.getZ(), 20, 0.4, 0.6, 0.4, 0.04);
            Fx.sound(l, r.position(), SoundEvents.GLASS_BREAK, 0.8f, 0.6f);
        }
    }

    /** The essence is contained: one seal spent, one sealed essence, the contract's essence closed for good. */
    private static void succeed(Session s, ServerPlayer p) {
        Incident in = Investigations.incident(p.level().getServer(), s.contract);
        if (Investigations.captureRefusal(in, p) != null || !consumeSeal(p)) {
            state(s, p, TrainingNet.FAILURE);
            return;
        }
        String guardian = Investigations.guardianName(in);
        Investigations.markCaptured(p.level().getServer(), in, Vessels.sealed(s.contract, guardian), p);
        state(s, p, TrainingNet.SUCCESS);
        Entity r = remnant(p, s);
        if (r != null && p.level() instanceof ServerLevel l) {
            // The energy pulls into the seal in the challenger's hands.
            Vec3 a = r.position().add(0, 1, 0), b = p.position().add(0, 1.1, 0);
            for (int i = 0; i <= 14; i++) {
                Vec3 q = a.lerp(b, i / 14.0);
                l.sendParticles(p, ParticleTypes.REVERSE_PORTAL, true, false, q.x, q.y, q.z, 3, 0.05, 0.05, 0.05, 0.01);
            }
            Fx.sound(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 0.8f);
            Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.5f);
            r.discard();
        }
        JJK.LOGGER.info("[guardians] {} captured the essence of contract {}", p.getName().getString(), s.contract);
    }

    private static boolean consumeSeal(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(ProgressionItems.EXORCISM_SEAL)) {
                st.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void end(MinecraftServer server, Session s, @Nullable ServerPlayer p, @Nullable String message) {
        if (SESSIONS.get(s.player) != s) return;
        SESSIONS.remove(s.player);
        if (p != null) {
            Entity r = remnant(p, s);
            if (r instanceof GuardianRemnantEntity g) g.setContaining(false);
            ClashCommon.release(p);
            ServerPlayNetworking.send(p, new TrainingNet.State(s.id, s.attempt, TrainingNet.ENDED, s.eval == null ? 0 : s.eval.misses(), 0, 0));
            if (message != null) p.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    // --- Tests ---

    public static void setManualClockForTest(boolean on) {
        manualClock = on;
    }

    public static void advanceForTest(ServerPlayer p, double ms) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        s.clock += ms;
        step(p.level().getServer(), s, p);
    }

    public static void inputForTest(ServerPlayer p, TrainingNet.Input in) {
        input(p, in);
    }
}
