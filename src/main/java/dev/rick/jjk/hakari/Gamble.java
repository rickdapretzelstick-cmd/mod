package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.GamblePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The gamble running inside one Idle Death Gamble. A state machine:
 * <pre>
 *   SPINNING ──(enough visual moves)──▶ RIICHI ──(reveal)──▶ JACKPOT ──▶ Hakari enters Jackpot, the domain closes
 *      ▲                                   │
 *      └──────────(next attempt)─────── MISS ──(no attempts left)──▶ DONE
 * </pre>
 * Visual moves are Hakari's own techniques used inside his domain. When enough have been made, a Riichi scenario is
 * drawn (Transit Card, Seat Struggle or Potty Emergency) with a signal colour (green, red, gold or the certain rainbow),
 * and its odds are rolled — hidden until the third reel stops. The first two reels always match; the third decides.
 * As the domain runs out, a final attempt is forced with slightly better odds. Odd jackpot numbers change the probability
 * of the next domain; even ones give it a head start (see {@link HakariState}).
 */
public final class Gamble {
    public enum State { SPINNING, RIICHI, MISS, JACKPOT, DONE }

    public enum Scenario {
        TRANSIT_CARD("TRANSIT CARD RIICHI"), SEAT_STRUGGLE("SEAT STRUGGLE RIICHI"), POTTY_EMERGENCY("POTTY EMERGENCY RIICHI");

        public final String title;

        Scenario(String title) {
            this.title = title;
        }
    }

    public enum Signal { GREEN, RED, GOLD, RAINBOW }

    /** Ticks into a Riichi when the third reel stops. */
    public static final int REVEAL_OFFSET = 10;

    public final DomainInstance domain;
    private final RandomSource rng;
    private State state = State.SPINNING;
    private int stateAge;
    private int progress;
    private int attempt;
    private Scenario scenario = Scenario.TRANSIT_CARD;
    private Signal signal = Signal.GREEN;
    private final int[] reels = {7, 1, 3};
    private float chance;
    private boolean jackpot;
    private boolean forced;
    private boolean dirty = true;

    Gamble(DomainInstance domain) {
        this.domain = domain;
        this.rng = RandomSource.create(domain.level.getGameTime() * 31 + domain.owner.getId());
        HakariState hs = HakariState.of(domain.owner);
        progress = Math.min(required() - 1, hs.headStart);
        shuffle();
    }

    public State state() {
        return state;
    }

    public boolean hitJackpot() {
        return jackpot;
    }

    private static JJKConfig.Hakari cfg() {
        return JJKConfig.get().hakari;
    }

    private int required() {
        return Math.max(1, cfg().visualMovesRequired);
    }

    /** Hakari used one of his techniques inside the domain. */
    void visualMove() {
        if (state != State.SPINNING) return;
        progress++;
        shuffle();
        Fx.play(domain.level, "gamble_visual", domain.owner.position().add(0, 1.2, 0), Vec3.ZERO, Math.min(1f, progress / (float) required()), domain.owner.getId());
        dirty = true;
    }

    private void shuffle() {
        for (int i = 0; i < 3; i++) reels[i] = 1 + rng.nextInt(7);
    }

    void tick() {
        stateAge++;
        JJKConfig.Hakari cfg = cfg();
        switch (state) {
            case SPINNING -> {
                boolean timeRunningOut = domain.remaining() < cfg.riichiTicks + 30 && attempt < cfg.maxAttempts;
                boolean ready = progress >= required() || timeRunningOut;
                // Wait for Hakari to finish what he's doing before the Riichi takes over.
                AbilityCaster c = Casters.getOrNull(domain.owner);
                boolean busy = c != null && c.isBusy() && stateAge < 400;
                if (ready && !busy && attempt < cfg.maxAttempts) startRiichi(timeRunningOut && progress < required());
            }
            case RIICHI -> {
                Statuses.apply(domain.owner, CombatStatus.GAMBLING, 3);
                int reveal = cfg.riichiTicks - REVEAL_OFFSET;
                if (stateAge == 16) Fx.play(domain.level, "gamble_signal", domain.owner.position().add(0, 2.4, 0), Vec3.ZERO, signal.ordinal(), domain.owner.getId());
                if (stateAge == reveal) {
                    Fx.play(domain.level, jackpot ? "gamble_hit" : "gamble_miss", domain.owner.position().add(0, 2.4, 0), Vec3.ZERO, 1f, domain.owner.getId());
                    dirty = true;
                }
                if (stateAge >= cfg.riichiTicks) {
                    if (jackpot) enterJackpot();
                    else set(attempt >= cfg.maxAttempts ? State.DONE : State.MISS);
                }
            }
            case MISS -> {
                if (stateAge >= cfg.missTicks) {
                    progress = 0;
                    shuffle();
                    set(State.SPINNING);
                }
            }
            default -> {}
        }
        if (dirty || domain.owner.tickCount % 20 == 0) sync();
    }

    private void set(State s) {
        state = s;
        stateAge = 0;
        dirty = true;
    }

    private void startRiichi(boolean forcedFinal) {
        JJKConfig.Hakari cfg = cfg();
        attempt++;
        forced = forcedFinal || attempt == cfg.maxAttempts;
        // Which scenario plays, and the colour of its signal.
        int s = rng.nextInt(100);
        scenario = s < 50 ? Scenario.TRANSIT_CARD : s < 85 ? Scenario.SEAT_STRUGGLE : Scenario.POTTY_EMERGENCY;
        float base = switch (scenario) {
            case TRANSIT_CARD -> cfg.transitCardOdds;
            case SEAT_STRUGGLE -> cfg.seatStruggleOdds;
            case POTTY_EMERGENCY -> cfg.pottyEmergencyOdds;
        };
        float roll = rng.nextFloat();
        if (roll < cfg.rainbowChance) signal = Signal.RAINBOW;
        else if (roll < cfg.rainbowChance + 0.12f) signal = Signal.GOLD;
        else if (roll < cfg.rainbowChance + 0.45f) signal = Signal.RED;
        else signal = Signal.GREEN;
        float mul = switch (signal) {
            case GREEN -> cfg.greenSignal;
            case RED -> cfg.redSignal;
            case GOLD -> cfg.goldSignal;
            case RAINBOW -> 100f;
        };
        HakariState hs = HakariState.of(domain.owner);
        chance = Math.min(1f, base * mul + hs.oddsBonus + (forced ? cfg.finalAttemptBonus : 0));
        jackpot = signal == Signal.RAINBOW || rng.nextFloat() < chance;
        if (signal == Signal.RAINBOW) chance = 1f;
        // Two reels always line up (that's what makes it a Riichi); the third decides.
        int n = 1 + rng.nextInt(7);
        reels[0] = n;
        reels[1] = n;
        reels[2] = jackpot ? n : 1 + Math.floorMod(n - 1 + (rng.nextBoolean() ? 1 : -1), 7);
        set(State.RIICHI);
        Fx.play(domain.level, "gamble_riichi", domain.owner.position().add(0, 1.2, 0), Vec3.ZERO, scenario.ordinal(), domain.owner.getId());
        Fx.shake(domain.level, domain.center, domain.radius + 8, 0.35f, 10);
    }

    private void enterJackpot() {
        set(State.JACKPOT);
        HakariState hs = HakariState.of(domain.owner);
        int n = reels[0];
        hs.lastJackpot = n;
        // The bonus of this jackpot replaces the one it consumed.
        hs.oddsBonus = n % 2 == 1 ? cfg().oddJackpotBonus : 0;
        hs.headStart = n % 2 == 0 ? cfg().evenJackpotHeadStart : 0;
        sync();
        HakariCharacter.jackpot(domain.owner, n);
        DomainManager.cancel(domain, DomainInstance.EndReason.CANCELLED);
    }

    /** Called when the domain ends without a jackpot. */
    void ended() {
        if (jackpot) return;
        // A miss still spends the domain; part of the meter comes back, and any bonus it carried was used up.
        HakariState hs = HakariState.of(domain.owner);
        hs.oddsBonus = 0;
        hs.headStart = 0;
        AbilityCaster c = Casters.getOrNull(domain.owner);
        if (c != null && !c.isAwakened()) c.setAwakening(c.awakening() + c.maxAwakening() * cfg().missRefund);
    }

    void sync() {
        dirty = false;
        broadcast(payload(true), payload(false));
    }

    void removed() {
        GamblePayload gone = new GamblePayload(domain.id, domain.owner.getId(), GamblePayload.REMOVED, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
        broadcast(gone, gone);
    }

    private GamblePayload payload(boolean forOwner) {
        int st = switch (state) {
            case SPINNING -> GamblePayload.SPINNING;
            case RIICHI -> GamblePayload.RIICHI;
            case MISS -> GamblePayload.MISS;
            case JACKPOT -> GamblePayload.JACKPOT;
            case DONE -> GamblePayload.DONE;
        };
        int dur = switch (state) {
            case RIICHI -> cfg().riichiTicks;
            case MISS -> cfg().missTicks;
            default -> 0;
        };
        // The third reel's true number isn't sent until it stops.
        boolean revealed = state != State.RIICHI || stateAge >= cfg().riichiTicks - REVEAL_OFFSET;
        String bonus = HakariState.of(domain.owner).bonusText();
        if (forced && state == State.RIICHI) bonus = "FINAL ATTEMPT";
        return new GamblePayload(domain.id, domain.owner.getId(), st, progress, required(), attempt, cfg().maxAttempts, scenario.ordinal(), signal.ordinal(),
                reels[0], reels[1], revealed ? reels[2] : 0, stateAge, dur, forOwner ? chanceShown() : -1f, forOwner ? bonus : "");
    }

    /** The odds the gambler sees: the next Riichi's rough odds while spinning, this one's once drawn. */
    private float chanceShown() {
        if (state == State.RIICHI || state == State.JACKPOT) return chance;
        JJKConfig.Hakari cfg = cfg();
        float avg = (cfg.transitCardOdds * 0.5f + cfg.seatStruggleOdds * 0.35f + cfg.pottyEmergencyOdds * 0.15f);
        return Math.min(1f, avg + HakariState.of(domain.owner).oddsBonus);
    }

    private void broadcast(GamblePayload owner, GamblePayload others) {
        double r = domain.radius + 24;
        for (ServerPlayer p : domain.level.players()) {
            if (p.position().distanceTo(domain.center) > r && p != domain.owner) continue;
            ServerPlayNetworking.send(p, p == domain.owner ? owner : others);
        }
    }
}
