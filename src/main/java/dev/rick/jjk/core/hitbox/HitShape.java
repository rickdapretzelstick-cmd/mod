package dev.rick.jjk.core.hitbox;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A 3D volume that can be tested against entity bounding boxes. */
public interface HitShape {
    /** Axis-aligned bounds used for the broad-phase entity lookup. */
    AABB bounds();

    /** Exact (narrow-phase) overlap test against a bounding box. */
    boolean intersects(AABB box);

    /** Representative point used for line-of-sight checks and effects. */
    Vec3 center();

    static HitShape sphere(Vec3 center, double radius) {
        return new Shapes.Sphere(center, radius);
    }

    static HitShape box(AABB box) {
        return new Shapes.Box(box);
    }

    /** Box oriented along {@code forward}, extending {@code length} blocks forward from {@code origin}. */
    static HitShape orientedBox(Vec3 origin, Vec3 forward, double length, double width, double height) {
        return Shapes.OrientedBox.forward(origin, forward, length, width, height);
    }

    static HitShape capsule(Vec3 a, Vec3 b, double radius) {
        return new Shapes.Capsule(a, b, radius);
    }

    static HitShape line(Vec3 a, Vec3 b) {
        return new Shapes.Capsule(a, b, 0.15);
    }

    static HitShape cone(Vec3 apex, Vec3 direction, double length, double halfAngleDegrees) {
        return new Shapes.Cone(apex, direction, length, Math.toRadians(halfAngleDegrees));
    }
}
