package dev.rick.jjk.yuji;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.gojo.GojoCombat;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Vessel's shared combat rules, on top of the common strike helpers ({@link HakariCombat}). */
public final class YujiCombat {
    /** Props Combat Instincts can punch across the battlefield (bins, barrels, crates...). */
    public static final TagKey<Block> THROWABLES = TagKey.create(Registries.BLOCK, JJK.id("throwables"));

    private YujiCombat() {}

    public static JJKConfig.Yuji cfg() {
        return JJKConfig.get().yuji;
    }

    public static boolean finishable(LivingEntity target) {
        return target.isAlive() && target.getHealth() <= target.getMaxHealth() * cfg().finisherThreshold;
    }

    public static HitResult execute(LivingEntity user, LivingEntity target, String id, String fx) {
        return HakariCombat.execute(user, target, id, fx);
    }

    /** Ragdolled (launched, spiked, down): moves that "cannot bypass ragdoll" pass through them. */
    public static boolean ragdolled(LivingEntity e) {
        return GojoCombat.ragdolled(e);
    }

    /** In the middle of a move or a dash (Divergent Fist's interruption variants). Blocking doesn't count. */
    public static boolean acting(LivingEntity e) {
        return GojoCombat.acting(e);
    }

    /** The user is at the target's back (the Black Flash chain). */
    public static boolean behind(LivingEntity user, LivingEntity target) {
        Vec3 to = target.position().subtract(user.position());
        to = new Vec3(to.x, 0, to.z);
        if (to.lengthSqr() < 1e-4) return false;
        return HakariCombat.flat(target).dot(to.normalize()) > 0.3;
    }

    /** A Vessel strike: melee by default; {@code unblockable} skips guard. */
    public static Hit.Builder strike(LivingEntity user, String id, float damage, boolean unblockable) {
        Hit.Builder b = Hit.builder(user, id).type(ModDamageTypes.MELEE).damage(damage).tag(AttackTag.MELEE).origin(user.getEyePosition());
        if (unblockable) b.tag(AttackTag.UNBLOCKABLE);
        return b;
    }

    /** Sukuna's technique: a slash of Shrine (not melee: it reaches). */
    public static Hit.Builder slash(LivingEntity user, String id, float damage) {
        return Hit.builder(user, id).type(ModDamageTypes.TECHNIQUE).damage(damage).tag(AttackTag.TECHNIQUE).origin(user.getEyePosition());
    }

    /** Combat Instincts takes 3% of the Awakening meter when there is any; it never needs it. */
    public static void feintCost(AbilityCaster caster) {
        if (caster.noCost() || caster.isAwakened()) return;
        float cost = caster.maxAwakening() * cfg().instinctsMeterCost / 100f;
        if (caster.awakening() > 0) caster.setAwakening(Math.max(0, caster.awakening() - cost));
    }

    /** The throwable prop nearest in front of the user, or null. */
    @Nullable
    public static BlockPos throwable(LivingEntity user) {
        double r = cfg().throwableRange;
        BlockPos base = user.blockPosition();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        int ir = (int) Math.ceil(r);
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-ir, -1, -ir), base.offset(ir, 2, ir))) {
            if (!user.level().getBlockState(p).is(THROWABLES)) continue;
            double d = Vec3.atCenterOf(p).distanceTo(user.position().add(0, 0.8, 0));
            if (d > r || d >= bd) continue;
            Vec3 to = Vec3.atCenterOf(p).subtract(user.position());
            if (new Vec3(to.x, 0, to.z).normalize().dot(HakariCombat.flat(user)) < -0.2) continue;
            bd = d;
            best = p.immutable();
        }
        return best;
    }

    /** Shrine's basic slashes cut through walls: whatever the swing reaches is carved out. */
    public static void shrineSwing(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level) || !Destruction.allowed(level)) return;
        double range = JJKConfig.get().melee.lightRange * cfg().shrineRangeMultiplier;
        Vec3 eye = user.getEyePosition(), end = eye.add(user.getLookAngle().scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return;
        Vec3 side = new Vec3(-HakariCombat.flat(user).z, 0, HakariCombat.flat(user).x);
        for (int i = -1; i <= 1; i++) {
            BlockPos p = BlockPos.containing(hit.getLocation().add(user.getLookAngle().scale(0.2)).add(side.scale(i * 0.9)));
            Destruction.destroy(level, p, 3f, user, "jjk:shrine");
        }
    }
}
