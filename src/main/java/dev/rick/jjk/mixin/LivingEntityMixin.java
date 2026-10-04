package dev.rick.jjk.mixin;

import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.CasterHolder;
import dev.rick.jjk.core.combat.CombatHolder;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatTicker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements CombatHolder, CasterHolder {
    /** Falls from height a move created don't hurt: only the drop below the launch point counts. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "causeFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double jjk$launchFall(double fallDistance) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return fallDistance;
        return dev.rick.jjk.core.combat.LaunchHeight.adjust(self, fallDistance);
    }

    /** Curses take cursed-tool hits scaled by the wielder's Mastery, and shrug off part of untechnical cursed energy. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float jjk$cursedDamage(float damage, net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
        return dev.rick.jjk.progression.grade.CursedDamage.modify((LivingEntity) (Object) this, source, damage);
    }

    @Unique private CombatState jjk$combat;
    @Unique private AbilityCaster jjk$caster;

    @Override
    public CombatState jjk$combat() {
        if (jjk$combat == null) jjk$combat = new CombatState();
        return jjk$combat;
    }

    @Override
    public CombatState jjk$combatOrNull() {
        return jjk$combat;
    }

    @Override
    public AbilityCaster jjk$caster() {
        if (jjk$caster == null) jjk$caster = new AbilityCaster((LivingEntity) (Object) this);
        return jjk$caster;
    }

    @Override
    public AbilityCaster jjk$casterOrNull() {
        return jjk$caster;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void jjk$tick(CallbackInfo ci) {
        if (jjk$combat != null || jjk$caster != null) CombatTicker.tick((LivingEntity) (Object) this, jjk$combat, jjk$caster);
    }

    /** Statuses that lift or pin an entity scale its gravity (hang time for combos, weightless while pulled). */
    @Inject(method = "getDefaultGravity", at = @At("RETURN"), cancellable = true)
    private void jjk$gravity(CallbackInfoReturnable<Double> cir) {
        if (jjk$combat != null) {
            float scale = jjk$combat.gravityScale();
            if (scale != 1f) cir.setReturnValue(cir.getReturnValue() * scale);
        }
    }

    /** Movement-locking statuses stop walking and jumping (mobs and players alike, both sides). */
    @Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
    private void jjk$immobile(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && jjk$combat != null && jjk$combat.movementLocked()) cir.setReturnValue(true);
    }
}
