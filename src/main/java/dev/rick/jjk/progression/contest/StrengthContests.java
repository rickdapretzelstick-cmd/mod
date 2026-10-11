package dev.rick.jjk.progression.contest;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Strength contests: some curses can grab. A grab is always telegraphed (the curse stops, a warning ring closes on you
 * for {@link JJKConfig.Training#contestTelegraphTicks}) and checked again by the server when it closes: still in reach,
 * in its line of sight, free to be grabbed. Then both are locked for a few seconds of tap checks on the shared skill
 * check, pushing a 0-100 tug-of-war meter against the curse's pressure ({@link ContestPreset}). 100 breaks free (the
 * curse is staggered and wide open); 0, or ending under the preset's line, is overpowered (you take the hit and are
 * thrown).
 *
 * <p>Who can grab is explicit ({@code contestKinds}; a fly head never). A player can be in one contest at a time,
 * reserved atomically with its curse, and after any contest is protected from every grab for a while (one timer per
 * player, shared by all curses). A Grade 1 guardian grabs at most {@code contestsPerGuardianAttempt} times per attempt.
 * While two are locked nobody else can hurt either of them (no cheap shots, no rescues that skip the contest). Leaving,
 * dying, the curse dying or vanishing ends it with no result. The server decides everything.
 */
public final class StrengthContests {
    public static final String KIT = "contest";
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Session> BY_CURSE = new HashMap<>();
    private static final Map<UUID, Telegraph> TELEGRAPHS = new HashMap<>();
    /** Per player: the game time until which no curse may grab them. */
    private static final Map<UUID, Long> PROTECTED = new HashMap<>();
    /** Per curse: the game time until which it won't try to grab again. */
    private static final Map<UUID, Long> CURSE_READY = new HashMap<>();
    /** Per guardian attempt (its arena's encounter id): contests so far. */
    private static final Map<UUID, Integer> PER_ATTEMPT = new HashMap<>();
    private static int nextId = 2_000_000;
    private static boolean manualClock, noRandomGrabs;

    private StrengthContests() {}

    /** A grab being telegraphed: who, at whom, and when it closes. */
    record Telegraph(UUID curse, UUID player, long closesAt) {}

    public static final class Session {
        public final int id;
        public final UUID player, curse;
        public final ContestPreset preset;
        public final String foe;
        int state = TrainingNet.INTRO, stateTicks;
        double clock, meter = ContestPreset.START;
        long lastNanos = System.nanoTime();
        List<TrainingCheck> checks;
        TrainingEvaluator eval;
        boolean ready;
        @Nullable Boolean won;

        Session(int id, UUID player, UUID curse, ContestPreset preset, String foe) {
            this.id = id;
            this.player = player;
            this.curse = curse;
            this.preset = preset;
            this.foe = foe;
        }

        public int state() {
            return state;
        }

        public double clock() {
            return clock;
        }

        public double meter() {
            return meter;
        }

        public List<TrainingCheck> checks() {
            return checks;
        }

        @Nullable
        public Boolean won() {
            return won;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StrengthContests::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> clear());
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            Session s = BY_PLAYER.get(h.player.getUUID());
            if (s != null) end(server, s, null, null);
            TELEGRAPHS.values().removeIf(t -> t.player.equals(h.player.getUUID()));
        });
        // Locked together: nobody else touches either of them (and they don't hit each other outside the contest).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Session s = entity instanceof ServerPlayer p ? BY_PLAYER.get(p.getUUID()) : BY_CURSE.get(entity.getUUID());
            return s == null || s.state == TrainingNet.SUCCESS || s.state == TrainingNet.FAILURE && source.getEntity() != null && source.getEntity().getUUID().equals(s.curse) && entity.getUUID().equals(s.player);
        });
    }

    private static void clear() {
        BY_PLAYER.clear();
        BY_CURSE.clear();
        TELEGRAPHS.clear();
        PROTECTED.clear();
        CURSE_READY.clear();
        PER_ATTEMPT.clear();
    }

    // --- Queries ---

    public static boolean active(ServerPlayer p) {
        return BY_PLAYER.containsKey(p.getUUID());
    }

    public static boolean locked(Entity e) {
        return BY_PLAYER.containsKey(e.getUUID()) || BY_CURSE.containsKey(e.getUUID());
    }

    @Nullable
    public static Session of(ServerPlayer p) {
        return BY_PLAYER.get(p.getUUID());
    }

    public static boolean telegraphing(Entity curse) {
        return TELEGRAPHS.containsKey(curse.getUUID());
    }

    /** Whether this curse's kind may grab at all (explicit list; a fly head never). */
    public static boolean eligible(CommonCurseEntity c) {
        String kind = c.curseKind();
        if ("fly_head".equals(kind)) return false;
        return JJKConfig.get().training.contestKinds.contains(kind) || isGuardian(c);
    }

    /** Whether this curse is a Grade 1 contract's guardian. */
    public static boolean isGuardian(CommonCurseEntity c) {
        Incident in = incidentOf(c);
        if (in == null || in.def() == null || in.def().objective() != IncidentTemplate.Objective.GUARDIAN) return false;
        return c.getUUID().equals(in.objectiveTarget());
    }

    @Nullable
    private static Incident incidentOf(CommonCurseEntity c) {
        String id = c.incidentId();
        if (id == null || id.isEmpty() || c.level().getServer() == null) return null;
        return InvestigationState.get(c.level().getServer()).incident(id);
    }

    public static ContestPreset presetFor(CommonCurseEntity c) {
        return ContestPreset.of(c.curseGrade(), isGuardian(c));
    }

    /** Why this player can't be grabbed right now (null if they can). */
    @Nullable
    public static String unavailable(ServerPlayer p) {
        if (!p.isAlive()) return "not alive";
        // (the game mode itself: a test player reports isCreative())
        if (p.isSpectator() || p.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE) return "not in survival";
        if (BY_PLAYER.containsKey(p.getUUID())) return "in a contest";
        Long until = PROTECTED.get(p.getUUID());
        if (until != null && p.level().getGameTime() < until) return "protected";
        if (dev.rick.jjk.progression.training.TrainingSessions.inTraining(p) || dev.rick.jjk.progression.guardian.EssenceCapture.active(p)) return "busy";
        if (ClashCommon.clashing(p) || CursedRealms.pulling(p)) return "busy";
        return null;
    }

    /** Why this curse can't grab right now (null if it can). */
    @Nullable
    public static String cannotGrab(CommonCurseEntity c) {
        if (!JJKConfig.get().training.contests) return "contests off";
        if (!c.isAlive() || !eligible(c)) return "not a grabber";
        if (BY_CURSE.containsKey(c.getUUID()) || TELEGRAPHS.containsKey(c.getUUID())) return "already grabbing";
        Long ready = CURSE_READY.get(c.getUUID());
        if (ready != null && c.level().getGameTime() < ready) return "cooling down";
        if (isGuardian(c)) {
            UUID attempt = attemptOf(c);
            if (attempt != null && PER_ATTEMPT.getOrDefault(attempt, 0) >= JJKConfig.get().training.contestsPerGuardianAttempt) return "no more this attempt";
        }
        return null;
    }

    @Nullable
    private static UUID attemptOf(CommonCurseEntity c) {
        InvestigationState.Arena a = CursedRealms.arenaHere(c);
        return a == null ? null : a.encounter;
    }

    // --- The grab ---

    /** A curse starts a grab at a player: telegraphed, decided when it closes. True if it began. */
    public static boolean telegraph(CommonCurseEntity c, ServerPlayer p) {
        if (cannotGrab(c) != null || unavailable(p) != null || !(c.level() instanceof ServerLevel level)) return false;
        JJKConfig.Training cfg = JJKConfig.get().training;
        if (c.distanceTo(p) > cfg.contestGrabRange) return false;
        TELEGRAPHS.put(c.getUUID(), new Telegraph(c.getUUID(), p.getUUID(), level.getGameTime() + cfg.contestTelegraphTicks));
        c.holdForContest(cfg.contestTelegraphTicks + 2);
        c.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        // The warning: a ring closing on you, a growl, the curse rearing up.
        Fx.play(level, "fb_smash_warn", p.position().add(0, 0.05, 0), new Vec3(cfg.contestTelegraphTicks, 0, 0), 1.4f);
        Fx.play(level, "curse_exposed", c.position().add(0, c.getBbHeight() * 0.9, 0), Vec3.ZERO, 0.8f);
        Fx.sound(level, c.position(), SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
        p.sendOverlayMessage(Component.literal("It's reaching for you. Get out of reach!").withStyle(ChatFormatting.RED));
        return true;
    }

    /** The telegraph closes: checked again, then reserved (both at once) and begun, or it grabs at nothing. */
    private static void close(MinecraftServer server, Telegraph t) {
        TELEGRAPHS.remove(t.curse);
        ServerPlayer p = server.getPlayerList().getPlayer(t.player);
        Entity e = p == null ? null : ((ServerLevel) p.level()).getEntity(t.curse);
        if (!(e instanceof CommonCurseEntity c) || p == null) return;
        JJKConfig.Training cfg = JJKConfig.get().training;
        boolean inReach = c.level() == p.level() && c.distanceTo(p) <= cfg.contestEscapeRange && c.hasLineOfSight(p);
        if (!inReach || unavailable(p) != null || !c.isAlive()) {
            // Dodged (or it could no longer reach): it grabs at the air and has to try again later.
            CURSE_READY.put(c.getUUID(), server.overworld().getGameTime() + cfg.contestCurseCooldownSeconds * 10L);
            c.wonContest(10);
            if (c.level() instanceof ServerLevel l) Fx.sound(l, c.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.6f);
            return;
        }
        begin(server, c, p);
    }

    /** Reserves both and starts the contest now (also the test hook, past the telegraph). False if either is taken. */
    public static boolean begin(MinecraftServer server, CommonCurseEntity c, ServerPlayer p) {
        // Atomic: both free, or neither is taken.
        if (BY_PLAYER.containsKey(p.getUUID()) || BY_CURSE.containsKey(c.getUUID())) return false;
        if (unavailable(p) != null) return false;
        JJKConfig.Training cfg = JJKConfig.get().training;
        ContestPreset preset = presetFor(c);
        String foe = c.getName().getString();
        Session s = new Session(nextId++, p.getUUID(), c.getUUID(), preset, foe);
        BY_PLAYER.put(p.getUUID(), s);
        BY_CURSE.put(c.getUUID(), s);
        if (isGuardian(c)) {
            UUID attempt = attemptOf(c);
            if (attempt != null) PER_ATTEMPT.merge(attempt, 1, Integer::sum);
        }
        JJKConfig.Training rules = TrainingSchedule.contestRules(cfg);
        long seed = p.getRandom().nextLong();
        s.checks = TrainingSchedule.contest(seed, cfg);
        for (int tries = 0; tries < 8 && !TrainingSchedule.problems(s.checks, rules).isEmpty(); tries++) {
            seed = p.getRandom().nextLong();
            s.checks = TrainingSchedule.contest(seed, cfg);
        }
        s.eval = new TrainingEvaluator(s.checks, rules.maxMisses, cfg.maxLatencyMs, cfg.clockSlackMs);
        s.clock = -cfg.contestIntroMs;
        c.holdForContest(10);
        ClashCommon.hold(p);
        // Face to face.
        c.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        Vec3 mid = c.position().add(0, c.getBbHeight() * 0.6, 0);
        ServerPlayNetworking.send(p, new TrainingNet.Start(s.id, 1, KIT, "BREAK ITS HOLD", false, seed, cfg.contestIntroMs, cfg.contestDurationMs,
                rules.phase2AtMs, rules.phase3AtMs, rules.maxMisses, p.getEyePosition(), mid, List.of("PUSH", "BRACE", "HEAVE"),
                List.of(0xFFFF9A6A, 0xFFFFE27A, 0xFFFF6A8A), s.checks));
        state(s, p, TrainingNet.INTRO);
        meter(s, p);
        if (p.level() instanceof ServerLevel l) {
            Fx.sound(l, p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.6f);
            Fx.shake(l, p.position(), 8, 0.4f, 8);
        }
        JJK.LOGGER.info("[contests] {} grabbed by {} ({})", p.getName().getString(), foe, preset.name());
        return true;
    }

    private static void state(Session s, ServerPlayer p, int state) {
        s.state = state;
        s.stateTicks = 0;
        ServerPlayNetworking.send(p, new TrainingNet.State(s.id, 1, state, s.eval.misses(), (int) Math.round(s.clock), 0));
    }

    private static void meter(Session s, ServerPlayer p) {
        ServerPlayNetworking.send(p, new TrainingNet.Meter(s.id, (float) s.meter, (float) s.preset.threshold(), s.foe));
    }

    /** A key timing (or READY / LEAVE) from the contestant's client. False if they aren't in a contest. */
    public static boolean input(ServerPlayer p, TrainingNet.Input in) {
        Session s = BY_PLAYER.get(p.getUUID());
        if (s == null) return false;
        if (in.session() != s.id) return true;
        if (in.action() == TrainingNet.READY) s.ready = true;
        // Taps only: a release means nothing.
        else if (in.action() == TrainingNet.PRESS && s.state == TrainingNet.ACTIVE) {
            TrainingEvaluator.Outcome o = s.eval.input(in.seq(), in.action(), in.gauge(), in.check(), in.clientMs(), s.clock);
            if (o != null) judged(s, p, o);
        }
        return true;
    }

    private static void judged(Session s, ServerPlayer p, TrainingEvaluator.Outcome o) {
        if (o.result() == TrainingCheck.HOLDING) return;
        s.meter = s.preset.judged(s.meter, o.result());
        ServerPlayNetworking.send(p, new TrainingNet.Judge(s.id, 1, o.check(), o.result(), s.eval.misses()));
        meter(s, p);
        if (p.level() instanceof ServerLevel l) {
            if (o.result() == TrainingCheck.MISS) Fx.sound(l, p.position(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.6f, 0.7f);
            else Fx.sound(l, p.position(), SoundEvents.ANVIL_LAND, 0.25f, o.result() == TrainingCheck.PERFECT ? 1.9f : 1.5f);
        }
        decide(s, p, false);
    }

    /** Over at either end of the meter, or (at {@code timeout}) by which side of the line it ends on. */
    private static void decide(Session s, ServerPlayer p, boolean timeout) {
        if (s.state != TrainingNet.ACTIVE) return;
        if (s.meter >= ContestPreset.MAX) finish(s, p, true);
        else if (s.meter <= 0) finish(s, p, false);
        else if (timeout) finish(s, p, s.meter >= s.preset.threshold());
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (!TELEGRAPHS.isEmpty()) {
            for (Telegraph t : List.copyOf(TELEGRAPHS.values())) if (now >= t.closesAt) close(server, t);
        }
        for (Session s : List.copyOf(BY_PLAYER.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.player);
            Entity c = p == null ? null : ((ServerLevel) p.level()).getEntity(s.curse);
            if (p == null || !p.isAlive() || !(c instanceof CommonCurseEntity curse) || !curse.isAlive() || c.level() != p.level()) {
                end(server, s, p, p == null ? null : "The hold breaks.");
                continue;
            }
            step(server, s, p, curse);
        }
        if (!noRandomGrabs && now % 10 == 0) tryGrabs(server);
        if (now % 200 == 0) {
            PROTECTED.values().removeIf(t -> t < now);
            CURSE_READY.values().removeIf(t -> t < now);
        }
    }

    /** Twice a second, every ready grabber next to its target may start a grab (by chance, so it isn't clockwork). */
    private static void tryGrabs(MinecraftServer server) {
        JJKConfig.Training cfg = JJKConfig.get().training;
        if (!cfg.contests) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (unavailable(p) != null || !(p.level() instanceof ServerLevel level)) continue;
            for (CommonCurseEntity c : level.getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(cfg.contestGrabRange),
                    e -> e.isAlive() && e.getTarget() == p && e.readyToGrab())) {
                if (cannotGrab(c) != null || c.distanceTo(p) > cfg.contestGrabRange) continue;
                if (p.getRandom().nextFloat() < cfg.contestChancePerSecond * 0.5f && telegraph(c, p)) break;
            }
        }
    }

    private static void step(MinecraftServer server, Session s, ServerPlayer p, CommonCurseEntity c) {
        long n = System.nanoTime();
        double d = manualClock ? 0 : Math.min(100.0, (n - s.lastNanos) / 1_000_000.0);
        s.lastNanos = n;
        s.stateTicks++;
        if (s.state == TrainingNet.INTRO || s.state == TrainingNet.ACTIVE) {
            ClashCommon.hold(p);
            c.holdForContest(4);
            // Locked face to face: neither drifts.
            p.setDeltaMovement(Vec3.ZERO);
        }
        if (s.stateTicks % 20 == 0) ServerPlayNetworking.send(p, new TrainingNet.State(s.id, 1, s.state, s.eval.misses(), (int) Math.round(s.clock), s.stateTicks));
        JJKConfig.Training cfg = JJKConfig.get().training;
        switch (s.state) {
            case TrainingNet.INTRO -> {
                s.clock += d;
                if (s.clock >= 0) state(s, p, TrainingNet.ACTIVE);
            }
            case TrainingNet.ACTIVE -> advance(s, p, d, cfg);
            case TrainingNet.SUCCESS, TrainingNet.FAILURE -> {
                if (s.stateTicks >= 24) end(server, s, p, null);
            }
            default -> {}
        }
    }

    private static void advance(Session s, ServerPlayer p, double d, JJKConfig.Training cfg) {
        double before = s.meter;
        s.clock += d;
        s.meter = s.preset.pressed(s.meter, d);
        for (TrainingEvaluator.Outcome o : s.eval.expire(s.clock)) {
            judged(s, p, o);
            if (s.state != TrainingNet.ACTIVE) return;
        }
        if ((int) before / 5 != (int) s.meter / 5 || s.stateTicks % 5 == 0) meter(s, p);
        if (!s.ready && s.clock > cfg.syncTimeoutMs) {
            // The client never answered: no contest (and no punishment for a dropped connection).
            end(p.level().getServer(), s, p, "The hold breaks.");
            return;
        }
        decide(s, p, false);
        if (s.state == TrainingNet.ACTIVE && s.clock >= cfg.contestDurationMs && s.eval.allDecided()) decide(s, p, true);
    }

    /** The result: broken free (it reels, wide open) or overpowered (the hit, and thrown). */
    private static void finish(Session s, ServerPlayer p, boolean won) {
        s.won = won;
        meter(s, p);
        state(s, p, won ? TrainingNet.SUCCESS : TrainingNet.FAILURE);
        ServerLevel level = (ServerLevel) p.level();
        Entity e = level.getEntity(s.curse);
        if (!(e instanceof CommonCurseEntity c)) return;
        Vec3 away = p.position().subtract(c.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
        if (won) {
            c.lostContest(level, p);
            c.setDeltaMovement(c.getDeltaMovement().add(-away.x * 0.6, 0.2, -away.z * 0.6));
            Fx.sound(level, c.position(), SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
            p.sendOverlayMessage(Component.literal("You break its hold. It's wide open!").withStyle(ChatFormatting.GOLD));
        } else {
            c.wonContest(30);
            ClashCommon.release(p);
            p.hurtServer(level, level.damageSources().mobAttack(c), s.preset.loseDamage());
            p.setDeltaMovement(away.x * 0.9, 0.35, away.z * 0.9);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
            Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
            p.sendOverlayMessage(Component.literal("It overpowers you.").withStyle(ChatFormatting.RED));
        }
        JJK.LOGGER.info("[contests] {} {} against {} (meter {})", p.getName().getString(), won ? "broke free" : "was overpowered", s.foe, Math.round(s.meter));
    }

    private static void end(MinecraftServer server, Session s, @Nullable ServerPlayer p, @Nullable String message) {
        if (BY_PLAYER.get(s.player) != s) return;
        BY_PLAYER.remove(s.player);
        BY_CURSE.remove(s.curse);
        JJKConfig.Training cfg = JJKConfig.get().training;
        long now = server.overworld().getGameTime();
        // One timer per player, shared by every curse; the curse waits its own.
        PROTECTED.put(s.player, now + cfg.contestProtectSeconds * 20L);
        CURSE_READY.put(s.curse, now + cfg.contestCurseCooldownSeconds * 20L);
        if (p != null) {
            Entity e = ((ServerLevel) p.level()).getEntity(s.curse);
            if (e instanceof CommonCurseEntity c && s.won == null) c.releaseFromContest();
            ClashCommon.release(p);
            ServerPlayNetworking.send(p, new TrainingNet.State(s.id, 1, TrainingNet.ENDED, s.eval == null ? 0 : s.eval.misses(), 0, 0));
            if (message != null) p.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.GRAY));
        }
    }

    // --- Simulation (tuning and tests) ---

    /** How a simulated contest ended: who won, where the meter was, and when (ms of the contest). */
    public record Simulated(boolean won, double meter, double atMs) {}

    /**
     * Plays a contest's schedule against a preset with every verdict given by {@code verdict} (check index ->
     * PERFECT / GOOD / MISS), exactly as the server scores it: pressure between checks, each verdict at its check's
     * centre, over at 0 or 100, else decided at the end by the preset's line.
     */
    public static Simulated simulate(List<TrainingCheck> checks, ContestPreset preset, int durationMs, java.util.function.IntUnaryOperator verdict) {
        double meter = ContestPreset.START, t = 0;
        for (TrainingCheck c : checks) {
            meter = preset.pressed(meter, c.atMs() - t);
            t = c.atMs();
            if (meter <= 0) return new Simulated(false, 0, t);
            meter = preset.judged(meter, verdict.applyAsInt(c.id()));
            if (meter >= ContestPreset.MAX) return new Simulated(true, meter, t);
            if (meter <= 0) return new Simulated(false, 0, t);
        }
        meter = preset.pressed(meter, Math.max(0, durationMs - t));
        if (meter <= 0) return new Simulated(false, 0, durationMs);
        return new Simulated(meter >= preset.threshold(), meter, durationMs);
    }

    // --- Tests ---

    public static void setManualClockForTest(boolean on) {
        manualClock = on;
    }

    /** Tests: no grabs happen by chance (only the ones a test starts). */
    public static void setRandomGrabsForTest(boolean on) {
        noRandomGrabs = !on;
    }

    public static void advanceForTest(ServerPlayer p, double ms) {
        Session s = BY_PLAYER.get(p.getUUID());
        if (s == null) return;
        Entity c = ((ServerLevel) p.level()).getEntity(s.curse);
        if (!(c instanceof CommonCurseEntity curse)) return;
        if (s.state == TrainingNet.ACTIVE) advance(s, p, ms, JJKConfig.get().training);
        else {
            s.clock += ms;
            step(p.level().getServer(), s, p, curse);
        }
    }

    public static void stepForTest(MinecraftServer server) {
        tick(server);
    }

    public static boolean protectedForTest(ServerPlayer p) {
        Long t = PROTECTED.get(p.getUUID());
        return t != null && p.level().getGameTime() < t;
    }

    public static void forgetForTest(ServerPlayer p, @Nullable Entity c) {
        PROTECTED.remove(p.getUUID());
        if (c != null) CURSE_READY.remove(c.getUUID());
    }

    public static Set<UUID> lockedForTest() {
        return Set.copyOf(BY_CURSE.keySet());
    }
}
