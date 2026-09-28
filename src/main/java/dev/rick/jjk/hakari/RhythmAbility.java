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
 * Jackpot special — Rhythm. Hakari dances to the beat of his Jackpot. A short bar of beats plays; press the Special key
 * on each one. Every beat you land stretches the Jackpot and patches him up; land them all at GREAT or better and he
 * rides a Lucky Streak (every Jackpot move hits harder for a while). Timing is judged with the same windows as a domain
 * clash ({@link ClashSession#rate}) against the client's own sub-tick clock, but it is its own mechanic.
 * Players press on their client ({@code RhythmInputPayload}); non-players (bots, tests) are judged as perfect.
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
            JJKConfig.Hakari cfg = JJKConfig.get().hakari;
            float secs = switch (j) {
                case PERFECT -> cfg.rhythmPerfectSeconds;
                case GREAT -> cfg.rhythmGreatSeconds;
                case GOOD -> cfg.rhythmGoodSeconds;
                default -> 0;
            };
            if (j == ClashJudgement.PERFECT || j == ClashJudgement.GREAT) great++;
            if (secs > 0) {
                // Stretch the Jackpot (the meter is its timer) and patch him up.
                float perSecond = caster.character() != null ? caster.character().awakeningDrainPerSecond() : 1f;
                caster.setAwakening(caster.awakening() + secs * perSecond);
                user.heal(secs * 2f);
            }
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
                if (great >= results.length) {
                    Statuses.apply(user, CombatStatus.LUCKY_STREAK, cfg.rhythmStreakTicks);
                    Fx.play(level, "rhythm_streak", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                }
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
