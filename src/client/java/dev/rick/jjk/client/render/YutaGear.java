package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rick.jjk.JJK;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Cursed Partners' gear as plain boxes in model pixels, coloured from a swatch palette ({@code yuta_gear.png}, 4x4 px
 * per colour): the katana, its sheath, the necklace with Rika's ring, and True Love's steel casing.
 */
public final class YutaGear {
    public static final RenderType TYPE = RenderTypes.entityCutout(JJK.id("textures/entity/yuta_gear.png"));
    public static final int STEEL = 0, EDGE = 1, GUARD = 2, WRAP = 3, SHEATH = 4, SHEATH_TRIM = 5, RING = 6, CHAIN = 7,
            CASING = 8, CASING_DARK = 9, CASING_LIGHT = 10, GOLD = 11, BLACK = 12, PINK = 13, ROPE = 14, RIVET = 15;

    private YutaGear() {}

    /** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1) in model pixels, in one swatch colour. */
    public static void box(PoseStack.Pose pose, VertexConsumer buf, float x0, float y0, float z0, float x1, float y1, float z1, int swatch, int light) {
        float u = (swatch % 4 * 4 + 2) / 16f, v = (swatch / 4 * 4 + 2) / 16f;
        float a = x0 / 16f, b = y0 / 16f, c = z0 / 16f, d = x1 / 16f, e = y1 / 16f, f = z1 / 16f;
        quad(pose, buf, a, b, c, d, b, c, d, e, c, a, e, c, 0, 0, -1, u, v, light);
        quad(pose, buf, d, b, f, a, b, f, a, e, f, d, e, f, 0, 0, 1, u, v, light);
        quad(pose, buf, a, b, f, a, b, c, a, e, c, a, e, f, -1, 0, 0, u, v, light);
        quad(pose, buf, d, b, c, d, b, f, d, e, f, d, e, c, 1, 0, 0, u, v, light);
        quad(pose, buf, a, b, f, d, b, f, d, b, c, a, b, c, 0, -1, 0, u, v, light);
        quad(pose, buf, a, e, c, d, e, c, d, e, f, a, e, f, 0, 1, 0, u, v, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buf, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, float nx, float ny, float nz, float u, float v, int light) {
        vtx(pose, buf, x1, y1, z1, nx, ny, nz, u, v, light);
        vtx(pose, buf, x2, y2, z2, nx, ny, nz, u, v, light);
        vtx(pose, buf, x3, y3, z3, nx, ny, nz, u, v, light);
        vtx(pose, buf, x4, y4, z4, nx, ny, nz, u, v, light);
    }

    private static void vtx(PoseStack.Pose pose, VertexConsumer buf, float x, float y, float z, float nx, float ny, float nz, float u, float v, int light) {
        buf.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /**
     * The katana along -z from its grip at the origin: a 6 px wrapped handle behind the origin, the guard, then an
     * 18 px blade with a pale edge on its underside, curving up a touch toward the tip.
     */
    public static void katana(PoseStack.Pose pose, VertexConsumer buf, int light, boolean blade) {
        // Handle: dark wrap with a red diamond pattern, pommel at the end.
        box(pose, buf, -0.55f, -0.65f, -0.5f, 0.55f, 0.65f, 5.5f, BLACK, light);
        for (int i = 0; i < 5; i++) box(pose, buf, -0.6f, -0.7f, 0.3f + i * 1.05f, 0.6f, 0.7f, 0.75f + i * 1.05f, WRAP, light);
        box(pose, buf, -0.65f, -0.75f, 5.4f, 0.65f, 0.75f, 6.0f, GOLD, light);
        // Guard.
        box(pose, buf, -1.4f, -1.5f, -1.0f, 1.4f, 1.5f, -0.5f, GUARD, light);
        if (!blade) return;
        // Blade in three gently rising segments.
        box(pose, buf, -0.22f, -0.75f, -7.0f, 0.22f, 0.45f, -1.0f, STEEL, light);
        box(pose, buf, -0.22f, -0.85f, -13.0f, 0.22f, 0.35f, -7.0f, STEEL, light);
        box(pose, buf, -0.2f, -0.95f, -18.0f, 0.2f, 0.15f, -13.0f, STEEL, light);
        box(pose, buf, -0.12f, 0.45f, -7.0f, 0.12f, 0.62f, -1.0f, EDGE, light);
        box(pose, buf, -0.12f, 0.35f, -13.0f, 0.12f, 0.52f, -7.0f, EDGE, light);
        box(pose, buf, -0.1f, 0.15f, -18.6f, 0.1f, 0.3f, -13.0f, EDGE, light);
    }

    /** The sheath along -z from its mouth at the origin (the katana's handle sits behind the mouth when holstered). */
    public static void sheath(PoseStack.Pose pose, VertexConsumer buf, int light) {
        box(pose, buf, -0.6f, -1.1f, -18.5f, 0.6f, 1.0f, 0f, SHEATH, light);
        box(pose, buf, -0.65f, -1.15f, -0.8f, 0.65f, 1.05f, 0f, SHEATH_TRIM, light);
        box(pose, buf, -0.65f, -1.15f, -19.0f, 0.65f, 1.05f, -18.2f, SHEATH_TRIM, light);
    }
}
