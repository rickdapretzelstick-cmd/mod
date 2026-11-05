package dev.rick.jjk.progression.finish;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.fx.Impact;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.moment.Moments;
import dev.rick.jjk.util.Motion;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Binds: a heavy that lands on a curse while it is winding up its own heavy, and the two blows meet. Both are locked
 * where they stand for at most {@link #BIND_TICKS}, grinding against each other: every tap of the attack button drives
 * the bind {@link #TAP} toward the player, while the curse bears down on it all the time (harder the higher its grade,
 * hardest for a guardian). Whoever has the better of it when it runs out (or drives it all the way) wins it: a won bind
 * staggers the curse wide open; a lost one throws the player back, hurt and reeling.
 *
 * <p>Sparks and a grinding at the point where the blows meet, a tight two-shot on the player's screen and the bind's
 * meter on the HUD (client.finish.BindClient). At most one bind per {@link #BIND_GAP_TICKS} for each player, never in a
 * moment, a finisher or a contest, never on a curse already locked in something.
 */
public final class Binds {
    public static final int BIND_TICKS = 40, BIND_GAP_TICKS = 240, WINDUP_GRACE = 3;
    public static final float TAP = 0.06f, START = 0.5f;

    /** A bind under way: who, with which curse, where it stands, how far it has gone. */
    static final class Bind {
        final UUID player, curse;
        final float pressure;
        float meter = START;
        int ticks, taps;

        Bind(UUID player, UUID curse, float pressure) {
            this.player = player;
            this.curse = curse;
            this.pressure = pressure;
        }
    }

    private static final Map<UUID, Bind> BINDS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Binds() {}

    public static void init() {
        CombatEvents.HIT_RESOLVED.add(Binds::onHit);
        ServerTickEvents.END_SERVER_TICK.register(Binds::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            BINDS.clear();
            LAST.clear();
        });
    }

    // --- Queries ---

    public static boolean binding(ServerPlayer p) {
        return BINDS.containsKey(p.getUUID());
    }

    public static boolean bound(Entity curse) {
        for (Bind b : BINDS.values()) if (b.curse.equals(curse.getUUID())) return true;
        return false;
    }

    /** The bind's meter for this player (0: the curse has it, 1: they do), or -1 if they aren't in one. */
    public static float meter(ServerPlayer p) {
        Bind b = BINDS.get(p.getUUID());
        return b == null ? -1 : b.meter;
    }

    /** How hard a curse bears down in a bind, per tick. */
    public static float pressure(CommonCurseEntity c) {
        return 0.004f + 0.0025f * c.curseGrade().rank() + (StrengthContests.isGuardian(c) ? 0.003f : 0f);
    }

    // --- The trigger ---

    static void onHit(HitResult r) {
        if (r.outcome() != HitResult.Outcome.HIT || !(r.hit().attacker instanceof ServerPlayer p) || !(r.target() instanceof CommonCurseEntity c)) return;
        if (!r.hit().has(AttackTag.HEAVY) || !r.hit().has(AttackTag.MELEE) || !c.woundUpWithin(WINDUP_GRACE)) return;
        if (!(c.level() instanceof ServerLevel level) || !available(p) || !free(c)) return;
        start(level, p, c);
    }

    private static boolean available(ServerPlayer p) {
        if (!p.isAlive() || binding(p) || Moments.busy(p) || Finishers.finishing(p) || StrengthContests.active(p)) return false;
        Long last = LAST.get(p.getUUID());
        return last == null || p.level().getGameTime() - last >= BIND_GAP_TICKS;
    }

    private static boolean free(CommonCurseEntity c) {
        return c.isAlive() && !c.isStaggered() && !bound(c) && !StrengthContests.locked(c) && !StrengthContests.telegraphing(c) && !Finishers.beingFinished(c) && !c.inBeat();
    }

    /** Starts a bind now (the test hook too). False if either is busy. */
    public static boolean start(ServerLevel level, ServerPlayer p, CommonCurseEntity c) {
        if (!available(p) || !free(c)) return false;
        Bind b = new Bind(p.getUUID(), c.getUUID(), pressure(c));
        BINDS.put(p.getUUID(), b);
        hold(p, c);
        Anim.play(p, "grapple_strain");
        Anim.playOn(c, c.curseKind() + "_grapple_press");
        Vec3 at = contact(p, c);
        Fx.play(level, "impact_bind", at, new Vec3(c.getId(), BIND_TICKS * 50, 0), 1f, p.getId());
        Impact.frame(p, Impact.INK, 0.7f);
        Fx.sound(level, at, SoundEvents.ANVIL_LAND, 1f, 0.6f);
        Fx.shake(level, at, 8, 0.5f, 8);
        p.sendOverlayMessage(Component.literal("BIND! Mash attack!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        JJK.LOGGER.info("[binds] {} binds with {}", p.getName().getString(), c.getName().getString());
        return true;
    }

    /** Where the two blows meet: between them, at chest height. */
    static Vec3 contact(ServerPlayer p, CommonCurseEntity c) {
        Vec3 a = p.position().add(0, 1.2, 0), b = c.position().add(0, Math.min(c.getBbHeight() * 0.6, 1.5), 0);
        Vec3 d = b.subtract(a);
        double toCurse = Math.max(0.3, d.length() - c.getBbWidth() * 0.5);
        return d.lengthSqr() < 1e-4 ? a : a.add(d.normalize().scale(Math.min(toCurse, 0.9)));
    }

    private static void hold(ServerPlayer p, CommonCurseEntity c) {
        ClashCommon.hold(p);
        p.setDeltaMovement(Vec3.ZERO);
        c.holdForContest(4);
        ClashCommon.face(p, c.position().add(0, Math.min(c.getBbHeight() * 0.6, 1.4), 0));
    }

    // --- Pressing it ---

    /** A tap of the attack button from a player in a bind (from the client: MeleeInputPayload.TAP). */
    public static void tap(ServerPlayer p) {
        Bind b = BINDS.get(p.getUUID());
        if (b == null || !(p.level() instanceof ServerLevel level) || !(level.getEntity(b.curse) instanceof CommonCurseEntity c)) return;
        b.taps++;
        b.meter = Math.min(1f, b.meter + TAP);
        Vec3 at = contact(p, c);
        Fx.play(level, "impact_bind_spark", at, new Vec3(b.meter, 0, 0), 0.6f, p.getId());
        Fx.sound(level, at, SoundEvents.GRINDSTONE_USE, 0.5f, 0.8f + 0.7f * b.meter);
        // Driven all the way home: it breaks at once.
        if (b.meter >= 1f) resolve(level, p, c, b, true);
    }

    private static void tick(MinecraftServer server) {
        if (BINDS.isEmpty()) return;
        for (Bind b : List.copyOf(BINDS.values())) step(server, b);
    }

    static void step(MinecraftServer server, Bind b) {
        ServerPlayer p = server.getPlayerList().getPlayer(b.player);
        Entity e = p == null ? null : ((ServerLevel) p.level()).getEntity(b.curse);
        if (p == null || !p.isAlive() || !(e instanceof CommonCurseEntity c) || !c.isAlive() || c.level() != p.level()) {
            BINDS.remove(b.player);
            if (p != null) {
                Anim.releaseHeld(p);
                if (p.level() instanceof ServerLevel l) Fx.play(l, "impact_bind_end", p.position(), new Vec3(0.5, 0, 0), 1f, p.getId());
            }
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        b.ticks++;
        hold(p, c);
        b.meter = Math.max(0f, b.meter - b.pressure);
        if (b.ticks % 4 == 0) {
            Vec3 at = contact(p, c);
            Fx.play(level, "impact_bind_spark", at, new Vec3(b.meter, 0, 0), 0.4f, p.getId());
            Fx.sound(level, at, SoundEvents.GRINDSTONE_USE, 0.35f, 0.6f + 0.5f * b.meter);
        }
        if (b.meter >= 1f || b.meter <= 0f || b.ticks >= BIND_TICKS) resolve(level, p, c, b, b.meter >= START);
    }

    /** The bind breaks: the loser is thrown off balance. */
    static void resolve(ServerLevel level, ServerPlayer p, CommonCurseEntity c, Bind b, boolean won) {
        BINDS.remove(b.player);
        LAST.put(p.getUUID(), level.getGameTime());
        c.releaseFromContest();
        Anim.releaseHeld(p);
        Vec3 at = contact(p, c);
        Vec3 dir = c.position().subtract(p.position());
        dir = new Vec3(dir.x, 0, dir.z);
        dir = dir.lengthSqr() < 1e-4 ? p.getLookAngle() : dir.normalize();
        Fx.play(level, "impact_bind_end", at, new Vec3(won ? 1 : 0, 0, 0), 1f, p.getId());
        if (won) {
            Anim.play(p, "grapple_push");
            c.staggerBy(level, p);
            Impact.frame(p, Impact.INK, 1f);
            Impact.crack(level, c.position(), dir, 1f);
            Impact.shove(level, at, dir, 1.2f);
            Fx.shake(level, at, 14, 0.7f, 10);
            Fx.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
            p.sendOverlayMessage(Component.literal("BIND WON · IT'S OPEN!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            Anim.play(p, "grapple_thrown");
            c.wonContest(15);
            float hurt = 3f + 2f * c.curseGrade().rank();
            p.hurtServer(level, level.damageSources().mobAttack(c), hurt);
            Motion.set(p, dir.scale(-0.9).add(0, 0.35, 0));
            Statuses.apply(p, CombatStatus.GUARD_BROKEN, 20);
            Impact.frame(p, Impact.NEGATIVE, 0.8f);
            Impact.shove(level, at, dir.scale(-1), 1.1f);
            Fx.sound(level, at, SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.7f);
            p.sendOverlayMessage(Component.literal("It overpowered you.").withStyle(ChatFormatting.RED));
        }
    }

    // --- Tests ---

    public static void tapForTest(ServerPlayer p) {
        tap(p);
    }

    public static void forgetForTest(ServerPlayer p) {
        BINDS.remove(p.getUUID());
        LAST.remove(p.getUUID());
    }

    @Nullable
    public static Integer tapsForTest(ServerPlayer p) {
        Bind b = BINDS.get(p.getUUID());
        return b == null ? null : b.taps;
    }
}
