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
 * As in Jujutsu Shenanigans: visual moves are specific techniques Hakari lands inside his domain (Reserve Balls, Shutter
 * Doors, the combination of both counting twice, Fever Breaker's dropkick landing, Fever Crush, a successful Door Guard).
 * Two of them start a Riichi scenario: Transit Card (one star) or Travel Emergency (two stars, better odds), rolled in
 * secret until the third reel stops. There are four attempts; the fourth is a guaranteed "pity" jackpot (with half the
 * usual Jackpot time) as long as someone was caught in the domain, and without one the domain breaks after it. Odd
 * jackpot numbers raise the next domain's odds; even ones make its Riichi scenarios play twice as fast (see
 * {@link HakariState}).
 */
public final class Gamble {
    public enum State { SPINNING, RIICHI, MISS, JACKPOT, DONE }

    public enum Scenario {
        TRANSIT_CARD("TRANSIT CARD RIICHI", 1), TRAVEL_EMERGENCY("TRAVEL EMERGENCY RIICHI", 2);

        public final String title;
        /** Out of three: more stars, better odds. */
        public final int stars;

        Scenario(String title, int stars) {
            this.title = title;
            this.stars = stars;
        }
    }

    /** The colour the Riichi plays in: green for one star, gold for two, rainbow for the pity jackpot. */
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
    private boolean pity;
    /** Someone was caught in the domain (the pity jackpot needs a witness). */
    private boolean caughtSomeone;
    /** Riichi scenarios play at double speed (the bonus of an even jackpot). */
    private final boolean fast;
    private boolean dirty = true;

    Gamble(DomainInstance domain) {
        this.domain = domain;
        this.rng = RandomSource.create(domain.level.getGameTime() * 31 + domain.owner.getId());
        HakariState hs = HakariState.of(domain.owner);
        fast = hs.fastRiichi;
        shuffle();
    }

    /** Length of a Riichi scenario in this domain (halved after an even jackpot). */
    public int riichiTicks() {
        return fast ? Math.max(Gamble.REVEAL_OFFSET + 8, cfg().riichiTicks / 2) : cfg().riichiTicks;
    }

    public boolean pity() {
        return pity;
    }

    void caught() {
        caughtSomeone = true;
    }

    public State state() {
        return state;
    }

    public int progress() {
        return progress;
    }

    public int attempt() {
        return attempt;
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

    /** Hakari landed a visual move inside the domain ({@code count} for a combination worth two). */
    void visualMove(int count) {
        if (state != State.SPINNING) return;
        progress = Math.min(required(), progress + count);
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
                boolean timeRunningOut = domain.remaining() < riichiTicks() + 30 && attempt < cfg.maxAttempts;
                boolean ready = progress >= required() || timeRunningOut;
                // Wait for Hakari to finish what he's doing before the Riichi takes over.
                AbilityCaster c = Casters.getOrNull(domain.owner);
                boolean busy = c != null && c.isBusy() && stateAge < 400;
                if (ready && !busy && attempt < cfg.maxAttempts) startRiichi(timeRunningOut && progress < required());
            }
            case RIICHI -> {
                Statuses.apply(domain.owner, CombatStatus.GAMBLING, 3);
                int reveal = riichiTicks() - REVEAL_OFFSET;
                if (stateAge == 16) Fx.play(domain.level, "gamble_signal", domain.owner.position().add(0, 2.4, 0), Vec3.ZERO, signal.ordinal(), domain.owner.getId());
                if (stateAge == reveal) {
                    Fx.play(domain.level, jackpot ? "gamble_hit" : "gamble_miss", domain.owner.position().add(0, 2.4, 0), Vec3.ZERO, 1f, domain.owner.getId());
                    dirty = true;
                }
                if (stateAge >= riichiTicks()) {
                    if (jackpot) enterJackpot();
                    else if (attempt >= cfg.maxAttempts) {
                        // Out of scenarios: the domain breaks.
                        set(State.DONE);
                        sync();
                        DomainManager.cancel(domain, DomainInstance.EndReason.EXPIRED);
                    } else set(State.MISS);
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
        // The last scenario is the pity jackpot, if anyone was caught in the domain to see it.
        pity = attempt >= cfg.maxAttempts && caughtSomeone;
        scenario = rng.nextFloat() < cfg.travelEmergencyChance ? Scenario.TRAVEL_EMERGENCY : Scenario.TRANSIT_CARD;
        float base = scenario == Scenario.TRAVEL_EMERGENCY ? cfg.travelEmergencyOdds : cfg.transitCardOdds;
        // A rare rainbow is a certain win (off by default, as in JJS); the pity jackpot plays in rainbow too.
        boolean rainbow = pity || rng.nextFloat() < cfg.rainbowChance;
        signal = rainbow ? Signal.RAINBOW : scenario == Scenario.TRAVEL_EMERGENCY ? Signal.GOLD : Signal.GREEN;
        HakariState hs = HakariState.of(domain.owner);
        chance = Math.min(1f, base + hs.oddsBonus + (forced && !pity ? cfg.finalAttemptBonus : 0));
        jackpot = rainbow || rng.nextFloat() < chance;
        if (rainbow) chance = 1f;
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
        hs.fastRiichi = n % 2 == 0;
        sync();
        HakariCharacter.jackpot(domain.owner, n, pity);
        DomainManager.cancel(domain, DomainInstance.EndReason.CANCELLED);
    }

    /** Called when the domain ends without a jackpot. */
    void ended() {
        if (jackpot) return;
        // A miss still spends the domain; part of the meter comes back, and any bonus it carried was used up.
        HakariState hs = HakariState.of(domain.owner);
        hs.oddsBonus = 0;
        hs.fastRiichi = false;
        // No jackpot: the chain of consecutive jackpots is broken.
        hs.jackpotChain = 0;
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
            case RIICHI -> riichiTicks();
            case MISS -> cfg().missTicks;
            default -> 0;
        };
        // The third reel's true number isn't sent until it stops.
        boolean revealed = state != State.RIICHI || stateAge >= riichiTicks() - REVEAL_OFFSET;
        String bonus = HakariState.of(domain.owner).bonusText();
        if (state == State.RIICHI) bonus = pity ? "PITY JACKPOT" : forced ? "FINAL ATTEMPT" : bonus;
        return new GamblePayload(domain.id, domain.owner.getId(), st, progress, required(), attempt, cfg().maxAttempts, scenario.ordinal(), signal.ordinal(),
                reels[0], reels[1], revealed ? reels[2] : 0, stateAge, dur, forOwner ? chanceShown() : -1f, forOwner ? bonus : "");
    }

    /** The odds the gambler sees: the next Riichi's rough odds while spinning, this one's once drawn. */
    private float chanceShown() {
        if (state == State.RIICHI || state == State.JACKPOT) return chance;
        JJKConfig.Hakari cfg = cfg();
        float avg = cfg.transitCardOdds * (1 - cfg.travelEmergencyChance) + cfg.travelEmergencyOdds * cfg.travelEmergencyChance;
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
