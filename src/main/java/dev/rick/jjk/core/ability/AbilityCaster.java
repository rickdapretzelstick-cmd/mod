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
    private final int[] cooldowns = new int[SLOTS];
    private final int[] maxCooldowns = new int[SLOTS];
    private final int[] charges = new int[SLOTS];
    private final int[] lockout = new int[SLOTS];
    @Nullable private AbilityInstance cast;
    private final List<AbilityInstance> overlays = new ArrayList<>();
    private final Set<String> toggles = new HashSet<>();
    private boolean noCost;
    private boolean dirty = true;
    private float lastSyncedEnergy = -1;
    private float appliedSlow = 1f;
    /** Last refusal reason, for tests and feedback. */
    @Nullable public String lastRefusal;

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
            for (Ability a : character.abilities().values()) if (a.kind() == Ability.Kind.TOGGLE && a.isToggled(this)) a.toggleOff(this, "character_change");
            character.onRemoved(this);
        }
        character = c;
        java.util.Arrays.fill(cooldowns, 0);
        java.util.Arrays.fill(lockout, 0);
        toggles.clear();
        melee.reset();
        if (c != null) {
            energy = c.maxEnergy();
            for (AbilitySlot s : AbilitySlot.values()) {
                Ability a = c.ability(s);
                charges[s.ordinal()] = a == null ? 0 : a.maxCharges(this);
            }
            c.onAssigned(this);
        }
        dirty = true;
    }

    @Nullable
    public Ability ability(AbilitySlot slot) {
        return character == null ? null : character.ability(slot);
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
        return cooldowns[slot.ordinal()];
    }

    public int charges(AbilitySlot slot) {
        return charges[slot.ordinal()];
    }

    public void startCooldown(AbilitySlot slot, int ticks) {
        if (noCost()) ticks = Math.min(ticks, 4);
        int i = slot.ordinal();
        Ability a = ability(slot);
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
        cooldowns[slot.ordinal()] = 0;
        lockout[slot.ordinal()] = 0;
        dirty = true;
    }

    public boolean isReady(AbilitySlot slot) {
        int i = slot.ordinal();
        if (lockout[i] > 0) return false;
        Ability a = ability(slot);
        if (a != null && a.maxCharges(this) > 1) return charges[i] > 0;
        return cooldowns[i] <= 0;
    }

    public void resetCooldowns() {
        java.util.Arrays.fill(cooldowns, 0);
        java.util.Arrays.fill(lockout, 0);
        if (character != null) {
            for (AbilitySlot s : AbilitySlot.values()) {
                Ability a = character.ability(s);
                charges[s.ordinal()] = a == null ? 0 : a.maxCharges(this);
            }
        }
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
        Ability ability = ability(slot);
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
        if (state.actionsLocked()) return refuse("stunned");
        if (ability.isTechnique() && state.techniquesLocked()) return refuse("technique_locked");
        if (isBusy() && !ability.usableWhileCasting()) {
            // Pressing the same hold ability again is not an error, just ignored.
            return refuse(cast.ability == ability ? "already_casting" : "busy");
        }
        if (melee.isCommitted() && slot != AbilitySlot.GUARD) return refuse("attacking");
        if (!isReady(slot)) return refuse("cooldown");
        float cost = ability.cost(this);
        if (!canAfford(cost)) {
            Fx.play(level, "no_energy", owner.position().add(0, 1, 0), net.minecraft.world.phys.Vec3.ZERO, 1f, owner.getId());
            return refuse("energy");
        }
        AbilityContext ctx = new AbilityContext(this, owner, level, slot, forward, strafe, targetHint);
        String reason = ability.checkActivation(ctx);
        if (reason != null) return refuse(reason);

        spend(cost);
        int i = slot.ordinal();
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

    private boolean refuse(String reason) {
        lastRefusal = reason;
        return false;
    }

    /** Stops the current exclusive cast without firing it. */
    public void interrupt(String reason) {
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
            for (Ability a : character.abilities().values()) if (a.kind() == Ability.Kind.TOGGLE && a.isToggled(this)) a.toggleOff(this, reason);
        }
        applySlow(1f);
    }

    private void removeInstance(AbilityInstance inst) {
        inst.finish();
        inst.end();
        if (inst == cast) {
            cast = null;
            Fx.toTrackers(owner, new CastPayload(owner.getId(), "", 0, 0), true);
        }
        if (inst.ability.cooldownOnEnd()) {
            for (AbilitySlot s : AbilitySlot.values()) if (ability(s) == inst.ability) startCooldown(s, inst.ability.cooldown(this));
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

        for (int i = 0; i < SLOTS; i++) {
            if (lockout[i] > 0) lockout[i]--;
            if (cooldowns[i] > 0 && --cooldowns[i] == 0) {
                Ability a = ability(AbilitySlot.values()[i]);
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
        if (cast != null) {
            if (!cast.isFinished()) cast.tickInternal();
            if (cast != null && cast.isFinished()) removeInstance(cast);
        }
        for (AbilityInstance o : List.copyOf(overlays)) {
            if (!o.isFinished()) o.tickInternal();
            if (o.isFinished()) removeInstance(o);
        }

        character.tick(this);
        melee.tick(this);

        float slow = 1f;
        if (cast != null && !cast.isFinished()) slow = Math.min(slow, cast.movementMultiplier());
        for (AbilityInstance o : overlays) slow = Math.min(slow, o.movementMultiplier());
        slow = Math.min(slow, melee.movementMultiplier());
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
        return new CasterSyncPayload(character == null ? "" : character.id, energy, maxEnergy(), cooldowns.clone(), maxCooldowns.clone(),
                charges.clone(), flags, isCasting() ? cast.ability.id : "", isCasting() ? cast.age() : 0);
    }

    public boolean isPlayer() {
        return owner instanceof Player;
    }
}
