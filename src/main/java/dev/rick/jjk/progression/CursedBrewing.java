package dev.rick.jjk.progression;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Brewing-stand recipes for items that aren't potions (vanilla only brews items carrying potion contents). The brewing
 * stand asks {@link #output} through a mixin on {@code PotionBrewing}, and its bottle slots accept every registered
 * input. The stand itself does the brewing: fuel, the 20-second brew, the ingredient used up.
 */
public final class CursedBrewing {
    public record Recipe(Item input, Item ingredient, Item output) {}

    private static final List<Recipe> RECIPES = new CopyOnWriteArrayList<>();

    private CursedBrewing() {}

    public static void register(Item input, Item ingredient, Item output) {
        RECIPES.add(new Recipe(input, ingredient, output));
    }

    public static void init() {
        register(ProgressionItems.SOUL_IN_A_BOTTLE, Items.GHAST_TEAR, ProgressionItems.CURSED_ENERGY_BOTTLE);
    }

    /** What brewing {@code ingredient} into {@code input} makes, or null if no recipe here covers it. */
    @Nullable
    public static ItemStack output(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || ingredient.isEmpty()) return null;
        for (Recipe r : RECIPES) if (input.is(r.input) && ingredient.is(r.ingredient)) return new ItemStack(r.output);
        return null;
    }

    /** Whether a bottle slot should take this item. */
    public static boolean isInput(ItemStack stack) {
        for (Recipe r : RECIPES) if (stack.is(r.input) || stack.is(r.output)) return true;
        return false;
    }
}
