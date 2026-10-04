package dev.rick.jjk.progression.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * A cursed tool's item. Its swings are cursed-energy damage to curses ({@link dev.rick.jjk.progression.grade.CursedDamage}),
 * scaled by the wielder's Mastery of it; using it runs whatever its tree has unlocked ({@link ToolBehavior}). It never
 * breaks: cursed tools are rare and kept.
 */
public class CursedToolItem extends Item {
    private final CursedToolDefinition definition;

    public CursedToolItem(CursedToolDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    public CursedToolDefinition definition() {
        return definition;
    }

    public ToolBehavior behavior() {
        return CursedTools.behavior(definition.id());
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        if (attacker instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) behavior().onHit(sl, sp, stack, target);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (behavior().useDuration(stack) > 0) {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && !behavior().use(sl, sp, stack)) return InteractionResult.PASS;
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) return behavior().use(sl, sp, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        int d = behavior().useDuration(stack);
        return d > 0 ? 72000 : 0;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return behavior().useAnimation(stack);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (level instanceof ServerLevel sl && entity instanceof ServerPlayer sp) behavior().release(sl, sp, stack, 72000 - remaining);
        return true;
    }
}
