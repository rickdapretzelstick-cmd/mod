package dev.rick.jjk.core.domain.structure;

import dev.rick.jjk.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Two clashing domain structures split between their owners. A plane across the line between the two centers divides
 * the space: everything on A's side is built from A's materials, everything on B's side from B's, so each sorcerer
 * stands in their own domain and the boundary between them is a real wall of blocks that moves as the clash goes.
 *
 * The two structures also merge into one enclosed space: a piece of either shell that ends up inside the other domain's
 * interior is opened up (air), while the outer walls of the union stay sealed.
 *
 * Only blocks the structures themselves placed are ever repainted, and restoration puts back each structure's recorded
 * originals regardless of what they were painted, so the world always comes back exactly.
 */
public final class ClashTerritory {
    private static final byte NONE = 0, SHELL = 1, AIR = 2, FLOOR = 3;

    private final DomainStructure a, b;
    private final Vec3 ca, cb, axis;
    private final double axisLenSqr;
    private final Part pa, pb;
    private double front = Double.NaN;

    /** One structure's positions: where each sits along the axis, and what the merged space wants there. */
    private static final class Part {
        final DomainStructure s;
        final float[] along;
        final byte[] kind;
        /** Which side each position was last painted for (-1 never, 0 A, 1 B). */
        final byte[] side;

        Part(DomainStructure s) {
            this.s = s;
            along = new float[s.positions.length];
            kind = new byte[s.positions.length];
            side = new byte[s.positions.length];
            java.util.Arrays.fill(side, (byte) -1);
        }
    }

    /** Null when the two can't be split (no spec, a structure restored from disk, or the same center). */
    @org.jetbrains.annotations.Nullable
    public static ClashTerritory of(@org.jetbrains.annotations.Nullable DomainStructure a, @org.jetbrains.annotations.Nullable DomainStructure b) {
        if (a == null || b == null || a.spec == null || b.spec == null || a.targets == null || b.targets == null) return null;
        if (!a.isActive() || !b.isActive() || a.center.equals(b.center)) return null;
        return new ClashTerritory(a, b);
    }

    private ClashTerritory(DomainStructure a, DomainStructure b) {
        this.a = a;
        this.b = b;
        ca = Vec3.atCenterOf(a.center);
        cb = Vec3.atCenterOf(b.center);
        axis = cb.subtract(ca);
        axisLenSqr = axis.lengthSqr();
        pa = prepare(a);
        pb = prepare(b);
        if (a.paint == null) a.paint = new BlockState[a.positions.length];
        if (b.paint == null) b.paint = new BlockState[b.positions.length];
    }

    private Part prepare(DomainStructure s) {
        Part p = new Part(s);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.positions.length; i++) {
            m.set(s.positions[i]);
            p.along[i] = (float) along(m);
            byte ka = kind(a, m), kb = kind(b, m);
            // The merged space: a floor anywhere is a floor, open interior beats a wall, and a wall is left only where
            // neither domain's interior reaches.
            p.kind[i] = (byte) Math.max(ka, kb);
        }
        return p;
    }

    private static byte kind(DomainStructure s, BlockPos p) {
        int dx = p.getX() - s.center.getX(), dy = p.getY() - s.center.getY(), dz = p.getZ() - s.center.getZ();
        BlockState t = s.spec.targetAt(dx, dy, dz);
        if (t == null) return NONE;
        if (t == s.spec.shell()) return SHELL;
        if (t.isAir()) return AIR;
        return FLOOR;
    }

    /** Where a block sits along the axis: 0 at A's center, 1 at B's. */
    private double along(BlockPos p) {
        return (Vec3.atCenterOf(p).subtract(ca)).dot(axis) / axisLenSqr;
    }

    /** How far along the axis a distance of {@code blocks} is. */
    public double axisFraction(double blocks) {
        return blocks / Math.sqrt(axisLenSqr);
    }

    public DomainStructure first() {
        return a;
    }

    public DomainStructure second() {
        return b;
    }

    /** Moves the boundary to {@code t} along the axis (0 at A's center, 1 at B's) and repaints whatever changed sides. */
    public void paint(double t) {
        if (t == front) return;
        front = t;
        paint(pa, t);
        paint(pb, t);
    }

    public double front() {
        return front;
    }

    private void paint(Part p, double t) {
        DomainStructure s = p.s;
        if (s.targets == null || s.paint == null) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.positions.length; i++) {
            byte side = (byte) (p.along[i] < t ? 0 : 1);
            if (p.side[i] == side) continue;
            p.side[i] = side;
            m.set(s.positions[i]);
            BlockState want = want(p.kind[i], side == 0 ? a : b, m, s.targets[i]);
            s.paint[i] = want;
            if (s.placed(i)) {
                BlockState now = s.level.getBlockState(m);
                if (now != want && (ModBlocks.isDomainBlock(now) || now.isAir() || now == s.targets[i])) s.level.setBlock(m, want, DomainStructure.FLAGS);
            }
        }
    }

    private static BlockState want(byte kind, DomainStructure owner, BlockPos p, BlockState own) {
        StructureSpec spec = owner.spec;
        return switch (kind) {
            case SHELL -> spec.shell();
            case AIR -> Blocks.AIR.defaultBlockState();
            case FLOOR -> spec.pattern() != null ? spec.pattern().at(p.getX() - owner.center.getX(), p.getZ() - owner.center.getZ(), spec.floor()) : spec.floor();
            default -> own;
        };
    }

    /** The split is called off: each structure goes back to its own design (only what is standing is touched). */
    public void release() {
        for (Part p : new Part[] {pa, pb}) {
            DomainStructure s = p.s;
            if (s.targets == null) continue;
            s.paint = null;
            if (!s.isActive()) continue;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int i = 0; i < s.positions.length; i++) {
                if (!s.placed(i)) continue;
                m.set(s.positions[i]);
                BlockState now = s.level.getBlockState(m);
                if (now != s.targets[i] && (ModBlocks.isDomainBlock(now) || now.isAir())) s.level.setBlock(m, s.targets[i], DomainStructure.FLAGS);
            }
        }
    }
}
