package dev.rick.jjk.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Blockbench model ({@code .bbmodel}, any format) drawn as it is in Blockbench: its bone hierarchy with pivots and rest
 * rotations, cubes with their own rotations, and per-face UVs. Coordinates stay Blockbench's (pixels, y up, the model's
 * front toward -z); the renderer turns the whole model to face where its entity faces.
 *
 * <p>Bones are posed with Blockbench's animator convention, so animations made in Blockbench play back the same: a
 * bone's rotation is added to its rest rotation with x and y negated (Bedrock style: x -90 swings a hanging arm forward),
 * its offset is in pixels with x negated, and its scale scales the bone and everything under it.
 */
public final class BbModel {
    /** One cube: corners, pivot, rest rotation (radians, applied Z then Y then X), and UVs per face (null = no face). */
    public record Cube(Vector3f from, Vector3f to, Vector3f origin, Vector3f rot, float[][] uv) {}

    /** A bone: pivot, rest rotation (radians), cubes and child bones. */
    public static final class Group {
        public final String name;
        public final Vector3f origin;
        public final Vector3f rot;
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Group> children = new ArrayList<>();
        @Nullable public final Group parent;

        Group(String name, Vector3f origin, Vector3f rot, @Nullable Group parent) {
            this.name = name;
            this.origin = origin;
            this.rot = rot;
            this.parent = parent;
        }
    }

    /** A bone's pose for one frame: rotation (radians, animator axes), offset (pixels), scale; any may be null. */
    public interface Posing {
        @Nullable float[] rot(String bone);

        @Nullable float[] pos(String bone);

        @Nullable float[] scale(String bone);

        Posing REST = new Posing() {
            public float[] rot(String bone) {
                return null;
            }

            public float[] pos(String bone) {
                return null;
            }

            public float[] scale(String bone) {
                return null;
            }
        };
    }

    // Face order: north, south, east, west, up, down.
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    public final String name;
    public final List<Group> roots = new ArrayList<>();
    public final Map<String, Group> bones = new HashMap<>();
    public final float texW, texH;
    /** Model extents (pixels): min and max corner of every cube at rest. */
    public final Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);

    private BbModel(String name, float texW, float texH) {
        this.name = name;
        this.texW = texW;
        this.texH = texH;
    }

    public static BbModel parse(String name, JsonObject o) {
        float tw = 16, th = 16;
        if (o.has("resolution")) {
            tw = o.getAsJsonObject("resolution").get("width").getAsFloat();
            th = o.getAsJsonObject("resolution").get("height").getAsFloat();
        }
        BbModel m = new BbModel(name, tw, th);
        Map<String, JsonObject> elements = new HashMap<>();
        for (JsonElement e : o.getAsJsonArray("elements")) {
            JsonObject eo = e.getAsJsonObject();
            elements.put(eo.get("uuid").getAsString(), eo);
        }
        for (JsonElement e : o.getAsJsonArray("outliner")) {
            if (e.isJsonObject()) m.roots.add(m.group(e.getAsJsonObject(), null, elements));
        }
        return m;
    }

    private Group group(JsonObject o, @Nullable Group parent, Map<String, JsonObject> elements) {
        Group g = new Group(o.get("name").getAsString(), vec(o, "origin", 0), rad(vec(o, "rotation", 0)), parent);
        bones.put(g.name, g);
        for (JsonElement c : o.getAsJsonArray("children")) {
            if (c.isJsonObject()) {
                g.children.add(group(c.getAsJsonObject(), g, elements));
            } else {
                JsonObject eo = elements.get(c.getAsString());
                if (eo == null || eo.has("type") && !eo.get("type").getAsString().equals("cube")) continue;
                if (eo.has("visibility") && !eo.get("visibility").getAsBoolean()) continue;
                g.cubes.add(cube(eo));
            }
        }
        return g;
    }

    private Cube cube(JsonObject o) {
        Vector3f from = vec(o, "from", 0), to = vec(o, "to", 0);
        float inflate = o.has("inflate") ? o.get("inflate").getAsFloat() : 0;
        from.sub(inflate, inflate, inflate);
        to.add(inflate, inflate, inflate);
        min.min(from);
        max.max(to);
        float[][] uv = new float[6][];
        JsonObject faces = o.getAsJsonObject("faces");
        for (int i = 0; i < 6; i++) {
            if (faces == null || !faces.has(FACES[i])) continue;
            JsonObject f = faces.getAsJsonObject(FACES[i]);
            if (!f.has("uv") || f.has("texture") && f.get("texture").isJsonNull()) continue;
            JsonArray a = f.getAsJsonArray("uv");
            uv[i] = new float[]{a.get(0).getAsFloat() / texW, a.get(1).getAsFloat() / texH, a.get(2).getAsFloat() / texW, a.get(3).getAsFloat() / texH,
                    f.has("rotation") ? f.get("rotation").getAsFloat() : 0};
        }
        return new Cube(from, to, vec(o, "origin", 0), rad(vec(o, "rotation", 0)), uv);
    }

    private static Vector3f vec(JsonObject o, String key, float def) {
        if (!o.has(key)) return new Vector3f(def);
        JsonArray a = o.getAsJsonArray(key);
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static Vector3f rad(Vector3f deg) {
        return deg.mul((float) (Math.PI / 180));
    }

    // ---------------------------------------------------------------- posing

    /** A bone's local transform for a frame: T(pivot + offset) R(rest + pose) S T(-pivot). */
    private static void apply(Matrix4f m, Group g, Posing pose) {
        float[] p = pose.pos(g.name), r = pose.rot(g.name), s = pose.scale(g.name);
        m.translate(g.origin.x + (p == null ? 0 : -p[0]), g.origin.y + (p == null ? 0 : p[1]), g.origin.z + (p == null ? 0 : p[2]));
        float rx = g.rot.x, ry = g.rot.y, rz = g.rot.z;
        if (r != null) {
            rx -= r[0];
            ry -= r[1];
            rz += r[2];
        }
        if (rx != 0 || ry != 0 || rz != 0) m.rotate(new Quaternionf().rotationZYX(rz, ry, rx));
        if (s != null) m.scale(s[0], s[1], s[2]);
        m.translate(-g.origin.x, -g.origin.y, -g.origin.z);
    }

    /**
     * Where a bone's pivot is, in the model's space (pixels) with the given pose, or null for an unknown bone. The pivot
     * is moved by {@code offset} (pixels, in the bone's own frame) first, for points like a fingertip or an eye.
     */
    @Nullable
    public Vector3f locate(String bone, Posing pose, Vector3f offset) {
        Group g = bones.get(bone);
        if (g == null) return null;
        List<Group> chain = new ArrayList<>();
        for (Group c = g; c != null; c = c.parent) chain.addFirst(c);
        Matrix4f m = new Matrix4f();
        for (Group c : chain) apply(m, c, pose);
        return m.transformPosition(new Vector3f(g.origin).add(offset));
    }

    // ---------------------------------------------------------------- drawing

    /** Draws the model through {@code base} (already placed, turned and scaled to pixels) with the given pose. */
    public void render(PoseStack.Pose base, VertexConsumer buf, Posing pose, int light, int argb) {
        Matrix4f m = new Matrix4f();
        for (Group g : roots) draw(base, buf, g, m, pose, light, argb);
    }

    private void draw(PoseStack.Pose base, VertexConsumer buf, Group g, Matrix4f parent, Posing pose, int light, int argb) {
        Matrix4f m = new Matrix4f(parent);
        apply(m, g, pose);
        for (Cube c : g.cubes) {
            Matrix4f cm = new Matrix4f(m);
            if (c.rot.x != 0 || c.rot.y != 0 || c.rot.z != 0) {
                cm.translate(c.origin).rotate(new Quaternionf().rotationZYX(c.rot.z, c.rot.y, c.rot.x)).translate(-c.origin.x, -c.origin.y, -c.origin.z);
            }
            cube(base, buf, c, cm, light, argb);
        }
        for (Group child : g.children) draw(base, buf, child, m, pose, light, argb);
    }

    private static final Vector3f[] NORMALS = {new Vector3f(0, 0, -1), new Vector3f(0, 0, 1), new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
            new Vector3f(0, 1, 0), new Vector3f(0, -1, 0)};

    private static void cube(PoseStack.Pose base, VertexConsumer buf, Cube c, Matrix4f m, int light, int argb) {
        float x1 = c.from.x, y1 = c.from.y, z1 = c.from.z, x2 = c.to.x, y2 = c.to.y, z2 = c.to.z;
        // Corners per face, in UV order: top-left, top-right, bottom-right, bottom-left (as seen from outside).
        float[][][] quads = {
                {{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}},
                {{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}},
                {{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}},
                {{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}},
                {{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}},
                {{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}},
        };
        Matrix3f nm = new Matrix3f(m).invert().transpose();
        Vector3f v = new Vector3f();
        for (int f = 0; f < 6; f++) {
            float[] uv = c.uv[f];
            if (uv == null) continue;
            float[][] q = quads[f];
            float[][] tex = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
            int turns = Math.floorMod(Math.round(uv[4] / 90f), 4);
            Vector3f n = nm.transform(new Vector3f(NORMALS[f])).normalize();
            for (int k = 0; k < 4; k++) {
                m.transformPosition(q[k][0], q[k][1], q[k][2], v);
                float[] t = tex[(k + turns) % 4];
                buf.addVertex(base, v.x, v.y, v.z).setColor(argb).setUv(t[0], t[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(base, n.x, n.y, n.z);
            }
        }
    }
}
