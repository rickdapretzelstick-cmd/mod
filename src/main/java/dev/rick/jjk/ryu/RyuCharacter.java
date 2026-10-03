package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.common.GuardAbility;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.melee.MeleeMove;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ryu Ishigori — True Cannon, after the Jujutsu Shenanigans character: absurd cursed-energy output, fired from the cannon
 * of his pompadour. 100 HP.
 *
 * <p>Base: Cursed Energy Discharge (his M1 chain is three hits, 3 + 3 + 4, and the neutral third fires a 24-stud ray for
 * double damage while his Overheat is under 90%), Overheat (every discharge heats him; at 100% they shut off and his head
 * smokes), 1 Granite Blast (tap, hold, or out of a front dash), 2 Unsatisfied, 3 Second Helping, 4 Appetizer, Special
 * Restyle (cools him down; overheated, the comb). Ultimate: Every Last Drop. — his strongest blast, everything he has;
 * fired with his Overheat between 80% and 100% it awakens him (90 seconds, heals 25).
 *
 * <p>Awakened (Decadence): no passive healing, Overheat locked at 100%, and once he is critical his Awakening meter
 * takes the damage instead (200 worth). His first three moves can be cancelled or feinted into each other for 5 HP
 * (that can kill), the cancelled move going on a 6-second cooldown. 1 "What are you after?", 2 "I had no idea...",
 * 3 "This is what dessert is like!", 4 "You weren't invited.", Special Restyle (+10 HP, +10% meter).
 *
 * <p>His Ultimate key also answers an incoming True Love Beam with Every Last Drop: a beam clash.
 */
public final class RyuCharacter extends JJKCharacter {
    public static final String ID = "ryu";
    private final MeleeMoveset melee = new MeleeMoveset("", "", 1.0f, 1.05f, 1.0f, 1.0f, true, false, null, 3, RyuCharacter::rayFinisher);

    public RyuCharacter() {
        super(ID);
        RyuDashAbility dash = new RyuDashAbility();
        GuardAbility guard = new GuardAbility();
        RestyleAbility restyle = new RestyleAbility();
        bind(AbilitySlot.DASH, dash);
        bind(AbilitySlot.GUARD, guard);
        bind(AbilitySlot.SKILL_1, new GraniteBlastAbility());
        bind(AbilitySlot.SKILL_2, new UnsatisfiedAbility());
        bind(AbilitySlot.SKILL_3, new SecondHelpingAbility());
        bind(AbilitySlot.SKILL_4, new AppetizerAbility());
        bind(AbilitySlot.SKILL_5, restyle);
        bind(AbilitySlot.ULTIMATE, new EveryLastDropAbility());
        bindAwakened(AbilitySlot.DASH, dash);
        bindAwakened(AbilitySlot.GUARD, guard);
        bindAwakened(AbilitySlot.SKILL_1, new WhatAreYouAfterAbility());
        bindAwakened(AbilitySlot.SKILL_2, new NoIdeaAbility());
        bindAwakened(AbilitySlot.SKILL_3, new DessertAbility());
        bindAwakened(AbilitySlot.SKILL_4, new NotInvitedAbility());
        bindAwakened(AbilitySlot.SKILL_5, restyle);
    }

    @Override
    public String displayName() {
        return "Ryu";
    }

    @Override
    public String title() {
        return "True Cannon";
    }

    @Override
    public String description() {
        return "Granite Blast, Unsatisfied, Second Helping and Appetizer, fired from his pompadour until he Overheats; Restyle cools him. "
                + "Every Last Drop. is his everything: fired hot enough, it awakens him into Decadence.";
    }

    @Override
    public float maxEnergy() {
        return RyuCombat.cfg().maxCursedEnergy;
    }

    @Override
    public float regenPerSecond() {
        return RyuCombat.cfg().regenPerSecond;
    }

    @Override
    public MeleeMoveset melee() {
        return melee;
    }

    @Override
    public float awakeningDrainPerSecond() {
        return JJKConfig.get().awakening.max / Math.max(1, RyuCombat.cfg().awakeningSeconds);
    }

    /** The awakening is only his if Every Last Drop earns it: the Awakening key never transforms him on its own. */
    @Override
    public boolean awakensOnCounter() {
        return false;
    }

    /**
     * Cursed Energy Discharge: while his Overheat is under 90% (and he is not awakened), the neutral third hit is a ray
     * three times as long, for double damage, heating him 10%.
     */
    @Nullable
    private static MeleeMove rayFinisher(LivingEntity user, MeleeMove standard) {
        dev.rick.jjk.core.ability.AbilityCaster c = dev.rick.jjk.core.ability.Casters.getOrNull(user);
        if (c == null || c.isAwakened() || RyuCombat.heat(user) >= RyuCombat.cfg().m1RayHeatLimit) return null;
        JJKConfig.Ryu cfg = RyuCombat.cfg();
        return new MeleeMove("finisher_ray", "ryu_m1_ray", 4, 2, 11, 17, 0.35f,
                u -> HitShape.orientedBox(RyuCombat.cannon(u).subtract(u.getLookAngle().scale(0.6)), u.getLookAngle(), cfg.m1RayRange + 0.6, 1.1, 1.1),
                (u, t) -> RyuCombat.blast(u, "ryu_m1_ray", cfg.m1RayDamage, RyuCombat.cannon(u))
                        .knockback(Knockback.directional(u.getLookAngle(), 0.95, 0.35)).hitstun(20).status(CombatStatus.LAUNCHED, 14)
                        .fx("ryu_ray_hit", 1f).build(),
                u -> {
                    RyuCombat.addHeat(u, cfg.heatM1);
                    if (u.level() instanceof ServerLevel level) {
                        Vec3 from = RyuCombat.cannon(u);
                        Vec3 to = RyuCombat.rayEnd(u, from, u.getLookAngle(), cfg.m1RayRange);
                        Fx.play(level, "ryu_m1_ray", from, to.subtract(from), 1f, u.getId());
                    }
                },
                null);
    }

    // --- Feints (Decadence) ---

    @Override
    public boolean interceptInput(AbilityCaster caster, AbilitySlot slot, Ability ability, @Nullable Entity targetHint) {
        if (!caster.isAwakened()) {
            // The front dash's Granite Blast: the move itself checks the timing; nothing to intercept.
            return false;
        }
        AbilityInstance cast = caster.cast() != null && !caster.cast().isFinished() ? caster.cast() : null;
        if (!(cast instanceof Feintable f) || !f.feintable()) return false;
        if (!(ability instanceof WhatAreYouAfterAbility || ability instanceof NoIdeaAbility || ability instanceof DessertAbility)) return false;
        // Cancel (the same move again) or feint (another of the first three): it costs him 5 HP, which can kill.
        JJKConfig.Ryu cfg = RyuCombat.cfg();
        AbilitySlot from = null;
        for (AbilitySlot s : AbilitySlot.values()) if (caster.ability(s) == cast.ability) from = s;
        caster.interrupt("feint");
        if (from != null) caster.startCooldown(from, cfg.feintCooldown);
        LivingEntity user = caster.owner;
        if (user.level() instanceof ServerLevel level) {
            Fx.play(level, "ryu_feint", user.position().add(0, 1, 0), user.getLookAngle(), 1f, user.getId());
            user.hurtServer(level, user.damageSources().magic(), cfg.feintCost);
        }
        // Cancelled: that's it. Feinted into another: it starts right away (the normal input path).
        return ability == cast.ability;
    }

    // --- Decadence ---

    @Override
    public void tick(AbilityCaster caster) {
        LivingEntity e = caster.owner;
        RyuState s = RyuState.of(e);
        if (caster.isAwakened()) {
            // Locked at 100% Overheat.
            if (s.heat < 100f) RyuCombat.setHeat(e, 100f);
            // Starving for a satisfying battle: no passive healing (his hunger won't let food heal him).
            if (e instanceof ServerPlayer sp) {
                var food = sp.getFoodData();
                if (s.savedFood < 0) s.savedFood = food.getFoodLevel();
                if (food.getFoodLevel() > 17) food.setFoodLevel(17);
                food.setSaturation(0f);
            }
            if (s.heat >= 100f && e.tickCount % 10 == 0 && e.level() instanceof ServerLevel level) {
                Fx.play(level, "ryu_smoke", e.getEyePosition().add(0, 0.45, 0), Vec3.ZERO, 1f, e.getId());
            }
        } else {
            if (s.savedFood >= 0 && e instanceof ServerPlayer sp) {
                sp.getFoodData().setFoodLevel(Math.max(sp.getFoodData().getFoodLevel(), s.savedFood));
                s.savedFood = -1;
            }
            float cool = RyuCombat.cfg().heatCoolPerSecond;
            if (cool > 0 && e.tickCount % 20 == 0 && s.heat > 0) RyuCombat.addHeat(e, -cool);
            if (s.overheated() && e.tickCount % 12 == 0 && e.level() instanceof ServerLevel level) {
                Fx.play(level, "ryu_smoke", e.getEyePosition().add(0, 0.45, 0), Vec3.ZERO, 0.6f, e.getId());
            }
        }
        RyuCombat.sync(e);
    }

    /**
     * Decadence: once he is critical, the Awakening meter takes the blow (200 damage' worth); without enough left he still
     * survives that hit, put out of his Awakening.
     */
    @Override
    public boolean preventDeath(AbilityCaster caster, DamageSource source, float amount) {
        if (!caster.isAwakened()) return false;
        LivingEntity e = caster.owner;
        JJKConfig.Ryu cfg = RyuCombat.cfg();
        float meterPerDamage = caster.maxAwakening() / Math.max(1f, cfg.decadenceMeterHealth);
        float need = amount * meterPerDamage;
        if (caster.awakening() > need) {
            caster.setAwakening(caster.awakening() - need);
        } else {
            caster.endAwakening("decadence");
        }
        e.setHealth(Math.max(1f, Math.min(e.getMaxHealth() * 0.05f, 2f)));
        if (e.level() instanceof ServerLevel level) Fx.play(level, "ryu_decadence", e.position().add(0, 1, 0), Vec3.ZERO, 1f, e.getId());
        return true;
    }

    /** Below critical health, damage that doesn't kill is taken by the meter too: called by the hit resolution. */
    public static float absorb(AbilityCaster caster, float amount) {
        if (!caster.isAwakened() || !(caster.character() instanceof RyuCharacter)) return amount;
        LivingEntity e = caster.owner;
        JJKConfig.Ryu cfg = RyuCombat.cfg();
        if (e.getHealth() > e.getMaxHealth() * cfg.decadenceCritical) return amount;
        float meterPerDamage = caster.maxAwakening() / Math.max(1f, cfg.decadenceMeterHealth);
        float can = caster.awakening() / meterPerDamage;
        float taken = Math.min(amount, can);
        caster.setAwakening(caster.awakening() - taken * meterPerDamage);
        if (caster.awakening() <= 0.01f) caster.endAwakening("decadence");
        return amount - taken;
    }

    @Override
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {
        LivingEntity e = caster.owner;
        if (awakened) {
            RyuCombat.setHeat(e, 100f);
        } else {
            // Out of Decadence: his Overheat comes back as it was (full).
            RyuCombat.setHeat(e, 100f);
            if (e.level() instanceof ServerLevel level) Fx.play(level, "ryu_awaken_end", e.position().add(0, 1.2, 0), Vec3.ZERO, 1f, e.getId());
        }
        Combat.state(e).remove(CombatStatus.AWAKENING);
        RyuCombat.sync(e);
    }

    @Override
    public void onDeath(AbilityCaster caster) {
        RyuState.clear(caster.owner);
    }

    @Override
    public void onAssigned(AbilityCaster caster) {
        RyuState.of(caster.owner).heat = 0;
        RyuCombat.sync(caster.owner);
    }

    @Override
    public void onRemoved(AbilityCaster caster) {
        LivingEntity e = caster.owner;
        RyuState s = RyuState.of(e);
        if (s.savedFood >= 0 && e instanceof ServerPlayer sp) sp.getFoodData().setFoodLevel(Math.max(sp.getFoodData().getFoodLevel(), s.savedFood));
        RyuState.clear(e);
        if (e instanceof ServerPlayer sp) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new dev.rick.jjk.core.net.RyuPayload(-1, false));
    }

    /** Facing used by several of his moves. */
    static Vec3 flat(LivingEntity e) {
        return HakariCombat.flat(e);
    }
}
