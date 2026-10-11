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
import dev.rick.jjk.progression.moment.Moment;
import dev.rick.jjk.progression.moment.Moments;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingEvaluator;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSchedule;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Map<UUID, Telegraph> TELEGRAPHS = new HashMap<>();
    /** Per player: the game time until which no curse may grab them. */
    private static final Map<UUID, Long> PROTECTED = new HashMap<>();
    /** Per curse: the game time until which it won't try to grab again. */
    private static final Map<UUID, Long> CURSE_READY = new HashMap<>();
    /** Per guardian attempt (its arena's encounter id): contests so far. */
    private static final Map<UUID, Integer> PER_ATTEMPT = new HashMap<>();
    /**
     * Grabs by chance never happen inside gametests (they would lock other tests' players and hold their curses): the
     * contest tests start theirs on purpose.
     */
    private static boolean noRandomGrabs = System.getProperty("fabric-api.gametest") != null || System.getProperty("fabric.client.gametest") != null;

    private StrengthContests() {}

    /** A grab being telegraphed: who, at whom, and when it closes. */
    record Telegraph(UUID curse, UUID player, long closesAt) {}

    /** One contest: the player, the curse that grabbed them, its preset, and the meter between them. */
    public static final class Session extends Moment {
        public final UUID curse;
        public final ContestPreset preset;
        public final String foe;
        double meter = ContestPreset.START;
        @Nullable Boolean won;

        Session(UUID player, UUID curse, ContestPreset preset, String foe) {
            super(player, curse);
            this.curse = curse;
            this.preset = preset;
            this.foe = foe;
        }

        public double meter() {
            return meter;
        }

        @Nullable
        public Boolean won() {
            return won;
        }

        @Override
        public String kit() {
            return KIT;
        }

        @Override
        public JJKConfig.Training rules() {
            return TrainingSchedule.contestRules(JJKConfig.get().training);
        }

        @Nullable
        private CommonCurseEntity curse(ServerPlayer p) {
            return ((ServerLevel) p.level()).getEntity(curse) instanceof CommonCurseEntity c ? c : null;
        }

        @Nullable
        @Override
        protected String broken(MinecraftServer server, @Nullable ServerPlayer p) {
            if (p == null || !p.isAlive()) return "";
            CommonCurseEntity c = curse(p);
            return c == null || !c.isAlive() || c.level() != p.level() ? "The hold breaks." : null;
        }

        @Override
        protected void hold(ServerPlayer p) {
            ClashCommon.hold(p);
            CommonCurseEntity c = curse(p);
            if (c != null) c.holdForContest(4);
            // Locked face to face: neither drifts.
            p.setDeltaMovement(Vec3.ZERO);
        }

        @Override
        protected boolean allowDamage(LivingEntity entity, DamageSource source) {
            // Locked together, nobody else touches either of them; once it's decided, the result's own hit lands
            // (and a curse that lost is fair game).
            if (state == TrainingNet.SUCCESS) return true;
            return state == TrainingNet.FAILURE && source.getEntity() != null && source.getEntity().getUUID().equals(curse) && entity.getUUID().equals(player);
        }

        @Override
        protected void onJudged(ServerPlayer p, TrainingEvaluator.Outcome o) {
            if (o.result() == TrainingCheck.HOLDING) return;
            meter = preset.judged(meter, o.result());
            sendMeter(p);
            if (p.level() instanceof ServerLevel l) {
                if (o.result() == TrainingCheck.MISS) Fx.sound(l, p.position(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.6f, 0.7f);
                else Fx.sound(l, p.position(), SoundEvents.ANVIL_LAND, 0.25f, o.result() == TrainingCheck.PERFECT ? 1.9f : 1.5f);
            }
            decide(p, false);
        }

        @Override
        protected void beforeChecks(ServerPlayer p, double dtMs) {
            double before = meter;
            meter = preset.pressed(meter, dtMs);
            if ((int) before / 5 != (int) meter / 5 || stateTicks % 5 == 0) sendMeter(p);
        }

        @Override
        protected void afterChecks(ServerPlayer p) {
            decide(p, false);
            if (state == TrainingNet.ACTIVE && clock >= JJKConfig.get().training.contestDurationMs && eval.allDecided()) decide(p, true);
        }

        @Override
        protected String syncLost() {
            return "The hold breaks.";
        }

        @Override
        protected void say(ServerPlayer p, String message) {
            p.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.GRAY));
        }

        void sendMeter(ServerPlayer p) {
            ServerPlayNetworking.send(p, new TrainingNet.Meter(id, (float) meter, (float) preset.threshold(), foe));
        }

        /** Over at either end of the meter, or (at {@code timeout}) by which side of the line it ends on. */
        private void decide(ServerPlayer p, boolean timeout) {
            if (state != TrainingNet.ACTIVE) return;
            if (meter >= ContestPreset.MAX) finish(p, true);
            else if (meter <= 0) finish(p, false);
            else if (timeout) finish(p, meter >= preset.threshold());
        }

        /** The result: broken free (it reels, wide open) or overpowered (the hit, and thrown). */
        private void finish(ServerPlayer p, boolean win) {
            won = win;
            sendMeter(p);
            setState(p, win ? TrainingNet.SUCCESS : TrainingNet.FAILURE);
            ServerLevel level = (ServerLevel) p.level();
            CommonCurseEntity c = curse(p);
            if (c == null) return;
            Vec3 away = p.position().subtract(c.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
            if (win) {
                c.lostContest(level, p);
                c.setDeltaMovement(c.getDeltaMovement().add(-away.x * 0.6, 0.2, -away.z * 0.6));
                Fx.sound(level, c.position(), SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
                p.sendOverlayMessage(Component.literal("You break its hold. It's wide open!").withStyle(ChatFormatting.GOLD));
            } else {
                c.wonContest(30);
                ClashCommon.release(p);
                p.hurtServer(level, level.damageSources().mobAttack(c), preset.loseDamage());
                p.setDeltaMovement(away.x * 0.9, 0.35, away.z * 0.9);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
                Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
                p.sendOverlayMessage(Component.literal("It overpowers you.").withStyle(ChatFormatting.RED));
            }
            JJK.LOGGER.info("[contests] {} {} against {} (meter {})", p.getName().getString(), win ? "broke free" : "was overpowered", foe, Math.round(meter));
        }

        @Override
        protected void onEnd(MinecraftServer server, @Nullable ServerPlayer p) {
            JJKConfig.Training cfg = JJKConfig.get().training;
            long now = server.overworld().getGameTime();
            // One timer per player, shared by every curse; the curse waits its own.
            PROTECTED.put(player, now + cfg.contestProtectSeconds * 20L);
            CURSE_READY.put(curse, now + cfg.contestCurseCooldownSeconds * 20L);
            if (p != null && won == null) {
                CommonCurseEntity c = curse(p);
                if (c != null) c.releaseFromContest();
            }
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StrengthContests::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> clear());
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> TELEGRAPHS.values().removeIf(t -> t.player.equals(h.player.getUUID())));
    }

    private static void clear() {
        TELEGRAPHS.clear();
        PROTECTED.clear();
        CURSE_READY.clear();
        PER_ATTEMPT.clear();
    }

    // --- Queries ---

    public static boolean active(ServerPlayer p) {
        return Moments.of(p) instanceof Session;
    }

    public static boolean locked(Entity e) {
        return e instanceof ServerPlayer p ? active(p) : Moments.ofFoe(e) instanceof Session;
    }

    @Nullable
    public static Session of(ServerPlayer p) {
        return Moments.of(p) instanceof Session s ? s : null;
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
        if (active(p)) return "in a contest";
        Long until = PROTECTED.get(p.getUUID());
        if (until != null && p.level().getGameTime() < until) return "protected";
        if (Moments.busy(p)) return "busy";
        return null;
    }

    /** Why this curse can't grab right now (null if it can). */
    @Nullable
    public static String cannotGrab(CommonCurseEntity c) {
        if (!JJKConfig.get().training.contests) return "contests off";
        if (!c.isAlive() || !eligible(c)) return "not a grabber";
        if (Moments.ofFoe(c) != null || TELEGRAPHS.containsKey(c.getUUID())) return "already grabbing";
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
        if (unavailable(p) != null) return false;
        JJKConfig.Training cfg = JJKConfig.get().training;
        Session s = new Session(p.getUUID(), c.getUUID(), presetFor(c), c.getName().getString());
        JJKConfig.Training rules = s.rules();
        long seed = p.getRandom().nextLong();
        List<TrainingCheck> checks = TrainingSchedule.contest(seed, cfg);
        for (int tries = 0; tries < 8 && !TrainingSchedule.problems(checks, rules).isEmpty(); tries++) {
            seed = p.getRandom().nextLong();
            checks = TrainingSchedule.contest(seed, cfg);
        }
        s.prepare(checks, rules.maxMisses, cfg.contestIntroMs);
        final long chosen = seed;
        // Atomic: both free, or neither is taken.
        return Moments.begin(p, s, () -> {
            if (isGuardian(c)) {
                UUID attempt = attemptOf(c);
                if (attempt != null) PER_ATTEMPT.merge(attempt, 1, Integer::sum);
            }
            c.holdForContest(10);
            ClashCommon.hold(p);
            // Face to face.
            c.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
            Vec3 mid = c.position().add(0, c.getBbHeight() * 0.6, 0);
            ServerPlayNetworking.send(p, new TrainingNet.Start(s.id, 1, KIT, "BREAK ITS HOLD", false, chosen, cfg.contestIntroMs, cfg.contestDurationMs,
                    rules.phase2AtMs, rules.phase3AtMs, rules.maxMisses, p.getEyePosition(), mid, List.of("PUSH", "BRACE", "HEAVE"),
                    List.of(0xFFFF9A6A, 0xFFFFE27A, 0xFFFF6A8A), s.checks()));
            s.setState(p, TrainingNet.INTRO);
            s.sendMeter(p);
            if (p.level() instanceof ServerLevel l) {
                Fx.sound(l, p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.6f);
                Fx.shake(l, p.position(), 8, 0.4f, 8);
            }
            JJK.LOGGER.info("[contests] {} grabbed by {} ({})", p.getName().getString(), s.foe, s.preset.name());
        });
    }

    /** A key timing from the contestant's client (routed by {@link Moments}). */
    public static boolean input(ServerPlayer p, TrainingNet.Input in) {
        return active(p) && Moments.input(p, in);
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (!TELEGRAPHS.isEmpty()) {
            for (Telegraph t : List.copyOf(TELEGRAPHS.values())) if (now >= t.closesAt) close(server, t);
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
        Moments.setManualClockForTest(on);
    }

    /** Tests: no grabs happen by chance (only the ones a test starts). */
    public static void setRandomGrabsForTest(boolean on) {
        noRandomGrabs = !on;
    }

    public static void advanceForTest(ServerPlayer p, double ms) {
        if (active(p)) Moments.advanceForTest(p, ms);
    }

    public static void stepForTest(MinecraftServer server) {
        tick(server);
        Moments.tickForTest(server);
    }

    public static boolean protectedForTest(ServerPlayer p) {
        Long t = PROTECTED.get(p.getUUID());
        return t != null && p.level().getGameTime() < t;
    }

    public static void forgetForTest(ServerPlayer p, @Nullable Entity c) {
        PROTECTED.remove(p.getUUID());
        if (c != null) CURSE_READY.remove(c.getUUID());
    }
}
