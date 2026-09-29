package dev.rick.jjk.client.anim.rig;

import dev.rick.jjk.client.mixin.rig.CubeDefinitionAccessor;
import dev.rick.jjk.client.mixin.rig.CubeDeformationAccessor;
import dev.rick.jjk.client.mixin.rig.PartDefinitionAccessor;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cuts the player's 12-pixel limbs and torso into jointed segments, so the skeleton has elbows, wrists, knees, ankles
 * and a waist. Every piece keeps exactly the texture it had, and at rest the model is identical to vanilla.
 *
 * <p>An arm or leg becomes {@code upper (6px) -> jjk_lower (4px) -> jjk_end (2px)}: the upper piece stays on the
 * original part, the next two hang off it as children pivoting at the joint's centre. The torso becomes the chest
 * (8px, the original part) and {@code jjk_lower}, the 4px abdomen that the skeleton drives from the hips. Overlays
 * (sleeves, pants, jacket) are cut the same way and their pieces ride the matching segment under the overlay's name.
 */
public final class MeshSplit {
    public static final String LOWER = "jjk_lower", END = "jjk_end";
    static final float[] LIMB = {6, 4, 2};
    static final float[] TORSO = {8, 4};
    private static final Set<String> LIMBS = Set.of("right_arm", "left_arm", "right_leg", "left_leg");

    private MeshSplit() {}

    public static void split(MeshDefinition mesh) {
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = acc(root).jjk$children();
        for (String name : List.copyOf(parts.keySet())) {
            if (LIMBS.contains(name)) parts.put(name, splitPart(parts.get(name), LIMB));
            else if (name.equals("body")) parts.put(name, splitPart(parts.get(name), TORSO));
        }
    }

    private static PartDefinitionAccessor acc(PartDefinition p) {
        return (PartDefinitionAccessor) p;
    }

    private static CubeDefinitionAccessor acc(CubeDefinition c) {
        return (CubeDefinitionAccessor) (Object) c;
    }

    /** A copy of a part with other cubes (part cube lists are immutable), keeping its pose and children. */
    private static PartDefinition withCubes(PartDefinition part, List<CubeDefinition> cubes) {
        PartDefinition copy = PartDefinitionAccessor.jjk$create(new ArrayList<>(cubes), acc(part).jjk$pose());
        acc(copy).jjk$children().putAll(acc(part).jjk$children());
        return copy;
    }

    /** Splits one part's full-length cubes, and those of its overlay children, at the joints. Returns the new part. */
    private static PartDefinition splitPart(PartDefinition original, float[] segs) {
        PartDefinitionAccessor a0 = acc(original);
        if (a0.jjk$children().containsKey(LOWER)) return original;
        CubeDefinition main = null;
        for (CubeDefinition c : a0.jjk$cubes()) if (spans(c, segs)) main = c;
        if (main == null) return original;
        // Joints at the centre of the limb's cross-section.
        CubeDefinitionAccessor m = acc(main);
        float jx = m.jjk$origin().x() + m.jjk$dimensions().x() / 2, jz = m.jjk$origin().z() + m.jjk$dimensions().z() / 2;
        float[] jy = new float[segs.length];
        jy[0] = m.jjk$origin().y();
        for (int i = 1; i < segs.length; i++) jy[i] = jy[i - 1] + segs[i - 1];

        // Pieces per segment, in the part's own space.
        List<List<CubeDefinition>> own = cut(a0.jjk$cubes(), segs, 0, 0, 0);
        PartDefinition part = withCubes(original, own.getFirst());
        PartDefinitionAccessor a = acc(part);
        PartDefinition[] seg = new PartDefinition[segs.length];
        seg[0] = part;
        for (int i = 1; i < segs.length; i++) {
            // Each joint's pose is relative to the previous segment's joint.
            float px = i == 1 ? jx : 0, py = i == 1 ? jy[1] : segs[i - 1], pz = i == 1 ? jz : 0;
            seg[i] = PartDefinitionAccessor.jjk$create(new ArrayList<>(shift(own.get(i), -jx, -jy[i], -jz)), PartPose.offset(px, py, pz));
            acc(seg[i - 1]).jjk$children().put(i == 1 ? LOWER : END, seg[i]);
        }

        // Overlays: the first piece stays where it is, the rest ride the matching segment.
        for (var e : List.copyOf(a.jjk$children().entrySet())) {
            String name = e.getKey();
            if (name.startsWith("jjk_")) continue;
            PartDefinitionAccessor o = acc(e.getValue());
            PartPose op = o.jjk$pose();
            if (op.xRot() != 0 || op.yRot() != 0 || op.zRot() != 0) continue;
            boolean any = false;
            for (CubeDefinition c : o.jjk$cubes()) any |= spans(c, segs, jy[0] - op.y());
            if (!any) continue;
            List<List<CubeDefinition>> pieces = cut(o.jjk$cubes(), segs, op.x(), op.y(), op.z(), jy[0]);
            a.jjk$children().put(name, withCubes(e.getValue(), shift(pieces.getFirst(), -op.x(), -op.y(), -op.z())));
            for (int i = 1; i < segs.length; i++) {
                List<CubeDefinition> p = shift(pieces.get(i), -jx, -jy[i], -jz);
                acc(seg[i]).jjk$children().put(name, PartDefinitionAccessor.jjk$create(new ArrayList<>(p), PartPose.ZERO));
            }
        }
        return part;
    }

    private static boolean spans(CubeDefinition c, float[] segs) {
        return spans(c, segs, acc(c).jjk$origin().y());
    }

    private static boolean spans(CubeDefinition c, float[] segs, float top) {
        float total = 0;
        for (float s : segs) total += s;
        CubeDefinitionAccessor a = acc(c);
        return Math.abs(a.jjk$dimensions().y() - total) < 1e-3 && Math.abs(a.jjk$origin().y() - top) < 1e-3;
    }

    private static List<List<CubeDefinition>> cut(List<CubeDefinition> cubes, float[] segs, float ox, float oy, float oz) {
        return cut(cubes, segs, ox, oy, oz, Float.NaN);
    }

    /**
     * Cuts each cube spanning the whole length into one piece per segment, offset by (ox, oy, oz) into the parent's
     * space. Cubes that don't span it stay whole on the first segment.
     */
    private static List<List<CubeDefinition>> cut(List<CubeDefinition> cubes, float[] segs, float ox, float oy, float oz, float top) {
        List<List<CubeDefinition>> out = new ArrayList<>();
        for (int i = 0; i < segs.length; i++) out.add(new ArrayList<>());
        for (CubeDefinition c : cubes) {
            CubeDefinitionAccessor a = acc(c);
            boolean spans = Float.isNaN(top) ? spans(c, segs) : spans(c, segs, top - oy);
            if (!spans) {
                out.getFirst().add(shift(c, ox, oy, oz));
                continue;
            }
            CubeDeformationAccessor g = (CubeDeformationAccessor) a.jjk$grow();
            float gy = g.jjk$y();
            float x = a.jjk$origin().x() + ox, z = a.jjk$origin().z() + oz;
            float w = a.jjk$dimensions().x(), d = a.jjk$dimensions().z();
            float u = a.jjk$texCoord().u(), v = a.jjk$texCoord().v();
            // Inflation only on the outer ends, so neighbouring pieces meet exactly instead of overlapping: an end
            // piece grows by half as much both ways and moves outward by that half. Texture sizes stay true.
            CubeDeformation flat = new CubeDeformation(g.jjk$x(), 0, g.jjk$z());
            float y = a.jjk$origin().y() + oy, start = 0;
            for (int i = 0; i < segs.length; i++) {
                boolean first = i == 0, last = i == segs.length - 1;
                float h = segs[i], y0 = y + start - (first ? gy / 2 : 0) + (last ? gy / 2 : 0);
                CubeDeformation grow = first || last ? new CubeDeformation(g.jjk$x(), gy / 2, g.jjk$z()) : flat;
                Set<Direction> faces = EnumSet.copyOf(a.jjk$faces());
                // The far end's cap is drawn by its own sliver below, with the original texture.
                if (last && segs.length > 1) faces.remove(Direction.DOWN);
                if (!faces.isEmpty()) {
                    out.get(i).add(new CubeDefinition(a.jjk$comment(), u, v + start, x, y0, z, w, h, d, grow, a.jjk$mirror(),
                            a.jjk$texScale().u(), a.jjk$texScale().v(), faces));
                }
                if (last && segs.length > 1 && a.jjk$faces().contains(Direction.DOWN)) {
                    out.get(i).add(new CubeDefinition(a.jjk$comment(), u, v, x, y + start + h + gy, z, w, 0, d, flat, a.jjk$mirror(),
                            a.jjk$texScale().u(), a.jjk$texScale().v(), EnumSet.of(Direction.DOWN)));
                }
                start += segs[i];
            }
        }
        return out;
    }

    private static List<CubeDefinition> shift(List<CubeDefinition> cubes, float dx, float dy, float dz) {
        List<CubeDefinition> out = new ArrayList<>();
        for (CubeDefinition c : cubes) out.add(shift(c, dx, dy, dz));
        return out;
    }

    private static CubeDefinition shift(CubeDefinition c, float dx, float dy, float dz) {
        if (dx == 0 && dy == 0 && dz == 0) return c;
        CubeDefinitionAccessor a = acc(c);
        return new CubeDefinition(a.jjk$comment(), a.jjk$texCoord().u(), a.jjk$texCoord().v(),
                a.jjk$origin().x() + dx, a.jjk$origin().y() + dy, a.jjk$origin().z() + dz,
                a.jjk$dimensions().x(), a.jjk$dimensions().y(), a.jjk$dimensions().z(),
                a.jjk$grow(), a.jjk$mirror(), a.jjk$texScale().u(), a.jjk$texScale().v(), a.jjk$faces());
    }
}
