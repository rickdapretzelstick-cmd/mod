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
 * Kinji Hakari — Restless Gambler. A brawler with gambling-themed techniques whose real power is behind his domain:
 * Idle Death Gamble runs a pachinko gamble, and hitting the Jackpot turns him into something that is almost impossible to
 * put down (full heal, heavy regeneration, a lethal blow shrugged off now and then, unlimited cursed energy) with a
 * completely different moveset, until the Jackpot timer runs out.
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

    /** The gamble hit: Jackpot begins. */
    static void jackpot(LivingEntity owner, int number) {
        AbilityCaster c = Casters.getOrNull(owner);
        if (c == null || !(owner.level() instanceof ServerLevel level)) return;
        c.enterAwakening();
        owner.setHealth(owner.getMaxHealth());
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
    public void onAbilityUsed(AbilityCaster caster, Ability ability, AbilitySlot slot) {
        // Inside his own domain, his techniques are the visual moves that drive the gamble.
        if (caster.isAwakened() || slot == AbilitySlot.ULTIMATE || slot == AbilitySlot.DASH || slot == AbilitySlot.GUARD) return;
        Gamble g = IdleDeathGamble.gambleOf(caster.owner);
        if (g != null) g.visualMove();
    }

    @Override
    public void tick(AbilityCaster caster) {
        LivingEntity e = caster.owner;
        if (!caster.isAwakened()) return;
        // Jackpot: unlimited cursed energy and a body that keeps putting itself back together.
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        if (caster.energy() < caster.maxEnergy()) caster.setEnergy(caster.maxEnergy());
        if (e.isAlive() && e.getHealth() < e.getMaxHealth()) e.heal(cfg.jackpotRegenPerSecond / 20f);
        if (Combat.state(e).get(CombatStatus.JACKPOT) < 20) Statuses.apply(e, CombatStatus.JACKPOT, 40);
        if (e.tickCount % 12 == 0 && e.level() instanceof ServerLevel level) {
            Fx.play(level, "jackpot_aura", e.position().add(0, 1, 0), Vec3.ZERO, 1f, e.getId());
        }
    }

    @Override
    public boolean preventDeath(AbilityCaster caster, DamageSource source, float amount) {
        if (!caster.isAwakened() || !(caster.owner.level() instanceof ServerLevel level)) return false;
        HakariState hs = HakariState.of(caster.owner);
        if (level.getGameTime() < hs.lethalReadyAt) return false;
        // Jackpot: the lethal blow is healed through, but not again right away.
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        hs.lethalReadyAt = level.getGameTime() + cfg.jackpotLethalCooldown;
        caster.owner.setHealth(Math.max(1f, caster.owner.getMaxHealth() * cfg.jackpotLethalHeal));
        Fx.play(level, "jackpot_heal", caster.owner.position().add(0, 1.2, 0), Vec3.ZERO, 1f, caster.owner.getId());
        return true;
    }

    @Override
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {
        if (!awakened) {
            // Jackpot over: back to the base kit.
            Statuses.remove(caster.owner, CombatStatus.JACKPOT);
            Statuses.remove(caster.owner, CombatStatus.LUCKY_STREAK);
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
