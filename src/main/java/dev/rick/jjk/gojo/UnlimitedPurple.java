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
 */
public final class UnlimitedPurple {
    private static final List<UnlimitedPurple> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    private final LivingEntity owner;
    private final Vec3 center;
    private int age;

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
        if (Destruction.allowed(level)) Destruction.sphere(level, center, r * 0.8, 50f, cfg.unlimitedPurpleMaxBlocks, owner, null, "jjk:unlimited_purple");
        return true;
    }
}
