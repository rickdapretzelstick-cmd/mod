package dev.rick.jjk.core.fx;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.util.Motion;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every Black Flash's presentation, decided from what actually happened (the server's resolved hit), never from what was
 * hoped for: a blow that whiffs, is blocked or is negated only sparks ({@code bf_fizzle}); one that connects is the full
 * Black Flash ({@code bf_impact}); one that kills is its lethal escalation ({@code bf_lethal}), at most once per body.
 * Presentation only: damage, kills and rewards stay with the move and the combat pipeline, so nothing here can execute
 * anyone or hand out anything twice.
 *
 * <p>A connecting Black Flash also holds a short local hit-stop: the two fighters hang in the moment of contact for a few
 * ticks (the target's launch is held and then given back, the attacker hovers), which is what sells the impact frame.
 */
public final class BlackFlash {
    /** How it ended, as presented. */
    public enum Shown { FIZZLE, IMPACT, LETHAL }

    /** A launch held for the hit-stop: given back to the target when it ends. */
    private record Held(LivingEntity target, @Nullable LivingEntity attacker, Vec3 velocity, long releaseAt) {}

    private static final List<Held> HELD = new ArrayList<>();
    /**
     * A lethal Black Flash's cinematic (after JJS's Resolute Black Flash finisher): the body held in the contact while the
     * power amps up, sparking and crackling ever harder, then launched away with the flash. Presentation only: the kill
     * already happened (resolved), this only holds and throws the body.
     */
    private record Finish(LivingEntity body, LivingEntity attacker, Vec3 at, Vec3 dir, float power, long start, int length) {}

    private static final List<Finish> FINISHES = new ArrayList<>();
    /** Per body: the game time its lethal Black Flash was shown (never twice for the same death). */
    private static final Map<UUID, Long> LETHAL_SHOWN = new HashMap<>();

    private BlackFlash() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BlackFlash::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            HELD.clear();
            FINISHES.clear();
            LETHAL_SHOWN.clear();
        });
    }

    /**
     * Presents a Black Flash blow from its resolved result ({@code r}; null when nothing was there to hit). {@code power}
     * scales it (1 a single flash, ~0.8 a chain link, 1.6+ the fourth "KOKUSEN" or a finisher).
     */
    public static Shown land(ServerLevel level, LivingEntity attacker, @Nullable LivingEntity target, @Nullable HitResult r, Vec3 at, Vec3 dir, float power) {
        Vec3 d = dir.lengthSqr() < 1e-6 ? attacker.getLookAngle() : dir.normalize();
        if (target == null || r == null || !r.connected()) {
            Fx.play(level, "bf_fizzle", at, d, Math.max(0.6f, power * 0.7f), attacker.getId());
            return Shown.FIZZLE;
        }
        boolean dead = target.isDeadOrDying() || !target.isAlive();
        if (dead && LETHAL_SHOWN.putIfAbsent(target.getUUID(), level.getGameTime()) == null) {
            Fx.play(level, "bf_lethal", at, d, Math.max(1.4f, power), attacker.getId());
            Fx.shake(level, at, 40, 1.1f, 16);
            finish(level, attacker, target, at, d, power);
            JJK.LOGGER.debug("[black flash] lethal on {} by {}", target.getName().getString(), attacker.getName().getString());
            return Shown.LETHAL;
        }
        Fx.play(level, "bf_impact", at, d, power, attacker.getId());
        Fx.shake(level, at, 24, Math.min(1f, 0.55f * power), 10);
        if (!dead) hitStop(level, attacker, target, JJKConfig.get().general.blackFlashHitStop);
        return Shown.IMPACT;
    }

    /** The lethal cinematic: hold the body in the contact for its build-up, cue the striker's camera, then launch it. */
    private static void finish(ServerLevel level, LivingEntity attacker, LivingEntity body, Vec3 at, Vec3 d, float power) {
        int length = Math.max(4, JJKConfig.get().general.blackFlashFinisherTicks);
        FINISHES.removeIf(f -> f.body == body);
        FINISHES.add(new Finish(body, attacker, at, d, power, level.getGameTime(), length));
        Motion.set(body, Vec3.ZERO);
        Motion.set(attacker, Vec3.ZERO);
        Statuses.apply(attacker, CombatStatus.HOVER, length + 2);
        Statuses.apply(attacker, CombatStatus.MELEE_ARMOR, length + 10);
        // The striker's cinematic (and the struck one's, if it was a player): dir.x the body, dir.y the build-up (ms).
        Fx.play(level, "bf_cam", at, new Vec3(body.getId(), length * 50.0, attacker.getId()), power, attacker.getId());
        if (body instanceof net.minecraft.server.level.ServerPlayer) Fx.play(level, "bf_cam", at, new Vec3(body.getId(), length * 50.0, attacker.getId()), power, body.getId());
    }

    /** Whether a lethal cinematic is holding this body (tests). */
    public static boolean finishing(LivingEntity body) {
        for (Finish f : FINISHES) if (f.body == body) return true;
        return false;
    }

    /** Whether this body's lethal Black Flash was already shown (tests). */
    public static boolean lethalShown(LivingEntity target) {
        return LETHAL_SHOWN.containsKey(target.getUUID());
    }

    /** Whether a hit-stop is holding this body right now (tests). */
    public static boolean held(LivingEntity e) {
        for (Held h : HELD) if (h.target == e || h.attacker == e) return true;
        return false;
    }

    /** Both caught in the contact for {@code ticks}: the target's launch held (and given back after), the attacker hanging. */
    private static void hitStop(ServerLevel level, LivingEntity attacker, LivingEntity target, int ticks) {
        if (ticks <= 0) return;
        HELD.removeIf(h -> h.target == target);
        HELD.add(new Held(target, attacker, target.getDeltaMovement(), level.getGameTime() + ticks));
        Motion.set(target, Vec3.ZERO);
        Motion.set(attacker, Vec3.ZERO);
        Statuses.apply(attacker, CombatStatus.HOVER, ticks + 1);
        if (target.isAlive()) Statuses.apply(target, CombatStatus.HOVER, ticks + 1);
    }

    private static void tickFinishes(MinecraftServer server) {
        for (Finish f : List.copyOf(FINISHES)) {
            if (f.body.isRemoved() || !(f.body.level() instanceof ServerLevel level) || f.attacker.level() != f.body.level()) {
                FINISHES.remove(f);
                continue;
            }
            long t = level.getGameTime() - f.start;
            if (t < f.length) {
                // Held in the contact, the corpse kept (not yet cleared away) while the power amps up.
                f.body.deathTime = Math.min(f.body.deathTime, 2);
                Motion.set(f.body, Vec3.ZERO);
                if (f.attacker.isAlive()) Motion.set(f.attacker, Vec3.ZERO);
                if (t % 2 == 0) Fx.play(level, "bf_amp", f.at, f.dir, f.power * (0.6f + 0.8f * t / f.length), f.attacker.getId());
                if (t % 6 == 0) Fx.shake(level, f.at, 30, 0.25f + 0.5f * t / f.length, 6);
                continue;
            }
            // The launch: the white flash, the shockwave, and the body thrown far along the blow.
            FINISHES.remove(f);
            Fx.play(level, "bf_launch", f.at, f.dir, f.power, f.attacker.getId());
            Fx.shake(level, f.at, 48, 1.2f, 18);
            Motion.set(f.body, f.dir.scale(3.6).add(0, 1.15, 0));
        }
    }

    private static void tick(MinecraftServer server) {
        if (!FINISHES.isEmpty()) tickFinishes(server);
        if (!HELD.isEmpty()) {
            long now = server.overworld().getGameTime();
            for (Held h : List.copyOf(HELD)) {
                if (h.target.isRemoved()) {
                    HELD.remove(h);
                    continue;
                }
                if (now < h.releaseAt) {
                    // Held in place (gravity, knockback from elsewhere: the moment waits).
                    Motion.set(h.target, Vec3.ZERO);
                    continue;
                }
                HELD.remove(h);
                Motion.set(h.target, h.velocity);
            }
        }
        if (!LETHAL_SHOWN.isEmpty() && server.getTickCount() % 200 == 0) {
            long now = server.overworld().getGameTime();
            LETHAL_SHOWN.values().removeIf(t -> now - t > 1200);
        }
    }
}
