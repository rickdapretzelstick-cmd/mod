package dev.rick.jjk.progression.tool;

import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.grade.GradedCurse;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slaughter Demon, the fast cursed blade. Its tree develops speed and precision:
 * <ul>
 *   <li>{@code flurry}: every third hit on the same foe in quick succession lands an extra cut and staggers them;</li>
 *   <li>{@code precision}: a falling (critical) hit cuts deeper;</li>
 *   <li>use dashes a few blocks forward through the first foe in line, cutting them (Quickstep);</li>
 *   <li>{@code severing_point}: a curse on its last quarter of health (Grade 3 or weaker) is exorcised outright by a hit.</li>
 * </ul>
 */
public final class SlaughterDemonBehavior implements ToolBehavior {
    private final CursedToolDefinition def;
    private record Chain(UUID target, int count, long at) {}
    private final Map<UUID, Chain> chains = new HashMap<>();

    SlaughterDemonBehavior(CursedToolDefinition def) {
        this.def = def;
    }

    @Override
    public void onHit(ServerLevel level, ServerPlayer player, ItemStack stack, LivingEntity target) {
        long now = level.getGameTime();
        Chain c = chains.get(player.getUUID());
        int count = c != null && c.target.equals(target.getUUID()) && now - c.at <= 25 ? c.count + 1 : 1;
        chains.put(player.getUUID(), new Chain(target.getUUID(), count, now));
        if (chains.size() > 256) chains.clear();
        float base = def.damage() + 1f;
        float bonus = 0f;
        if (count % 3 == 0 && Mastery.unlocked(player, def.unlockKey("flurry"))) {
            bonus += base * 0.6f * (float) Mastery.param(player, def.paramKey("flurry"));
            Statuses.apply(target, CombatStatus.HITSTUN, 8);
            Fx.play(level, "tool_flurry", target.getBoundingBox().getCenter(), player.getLookAngle(), 1f, target.getId());
        }
        if (player.fallDistance > 0 && !player.onGround() && Mastery.unlocked(player, def.unlockKey("precision"))) {
            bonus += base * 0.5f;
            Fx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.4f);
        }
        if (target instanceof GradedCurse g && g.curseGrade().rank() <= CurseGrade.GRADE_3.rank() && target.getHealth() <= target.getMaxHealth() * 0.25f
                && Mastery.unlocked(player, def.unlockKey("severing_point"))) {
            bonus = Math.max(bonus, target.getHealth() + 1f);
            Fx.play(level, "tool_sever", target.getBoundingBox().getCenter(), player.getLookAngle(), 1.2f, target.getId());
        }
        if (bonus > 0 && target.isAlive()) {
            target.setInvulnerableTime(0);
            target.hurtServer(level, level.damageSources().playerAttack(player), bonus);
        }
    }

    @Override
    public boolean use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        // In the hand, use is Quickstep (the moveset's move 1, the same dart).
        if (player.getCooldowns().isOnCooldown(stack)) return false;
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) return false;
        flat = flat.normalize();
        double reach = 5 * Mastery.param(player, def.paramKey("sd_quickstep_reach"));
        Vec3 from = player.position().add(0, 0.6, 0);
        var hit = level.clip(new ClipContext(from, from.add(flat.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double go = hit.getType() == HitResult.Type.MISS ? reach : Math.max(0, hit.getLocation().distanceTo(from) - 0.6);
        Vec3 to = player.position().add(flat.scale(go));
        List<LivingEntity> met = HitboxQuery.targets(player, HitShape.capsule(from, from.add(flat.scale(go + 0.8)), 0.9), 0.2, true);
        player.teleportTo(level, to.x, to.y, to.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        Motion.set(player, flat.scale(0.3));
        Fx.play(level, "tool_quickstep", from, flat.scale(go), 1f, player.getId());
        Fx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        if (!met.isEmpty()) {
            LivingEntity t = met.getFirst();
            t.setInvulnerableTime(0);
            t.hurtServer(level, level.damageSources().playerAttack(player), (def.damage() + 1f) * 1.4f);
            onHit(level, player, stack, t);
        }
        player.getCooldowns().addCooldown(stack, 50);
        return true;
    }
}
