package dev.rick.jjk.progression.block;

import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.CharacterStory;
import dev.rick.jjk.progression.story.RelicForging;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a full cauldron of cursed energy can be dipped into, and what rises out: plain Glasses become Cursed Glasses, a
 * Dormant Prison Realm becomes the world's Prison Realm, ordinary steel takes on cursed energy as the first cursed tools,
 * and a character object (crafted from its Essence) becomes that character's one infused relic. The cauldron's
 * {@code held} state is the index here (so it is saved with the block and given back if broken): new entries only ever
 * go on the end.
 */
public final class CauldronInfusions {
    /** What rises out (made at the moment of collapse, where the world's one-of-a-kind checks are atomic). */
    @FunctionalInterface
    public interface Result {
        ItemStack make(ServerLevel level, BlockPos cauldron);
    }

    /** Why the energy won't take it now ({@code player} is who offered it, or null if it was tossed in), or null. */
    @FunctionalInterface
    public interface Check {
        @Nullable
        String refuse(ServerLevel level, @Nullable ServerPlayer player);
    }

    /**
     * @param input   what is dipped
     * @param result  what rises out
     * @param allowed whether the energy takes it right now (null: always)
     */
    public record Infusion(Supplier<Item> input, Result result, @Nullable Check allowed) {}

    public static final List<Infusion> ALL;

    static {
        List<Infusion> all = new ArrayList<>();
        all.add(new Infusion(() -> ProgressionItems.GLASSES, (l, pos) -> new ItemStack(ProgressionItems.CURSED_GLASSES), null));
        all.add(new Infusion(() -> ProgressionItems.DORMANT_PRISON_REALM, (l, pos) -> {
            // The world gives the cube its one id now (or, if another realm appeared meanwhile, it stays dormant).
            java.util.UUID id = dev.rick.jjk.progression.prison.PrisonRealm.forge(l.getServer());
            return id != null ? dev.rick.jjk.progression.prison.PrisonRealmItem.create(id) : new ItemStack(ProgressionItems.DORMANT_PRISON_REALM);
        }, (l, p) -> dev.rick.jjk.progression.prison.PrisonRealm.canForge(l.getServer()) ? null : "The energy recoils: a Prison Realm already exists in this world."));
        all.add(new Infusion(() -> Items.IRON_SWORD, (l, pos) -> new ItemStack(ProgressionItems.SLAUGHTER_DEMON), null));
        all.add(new Infusion(() -> Items.IRON_AXE, (l, pos) -> new ItemStack(ProgressionItems.CURSED_CLEAVER), null));
        all.add(new Infusion(() -> Items.COMPASS, (l, pos) -> new ItemStack(ProgressionItems.CURSED_COMPASS), null));
        // The character objects (indices 5-9): each becomes its character's one relic, if the world has none yet.
        for (CharacterStory s : CharacterStories.all()) {
            all.add(new Infusion(s.object(), (l, pos) -> RelicForging.make(l, pos, s), (l, p) -> RelicForging.refuse(l, s, p)));
        }
        ALL = List.copyOf(all);
    }

    private CauldronInfusions() {}

    /** The infusion for what is being dipped, as its index, or -1. */
    public static int indexOf(ItemStack stack) {
        for (int i = 0; i < ALL.size(); i++) if (stack.is(ALL.get(i).input().get())) return i;
        return -1;
    }

    public static Infusion get(int i) {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, i)));
    }

    /** Why the energy won't take it now, or null if it will. */
    @Nullable
    public static Component refused(ServerLevel level, int i, @Nullable ServerPlayer player) {
        Infusion f = get(i);
        String why = f.allowed() == null ? null : f.allowed().refuse(level, player);
        return why == null ? null : Component.literal(why);
    }

    /** It has gone in: what the energy needs to remember about who offered it (a relic's keeper). */
    public static void taken(ServerLevel level, BlockPos cauldron, int i, @Nullable ServerPlayer player) {
        if (i >= 5) RelicForging.offered(level, cauldron, player);
    }
}
