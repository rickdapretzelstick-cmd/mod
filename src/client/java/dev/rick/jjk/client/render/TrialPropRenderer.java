package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import dev.rick.jjk.progression.training.TrialPropEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a relic-trial room's props ({@link TrialPropEntity}) from their Blockbench models ({@code models/bb/trial_*}).
 *
 * <p>Each model is baked once (per resource reload) into flat quad arrays, one part per top-level group, so a frame only
 * streams vertices: no per-cube matrix work, no entity per cube. Static parts are lit per cube from the world's light
 * around each cube (refreshed once a second), so the furnishings sit in the room's own lighting; {@code #glow} parts
 * (lantern cores, neon, the Blue, ritual inlays) are drawn full bright. {@code #spin}/{@code #bob}/{@code #flicker} parts
 * move from the shared game time, identically for everyone. {@code #rect} groups are the rectangles the working parts
 * draw into: the CRT's animated screen (static and scanlines, tinted by the television's mood) and the six reel windows
 * (the server's symbols, a cosmetic spin while a reel is live, a settle when it locks, a coloured flash for its result).
 * Bounds for culling come from the baked geometry.
 */
public class TrialPropRenderer extends EntityRenderer<TrialPropEntity, TrialPropRenderer.State> {
    public static final Identifier ATLAS = JJK.id("textures/entity/trial_atlas.png");
    public static final Identifier STATIC = JJK.id("textures/entity/trial_crt_static.png");
    public static final Identifier SYMBOLS = JJK.id("textures/entity/trial_reel_symbols.png");
    /** The cursed realm rooms' atlas (the trial atlas plus earth, soil and ash). */
    public static final Identifier REALM_ATLAS = JJK.id("textures/entity/realm_atlas.png");
    private static final RenderType SOLID = RenderTypes.entityCutoutCull(ATLAS);
    private static final RenderType REALM_SOLID = RenderTypes.entityCutoutCull(REALM_ATLAS);
    private static final RenderType RIKA = RenderTypes.entityCutout(RikaRenderer.TEXTURE);
    private static final RenderType SCREEN = RenderTypes.entityCutout(STATIC);
    private static final RenderType REEL = RenderTypes.entityCutout(SYMBOLS);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int CRT_FRAMES = 16, SYMBOL_COUNT = 6;
    /** The atlas's plain white tile (the "white" material), for tinted frames. */
    private static final float[] WHITE_UV = {32 / 128f + 0.01f, 16 / 128f + 0.01f, 48 / 128f - 0.01f, 32 / 128f - 0.01f};

    public static class State extends EntityRenderState {
        @Nullable Mesh mesh;
        int kind, state, flags;
        float param, time;
        int[] lights = new int[0];
        int id;
        float yaw;
        boolean realm;
        @Nullable dev.rick.jjk.client.anim.PoseFrame pose;
        /** Per reel: ticks since it locked (for the settle), and since its result changed (for the flash). */
        float[] settle = new float[3], flash = new float[3];
    }

    private final Map<Integer, LightCache> lightCache = new HashMap<>();
    private final Map<Integer, int[]> reelMemory = new HashMap<>();

    private record LightCache(Mesh mesh, long at, int[] lights) {}

    public TrialPropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(TrialPropEntity e, float partial) {
        if (e.kind() == TrialPropEntity.RIKA) {
            float k = Math.max(1, e.param());
            return new AABB(-1.6 * k, -0.2, -1.6 * k, 1.6 * k, 4 * k, 1.6 * k).move(e.getX(), e.getY(), e.getZ());
        }
        Mesh m = Mesh.of(e.model());
        if (m == null) return super.getBoundingBoxForCulling(e, partial);
        return new AABB(m.min[0], m.min[1], m.min[2], m.max[0], m.max[1], m.max[2]).move(e.getX(), e.getY(), e.getZ());
    }

    @Override
    public void extractRenderState(TrialPropEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.kind = e.kind();
        s.yaw = e.getYRot();
        s.realm = e.model().startsWith("realm_");
        if (s.kind == TrialPropEntity.RIKA) {
            // Rika's own idle clip on this display, as her renderer runs it on her.
            float now = e.level().getGameTime() + partial;
            RikaRenderer.ensureIdle(e.getId(), now);
            s.pose = dev.rick.jjk.client.anim.ClientAnimations.computeModel(e, now);
            s.param = e.param();
            s.mesh = null;
            return;
        }
        s.mesh = Mesh.of(e.model());
        s.state = e.state();
        s.flags = e.has(TrialPropEntity.HIDDEN) ? TrialPropEntity.HIDDEN : 0;
        s.param = e.param();
        s.time = e.level().getGameTime() + partial;
        s.id = e.getId();
        if (s.mesh == null) return;
        // Per-cube light, sampled round each cube once a second (the rooms' lights only change by phase).
        long now = e.level().getGameTime();
        LightCache c = lightCache.get(e.getId());
        if (c == null || c.mesh != s.mesh || now - c.at >= 20 || now < c.at) {
            c = new LightCache(s.mesh, now, s.mesh.sampleLights(e.level(), e.getX(), e.getY(), e.getZ()));
            lightCache.put(e.getId(), c);
            if (lightCache.size() > 64) lightCache.keySet().removeIf(id -> e.level().getEntity(id) == null);
        }
        s.lights = c.lights;
        if (s.kind == TrialPropEntity.REELS) reels(e, s, now, partial);
    }

    /** Remembers, per reel, when it last stopped and when its result changed (client-side timing of the server's state). */
    private void reels(TrialPropEntity e, State s, long now, float partial) {
        int[] mem = reelMemory.computeIfAbsent(e.getId(), k -> new int[] {-1, 0, 0, 0, 0, 0, 0});
        if (reelMemory.size() > 32) reelMemory.keySet().removeIf(id -> e.level().getEntity(id) == null);
        for (int i = 0; i < 3; i++) {
            int v = s.state >> (i * 9) & 0x1FF;
            int was = mem[0] < 0 ? v : mem[0] >> (i * 9) & 0x1FF;
            boolean spinning = (v >> 6 & 1) == 1, wasSpinning = (was >> 6 & 1) == 1;
            if (mem[0] < 0) {
                mem[1 + i] = (int) now - 100;
                mem[4 + i] = (int) now - 100;
            }
            if (wasSpinning && !spinning || !spinning && (was & 0x3F) != (v & 0x3F)) mem[1 + i] = (int) now;
            if ((was >> 7 & 3) != (v >> 7 & 3)) mem[4 + i] = (int) now;
            s.settle[i] = now - mem[1 + i] + partial;
            s.flash[i] = now - mem[4 + i] + partial;
        }
        mem[0] = s.state;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        if (s.kind == TrialPropEntity.RIKA) {
            rika(s, ps, c);
            super.submit(s, ps, c, cam);
            return;
        }
        Mesh m = s.mesh;
        if (m == null || (s.flags & TrialPropEntity.HIDDEN) != 0) return;
        if (s.kind == TrialPropEntity.BLUE && s.param < 0.02f) return;
        ps.pushPose();
        int[] lights = s.lights;
        for (Part part : m.parts) {
            ps.pushPose();
            float scale = 1;
            if (s.kind == TrialPropEntity.BLUE) {
                // The Blue: its size is the server's (it grows in, collapses), with a slow turn and a breath.
                scale = s.param * (1 + 0.04f * Mth.sin(s.time * 0.21f));
                ps.translate(part.pivot[0], part.pivot[1], part.pivot[2]);
                ps.rotate(Axis.YP.rotationDegrees(s.time * 3.2f));
                ps.rotate(Axis.XP.rotationDegrees(Mth.sin(s.time * 0.05f) * 12f));
                ps.scale(scale, scale, scale);
                ps.translate(-part.pivot[0], -part.pivot[1], -part.pivot[2]);
            }
            part.animate(ps, s.time);
            int argb = 0xFFFFFFFF;
            boolean glow = part.glow;
            if (s.kind == TrialPropEntity.GLOW && glow) {
                // The altar's blade: as bright as his control (the server's), a stutter after a mistake, dark when broken.
                float k = s.state == 2 ? 0.12f : s.param * (s.state == 1 ? 0.55f + 0.45f * Math.abs(Mth.sin(s.time * 2.3f)) : 1f);
                int v = Mth.clamp(Math.round(80 + 175 * k), 0, 255);
                argb = 0xFF000000 | v << 16 | v << 8 | v;
            }
            final int color = argb;
            final int[] lit = lights;
            final int fixed = glow ? FULL_BRIGHT : -1;
            c.submitCustomGeometry(ps, s.realm ? REALM_SOLID : SOLID, (pose, buf) -> part.emit(pose, buf, lit, fixed, color));
            ps.popPose();
        }
        if (s.kind == TrialPropEntity.CRT) for (float[] r : m.rects) screen(s, ps, c, r);
        if (s.kind == TrialPropEntity.REELS) reelWindows(s, ps, c, m.rects);
        ps.popPose();
        super.submit(s, ps, c, cam);
    }

    // ---------------------------------------------------------------- Rika

    /**
     * The mod's own Rika (her model, texture and idle clip, as {@link RikaRenderer} draws her fully manifested), at the
     * room's presentation scale and facing: a presence on a terrace, not an entity of hers.
     */
    private static void rika(State s, PoseStack ps, SubmitNodeCollector c) {
        BbModel model = BbModels.get("rika");
        if (model == null) return;
        ps.pushPose();
        ps.rotate(Axis.YP.rotationDegrees(180f - s.yaw));
        float k = RikaRenderer.SCALE * Math.max(0.1f, s.param);
        ps.scale(k, k, k);
        BbModel.Posing pose = RikaRenderer.posing(s.pose);
        int light = s.lightCoords;
        c.submitCustomGeometry(ps, RIKA, (p, buf) -> model.render(p, buf, pose, light, 0xFFFFFFFF));
        ps.popPose();
    }

    // ---------------------------------------------------------------- the CRT

    /** Mood tints: calm cyan-white, suspense red, action amber, sad blue. */
    private static final int[] MOOD = {0xBFE6FF, 0xFF4040, 0xFFB050, 0x7088FF};

    private static void screen(State s, PoseStack ps, SubmitNodeCollector c, float[] r) {
        // Static frames run faster when the room is agitated; a slow vertical roll every few seconds.
        float speed = 0.35f + 0.45f * Mth.clamp(s.param, 0, 1.5f);
        int frame = Math.floorMod((int) (s.time * speed) + s.id, CRT_FRAMES);
        int tint = MOOD[Math.floorMod(s.state, MOOD.length)];
        float bright = 0.75f + 0.25f * Mth.clamp(1 - s.param * 0.4f, 0, 1) + 0.06f * Mth.sin(s.time * 0.9f);
        int rr = Math.min(255, Math.round((tint >> 16 & 255) * bright)), gg = Math.min(255, Math.round((tint >> 8 & 255) * bright)),
                bb = Math.min(255, Math.round((tint & 255) * bright));
        int argb = 0xFF000000 | rr << 16 | gg << 8 | bb;
        float v0 = frame / (float) CRT_FRAMES, v1 = (frame + 1) / (float) CRT_FRAMES;
        // The glass faces -Z (the room is turned so the participant looks toward +Z at it).
        float z = r[2] - 0.012f;
        c.submitCustomGeometry(ps, SCREEN, (pose, buf) -> quadZ(pose, buf, r[0], r[1], r[3], r[4], z, 0, v0, 1, v1, argb, FULL_BRIGHT));
        // A brighter band rolling down the picture.
        float roll = 1 - (s.time * 0.012f % 1f);
        float h = (r[4] - r[1]) * 0.08f, y = r[1] + (r[4] - r[1] - h) * roll;
        int band = 0xFF000000 | Math.min(255, rr + 50) << 16 | Math.min(255, gg + 50) << 8 | Math.min(255, bb + 50);
        c.submitCustomGeometry(ps, SCREEN, (pose, buf) -> quadZ(pose, buf, r[0], y, r[3], y + h, z - 0.002f, 0, v0, 1, v0 + 0.01f, band, FULL_BRIGHT));
    }

    // ---------------------------------------------------------------- the reels

    private static final int[] RESULT = {0, 0x55FF77, 0xFF3344, 0xFFD040};

    private static void reelWindows(State s, PoseStack ps, SubmitNodeCollector c, List<float[]> rects) {
        for (int i = 0; i < 3 && i * 2 + 1 < rects.size(); i++) {
            int v = s.state >> (i * 9) & 0x1FF;
            int top = v & 7, low = v >> 3 & 7;
            boolean spinning = (v >> 6 & 1) == 1;
            int res = v >> 7 & 3;
            float[] up = rects.get(i * 2), dn = rects.get(i * 2 + 1);
            float strip;
            if (spinning) {
                // A cosmetic blur of the strip (no outcome is ever chosen here: a stopped reel shows the server's symbol).
                strip = s.time * 0.42f + i * 1.7f;
                drawStrip(ps, c, up, strip, 0);
                drawStrip(ps, c, dn, strip + 1, 0);
            } else {
                // Locked: the server's symbols, settling with a small overshoot.
                float t = Mth.clamp(s.settle[i] / 7f, 0, 1);
                float off = (1 - t) * (1 - t) * 0.45f - Mth.sin(t * Mth.PI) * 0.08f;
                drawCell(ps, c, up, top, off);
                drawCell(ps, c, dn, low, off);
            }
            if (res != 0 && !spinning) {
                // The result light round the payline window: a flash that settles to a steady rim.
                float f = Mth.clamp(s.flash[i] / 14f, 0, 1);
                float k = res == 2 ? 1 - f * 0.8f : 1 - f * 0.55f;
                int col = RESULT[res];
                int argb = 0xFF000000 | Math.round((col >> 16 & 255) * k) << 16 | Math.round((col >> 8 & 255) * k) << 8 | Math.round((col & 255) * k);
                frame(ps, c, up, argb, 0.14f + 0.12f * (1 - f));
            }
        }
    }

    /** The strip at position {@code pos} (symbol index + fraction) in a window: two partial cells. */
    private static void drawStrip(PoseStack ps, SubmitNodeCollector c, float[] r, float pos, float unused) {
        int k = Math.floorMod((int) Math.floor(pos), SYMBOL_COUNT);
        float f = pos - (float) Math.floor(pos);
        drawCell(ps, c, r, k, f);
        drawCell(ps, c, r, (k + 1) % SYMBOL_COUNT, f - 1);
    }

    /**
     * Symbol {@code k} in a window, shifted down by {@code off} windows (0 centred, 0.5 half below), clipped to the
     * window: the part showing is drawn with its texture cut to match.
     */
    private static void drawCell(PoseStack ps, SubmitNodeCollector c, float[] r, int k, float off) {
        float w = r[3] - r[0], h = r[4] - r[1];
        float size = Math.min(w, h) * 0.86f;
        float x0 = r[0] + (w - size) / 2, x1 = x0 + size;
        float y0 = r[1] + (h - size) / 2 - off * h, y1 = y0 + size;
        float cy0 = Math.max(y0, r[1] + 0.05f), cy1 = Math.min(y1, r[4] - 0.05f);
        if (cy1 <= cy0) return;
        float vs = k / (float) SYMBOL_COUNT, ve = (k + 1) / (float) SYMBOL_COUNT;
        // Texture v runs top (vs) to bottom (ve); world y runs bottom to top.
        float vt = vs + (y1 - cy1) / size * (ve - vs), vb = vs + (y1 - cy0) / size * (ve - vs);
        float z = r[2] - 0.02f;
        c.submitCustomGeometry(ps, REEL, (pose, buf) -> quadZ(pose, buf, x0, cy0, x1, cy1, z, 0, vt, 1, vb, 0xFFFFFFFF, FULL_BRIGHT));
    }

    private static void frame(PoseStack ps, SubmitNodeCollector c, float[] r, int argb, float t) {
        float z = r[2] - 0.03f;
        float x0 = r[0] - 0.05f, x1 = r[3] + 0.05f, y0 = r[1] - 0.05f, y1 = r[4] + 0.05f;
        float[][] bars = {{x0, y0, x1, y0 + t}, {x0, y1 - t, x1, y1}, {x0, y0, x0 + t, y1}, {x1 - t, y0, x1, y1}};
        for (float[] b : bars) {
            c.submitCustomGeometry(ps, SOLID, (pose, buf) -> quadZ(pose, buf, b[0], b[1], b[2], b[3], z, WHITE_UV[0], WHITE_UV[1], WHITE_UV[2], WHITE_UV[3], argb, FULL_BRIGHT));
        }
    }

    /** A quad facing -Z at depth {@code z}, from (x0,y0) to (x1,y1); u0,v0 at its top-left as seen from -Z. */
    private static void quadZ(PoseStack.Pose pose, VertexConsumer buf, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1,
                              float v1, int argb, int light) {
        // Seen from -Z, +X is on the left: the top-left corner is (x1, y1).
        vertex(pose, buf, x1, y1, z, u0, v0, argb, light);
        vertex(pose, buf, x0, y1, z, u1, v0, argb, light);
        vertex(pose, buf, x0, y0, z, u1, v1, argb, light);
        vertex(pose, buf, x1, y0, z, u0, v1, argb, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buf, float x, float y, float z, float u, float v, int argb, int light) {
        buf.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
    }

    // ---------------------------------------------------------------- baking

    /** One top-level group of a prop model, baked: its quads (blocks, model space) and how it moves. */
    static final class Part {
        final boolean glow, flicker;
        final float[] pivot = new float[3];
        final float[] spin; // axis x,y,z and degrees per tick, or null
        final float bobAmp, bobPeriod;
        final float phase;
        // Per quad: 4 vertices x (x,y,z,u,v) and a normal; the cube it came from (for light).
        float[] verts = new float[0];
        float[] normals = new float[0];
        int[] cube = new int[0];
        int quads;
        /** The world-light sample points (one per cube), and this part's first index in the mesh's light array. */
        final List<float[]> centres = new ArrayList<>();
        int lightBase;

        Part(String name, Vector3f origin) {
            boolean g = false, f = false;
            float[] sp = null;
            float amp = 0, per = 1;
            for (String tok : name.split("#")) {
                if (tok.equals("glow")) g = true;
                else if (tok.equals("flicker")) f = true;
                else if (tok.startsWith("spin:")) {
                    String[] a = tok.substring(5).split(",");
                    sp = new float[] {Float.parseFloat(a[0]), Float.parseFloat(a[1]), Float.parseFloat(a[2]), Float.parseFloat(a[3])};
                } else if (tok.startsWith("bob:")) {
                    String[] a = tok.substring(4).split(",");
                    amp = Float.parseFloat(a[0]);
                    per = Math.max(1, Float.parseFloat(a[1]));
                }
            }
            glow = g;
            flicker = f;
            spin = sp;
            bobAmp = amp;
            bobPeriod = per;
            pivot[0] = origin.x / 16f;
            pivot[1] = origin.y / 16f;
            pivot[2] = origin.z / 16f;
            phase = (Math.abs(name.hashCode()) % 1000) / 1000f * Mth.TWO_PI;
        }

        boolean moves() {
            return spin != null || bobAmp != 0 || flicker;
        }

        void animate(PoseStack ps, float time) {
            if (!moves()) return;
            ps.translate(pivot[0], pivot[1] + (bobAmp != 0 ? bobAmp * Mth.sin(time * Mth.TWO_PI / bobPeriod + phase) : 0), pivot[2]);
            if (spin != null) ps.rotate(new Quaternionf().rotationAxis(spin[3] * time * Mth.DEG_TO_RAD, spin[0], spin[1], spin[2]));
            if (flicker) {
                float k = 0.85f + 0.15f * Mth.sin(time * 1.7f + phase) + 0.08f * Mth.sin(time * 4.3f + phase * 2);
                ps.scale(0.9f + 0.1f * k, k, 0.9f + 0.1f * k);
            }
            ps.translate(-pivot[0], -pivot[1], -pivot[2]);
        }

        void emit(PoseStack.Pose pose, VertexConsumer buf, int[] lights, int fixedLight, int argb) {
            for (int q = 0; q < quads; q++) {
                int light = fixedLight >= 0 ? fixedLight : lights.length > lightBase + cube[q] ? lights[lightBase + cube[q]] : FULL_BRIGHT;
                float nx = normals[q * 3], ny = normals[q * 3 + 1], nz = normals[q * 3 + 2];
                for (int k = 0; k < 4; k++) {
                    int o = (q * 4 + k) * 5;
                    buf.addVertex(pose, verts[o], verts[o + 1], verts[o + 2]).setColor(argb).setUv(verts[o + 3], verts[o + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
                }
            }
        }
    }

    /** A prop model, baked. Rebuilt when the resource reload replaces the model. */
    static final class Mesh {
        private static final Map<BbModel, Mesh> CACHE = new WeakHashMap<>();
        final List<Part> parts = new ArrayList<>();
        /** Rect groups' cubes as {x0, y0, z0, x1, y1, z1} (blocks), in the order the model lists them. */
        final List<float[]> rects = new ArrayList<>();
        final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        int samples;

        @Nullable
        static Mesh of(String name) {
            if (name.isEmpty()) return null;
            BbModel m = BbModels.get(name);
            if (m == null) return null;
            synchronized (CACHE) {
                return CACHE.computeIfAbsent(m, Mesh::new);
            }
        }

        private Mesh(BbModel model) {
            for (BbModel.Group g : model.roots) bake(g);
            if (min[0] > max[0]) {
                for (int i = 0; i < 3; i++) {
                    min[i] = -1;
                    max[i] = 1;
                }
            }
        }

        private void bake(BbModel.Group g) {
            if (g.name.contains("#rect")) {
                for (BbModel.Cube c : g.cubes) rects.add(new float[] {c.from().x / 16f, c.from().y / 16f, c.from().z / 16f, c.to().x / 16f, c.to().y / 16f, c.to().z / 16f});
                return;
            }
            Part p = new Part(g.name, g.origin);
            p.lightBase = samples;
            List<float[]> verts = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();
            List<Integer> cubes = new ArrayList<>();
            float reach = 0;
            for (int ci = 0; ci < g.cubes.size(); ci++) {
                BbModel.Cube c = g.cubes.get(ci);
                Matrix4f m = new Matrix4f();
                if (c.rot().x != 0 || c.rot().y != 0 || c.rot().z != 0) {
                    m.translate(c.origin()).rotate(new Quaternionf().rotationZYX(c.rot().z, c.rot().y, c.rot().x)).translate(-c.origin().x, -c.origin().y, -c.origin().z);
                }
                Matrix3f nm = new Matrix3f(m).invert().transpose();
                float x1 = c.from().x, y1 = c.from().y, z1 = c.from().z, x2 = c.to().x, y2 = c.to().y, z2 = c.to().z;
                float[][][] faces = {
                        {{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}},
                        {{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}},
                        {{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}},
                        {{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}},
                        {{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}},
                        {{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}},
                };
                Vector3f[] base = {new Vector3f(0, 0, -1), new Vector3f(0, 0, 1), new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0),
                        new Vector3f(0, -1, 0)};
                Vector3f v = new Vector3f();
                for (int f = 0; f < 6; f++) {
                    float[] uv = c.uv()[f];
                    if (uv == null) continue;
                    float[][] tex = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
                    int turns = Math.floorMod(Math.round(uv[4] / 90f), 4);
                    float[] quad = new float[20];
                    for (int k = 0; k < 4; k++) {
                        m.transformPosition(faces[f][k][0], faces[f][k][1], faces[f][k][2], v);
                        v.div(16f);
                        float[] t = tex[(k + turns) % 4];
                        quad[k * 5] = v.x;
                        quad[k * 5 + 1] = v.y;
                        quad[k * 5 + 2] = v.z;
                        quad[k * 5 + 3] = t[0];
                        quad[k * 5 + 4] = t[1];
                        for (int a = 0; a < 3; a++) {
                            float val = a == 0 ? v.x : a == 1 ? v.y : v.z;
                            min[a] = Math.min(min[a], val);
                            max[a] = Math.max(max[a], val);
                        }
                        reach = Math.max(reach, (float) Math.sqrt((v.x - p.pivot[0]) * (v.x - p.pivot[0]) + (v.y - p.pivot[1]) * (v.y - p.pivot[1])
                                + (v.z - p.pivot[2]) * (v.z - p.pivot[2])));
                    }
                    Vector3f n = nm.transform(new Vector3f(base[f])).normalize();
                    verts.add(quad);
                    normals.add(new float[] {n.x, n.y, n.z});
                    cubes.add(ci);
                }
                p.centres.add(new float[] {(x1 + x2) / 32f, (y1 + y2) / 32f, (z1 + z2) / 32f});
            }
            if (p.moves()) {
                // Whatever turns or floats can reach anywhere round its pivot: the culling box covers that.
                for (int a = 0; a < 3; a++) {
                    min[a] = Math.min(min[a], p.pivot[a] - reach - Math.abs(p.bobAmp));
                    max[a] = Math.max(max[a], p.pivot[a] + reach + Math.abs(p.bobAmp));
                }
                // Moving parts are lit from their pivot, all alike.
                p.centres.clear();
                p.centres.add(p.pivot.clone());
                for (int q = 0; q < cubes.size(); q++) cubes.set(q, 0);
            }
            p.quads = verts.size();
            p.verts = new float[p.quads * 20];
            p.normals = new float[p.quads * 3];
            p.cube = new int[p.quads];
            for (int q = 0; q < p.quads; q++) {
                System.arraycopy(verts.get(q), 0, p.verts, q * 20, 20);
                System.arraycopy(normals.get(q), 0, p.normals, q * 3, 3);
                p.cube[q] = cubes.get(q);
            }
            samples += p.centres.size();
            if (p.quads > 0) parts.add(p);
            for (BbModel.Group child : g.children) bake(child);
        }

        /**
         * The light at every cube: the brightest of its own block and the six round it (a cube against a wall or the
         * floor would otherwise read the inside of the solid block).
         */
        int[] sampleLights(Level level, double ox, double oy, double oz) {
            int[] out = new int[samples];
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int i = 0;
            for (Part p : parts) {
                for (float[] c : p.centres) {
                    int bx = Mth.floor(ox + c[0]), by = Mth.floor(oy + c[1]), bz = Mth.floor(oz + c[2]);
                    int block = 0, sky = 0;
                    for (int d = 0; d < 7; d++) {
                        m.set(bx + (d == 1 ? 1 : d == 2 ? -1 : 0), by + (d == 3 ? 1 : d == 4 ? -1 : 0), bz + (d == 5 ? 1 : d == 6 ? -1 : 0));
                        block = Math.max(block, level.getBrightness(LightLayer.BLOCK, m));
                        sky = Math.max(sky, level.getBrightness(LightLayer.SKY, m));
                    }
                    out[p.lightBase + (i - p.lightBase)] = block << 4 | sky << 20;
                    i++;
                }
            }
            return out;
        }
    }
}
