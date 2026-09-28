package dev.rick.jjk.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Puts a character into 2D presentations (cinematics, versus cards): their actual model, as it looks right now
 * (skin, blindfold or glowing eyes, armour), facing the viewer. Works for any living entity, so any future character
 * gets a portrait with no extra work.
 */
public final class Portraits {
    private Portraits() {}

    /**
     * Draws {@code entityId}'s portrait in the box, framed as a bust ({@code zoom} scales the model; ~1.6 shows head and
     * shoulders). Falls back to a plain silhouette card when the entity isn't loaded on this client.
     */
    public static void draw(GuiGraphicsExtractor g, int entityId, int x0, int y0, int x1, int y1, float zoom, int frameColor) {
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, frameColor);
        g.fillGradient(x0, y0, x1, y1, 0xFF14182A, 0xFF05060C);
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e instanceof LivingEntity le) {
            int h = y1 - y0;
            int size = Math.round(h * 0.55f * zoom);
            float cx = (x0 + x1) / 2f;
            // Frame a bust: the top of the head sits just under the card's top edge, the rest is cropped by the card.
            float body = le.getBbHeight() * size;
            int centerY = Math.round(y0 + h * 0.1f + body / 2f);
            g.enableScissor(x0, y0, x1, y1);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, 2 * centerY - y0, size, 0.0625f, cx, y0 + h * 0.2f, le);
            g.disableScissor();
        } else {
            int cx = (x0 + x1) / 2, w = (x1 - x0) / 4;
            g.fill(cx - w, y0 + (y1 - y0) / 4, cx + w, y1, 0xFF22283C);
        }
    }
}
