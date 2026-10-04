package dev.rick.jjk.progression.tool;

import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * The Cursed Cleaver, the heavy cursed blade. Its tree develops weight and impact:
 * <ul>
 *   <li>{@code heavy_swing}: hold use to raise it (up to 1.5 s), release to bring it down on everything in front, harder
 *   the longer it was raised;</li>
 *   <li>{@code guard_break}: that blow staggers and breaks a guard;</li>
 *   <li>{@code shockwave}: a fully raised blow cracks the ground and throws everyone around;</li>
 *   <li>{@code momentum}: its swings throw foes back further.</li>
 * </ul>
 */
public final class CursedCleaverBehavior implements ToolBehavior {
    public static final int FULL_CHARGE = 30;
    private final CursedToolDefinition def;

    CursedCleaverBehavior(CursedToolDefinition def) {
        this.def = def;
    }

    @Override
    public int useDuration(ItemStack stack) {
        // Only held once the heavy swing is learned (the client asks too: it has no Mastery, so it always may hold).
        return 1;
    }

    @Override
    public ItemUseAnimation useAnimation(ItemStack stack) {
        return ItemUseAnimation.SPEAR;
    }

    @Override
    public boolean use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!Mastery.unlocked(player, def.unlockKey("heavy_swing")) || player.getCooldowns().isOnCooldown(stack)) {
            if (!Mastery.unlocked(player, def.unlockKey("heavy_swing"))) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("You haven't learned to raise this blade for a heavy swing yet.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            return false;
        }
        Fx.sound(level, player.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 0.6f);
        return true;
    }

    @Override
    public void heldTick(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (player.isUsingItem() && player.getUseItem() == stack) {
            int held = player.getTicksUsingItem();
            if (held == FULL_CHARGE) {
                Fx.play(level, "tool_charged", player.position().add(0, 1.6, 0), Vec3.ZERO, 1f, player.getId());
                Fx.sound(level, player.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7f, 1.4f);
            }
        }
    }

    @Override
    public void release(ServerLevel level, ServerPlayer player, ItemStack stack, int heldTicks) {
        if (!Mastery.unlocked(player, def.unlockKey("heavy_swing")) || heldTicks < 6) return;
        float charge = Math.min(1f, heldTicks / (float) FULL_CHARGE);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 at = player.position().add(flat.scale(2.0)).add(0, 0.9, 0);
        float damage = (def.damage() + 1f) * (1f + 1.5f * charge);
        player.swingAndResetAttackStrength(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        Fx.play(level, "tool_heavy", at, flat, charge, player.getId());
        Fx.sound(level, at, SoundEvents.MACE_SMASH_GROUND, 1f, 0.8f);
        boolean breaks = Mastery.unlocked(player, def.unlockKey("guard_break"));
        for (LivingEntity t : HitboxQuery.targets(player, HitShape.sphere(at, 2.2), 0.3, true)) {
            t.setInvulnerableTime(0);
            t.hurtServer(level, level.damageSources().playerAttack(player), damage);
            Motion.set(t, flat.scale(0.8 + 0.6 * charge).add(0, 0.3, 0));
            if (breaks) {
                Combat.state(t).stopGuard();
                Statuses.apply(t, CombatStatus.GUARD_BROKEN, 20 + Math.round(20 * charge));
            }
        }
        if (charge >= 1f && Mastery.unlocked(player, def.unlockKey("shockwave"))) {
            Vec3 ground = player.position().add(flat.scale(2.0));
            Fx.play(level, "tool_shockwave", ground, Vec3.ZERO, 4f, player.getId());
            Fx.shake(level, ground, 16, 0.6f, 10);
            for (LivingEntity t : HitboxQuery.targets(player, HitShape.sphere(ground, 4.0), 0.3, false)) {
                t.setInvulnerableTime(0);
                t.hurtServer(level, level.damageSources().playerAttack(player), damage * 0.6f);
                Vec3 out = t.position().subtract(ground).multiply(1, 0, 1);
                Motion.set(t, (out.lengthSqr() < 1e-4 ? flat : out.normalize()).scale(0.7).add(0, 0.55, 0));
            }
        }
        player.getCooldowns().addCooldown(stack, 30);
    }

    @Override
    public void onHit(ServerLevel level, ServerPlayer player, ItemStack stack, LivingEntity target) {
        if (Mastery.unlocked(player, def.unlockKey("momentum"))) {
            Vec3 flat = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize();
            Motion.set(target, target.getDeltaMovement().add(flat.scale(0.6)).add(0, 0.15, 0));
        }
    }
}
