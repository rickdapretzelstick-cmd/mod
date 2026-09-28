package dev.rick.jjk.core.domain.structure;

/**
 * When each part of a domain structure is built, as a fraction (0..1) of the formation time. Shared by the server
 * (which places the real blocks on this schedule) and the client (which draws the energy along the newest edge), so
 * the two always agree.
 *
 * The sequence radiates from the sorcerer: the ground at their feet first, spreading outward to the outer ring; then
 * the wall rises from that ring and curves inward until the ceiling closes; then the underground half seals beneath;
 * a final pulse confirms the seal.
 */
public final class DomainFormation {
    /** The ground has spread to the outer ring. */
    public static final float GROUND_END = 0.35f;
    /** The walls have curved over and the ceiling is closed. */
    public static final float CEILING_END = 0.80f;
    /** The underground half is sealed. */
    public static final float SEALED = 0.95f;

    private DomainFormation() {}

    /**
     * Build time of the block at offset (dx, dy, dz) from the domain's anchor (the caster's feet; dy = -1 is the
     * floor under them).
     */
    public static float time(int dx, int dy, int dz, double radius, int thickness) {
        double h = Math.sqrt(dx * dx + dz * dz);
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double r = Math.max(1, radius);
        boolean shell = d > radius + 0.5 - thickness;
        if (!shell) {
            // Ground and the space above it clear outward from the feet, just ahead of the rising wall.
            float ground = (float) (GROUND_END * Math.min(1, h / r));
            return dy <= -1 ? ground : Math.min(CEILING_END - 0.02f, ground + (float) (0.12 * dy / r));
        }
        // Shell: angle above (or below) the ground plane decides when it closes.
        double elevation = Math.atan2(dy + 0.5, Math.max(0.01, h)) / (Math.PI / 2); // -1 (straight down) .. 1 (top)
        if (elevation >= 0) return GROUND_END + (float) ((CEILING_END - GROUND_END) * elevation);
        return CEILING_END + (float) ((SEALED - CEILING_END) * Math.min(1, -elevation));
    }

    /** Height angle (radians above the ground plane) of the wall's newest edge at formation progress {@code p}. */
    public static double wallAngle(float p) {
        if (p <= GROUND_END) return 0;
        if (p >= CEILING_END) return Math.PI / 2;
        return (p - GROUND_END) / (CEILING_END - GROUND_END) * Math.PI / 2;
    }

    /** Depth angle (radians below the ground plane) of the underground seal's newest edge at progress {@code p}. */
    public static double underAngle(float p) {
        if (p <= CEILING_END) return 0;
        if (p >= SEALED) return Math.PI / 2;
        return (p - CEILING_END) / (SEALED - CEILING_END) * Math.PI / 2;
    }
}
