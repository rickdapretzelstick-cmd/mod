package dev.rick.jjk.core.ability;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.melee.MeleeState;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.CastPayload;
import dev.rick.jjk.core.net.CasterSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-entity ability state: character, cursed energy, cooldowns/charges, the running cast, toggles and melee state.
 * Works for any LivingEntity; players additionally get their state synced for the HUD.
 */
public final class AbilityCaster {
    private static final Identifier SLOW_ID = JJK.id("cast_slow");
    private static final int SLOTS = AbilitySlot.values().length;

    public final LivingEntity owner;
    public final MeleeState melee = new MeleeState();
    @Nullable private JJKCharacter character;
    private float energy;
    private int regenDelay;
    private static final int MODES = JJKCharacter.MODES;
    // Indexed by idx(): one block of slots per moveset (an ability bound in several movesets uses its first block).
    private final int[] cooldowns = new int[SLOTS * MODES];
    private final int[] maxCooldowns = new int[SLOTS * MODES];
    private final int[] charges = new int[SLOTS * MODES];
    private final int[] lockout = new int[SLOTS * MODES];
    // Awakening.
    private float awakening;
    private boolean awakened;
    private int refillDelay;
    @Nullable private AbilityInstance cast;
    private final List<AbilityInstance> overlays = new ArrayList<>();
    private final Set<String> toggles = new HashSet<>();
    private boolean noCost;
    private boolean dirty = true;
    private float lastSyncedEnergy = -1;
    private float appliedSlow = 1f;
    /** Last refusal reason, for tests and feedback. */
    @Nullable public String lastRefusal;

    private float castSpeedCarry;

    public AbilityCaster(LivingEntity owner) {
        this.owner = owner;
    }

    // --- Character ---

    @Nullable
    public JJKCharacter character() {
        return character;
    }

    public void setCharacter(@Nullable JJKCharacter c) {
        if (character == c) return;
        interrupt("character_change");
        for (AbilityInstance o : List.copyOf(overlays)) o.interrupt("character_change");
        if (character != null) {
            for (Ability a : character.abilitiesAllModes()) if (a.kind() == Ability.Kind.TOGGLE && a.isToggled(this)) a.toggleOff(this, "character_change");
            character.onRemoved(this);
        }
        character = c;
        java.util.Arrays.fill(cooldowns, 0);
        java.util.Arrays.fill(lockout, 0);
        toggles.clear();
        melee.reset();
        if (c != null) {
            energy = c.maxEnergy();
            awakened = false;
            awakening = 0;
            refillCharges();
            c.onAssigned(this);
        }
        dirty = true;
    }

    @Nullable
    public Ability ability(AbilitySlot slot) {
        return character == null ? null : character.ability(slot, mode());
    }

    /** The moveset in use right now (0 base, 1 awakened, others the character's own). */
    public int mode() {
        return character == null ? 0 : Math.max(0, Math.min(MODES - 1, character.mode(this)));
    }

    /** Cooldown array index for a slot in the current mode. */
    private int idx(AbilitySlot slot) {
        return idx(slot, mode());
    }

    private int idx(AbilitySlot slot, int mode) {
        if (character == null) return slot.ordinal();
        Ability a = character.ability(slot, mode);
        // An ability bound in several movesets keeps one cooldown: the first moveset's.
        for (int m = 0; m < mode; m++) if (a != null && character.ability(slot, m) == a) return m * SLOTS + slot.ordinal();
        return mode * SLOTS + slot.ordinal();
    }

    @Nullable
    private Ability abilityAt(int index) {
        if (character == null) return null;
        return character.ability(AbilitySlot.values()[index % SLOTS], index / SLOTS);
    }

    /** The cooldown left on a slot of any moveset. */
    public int cooldown(AbilitySlot slot, int mode) {
        return cooldowns[idx(slot, mode)];
    }

    /** Starts (or extends) the cooldown of a slot in any moveset. */
    public void startCooldown(AbilitySlot slot, int mode, int ticks) {
        startCooldownAt(idx(slot, mode), character == null ? null : character.ability(slot, mode), ticks);
    }

    /** Shortens a moveset's running cooldowns by {@code ticks} (never finishing them outright). */
    public void reduceCooldowns(int mode, int ticks) {
        for (AbilitySlot s : AbilitySlot.values()) {
            int i = idx(s, mode);
            if (cooldowns[i] > 0) cooldowns[i] = Math.max(1, cooldowns[i] - ticks);
        }
        dirty = true;
    }

    private void refillCharges() {
        for (int i = 0; i < SLOTS * MODES; i++) {
            Ability a = abilityAt(i);
            charges[i] = a == null ? 0 : a.maxCharges(this);
        }
    }

    // --- Awakening ---

    public float awakening() {
        return awakening;
    }

    public float maxAwakening() {
        return JJKConfig.get().awakening.max;
    }

    public boolean isAwakened() {
        return awakened;
    }

    /** Builds the meter through combat (not while awakened or shortly after it ended). */
    public void gainAwakening(float amount) {
        if (character == null || awakened || refillDelay > 0 || amount <= 0) return;
        float before = awakening;
        awakening = Math.min(maxAwakening(), awakening + amount);
        if ((int) before != (int) awakening || awakening >= maxAwakening()) dirty = true;
    }

    public void setAwakening(float value) {
        awakening = Math.max(0, Math.min(maxAwakening(), value));
        dirty = true;
    }

    public boolean canAffordAwakening(float cost) {
        return noCost() || awakening >= cost;
    }

    /** Switches to the awakened moveset. The meter becomes the timer. */
    public void enterAwakening() {
        if (character == null || awakened) return;
        awakened = true;
        awakening = maxAwakening();
        character.onAwakeningChanged(this, true);
        dirty = true;
    }

    /** Back to the base moveset. */
    public void endAwakening(String reason) {
        if (!awakened) return;
        awakened = false;
        awakening = 0;
        refillDelay = JJKConfig.get().awakening.refillDelay;
        if (character != null) character.onAwakeningChanged(this, false);
        dirty = true;
    }

    // --- Energy ---

    public float energy() {
        return energy;
    }

    public float maxEnergy() {
        return character == null ? 0 : character.maxEnergy();
    }

    public boolean noCost() {
        return noCost || JJKConfig.get().resources.creativeNoCost;
    }

    public void setNoCost(boolean v) {
        noCost = v;
        dirty = true;
    }

    public boolean canAfford(float amount) {
        return noCost() || energy >= amount;
    }

    /** Spends energy; returns false (spending nothing) if there isn't enough. */
    public boolean spend(float amount) {
        if (amount <= 0 || noCost()) return true;
        if (energy < amount) return false;
        energy -= amount;
        regenDelay = JJKConfig.get().resources.regenDelay;
        return true;
    }

    /** Drains up to {@code amount}; returns false if the pool ran dry. */
    public boolean drain(float amount) {
        if (noCost()) return true;
        energy = Math.max(0, energy - amount);
        regenDelay = Math.max(regenDelay, 5);
        return energy > 0;
    }

    public void setEnergy(float e) {
        energy = Math.max(0, Math.min(maxEnergy(), e));
        dirty = true;
    }

    // --- Cooldowns ---

    public int cooldown(AbilitySlot slot) {
        return cooldowns[idx(slot)];
    }

    public int charges(AbilitySlot slot) {
        return charges[idx(slot)];
    }

    /** Starts the cooldown of whichever slot (the current moveset first, then the others) holds this ability. */
    public void startCooldown(Ability ability, int ticks) {
        if (character == null) return;
        int cur = mode();
        for (int k = 0; k < MODES; k++) {
            int m = k == 0 ? cur : (k <= cur ? k - 1 : k);
            for (AbilitySlot s : AbilitySlot.values()) {
                if (character.ability(s, m) == ability) {
                    startCooldownAt(idx(s, m), ability, ticks);
                    return;
                }
            }
        }
    }

    public void startCooldown(AbilitySlot slot, int ticks) {
        startCooldownAt(idx(slot), ability(slot), ticks);
    }

    private void startCooldownAt(int i, @Nullable Ability a, int ticks) {
        if (noCost()) ticks = Math.min(ticks, 4);
        if (a != null && a.maxCharges(this) > 1) {
            if (cooldowns[i] <= 0) {
                cooldowns[i] = ticks;
                maxCooldowns[i] = ticks;
            }
        } else {
            cooldowns[i] = Math.max(cooldowns[i], ticks);
            maxCooldowns[i] = Math.max(1, cooldowns[i]);
        }
        dirty = true;
    }

    public void resetSlot(AbilitySlot slot) {
        resetSlot(slot, mode());
    }

    /** Clears the cooldown of a slot in any moveset. */
    public void resetSlot(AbilitySlot slot, int mode) {
        int i = idx(slot, mode);
        cooldowns[i] = 0;
        lockout[i] = 0;
        dirty = true;
    }

    public boolean isReady(AbilitySlot slot) {
        int i = idx(slot);
        if (lockout[i] > 0) return false;
        Ability a = ability(slot);
        if (a != null && a.maxCharges(this) > 1) return charges[i] > 0;
        return cooldowns[i] <= 0;
    }

    /** Every running cooldown finishes {@code ticks} sooner. */
    public void reduceCooldowns(int ticks) {
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) cooldowns[i] = Math.max(1, cooldowns[i] - ticks);
        }
        dirty = true;
    }

    public void resetCooldowns() {
        java.util.Arrays.fill(cooldowns, 0);
        java.util.Arrays.fill(lockout, 0);
        refillCharges();
        melee.reset();
        dirty = true;
    }

    // --- Toggles ---

    public boolean toggled(String id) {
        return toggles.contains(id);
    }

    public void setToggle(String id, boolean on) {
        if (on ? toggles.add(id) : toggles.remove(id)) dirty = true;
    }

    // --- Casting ---

    @Nullable
    public AbilityInstance cast() {
        return cast;
    }

    public boolean isCasting() {
        return cast != null && !cast.isFinished();
    }

    public boolean isCasting(String abilityId) {
        return isCasting() && cast.ability.id.equals(abilityId);
    }

    /** Busy with an exclusive cast (melee and other casts are blocked). */
    public boolean isBusy() {
        return isCasting() && cast.exclusive();
    }

    public List<AbilityInstance> overlays() {
        return overlays;
    }

    /** Result of an input; also stored in {@link #lastRefusal} when refused. */
    public boolean input(AbilitySlot slot, boolean pressed, float forward, float strafe, @Nullable Entity targetHint) {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        // The Ultimate key answers an incoming ultimate beam first (a beam clash), whatever it is bound to.
        if (pressed && slot == AbilitySlot.ULTIMATE && owner.isAlive() && dev.rick.jjk.core.clash.BeamClashManager.tryCounter(this)) {
            lastRefusal = null;
            dirty = true;
            return true;
        }
        Ability ability = ability(slot);
        // The Awakening key is always the domain counter, even when the current kit has nothing bound to it.
        if (ability == null && pressed && slot == AbilitySlot.ULTIMATE && dev.rick.jjk.core.domain.DomainCounter.tryCounter(this)) return true;
        if (ability == null) return refuse("no_ability");

        if (!pressed) {
            if (cast != null && cast.ability == ability && !cast.isFinished()) cast.release();
            for (AbilityInstance o : overlays) if (o.ability == ability && !o.isFinished()) o.release();
            return true;
        }

        if (!owner.isAlive() || owner.isSpectator()) return refuse("dead");
        CombatState state = Combat.state(owner);

        if (ability.kind() == Ability.Kind.TOGGLE && ability.isToggled(this)) {
            ability.toggleOff(this, "manual");
            return true;
        }
        if (state.actionsLocked() && cast instanceof LockedInputCast locked && !cast.isFinished() && locked.pressWhileLocked(slot)) {
            lastRefusal = null;
            dirty = true;
            return true;
        }
        // JJS: the dash key breaks out of a ragdoll (launched, spiked or knocked down) on its own cooldown.
        if (slot == AbilitySlot.DASH && state.actionsLocked()) {
            if (dev.rick.jjk.core.ability.common.DashAbility.ragdollEscape(this, state, forward, strafe)) {
                lastRefusal = null;
                dirty = true;
                return true;
            }
        }
        if (state.actionsLocked()) return refuse("stunned");
        if (ability.isTechnique() && state.techniquesLocked()) return refuse("technique_locked");
        // The character can take the press itself (a combination during another move's wind-up, a follow-up).
        if (character.interceptInput(this, slot, ability, targetHint)) {
            lastRefusal = null;
            dirty = true;
            return true;
        }
        if (isBusy() && !ability.usableWhileCasting()) {
            // Pressing the same hold ability again is not an error, just ignored.
            return refuse(cast.ability == ability ? "already_casting" : "busy");
        }
        if (melee.isCommitted() && slot != AbilitySlot.GUARD && !ability.usableDuringMelee()) return refuse("attacking");
        if (!isReady(slot)) return refuse("cooldown");
        float meterCost = ability.awakeningCost(this);
        if (meterCost > 0 && !canAffordAwakening(meterCost)) {
            Fx.play(level, "no_energy", owner.position().add(0, 1, 0), net.minecraft.world.phys.Vec3.ZERO, 1f, owner.getId());
            return refuse("awakening");
        }
        float cost = ability.cost(this);
        if (!canAfford(cost)) {
            Fx.play(level, "no_energy", owner.position().add(0, 1, 0), net.minecraft.world.phys.Vec3.ZERO, 1f, owner.getId());
            return refuse("energy");
        }
        AbilityContext ctx = new AbilityContext(this, owner, level, slot, forward, strafe, targetHint);
        String reason = ability.checkActivation(ctx);
        if (reason != null) return refuse(reason);

        spend(cost);
        if (meterCost > 0 && !noCost()) {
            awakening = Math.max(0, awakening - meterCost);
        }
        int i = idx(slot);
        if (ability.maxCharges(this) > 1) {
            charges[i]--;
            lockout[i] = noCost() ? 0 : ability.minInterval(this);
            startCooldown(slot, ability.cooldown(this));
        } else if (!ability.cooldownOnEnd()) {
            startCooldown(slot, ability.cooldown(this));
        }
        dirty = true;

        // Techniques and movement cancel melee recovery frames.
        melee.cancel();
        AbilityInstance inst = ability.activate(ctx);
        lastRefusal = null;
        character.onAbilityUsed(this, ability, slot);
        if (inst == null) return true;
        if (ability.usableWhileCasting()) {
            overlays.add(inst);
        } else {
            // A non-exclusive cast still running (e.g. steering Blue) keeps going in the background.
            if (cast != null && !cast.isFinished()) overlays.add(cast);
            cast = inst;
        }
        inst.start();
        if (inst.isFinished()) removeInstance(inst);
        return true;
    }

    /**
     * Starts a cast directly, replacing the current one (a "use again" follow-up a character starts itself: Resolute
     * Black Flash, Fakeout, Jacob's Ladder...). The replaced cast ends without firing.
     */
    public void begin(AbilityInstance inst) {
        if (cast != null && !cast.isFinished()) {
            cast.finish();
            removeInstance(cast);
        }
        melee.cancel();
        cast = inst;
        dirty = true;
        inst.start();
        if (inst.isFinished()) removeInstance(inst);
    }

    /** Runs an instance alongside whatever is going on (a lingering effect a move leaves behind: a swarm, a mark). */
    public void addOverlay(AbilityInstance inst) {
        overlays.add(inst);
        inst.start();
        if (inst.isFinished()) removeInstance(inst);
    }

    private boolean refuse(String reason) {
        lastRefusal = reason;
        return false;
    }

    /** Stops the current exclusive cast without firing it. */
    public void interrupt(String reason) {
        boolean stun = !reason.equals("death") && !reason.equals("disconnect") && !reason.equals("feint") && !reason.equals("removed");
        if (stun && cast != null && !cast.isFinished() && cast.uninterruptible()) return;
        if (cast != null && !cast.isFinished()) {
            cast.interrupt(reason);
            cast.finish();
        }
        melee.cancelCharge();
    }

    /** Interrupt everything, including overlays and toggles (death, disconnect, dimension change). */
    public void shutdown(String reason) {
        interrupt(reason);
        if (cast != null) removeInstance(cast);
        for (AbilityInstance o : List.copyOf(overlays)) {
            o.interrupt(reason);
            removeInstance(o);
        }
        if (character != null) {
            for (Ability a : character.abilitiesAllModes()) if (a.kind() == Ability.Kind.TOGGLE && a.isToggled(this)) a.toggleOff(this, reason);
        }
        applySlow(1f);
    }

    private void removeInstance(AbilityInstance inst) {
        inst.finish();
        inst.end();
        if (inst.exclusive()) dev.rick.jjk.core.anim.Anim.releaseHeld(owner);
        if (inst == cast) {
            cast = null;
            Fx.toTrackers(owner, new CastPayload(owner.getId(), "", 0, 0), true);
        }
        if (inst.ability.cooldownOnEnd()) {
            startCooldown(inst.ability, inst.ability.cooldown(this));
        }
        overlays.remove(inst);
        dirty = true;
    }

    // --- Tick ---

    public void tick() {
        if (character == null) return;
        JJKConfig cfg = JJKConfig.get();

        if (regenDelay > 0) regenDelay--;
        else if (energy < maxEnergy()) {
            energy = Math.min(maxEnergy(), energy + character.regenPerSecond() / 20f);
        }

        if (refillDelay > 0 && --refillDelay == 0) dirty = true;
        if (awakened) {
            // Awakening is a timer: it drains, and when it's empty Gojo returns to his base kit.
            if (!noCost()) awakening -= character.awakeningDrainPerSecond() / 20f;
            if (awakening <= 0 && !isCasting()) endAwakening("expired");
            else if (owner.tickCount % 5 == 0) dirty = true;
        }

        for (int i = 0; i < SLOTS * MODES; i++) {
            if (lockout[i] > 0) lockout[i]--;
            if (cooldowns[i] > 0 && --cooldowns[i] == 0) {
                Ability a = abilityAt(i);
                if (a != null && a.maxCharges(this) > 1 && charges[i] < a.maxCharges(this)) {
                    charges[i]++;
                    if (charges[i] < a.maxCharges(this)) {
                        cooldowns[i] = a.cooldown(this);
                        maxCooldowns[i] = cooldowns[i];
                    }
                }
                dirty = true;
            }
        }

        CombatState state = Combat.state(owner);
        if (cast != null && !cast.isFinished() && state.shouldInterruptCasting()) interrupt("stunned");
        // Faster casting (a character's speed buff) runs the casts extra ticks now and then.
        float speed = Math.max(1f, character.castSpeed(this));
        castSpeedCarry += speed - 1f;
        int steps = 1;
        while (castSpeedCarry >= 1f) {
            castSpeedCarry -= 1f;
            steps++;
        }
        for (int step = 0; step < steps; step++) {
            if (cast != null) {
                if (!cast.isFinished()) cast.tickInternal();
                if (cast != null && cast.isFinished()) removeInstance(cast);
            }
            for (AbilityInstance o : List.copyOf(overlays)) {
                if (!o.isFinished()) o.tickInternal();
                if (o.isFinished()) removeInstance(o);
            }
        }

        character.tick(this);
        melee.tick(this);

        float slow = 1f;
        if (cast != null && !cast.isFinished()) slow = Math.min(slow, cast.movementMultiplier());
        for (AbilityInstance o : overlays) slow = Math.min(slow, o.movementMultiplier());
        slow = Math.min(slow, melee.movementMultiplier());
        slow = Math.min(slow, character.movementMultiplier(this));
        applySlow(slow);

        if (owner instanceof ServerPlayer sp) sync(sp, cfg);
    }

    private void applySlow(float multiplier) {
        if (Math.abs(multiplier - appliedSlow) < 1e-3) return;
        appliedSlow = multiplier;
        AttributeInstance attr = owner.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(SLOW_ID);
        if (multiplier < 1f) {
            attr.addTransientModifier(new AttributeModifier(SLOW_ID, multiplier - 1f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public void markDirty() {
        dirty = true;
    }

    private void sync(ServerPlayer player, JJKConfig cfg) {
        boolean energyChanged = Math.abs(energy - lastSyncedEnergy) >= 1f || (energy >= maxEnergy() && lastSyncedEnergy < maxEnergy());
        if (!dirty && !(energyChanged && player.tickCount % 3 == 0)) return;
        ServerPlayNetworking.send(player, buildSync());
        dirty = false;
        lastSyncedEnergy = energy;
    }

    public CasterSyncPayload buildSync() {
        int flags = 0;
        if (toggled("infinity")) flags |= CasterSyncPayload.FLAG_INFINITY;
        if (noCost()) flags |= CasterSyncPayload.FLAG_NO_COST;
        if (Combat.isGuarding(owner)) flags |= CasterSyncPayload.FLAG_GUARDING;
        if (awakened) flags |= CasterSyncPayload.FLAG_AWAKENED;
        if (refillDelay > 0) flags |= CasterSyncPayload.FLAG_REFILL_LOCKED;
        int[] cd = new int[SLOTS], maxCd = new int[SLOTS], ch = new int[SLOTS];
        StringBuilder ids = new StringBuilder();
        for (AbilitySlot s : AbilitySlot.values()) {
            int i = idx(s);
            cd[s.ordinal()] = cooldowns[i];
            maxCd[s.ordinal()] = maxCooldowns[i];
            ch[s.ordinal()] = charges[i];
            Ability a = ability(s);
            if (s.ordinal() > 0) ids.append(',');
            if (a != null) ids.append(a.id);
        }
        return new CasterSyncPayload(character == null ? "" : character.id, energy, maxEnergy(), cd, maxCd, ch, flags,
                isCasting() ? cast.ability.id : "", isCasting() ? cast.age() : 0, awakening, maxAwakening(), ids.toString());
    }

    public boolean isPlayer() {
        return owner instanceof Player;
    }
}
