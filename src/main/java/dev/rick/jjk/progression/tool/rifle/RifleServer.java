package dev.rick.jjk.progression.tool.rifle;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.RifleStatePayload;
import dev.rick.jjk.progression.grade.GradedCurse;
import dev.rick.jjk.progression.tool.CursedToolItem;
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Cursed Rifle, as the server runs it. Everything is decided here: when a shot fires and what it hits, what the
 * reserve pays, and the beam's whole sequence. Clients are told the phase (to play the matching clip, for everyone
 * nearby) and draw what the server says.
 *
 * <p>Controls (the cursed tools' use key): hold use to raise the scope, let go to fire (a quick tap is a hip shot,
 * swaying more). Once Unfolding Array is learned, sneak and hold use instead: the arms deploy, the lenses charge, the
 * rifle reaches its ready state; let go then to fire the beam. Letting go early, switching items, running the reserve
 * dry, dying or leaving all cancel it, and the arms retract.
 *
 * <p>The reserve ({@link ModAttachments#RIFLE_ENERGY}) is the rifle's own cursed energy, kept per player and refilling
 * over time: it never depends on a technique.
 */
public final class RifleServer {
    public enum Phase { IDLE, AIM, DEPLOY, CHARGE, READY, FIRE, COOLDOWN, RETRACT }

    static final class State {
        Phase phase = Phase.IDLE;
        int age;
        int duration;
        int aimTicks;
        @Nullable RifleBeam beam;
        long beamReadyAt;
        int sentPhase = -1;
        float sentEnergy = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private RifleServer() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RifleServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> drop(h.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            if (e instanceof ServerPlayer p) drop(p);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> STATES.clear());
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> sync(h.player, state(h.player), true));
    }

    static JJKConfig.Rifle cfg() {
        return JJKConfig.get().rifle;
    }

    public static boolean isRifle(ItemStack s) {
        return s.getItem() instanceof CursedToolItem t && t.definition() == CursedTools.CURSED_RIFLE;
    }

    static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** The phase a player's rifle is in (tests, HUD). */
    public static Phase phase(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? Phase.IDLE : s.phase;
    }

    @Nullable
    public static RifleBeam beam(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? null : s.beam;
    }

    // --- The reserve ---

    public static float energy(ServerPlayer p) {
        Float f = p.getAttached(ModAttachments.RIFLE_ENERGY);
        return f == null ? cfg().capacity : f;
    }

    public static void setEnergy(ServerPlayer p, float v) {
        p.setAttached(ModAttachments.RIFLE_ENERGY, Math.max(0f, Math.min(cfg().capacity, v)));
    }

    // --- Input (from the item, through RifleBehavior) ---

    /** Use pressed. True starts holding the use key (aiming or charging); false refuses. */
    static boolean use(ServerLevel level, ServerPlayer p, ItemStack stack) {
        Casters.get(p);
        String inert = RifleClaims.inertReason(p, stack);
        if (inert != null) {
            p.sendOverlayMessage(Component.literal(inert).withStyle(ChatFormatting.GRAY));
            return false;
        }
        State s = state(p);
        if (s.phase != Phase.IDLE && s.phase != Phase.AIM) return false;
        if (p.isShiftKeyDown() && RifleRules.beamUnlocked(p)) {
            if (level.getGameTime() < s.beamReadyAt) {
                p.sendOverlayMessage(Component.literal("The array is still cooling.").withStyle(ChatFormatting.GRAY));
                return false;
            }
            if (energy(p) < RifleRules.beamCost(p)) {
                p.sendOverlayMessage(Component.literal("The reserve is too low to open the array.").withStyle(ChatFormatting.GRAY));
                return false;
            }
            beginDeploy(level, p, s);
            return true;
        }
        set(p, s, Phase.AIM, 0);
        s.aimTicks = 0;
        return true;
    }

    /** Use released after {@code held} ticks. */
    static void release(ServerLevel level, ServerPlayer p, ItemStack stack, int held) {
        State s = state(p);
        switch (s.phase) {
            case AIM -> {
                shoot(level, p, stack, s);
                set(p, s, Phase.IDLE, 0);
            }
            case DEPLOY, CHARGE -> cancel(level, p, s, null);
            case READY -> fireBeam(level, p, s, null);
            default -> {}
        }
    }

    // --- Normal shots ---

    static void shoot(ServerLevel level, ServerPlayer p, ItemStack stack, State s) {
        if (p.getCooldowns().isOnCooldown(stack)) return;
        float cost = RifleRules.shotCost(p);
        if (energy(p) < cost) {
            Fx.sound(level, p.getEyePosition(), SoundEvents.DISPENSER_FAIL, 0.6f, 1.6f);
            p.sendOverlayMessage(Component.literal("The rifle's reserve is empty. Let it refill.").withStyle(ChatFormatting.GRAY));
            return;
        }
        setEnergy(p, energy(p) - cost);
        p.getCooldowns().addCooldown(stack, RifleRules.shotInterval(p));
        boolean scoped = s.aimTicks >= 4;
        float[] drift = RifleAim.drift(level.getGameTime(), p.getId(), s.aimTicks, RifleRules.sway(p), RifleRules.settleTicks(p), scoped, cfg().hipSwayDegrees);
        Vec3 dir = RifleAim.apply(p.getYRot(), p.getXRot(), drift);
        Vec3 from = p.getEyePosition();
        double range = cfg().shotRange;
        var clip = level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = clip.getType() == HitResult.Type.MISS ? range : clip.getLocation().distanceTo(from);
        LivingEntity target = firstAlong(level, p, from, dir, reach);
        double at = target == null ? reach : target.getBoundingBox().inflate(0.25).clip(from, from.add(dir.scale(reach))).map(v -> v.distanceTo(from)).orElse(reach);
        Vec3 end = from.add(dir.scale(at));
        Vec3 muzzle = muzzle(p, dir);
        Fx.play(level, "rifle_shot", muzzle, end.subtract(muzzle), target != null ? 1f : 0f, p.getId());
        Fx.sound(level, muzzle, SoundEvents.FIREWORK_ROCKET_BLAST, 1.6f, 0.55f);
        Fx.sound(level, muzzle, SoundEvents.GENERIC_EXPLODE, 0.35f, 1.9f);
        if (target != null) {
            // A curse's grade has its own say on tool damage (CursedDamage): the Mastery bonus is applied there, once.
            float dmg = target instanceof GradedCurse ? cfg().shotDamage : RifleRules.shotDamage(p);
            HitResolver.resolve(Hit.builder(p, "rifle_shot").damage(dmg).tag(AttackTag.PROJECTILE).knockback(Knockback.directional(dir, 0.35, 0.08))
                    .hitstun(6).origin(muzzle).noComboScaling().fx("curse_bite", 0.7f).build(), target);
        } else if (clip.getType() != HitResult.Type.MISS) {
            Fx.play(level, "rifle_impact", end, dir.scale(-1), 1f, p.getId());
        }
    }

    /** The first living thing the round meets before {@code reach}. */
    @Nullable
    static LivingEntity firstAlong(ServerLevel level, ServerPlayer p, Vec3 from, Vec3 dir, double reach) {
        Vec3 to = from.add(dir.scale(reach));
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
                e -> e != p && e.isAlive() && !e.isSpectator() && Targeting.canTarget(p, e));
        for (LivingEntity e : near) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.25).clip(from, to);
            if (hit.isEmpty()) continue;
            double t = hit.get().distanceToSqr(from);
            if (t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }

    /** Where the barrel ends (the model's beam_origin, held at the right side). */
    public static Vec3 muzzle(LivingEntity p, Vec3 dir) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        return p.getEyePosition().add(dir.scale(1.1)).add(side.scale(0.3)).add(0, -0.22, 0);
    }

    // --- The beam ---

    static void beginDeploy(ServerLevel level, ServerPlayer p, State s) {
        float output = RifleRules.output(p);
        s.beam = new RifleBeam(p, level, output);
        set(p, s, Phase.DEPLOY, cfg().deployTicks);
        Fx.sound(level, p.getEyePosition(), SoundEvents.PISTON_EXTEND, 0.8f, 0.7f);
        // The counter opportunity begins the moment it starts charging.
        BeamClashManager.threaten(s.beam, level.getGameTime() + cfg().deployTicks + RifleRules.chargeTicks(p));
    }

    /**
     * Fires the charged beam: at {@code at} if given (an answer to someone else's beam), else where they aim. Tries the
     * shared clash system's answer first, so firing while a window is open on you is the counter.
     */
    static boolean fireBeam(ServerLevel level, ServerPlayer p, State s, @Nullable Vec3 at) {
        if (s.phase != Phase.READY || s.beam == null) return false;
        float cost = RifleRules.beamCost(p);
        if (energy(p) < cost) {
            cancel(level, p, s, "The reserve gave out.");
            return false;
        }
        if (at == null && BeamClashManager.tryCounter(Casters.get(p))) return true;
        setEnergy(p, energy(p) - cost);
        Vec3 dir = at == null ? p.getLookAngle() : at.subtract(p.getEyePosition()).normalize();
        s.beam.fire(muzzle(p, dir), dir, RifleRules.beamTicks(p));
        set(p, s, Phase.FIRE, s.beam.lifetime());
        return true;
    }

    /** Called by {@link RifleCounter}: fire the ready beam straight back at an incoming one. */
    static boolean answer(ServerPlayer p, Vec3 at) {
        State s = state(p);
        return fireBeam((ServerLevel) p.level(), p, s, at);
    }

    /** Stops whatever the beam was doing and folds the arms away. */
    static void cancel(ServerLevel level, ServerPlayer p, State s, @Nullable String why) {
        if (s.beam != null) {
            s.beam.stop();
            s.beam = null;
        }
        if (why != null) p.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
        set(p, s, Phase.RETRACT, cfg().retractTicks);
        Fx.sound(level, p.getEyePosition(), SoundEvents.PISTON_CONTRACT, 0.7f, 0.8f);
    }

    /** A player gone (death, logout): nothing of theirs may keep running. */
    static void drop(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        if (s != null && s.beam != null) s.beam.stop();
    }

    // --- Every tick ---

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level)) continue;
            // The reserve refills on its own (a twentieth of the per-second rate a tick).
            float e = energy(p);
            if (e < cfg().capacity) setEnergy(p, e + cfg().regenPerSecond / 20f);
            State s = STATES.get(p.getUUID());
            if (s != null) tick(level, p, s);
            if (s != null || isRifle(p.getMainHandItem())) sync(p, s == null ? state(p) : s, false);
        }
    }

    static void tick(ServerLevel level, ServerPlayer p, State s) {
        s.age++;
        boolean held = isRifle(p.getMainHandItem());
        boolean using = held && p.isUsingItem() && isRifle(p.getUseItem());
        if (!p.isAlive()) {
            drop(p);
            return;
        }
        switch (s.phase) {
            case IDLE -> {}
            case AIM -> {
                if (!using) set(p, s, Phase.IDLE, 0);
                else s.aimTicks++;
            }
            case DEPLOY, CHARGE, READY -> {
                if (!using) {
                    cancel(level, p, s, null);
                    return;
                }
                if (s.beam != null) s.beam.aim(muzzle(p, p.getLookAngle()), p.getLookAngle());
                if (s.phase == Phase.DEPLOY && s.age >= s.duration) {
                    set(p, s, Phase.CHARGE, RifleRules.chargeTicks(p));
                } else if (s.phase == Phase.CHARGE) {
                    if (s.age % 3 == 0) Fx.play(level, "rifle_charge", muzzle(p, p.getLookAngle()), p.getLookAngle(), s.age / (float) Math.max(1, s.duration), p.getId());
                    if (s.age >= s.duration) {
                        set(p, s, Phase.READY, 0);
                        Fx.play(level, "rifle_ready", muzzle(p, p.getLookAngle()), p.getLookAngle(), s.beam == null ? 0.45f : s.beam.output(), p.getId());
                        Fx.sound(level, p.getEyePosition(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.6f);
                        // Ready and held: still answerable, for as long as it is held.
                        if (s.beam != null) BeamClashManager.threaten(s.beam, level.getGameTime() + 40);
                    }
                } else if (s.phase == Phase.READY) {
                    setEnergy(p, energy(p) - cfg().readyDrainPerSecond / 20f);
                    if (energy(p) < RifleRules.beamCost(p)) cancel(level, p, s, "The reserve gave out.");
                    else if (s.age % 10 == 0) Fx.play(level, "rifle_charge", muzzle(p, p.getLookAngle()), p.getLookAngle(), 1f, p.getId());
                }
            }
            case FIRE -> {
                RifleBeam b = s.beam;
                // Switching away mid-beam lets it go (a clash it was in is forfeited by the shared rules).
                if (b == null || !held) {
                    if (b != null) b.stop();
                    s.beam = null;
                    set(p, s, Phase.COOLDOWN, cfg().cooldownTicks);
                    return;
                }
                if (!b.tick()) {
                    s.beam = null;
                    s.beamReadyAt = level.getGameTime() + cfg().beamCooldown;
                    set(p, s, Phase.COOLDOWN, cfg().cooldownTicks);
                }
            }
            case COOLDOWN -> {
                if (s.age >= s.duration) set(p, s, Phase.RETRACT, cfg().retractTicks);
            }
            case RETRACT -> {
                if (s.age >= s.duration) set(p, s, Phase.IDLE, 0);
            }
        }
    }

    static void set(ServerPlayer p, State s, Phase phase, int duration) {
        s.phase = phase;
        s.age = 0;
        s.duration = duration;
        sync(p, s, true);
    }

    /** Tells the shooter and everyone tracking them (so all see the same clip). */
    static void sync(ServerPlayer p, State s, boolean force) {
        float e = energy(p);
        if (!force && s.sentPhase == s.phase.ordinal() && Math.abs(e - s.sentEnergy) < 1f) return;
        s.sentPhase = s.phase.ordinal();
        s.sentEnergy = e;
        float output = s.beam != null ? s.beam.output() : RifleRules.output(p);
        RifleStatePayload payload = new RifleStatePayload(p.getId(), s.phase.ordinal(), s.duration, output, e, cfg().capacity,
                RifleRules.beamUnlocked(p), RifleRules.sway(p), RifleRules.settleTicks(p));
        Fx.toTrackers(p, payload, true);
    }

    /** Test hook: advance a player's rifle one tick as the server would. */
    public static void tickForTest(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s != null && p.level() instanceof ServerLevel level) tick(level, p, s);
    }

    /** Test hooks: the same input the item gives. */
    public static boolean useForTest(ServerPlayer p) {
        return use((ServerLevel) p.level(), p, p.getMainHandItem());
    }

    public static void releaseForTest(ServerPlayer p) {
        release((ServerLevel) p.level(), p, p.getMainHandItem(), 20);
    }

    /** Test hook: set a phase directly (e.g. straight to READY). */
    public static void forcePhaseForTest(ServerPlayer p, Phase phase) {
        State s = state(p);
        if (s.beam == null && phase != Phase.IDLE && phase != Phase.AIM) s.beam = new RifleBeam(p, (ServerLevel) p.level(), RifleRules.output(p));
        set(p, s, phase, 0);
    }
}
