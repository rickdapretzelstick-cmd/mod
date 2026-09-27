package dev.rick.jjk.core.hitbox;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Implementations of {@link HitShape}. */
public final class Shapes {
    private Shapes() {}

    static Vec3 closestPoint(AABB box, Vec3 p) {
        return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
    }

    static double distSqToBox(AABB box, Vec3 p) {
        return closestPoint(box, p).distanceToSqr(p);
    }

    public record Sphere(Vec3 center, double radius) implements HitShape {
        @Override
        public AABB bounds() {
            return new AABB(center.subtract(radius, radius, radius), center.add(radius, radius, radius));
        }

        @Override
        public boolean intersects(AABB box) {
            return distSqToBox(box, center) <= radius * radius;
        }
    }

    public record Box(AABB box) implements HitShape {
        @Override
        public AABB bounds() {
            return box;
        }

        @Override
        public boolean intersects(AABB other) {
            return box.intersects(other);
        }

        @Override
        public Vec3 center() {
            return box.getCenter();
        }
    }

    /** Oriented bounding box tested with the separating axis theorem. */
    public record OrientedBox(Vec3 center, Vec3[] axes, double[] half) implements HitShape {
        public static OrientedBox forward(Vec3 origin, Vec3 forward, double length, double width, double height) {
            Vec3 f = forward.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : forward.normalize();
            Vec3 worldUp = Math.abs(f.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = f.cross(worldUp).normalize();
            Vec3 up = right.cross(f).normalize();
            Vec3 c = origin.add(f.scale(length / 2));
            return new OrientedBox(c, new Vec3[]{right, up, f}, new double[]{width / 2, height / 2, length / 2});
        }

        @Override
        public AABB bounds() {
            double ex = 0, ey = 0, ez = 0;
            for (int i = 0; i < 3; i++) {
                ex += Math.abs(axes[i].x) * half[i];
                ey += Math.abs(axes[i].y) * half[i];
                ez += Math.abs(axes[i].z) * half[i];
            }
            return new AABB(center.x - ex, center.y - ey, center.z - ez, center.x + ex, center.y + ey, center.z + ez);
        }

        @Override
        public boolean intersects(AABB box) {
            Vec3 bc = box.getCenter();
            double[] bh = {box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2};
            Vec3[] bAxes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
            Vec3 t = bc.subtract(center);
            // Face axes of both boxes, then the 9 edge cross products.
            for (Vec3 a : axes) if (separated(a, t, bAxes, bh)) return false;
            for (Vec3 a : bAxes) if (separated(a, t, bAxes, bh)) return false;
            for (Vec3 a : axes) {
                for (Vec3 b : bAxes) {
                    Vec3 c = a.cross(b);
                    if (c.lengthSqr() < 1e-8) continue;
                    if (separated(c.normalize(), t, bAxes, bh)) return false;
                }
            }
            return true;
        }

        private boolean separated(Vec3 axis, Vec3 t, Vec3[] bAxes, double[] bh) {
            double ra = 0, rb = 0;
            for (int i = 0; i < 3; i++) {
                ra += Math.abs(axes[i].dot(axis)) * half[i];
                rb += Math.abs(bAxes[i].dot(axis)) * bh[i];
            }
            return Math.abs(t.dot(axis)) > ra + rb;
        }
    }

    /** Swept sphere between two points; with a small radius it doubles as a line/ray hitbox. */
    public record Capsule(Vec3 a, Vec3 b, double radius) implements HitShape {
        @Override
        public AABB bounds() {
            return new AABB(Math.min(a.x, b.x) - radius, Math.min(a.y, b.y) - radius, Math.min(a.z, b.z) - radius,
                    Math.max(a.x, b.x) + radius, Math.max(a.y, b.y) + radius, Math.max(a.z, b.z) + radius);
        }

        @Override
        public boolean intersects(AABB box) {
            return segmentDistSq(box) <= radius * radius;
        }

        /** Squared distance between the segment and the box. The distance along a segment to a convex set is convex, so ternary search converges on the true minimum. */
        double segmentDistSq(AABB box) {
            Vec3 d = b.subtract(a);
            double lo = 0, hi = 1;
            for (int i = 0; i < 40; i++) {
                double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
                if (distSqToBox(box, a.add(d.scale(m1))) <= distSqToBox(box, a.add(d.scale(m2)))) hi = m2;
                else lo = m1;
            }
            double best = distSqToBox(box, a.add(d.scale((lo + hi) / 2)));
            return Math.min(best, Math.min(distSqToBox(box, a), distSqToBox(box, b)));
        }

        @Override
        public Vec3 center() {
            return a.add(b).scale(0.5);
        }
    }

    public record Cone(Vec3 apex, Vec3 direction, double length, double halfAngle) implements HitShape {
        public Cone {
            direction = direction.normalize();
        }

        @Override
        public AABB bounds() {
            Vec3 end = apex.add(direction.scale(length));
            double r = length * Math.tan(Math.min(halfAngle, Math.toRadians(89)));
            r = Math.min(r, length);
            return new AABB(apex, end).inflate(r);
        }

        boolean contains(Vec3 p) {
            Vec3 v = p.subtract(apex);
            double along = v.dot(direction);
            if (along < 0 || along > length) return false;
            double lenSq = v.lengthSqr();
            if (lenSq < 1e-8) return true;
            return along / Math.sqrt(lenSq) >= Math.cos(halfAngle);
        }

        @Override
        public boolean intersects(AABB box) {
            if (box.contains(apex)) return true;
            if (contains(closestPoint(box, apex)) || contains(box.getCenter())) return true;
            // Points on the box nearest to samples along the cone axis cover boxes the axis passes close to.
            for (int i = 1; i <= 8; i++) {
                Vec3 axisPoint = apex.add(direction.scale(length * i / 8));
                if (contains(closestPoint(box, axisPoint))) return true;
            }
            for (int i = 0; i < 8; i++) {
                Vec3 corner = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
                if (contains(corner)) return true;
            }
            return false;
        }

        @Override
        public Vec3 center() {
            return apex.add(direction.scale(length / 2));
        }
    }
}
