package dev.rick.jjk.core.defense;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.registry.ModDamageTypes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Routes vanilla damage (mob attacks, arrows, explosions, other mods) through the same defense layers as this mod's
 * own hits, so Infinity and guarding behave identically against a zombie and against a sorcerer.
 */
public final class VanillaDamageBridge {
    private static final ThreadLocal<Boolean> REAPPLYING = ThreadLocal.withInitial(() -> false);

    private VanillaDamageBridge() {}

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(VanillaDamageBridge::allow);
    }

    private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
        if (REAPPLYING.get() || ModDamageTypes.isOurs(source) || !Defenses.isProtected(entity)) return true;
        IncomingAttack attack = map(entity, source, amount);
        DefenseResult r = Defenses.resolve(attack);
        switch (r.kind()) {
            case NEGATE, PARRY -> {
                return false;
            }
            case BLOCK, REDUCE -> {
                if (r.damageScale() >= 1f) return true;
                if (r.damageScale() > 0 && entity.level() instanceof ServerLevel level) {
                    REAPPLYING.set(true);
                    try {
                        entity.hurtServer(level, source, amount * r.damageScale());
                    } finally {
                        REAPPLYING.set(false);
                    }
                }
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    public static IncomingAttack map(LivingEntity target, DamageSource source, float amount) {
        Set<AttackTag> tags = new HashSet<>();
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_PROJECTILE)) tags.add(AttackTag.PROJECTILE);
        else if (source.is(DamageTypeTags.IS_EXPLOSION)) tags.add(AttackTag.EXPLOSION);
        else if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || attacker == null && direct == null) {
            tags.add(AttackTag.ENVIRONMENTAL);
        } else if (direct != null && direct == attacker) {
            tags.add(AttackTag.MELEE);
        } else {
            // Magic from an entity (evoker fangs, sonic boom...) behaves like a technique.
            tags.add(AttackTag.TECHNIQUE);
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) tags.add(AttackTag.UNBLOCKABLE);
        Vec3 origin = source.getSourcePosition() != null ? source.getSourcePosition()
                : direct != null ? direct.position() : target.position();
        return new IncomingAttack(target, attacker, direct, origin, tags, amount, null, source);
    }
}
