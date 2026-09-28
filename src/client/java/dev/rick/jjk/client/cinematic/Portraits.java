package dev.rick.jjk.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    /**
     * Renders a living entity's model into a GUI box looking toward (lookX, lookY), like the inventory preview but
     * without its name tag or shadow, so only the character itself appears.
     */
    public static void model(GuiGraphicsExtractor g, LivingEntity entity, int x0, int y0, int x1, int y1, int size, float lookX, float lookY) {
        float centerX = (x0 + x1) / 2f, centerY = (y0 + y1) / 2f;
        float xAngle = (float) Math.atan((centerX - lookX) / 40f), yAngle = (float) Math.atan((centerY - lookY) / 40f);
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf xRotation = new org.joml.Quaternionf().rotateX(yAngle * 20f * ((float) Math.PI / 180f));
        rotation.mul(xRotation);
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        var state = renderer.createRenderState(entity, 1f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState ls) {
            ls.bodyRot = 180f + xAngle * 20f;
            ls.yRot = xAngle * 20f;
            ls.xRot = -yAngle * 20f;
            ls.boundingBoxWidth /= ls.scale;
            ls.boundingBoxHeight /= ls.scale;
            ls.scale = 1f;
        }
        org.joml.Vector3f translation = new org.joml.Vector3f(0, state.boundingBoxHeight / 2f + 0.0625f, 0);
        g.entity(state, size, translation, rotation, xRotation, x0, y0, x1, y1);
    }

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
            model(g, le, x0, y0, x1, 2 * centerY - y0, size, cx, y0 + h * 0.2f);
            g.disableScissor();
        } else {
            int cx = (x0 + x1) / 2, w = (x1 - x0) / 4;
            g.fill(cx - w, y0 + (y1 - y0) / 4, cx + w, y1, 0xFF22283C);
        }
    }
}
