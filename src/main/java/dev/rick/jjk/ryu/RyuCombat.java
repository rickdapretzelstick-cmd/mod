package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.RyuPayload;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.registry.ModDamageTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * True Cannon's shared pieces: his Overheat meter, the rays his Cursed Energy Discharge fires (from the cannon of his
 * pompadour, or his fingertip), and his strikes and finishers.
 */
public final class RyuCombat {
    private RyuCombat() {}

    public static JJKConfig.Ryu cfg() {
        return JJKConfig.get().ryu;
    }

    // --- Overheat ---

    public static float heat(LivingEntity e) {
        return RyuState.of(e).heat;
    }

    /**
     * Adds Overheat (Decadence holds it at 100 while awakened). At 100 the top of his head starts smoking and his
     * discharges are shut off until he cools down (Restyle).
     */
    public static void addHeat(LivingEntity e, float amount) {
        RyuState s = RyuState.of(e);
        boolean was = s.overheated();
        s.heat = Mth.clamp(s.heat + amount, 0f, 100f);
        if (!was && s.overheated() && e.level() instanceof ServerLevel level) {
            Fx.play(level, "ryu_overheat", e.getEyePosition().add(0, 0.4, 0), Vec3.ZERO, 1f, e.getId());
        }
        sync(e);
    }

    public static void setHeat(LivingEntity e, float value) {
        RyuState.of(e).heat = Mth.clamp(value, 0f, 100f);
        sync(e);
    }

    /** Whether his discharges (Granite Blast, Appetizer's blasts, Every Last Drop) can fire. */
    public static boolean canDischarge(LivingEntity e) {
        return !RyuState.of(e).overheated();
    }

    public static void sync(LivingEntity e) {
        RyuState s = RyuState.of(e);
        AbilityCaster c = Casters.getOrNull(e);
        boolean awakened = c != null && c.isAwakened();
        if (!(e instanceof ServerPlayer sp) || Math.abs(s.sentHeat - s.heat) < 0.01f && s.sentAwakened == awakened) return;
        s.sentHeat = s.heat;
        s.sentAwakened = awakened;
        ServerPlayNetworking.send(sp, new RyuPayload(s.heat, awakened));
    }

    // --- Rays ---

    /** The cannon: just above his forehead, out in front. */
    public static Vec3 cannon(LivingEntity user) {
        return user.getEyePosition().add(0, 0.3, 0).add(user.getLookAngle().scale(0.45));
    }

    /** His fingertip, arm out (Every Last Drop). */
    public static Vec3 fingertip(LivingEntity user, Vec3 dir) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        flat = flat.lengthSqr() < 1e-6 ? HakariCombat.flat(user) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return user.position().add(0, user.getBbHeight() * 0.78, 0).add(dir.normalize().scale(0.85)).add(right.scale(-0.32));
    }

    /** One body a ray met, and how far along it. */
    public record RayHit(LivingEntity target, double along) {}

    /**
     * What a ray from {@code from} along {@code dir} meets, nearest first, as far as {@code range} or the first solid
     * block (unless {@code throughBlocks}); bodies within {@code width} of its line count.
     */
    public static List<RayHit> ray(LivingEntity user, Vec3 from, Vec3 dir, double range, double width, boolean throughBlocks) {
        Vec3 d = dir.normalize();
        double reach = range;
        if (!throughBlocks) {
            var clip = user.level().clip(new ClipContext(from, from.add(d.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            if (clip.getType() != Type.MISS) reach = clip.getLocation().distanceTo(from);
        }
        Vec3 to = from.add(d.scale(reach));
        List<RayHit> out = new ArrayList<>();
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width + 1),
                e -> Targeting.canTarget(user, e))) {
            AABB box = e.getBoundingBox().inflate(width);
            var hit = box.clip(from, to);
            if (hit.isEmpty() && !box.contains(from)) continue;
            double along = hit.map(h -> h.distanceTo(from)).orElse(0.0);
            out.add(new RayHit(e, along));
        }
        out.sort(Comparator.comparingDouble(RayHit::along));
        return out;
    }

    /** Where a ray ends: the first solid block, or its full range. */
    public static Vec3 rayEnd(LivingEntity user, Vec3 from, Vec3 dir, double range) {
        Vec3 d = dir.normalize();
        var clip = user.level().clip(new ClipContext(from, from.add(d.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return clip.getType() == Type.MISS ? from.add(d.scale(range)) : clip.getLocation();
    }

    // --- Sound ---

    /**
     * One of his own JJS sounds at a beat of a move that has no effect of its own (a startup, a dash, a wind-up),
     * heard by everyone near through the effect channel (so the client's volume setting applies).
     */
    public static void sfx(LivingEntity user, String sound, float volume) {
        if (user.level() instanceof ServerLevel level) Fx.play(level, "sfx:" + sound, user.getEyePosition(), Vec3.ZERO, volume, user.getId());
    }

    /** The effect of a landed blow that plays {@code sound}: heavy ("ryuh:") or light ("ryul:") visuals. */
    public static String hitFx(String sound, boolean heavy) {
        return (heavy ? "ryuh:" : "ryul:") + sound;
    }

    // --- Strikes ---

    public static Hit.Builder strike(LivingEntity user, String id, float damage, boolean unblockable) {
        Hit.Builder b = Hit.builder(user, id).type(ModDamageTypes.MELEE).damage(damage).tag(AttackTag.MELEE).origin(user.getEyePosition());
        if (unblockable) b.tag(AttackTag.UNBLOCKABLE);
        return b;
    }

    /** A discharge: a cursed-energy blast, a projectile (JJS "Bullet") unless {@code beam}. */
    public static Hit.Builder blast(LivingEntity user, String id, float damage, Vec3 from) {
        return Hit.builder(user, id).type(ModDamageTypes.TECHNIQUE).damage(damage).tag(AttackTag.TECHNIQUE, AttackTag.PROJECTILE).origin(from);
    }

    public static boolean finishable(LivingEntity target) {
        return target.isAlive() && target.getHealth() <= target.getMaxHealth() * cfg().finisherThreshold;
    }

    public static HitResult execute(LivingEntity user, LivingEntity target, String id, String fx) {
        return HakariCombat.execute(user, target, id, fx);
    }

    public static boolean ragdolled(LivingEntity e) {
        return dev.rick.jjk.gojo.GojoCombat.ragdolled(e);
    }

    /** Self damage some of his awakened blows cost him (it can't kill, unlike a feint). */
    public static void selfDamage(LivingEntity user, float amount) {
        if (amount <= 0) return;
        user.setHealth(Math.max(1f, user.getHealth() - amount));
    }
}
