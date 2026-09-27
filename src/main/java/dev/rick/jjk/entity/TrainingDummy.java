package dev.rick.jjk.entity;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Practice target. Shows HP, combo count and combo damage above its head and heals to full after a few seconds
 * without being hit. Modes: STAND (still), JUMP (hops, for air combos), FIGHT (walks up and punches, for testing
 * Infinity and guarding).
 */
public class TrainingDummy extends PathfinderMob {
    public enum Mode { STAND, JUMP, FIGHT }

    private Mode mode = Mode.STAND;
    private boolean autoHeal = true;
    private int sinceHit = 1000;
    private int attackCooldown;
    private float lastComboDamage;
    private int lastCombo;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 24);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setAutoHeal(boolean autoHeal) {
        this.autoHeal = autoHeal;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16f));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) sinceHit = 0;
        return hurt;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        switch (mode) {
            case JUMP -> {
                if (onGround() && tickCount % 30 == 0) getJumpControl().jump();
            }
            case FIGHT -> {
                Player p = level.getNearestPlayer(this, 16);
                if (p != null && !p.isSpectator() && !p.isCreative()) {
                    if (distanceTo(p) > 1.8) getNavigation().moveTo(p, 1.1);
                    else getNavigation().stop();
                    getLookControl().setLookAt(p);
                    if (attackCooldown <= 0 && distanceTo(p) < 2.4) {
                        doHurtTarget(level, p);
                        attackCooldown = 20;
                    }
                }
            }
            default -> getNavigation().stop();
        }
        if (attackCooldown > 0) attackCooldown--;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        sinceHit++;
        if (autoHeal && sinceHit > 60 && getHealth() < getMaxHealth()) setHealth(getMaxHealth());
        if (tickCount % 5 == 0) updateLabel();
    }

    private void updateLabel() {
        CombatState s = Combat.stateOrNull(this);
        long now = level().getGameTime();
        int combo = s == null ? 0 : s.comboCount(now, JJKConfig.get().general.comboWindow);
        if (combo > 0) {
            lastCombo = combo;
            lastComboDamage = s.comboDamage();
        }
        Component label = Component.literal(String.format("❤ %.0f", getHealth())).withStyle(ChatFormatting.RED)
                .append(Component.literal("  combo " + lastCombo).withStyle(combo > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY))
                .append(Component.literal(String.format("  %.1f dmg", lastComboDamage)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  [" + mode.name().toLowerCase() + "]").withStyle(ChatFormatting.DARK_GRAY));
        setCustomName(label);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("DummyMode", mode.name());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            mode = Mode.valueOf(input.getStringOr("DummyMode", "STAND"));
        } catch (IllegalArgumentException e) {
            mode = Mode.STAND;
        }
    }
}
