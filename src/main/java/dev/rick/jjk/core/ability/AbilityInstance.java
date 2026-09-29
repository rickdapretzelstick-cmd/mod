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

    /** Advances to a new phase and informs clients (drives charge visuals). */
    protected void setPhase(int phase, int expectedDuration) {
        this.phase = phase;
        Fx.toTrackers(user, new CastPayload(user.getId(), ability.id, phase, expectedDuration), true);
    }

    final void tickInternal() {
        age++;
        tick();
    }
}
