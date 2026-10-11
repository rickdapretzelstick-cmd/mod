package dev.rick.jjk.client.camera;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** One camera placement: where it is and what it looks at. */
public record Frame(Vec3 pos, Vec3 look) {
    public float yaw() {
        Vec3 d = look.subtract(pos);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public float pitch() {
        Vec3 d = look.subtract(pos);
        return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    /** Eased between two frames ({@code k} 0..1), position and aim separately (so a pan reads as a pan). */
    public Frame lerp(Frame to, double k) {
        return new Frame(pos.lerp(to.pos, k), look.lerp(to.look, k));
    }

    /** {x, y, z, yaw, pitch}, blended in from the vanilla view by {@code k} (0 = vanilla, 1 = this frame). */
    public double[] blendFrom(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch, double k) {
        Vec3 p = vanillaPos.lerp(pos, k);
        return new double[] {p.x, p.y, p.z, vanillaYaw + Mth.wrapDegrees(yaw() - vanillaYaw) * k, Mth.lerp(k, vanillaPitch, pitch())};
    }

    public double[] array() {
        return new double[] {pos.x, pos.y, pos.z, yaw(), pitch()};
    }
}
