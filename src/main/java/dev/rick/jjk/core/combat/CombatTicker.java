package dev.rick.jjk.core.combat;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.StatusPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Per-entity combat upkeep, run at the end of every LivingEntity tick (only for entities that have combat data). */
public final class CombatTicker {
    private CombatTicker() {}

    public static void tick(LivingEntity e, @Nullable CombatState state, @Nullable AbilityCaster caster) {
        if (state != null) {
            state.tick();
            if (e.level() instanceof ServerLevel level) serverTick(e, level, state);
        }
        if (caster != null && caster.character() != null && !e.level().isClientSide() && e.isAlive()) caster.tick();
    }

    private static void serverTick(LivingEntity e, ServerLevel level, CombatState state) {
        JJKConfig cfg = JJKConfig.get();
        if (!e.isAlive()) {
            if (state.isActive()) state.clearAll();
        } else {
            // Spiked into the ground: the landing turns into a knockdown with an impact.
            if (state.has(CombatStatus.SPIKED) && (e.onGround() || e.isInWater())) {
                state.remove(CombatStatus.SPIKED);
                Statuses.apply(e, CombatStatus.KNOCKDOWN, cfg.melee.knockdownTicks);
                Fx.play(level, "ground_impact", e.position(), new Vec3(0, 1, 0), 1.2f, e.getId());
                Fx.shake(level, e.position(), 14, 0.7f, 10);
            }
            // Getting up grants a moment of melee immunity so knockdowns can't be looped forever.
            if (state.get(CombatStatus.KNOCKDOWN) == 1) Statuses.apply(e, CombatStatus.WAKEUP, cfg.general.wakeupInvulnerability);
            if (e.onGround() && e.getDeltaMovement().y <= 0.01) {
                if (state.has(CombatStatus.LAUNCHED)) state.remove(CombatStatus.LAUNCHED);
                if (state.has(CombatStatus.HOVER)) state.remove(CombatStatus.HOVER);
            }
            if (!state.isGuarding()) state.tickGuardRegen(cfg.guard.guardRegenInterval);
            state.expireCombo(level.getGameTime(), cfg.general.comboWindow);
        }
        if (state.consumeDirty()) {
            Fx.toTrackers(e, new StatusPayload(e.getId(), state.snapshot(), state.isGuarding()), true);
        }
    }
}
