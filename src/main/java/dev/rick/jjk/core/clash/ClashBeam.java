package dev.rick.jjk.core.clash;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * An ultimate beam that can meet another in a {@link BeamClashSession}: True Love Beam and Every Last Drop. The beam keeps
 * running its own move (its sound, its drawing, its own damage); while a clash holds it, it is cut off at the collision
 * point (no damage or destruction past it), never runs out, and on release either punches through or collapses.
 */
public interface ClashBeam {
    /** Who fires it (Yuta for True Love Beam, though Rika's mouth is its origin). */
    LivingEntity beamOwner();

    ServerLevel beamLevel();

    /** Where it comes from and the way it points (a unit vector). */
    Vec3 beamOrigin();

    Vec3 beamDir();

    double beamRange();

    /** Firing at full strength right now (not charging, not collapsing, not already clashing). */
    boolean beamLive();

    /** "tlb" or "eld": the look the clients give it. */
    String beamKind();

    /**
     * How much of its strength it brings to a clash, 0..1: all of it fresh, less the longer it had already been pouring
     * out before the clash took hold of it (a beam almost spent is overpowered by a fresh one).
     */
    default float beamStrength() {
        return 1f;
    }

    /**
     * A clash has taken hold of it: from now on {@link BeamClashSession#reach(ClashBeam)} is how far it gets, it keeps
     * firing until released, and it is re-aimed down the clash axis.
     */
    void enterClash(BeamClashSession session, Vec3 origin, Vec3 dir);

    /**
     * The clash is over. {@code won}: it punches through and fires on at full length for at least {@code extraTicks}
     * more (doing its damage again); otherwise it collapses at once.
     */
    void leaveClash(boolean won, int extraTicks);
}
