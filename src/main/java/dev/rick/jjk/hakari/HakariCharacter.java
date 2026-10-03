package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.ability.common.DashAbility;
import dev.rick.jjk.core.ability.common.GuardAbility;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Kinji Hakari — Restless Gambler, after the Jujutsu Shenanigans character. A brawler with gambling-themed techniques
 * whose real power is behind his domain: Idle Death Gamble runs a pachinko gamble, and hitting the Jackpot gives him
 * infinite cursed energy, which his body turns into a reflexive Reverse Cursed Technique — effectively immortal, with
 * a completely different moveset — for 100 seconds (50 after a pity jackpot). Damage he takes drains the Jackpot meter
 * instead of killing him; surviving to the end of a Jackpot refunds Awakening, more for each Jackpot in a row.
 *
 * <p>Built entirely on the shared framework: the base kit is the normal moveset, the Jackpot kit is the awakened
 * moveset, the Jackpot timer is the Awakening meter (drained at Jackpot's own rate), and the domain is an ordinary
 * {@link dev.rick.jjk.core.domain.DomainDefinition}.
 */
public final class HakariCharacter extends JJKCharacter {
    public static final String ID = "hakari";
    private final MeleeMoveset melee = new MeleeMoveset("", "", 1.1f, 1.0f, 1.0f);

    public HakariCharacter() {
        super(ID);
        DashAbility dash = new DashAbility();
        GuardAbility guard = new GuardAbility();
        // Base kit: 1-4, the Door Guard special, and the domain on the Ultimate key.
        bind(AbilitySlot.DASH, dash);
        bind(AbilitySlot.GUARD, guard);
        bind(AbilitySlot.SKILL_1, new ReserveBallsAbility());
        bind(AbilitySlot.SKILL_2, new ShutterDoorsAbility());
        bind(AbilitySlot.SKILL_3, new RoughEnergyAbility());
        bind(AbilitySlot.SKILL_4, new FeverBreakerAbility());
        bind(AbilitySlot.SKILL_5, new DoorGuardAbility());
        bind(AbilitySlot.ULTIMATE, new IdleDeathGambleAbility());
        // Jackpot kit.
        bindAwakened(AbilitySlot.DASH, dash);
        bindAwakened(AbilitySlot.GUARD, guard);
        bindAwakened(AbilitySlot.SKILL_1, new LuckyVolleyAbility());
        bindAwakened(AbilitySlot.SKILL_2, new LuckyRushdownAbility());
        bindAwakened(AbilitySlot.SKILL_3, new OverwhelmingLuckAbility());
        bindAwakened(AbilitySlot.SKILL_4, new EnergySurgeAbility());
        bindAwakened(AbilitySlot.SKILL_5, new RhythmAbility());
    }

    @Override
    public String displayName() {
        return "Hakari";
    }

    @Override
    public String title() {
        return "Restless Gambler";
    }

    @Override
    public String description() {
        return "Brawls with pachinko balls, shutter doors and rough cursed energy. Idle Death Gamble spins for a Jackpot that makes him nearly unkillable.";
    }

    @Override
    public float maxEnergy() {
        return JJKConfig.get().hakari.maxCursedEnergy;
    }

    @Override
    public float regenPerSecond() {
        return JJKConfig.get().hakari.regenPerSecond;
    }

    @Override
    public MeleeMoveset melee() {
        return melee;
    }

    @Override
    public float awakeningDrainPerSecond() {
        // The meter is the Jackpot timer: full to empty over the Jackpot's length.
        return JJKConfig.get().awakening.max / Math.max(1f, JJKConfig.get().hakari.jackpotSeconds);
    }

    @Override
    public boolean awakensOnCounter() {
        // His domain is how he gets to Jackpot: he answers with it directly.
        return false;
    }

    /** The gamble hit: Jackpot begins (half as long after a pity jackpot). */
    static void jackpot(LivingEntity owner, int number, boolean pity) {
        AbilityCaster c = Casters.getOrNull(owner);
        if (c == null || !(owner.level() instanceof ServerLevel level)) return;
        HakariState hs = HakariState.of(owner);
        hs.jackpotChain++;
        c.enterAwakening();
        if (pity) c.setAwakening(c.maxAwakening() * 0.5f);
        hs.lastHealth = owner.getHealth();
        c.setEnergy(c.maxEnergy());
        Statuses.remove(owner, CombatStatus.BURNOUT);
        Statuses.remove(owner, CombatStatus.GAMBLING);
        Statuses.apply(owner, CombatStatus.JACKPOT, 40);
        Anim.play(owner, "jackpot");
        Fx.play(level, "jackpot", owner.position().add(0, 1.2, 0), Vec3.ZERO, number, owner.getId());
        Fx.shake(level, owner.position(), 48, 1.2f, 24);
        Fx.flash(level, owner.position(), 40, 0x90FFFFFF, 10);
    }

    @Override
    public boolean interceptInput(AbilityCaster caster, AbilitySlot slot, Ability ability, net.minecraft.world.entity.Entity targetHint) {
        if (caster.isAwakened()) return false;
        // Renewal: Reserve Balls again inside the domain, within 8s of a ball landing, rewinds to that moment.
        if (ability instanceof ReserveBallsAbility && ReserveBallsAbility.renew(caster.owner)) return true;
        // Shutter Doors during Reserve Balls' or Fever Breaker's wind-up combines with it; both go on cooldown.
        if (ability instanceof ShutterDoorsAbility && caster.cast() instanceof DoorCombo combo && !caster.cast().isFinished() && combo.acceptsDoors()) {
            if (!caster.isReady(slot)) return false;
            float cost = ability.cost(caster);
            if (!caster.canAfford(cost)) return false;
            caster.spend(cost);
            caster.startCooldown(slot, ability.cooldown(caster));
            combo.addDoors();
            return true;
        }
        return false;
    }

    @Override
    public float castSpeed(AbilityCaster caster) {
        // Rhythm: each finished dance makes his moves and special faster.
        return 1f + HakariState.of(caster.owner).rhythmStacks * JJKConfig.get().hakari.rhythmSpeedPerStack;
    }

    /** Damage taken during Jackpot drains its meter: it empties after a few times his max health. */
    private static void drainJackpot(AbilityCaster caster, float damage) {
        if (damage <= 0 || caster.noCost()) return;
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        float perHealth = caster.maxAwakening() / Math.max(1f, caster.owner.getMaxHealth() * cfg.jackpotHitDrainHealths);
        caster.setAwakening(Math.max(0, caster.awakening() - damage * perHealth));
    }

    @Override
    public void tick(AbilityCaster caster) {
        LivingEntity e = caster.owner;
        HakariState hs = HakariState.of(e);
        if (!caster.isAwakened()) {
            hs.lastHealth = e.getHealth();
            return;
        }
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        if (e.getHealth() < hs.lastHealth) drainJackpot(caster, hs.lastHealth - e.getHealth());
        // Jackpot: infinite cursed energy, and the Reverse Cursed Technique running on its own.
        if (caster.energy() < caster.maxEnergy()) caster.setEnergy(caster.maxEnergy());
        if (e.isAlive() && e.getHealth() < e.getMaxHealth()) e.heal(e.getMaxHealth() * cfg.jackpotRegenShare / 20f);
        hs.lastHealth = e.getHealth();
        if (Combat.state(e).get(CombatStatus.JACKPOT) < 20) Statuses.apply(e, CombatStatus.JACKPOT, 40);
        if (e.tickCount % 12 == 0 && e.level() instanceof ServerLevel level) {
            Fx.play(level, "jackpot_aura", e.position().add(0, 1, 0), Vec3.ZERO, 1f, e.getId());
        }
    }

    /**
     * Jackpot: essentially immortal. Every hit he survives is healed at once, back to full (and drains the Jackpot meter
     * as damage always has); the only thing that kills him is a single blow big enough to kill him from full health.
     */
    public static void init() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            AbilityCaster c = dev.rick.jjk.core.ability.Casters.getOrNull(entity);
            if (c == null || !(c.character() instanceof HakariCharacter) || !c.isAwakened() || !entity.isAlive() || taken <= 0) return;
            drainJackpot(c, taken);
            entity.setHealth(entity.getMaxHealth());
            HakariState.of(entity).lastHealth = entity.getHealth();
            if (entity.level() instanceof ServerLevel level) {
                Fx.play(level, "jackpot_heal", entity.position().add(0, 1.2, 0), Vec3.ZERO, 1f, entity.getId());
            }
        });
    }

    @Override
    public boolean preventDeath(AbilityCaster caster, DamageSource source, float amount) {
        if (!caster.isAwakened() || !(caster.owner.level() instanceof ServerLevel level)) return false;
        // One blow that would kill him from full health is the only death in Jackpot; anything less is healed away.
        if (amount >= caster.owner.getMaxHealth()) return false;
        drainJackpot(caster, amount);
        caster.owner.setHealth(caster.owner.getMaxHealth());
        HakariState.of(caster.owner).lastHealth = caster.owner.getHealth();
        Fx.play(level, "jackpot_heal", caster.owner.position().add(0, 1.2, 0), Vec3.ZERO, 1f, caster.owner.getId());
        return true;
    }

    @Override
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {
        if (!awakened) {
            // Jackpot over: back to the base kit. Surviving it refunds Awakening, more for each Jackpot in a row.
            Statuses.remove(caster.owner, CombatStatus.JACKPOT);
            Statuses.remove(caster.owner, CombatStatus.LUCKY_STREAK);
            if (caster.owner.isAlive()) {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                int chain = Math.max(1, HakariState.of(caster.owner).jackpotChain);
                caster.setAwakening(caster.maxAwakening() * Math.min(1f, cfg.jackpotRefund + cfg.jackpotRefundChain * (chain - 1)));
            }
            if (caster.owner.level() instanceof ServerLevel level) {
                Fx.play(level, "jackpot_end", caster.owner.position().add(0, 1.2, 0), Vec3.ZERO, 1f, caster.owner.getId());
            }
        }
    }

    @Override
    public void onDeath(AbilityCaster caster) {
        // The jackpot bonus is lost with the life that earned it.
        HakariState.clear(caster.owner);
    }

    @Override
    public void onRemoved(AbilityCaster caster) {
        Statuses.remove(caster.owner, CombatStatus.JACKPOT);
        Statuses.remove(caster.owner, CombatStatus.LUCKY_STREAK);
        Statuses.remove(caster.owner, CombatStatus.GAMBLING);
    }

    @Override
    public String switchBlocked(AbilityCaster caster) {
        return IdleDeathGamble.gambleOf(caster.owner) != null ? "You can't switch mid-gamble." : null;
    }
}
