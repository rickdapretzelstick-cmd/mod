package dev.rick.jjk.core.defense;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * An attack about to land, described generically so defenses can decide how to react.
 * Comes either from this mod's hit system ({@link #hit}) or from vanilla damage ({@link #vanillaSource}).
 */
public final class IncomingAttack {
    public final LivingEntity target;
    @Nullable public final Entity attacker;
    @Nullable public final Entity direct;
    public final Vec3 origin;
    public final Set<AttackTag> tags;
    public final float damage;
    @Nullable public final Hit hit;
    @Nullable public final DamageSource vanillaSource;

    public IncomingAttack(LivingEntity target, @Nullable Entity attacker, @Nullable Entity direct, Vec3 origin, Set<AttackTag> tags,
                          float damage, @Nullable Hit hit, @Nullable DamageSource vanillaSource) {
        this.target = target;
        this.attacker = attacker;
        this.direct = direct;
        this.origin = origin;
        this.tags = tags;
        this.damage = damage;
        this.hit = hit;
        this.vanillaSource = vanillaSource;
    }

    public static IncomingAttack of(Hit hit, LivingEntity target, float damage) {
        Vec3 origin = hit.direct != null ? hit.direct.position() : hit.origin;
        return new IncomingAttack(target, hit.attacker, hit.direct, origin, hit.tags, damage, hit, null);
    }

    public boolean has(AttackTag tag) {
        return tags.contains(tag);
    }

    /** Horizontal direction the attack is coming from, as seen from the target. */
    public Vec3 directionFromTarget() {
        Vec3 d = origin.subtract(target.position());
        return new Vec3(d.x, 0, d.z);
    }
}
