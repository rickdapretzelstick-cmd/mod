package dev.rick.jjk.progression.finish;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.fx.Impact;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.moment.Moments;
import dev.rick.jjk.registry.ModDamageTypes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fighting curses has two payoffs of its own, both server-decided from how hits resolve:
 *
 * <ul>
 *   <li><b>Perfect dodge, then counter.</b> A curse's blow that whiffs on someone mid-dash (inside the dash's
 *   invulnerable frames: they dashed out at the last moment) is a perfect dodge: time slows for them for a beat and for
 *   {@link #COUNTER_TICKS} their next melee hit on a curse is a counter, which staggers it (wide open) and deals
 *   {@link #COUNTER_BONUS} more. At most one perfect dodge per {@link #DODGE_GAP_TICKS}.</li>
 *   <li><b>Finisher.</b> A melee hit on a curse that is exposed (staggered, reeling, open) and down to
 *   {@link #FINISH_BELOW} of its health starts a finisher: both are held for {@link #FINISH_TICKS}, the camera cuts
 *   through a short cinematic, and the character's own finishing blow lands (Gojo's Red, Yuji's Black Flash, Hakari's
 *   doors, Yuta's Rika, Ryu's Granite Blast, or a plain haymaker for someone with no technique), killing it with the
 *   finisher's own hit's damage type (so a curse's immunities still decide).</li>
 * </ul>
 *
 * Neither happens inside a moment (a strength contest, training, a containment) or to a curse locked in one.
 */
public final class Finishers {
    public static final int COUNTER_TICKS = 24, DODGE_GAP_TICKS = 30, FINISH_TICKS = 30, FINISH_BLOW_TICK = 18;
    public static final float COUNTER_BONUS = 0.5f, FINISH_BELOW = 0.25f;

    record Counter(long until) {}

    /** A finisher under way: who, on which curse, with which character's blow and which damage type, and how far in. */
    static final class Finish {
        final UUID player, curse;
        final String character;
        final ResourceKey<DamageType> damageType;
        int ticks;

        Finish(UUID player, UUID curse, String character, ResourceKey<DamageType> damageType) {
            this.player = player;
            this.curse = curse;
            this.character = character;
            this.damageType = damageType;
        }
    }

    /** Each character's finisher: its wind-up and blow clips, the effect where it lands, and its impact frame style. */
    record Style(String windup, String blow, String fx, float fxScale, int frame) {}

    static final Map<String, Style> STYLES = Map.of(
            "gojo", new Style("red_charge", "red_release", "red_explosion", 1.2f, Impact.INK),
            "yuji", new Style("divergent_windup", "black_flash_punch", "black_flash_finisher", 1.2f, Impact.BLACK_FLASH),
            "hakari", new Style("rough_charge", "rough_strike", "shutter_finisher", 1.1f, Impact.INK),
            "yuta", new Style("yuta_resolute_windup", "yuta_resolute_slash", "rika_smash_finisher", 1.1f, Impact.INK),
            "ryu", new Style("ryu_granite_aim", "ryu_granite_fire", "granite_finisher", 1.1f, Impact.NEGATIVE));
    /** No technique: a wound-up haymaker. */
    static final Style PLAIN = new Style("heavy_charge", "uppercut", "finisher", 1f, Impact.INK);

    private static final Map<UUID, Long> LAST_DODGE = new HashMap<>();
    private static final Map<UUID, Counter> COUNTERS = new HashMap<>();
    private static final Map<UUID, Finish> FINISHES = new HashMap<>();
    /** Per curse: when its players were last told it can be finished (so the hint isn't repeated every tick). */
    private static final Map<UUID, Long> HINTED = new HashMap<>();

    private Finishers() {}

    public static void init() {
        CombatEvents.HIT_RESOLVED.add(Finishers::onHit);
        ServerTickEvents.END_SERVER_TICK.register(Finishers::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            LAST_DODGE.clear();
            COUNTERS.clear();
            FINISHES.clear();
            HINTED.clear();
        });
    }

    // --- Queries (tests and the HUD) ---

    public static boolean counterReady(ServerPlayer p) {
        Counter c = COUNTERS.get(p.getUUID());
        return c != null && p.level().getGameTime() <= c.until;
    }

    public static boolean finishing(ServerPlayer p) {
        return FINISHES.containsKey(p.getUUID());
    }

    public static boolean beingFinished(Entity curse) {
        for (Finish f : FINISHES.values()) if (f.curse.equals(curse.getUUID())) return true;
        return false;
    }

    /** Whether a hit on this curse now would finish it: exposed, low, free (not in a moment or already being finished). */
    public static boolean finishable(CommonCurseEntity c) {
        return c.isAlive() && c.isExposed() && c.getHealth() <= c.getMaxHealth() * FINISH_BELOW && !StrengthContests.locked(c) && !beingFinished(c);
    }

    // --- How hits resolve ---

    static void onHit(HitResult r) {
        if (!(r.target().level() instanceof ServerLevel level)) return;
        // A curse's blow passing through someone mid-dash: a perfect dodge.
        if (r.outcome() == HitResult.Outcome.WHIFF && r.target() instanceof ServerPlayer p && r.hit().attacker instanceof CommonCurseEntity c
                && Combat.state(p).isEvading()) {
            perfectDodge(level, p, c);
            return;
        }
        if (r.outcome() != HitResult.Outcome.HIT || !(r.hit().attacker instanceof ServerPlayer p) || !(r.target() instanceof CommonCurseEntity c)
                || !r.hit().has(AttackTag.MELEE) || finishing(p) || Moments.busy(p)) return;
        if (counterReady(p)) {
            COUNTERS.remove(p.getUUID());
            counter(level, p, c, r);
            return;
        }
        if (finishable(c)) start(level, p, c, r.hit().damageType);
    }

    static void perfectDodge(ServerLevel level, ServerPlayer p, CommonCurseEntity c) {
        long now = level.getGameTime();
        Long last = LAST_DODGE.get(p.getUUID());
        if (last != null && now - last < DODGE_GAP_TICKS) return;
        LAST_DODGE.put(p.getUUID(), now);
        COUNTERS.put(p.getUUID(), new Counter(now + COUNTER_TICKS));
        Impact.slowMotion(p, 650);
        Fx.play(level, "evade", p.position().add(0, 0.9, 0), Vec3.ZERO, 1.6f, p.getId());
        Fx.sound(level, p.position(), SoundEvents.ENDER_EYE_DEATH, 0.8f, 1.6f);
        p.sendOverlayMessage(Component.literal("PERFECT DODGE · COUNTER!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    static void counter(ServerLevel level, ServerPlayer p, CommonCurseEntity c, HitResult r) {
        float bonus = Math.max(2f, r.damageDealt() * COUNTER_BONUS);
        c.hurtServer(level, ModDamageTypes.source(level, r.hit().damageType, p, p), bonus);
        if (c.isAlive()) c.staggerBy(level, p);
        Vec3 at = c.position().add(0, c.getBbHeight() * 0.6, 0);
        Vec3 dir = at.subtract(p.getEyePosition());
        Impact.frame(p, Impact.INK, 0.8f);
        Impact.shove(level, at, dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize(), 1.1f);
        Fx.shake(level, at, 10, 0.6f, 8);
        Fx.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.7f);
        p.sendOverlayMessage(Component.literal("COUNTER!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // --- Finishers ---

    static Style style(String character) {
        return STYLES.getOrDefault(character, PLAIN);
    }

    static void start(ServerLevel level, ServerPlayer p, CommonCurseEntity c, ResourceKey<DamageType> damageType) {
        AbilityCaster caster = Casters.getOrNull(p);
        String character = caster != null && caster.character() != null ? caster.character().id : "";
        Finish f = new Finish(p.getUUID(), c.getUUID(), character, damageType);
        FINISHES.put(p.getUUID(), f);
        Style s = style(character);
        hold(p, c);
        ClashCommon.face(p, c.position().add(0, Math.min(c.getBbHeight() * 0.6, 1.4), 0));
        Anim.play(p, s.windup);
        Impact.slowMotion(p, FINISH_BLOW_TICK * 50);
        // The cinematic (the finisher's camera), on this player's screen only.
        Fx.play(level, "impact_finisher_cam", c.position(), new Vec3(c.getId(), FINISH_TICKS * 50, 0), 1f, p.getId());
        Fx.sound(level, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 1.4f);
        p.sendOverlayMessage(Component.literal("FINISH IT").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        JJK.LOGGER.info("[finishers] {} finishes {} ({})", p.getName().getString(), c.getName().getString(), character.isEmpty() ? "no technique" : character);
    }

    private static void hold(ServerPlayer p, CommonCurseEntity c) {
        ClashCommon.hold(p);
        p.setDeltaMovement(Vec3.ZERO);
        c.holdForContest(4);
    }

    private static void tick(MinecraftServer server) {
        if (!FINISHES.isEmpty()) {
            for (Finish f : List.copyOf(FINISHES.values())) step(server, f);
        }
        if (server.getTickCount() % 5 == 0) hint(server);
    }

    static void step(MinecraftServer server, Finish f) {
        ServerPlayer p = server.getPlayerList().getPlayer(f.player);
        Entity e = p == null ? null : ((ServerLevel) p.level()).getEntity(f.curse);
        if (p == null || !p.isAlive() || !(e instanceof CommonCurseEntity c) || !c.isAlive() && f.ticks < FINISH_BLOW_TICK) {
            FINISHES.remove(f.player);
            return;
        }
        f.ticks++;
        if (c.isAlive()) hold(p, c);
        if (f.ticks == FINISH_BLOW_TICK) blow((ServerLevel) p.level(), p, c, f);
        if (f.ticks >= FINISH_TICKS) FINISHES.remove(f.player);
    }

    /** The finishing blow: the character's clip and effect, the impact frame, and the kill (the body sent flying). */
    static void blow(ServerLevel level, ServerPlayer p, CommonCurseEntity c, Finish f) {
        Style s = style(f.character);
        Vec3 at = c.position().add(0, c.getBbHeight() * 0.55, 0);
        Vec3 dir = at.subtract(p.getEyePosition());
        dir = dir.lengthSqr() < 1e-4 ? p.getLookAngle() : dir.normalize();
        Anim.play(p, s.blow);
        Fx.play(level, s.fx, at, dir, s.fxScale, c.getId());
        Impact.frame(p, s.frame, 1.3f);
        Impact.crack(level, c.position(), dir, 1.4f);
        Impact.dust(level, c.position(), 1.4f);
        Fx.shake(level, at, 24, 1.0f, 14);
        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.3f);
        c.releaseFromContest();
        c.hurtServer(level, ModDamageTypes.source(level, f.damageType, p, p), c.getHealth() + c.getMaxHealth());
        c.setDeltaMovement(dir.scale(1.8).add(0, 0.8, 0));
    }

    /** Players near a curse that can be finished are told so, once in a while (the hit that does it is theirs to land). */
    private static void hint(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level) || finishing(p) || Moments.busy(p)) continue;
            for (CommonCurseEntity c : level.getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(6), Finishers::finishable)) {
                Long last = HINTED.get(c.getUUID());
                if (last != null && now - last < 60) continue;
                HINTED.put(c.getUUID(), now);
                p.sendOverlayMessage(Component.literal("It's reeling. Hit it to finish it!").withStyle(ChatFormatting.RED));
                break;
            }
        }
        if (now % 600 == 0) HINTED.values().removeIf(t -> now - t > 600);
    }

    // --- Tests ---

    public static void stepForTest(MinecraftServer server) {
        tick(server);
    }

    @Nullable
    public static String characterOfFinishForTest(ServerPlayer p) {
        Finish f = FINISHES.get(p.getUUID());
        return f == null ? null : f.character;
    }

    public static void forgetForTest(ServerPlayer p) {
        LAST_DODGE.remove(p.getUUID());
        COUNTERS.remove(p.getUUID());
        FINISHES.remove(p.getUUID());
    }
}
