package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.clash.ClashJudgement;
import dev.rick.jjk.core.domain.clash.ClashSession;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jackpot special — Rhythm. Hakari dances to the music of his Jackpot. Finish the dance without being interrupted and
 * he gains a stack of speed (his moves and special play out faster, stacking, for the rest of his life) and every move
 * on cooldown finishes 0.6 seconds sooner. Interrupted, he gets nothing. A short bar of beats plays while he dances;
 * pressing the Special key on them is judged (same windows as a domain clash, {@link ClashSession#rate}) for the feel of
 * it, but the reward is the finished dance. Players press on their client ({@code RhythmInputPayload}); non-players are
 * judged as perfect.
 */
public final class RhythmAbility extends Ability {
    public static final String ID = "rhythm";

    public RhythmAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.rhythmCooldown;
    }

    /** Time (ticks since Rhythm started) of beat {@code i}. */
    public static float beatTime(int i) {
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        return cfg.rhythmLeadIn + i * cfg.rhythmBeatTicks;
    }

    /** A beat press from a player's client. */
    public static void input(ServerPlayer player, float time) {
        AbilityCaster c = Casters.getOrNull(player);
        if (c == null) return;
        if (c.cast() instanceof Session s && !s.isFinished()) s.press(time);
        for (AbilityInstance o : c.overlays()) if (o instanceof Session s && !s.isFinished()) s.press(time);
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Session(this, ctx);
    }

    static final class Session extends AbilityInstance {
        private final ClashJudgement[] results;
        private int judged;
        private int great;

        Session(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            results = new ClashJudgement[JJKConfig.get().hakari.rhythmBeats];
        }

        @Override
        public void start() {
            Anim.play(user, "rhythm_dance");
            JJKConfig.Hakari cfg = JJKConfig.get().hakari;
            setPhase(0, cfg.rhythmLeadIn + cfg.rhythmBeats * cfg.rhythmBeatTicks + 6);
            Fx.play(level, "rhythm_start", user.position().add(0, 1, 0), Vec3.ZERO, cfg.rhythmBeatTicks, user.getId());
        }

        void press(float time) {
            // Judge against the nearest beat that hasn't been judged yet.
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < results.length; i++) {
                if (results[i] != null) continue;
                float d = Math.abs(time - beatTime(i));
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best < 0) return;
            ClashJudgement j = ClashSession.rate((time - beatTime(best)) * 50.0);
            if (j == ClashJudgement.MISS && bestD > 4) return; // a stray press far from any beat: ignore
            judge(best, j);
        }

        private void judge(int beat, ClashJudgement j) {
            results[beat] = j;
            judged++;
            if (j == ClashJudgement.PERFECT || j == ClashJudgement.GREAT) great++;
            Fx.play(level, "rhythm_beat", user.position().add(0, 2.2, 0), Vec3.ZERO, j.ordinal(), user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Hakari cfg = JJKConfig.get().hakari;
            // Bots dance perfectly; players' beats that went unpressed are misses once their window has passed.
            for (int i = 0; i < results.length; i++) {
                if (results[i] != null) continue;
                if (!(user instanceof ServerPlayer)) {
                    if (age >= beatTime(i)) judge(i, ClashJudgement.PERFECT);
                } else if (age > beatTime(i) + JJKConfig.get().clash.goodWindowMs / 50f + 1) {
                    judge(i, ClashJudgement.MISS);
                }
            }
            if (judged >= results.length || age > cfg.rhythmLeadIn + cfg.rhythmBeats * cfg.rhythmBeatTicks + 8) {
                // The dance is finished: a stack of speed, and every cooldown comes back sooner.
                HakariState hs = HakariState.of(user);
                hs.rhythmStacks = Math.min(cfg.rhythmMaxStacks, hs.rhythmStacks + 1);
                caster.reduceCooldowns(cfg.rhythmCooldownCut);
                Statuses.apply(user, CombatStatus.LUCKY_STREAK, 60);
                Fx.play(level, "rhythm_streak", user.position().add(0, 1.2, 0), Vec3.ZERO, hs.rhythmStacks, user.getId());
                finish();
            }
        }

        @Override
        public boolean exclusive() {
            return false;
        }

        @Override
        public float movementMultiplier() {
            return 0.7f;
        }
    }
}
