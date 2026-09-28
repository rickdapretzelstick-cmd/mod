package dev.rick.jjk.core.combat.melee;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Per-caster melee state: the running attack, chain position, input buffer and heavy charge. */
public final class MeleeState {
    int chainIndex;
    long lastAttackTime = Long.MIN_VALUE / 2;
    boolean heavyCharging;
    long heavyStartTime;
    @Nullable MeleeMove current;
    int currentAge;
    boolean connected;
    final Set<UUID> hitThisMove = new HashSet<>();
    int targetHint = -1;
    // Buffered light input (pressed slightly early).
    int bufferedFlags = -1;
    int bufferedHint = -1;
    int bufferAge;

    public int chainIndex() {
        return chainIndex;
    }

    /** Moves the light chain on, so the next light attack is hit {@code index + 1} of the chain (a technique that combos into melee). */
    public void setChainIndex(int index, long now) {
        chainIndex = Math.max(0, index);
        lastAttackTime = now;
    }

    public boolean isHeavyCharging() {
        return heavyCharging;
    }

    public long heavyStartTime() {
        return heavyStartTime;
    }

    @Nullable
    public MeleeMove current() {
        return current;
    }

    /** "Is the fighter currently attacking?" */
    public boolean isAttacking() {
        return heavyCharging || current != null;
    }

    /** Startup or active frames: the attack is committed and can't be cancelled. */
    public boolean isCommitted() {
        return current != null && currentAge < current.startup() + current.active();
    }

    public boolean canChain() {
        return current == null || currentAge >= current.chainAt();
    }

    public void reset() {
        chainIndex = 0;
        heavyCharging = false;
        current = null;
        bufferedFlags = -1;
        hitThisMove.clear();
    }

    /** Stops whatever is happening (hitstun, techniques cancelling recovery). */
    public void cancel() {
        heavyCharging = false;
        current = null;
        bufferedFlags = -1;
    }

    public void cancelCharge() {
        cancel();
    }

    public float movementMultiplier() {
        if (heavyCharging) return 0.4f;
        return current != null ? current.movementMultiplier() : 1f;
    }

    public void tick(AbilityCaster caster) {
        LivingEntity user = caster.owner;
        if (current != null) {
            MeleeMove move = current;
            currentAge++;
            if (currentAge == 1 && move.onStart() != null) move.onStart().accept(user);
            if (currentAge > move.startup() && currentAge <= move.startup() + move.active()) {
                MeleeSystem.activeFrame(user, this, move, currentAge == move.startup() + 1);
            }
            if (current == move && currentAge >= move.total()) current = null;
        }
        if (bufferedFlags >= 0) {
            if (++bufferAge > 8) bufferedFlags = -1;
            else if (canChain()) {
                int flags = bufferedFlags;
                bufferedFlags = -1;
                MeleeSystem.light(user, caster, flags, bufferedHint);
            }
        }
        if (heavyCharging && user.level().getGameTime() - heavyStartTime > JJKConfig.get().melee.heavyMaxCharge + 30) {
            MeleeSystem.heavyRelease(user, caster);
        }
    }
}
