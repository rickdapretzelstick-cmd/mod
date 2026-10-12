package dev.rick.jjk.progression.contest;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.fx.Impact;
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
 * in its line of sight, free to be grabbed. Then both are locked in a struggle: one key at a time pops up (W, A, S or
 * D, your movement keys; only the next one is ever shown, never the same twice running) and every right key pushes a
 * 0-100 tug-of-war meter your way against the curse's steady pressure ({@link ContestPreset}). A wrong key slips it back
 * and locks the keys for a moment (mashing loses). It goes on until one of you wins: 100 breaks free (the curse is
 * staggered and wide open), 0 is overpowered (you take the hit and are thrown). After a while its grip tightens, so it
 * always ends. The order of keys comes from a seed both sides know; the server judges every key.
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

    /**
     * The kinds with a grapple of their own ({@code <kind>_grapple*} clips from tools/gen_curse_models.py), and how far
     * apart the two stand locked together (its reach: a toad's bulk keeps you further off than a bride's hands).
     */
    static final Map<String, Double> GRAPPLE_REACH = Map.of("school_maw", 1.6, "school_crawler", 1.5, "forest_stalker", 1.25,
            "grave_hound", 1.45, "veiled_bride", 1.2, "bloat_toad", 1.9, "brood_mother", 1.7);
    /** The keys, as directions: the player's forward, left, back and right movement keys (W, A, S, D by default). */
    public static final int UP = 0, LEFT = 1, DOWN = 2, RIGHT = 3, KEYS = 4;
    /** How many keys a contest's order runs to before it repeats (far more than any contest lasts). */
    public static final int ORDER_LENGTH = 512;
    /** The player's beat for each right key: W drives forward, S digs in, A and D wrench it aside (tools/gen_contest_anims.py). */
    static final String[] BEATS = {"grapple_push", "grapple_heave", "grapple_brace", "grapple_heave"};
    /** How far the meter moves the pair: winning drives it back by up to this, losing lets it bear in as far. */
    static final double DRIFT = 0.5;
    /** Every this many right keys in a row is a surge (a bigger shove, an ink frame; at most one frame per {@link #FRAME_GAP_TICKS}). */
    static final int STREAK = 8, FRAME_GAP_TICKS = 30;

    /** One contest: the player, the curse that grabbed them, its preset, and the meter between them. */
    public static final class Session extends Moment {
        public final UUID curse;
        public final ContestPreset preset;
        public final String foe;
        /** The curse's kind, whether it has a grapple of its own, and how far apart the two are held. */
        public final String kind;
        final boolean rig;
        final double reach;
        double meter = ContestPreset.START;
        @Nullable Boolean won;
        int lastFrame = -FRAME_GAP_TICKS;
        int beats;
        /** The order of keys, the one shown now, and the run of right keys since the last wrong one. */
        final int[] order;
        int next, streak;
        /** Contest time (ms) until which keys are locked (after a wrong one), and of the last key taken. */
        double lockedUntil = Double.NEGATIVE_INFINITY, lastKey = Double.NEGATIVE_INFINITY;

        Session(UUID player, UUID curse, ContestPreset preset, String foe, String kind, double width, long seed) {
            super(player, curse);
            this.order = order(seed);
            this.curse = curse;
            this.preset = preset;
            this.foe = foe;
            this.kind = kind;
            this.rig = GRAPPLE_REACH.containsKey(kind);
            this.reach = GRAPPLE_REACH.getOrDefault(kind, width * 0.5 + 0.9);
        }

        /** Beats played so far (one per key judged), for tests. */
        public int beats() {
            return beats;
        }

        /** The key shown now (a direction), and its place in the order. */
        public int shown() {
            return order[next % order.length];
        }

        public int nextIndex() {
            return next;
        }

        /** The flat direction from the player to the curse. */
        private static Vec3 toward(ServerPlayer p, Entity c) {
            Vec3 d = c.position().subtract(p.position()).multiply(1, 0, 1);
            return d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()).multiply(1, 0, 1).normalize() : d.normalize();
        }

        /** Where their hands meet. */
        private static Vec3 contact(ServerPlayer p, CommonCurseEntity c) {
            return p.position().add(toward(p, c).scale(0.55)).add(0, 1.15, 0);
        }

        /** The grab lands: the curse is set at its kind's reach, the two face each other and lock into the grip. */
        void stage(ServerPlayer p, CommonCurseEntity c) {
            Vec3 want = p.position().add(toward(p, c).scale(reach));
            Vec3 to = new Vec3(want.x, c.getY(), want.z);
            if (c.level().noCollision(c, c.getBoundingBox().move(to.subtract(c.position())))) c.teleportTo(to.x, to.y, to.z);
            face(p, c);
            Anim.play(p, "grapple_hold");
            if (rig) Anim.playOn(c, kind + "_grapple");
        }

        private static void face(ServerPlayer p, CommonCurseEntity c) {
            ClashCommon.face(p, c.position().add(0, Math.min(c.getBbHeight() * 0.6, 1.4), 0));
            c.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
            c.setYBodyRot(c.getYHeadRot());
        }

        /** The meter shows in the bodies: winning, you drive it back; losing, it bears in on you (eased, never into a wall). */
        private void drift(ServerPlayer p, CommonCurseEntity c) {
            double d = reach + (meter - ContestPreset.START) / (ContestPreset.MAX - ContestPreset.START) * DRIFT;
            Vec3 want = p.position().add(toward(p, c).scale(Math.max(0.8, d)));
            Vec3 step = new Vec3(want.x - c.getX(), 0, want.z - c.getZ()).scale(0.25);
            if (step.lengthSqr() < 1e-5 || !c.level().noCollision(c, c.getBoundingBox().move(step))) return;
            c.setPos(c.position().add(step));
        }

        /** One key as a beat of the grapple: who gives ground, the shove of air where they push, the bodies' clips. */
        private void beat(ServerPlayer p, ServerLevel l, CommonCurseEntity c, int result, int dir) {
            beats++;
            Vec3 at = contact(p, c), along = toward(p, c);
            if (result == TrainingCheck.MISS) {
                Anim.play(p, "grapple_strain");
                if (rig) Anim.playOn(c, kind + "_grapple_press");
                Impact.shove(l, at, along.scale(-1), 0.8f);
                Fx.shake(l, p.position(), 6, 0.35f, 6);
                return;
            }
            boolean surge = streak > 0 && streak % STREAK == 0;
            Anim.play(p, BEATS[Math.floorMod(dir, BEATS.length)]);
            if (rig) Anim.playOn(c, kind + "_grapple_shove");
            Impact.shove(l, at, along, surge ? 1.2f : 0.7f);
            // Digging in (S) kicks the dust up round the feet.
            if (dir == DOWN) Impact.dust(l, p.position(), surge ? 1.0f : 0.7f);
            if (surge) {
                Fx.shake(l, p.position(), 6, 0.45f, 5);
                if (stateTicks - lastFrame >= FRAME_GAP_TICKS) {
                    lastFrame = stateTicks;
                    Impact.frame(p, Impact.INK, 0.5f);
                }
            }
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
            if (c != null) {
                c.holdForContest(4);
                // Locked face to face, the pair giving and taking ground with the meter.
                drift(p, c);
                c.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
                c.setYBodyRot(c.getYHeadRot());
            }
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
            // (Only this contest's own keys are judged: it has no skill-check schedule.)
            if (o.result() == TrainingCheck.HOLDING) return;
            sendMeter(p);
            if (p.level() instanceof ServerLevel l) {
                if (o.result() == TrainingCheck.MISS) Fx.sound(l, p.position(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.6f, 0.7f);
                else Fx.sound(l, p.position(), SoundEvents.ANVIL_LAND, 0.25f, streak % STREAK == 0 ? 1.9f : 1.3f + 0.05f * (streak % STREAK));
                CommonCurseEntity c = curse(p);
                if (c != null && state == TrainingNet.ACTIVE) beat(p, l, c, o.result(), order[o.check() % order.length]);
            }
            decide(p);
        }

        /**
         * A key from the contestant: the right one (the one shown, at its place in the order) pushes the meter and shows
         * the next; any other slips it back and locks the keys for a moment. Keys while locked, too close together, or
         * for a place already passed are ignored. Releases mean nothing here.
         */
        @Override
        protected boolean key(ServerPlayer p, TrainingNet.Input in) {
            if (in.action() != TrainingNet.PRESS || state != TrainingNet.ACTIVE) return true;
            JJKConfig.Training cfg = JJKConfig.get().training, rules = rules();
            // The key's time is the client's own stamp, kept within what the connection allows (so it can't be faked
            // to press faster than allowed), and never before the last one.
            double t = Math.max(Math.max(clock - rules.maxLatencyMs, Math.min(clock + rules.clockSlackMs, in.clientMs())), lastKey);
            if (in.check() != next || in.gauge() < 0 || in.gauge() >= KEYS || t < lockedUntil || t - lastKey < cfg.contestMinPressGapMs) {
                // Not taken: the client is told where the contest stands, so the key it shows is the right one.
                ServerPlayNetworking.send(p, new TrainingNet.Judge(id, attempt(), next, TrainingCheck.HOLDING, 0));
                return true;
            }
            lastKey = t;
            int at = next;
            if (in.gauge() == shown()) {
                meter = preset.pushed(meter);
                streak++;
                next++;
                judge(p, new TrainingEvaluator.Outcome(at, TrainingCheck.GOOD));
            } else {
                meter = preset.slipped(meter);
                streak = 0;
                lockedUntil = t + cfg.contestLockoutMs;
                judge(p, new TrainingEvaluator.Outcome(at, TrainingCheck.MISS));
            }
            return true;
        }

        @Override
        protected void beforeChecks(ServerPlayer p, double dtMs) {
            double before = meter;
            JJKConfig.Training cfg = JJKConfig.get().training;
            meter = preset.pressed(meter, dtMs, clock, cfg.contestTightenAfterMs, cfg.contestTightenPerSecond);
            if ((int) before / 5 != (int) meter / 5 || stateTicks % 5 == 0) sendMeter(p);
        }

        @Override
        protected void afterChecks(ServerPlayer p) {
            decide(p);
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
            Entity c = ((ServerLevel) p.level()).getEntity(curse);
            // (No line to end above: it goes on until one side wins.)
            ServerPlayNetworking.send(p, new TrainingNet.Meter(id, (float) meter, -1f, foe, c == null ? -1 : c.getId(), kind));
        }

        /** Over when the meter reaches either end. */
        private void decide(ServerPlayer p) {
            if (state != TrainingNet.ACTIVE) return;
            if (meter >= ContestPreset.MAX) finish(p, true);
            else if (meter <= 0) finish(p, false);
        }

        /** The result: broken free (it reels, wide open) or overpowered (the hit, and thrown). */
        void finish(ServerPlayer p, boolean win) {
            won = win;
            sendMeter(p);
            setState(p, win ? TrainingNet.SUCCESS : TrainingNet.FAILURE);
            ServerLevel level = (ServerLevel) p.level();
            CommonCurseEntity c = curse(p);
            if (c == null) return;
            Vec3 away = p.position().subtract(c.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
            if (win) {
                // The reversal: time slows on the heave, the frame inks, and it is thrown down hard enough to split the ground.
                Anim.play(p, "grapple_throw");
                c.lostContest(level, p);
                c.setDeltaMovement(c.getDeltaMovement().add(-away.x * 0.6, 0.2, -away.z * 0.6));
                Impact.slowMotion(p, 450);
                Impact.frame(p, Impact.INK, 1f);
                Impact.crack(level, c.position(), away.scale(-1), 1.6f);
                Impact.dust(level, c.position(), 1.3f);
                Impact.shove(level, contact(p, c), away.scale(-1), 1.4f);
                Fx.sound(level, c.position(), SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
                p.sendOverlayMessage(Component.literal("You break its hold. It's wide open!").withStyle(ChatFormatting.GOLD));
            } else {
                // Overpowered: the world goes negative for a beat and you are flung off it.
                Anim.play(p, "grapple_thrown");
                if (rig) Anim.playOn(c, kind + "_grapple_fling");
                c.wonContest(30);
                ClashCommon.release(p);
                p.hurtServer(level, level.damageSources().mobAttack(c), preset.loseDamage());
                p.setDeltaMovement(away.x * 0.9, 0.35, away.z * 0.9);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
                Impact.frame(p, Impact.NEGATIVE, 0.8f);
                Impact.shove(level, contact(p, c), away, 1.2f);
                Impact.dust(level, p.position(), 0.9f);
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
                // Broken off (no result): both let go of the grip.
                CommonCurseEntity c = curse(p);
                if (c != null) {
                    c.releaseFromContest();
                    if (rig) Anim.playOn(c, "");
                }
                Anim.releaseHeld(p);
            }
        }
    }

    public static void init() {
        dev.rick.jjk.core.combat.CombatEvents.HIT_RESOLVED.add(StrengthContests::onHit);
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
        if (dev.rick.jjk.progression.finish.Binds.binding(p) || dev.rick.jjk.progression.finish.Finishers.finishing(p)) return "busy";
        return null;
    }

    /** Why this curse can't grab right now (null if it can). */
    @Nullable
    public static String cannotGrab(CommonCurseEntity c) {
        if (!JJKConfig.get().training.contests) return "contests off";
        if (!c.isAlive() || !eligible(c)) return "not a grabber";
        if (Moments.ofFoe(c) != null || TELEGRAPHS.containsKey(c.getUUID())) return "already grabbing";
        if (dev.rick.jjk.progression.finish.Binds.bound(c) || c.chaining() || c.inBeat()) return "busy";
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

    // --- Rescue ---

    /**
     * Someone else lands a hit on a curse while it reaches for a player (its telegraph): the grab is broken off before it
     * closes. The curse reels (a stagger) and has to wait its full cooldown to reach again; both players see the cut.
     */
    static void onHit(dev.rick.jjk.core.combat.HitResult r) {
        if (r.outcome() != dev.rick.jjk.core.combat.HitResult.Outcome.HIT || !(r.hit().attacker instanceof ServerPlayer rescuer)
                || !(r.target() instanceof CommonCurseEntity c) || !(c.level() instanceof ServerLevel level)) return;
        Telegraph t = TELEGRAPHS.get(c.getUUID());
        if (t == null || t.player.equals(rescuer.getUUID())) return;
        TELEGRAPHS.remove(c.getUUID());
        ServerPlayer victim = level.getServer().getPlayerList().getPlayer(t.player);
        rescue(level, c, rescuer, victim);
    }

    static void rescue(ServerLevel level, CommonCurseEntity c, ServerPlayer rescuer, @Nullable ServerPlayer victim) {
        JJKConfig.Training cfg = JJKConfig.get().training;
        CURSE_READY.put(c.getUUID(), level.getGameTime() + cfg.contestCurseCooldownSeconds * 20L);
        c.releaseFromContest();
        c.staggerBy(level, rescuer);
        Vec3 at = c.position().add(0, c.getBbHeight() * 0.6, 0);
        Vec3 dir = at.subtract(rescuer.getEyePosition());
        dir = dir.lengthSqr() < 1e-4 ? rescuer.getLookAngle() : dir.normalize();
        Fx.play(level, "impact_rescue", at, dir, 1.2f);
        dev.rick.jjk.core.fx.Impact.frame(rescuer, dev.rick.jjk.core.fx.Impact.INK, 0.8f);
        Fx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        Fx.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.1f);
        rescuer.sendOverlayMessage(Component.literal("RESCUE! You broke its grab.").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (victim != null) {
            dev.rick.jjk.core.fx.Impact.frame(victim, dev.rick.jjk.core.fx.Impact.INK, 0.6f);
            victim.sendOverlayMessage(Component.literal(rescuer.getName().getString() + " broke its grab!").withStyle(ChatFormatting.AQUA));
        }
        JJK.LOGGER.info("[contests] {} rescued {} from {}", rescuer.getName().getString(), victim == null ? "?" : victim.getName().getString(), c.getName().getString());
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
        long seed = p.getRandom().nextLong();
        Session s = new Session(p.getUUID(), c.getUUID(), presetFor(c), c.getName().getString(), c.curseKind(), c.getBbWidth(), seed);
        JJKConfig.Training rules = s.rules();
        // No skill-check schedule: its keys are its own (the client builds the same order from the seed).
        s.prepare(List.of(), rules.maxMisses, cfg.contestIntroMs);
        final long chosen = seed;
        // Atomic: both free, or neither is taken.
        return Moments.begin(p, s, () -> {
            if (isGuardian(c)) {
                UUID attempt = attemptOf(c);
                if (attempt != null) PER_ATTEMPT.merge(attempt, 1, Integer::sum);
            }
            c.holdForContest(10);
            ClashCommon.hold(p);
            // Face to face, at its reach, locked into the grip.
            s.stage(p, c);
            Vec3 mid = c.position().add(0, c.getBbHeight() * 0.6, 0);
            ServerPlayNetworking.send(p, new TrainingNet.Start(s.id, 1, KIT, "BREAK ITS HOLD", false, chosen, cfg.contestIntroMs, 0,
                    0, 0, rules.maxMisses, p.getEyePosition(), mid, List.of(), List.of(), List.of()));
            s.setState(p, TrainingNet.INTRO);
            s.sendMeter(p);
            if (p.level() instanceof ServerLevel l) {
                Fx.sound(l, p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.6f);
                Fx.shake(l, p.position(), 8, 0.4f, 8);
            }
            JJK.LOGGER.info("[contests] {} grabbed by {} ({})", p.getName().getString(), s.foe, s.preset.name());
        });
    }

    /** A key from the contestant's client (routed by {@link Moments}). */
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

    /** A contest's order of keys from its seed: random directions, never the same twice running (the same on both sides). */
    public static int[] order(long seed) {
        java.util.SplittableRandom r = new java.util.SplittableRandom(seed);
        int[] out = new int[ORDER_LENGTH];
        out[0] = r.nextInt(KEYS);
        for (int i = 1; i < out.length; i++) out[i] = (out[i - 1] + 1 + r.nextInt(KEYS - 1)) % KEYS;
        return out;
    }

    // --- Simulation (tuning and tests) ---

    /** How a simulated contest ended: who won, where the meter was, and when (ms after the intro). */
    public record Simulated(boolean won, double meter, double atMs) {}

    /**
     * Plays a contest against a preset as the server scores it, on a 10 ms clock: a key every {@code 1000 / perSecond}
     * ms (none at 0), the n-th of them (from 1) the wrong key when {@code wrong} says so (it slips the meter and locks
     * the keys; a key while locked is lost); the curse's pressure (tightening past its point) all the while. Gives up at
     * {@code limitMs}.
     */
    public static Simulated simulate(ContestPreset preset, double perSecond, java.util.function.IntPredicate wrong, JJKConfig.Training cfg, double limitMs) {
        double meter = ContestPreset.START, t = 0, nextKey = perSecond > 0 ? 1000 / perSecond : Double.POSITIVE_INFINITY, locked = -1;
        int n = 0;
        while (t < limitMs) {
            t += 10;
            meter = preset.pressed(meter, 10, t, cfg.contestTightenAfterMs, cfg.contestTightenPerSecond);
            if (meter <= 0) return new Simulated(false, 0, t);
            if (t >= nextKey) {
                nextKey += Math.max(cfg.contestMinPressGapMs, 1000 / perSecond);
                if (t < locked) continue;
                n++;
                if (wrong.test(n)) {
                    meter = preset.slipped(meter);
                    locked = t + cfg.contestLockoutMs;
                    if (meter <= 0) return new Simulated(false, 0, t);
                } else {
                    meter = preset.pushed(meter);
                    if (meter >= ContestPreset.MAX) return new Simulated(true, meter, t);
                }
            }
        }
        return new Simulated(meter >= ContestPreset.START, meter, t);
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

    /** Tests: decides a running contest now (broken free or overpowered), as its checks would have. */
    public static void finishForTest(ServerPlayer p, boolean win) {
        Session s = of(p);
        if (s != null && s.state() == TrainingNet.ACTIVE) s.finish(p, win);
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
