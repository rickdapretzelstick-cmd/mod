package dev.rick.jjk.progression;

import dev.rick.jjk.JJK;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Whether a player can perceive curses: the server's answer, per player. Curses that need perception
 * ({@link #requiresPerception}) are drawn only for players who have it, and can't start a fight with someone who
 * doesn't ({@link CurseAggro}).
 *
 * <p>Perception comes from {@link Source}s. Today the only one is equipment: anything worn that is in the
 * {@code jjk:grants_curse_perception} item tag (Cursed Glasses). Techniques, status effects or progression register
 * their own source later; nothing checks for a particular item.
 */
public final class CursePerception {
    /** Items that let their wearer perceive curses. */
    public static final TagKey<Item> PERCEPTION_GEAR = TagKey.create(Registries.ITEM, JJK.id("grants_curse_perception"));
    /** Entity types that can only be seen (and only start fights) with perception. */
    public static final TagKey<EntityType<?>> REQUIRES_PERCEPTION = TagKey.create(Registries.ENTITY_TYPE, JJK.id("requires_curse_perception"));

    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** One reason a player might perceive curses. */
    @FunctionalInterface
    public interface Source {
        boolean grants(Player player);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    static {
        register(CursePerception::wearsPerceptionGear);
    }

    private CursePerception() {}

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** Server-side truth (the client is only told the result). */
    public static boolean canPerceive(Player player) {
        if (player.isSpectator()) return true;
        for (Source s : SOURCES) if (s.grants(player)) return true;
        return false;
    }

    public static boolean wearsPerceptionGear(Player player) {
        for (EquipmentSlot slot : WORN) if (player.getItemBySlot(slot).is(PERCEPTION_GEAR)) return true;
        return false;
    }

    /** Whether this entity is a curse that only the perceptive can see. */
    public static boolean requiresPerception(Entity entity) {
        if (entity instanceof CursedSpirit spirit) return spirit.requiresCursePerception();
        return entity.getType().is(REQUIRES_PERCEPTION);
    }
}
