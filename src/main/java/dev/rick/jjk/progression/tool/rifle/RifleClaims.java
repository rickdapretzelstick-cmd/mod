package dev.rick.jjk.progression.tool.rifle;

import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.registry.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Which Cursed Rifles are real. The rifle is not unique (anyone who sees a lodge investigation through earns one), but
 * every rifle a lodge hands out carries a claim id, recorded in the world's investigation save with whose it is. A
 * player has at most one live claim: the recovery route (a lodge's rack, for someone who earned a rifle there and no
 * longer carries it) issues a new rifle and retires the old id, so the lost one, wherever it is, goes cold rather than
 * making two. A rifle with no claim id (Creative, an operator's give) always works.
 */
public final class RifleClaims {
    public static final String TAG = "jjk_rifle_claim";

    private RifleClaims() {}

    @Nullable
    public static String claimOf(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        String id = d.copyTag().getStringOr(TAG, "");
        return id.isEmpty() ? null : id;
    }

    /** Why this rifle won't fire (its claim has been retired), or null. */
    @Nullable
    public static String inertReason(ServerPlayer p, ItemStack stack) {
        String id = claimOf(stack);
        if (id == null) return null;
        InvestigationState st = InvestigationState.get(p.level().getServer());
        return st.rifleClaims().containsKey(id) ? null : "The rifle is cold and silent. Whatever lived in it has moved on.";
    }

    /** A new rifle for {@code p}, replacing (retiring) any claim they held before. */
    public static ItemStack issue(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        InvestigationState st = InvestigationState.get(server);
        String old = p.getAttached(ModAttachments.RIFLE_CLAIM);
        String id = UUID.randomUUID().toString();
        InvestigationState.Claims.issue(st, old, id, p.getUUID());
        p.setAttached(ModAttachments.RIFLE_CLAIM, id);
        ItemStack s = new ItemStack(ProgressionItems.CURSED_RIFLE);
        CompoundTag t = new CompoundTag();
        t.putString(TAG, id);
        CustomData.set(DataComponents.CUSTOM_DATA, s, t);
        return s;
    }

    /** Whether {@code p} carries (inventory or ender chest) the rifle of their live claim. */
    public static boolean carriesLive(ServerPlayer p) {
        java.util.Map<String, java.util.UUID> live = InvestigationState.get(p.level().getServer()).rifleClaims();
        for (net.minecraft.world.Container c : new net.minecraft.world.Container[] {p.getInventory(), p.getEnderChestInventory()}) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                String id = claimOf(c.getItem(i));
                if (id != null && p.getUUID().equals(live.get(id))) return true;
            }
        }
        return false;
    }

    /** Whether {@code p} has ever been issued a rifle (a live claim on record). */
    public static boolean everIssued(ServerPlayer p) {
        return InvestigationState.get(p.level().getServer()).rifleClaims().containsValue(p.getUUID());
    }
}
