package dev.rick.jjk.registry;

import dev.rick.jjk.JJK;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Damage types are data-driven (data/jjk/damage_type); these are their keys. */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> MELEE = key("melee");
    public static final ResourceKey<DamageType> TECHNIQUE = key("technique");
    public static final ResourceKey<DamageType> BLUE = key("blue");
    public static final ResourceKey<DamageType> RED = key("red");
    public static final ResourceKey<DamageType> HOLLOW_PURPLE = key("hollow_purple");
    public static final ResourceKey<DamageType> SURE_HIT = key("sure_hit");

    private ModDamageTypes() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, JJK.id(name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key, @Nullable Entity direct, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), direct, attacker);
    }

    /** Damage produced by this mod's hit system (already went through defenses). */
    public static boolean isOurs(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> k.identifier().getNamespace().equals(JJK.MOD_ID)).orElse(false);
    }
}
