package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle;
import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.FxPayload;
import dev.rick.jjk.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side effect library. The server sends compact effect events (id, position, direction, scale); this class
 * turns them into particles, sounds and screen feedback, scaled by the player's particle quality setting.
 */
public final class ClientFx {
    // Palette (r, g, b).
    public static final float[] WHITE = {1f, 1f, 1f};
    public static final float[] BLUE = {0.3f, 0.6f, 1f};
    public static final float[] BLUE_LIGHT = {0.75f, 0.9f, 1f};
    public static final float[] RED = {1f, 0.18f, 0.12f};
    public static final float[] ORANGE = {1f, 0.55f, 0.2f};
    public static final float[] PURPLE = {0.62f, 0.28f, 1f};
    public static final float[] PURPLE_LIGHT = {0.88f, 0.7f, 1f};
    public static final float[] GOLD = {1f, 0.85f, 0.4f};
    public static final float[] GREY = {0.6f, 0.6f, 0.65f};
    public static final float[] DARK = {0.08f, 0.08f, 0.14f};

    private static final RandomSource RNG = RandomSource.create();
    private static int trailCounter;

    private ClientFx() {}

    public static void handle(FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 pos = p.pos();
        Vec3 dir = p.dir();
        float s = p.scale();
        boolean mine = mc.player != null && p.entityId() == mc.player.getId();
        switch (p.id()) {
            // --- melee ---
            case "swing" -> {
                sound(s > 1.2f ? "swing_heavy" : "swing", pos, 0.6f, 0.9f + RNG.nextFloat() * 0.3f);
                arc(level, pos, dir, 6, WHITE, 0.35f);
            }
            case "hit_light" -> {
                sound("hit_light", pos, 0.9f, 0.9f + RNG.nextFloat() * 0.25f);
                flashAt(level, pos, 0.9f * s, WHITE, 4);
                ringBurst(level, pos, 0.2f, 1.4f * s, WHITE, 0.6f, 6);
                burst(level, pos, q(7), 0.35, Sprite.SPARK, WHITE, 0.12f, 6);
                victimFeedback(p, 0.25f);
            }
            case "hit_heavy" -> {
                sound("hit_heavy", pos, 1f, 0.9f + RNG.nextFloat() * 0.15f);
                flashAt(level, pos, 1.6f * s, WHITE, 5);
                ringBurst(level, pos, 0.3f, 2.6f * s, BLUE_LIGHT, 0.7f, 8);
                burst(level, pos, q(16), 0.6, Sprite.SPARK, WHITE, 0.16f, 8);
                burst(level, pos, q(5), 0.15, Sprite.SMOKE, GREY, 0.5f, 14);
                victimFeedback(p, 0.55f);
            }
            case "hit_launch" -> {
                sound("hit_heavy", pos, 1f, 1.2f);
                flashAt(level, pos, 1.3f * s, WHITE, 5);
                for (int i = 0; i < q(14); i++) {
                    add(level, pos, new Vec3(gauss(0.08), 0.4 + RNG.nextDouble() * 0.5, gauss(0.08)), Sprite.SPARK, WHITE, 0.9f, 0.15f, 0.05f, 8);
                }
                ring3d(level, pos, new Vec3(0, 1, 0), 0.4, q(16), 0.35, BLUE_LIGHT, 0.14f, 8);
                victimFeedback(p, 0.5f);
            }
            case "hit_slam" -> {
                sound("hit_slam", pos, 1f, 1f);
                flashAt(level, pos, 1.5f * s, WHITE, 5);
                for (int i = 0; i < q(14); i++) {
                    add(level, pos, new Vec3(gauss(0.1), -0.5 - RNG.nextDouble() * 0.5, gauss(0.1)), Sprite.SPARK, WHITE, 0.9f, 0.16f, 0.05f, 8);
                }
                victimFeedback(p, 0.6f);
            }
            case "heavy_charge" -> {
                sound("heavy_charge", pos, 0.7f, 1f);
                Entity e = level.getEntity(p.entityId());
                Vec3 at = e != null ? e.position().add(0, 1.2, 0) : pos.add(0, 1.2, 0);
                implode(level, at, 1.5, q(12), BLUE_LIGHT, 0.12f, 10);
            }
            case "block" -> {
                sound("block", pos, 0.9f, 1f + RNG.nextFloat() * 0.1f);
                ring3d(level, pos, dir, 0.3, q(14), 0.18, BLUE_LIGHT, 0.12f, 7);
                burst(level, pos, q(6), 0.25, Sprite.SPARK, WHITE, 0.1f, 5);
            }
            case "parry" -> {
                sound("parry", pos, 1f, 1f);
                flashAt(level, pos, 2.2f, GOLD, 6);
                ringBurst(level, pos, 0.2f, 2.8f, GOLD, 0.8f, 9);
                burst(level, pos, q(18), 0.5, Sprite.STAR, GOLD, 0.18f, 10);
                if (mine) ScreenEffects.flash(0x40FFE9A0, 5);
            }
            case "guard_break" -> {
                sound("guard_break", pos, 1f, 1f);
                flashAt(level, pos, 2f, WHITE, 6);
                burst(level, pos, q(22), 0.45, Sprite.SHARD, BLUE_LIGHT, 0.14f, 16).forEach(x -> x.gravity(0.6f).physics());
                if (mine) ScreenEffects.shake(0.6f, 10);
            }
            case "evade" -> {
                sound("dash", pos, 0.4f, 1.6f);
                burst(level, pos, q(8), 0.08, Sprite.SMOKE, GREY, 0.45f, 10);
            }
            case "dash" -> {
                sound("dash", pos, 0.7f, 1f + RNG.nextFloat() * 0.2f);
                for (int i = 0; i < q(10); i++) {
                    Vec3 at = pos.add(gauss(0.3), gauss(0.5), gauss(0.3)).subtract(dir.scale(RNG.nextDouble()));
                    add(level, at, dir.scale(-0.08), Sprite.SMOKE, GREY, 0.5f, 0.35f, 0.6f, 10);
                }
            }
            case "ground_impact" -> {
                sound("ground_impact", pos, 1f, 1f);
                BlockState ground = level.getBlockState(BlockPos.containing(pos).below());
                if (!ground.isAir()) {
                    for (int i = 0; i < q(24); i++) {
                        double a = RNG.nextDouble() * Mth.TWO_PI;
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), pos.x, pos.y + 0.1, pos.z,
                                Math.cos(a) * 0.3, 0.2 + RNG.nextDouble() * 0.3, Math.sin(a) * 0.3);
                    }
                }
                ring3d(level, pos.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, q(20), 0.4, GREY, 0.35f, 10);
            }
            case "no_energy" -> {
                if (mine) sound("no_energy", pos, 0.6f, 1f);
                burst(level, pos, q(4), 0.05, Sprite.SMOKE, GREY, 0.25f, 10);
            }
            // --- Infinity ---
            case "infinity_on" -> {
                sound("infinity_on", pos, 0.8f, 1f);
                sphereShell(level, pos, 1.3, q(24), BLUE_LIGHT, 0.12f, 14, 0.02);
                ringBurst(level, pos, 0.4f, 3f, BLUE_LIGHT, 0.35f, 12);
            }
            case "infinity_off" -> {
                sound("infinity_off", pos, 0.7f, 1f);
                sphereShell(level, pos, 1.2, q(14), BLUE_LIGHT, 0.1f, 12, -0.01).forEach(x -> x.gravity(0.2f));
            }
            case "infinity_collapse" -> {
                sound("guard_break", pos, 1f, 0.7f);
                sound("infinity_off", pos, 0.9f, 0.8f);
                burst(level, pos, q(26), 0.4, Sprite.SHARD, BLUE_LIGHT, 0.14f, 18).forEach(x -> x.gravity(0.5f).physics());
                if (mine) {
                    ScreenEffects.flash(0x5080C0FF, 8);
                    ScreenEffects.shake(0.5f, 10);
                }
            }
            case "infinity_ripple", "infinity_ripple_small" -> {
                boolean big = p.id().equals("infinity_ripple");
                sound("infinity_ripple", pos, big ? 0.8f : 0.5f, 0.9f + RNG.nextFloat() * 0.3f);
                // Concentric ripples in the plane facing the attack: space itself bending.
                for (int k = 0; k < 3; k++) {
                    ring3d(level, pos, dir, 0.05 + k * 0.12, q(big ? 20 : 12), (0.05 + k * 0.03) * s, BLUE_LIGHT, 0.07f, 9 + k * 2);
                }
                flashAt(level, pos, 0.5f * s, BLUE_LIGHT, 4);
                if (mine) ScreenEffects.fovPunch(-0.01f);
            }
            case "infinity_hold" -> {
                sound("infinity_hold", pos, 0.6f, 1.2f + RNG.nextFloat() * 0.3f);
                ring3d(level, pos, dir, 0.05, q(12), 0.06, BLUE_LIGHT, 0.06f, 12);
                flashAt(level, pos, 0.4f, BLUE_LIGHT, 5);
            }
            // --- Blue ---
            case "blue_cast" -> {
                sound("blue_cast", pos, 0.8f, 1f);
                burst(level, pos.add(dir.scale(0.8)), q(8), 0.1, Sprite.GLOW, BLUE, 0.15f, 8);
            }
            case "blue_spawn" -> {
                sound("blue_spawn", pos, 1.2f, 1f);
                implode(level, pos, 4.5 * s, q(40), BLUE, 0.2f, 14);
                flashAt(level, pos, 2.2f * s, BLUE_LIGHT, 5);
                ringCollapse(level, pos, 4f * s, BLUE_LIGHT, 10);
            }
            case "blue_debris" -> {
                BlockState st = Block.stateById(p.entityId());
                if (!st.isAir()) {
                    for (int i = 0; i < q(6); i++) {
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.x + gauss(0.3), pos.y + gauss(0.3), pos.z + gauss(0.3),
                                dir.x * 0.15, dir.y * 0.15, dir.z * 0.15);
                    }
                }
            }
            case "blue_hit" -> burst(level, pos, q(4), 0.12, Sprite.GLOW, BLUE, 0.15f * s, 6);
            case "blue_collapse" -> {
                sound("blue_collapse", pos, 1.2f, 1f);
                flashAt(level, pos, 3f * s, WHITE, 6);
                ringBurst(level, pos, 0.5f, 5f * s, BLUE_LIGHT, 0.7f, 10);
                burst(level, pos, q(30), 0.5, Sprite.GLOW, BLUE, 0.2f, 12);
            }
            // --- Red ---
            case "red_charge" -> sound("red_charge", pos, 0.9f, 1f);
            case "red_full" -> {
                sound("infinity_hold", pos, 0.8f, 0.6f);
                flashAt(level, pos, 1.2f, RED, 4);
            }
            case "red_fire", "red_pointblank" -> {
                sound("red_fire", pos, 1.2f, 1f);
                flashAt(level, pos, 1.8f * s, RED, 4);
                ring3d(level, pos, dir, 0.1, q(16), 0.35, ORANGE, 0.12f, 6);
                if (mine) {
                    ScreenEffects.shake(0.35f, 6);
                    ScreenEffects.fovPunch(0.04f);
                }
            }
            case "red_fizzle", "purple_fizzle", "domain_fizzle" -> {
                sound("infinity_off", pos, 0.6f, 1.4f);
                burst(level, pos, q(10), 0.1, Sprite.SMOKE, GREY, 0.3f, 12);
            }
            case "red_explosion", "red_amplified" -> {
                boolean amp = p.id().equals("red_amplified");
                sound(amp ? "red_amplified" : "red_explosion", pos, 2.5f, amp ? 0.9f : 1f);
                float[] outer = amp ? PURPLE : ORANGE;
                flashAt(level, pos, 4f * s, WHITE, 6);
                flashAt(level, pos, 6f * s, RED, 9);
                ringBurst(level, pos, 0.6f, 8f * s, outer, 0.8f, 12);
                ringBurst(level, pos, 0.4f, 5f * s, RED, 0.9f, 8);
                burst(level, pos, q(50), 1.1 * Math.sqrt(s), Sprite.SPARK, ORANGE, 0.22f, 10);
                burst(level, pos, q(24), 0.6 * Math.sqrt(s), Sprite.GLOW, RED, 0.35f, 14);
                burst(level, pos, q(16), 0.25, Sprite.SMOKE, GREY, Math.min(0.9f, 0.6f * s), 30);
                ring3d(level, pos, new Vec3(0, 1, 0), 0.5, q(30), 0.9 * s, ORANGE, 0.25f, 12);
                distanceShake(pos, 30 * s, 0.25f);
            }
            case "red_hit" -> burst(level, pos, q(6), 0.3, Sprite.SPARK, ORANGE, 0.14f, 6);
            // --- Hollow Purple ---
            case "purple_blue" -> {
                sound("purple_form", pos, 0.9f, 0.8f);
                implode(level, pos, 1.6, q(16), BLUE, 0.12f, 12);
            }
            case "purple_red" -> {
                sound("purple_form", pos, 0.9f, 1.25f);
                implode(level, pos, 1.6, q(16), RED, 0.12f, 12);
            }
            case "purple_fusion" -> {
                sound("purple_fusion", pos, 1.2f, 1f);
                for (int i = 0; i < q(30); i++) {
                    float[] c = i % 2 == 0 ? BLUE : RED;
                    Vec3 from = pos.add(randomUnit().scale(1.8));
                    add(level, from, Vec3.ZERO, Sprite.GLOW, c, 0.9f, 0.2f, 0.05f, 16).attract(pos, 0.03).spin(0.2f);
                }
            }
            case "purple_charged" -> {
                sound("infinity_hold", pos, 1f, 0.5f);
                ringBurst(level, pos, 0.3f, 3f, PURPLE, 0.7f, 10);
                if (mine) ScreenEffects.fovPunch(-0.05f);
            }
            case "purple_fire" -> {
                sound("purple_fire", pos, 3f, 1f);
                flashAt(level, pos, 5f * s, WHITE, 6);
                ringBurst(level, pos, 0.5f, 9f * s / 2.4f, PURPLE, 0.9f, 12);
                ring3d(level, pos, dir, 0.5, q(40), 0.9, PURPLE_LIGHT, 0.25f, 12);
                if (mine) {
                    ScreenEffects.fovPunch(0.12f);
                    ScreenEffects.shake(0.9f, 16);
                }
            }
            case "purple_trail" -> {
                if (trailCounter++ % 6 == 0) sound("purple_travel", pos, 2f, 0.9f + RNG.nextFloat() * 0.2f);
                for (int i = 0; i < q(8); i++) {
                    Vec3 off = randomUnit().scale(s * (0.8 + RNG.nextDouble() * 0.5));
                    add(level, pos.add(off), off.scale(0.04).subtract(dir.scale(0.05)), Sprite.GLOW, i % 3 == 0 ? PURPLE_LIGHT : PURPLE, 0.8f,
                            0.35f, 0.05f, 14);
                }
                burst(level, pos, q(3), 0.05, Sprite.SMOKE, DARK, Math.min(0.9f, s * 0.4f), 20);
            }
            case "purple_hit" -> {
                flashAt(level, pos, 2.5f * s, PURPLE_LIGHT, 5);
                burst(level, pos, q(20), 0.6, Sprite.SPARK, PURPLE_LIGHT, 0.2f, 8);
            }
            case "purple_end" -> {
                sound("purple_end", pos, 4f, 1f);
                flashAt(level, pos, 8f, WHITE, 8);
                flashAt(level, pos, 12f, PURPLE, 14);
                ringBurst(level, pos, 1f, 18f, PURPLE, 0.9f, 16);
                burst(level, pos, q(80), 1.5, Sprite.SPARK, PURPLE_LIGHT, 0.3f, 14);
                burst(level, pos, q(40), 0.8, Sprite.GLOW, PURPLE, 0.45f, 20);
                burst(level, pos, q(20), 0.35, Sprite.SMOKE, DARK, 0.9f, 40);
                distanceShake(pos, 60, 0.35f);
            }
            // --- Teleport ---
            case "teleport_out" -> {
                sound("teleport", pos, 0.9f, 1f);
                afterimage(level, pos);
            }
            case "teleport_in" -> {
                sound("teleport", pos, 0.9f, 1.35f);
                flashAt(level, pos, 1.4f, BLUE_LIGHT, 4);
                burst(level, pos, q(12), 0.3, Sprite.STAR, BLUE_LIGHT, 0.12f, 8);
                if (mine) ScreenEffects.fovPunch(0.06f);
            }
            // --- Domain ---
            case "domain_charge" -> {
                sound("domain_charge", pos, 1.5f, 1f);
                for (int i = 0; i < q(40); i++) {
                    Vec3 from = pos.add(randomUnit().scale(3 + RNG.nextDouble() * 2));
                    add(level, from, Vec3.ZERO, Sprite.GLOW, i % 3 == 0 ? WHITE : BLUE_LIGHT, 0.7f, 0.15f, 0.05f, 28).attract(pos, 0.012).spin(0.1f).fadeIn();
                }
            }
            case "domain_expand" -> {
                sound("domain_expand", pos, 6f, 1f);
                ringBurst(level, pos, 1f, s * 2.2f, WHITE, 0.9f, 20);
                ring3d(level, pos, new Vec3(0, 1, 0), 1, q(60), s / 14.0, WHITE, 0.4f, 16);
                if (mc.player != null && mc.player.position().distanceTo(pos) < s + 20) {
                    ScreenEffects.flash(0xC0FFFFFF, 18);
                    ScreenEffects.shake(0.8f, 20);
                }
            }
            case "domain_clash", "domain_clash_end" -> {
                sound("domain_clash", pos, 4f, 1f);
                burst(level, pos, q(40), 0.8, Sprite.SPARK, WHITE, 0.3f, 12);
                flashAt(level, pos, 6f, WHITE, 8);
            }
            case "domain_collapse" -> {
                sound("domain_collapse", pos, 5f, 1f);
                sphereShell(level, pos, s * 0.9, q(80), WHITE, 0.3f, 20, -0.08).forEach(x -> x.gravity(0.3f));
            }
            case "domain_surehit" -> {
                sound("domain_surehit", pos, 1f, 1f);
                burst(level, pos, q(16), 0.3, Sprite.STAR, WHITE, 0.14f, 10);
                if (mine) {
                    ScreenEffects.flash(0xE0FFFFFF, 20);
                    ScreenEffects.shake(0.4f, 12);
                }
            }
            case "domain_surehit_tick" -> burst(level, pos, q(3), 0.2, Sprite.STAR, WHITE, 0.08f, 6);
            default -> {}
        }
    }

    // --- helpers ---

    /** Scales particle counts by the quality setting. */
    public static int q(int n) {
        float f = switch (JJKConfig.get().client.particleQuality) {
            case 0 -> 0.2f;
            case 1 -> 0.5f;
            case 3 -> 1.6f;
            default -> 1f;
        };
        return Math.max(1, Math.round(n * f));
    }

    public static EnergyParticle add(ClientLevel level, Vec3 pos, Vec3 vel, Sprite sprite, float[] c, float alpha, float size0, float size1, int life) {
        EnergyParticle p = new EnergyParticle(level, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, sprite, c[0], c[1], c[2], alpha, size0, size1, life);
        Minecraft.getInstance().particleEngine.add(p);
        return p;
    }

    public static java.util.List<EnergyParticle> burst(ClientLevel level, Vec3 pos, int n, double speed, Sprite sprite, float[] c, float size, int life) {
        java.util.List<EnergyParticle> list = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(speed * (0.4 + RNG.nextDouble() * 0.6));
            list.add(add(level, pos, v, sprite, c, 0.9f, size * (0.7f + RNG.nextFloat() * 0.6f), size * 0.2f, life + RNG.nextInt(Math.max(1, life / 2))));
        }
        return list;
    }

    /** A bright flash billboard that shrinks away. */
    public static void flashAt(ClientLevel level, Vec3 pos, float size, float[] c, int life) {
        dev.rick.jjk.client.render.Flashes.flash(pos, size * 0.8f, c, life, level.getGameTime());
    }

    /** Expanding (camera-facing) shockwave ring. */
    public static void ringBurst(ClientLevel level, Vec3 pos, float from, float to, float[] c, float alpha, int life) {
        dev.rick.jjk.client.render.Flashes.ring(pos, from, to, c, alpha, life, level.getGameTime());
    }

    /** Shrinking ring (implosion). */
    public static void ringCollapse(ClientLevel level, Vec3 pos, float from, float[] c, int life) {
        dev.rick.jjk.client.render.Flashes.ring(pos, from, 0.1f, c, 0.7f, life, level.getGameTime());
    }

    /** Particles on a circle in the plane perpendicular to {@code normal}, flying outward. */
    public static void ring3d(ClientLevel level, Vec3 pos, Vec3 normal, double radius, int n, double speed, float[] c, float size, int life) {
        Vec3 nrm = normal.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 a = Math.abs(nrm.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = nrm.cross(a).normalize();
        Vec3 w = nrm.cross(u).normalize();
        for (int i = 0; i < n; i++) {
            double t = Mth.TWO_PI * i / n;
            Vec3 d = u.scale(Math.cos(t)).add(w.scale(Math.sin(t)));
            add(level, pos.add(d.scale(radius)), d.scale(speed), Sprite.GLOW, c, 0.85f, size, size * 0.3f, life).friction(0.86f);
        }
    }

    /** Particles gathered in from a sphere around {@code center}. */
    public static void implode(ClientLevel level, Vec3 center, double radius, int n, float[] c, float size, int life) {
        for (int i = 0; i < n; i++) {
            Vec3 from = center.add(randomUnit().scale(radius * (0.6 + RNG.nextDouble() * 0.4)));
            Vec3 tangent = from.subtract(center).cross(new Vec3(0, 1, 0)).normalize().scale(0.06);
            add(level, from, tangent, Sprite.GLOW, c, 0.85f, size, size * 0.4f, life).attract(center, 0.04).fadeIn();
        }
    }

    public static java.util.List<EnergyParticle> sphereShell(ClientLevel level, Vec3 center, double radius, int n, float[] c, float size, int life, double outward) {
        java.util.List<EnergyParticle> list = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec3 d = randomUnit();
            list.add(add(level, center.add(d.scale(radius)), d.scale(outward), Sprite.STAR, c, 0.8f, size, size * 0.3f, life).fadeIn());
        }
        return list;
    }

    /** White streaks along a swing arc in front of the attacker. */
    private static void arc(ClientLevel level, Vec3 pos, Vec3 dir, int n, float[] c, float alpha) {
        if (dir.lengthSqr() < 1e-4) return;
        Vec3 d = dir.normalize();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        for (int i = 0; i < q(n); i++) {
            double t = (i / (double) Math.max(1, n - 1) - 0.5) * 1.6;
            Vec3 at = pos.add(side.scale(Math.sin(t) * 0.8)).add(d.scale(Math.cos(t) * 0.4));
            add(level, at, d.scale(0.05), Sprite.SPARK, c, alpha, 0.12f, 0.02f, 4);
        }
    }

    /** Fading silhouette-ish cloud where someone just vanished. */
    private static void afterimage(ClientLevel level, Vec3 pos) {
        for (int i = 0; i < q(16); i++) {
            Vec3 at = pos.add(gauss(0.25), gauss(0.6), gauss(0.25));
            add(level, at, new Vec3(0, 0.01, 0), Sprite.GLOW, BLUE_LIGHT, 0.5f, 0.25f, 0.02f, 10);
        }
        flashAt(level, pos, 1.2f, WHITE, 3);
    }

    private static void victimFeedback(FxPayload p, float strength) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && p.entityId() == mc.player.getId()) {
            ScreenEffects.shake(strength, 8);
            ScreenEffects.flash(0x30FF2020, 4);
        }
    }

    private static void distanceShake(Vec3 pos, double radius, float max) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(pos);
        if (d < radius) ScreenEffects.shake((float) (max * (1 - d / radius)), 12);
    }

    public static void sound(String name, Vec3 pos, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float v = volume * JJKConfig.get().client.soundVolume;
        if (v <= 0) return;
        mc.level.playLocalSound(pos.x, pos.y, pos.z, ModSounds.get(name), SoundSource.PLAYERS, v, pitch, false);
    }

    public static Vec3 randomUnit() {
        double z = RNG.nextDouble() * 2 - 1;
        double a = RNG.nextDouble() * Mth.TWO_PI;
        double r = Math.sqrt(1 - z * z);
        return new Vec3(r * Math.cos(a), z, r * Math.sin(a));
    }

    private static double gauss(double s) {
        return RNG.nextGaussian() * s;
    }
}
