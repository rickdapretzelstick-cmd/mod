package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Unlimited Purple (JJS variant). The orb Lapse Blue MAX leaves lingering after a kill, shot with Reversal Red MAX (the
 * blast or its rebound), turns into a purple nuke: three seconds later it erases everything in its radius, 50 to 100
 * damage depending on how close to the center. It drains Gojo's entire Awakening.
 *
 * <p>One radius ({@code unlimitedPurpleRadius}, 48: three times the original 16) drives all of it: the drawn dome and
 * detonation, the damage sphere and its falloff, the knockback, and the crater (the same 0.8 of the radius as before).
 * The crater is carved from the centre outward, one ring a tick within the shared per-tick block budget and the blast's
 * own block cap, so a blast this big never stalls the server; whatever the budget leaves untouched past the cap stays.
 */
public final class UnlimitedPurple {
    private static final List<UnlimitedPurple> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    private final LivingEntity owner;
    private final Vec3 center;
    private int age;
    /** After the detonation: the crater ring being carved (blocks from the centre), what is left of it, and the tally. */
    private int ring = -1;
    private final java.util.ArrayDeque<BlockPos> pending = new java.util.ArrayDeque<>();
    private int carved;

    private UnlimitedPurple(ServerLevel level, LivingEntity owner, Vec3 center) {
        this.level = level;
        this.owner = owner;
        this.center = center;
    }

    public static void start(ServerLevel level, LivingEntity owner, BlueEntity blue) {
        Vec3 at = blue.position();
        blue.consume();
        ACTIVE.add(new UnlimitedPurple(level, owner, at));
        JJKConfig.Gojo cfg = JJKConfig.get().gojo;
        // The client plays the whole fuse from this: the collision, the mass, its lightning, the dome, the dark shell.
        Fx.play(level, "unlimited_purple", at, new Vec3(cfg.unlimitedPurpleFuse, 0, 0), (float) cfg.unlimitedPurpleRadius, owner.getId());
        Fx.play(level, "sfx:unlimited_purple_start", at, Vec3.ZERO, 6f, owner.getId());
        Fx.shake(level, at, 60, 0.6f, 20);
        var caster = Casters.getOrNull(owner);
        if (caster != null && !caster.noCost()) caster.setAwakening(0);
    }

    public static void tick(ServerLevel level) {
        for (UnlimitedPurple p : List.copyOf(ACTIVE)) {
            if (p.level != level) continue;
            if (p.tick()) ACTIVE.remove(p);
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    /** True when done. */
    private boolean tick() {
        JJKConfig.Gojo cfg = JJKConfig.get().gojo;
        age++;
        if (age < cfg.unlimitedPurpleFuse) return false;
        if (age > cfg.unlimitedPurpleFuse) return carve(cfg);
        double r = cfg.unlimitedPurpleRadius;
        Fx.play(level, "unlimited_purple_end", center, Vec3.ZERO, (float) r, owner.getId());
        Fx.play(level, "sfx:unlimited_purple_explode", center, Vec3.ZERO, 8f, owner.getId());
        Fx.shake(level, center, 96, 1.6f, 30);
        for (LivingEntity t : HitboxQuery.targets(owner, HitShape.sphere(center, r), 0, false)) {
            double d = t.getBoundingBox().getCenter().distanceTo(center);
            float damage = Mth.lerp((float) Mth.clamp(d / r, 0, 1), cfg.unlimitedPurpleMaxDamage, cfg.unlimitedPurpleMinDamage);
            Hit hit = Hit.builder(owner, "unlimited_purple").type(ModDamageTypes.HOLLOW_PURPLE).damage(damage)
                    .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.BYPASS_INFINITY, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION,
                            AttackTag.AREA, AttackTag.OTG, AttackTag.ULTIMATE)
                    .origin(center).knockback(Knockback.radial(center, 2.5, 0.8)).hitstun(30).status(CombatStatus.LAUNCHED, 30)
                    .noComboScaling().fx("purple_hit", 2f).build();
            HitResolver.resolve(hit, t);
        }
        if (!Destruction.allowed(level)) return true;
        ring = 0;
        return carve(cfg);
    }

    /** The crater, nearest rings first, as far as this tick's budget goes. True when it is finished (or capped). */
    private boolean carve(JJKConfig.Gojo cfg) {
        double outer = cfg.unlimitedPurpleRadius * 0.8;
        int limit = cfg.unlimitedPurpleMaxBlocks;
        int scans = 0;
        while (carved < limit) {
            if (pending.isEmpty()) {
                if (ring > outer) return true;
                // At most two rings looked over a tick (the outer ones are large), the rest next tick.
                if (scans++ >= 2) return false;
                collectRing(ring++, outer);
                continue;
            }
            if (Destruction.budgetExhausted(level)) return false;
            BlockPos p = pending.poll();
            if (level.isLoaded(p) && !level.getBlockState(p).isAir() && Destruction.destroy(level, p, 50f, owner, "jjk:unlimited_purple")) carved++;
        }
        return true;
    }

    /** Every solid block whose distance from the centre falls in [k, k+1) (and within the crater). */
    private void collectRing(int k, double outer) {
        int r = k + 1;
        BlockPos c = BlockPos.containing(center);
        double lo = k * (double) k, hi = Math.min(k + 1.0, outer) * Math.min(k + 1.0, outer);
        if (hi <= lo) return;
        for (int y = r; y >= -r; y--) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    double dx = x + 0.5 + c.getX() - center.x, dy = y + 0.5 + c.getY() - center.y, dz = z + 0.5 + c.getZ() - center.z;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 < lo || d2 >= hi) continue;
                    BlockPos p = new BlockPos(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (level.isLoaded(p) && !level.getBlockState(p).isAir()) pending.add(p);
                }
            }
        }
    }
}
