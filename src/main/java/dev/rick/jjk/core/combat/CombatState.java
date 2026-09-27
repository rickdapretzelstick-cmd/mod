package dev.rick.jjk.core.combat;

import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.UUID;

/**
 * Transient combat data attached to every living entity (see {@link CombatHolder}).
 * Exists on both sides: the server is authoritative, clients receive status changes so they can
 * predict gravity/movement locks for the local player and draw state visuals for others.
 */
public final class CombatState {
    private int[] ticks = new int[CombatStatus.count()];
    private int activeCount;
    private boolean dirty;

    // Combo tracking (this entity as the victim).
    private UUID comboAttacker;
    private int comboCount;
    private long lastHitTime = Long.MIN_VALUE / 2;
    private float comboDamage;

    // Attraction (Blue) data, refreshed every tick while pulled.
    private Vec3 pullCenter;
    private double pullStrength;
    private long pullTime;

    // Guard.
    private boolean guarding;
    private long guardStartTime;
    private int guardHitsTaken;
    private int guardRegenTimer;

    public int get(CombatStatus status) {
        ensureCapacity(status.index);
        return ticks[status.index];
    }

    public boolean has(CombatStatus status) {
        return get(status) > 0;
    }

    /** Applies a status, keeping whichever duration is longer. */
    public void apply(CombatStatus status, int duration) {
        if (duration <= 0) return;
        ensureCapacity(status.index);
        int old = ticks[status.index];
        if (duration > old) {
            if (old == 0) activeCount++;
            ticks[status.index] = duration;
            // Clients count statuses down themselves; only new statuses and real extensions need a sync.
            if (old == 0 || duration > old + 2) dirty = true;
        }
    }

    /** Sets a status to an exact duration (0 removes it). */
    public void set(CombatStatus status, int duration) {
        ensureCapacity(status.index);
        int old = ticks[status.index];
        if (old == duration) return;
        if (old == 0 && duration > 0) activeCount++;
        if (old > 0 && duration <= 0) activeCount--;
        ticks[status.index] = Math.max(0, duration);
        dirty = true;
    }

    public void remove(CombatStatus status) {
        set(status, 0);
    }

    public void clearAll() {
        Arrays.fill(ticks, 0);
        if (activeCount > 0) dirty = true;
        activeCount = 0;
        pullCenter = null;
        guarding = false;
    }

    /**
     * Counts every status down by one tick.
     * Clients also call this so they stay in step between syncs; removals from expiry don't mark dirty
     * since both sides expire on their own.
     */
    public void tick() {
        if (activeCount == 0) return;
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] > 0 && --ticks[i] == 0) activeCount--;
        }
    }

    public boolean isActive() {
        return activeCount > 0 || guarding || comboCount > 0 || pullCenter != null;
    }

    public boolean movementLocked() {
        return anyMatch(0);
    }

    public boolean actionsLocked() {
        return anyMatch(1);
    }

    public boolean techniquesLocked() {
        return anyMatch(2);
    }

    public boolean shouldInterruptCasting() {
        return anyMatch(3);
    }

    public boolean isDowned() {
        return anyMatch(4);
    }

    public boolean isMeleeImmune() {
        return anyMatch(5);
    }

    public boolean isEvading() {
        return anyMatch(6);
    }

    private boolean anyMatch(int property) {
        if (activeCount == 0) return false;
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] <= 0) continue;
            CombatStatus s = CombatStatus.byIndex(i);
            if (s == null) continue;
            boolean v = switch (property) {
                case 0 -> s.locksMovement;
                case 1 -> s.locksActions;
                case 2 -> s.locksTechniques;
                case 3 -> s.interruptsCasting;
                case 4 -> s.downed;
                case 5 -> s.meleeImmune;
                default -> s.evasive;
            };
            if (v) return true;
        }
        return false;
    }

    /** Lowest gravity scale among active statuses (1 when none apply). */
    public float gravityScale() {
        if (activeCount == 0) return 1f;
        float g = 1f;
        boolean any = false;
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] <= 0) continue;
            CombatStatus s = CombatStatus.byIndex(i);
            if (s == null || s.gravityScale == 1f) continue;
            // Spiking accelerates downward even while other statuses reduce gravity.
            if (s.gravityScale > 1f) return s.gravityScale;
            g = any ? Math.min(g, s.gravityScale) : s.gravityScale;
            any = true;
        }
        return g;
    }

    // --- Combo tracking ---

    /**
     * Registers a hit in the current combo and returns the new hit count.
     * A different attacker or a gap longer than the combo window starts a new combo.
     */
    public int registerComboHit(UUID attacker, long gameTime, int window, float damage) {
        if (comboAttacker == null || !comboAttacker.equals(attacker) || gameTime - lastHitTime > window) {
            comboCount = 0;
            comboDamage = 0;
        }
        comboAttacker = attacker;
        comboCount++;
        comboDamage += damage;
        lastHitTime = gameTime;
        return comboCount;
    }

    public int comboCount(long gameTime, int window) {
        return gameTime - lastHitTime > window ? 0 : comboCount;
    }

    /** Combo count as seen by a specific attacker, without registering a hit. */
    public int peekCombo(UUID attacker, long gameTime, int window) {
        if (comboAttacker == null || !comboAttacker.equals(attacker)) return 0;
        return comboCount(gameTime, window);
    }

    public float comboDamage() {
        return comboDamage;
    }

    public void expireCombo(long gameTime, int window) {
        if (comboCount > 0 && gameTime - lastHitTime > window) {
            comboCount = 0;
            comboDamage = 0;
            comboAttacker = null;
        }
    }

    // --- Pull ---

    public void setPull(Vec3 center, double strength, long gameTime) {
        this.pullCenter = center;
        this.pullStrength = strength;
        this.pullTime = gameTime;
    }

    public Vec3 pullCenter(long gameTime) {
        if (pullCenter != null && gameTime - pullTime > 1) pullCenter = null;
        return pullCenter;
    }

    public double pullStrength() {
        return pullStrength;
    }

    // --- Guard ---

    public boolean isGuarding() {
        return guarding;
    }

    public void startGuard(long gameTime) {
        if (!guarding) {
            guarding = true;
            guardStartTime = gameTime;
            dirty = true;
        }
    }

    public void stopGuard() {
        if (guarding) {
            guarding = false;
            dirty = true;
        }
    }

    public long guardStartTime() {
        return guardStartTime;
    }

    public int guardHitsTaken() {
        return guardHitsTaken;
    }

    public void addGuardHit() {
        guardHitsTaken++;
        guardRegenTimer = 0;
    }

    public void resetGuard() {
        guardHitsTaken = 0;
        guardRegenTimer = 0;
    }

    /** Recovers one guard hit every {@code interval} ticks while not taking guard damage. */
    public void tickGuardRegen(int interval) {
        if (guardHitsTaken > 0 && ++guardRegenTimer >= interval) {
            guardHitsTaken--;
            guardRegenTimer = 0;
        }
    }

    // --- Sync ---

    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void markDirty() {
        dirty = true;
    }

    public int[] snapshot() {
        return ticks.clone();
    }

    /** Replaces statuses from a server snapshot (client side). */
    public void load(int[] snapshot, boolean guarding) {
        ensureCapacity(snapshot.length - 1);
        Arrays.fill(ticks, 0);
        activeCount = 0;
        for (int i = 0; i < snapshot.length && i < ticks.length; i++) {
            ticks[i] = snapshot[i];
            if (ticks[i] > 0) activeCount++;
        }
        this.guarding = guarding;
    }

    private void ensureCapacity(int index) {
        if (index >= ticks.length) ticks = Arrays.copyOf(ticks, Math.max(index + 1, CombatStatus.count()));
    }
}
