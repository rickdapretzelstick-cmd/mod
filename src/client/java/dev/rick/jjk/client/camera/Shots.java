package dev.rick.jjk.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The shot vocabulary every cinematic camera builds from, each collision-aware: a camera that would sit in a wall is
 * pulled in along its own line of sight until it has a clear view (never closer than {@link #MIN_DISTANCE}).
 *
 * <ul>
 *   <li>{@link #twoShot}: side-on to two subjects, framing both.</li>
 *   <li>{@link #overShoulder}: just behind one subject's shoulder, looking past them at the other.</li>
 *   <li>{@link #lowHero}: low and close, looking up at a subject (power, menace).</li>
 *   <li>{@link #topDown}: from straight above (pinned, thrown, a crater).</li>
 *   <li>{@link #orbit}: round a point at a radius, height and angle.</li>
 * </ul>
 */
public final class Shots {
    public static final double MIN_DISTANCE = 1.2;

    private Shots() {}

    /** The flat unit direction from {@code a} to {@code b} (+z if they stand on the same spot). */
    public static Vec3 flatDir(Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** The horizontal perpendicular of a flat direction ({@code side} +1 or -1 picks which). */
    public static Vec3 perpendicular(Vec3 n, int side) {
        return new Vec3(-n.z * side, 0, n.x * side);
    }

    /** Rotates a flat direction by {@code radians} about the vertical. */
    public static Vec3 rotate(Vec3 v, double radians) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    /** Side-on to {@code a} and {@code b}: far enough back to frame both ({@code spread} widens it), {@code side} picks which side. */
    public static Frame twoShot(Vec3 a, Vec3 b, int side, double spread, double height) {
        Vec3 mid = a.lerp(b, 0.5);
        double span = Math.max(1.5, a.distanceTo(b));
        Vec3 out = perpendicular(flatDir(a, b), side);
        return place(mid, mid.add(out.scale(span * 1.1 + spread)).add(0, height, 0));
    }

    /** Behind {@code subject}'s shoulder ({@code side} +1 right, -1 left), looking past them at {@code target}. */
    public static Frame overShoulder(Vec3 subject, Vec3 target, int side, double back) {
        Vec3 n = flatDir(subject, target);
        Vec3 want = subject.subtract(n.scale(back)).add(perpendicular(n, side).scale(0.75)).add(0, 0.35, 0);
        Frame f = place(subject, want);
        return new Frame(f.pos(), target);
    }

    /**
     * Low and close in front of {@code feet} (a subject standing there, facing {@code facing}), the camera near the ground
     * looking up at its chest: it towers.
     */
    public static Frame lowHero(Vec3 feet, Vec3 facing, double distance, double subjectHeight) {
        Vec3 n = flatDir(feet, facing);
        Vec3 look = feet.add(0, subjectHeight * 0.55, 0);
        Frame f = place(look, feet.add(n.scale(distance)).add(perpendicular(n, 1).scale(distance * 0.35)).add(0, 0.3, 0));
        return new Frame(f.pos(), look);
    }

    /** From straight above {@code subject} (nudged so the view has a direction). */
    public static Frame topDown(Vec3 subject, double height, Vec3 up) {
        Frame f = place(subject, subject.add(0, height, 0).add(up.multiply(1, 0, 1).normalize().scale(0.01)));
        return new Frame(f.pos(), subject);
    }

    /** Round {@code center} at {@code radius}, {@code height} above it, at {@code radians} from +z. */
    public static Frame orbit(Vec3 center, double radius, double height, double radians) {
        Vec3 dir = new Vec3(Math.sin(radians), 0, Math.cos(radians));
        return place(center, center.add(dir.scale(radius)).add(0, height, 0));
    }

    /** A camera at {@code want} looking at {@code look}, pulled in toward it until the view is clear. */
    public static Frame place(Vec3 look, Vec3 want) {
        return new Frame(clip(look, want), look);
    }

    /** {@code want}, or pulled in along the line from {@code from} so it stays out of walls (half a block off them). */
    public static Vec3 clip(Vec3 from, Vec3 want) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return want;
        var hit = mc.level.clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return want;
        Vec3 d = want.subtract(from);
        double len = d.length();
        double ok = Math.max(MIN_DISTANCE, hit.getLocation().distanceTo(from) - 0.5);
        Vec3 pos = from.add(d.scale(Math.min(1, ok / Math.max(1e-6, len))));
        if (!clear(pos)) pos = pos.add(0, 1, 0);
        return pos;
    }

    /** Nothing solid at {@code pos}. */
    public static boolean clear(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return true;
        BlockPos b = BlockPos.containing(pos);
        return mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty();
    }

    /** How far the view is clear from {@code from} toward {@code to}. */
    public static double clearance(Vec3 from, Vec3 to) {
        return clip(from, to).distanceTo(from);
    }

    /** Smoothstep. */
    public static double ease(double k) {
        k = Math.max(0, Math.min(1, k));
        return k * k * (3 - 2 * k);
    }
}
