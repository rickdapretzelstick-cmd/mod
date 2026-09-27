package dev.rick.jjk.core.ability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class Casters {
    private Casters() {}

    public static AbilityCaster get(LivingEntity entity) {
        return ((CasterHolder) entity).jjk$caster();
    }

    @Nullable
    public static AbilityCaster getOrNull(Entity entity) {
        return entity instanceof CasterHolder h ? h.jjk$casterOrNull() : null;
    }

    /** The caster if this entity is playing a character. */
    @Nullable
    public static AbilityCaster active(Entity entity) {
        AbilityCaster c = getOrNull(entity);
        return c != null && c.character() != null ? c : null;
    }
}
