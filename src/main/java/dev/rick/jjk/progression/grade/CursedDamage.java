package dev.rick.jjk.progression.grade;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.TechniqueEntity;
import dev.rick.jjk.progression.CursedSpirit;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.tool.CursedToolItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of harm a damage source is, as far as curses are concerned. Cursed spirits are made of cursed energy: an
 * ordinary blade, arrow, fall or fire passes through them. Only cursed energy hurts them, carried by a cursed tool, a
 * technique, or a sorcerer's reinforced body. One rule, in one place, for every curse.
 *
 * <ul>
 *   <li>{@link Kind#MUNDANE}: vanilla weapons, arrows, fire, falls, lava, explosions, mobs. Curses take none of it.</li>
 *   <li>{@link Kind#CURSED_TOOL}: a swing or special attack of a {@link CursedToolItem} by a player (scaled by that tool's
 *   Mastery).</li>
 *   <li>{@link Kind#TECHNIQUE}: anything this mod's techniques do (every {@code jjk:} damage type, its technique
 *   entities), and a sorcerer's own melee.</li>
 *   <li>{@link Kind#CURSED_ENERGY}: cursed energy with no sorcerer behind it (another curse, a cursed object).</li>
 *   <li>{@link Kind#ABSOLUTE}: {@code /kill} and the void: always lands.</li>
 * </ul>
 */
public final class CursedDamage {
    public enum Kind { MUNDANE, CURSED_TOOL, TECHNIQUE, CURSED_ENERGY, ABSOLUTE }

    private CursedDamage() {}

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof CursedSpirit) || !(entity.level() instanceof ServerLevel level)) return true;
            if (classify(source) != Kind.MUNDANE) return true;
            // It passes straight through: a flicker of the curse and a hollow sound, now and then.
            if (entity.tickCount % 5 == 0 || source.getEntity() instanceof ServerPlayer) {
                Fx.play(level, "curse_unharmed", entity.getBoundingBox().getCenter(), Vec3.ZERO, entity.getBbWidth(), entity.getId());
                if (entity.tickCount % 10 == 0) Fx.sound(level, entity.position(), SoundEvents.SOUL_ESCAPE.value(), 0.5f, 0.6f);
            }
            return false;
        });
    }

    public static Kind classify(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return Kind.ABSOLUTE;
        Entity direct = source.getDirectEntity(), cause = source.getEntity();
        // A cursed tool in the hand that struck (a direct swing), or a projectile thrown by one.
        ItemStack weapon = source.getWeaponItem();
        if (cause instanceof ServerPlayer && weapon != null && weapon.getItem() instanceof CursedToolItem && (direct == cause || direct instanceof Projectile)) {
            return Kind.CURSED_TOOL;
        }
        // A move of an equipped cursed tool's moveset (the Cursed Item slot), or its basic attacks while it is in use.
        if (cause instanceof ServerPlayer sp && dev.rick.jjk.registry.ModDamageTypes.isOurs(source)
                && dev.rick.jjk.progression.tool.kit.CursedKits.credited(sp) != null) {
            return Kind.CURSED_TOOL;
        }
        if (source.typeHolder().unwrapKey().map(k -> k.identifier().getNamespace().equals(JJK.MOD_ID)).orElse(false) || direct instanceof TechniqueEntity) {
            return cause instanceof LivingEntity le && Casters.active(le) != null ? Kind.TECHNIQUE : Kind.CURSED_ENERGY;
        }
        return Kind.MUNDANE;
    }

    /** The cursed tool behind a {@link Kind#CURSED_TOOL} hit (in the hand that struck, or the moveset that did), or null. */
    @Nullable
    public static dev.rick.jjk.progression.tool.CursedToolDefinition tool(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        if (weapon != null && weapon.getItem() instanceof CursedToolItem t && (source.getDirectEntity() == source.getEntity() || source.getDirectEntity() instanceof Projectile)
                && !dev.rick.jjk.registry.ModDamageTypes.isOurs(source)) {
            return t.definition();
        }
        if (source.getEntity() instanceof ServerPlayer sp && dev.rick.jjk.registry.ModDamageTypes.isOurs(source)) {
            var kit = dev.rick.jjk.progression.tool.kit.CursedKits.credited(sp);
            if (kit != null) return kit;
        }
        return weapon != null && weapon.getItem() instanceof CursedToolItem t ? t.definition() : null;
    }

    /**
     * The damage a curse actually takes: cursed-tool hits scaled by the attacker's Mastery of that tool, and the curse's
     * grade shrugging off part of anything that isn't a technique.
     */
    public static float modify(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof GradedCurse g)) return amount;
        Kind k = classify(source);
        if (k == Kind.CURSED_TOOL && source.getEntity() instanceof ServerPlayer p) {
            var tool = tool(source);
            if (tool != null) amount *= (float) Mastery.param(p, tool.paramKey("damage"));
        }
        if (k == Kind.CURSED_TOOL || k == Kind.CURSED_ENERGY) amount *= 1f - g.curseGrade().resistance;
        return amount;
    }
}
