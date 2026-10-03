package dev.rick.jjk.core.ability;

import dev.rick.jjk.core.net.CastPayload;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** One running use of an ability: holds phase/timing state and reacts to input, ticks and interruption. */
public abstract class AbilityInstance {
    public final Ability ability;
    public final AbilityCaster caster;
    public final LivingEntity user;
    public final ServerLevel level;
    protected int age;
    protected boolean held = true;
    private boolean finished;
    private int phase;
    /** When the current phase began and how long it said it would take (0: it never said): for {@link #stuck}. */
    private int phaseStart, phaseExpected;

    /** How far past its declared phase length (at least) a cast may run before it counts as stuck. */
    public static final int STUCK_SLACK = 200;
    /** A cast that never declares a phase length counts as stuck past this age. */
    public static final int STUCK_CEILING = 2400;

    protected AbilityInstance(Ability ability, AbilityContext ctx) {
        this.ability = ability;
        this.caster = ctx.caster();
        this.user = ctx.user();
        this.level = ctx.level();
    }

    /** Called once right after activation. */
    public void start() {}

    /** Called every server tick while running. */
    public abstract void tick();

    /** HOLD abilities: key released. */
    public void release() {
        held = false;
    }

    /** Cast was interrupted (hitstun, death, disconnect...). Clean up without firing. */
    public void interrupt(String reason) {
        finish();
    }

    /** Called once when the instance is removed, however it ended. */
    public void end() {}

    /** Whether this cast prevents other (non-overlay) abilities and melee. */
    public boolean exclusive() {
        return true;
    }

    /** JJS "uninterruptible": stuns don't stop it (the hit still lands; only death or a disconnect ends it). */
    public boolean uninterruptible() {
        return false;
    }

    /** Multiplier on the caster's movement speed while this runs (1 = unaffected). */
    public float movementMultiplier() {
        return 1f;
    }

    public final void finish() {
        finished = true;
    }

    public final boolean isFinished() {
        return finished;
    }

    public final int age() {
        return age;
    }

    public final int phase() {
        return phase;
    }

    /**
     * A long action that may legitimately run past every usual limit (the caster's watchdog leaves it alone). Clashes
     * and the caster's own open domain are already exempt; a move that lasts as long as something else does says so.
     */
    public boolean longAction() {
        return false;
    }

    /**
     * Whether this cast has run far past what it said it would take: a missed end condition (an end event that never
     * came) rather than anything still playing out. A held charge being held, or a long action, never is.
     */
    public final boolean stuck() {
        if (finished || longAction()) return false;
        if (ability.kind() == Ability.Kind.HOLD && held) return false;
        if (phaseExpected > 0) return age - phaseStart > phaseExpected + Math.max(STUCK_SLACK, phaseExpected);
        return age - phaseStart > STUCK_CEILING;
    }

    /** Restarts the stuck clock (the cast was legitimately held up: a clash, a domain). */
    final void keepAlive() {
        phaseStart = age;
    }

    /** Advances to a new phase and informs clients (drives charge visuals). */
    protected void setPhase(int phase, int expectedDuration) {
        this.phase = phase;
        this.phaseStart = age;
        this.phaseExpected = Math.max(0, expectedDuration);
        Fx.toTrackers(user, new CastPayload(user.getId(), ability.id, phase, expectedDuration), true);
    }

    final void tickInternal() {
        age++;
        tick();
    }
}
