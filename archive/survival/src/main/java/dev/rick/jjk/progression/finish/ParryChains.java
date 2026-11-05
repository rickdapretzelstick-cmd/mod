package dev.rick.jjk.progression.finish;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.fx.Impact;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.moment.Moments;
import dev.rick.jjk.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Parry chains: a Grade 1 guardian's string of {@link #MIN_BLOWS}-{@link #MAX_BLOWS} blows, each one flagged a beat
 * before it lands with a glint on its striking point and a ping that climbs in pitch through the string. Every blow is
 * a real hit; starting a guard just as it lands (the perfect block's window) parries it, and a parry inside a string
 * doesn't stun the guardian: the next blow comes. Parry the whole string and its posture breaks: it shatters out of the
 * string reeling, wide open for {@link #BREAK_TICKS}, and a finisher can be landed on it from half its health
 * ({@link Finishers#open}). Miss one and the string just runs out. Nothing locks: the player is free throughout.
 *
 * <p>A guardian strings one at most every {@link #CHAIN_GAP_TICKS}, only at someone in its reach who isn't busy in a
 * moment, a bind or a finisher. The string itself runs on the curse ({@link CommonCurseEntity#beginChain}).
 */
public final class ParryChains {
    public static final int MIN_BLOWS = 3, MAX_BLOWS = 5, BLOW_GAP = 12, BREAK_TICKS = 60, CHAIN_GAP_TICKS = 240;
    public static final double RANGE = 3.6;

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static boolean randomChains = true;

    private ParryChains() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ParryChains::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> LAST.clear());
    }

    /** Whether this curse strings parry chains: a Grade 1 contract's guardian. */
    public static boolean chains(CommonCurseEntity c) {
        return StrengthContests.isGuardian(c);
    }

    private static void tick(MinecraftServer server) {
        if (!randomChains || server.getTickCount() % 5 != 0) return;
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level) || !p.isAlive() || p.isSpectator() || busy(p)) continue;
            for (CommonCurseEntity c : level.getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(RANGE + 3),
                    e -> e.isAlive() && e.getTarget() == p && e.readyToGrab() && chains(e))) {
                if (c.distanceTo(p) > RANGE + c.getBbWidth() * 0.5 || !free(c)) continue;
                Long last = LAST.get(c.getUUID());
                if (last != null && now - last < CHAIN_GAP_TICKS) continue;
                if (p.getRandom().nextFloat() > 0.3f) continue;
                start(c, p, MIN_BLOWS + p.getRandom().nextInt(MAX_BLOWS - MIN_BLOWS + 1));
                break;
            }
        }
        if (now % 1200 == 0) LAST.values().removeIf(t -> now - t > CHAIN_GAP_TICKS * 4L);
    }

    private static boolean busy(ServerPlayer p) {
        return Moments.busy(p) || Finishers.finishing(p) || Binds.binding(p) || StrengthContests.active(p);
    }

    private static boolean free(CommonCurseEntity c) {
        return !StrengthContests.locked(c) && !StrengthContests.telegraphing(c) && !Binds.bound(c) && !Finishers.beingFinished(c) && !c.inBeat();
    }

    /** Starts a string of {@code blows} at {@code p} now (the test hook too). False if the curse is busy. */
    public static boolean start(CommonCurseEntity c, ServerPlayer p, int blows) {
        if (!(c.level() instanceof ServerLevel level) || !free(c) || !c.beginChain(p, blows, BLOW_GAP)) return false;
        LAST.put(c.getUUID(), level.getGameTime());
        Fx.sound(level, c.position(), SoundEvents.WARDEN_ANGRY, 0.6f, 1.5f);
        p.sendOverlayMessage(Component.literal("A string of blows: parry each one on its glint!").withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Where a curse's blows land from: just in front of it, at chest height, toward its prey. */
    static Vec3 strikePoint(CommonCurseEntity c, LivingEntity t) {
        Vec3 to = t.position().subtract(c.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
        return c.position().add(flat.scale(c.getBbWidth() * 0.5 + 0.5)).add(0, Math.min(c.getBbHeight() * 0.7, 1.5), 0);
    }

    /** The {@code i}-th blow's tell: a glint on its striking point and a ping higher than the last. */
    public static void glint(ServerLevel level, CommonCurseEntity c, LivingEntity t, int i) {
        Vec3 at = strikePoint(c, t);
        Fx.play(level, "impact_glint", at, Vec3.ZERO, 1f + 0.1f * i);
        Fx.sound(level, at, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 1.25f + 0.12f * i);
    }

    /** A blow landed: parried (a ringing clash, rising through the string) or not (the string is broken). */
    public static void blow(ServerLevel level, CommonCurseEntity c, LivingEntity t, int i, boolean parried) {
        Vec3 at = strikePoint(c, t);
        if (!parried) {
            // The first one missed: the rest can't break it now.
            if (c.chainParried() == i && t instanceof ServerPlayer p) {
                p.sendOverlayMessage(Component.literal("Missed the parry: the chain holds.").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        Vec3 dir = at.subtract(t.getEyePosition());
        Fx.sound(level, at, ModSounds.get("clash_perfect"), 0.9f, 0.85f + 0.17f * i);
        Fx.sound(level, at, SoundEvents.ANVIL_PLACE, 0.4f, 1.6f + 0.08f * i);
        Impact.shove(level, at, dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize(), 0.7f);
        if (t instanceof ServerPlayer p) {
            p.sendOverlayMessage(Component.literal("PARRY " + (i + 1) + " / " + c.chainBlows()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    /** The string ran out: every blow parried breaks its posture, wide open for a finisher. */
    public static void ended(ServerLevel level, CommonCurseEntity c, LivingEntity t, boolean all) {
        if (!all || !c.isAlive()) return;
        Vec3 at = c.position().add(0, c.getBbHeight() * 0.6, 0);
        Vec3 dir = at.subtract(t.getEyePosition());
        dir = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize();
        c.postureBreak(level, t, BREAK_TICKS);
        Finishers.open(c, BREAK_TICKS);
        Fx.play(level, "impact_shatter", at, dir, 1.1f + c.getBbWidth() * 0.3f);
        Impact.crack(level, c.position(), dir, 1.1f);
        Impact.dust(level, c.position(), 1.1f);
        Fx.shake(level, at, 16, 0.8f, 10);
        Fx.sound(level, at, SoundEvents.GLASS_BREAK, 1f, 0.6f);
        Fx.sound(level, at, SoundEvents.ANVIL_LAND, 0.7f, 0.7f);
        if (t instanceof ServerPlayer p) {
            Impact.frame(p, Impact.INK, 1f);
            Impact.slowMotion(p, 450);
            p.sendOverlayMessage(Component.literal("POSTURE BROKEN · FINISH IT").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
    }

    // --- Tests ---

    public static void setRandomChainsForTest(boolean on) {
        randomChains = on;
    }

    public static void forgetForTest(CommonCurseEntity c) {
        LAST.remove(c.getUUID());
    }
}
