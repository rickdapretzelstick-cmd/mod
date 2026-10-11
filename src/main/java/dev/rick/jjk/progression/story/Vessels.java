package dev.rick.jjk.progression.story;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.ProgressionItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A character's vessel and its three states, told apart in the item's data and tooltip:
 * <ol>
 *   <li><b>Ordinary</b>: the VHS, Blindfold, Ring, Comb or Scratch-Off, crafted from everyday materials. Nothing in it.</li>
 *   <li><b>Essence-bearing</b>: an ordinary vessel with a Sealed Grade 1 Essence bound into it at a cauldron (hold both,
 *   use the cauldron). Marked {@code JjkEssence} in its data, glinting, its tooltip saying so. No cursed energy is spent
 *   on this step.</li>
 *   <li><b>Infused</b>: the essence-bearing vessel taken by a full cauldron of cursed energy: the world's one relic
 *   ({@link RelicForging}). Only an essence-bearing vessel can be infused.</li>
 * </ol>
 * Holding a sealed essence (or an essence-bearing vessel) claims nothing: the character's world slot is taken at
 * infusion, as before.
 */
public final class Vessels {
    static final String KEY = "JjkEssence", FROM = "JjkEssenceFrom";

    private Vessels() {}

    public static boolean essenceBearing(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBooleanOr(KEY, false);
    }

    /** Whether this is an ordinary (empty) character vessel. */
    public static boolean ordinary(ItemStack stack) {
        return CharacterStories.byObject(stack) != null && !essenceBearing(stack);
    }

    /** The essence-bearing form of a character's vessel (where the essence came from noted, if known). */
    public static ItemStack essenceBearing(CharacterStory story, @Nullable String from) {
        ItemStack s = new ItemStack(story.object().get());
        mark(s, from);
        return s;
    }

    static void mark(ItemStack s, @Nullable String from) {
        CompoundTag t = s.has(DataComponents.CUSTOM_DATA) ? s.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        t.putBoolean(KEY, true);
        if (from != null) t.putString(FROM, from);
        CustomData.set(DataComponents.CUSTOM_DATA, s, t);
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        s.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Essence-bearing: a Grade 1 essence is bound into it.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), Component.literal("A full cauldron of cursed energy will awaken it.").withStyle(ChatFormatting.GRAY))));
    }

    // --- The conversion: sealed essence + ordinary vessel, at a cauldron ---

    /**
     * Holding a Sealed Grade 1 Essence in one hand and an ordinary character vessel in the other, used on a cauldron
     * (empty or holding cursed energy, but not mid-ritual): the essence is bound into the vessel. Everything is checked
     * first (the right items, the character still free in this world); only then is the essence spent.
     */
    public static InteractionResult useOnCauldron(ServerPlayer p, ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        boolean cursed = state.is(dev.rick.jjk.progression.ProgressionBlocks.CURSED_CAULDRON);
        if (!state.is(Blocks.CAULDRON) && !cursed) return InteractionResult.PASS;
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        boolean mainEssence = main.is(ProgressionItems.SEALED_GRADE1_ESSENCE), offEssence = off.is(ProgressionItems.SEALED_GRADE1_ESSENCE);
        if (!mainEssence && !offEssence) return InteractionResult.PASS;
        ItemStack essence = mainEssence ? main : off, vessel = mainEssence ? off : main;
        if (cursed && state.getValue(dev.rick.jjk.progression.block.CursedCauldronBlock.INFUSION) > 0) {
            return refuse(p, "Not while the cauldron is working.");
        }
        CharacterStory story = CharacterStories.byObject(vessel);
        if (story == null) {
            return refuse(p, "Hold the character's ordinary vessel in your other hand (the VHS, Blindfold, Ring, Comb or Scratch-Off).");
        }
        if (essenceBearing(vessel)) return refuse(p, "That vessel already bears an essence.");
        if (vessel.getCount() != 1) return refuse(p, "One vessel at a time.");
        String why = RelicForging.refuse(level, story, p);
        if (why != null) return refuse(p, why);
        // Everything checks out: spend the essence, bind it into the vessel.
        String from = sealedFrom(essence);
        essence.shrink(1);
        mark(vessel, from);
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.4, 0);
        Fx.play(level, "prog_infuse_done", c, Vec3.ZERO, 1f, -1);
        Fx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
        Fx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
        p.sendOverlayMessage(Component.literal("The essence sinks into the " + vessel.getHoverName().getString() + ". It is no longer ordinary.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult refuse(ServerPlayer p, String why) {
        p.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
        return InteractionResult.FAIL;
    }

    // --- The sealed essence ---

    /** A Sealed Grade 1 Essence, captured from a contract's guardian. */
    public static ItemStack sealed(String contract, String guardianName) {
        ItemStack s = new ItemStack(ProgressionItems.SEALED_GRADE1_ESSENCE);
        CompoundTag t = new CompoundTag();
        t.putString("JjkContract", contract);
        t.putString("JjkGuardian", guardianName);
        CustomData.set(DataComponents.CUSTOM_DATA, s, t);
        s.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Captured from " + guardianName + ".").withStyle(ChatFormatting.GRAY),
                Component.literal("Bind it into a character's ordinary vessel at a cauldron.").withStyle(ChatFormatting.DARK_PURPLE))));
        return s;
    }

    @Nullable
    static String sealedFrom(ItemStack essence) {
        CustomData d = essence.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        String c = d.copyTag().getStringOr("JjkContract", "");
        return c.isEmpty() ? null : c;
    }

    public static boolean isSealed(ItemStack s, String contract) {
        return s.is(ProgressionItems.SEALED_GRADE1_ESSENCE) && contract.equals(sealedFrom(s));
    }

    public static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) return InteractionResult.PASS;
            return useOnCauldron(sp, level, hit.getBlockPos());
        });
    }
}
