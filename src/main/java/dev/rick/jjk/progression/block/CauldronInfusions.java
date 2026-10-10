package dev.rick.jjk.progression.block;

import dev.rick.jjk.progression.ProgressionItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a full cauldron of cursed energy can be dipped into, and what rises out: plain Glasses become Cursed Glasses, a
 * Dormant Prison Realm becomes the world's Prison Realm, and ordinary steel takes on cursed energy as the first cursed
 * tools. The cauldron's {@code held} state is the index here (so it is saved with the block and given back if broken).
 * New cursed tools join the list with one line.
 */
public final class CauldronInfusions {
    /**
     * @param input   what is dipped
     * @param result  what rises out
     * @param allowed whether the energy takes it right now (null: always)
     * @param refusal said when it doesn't
     */
    public record Infusion(Supplier<Item> input, Function<ServerLevel, ItemStack> result, @Nullable Predicate<ServerLevel> allowed, String refusal) {}

    public static final List<Infusion> ALL = List.of(
            new Infusion(() -> ProgressionItems.GLASSES, l -> new ItemStack(ProgressionItems.CURSED_GLASSES), null, ""),
            new Infusion(() -> ProgressionItems.DORMANT_PRISON_REALM, l -> {
                // The world gives the cube its one id now (or, if another realm appeared meanwhile, it stays dormant).
                java.util.UUID id = dev.rick.jjk.progression.prison.PrisonRealm.forge(l.getServer());
                return id != null ? dev.rick.jjk.progression.prison.PrisonRealmItem.create(id) : new ItemStack(ProgressionItems.DORMANT_PRISON_REALM);
            }, l -> dev.rick.jjk.progression.prison.PrisonRealm.canForge(l.getServer()), "The energy recoils: a Prison Realm already exists in this world."),
            new Infusion(() -> Items.IRON_SWORD, l -> new ItemStack(ProgressionItems.SLAUGHTER_DEMON), null, ""),
            new Infusion(() -> Items.IRON_AXE, l -> new ItemStack(ProgressionItems.CURSED_CLEAVER), null, ""),
            new Infusion(() -> Items.COMPASS, l -> new ItemStack(ProgressionItems.CURSED_COMPASS), null, ""));

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
    public static Component refused(ServerLevel level, int i) {
        Infusion f = get(i);
        return f.allowed() != null && !f.allowed().test(level) ? Component.literal(f.refusal()) : null;
    }
}
