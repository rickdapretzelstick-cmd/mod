package dev.rick.jjk.progression.story;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A character object after a full cauldron of cursed energy has gone into it: the world's one functional Infused Blindfold
 * (or VHS, Ring, Comb, Scratch-Off). It carries the token the world gave it ({@link UniqueRelics}); only the live token
 * works, so a copy or a re-forged-over relic is inert. It still grants nothing by itself: it is the key to its
 * character's personal storyline ({@link PersonalTrials}), and how it is used is for the player to find out (the VHS is
 * played on a jukebox, the Blindfold worn, the Ring, the Comb and the ticket used).
 *
 * <p>Destroyed while unbound (lava, a cactus, the void), it frees the world to forge another; once its storyline is
 * complete it stays bound to its owner.
 */
public class RelicItem extends Item {
    static final String KEY = "jjk_relic";
    /** Forged in Creative: a test relic, never registered, never claims anything. */
    static final String TEST = "jjk_relic_test";

    private final String kit;

    public RelicItem(String kit, Properties properties) {
        super(properties);
        this.kit = kit;
    }

    public String kit() {
        return kit;
    }

    /** The registry key of this kind of relic. */
    public static String key(String kit) {
        return "relic:" + kit;
    }

    @Nullable
    public static UUID token(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        String s = d.copyTag().getStringOr(KEY, "");
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isTest(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBooleanOr(TEST, false);
    }

    /** A relic stamped with the world's live token. */
    public static ItemStack create(Item item, UUID token) {
        ItemStack stack = new ItemStack(item);
        CompoundTag t = new CompoundTag();
        t.putString(KEY, token.toString());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, t);
        return stack;
    }

    /** A Creative test relic: works for trying the storyline out, never registered, never claims a character. */
    public static ItemStack createTest(Item item) {
        ItemStack stack = new ItemStack(item);
        CompoundTag t = new CompoundTag();
        t.putBoolean(TEST, true);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, t);
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // The Blindfold is worn (its equippable component puts it on); the VHS needs something to play it on.
        CharacterStory story = CharacterStories.byKit(kit);
        if (story == null || story.activation() != CharacterStory.Activation.USE) return super.use(level, player, hand);
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        return PersonalTrials.use(sp, player.getItemInHand(hand), null) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        CharacterStory story = CharacterStories.byKit(kit);
        if (story == null || story.activation() != CharacterStory.Activation.PLAY) return super.useOn(ctx);
        if (!(ctx.getLevel() instanceof ServerLevel) || !(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        return PersonalTrials.use(sp, ctx.getItemInHand(), ctx.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        super.onDestroyed(itemEntity);
        if (itemEntity.level() instanceof ServerLevel level && !isTest(itemEntity.getItem())) {
            if (UniqueRelics.get(level.getServer()).destroyed(key(kit), token(itemEntity.getItem()))) {
                for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Somewhere, something precious burns away. Its energy is loose in the world again.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC));
            }
        }
    }
}
