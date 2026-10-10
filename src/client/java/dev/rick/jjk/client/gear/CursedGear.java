package dev.rick.jjk.client.gear;

import dev.rick.jjk.progression.tool.CursedToolItem;
import dev.rick.jjk.progression.tool.kit.CursedKits;
import dev.rick.jjk.progression.tool.kit.CursedToolKit;
import dev.rick.jjk.progression.tool.kit.GripProfile;
import dev.rick.jjk.registry.ModAttachments;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a player's equipped cursed tool looks like to everyone: drawn (its moveset in use) it is in their hand, even
 * though the hand slot itself is empty, in first person and third; holstered it rides where its grip profile carries it
 * (a sheath at the hip, slung on the back). Read from two synced attachments (the slot and whether it is drawn), so
 * every client sees every player the same way.
 */
public final class CursedGear {
    /** The holstered tool's item state, for {@link CursedGearLayer}. */
    public static final RenderStateDataKey<ItemStackRenderState> HOLSTERED = RenderStateDataKey.create(() -> "jjk:holstered_tool");
    public static final RenderStateDataKey<GripProfile> GRIP = RenderStateDataKey.create(() -> "jjk:tool_grip");

    private CursedGear() {}

    public static ItemStack equipped(Player p) {
        ItemStack s = p.getAttached(ModAttachments.CURSED_ITEM);
        return s == null ? ItemStack.EMPTY : s;
    }

    public static boolean drawn(Player p) {
        Boolean d = p.getAttached(ModAttachments.TOOL_DRAWN);
        return d != null && d && !equipped(p).isEmpty();
    }

    /** The tool to show in the main hand: the drawn tool (in place of what the hand slot has), else what is really there. */
    public static ItemStack mainHand(Player p, ItemStack real) {
        return drawn(p) ? equipped(p) : real;
    }

    /** Whether the drawn tool is the Cursed Rifle (its HUD, its first-person pose). */
    public static boolean rifleDrawn(Player p) {
        return drawn(p) && dev.rick.jjk.progression.tool.rifle.RifleServer.isRifle(equipped(p));
    }

    public static GripProfile grip(ItemStack s) {
        if (s.getItem() instanceof CursedToolItem t) {
            CursedToolKit k = CursedKits.kit(t.definition().id());
            if (k != null) return k.grip();
        }
        return GripProfile.ONE_HAND;
    }
}
