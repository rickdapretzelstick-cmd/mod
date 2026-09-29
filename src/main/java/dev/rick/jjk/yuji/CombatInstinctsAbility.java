package dev.rick.jjk.yuji;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.ThrownPropEntity;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Combat Instincts (JJS Special, 2s). During an M1's or a skill's wind-up (not Manji Kick) it cancels the attack with no
 * endlag and keeps the move off cooldown: a bait for blocks and counters, or, with the aerial variants, extra height and
 * mobility. It takes 3% Awakening when there is any, but never needs it (the feint itself is handled by
 * {@link YujiCharacter#interceptInput}).
 *
 * <p>Pressed next to a throwable (bins, barrels, crates...): a punch that hurls it forward, unblockable, 15 damage. That
 * one needs the 3%.
 */
public final class CombatInstinctsAbility extends Ability {
    public static final String ID = "combat_instincts";

    public CombatInstinctsAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public boolean isTechnique() {
        return false;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().instinctsCooldown;
    }

    @Override
    public float awakeningCost(AbilityCaster caster) {
        return caster.maxAwakening() * YujiCombat.cfg().instinctsMeterCost / 100f;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        // Nothing to feint: only a throwable prop makes it do anything.
        return YujiCombat.throwable(ctx.user()) != null ? null : "nothing_to_feint";
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        LivingEntity user = ctx.user();
        BlockPos at = YujiCombat.throwable(user);
        if (at == null) return null;
        ServerLevel level = ctx.level();
        BlockState state = level.getBlockState(at);
        Vec3 from = Vec3.atCenterOf(at);
        // The prop is punched out of the world (restored later like any other destruction) and flies.
        if (!Destruction.destroy(level, at, 50f, user, "jjk:combat_instincts")) level.removeBlock(at, false);
        Anim.play(user, "instincts_throw");
        Vec3 dir = user.getLookAngle();
        dir = new Vec3(dir.x, Math.max(-0.2, Math.min(0.3, dir.y)), dir.z).normalize();
        ThrownPropEntity.fire(level, user, from.add(0, 0.2, 0), dir, state);
        Fx.play(level, "instincts_throw", from, dir, 1f, user.getId());
        return null;
    }

    /** The white ring of a feint. */
    static void feintFx(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) return;
        Anim.play(user, "instincts_feint");
        Fx.play(level, "instincts_feint", user.position().add(0, 1.1, 0), HakariCombat.flat(user), 1f, user.getId());
    }
}
