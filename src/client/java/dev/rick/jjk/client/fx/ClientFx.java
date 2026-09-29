package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle;
import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.client.ClientState;
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

    static final RandomSource RNG = RandomSource.create();
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
        long now = level.getGameTime();
        // Distance LOD: far effects spawn fewer particles; small effects far away are heard, not drawn.
        double dist = mc.player != null ? mc.player.getEyePosition().distanceTo(pos) : 0;
        boolean major = MAJOR.contains(p.id());
        lod = dist > 96 && !major ? 0f : dist > 48 ? 0.5f : 1f;
        try {
            play(p, mc, level, pos, dir, s, mine, now);
        } finally {
            lod = 1f;
        }
    }

    /** Effects big enough to always be drawn in full, whatever the distance. */
    private static final java.util.Set<String> MAJOR = java.util.Set.of("awaken", "max_blue_spawn", "max_blue_collapse", "max_red_explosion",
            "purple_fire", "purple_end", "domain_expand", "domain_sealed", "domain_counter", "domain_collapse", "red_explosion", "red_amplified", "finisher", "clash_start",
            "clash_sudden_death", "clash_perfect", "clash_win", "jackpot", "gamble_riichi", "gamble_hit", "overwhelm_final", "idg_charge");

    static float lod = 1f;

    private static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = lod > 0;
        // A plain JJS sound for a move step ("sfx:<sound>"), or music that follows its entity ("music:<sound>").
        if (p.id().startsWith("sfx:")) {
            sound(p.id().substring(4), pos, Math.max(0.6f, s), 1f);
            return;
        }
        if (p.id().startsWith("music:")) {
            follow(p.id().substring(6), p.entityId(), Math.max(1f, s));
            return;
        }
        switch (p.id()) {
            case "zero_two_cutin" -> dev.rick.jjk.client.hud.GojoPresentation.domainCutIn();
            case "limitless_shatter" -> {
                // The caster's view breaks like glass as space folds; everyone else sees the flash where he stood.
                if (mine) dev.rick.jjk.client.hud.GojoPresentation.shatter();
                Flashes.flash(pos, 0.3f, 2.2f, WHITE, 0.9f, 5, now);
                if (drawn) sparks(level, pos, Vec3.ZERO, q(14), 0.5, WHITE, 0.1f, 8);
            }
            // --- melee: clean, precise, small. Light cuts, not explosions. ---
            case "swing" -> {
                boolean heavy = s > 1.2f;
                sound(heavy ? "swing_heavy" : "swing", pos, 0.6f, 0.9f + RNG.nextFloat() * 0.3f);
                if (drawn) swingTrail(pos, dir, heavy ? 1.2f : 0.9f, heavy ? 0.14f : 0.08f, now);
            }
            case "hit_light" -> {
                sound("hit_light", pos, 0.9f, 0.9f + RNG.nextFloat() * 0.25f);
                if (drawn) {
                    Flashes.flash(pos, 0.9f, 0.2f, WHITE, 1f, 3, now);
                    impactStar(pos, dir, 4, 0.9f, 0.04f, WHITE, now);
                    Flashes.ripple(pos, dir, 0.15f, 0.9f, BLUE_LIGHT, 0.5f, 5, now);
                    sparks(level, pos, dir, q(5), 0.35, WHITE, 0.1f, 5);
                }
                victimFeedback(p, 0.25f);
            }
            case "hit_heavy" -> {
                sound("hit_heavy", pos, 1f, 0.9f + RNG.nextFloat() * 0.15f);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.3f, WHITE, 1f, 4, now);
                    impactStar(pos, dir, 6, 1.6f, 0.07f, WHITE, now);
                    Flashes.ripple(pos, dir, 0.2f, 1.8f, BLUE_LIGHT, 0.7f, 7, now);
                    Flashes.ripple(pos.add(dir.scale(0.4)), dir, 0.1f, 1.1f, WHITE, 0.4f, 6, now + 1);
                    sparks(level, pos, dir, q(10), 0.6, WHITE, 0.14f, 7);
                    burst(level, pos, q(3), 0.1, Sprite.SMOKE, GREY, 0.4f, 12);
                }
                victimFeedback(p, 0.55f);
                if (mine || isAttackerClose(mc, pos)) ScreenEffects.fovPunch(0.02f);
            }
            case "hit_launch" -> {
                sound("hit_heavy", pos, 1f, 1.2f);
                if (drawn) {
                    Flashes.flash(pos, 1.3f, 0.3f, WHITE, 1f, 4, now);
                    Flashes.beam(pos.add(0, -0.6, 0), pos.add(0, 2.2, 0), 0.12f, BLUE_LIGHT, 0.8f, 6, now);
                    Flashes.ripple(pos, new Vec3(0, 1, 0), 0.2f, 1.6f, BLUE_LIGHT, 0.6f, 7, now);
                    sparks(level, pos, new Vec3(0, 1, 0), q(10), 0.7, WHITE, 0.13f, 8);
                }
                victimFeedback(p, 0.5f);
            }
            case "hit_slam" -> {
                sound("hit_slam", pos, 1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.5f, 0.3f, WHITE, 1f, 4, now);
                    Flashes.beam(pos.add(0, 1.5, 0), pos.add(0, -0.8, 0), 0.14f, WHITE, 0.8f, 5, now);
                    Flashes.ground(groundBelow(level, pos), 0.3f, 2.6f, GREY, 0.7f, 9, now + 1);
                    sparks(level, pos, new Vec3(0, -1, 0), q(10), 0.6, WHITE, 0.13f, 7);
                }
                victimFeedback(p, 0.6f);
            }
            case "heavy_charge" -> {
                sound("heavy_charge", pos, 0.7f, 1f);
                Entity e = level.getEntity(p.entityId());
                Vec3 at = e != null ? e.position().add(0, 1.2, 0) : pos.add(0, 1.2, 0);
                if (drawn) {
                    implode(level, at, 1.3, q(8), BLUE_LIGHT, 0.1f, 10);
                    Flashes.ring(at, 1.3f, 0.2f, BLUE_LIGHT, 0.5f, 10, now);
                }
            }
            case "block" -> {
                sound("block", pos, 0.9f, 1f + RNG.nextFloat() * 0.1f);
                if (drawn) {
                    Flashes.ripple(pos, dir, 0.2f, 1.1f, BLUE_LIGHT, 0.7f, 6, now);
                    sparks(level, pos, dir, q(5), 0.3, WHITE, 0.09f, 5);
                }
            }
            case "parry" -> {
                sound("parry", pos, 1f, 1f);
                Flashes.flash(pos, 2.2f, 0.4f, GOLD, 1f, 6, now);
                impactStar(pos, dir, 8, 2.2f, 0.07f, GOLD, now);
                Flashes.ring(pos, 0.2f, 2.8f, GOLD, 0.8f, 9, now);
                if (drawn) burst(level, pos, q(12), 0.5, Sprite.STAR, GOLD, 0.16f, 10);
                if (mine) ScreenEffects.flash(0x40FFE9A0, 5);
            }
            case "guard_break" -> {
                sound("guard_break", pos, 1f, 1f);
                Flashes.flash(pos, 2f, 0.4f, WHITE, 1f, 5, now);
                Flashes.ripple(pos, dir, 0.3f, 2.4f, BLUE_LIGHT, 0.9f, 8, now);
                if (drawn) burst(level, pos, q(18), 0.45, Sprite.SHARD, BLUE_LIGHT, 0.14f, 16).forEach(x -> x.gravity(0.6f).physics());
                if (mine) ScreenEffects.shake(0.6f, 10);
            }
            case "evade" -> {
                sound("side_dash", pos, 0.8f, 1f);
                if (drawn) burst(level, pos, q(6), 0.08, Sprite.SMOKE, GREY, 0.45f, 10);
            }
            case "dash" -> {
                sound("dash", pos, 0.7f, 1f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    for (int i = 0; i < q(8); i++) {
                        Vec3 at = pos.add(gauss(0.3), gauss(0.5), gauss(0.3)).subtract(dir.scale(RNG.nextDouble()));
                        add(level, at, dir.scale(-0.08), Sprite.SMOKE, GREY, 0.5f, 0.35f, 0.6f, 10);
                    }
                    Flashes.ground(groundBelow(level, pos), 0.2f, 1.4f, GREY, 0.35f, 7, now);
                }
            }
            case "ground_impact" -> {
                sound("ground_impact", pos, 1f, 1f);
                if (drawn) {
                    debris(level, pos, q(18), 0.3);
                    Flashes.ground(pos.add(0, 0.05, 0), 0.4f, 3f, GREY, 0.6f, 12, now);
                }
            }
            case "no_energy" -> {
                if (mine) sound("no_energy", pos, 0.6f, 1f);
                if (drawn) burst(level, pos, q(4), 0.05, Sprite.SMOKE, GREY, 0.25f, 10);
            }
            // --- Infinity: space bending, never noise. ---
            case "infinity_on" -> {
                sound("infinity_on", pos, 0.8f, 1f);
                Flashes.lens(pos, 2.4f, 1.2f, BLUE_LIGHT, 0.5f, 14, now);
                Flashes.ring(pos, 0.4f, 2.4f, BLUE_LIGHT, 0.35f, 12, now);
                if (drawn) sphereShell(level, pos, 1.3, q(10), BLUE_LIGHT, 0.08f, 14, 0.02);
            }
            case "infinity_off" -> {
                sound("infinity_off", pos, 0.7f, 1f);
                Flashes.lens(pos, 1.2f, 1.8f, BLUE_LIGHT, 0.35f, 10, now);
                if (drawn) sphereShell(level, pos, 1.2, q(10), BLUE_LIGHT, 0.08f, 12, -0.01).forEach(x -> x.gravity(0.2f));
            }
            case "infinity_collapse" -> {
                sound("guard_break", pos, 1f, 0.7f);
                sound("infinity_off", pos, 0.9f, 0.8f);
                Flashes.lens(pos, 1.3f, 2.6f, BLUE_LIGHT, 0.8f, 10, now);
                Flashes.flash(pos, 2f, 0.4f, BLUE_LIGHT, 0.9f, 6, now);
                if (drawn) burst(level, pos, q(20), 0.4, Sprite.SHARD, BLUE_LIGHT, 0.14f, 18).forEach(x -> x.gravity(0.5f).physics());
                if (mine) {
                    ScreenEffects.flash(0x5080C0FF, 8);
                    ScreenEffects.shake(0.5f, 10);
                }
            }
            case "infinity_ripple", "infinity_ripple_small" -> {
                boolean big = p.id().equals("infinity_ripple");
                sound("infinity_ripple", pos, big ? 0.8f : 0.5f, 0.9f + RNG.nextFloat() * 0.3f);
                // Concentric ripples in the plane facing the attack, like a stone dropped into space.
                float k = big ? 1f : 0.6f;
                for (int i = 0; i < 3; i++) {
                    Flashes.ripple(pos, dir, 0.05f, (0.7f + i * 0.35f) * k, BLUE_LIGHT, 0.55f - i * 0.12f, 9 + i * 2, now + i * 2L);
                }
                Flashes.lens(pos, 0.2f, 0.9f * k, BLUE_LIGHT, 0.35f, 8, now);
                if (drawn && big) sparks(level, pos, dir.reverse(), q(3), 0.1, BLUE_LIGHT, 0.05f, 8);
                if (mine) ScreenEffects.fovPunch(-0.01f);
            }
            case "infinity_hold" -> {
                // A projectile frozen at the boundary: a faint ring and nothing else.
                if (RNG.nextFloat() < 0.5f) sound("infinity_hold", pos, 0.4f, 1.2f + RNG.nextFloat() * 0.3f);
                Flashes.ripple(pos, dir, 0.05f, 0.6f, BLUE_LIGHT, 0.35f, 10, now);
            }
            // --- Blue: gravity folding inward. ---
            case "blue_cast" -> {
                sound("blue_cast", pos, 0.8f, 1f);
                Vec3 at = pos.add(dir.scale(0.8));
                Flashes.ring(at, 1f, 0.1f, BLUE_LIGHT, 0.6f, 7, now);
                if (drawn) implode(level, at, 0.9, q(6), BLUE, 0.1f, 7);
            }
            case "blue_spawn" -> {
                sound("blue_spawn", pos, 1.2f, 1f);
                // Lapse Blue's pull (JJS GIF): the caster's view floods with the blue vortex for a moment.
                if (mine && s > 0.65f && s < 0.75f) ScreenEffects.flash(0xB070D0FF, 9);
                Vec3 core = pos.add(0, 0.5, 0);
                Flashes.ring(core, 3.5f * s, 0.2f, BLUE_LIGHT, 0.7f, 9, now);
                Flashes.lens(core, 3f * s, 0.6f, BLUE, 0.6f, 9, now);
                Flashes.flash(core, 1.4f * s, 0.3f, BLUE_LIGHT, 0.8f, 5, now + 8);
                if (drawn) implode(level, core, 3.5 * s, q(24), BLUE, 0.16f, 12);
            }
            case "blue_debris" -> {
                BlockState st = Block.stateById(p.entityId());
                if (drawn && !st.isAir()) {
                    for (int i = 0; i < q(5); i++) {
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.x + gauss(0.3), pos.y + gauss(0.3), pos.z + gauss(0.3),
                                dir.x * 0.15, dir.y * 0.15, dir.z * 0.15);
                    }
                }
            }
            case "blue_hit" -> {
                if (drawn) burst(level, pos, q(3), 0.08, Sprite.GLOW, BLUE, 0.12f * s, 6);
            }
            case "blue_collapse" -> {
                // Implosion: everything snaps to the point, then a small clean pop.
                sound("blue_collapse", pos, 1.2f, 1f);
                Vec3 core = pos.add(0, 0.5, 0);
                Flashes.ring(core, 3f * s, 0.2f, BLUE_LIGHT, 0.9f, 5, now);
                Flashes.flash(core, 2.4f * s, 0.4f, WHITE, 1f, 5, now + 4);
                Flashes.ring(core, 0.3f, 3.5f * s, BLUE, 0.6f, 8, now + 5);
                if (drawn) implode(level, core, 2.5 * s, q(20), BLUE_LIGHT, 0.14f, 6);
            }
            // --- Red: unstable build-up, a compression moment, then an outward shockwave. ---
            case "red_charge" -> {
                sound("red_charge", pos, 0.9f, 1f);
                if (drawn) implode(level, pos, 1.2, q(8), RED, 0.08f, 12);
            }
            case "red_full" -> {
                sound("red_compress", pos, 0.9f, 1f);
                Flashes.ring(pos, 1.4f, 0.1f, ORANGE, 0.9f, 5, now);
                Flashes.flash(pos, 1.2f, 0.2f, RED, 1f, 4, now + 4);
            }
            case "red_fire", "red_pointblank" -> {
                sound("red_compress", pos, 0.7f, 1.3f);
                sound("red_fire", pos, 1.2f, 1f);
                Flashes.flash(pos, 1.6f * s, 0.3f, RED, 1f, 4, now);
                Flashes.ripple(pos, dir, 0.2f, 1.8f * s, ORANGE, 0.8f, 6, now);
                if (drawn) sparks(level, pos, dir, q(8), 0.5, ORANGE, 0.1f, 6);
                if (mine) {
                    ScreenEffects.shake(0.35f, 6);
                    ScreenEffects.fovPunch(0.04f);
                }
            }
            case "red_fizzle", "purple_fizzle", "domain_fizzle" -> {
                sound("infinity_off", pos, 0.6f, 1.4f);
                if (drawn) burst(level, pos, q(8), 0.1, Sprite.SMOKE, GREY, 0.3f, 12);
            }
            case "red_explosion", "red_amplified" -> {
                boolean amp = p.id().equals("red_amplified");
                sound(amp ? "red_amplified" : "red_explosion", pos, 2.5f, amp ? 0.9f : 1f);
                float[] outer = amp ? PURPLE : ORANGE;
                // Compression: a beat where the light squeezes into a point...
                Flashes.ring(pos, 2f * s, 0.2f, ORANGE, 0.9f, 3, now);
                Flashes.flash(pos, 1f * s, 0.2f, WHITE, 1f, 3, now);
                // ...then everything goes outward.
                Flashes.flash(pos, 3.5f * s, 1f, WHITE, 1f, 5, now + 2);
                Flashes.flash(pos, 5f * s, 1.5f, RED, 0.8f, 9, now + 2);
                Flashes.ring(pos, 0.5f, 6f * s, outer, 0.9f, 10, now + 2);
                Flashes.ground(groundBelow(level, pos), 0.5f, 7f * s, outer, 0.8f, 14, now + 3);
                Flashes.ground(groundBelow(level, pos), 0.3f, 4.5f * s, WHITE, 0.5f, 10, now + 5);
                if (drawn) {
                    sparks(level, pos, Vec3.ZERO, q(28), 1.0 * Math.sqrt(s), ORANGE, 0.18f, 9);
                    burst(level, pos, q(10), 0.2, Sprite.SMOKE, GREY, Math.min(0.9f, 0.6f * s), 30);
                    debris(level, pos, q(14), 0.45 * Math.sqrt(s));
                }
                distanceShake(pos, 30 * s, 0.3f);
            }
            case "red_hit" -> {
                if (drawn) sparks(level, pos, dir, q(5), 0.3, ORANGE, 0.12f, 6);
            }
            // --- Hollow Purple: Blue, Red, collision, the core, the release, the impact. ---
            case "purple_blue" -> {
                sound("purple_form", pos, 0.9f, 0.8f);
                follow("purple_music", p.entityId(), 3f);
                Flashes.ring(pos, 1.6f, 0.2f, BLUE_LIGHT, 0.8f, 10, now);
                if (drawn) implode(level, pos, 1.6, q(14), BLUE, 0.12f, 12);
            }
            case "purple_red" -> {
                sound("purple_form", pos, 0.9f, 1.25f);
                Flashes.ring(pos, 0.2f, 1.6f, ORANGE, 0.8f, 10, now);
                if (drawn) sparks(level, pos, Vec3.ZERO, q(10), 0.2, RED, 0.1f, 10);
            }
            case "purple_fusion" -> {
                sound("purple_collision", pos, 1.3f, 1f);
                sound("purple_fusion", pos, 1.2f, 1f);
                // The two opposites grind together: arcs, then a crushing compression flash.
                for (int i = 0; i < 5; i++) {
                    Vec3 a = pos.add(randomUnit().scale(0.9)), b = pos.add(randomUnit().scale(0.9));
                    Flashes.beam(a, b, 0.08f, i % 2 == 0 ? BLUE_LIGHT : ORANGE, 0.9f, 4, now + i * 3L);
                }
                Flashes.ring(pos, 2.5f, 0.2f, PURPLE_LIGHT, 0.9f, 12, now);
                Flashes.flash(pos, 2.5f, 0.5f, WHITE, 1f, 6, now + 12);
                if (drawn) {
                    for (int i = 0; i < q(24); i++) {
                        float[] c = i % 2 == 0 ? BLUE : RED;
                        Vec3 from = pos.add(randomUnit().scale(1.8));
                        add(level, from, Vec3.ZERO, Sprite.GLOW, c, 0.9f, 0.18f, 0.05f, 16).attract(pos, 0.03).spin(0.2f);
                    }
                }
                if (mine) ScreenEffects.shake(0.25f, 14);
            }
            case "purple_charged" -> {
                sound("infinity_hold", pos, 1f, 0.5f);
                Flashes.lens(pos, 3.5f, 1.2f, PURPLE, 0.7f, 10, now);
                Flashes.ring(pos, 0.3f, 3f, PURPLE_LIGHT, 0.7f, 10, now);
                if (mine) ScreenEffects.fovPunch(-0.05f);
            }
            case "purple_fire" -> {
                sound("purple_fire", pos, 3f, 1f);
                float k = s / 3.2f;
                // Release: space gives way in front of it, and the air behind it is torn open.
                Flashes.flash(pos, 5f * k, 1f, WHITE, 1f, 6, now);
                Flashes.lens(pos, 1f, 9f * k, PURPLE, 0.8f, 12, now);
                for (int i = 0; i < 3; i++) {
                    Flashes.ripple(pos.add(dir.scale(1.5 + i * 2.5)), dir, 1f, (4f + i * 1.5f) * k, i == 0 ? WHITE : PURPLE_LIGHT, 0.8f - i * 0.15f,
                            10, now + i);
                }
                Flashes.beam(pos, pos.add(dir.scale(22)), 1.2f * k, PURPLE, 0.8f, 10, now);
                if (drawn) sparks(level, pos, dir, q(24), 1.2, PURPLE_LIGHT, 0.22f, 10);
                if (mine) {
                    ScreenEffects.fovPunch(0.14f);
                    ScreenEffects.shake(0.9f, 16);
                }
            }
            case "purple_trail" -> {
                if (trailCounter++ % 6 == 0) sound("purple_travel", pos, 2f, 0.9f + RNG.nextFloat() * 0.2f);
                if (trailCounter % 3 == 0) Flashes.ripple(pos, dir, s * 0.8f, s * 2.6f, PURPLE_LIGHT, 0.45f, 10, now);
                if (drawn) {
                    for (int i = 0; i < q(6); i++) {
                        Vec3 off = randomUnit().scale(s * (0.8 + RNG.nextDouble() * 0.5));
                        add(level, pos.add(off), off.scale(0.04).subtract(dir.scale(0.05)), Sprite.GLOW, i % 3 == 0 ? PURPLE_LIGHT : PURPLE, 0.8f,
                                0.35f, 0.05f, 14);
                    }
                    burst(level, pos, q(2), 0.05, Sprite.SMOKE, DARK, Math.min(0.9f, s * 0.4f), 20);
                }
            }
            case "purple_hit" -> {
                Flashes.flash(pos, 2.5f, 0.5f, PURPLE_LIGHT, 1f, 5, now);
                if (drawn) sparks(level, pos, dir, q(12), 0.6, PURPLE_LIGHT, 0.18f, 8);
            }
            case "purple_end" -> {
                sound("purple_end", pos, 4f, 1f);
                sound("max_red_explosion", pos, 2.5f, 0.75f);
                float k = s / 3.2f;
                Vec3 ground = groundBelow(level, pos);
                // A white-out that swallows the area, then the purple fire and the ground giving way in waves.
                Flashes.flash(pos, 3f * k, 16f * k, WHITE, 1f, 10, now);
                Flashes.lens(pos, 2f, 16f * k, PURPLE, 0.9f, 22, now);
                Flashes.flash(pos, 14f * k, 5f * k, PURPLE, 0.8f, 30, now + 4);
                for (int i = 0; i < 3; i++) {
                    Flashes.ground(ground, 1f, (14f + i * 7f) * k, i == 0 ? WHITE : PURPLE_LIGHT, 0.9f - i * 0.2f, 18 + i * 4, now + 3 + i * 4L);
                }
                Flashes.beam(ground, ground.add(0, 38 * k, 0), 2.6f * k, PURPLE_LIGHT, 0.9f, 26, now + 2);
                Flashes.ring(pos, 1f, 20f * k, PURPLE, 0.9f, 16, now + 2);
                if (drawn) {
                    sparks(level, pos, Vec3.ZERO, q(50), 1.5, PURPLE_LIGHT, 0.28f, 14);
                    burst(level, pos, q(24), 0.8, Sprite.GLOW, PURPLE, 0.45f, 26);
                    burst(level, pos, q(14), 0.35, Sprite.SMOKE, DARK, 0.9f, 40);
                    debris(level, pos, q(40), 0.9);
                }
                distanceShake(pos, 70, 0.6f);
                if (mc.player != null && mc.player.position().distanceTo(pos) < 40) ScreenEffects.impact(4);
            }
            // --- Teleport: space folds shut here and opens there. ---
            case "teleport_out" -> {
                boolean combat = dir.length() < 12;
                sound("teleport_out", pos, 0.9f, combat ? 1.2f : 1f);
                Flashes.lens(pos, combat ? 1.2f : 1.8f, 0.1f, BLUE_LIGHT, 0.7f, 6, now);
                Flashes.flash(pos, 1.1f, 0.1f, WHITE, 0.9f, 3, now + 4);
                if (!combat) Flashes.beam(pos.add(0, -1, 0), pos.add(0, 1.2, 0), 0.25f, BLUE_LIGHT, 0.9f, 6, now + 3);
                if (drawn) {
                    implode(level, pos, combat ? 0.9 : 1.4, q(combat ? 6 : 12), BLUE_LIGHT, 0.09f, 6);
                    if (!combat) afterimage(level, pos);
                }
            }
            case "teleport_in" -> {
                boolean combat = dir.length() < 12;
                sound("teleport_in", pos, 0.9f, combat ? 1.2f : 1f);
                Flashes.lens(pos, 0.1f, combat ? 1.4f : 2.2f, BLUE_LIGHT, 0.7f, 7, now);
                Flashes.flash(pos, 1.3f, 0.2f, WHITE, 1f, 4, now);
                Flashes.ripple(pos.add(0, -0.85, 0), new Vec3(0, 1, 0), 0.3f, combat ? 1.6f : 2.6f, BLUE_LIGHT, 0.6f, 9, now);
                if (combat) {
                    // A clean streak from where you were: the only trace of the jump.
                    Flashes.beam(pos.subtract(dir), pos, 0.1f, BLUE_LIGHT, 0.55f, 5, now);
                } else if (drawn) {
                    burst(level, pos, q(10), 0.3, Sprite.STAR, BLUE_LIGHT, 0.1f, 8);
                }
                if (mine) ScreenEffects.fovPunch(combat ? 0.04f : 0.07f);
            }
            // --- Domain ---
            case "domain_charge" -> {
                sound("domain_charge", pos, 1.5f, 1f);
                Flashes.lens(pos, 6f, 0.5f, BLUE_LIGHT, 0.6f, 30, now);
                for (int i = 0; i < 4; i++) Flashes.ring(pos, 5f, 0.3f, i % 2 == 0 ? WHITE : BLUE_LIGHT, 0.5f, 10, now + i * 6L);
                if (drawn) {
                    for (int i = 0; i < q(30); i++) {
                        Vec3 from = pos.add(randomUnit().scale(3 + RNG.nextDouble() * 2));
                        add(level, from, Vec3.ZERO, Sprite.GLOW, i % 3 == 0 ? WHITE : BLUE_LIGHT, 0.7f, 0.15f, 0.05f, 28).attract(pos, 0.012).spin(0.1f).fadeIn();
                    }
                }
            }
            case "domain_expand" -> {
                // The domain starts at the sorcerer's feet: a burst of energy into the ground that the structure grows from.
                sound("max_charge", pos, 2f, 0.7f);
                Vec3 feet = pos.add(0, -0.5, 0);
                Flashes.flash(feet.add(0, 0.6, 0), 2.5f, 0.5f, WHITE, 1f, 8, now);
                Flashes.ground(feet, 0.3f, 4f, WHITE, 0.9f, 10, now);
                Flashes.ground(feet, 0.3f, 2.5f, BLUE_LIGHT, 0.8f, 8, now + 3);
                Flashes.beam(feet, feet.add(0, 7, 0), 0.6f, BLUE_LIGHT, 0.9f, 14, now);
                if (drawn) {
                    for (int i = 0; i < q(30); i++) {
                        double ang = RNG.nextDouble() * Mth.TWO_PI, rad = 0.4 + RNG.nextDouble() * 2.5;
                        Vec3 at = feet.add(Math.cos(ang) * rad, 0.1, Math.sin(ang) * rad);
                        add(level, at, new Vec3(Math.cos(ang) * 0.15, 0.02, Math.sin(ang) * 0.15), Sprite.GLOW, i % 3 == 0 ? WHITE : BLUE_LIGHT,
                                0.8f, 0.2f, 0.04f, 14).friction(0.9f);
                    }
                }
                distanceShake(pos, s * 3, 0.35f);
            }
            case "domain_counter" -> {
                // Answering a domain with a domain: energy erupts and lashes toward the one who opened first.
                sound("max_charge", pos, 3f, 1.4f);
                sound("clash_start", pos, 2.5f, 1.2f);
                Flashes.flash(pos, 5f, 1f, WHITE, 1f, 8, now);
                Flashes.lens(pos, 1f, 9f, BLUE_LIGHT, 0.9f, 12, now);
                Flashes.ground(groundBelow(level, pos), 1f, 10f, WHITE, 0.9f, 12, now);
                if (dir.lengthSqr() > 1e-4) Flashes.beam(pos, pos.add(dir.scale(0.5)), 0.8f, BLUE_LIGHT, 1f, 10, now);
                if (drawn) sparks(level, pos, Vec3.ZERO, q(40), 1.2, WHITE, 0.25f, 12);
                if (mine) {
                    ScreenEffects.flash(0x60FFFFFF, 6);
                    ScreenEffects.fovPunch(0.12f);
                }
            }
            case "domain_sealed" -> {
                // The final seal: one pulse runs through the whole structure.
                float r = Math.max(4f, s);
                ClientState.Domain sealed = ClientState.domainOwnedBy(p.entityId());
                boolean idg = sealed != null && dev.rick.jjk.hakari.IdleDeathGamble.ID.equals(sealed.definition);
                boolean shrine = sealed != null && dev.rick.jjk.yuji.MalevolentShrine.ID.equals(sealed.definition);
                sound(idg ? "idg_sealed" : shrine ? "shrine_expand" : "domain_expand", pos, 6f, 1f);
                sound(idg ? "idg_music" : shrine ? "shrine_music" : "uv_music", pos, 4f, 1f);
                if (shrine) sound("shrine_splash", pos, 3f, 1f);
                Flashes.lens(pos, r * 0.85f, r * 1.08f, WHITE, 0.9f, 12, now);
                Flashes.ground(pos.add(0, -0.45, 0), r * 0.2f, r * 1.1f, BLUE_LIGHT, 0.8f, 14, now);
                Flashes.ring(pos, r * 0.5f, r * 1.2f, WHITE, 0.6f, 12, now);
                if (drawn) sphereShell(level, pos, r * 0.95, q(50), WHITE, 0.3f, 16, -0.05).forEach(x -> x.gravity(0.2f));
                if (mc.player != null && mc.player.position().distanceTo(pos) < r + 2) {
                    ScreenEffects.flash(0x70FFFFFF, 12);
                    ScreenEffects.shake(0.7f, 16);
                }
            }
            case "domain_clash", "domain_clash_end" -> {
                sound("domain_clash", pos, 4f, 1f);
                Flashes.flash(pos, 6f, 1f, WHITE, 1f, 8, now);
                if (dir.lengthSqr() > 1e-4) Flashes.ripple(pos, dir, 1f, 8f, WHITE, 0.8f, 12, now);
                if (drawn) sparks(level, pos, Vec3.ZERO, q(30), 0.8, WHITE, 0.25f, 12);
            }
            case "domain_collapse" -> {
                sound("domain_collapse", pos, 5f, 1f);
                stopSound("clash_music");
                ClientState.Domain ending = ClientState.domainOwnedBy(p.entityId());
                if (ending != null) stopSound(dev.rick.jjk.hakari.IdleDeathGamble.ID.equals(ending.definition) ? "idg_music"
                        : dev.rick.jjk.yuji.MalevolentShrine.ID.equals(ending.definition) ? "shrine_music" : "uv_music");
                Flashes.lens(pos, s, 1f, WHITE, 0.7f, 20, now);
                if (drawn) sphereShell(level, pos, s * 0.9, q(60), WHITE, 0.3f, 20, -0.08).forEach(x -> x.gravity(0.3f));
            }
            case "domain_surehit" -> {
                sound("domain_surehit", pos, 1f, 1f);
                if (drawn) burst(level, pos, q(12), 0.3, Sprite.STAR, WHITE, 0.14f, 10);
                if (mine) {
                    ScreenEffects.flash(0xE0FFFFFF, 20);
                    ScreenEffects.shake(0.4f, 12);
                }
            }
            case "domain_surehit_tick" -> {
                if (drawn) burst(level, pos, q(2), 0.2, Sprite.STAR, WHITE, 0.08f, 6);
            }
            // --- Awakening: the biggest non-ultimate moment in the kit. ---
            case "awaken_start" -> {
                sound("awaken_grab", pos, 2f, 1f);
                // "Let's get... a little crazy."
                dev.rick.jjk.client.hud.GojoPresentation.speech(p.entityId(), JJKConfig.get().awakening.transitionTicks);
                Flashes.lens(pos, 7f, 0.8f, BLUE_LIGHT, 0.6f, 17, now);
                for (int i = 0; i < 3; i++) Flashes.ring(pos, 6f - i, 0.3f, i == 1 ? WHITE : BLUE_LIGHT, 0.6f, 12, now + i * 5L);
                Flashes.ground(groundBelow(level, pos), 6f, 0.5f, BLUE_LIGHT, 0.6f, 17, now);
                if (drawn) {
                    for (int i = 0; i < q(40); i++) {
                        Vec3 from = pos.add(randomUnit().scale(4 + RNG.nextDouble() * 3));
                        add(level, from, Vec3.ZERO, Sprite.GLOW, i % 2 == 0 ? WHITE : BLUE_LIGHT, 0.8f, 0.18f, 0.04f, 20).attract(pos, 0.02).spin(0.2f).fadeIn();
                    }
                }
                if (mine) {
                    ScreenEffects.fovHold(-0.1f);
                    ScreenEffects.flash(0x60000010, 16);
                }
            }
            case "awaken" -> {
                // The eyes open: a pillar of light tears upward, the ground ripples out in waves, space lurches.
                sound("awaken", pos, 5f, 1f);
                Vec3 ground = groundBelow(level, pos);
                Flashes.flash(pos, 3f, 12f, WHITE, 1f, 10, now);
                Flashes.flash(pos, 12f, 4f, BLUE_LIGHT, 0.7f, 22, now + 2);
                Flashes.beam(ground, ground.add(0, 42, 0), 2.2f, BLUE_LIGHT, 1f, 30, now);
                Flashes.beam(ground, ground.add(0, 28, 0), 0.8f, WHITE, 1f, 20, now);
                Flashes.lens(pos, 1f, 12f, BLUE_LIGHT, 0.8f, 18, now);
                for (int i = 0; i < 3; i++) {
                    Flashes.ground(ground, 1f, 10f + i * 6f, i == 0 ? WHITE : BLUE_LIGHT, 0.9f - i * 0.2f, 16 + i * 3, now + i * 4L);
                }
                Flashes.ring(pos, 1f, 18f, BLUE_LIGHT, 0.8f, 18, now + 1);
                if (drawn) {
                    for (int i = 0; i < q(60); i++) {
                        double a = RNG.nextDouble() * Mth.TWO_PI, r = RNG.nextDouble() * 1.2;
                        Vec3 at = pos.add(Math.cos(a) * r, -1 + RNG.nextDouble(), Math.sin(a) * r);
                        add(level, at, new Vec3(Math.cos(a) * 0.05, 0.6 + RNG.nextDouble() * 0.9, Math.sin(a) * 0.05), Sprite.GLOW,
                                i % 3 == 0 ? WHITE : BLUE_LIGHT, 0.9f, 0.35f, 0.05f, 24 + RNG.nextInt(12)).friction(0.95f);
                    }
                    debris(level, ground.add(0, 0.2, 0), q(30), 0.6);
                }
                distanceShake(pos, 40, 0.7f);
                if (mine) {
                    ScreenEffects.fovHold(0);
                    ScreenEffects.fovPunch(0.12f);
                }
            }
            case "awaken_end" -> {
                sound("awaken_end", pos, 1.2f, 1f);
                Flashes.lens(pos, 1.5f, 0.3f, BLUE_LIGHT, 0.5f, 14, now);
                if (drawn) sphereShell(level, pos, 1.4, q(24), BLUE_LIGHT, 0.14f, 16, -0.02).forEach(x -> x.gravity(0.2f));
            }
            // --- Max techniques: the same identities, a whole tier above. ---
            case "max_blue_cast" -> {
                sound("max_blue_wind", pos, 1.6f, 1f);
                Vec3 at = pos.add(dir.scale(1.2));
                Flashes.lens(at, 5f, 0.3f, BLUE, 0.7f, 16, now);
                for (int i = 0; i < 3; i++) Flashes.ring(at, 4.5f, 0.2f, BLUE_LIGHT, 0.7f, 10, now + i * 5L);
                if (drawn) implode(level, at, 3.5, q(30), BLUE, 0.2f, 14);
                if (mine) ScreenEffects.fovPunch(-0.06f);
            }
            case "max_blue_spawn" -> {
                sound("max_blue_absorb", pos, 3f, 1f);
                sound("max_blue_hum", pos, 3f, 1f);
                Vec3 core = pos.add(0, 0.5, 0);
                // The world caves toward a single point: huge collapsing lens, rings pouring in, the ground dragged along.
                Flashes.lens(core, 20f, 2f, BLUE, 0.8f, 20, now);
                for (int i = 0; i < 4; i++) Flashes.ring(core, 18f - i * 2, 0.5f, i % 2 == 0 ? BLUE_LIGHT : WHITE, 0.8f, 14, now + i * 4L);
                Flashes.ground(groundBelow(level, pos), 18f, 1f, BLUE, 0.8f, 20, now);
                Flashes.flash(core, 8f, 2f, BLUE_LIGHT, 0.9f, 10, now + 16);
                if (drawn) {
                    implode(level, core, 14, q(90), BLUE, 0.35f, 22);
                    debris(level, groundBelow(level, pos).add(0, 0.3, 0), q(30), 0.3);
                }
                distanceShake(pos, 50, 0.6f);
            }
            case "max_blue_collapse" -> {
                sound("max_blue_collapse", pos, 4f, 0.8f);
                Vec3 core = pos.add(0, 0.5, 0);
                // Everything is crushed to the core, then the release hits like a wall.
                Flashes.lens(core, 14f, 0.5f, BLUE, 1f, 5, now);
                Flashes.ring(core, 16f, 0.5f, WHITE, 1f, 5, now);
                Flashes.flash(core, 4f, 16f, WHITE, 1f, 10, now + 5);
                Flashes.ring(core, 1f, 22f, BLUE_LIGHT, 0.9f, 14, now + 5);
                Flashes.ground(groundBelow(level, pos), 1f, 20f, BLUE_LIGHT, 0.9f, 16, now + 6);
                if (drawn) {
                    sparks(level, core, Vec3.ZERO, q(60), 1.1, BLUE_LIGHT, 0.3f, 16);
                    debris(level, core, q(30), 0.8);
                }
                distanceShake(pos, 60, 0.8f);
            }
            case "max_red_charge" -> {
                sound("max_red_charge", pos, 1.8f, 1f);
                Flashes.lens(pos, 0.3f, 2.5f, RED, 0.6f, 14, now);
                Flashes.ring(pos, 3f, 0.2f, ORANGE, 0.8f, 14, now);
                if (drawn) {
                    implode(level, pos, 2.5, q(24), RED, 0.16f, 16);
                    sparks(level, pos, Vec3.ZERO, q(10), 0.4, ORANGE, 0.1f, 6);
                }
            }
            case "max_red_fire" -> {
                sound("max_red_fire", pos, 2.5f, 1f);
                Flashes.ring(pos, 4f, 0.2f, ORANGE, 1f, 3, now);
                Flashes.flash(pos, 5f, 1f, RED, 1f, 6, now + 2);
                for (int i = 0; i < 3; i++) Flashes.ripple(pos.add(dir.scale(1 + i * 1.8)), dir, 0.5f, 4f + i * 1.5f, ORANGE, 0.8f - i * 0.2f, 9, now + 2 + i);
                if (drawn) sparks(level, pos, dir, q(24), 0.9, ORANGE, 0.18f, 9);
                if (mine) {
                    ScreenEffects.shake(0.7f, 12);
                    ScreenEffects.fovPunch(0.1f);
                }
            }
            case "max_red_explosion" -> {
                sound("max_red_explosion", pos, 5f, 1f);
                sound("red_amplified", pos, 3f, 0.8f);
                Vec3 ground = groundBelow(level, pos);
                float k = Math.max(1f, s / 3f);
                // Compression, then catastrophic release: three shockwaves rolling across the ground and a fireball.
                Flashes.ring(pos, 5f * k, 0.3f, ORANGE, 1f, 4, now);
                Flashes.flash(pos, 2f * k, 0.3f, WHITE, 1f, 4, now);
                Flashes.flash(pos, 4f * k, 12f * k, WHITE, 1f, 8, now + 3);
                Flashes.flash(pos, 11f * k, 5f * k, RED, 0.85f, 20, now + 4);
                for (int i = 0; i < 3; i++) {
                    Flashes.ground(ground, 1f, (12f + i * 7f) * k, i == 0 ? WHITE : ORANGE, 0.95f - i * 0.2f, 16 + i * 4, now + 3 + i * 4L);
                }
                Flashes.ring(pos, 1f, 16f * k, ORANGE, 0.9f, 14, now + 3);
                Flashes.lens(pos, 2f, 12f * k, RED, 0.7f, 14, now + 3);
                if (drawn) {
                    sparks(level, pos, Vec3.ZERO, q(70), 1.8, ORANGE, 0.28f, 14);
                    burst(level, pos, q(30), 1.0, Sprite.GLOW, RED, 0.5f, 18);
                    burst(level, pos, q(18), 0.35, Sprite.SMOKE, GREY, 0.9f, 40);
                    debris(level, pos, q(50), 1.1);
                }
                // No impact frame: those are reserved for the top tier (Hollow Purple, the domain, finishers).
                distanceShake(pos, 80, 0.8f);
            }
            case "finisher" -> {
                sound("finisher", pos, 4f, 1f);
                Flashes.flash(pos, 6f, 1f, WHITE, 1f, 6, now);
                impactStar(pos, dir, 10, 6f, 0.15f, WHITE, now);
                Flashes.ring(pos, 0.5f, 10f, WHITE, 1f, 10, now);
                if (drawn) sparks(level, pos, dir, q(30), 1.2, GOLD, 0.25f, 14);
            }
            // --- Domain clash ---
            case "clash_start", "clash_sudden_death" -> {
                boolean sd = p.id().equals("clash_sudden_death");
                sound("clash_start", pos, 4f, sd ? 1.2f : 1f);
                // The JJS domain clash track, on everyone watching (it travels with them, not left at the midpoint).
                if (!sd && mc.player != null && mc.player.position().distanceTo(pos) < 64) {
                    stopSound("clash_music");
                    follow("clash_music", mc.player.getId(), 1f);
                }
                float r = Math.max(4f, s);
                Flashes.flash(pos, 3f, r * 0.8f, sd ? RED : WHITE, 1f, 10, now);
                Flashes.lens(pos, 1f, r, sd ? RED : BLUE_LIGHT, 0.9f, 16, now);
                for (int i = 0; i < 3; i++) Flashes.ground(groundBelow(level, pos), 1f, r * (0.8f + i * 0.35f), i == 0 ? WHITE : BLUE_LIGHT, 0.8f, 16, now + i * 3L);
                Flashes.beam(pos.add(0, -2, 0), pos.add(0, r * 1.5, 0), 1.2f, WHITE, 0.9f, 14, now);
                if (drawn) sparks(level, pos, Vec3.ZERO, q(40), 1.0, WHITE, 0.25f, 14);
                if (mc.player != null && mc.player.position().distanceTo(pos) < r * 3) {
                    ScreenEffects.shake(0.8f, 16);
                    ScreenEffects.fovPunch(-0.08f);
                }
            }
            case "clash_perfect" -> {
                // The duellist's own domain surges: a shockwave out through it, the barrier flares, the front is shoved.
                float tier = Math.max(1f, s);
                float[] c = clashColor(p.entityId());
                ClientState.Domain dom = ClientState.domainOwnedBy(p.entityId());
                float r = dom != null ? dom.radius : 10f;
                if (dom != null) {
                    dom.pulseTick = now;
                    dom.pulseStrength = tier;
                }
                sound("clash_perfect", pos, 1.2f + 0.3f * tier, 0.9f + 0.1f * tier);
                Flashes.flash(pos, 1.5f * tier, 0.3f, WHITE, 1f, 5, now);
                Flashes.lens(pos, 0.5f, r * (0.55f + 0.15f * tier), c, 0.55f + 0.1f * tier, 12, now);
                Flashes.ring(pos, 0.5f, r * 0.5f * tier, c, 0.8f, 10, now);
                Flashes.ground(groundBelow(level, pos), 0.5f, r * 0.9f, c, 0.7f + 0.1f * tier, 14, now + 1);
                if (dir.lengthSqr() > 1e-4) {
                    // The pulse rolls toward the opposing domain in rings.
                    Vec3 d = dir.normalize();
                    double reach = dir.length() * 0.6;
                    int rings = 2 + Math.round(tier);
                    for (int i = 0; i < rings; i++) {
                        Vec3 at = pos.add(d.scale(reach * (i + 1) / rings));
                        Flashes.ripple(at, d, 0.5f, 2f + tier * 1.2f, i % 2 == 0 ? c : WHITE, 0.8f, 8, now + i * 2L);
                    }
                    Flashes.beam(pos, pos.add(d.scale(reach)), 0.25f * tier, c, 0.8f, 7, now);
                }
                if (drawn) {
                    sparks(level, pos, Vec3.ZERO, q(Math.round(14 * tier)), 0.6 + 0.2 * tier, c, 0.16f, 10);
                    // Stars in the void flare outward from the duellist.
                    if (dom != null) sphereShell(level, dom.center, r * 0.85, q(Math.round(10 * tier)), WHITE, 0.2f, 12, 0.08);
                }
                if (mine) {
                    ScreenEffects.fovPunch(0.02f * tier);
                    ScreenEffects.shake(0.12f * tier, 5);
                }
            }
            case "clash_hit" -> {
                float[] c = clashColor(p.entityId());
                sound("clash_hit", pos, 0.8f, 0.9f + 0.2f * s);
                Flashes.ring(pos, 0.3f, 2.2f * s, c, 0.6f, 7, now);
                Flashes.lens(pos, 0.3f, 2.5f * s, c, 0.35f, 7, now);
                if (drawn) sparks(level, pos, Vec3.ZERO, q(5), 0.3, c, 0.1f, 6);
            }
            case "clash_miss" -> {
                // The domain falters: its energy flickers and caves in a little.
                ClientState.Domain dom = ClientState.domainOwnedBy(p.entityId());
                if (dom != null) dom.unstableUntil = now + 10;
                sound("clash_miss", pos, 0.6f, 0.9f + RNG.nextFloat() * 0.15f);
                Flashes.lens(pos, 2.2f, 0.3f, GREY, 0.45f, 6, now);
                if (drawn) burst(level, pos, q(5), 0.06, Sprite.SMOKE, GREY, 0.35f, 12);
                if (mine) ScreenEffects.shake(0.12f, 4);
            }
            case "clash_win" -> {
                // The winner's domain swallows the other.
                float r = Math.max(6f, s);
                float[] c = clashColor(p.entityId());
                sound("clash_win", pos, 5f, 1f);
                stopSound("clash_music");
                Vec3 ground = groundBelow(level, pos);
                Flashes.flash(pos, 3f, r, WHITE, 1f, 12, now);
                Flashes.lens(pos, 1f, r * 1.3f, c, 0.95f, 20, now);
                for (int i = 0; i < 3; i++) Flashes.ground(ground, 1f, r * (0.9f + i * 0.4f), i == 0 ? WHITE : c, 0.9f - i * 0.2f, 18 + i * 4, now + i * 4L);
                Flashes.beam(ground, ground.add(0, r * 2.2, 0), 2f, c, 1f, 24, now);
                if (drawn) sparks(level, pos, Vec3.ZERO, q(60), 1.4, c, 0.3f, 16);
                distanceShake(pos, r * 3, 0.8f);
                if (mc.player != null && mc.player.position().distanceTo(pos) < r * 2) ScreenEffects.impact(4);
            }
            case "domain_block" -> sound("domain_block", pos, 1f, 0.9f + RNG.nextFloat() * 0.2f);
            default -> HakariFx.play(p, mc, level, pos, dir, s, mine, now);
        }
    }

    /** The clash colour of whoever owns this entity's domain (as the clash announced it), as rgb floats. */
    private static float[] clashColor(int entityId) {
        dev.rick.jjk.client.clash.ClashClient.View v = dev.rick.jjk.client.clash.ClashClient.view();
        if (v != null) {
            for (int i = 0; i < v.entities.length; i++) {
                if (v.entities[i] == entityId) {
                    int c = v.colors[i];
                    return new float[] {(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
                }
            }
        }
        return BLUE_LIGHT;
    }

    // --- composite effects ---

    /** A crisp arc of light across the swing, plus a thin inner edge. */
    static void swingTrail(Vec3 pos, Vec3 dir, float reach, float width, long now) {
        if (dir.lengthSqr() < 1e-4) return;
        Vec3 d = dir.normalize();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        float tilt = RNG.nextFloat() - 0.5f;
        Vec3 prev = null;
        for (int i = 0; i <= 6; i++) {
            double t = (i / 6.0 - 0.5) * 2.2;
            Vec3 at = pos.add(side.scale(Math.sin(t) * reach)).add(d.scale(Math.cos(t) * reach * 0.5)).add(up.scale(Math.sin(t) * tilt * reach));
            if (prev != null) Flashes.beam(prev, at, width * (float) Math.cos(t * 0.6), WHITE, 0.7f, 4, now);
            prev = at;
        }
    }

    /** Thin radial streaks: the sharp "star" of a clean hit. */
    static void impactStar(Vec3 pos, Vec3 dir, int n, float length, float width, float[] c, long now) {
        for (int i = 0; i < n; i++) {
            Vec3 d = randomUnit();
            if (dir.lengthSqr() > 1e-4) d = d.add(dir.normalize().scale(0.8)).normalize();
            Flashes.beam(pos, pos.add(d.scale(length * (0.5 + RNG.nextDouble() * 0.5))), width, c, 0.9f, 3, now);
        }
    }

    /** Sparks flying mostly along {@code dir} (or in every direction when it is zero). */
    static void sparks(ClientLevel level, Vec3 pos, Vec3 dir, int n, double speed, float[] c, float size, int life) {
        Vec3 d = dir.lengthSqr() > 1e-4 ? dir.normalize() : Vec3.ZERO;
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(0.6).add(d).normalize().scale(speed * (0.4 + RNG.nextDouble() * 0.6));
            add(level, pos, v, Sprite.SPARK, c, 0.95f, size * (0.7f + RNG.nextFloat() * 0.6f), size * 0.1f, life + RNG.nextInt(Math.max(1, life / 2)))
                    .friction(0.85f);
        }
    }

    /** Chunks of the ground below {@code pos} thrown up and out. */
    static void debris(ClientLevel level, Vec3 pos, int n, double speed) {
        BlockPos below = BlockPos.containing(pos).below();
        BlockState ground = level.getBlockState(below);
        for (int k = 0; k < 4 && ground.isAir(); k++) {
            below = below.below();
            ground = level.getBlockState(below);
        }
        if (ground.isAir()) return;
        double y = below.getY() + 1.1;
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Mth.TWO_PI;
            double sp = speed * (0.4 + RNG.nextDouble() * 0.6);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), pos.x + Math.cos(a) * 0.5, y, pos.z + Math.sin(a) * 0.5,
                    Math.cos(a) * sp, 0.2 + RNG.nextDouble() * speed * 0.8, Math.sin(a) * sp);
        }
    }

    /** The first solid surface under {@code pos} (up to 6 blocks down), for effects that roll along the ground. */
    static Vec3 groundBelow(ClientLevel level, Vec3 pos) {
        BlockPos b = BlockPos.containing(pos);
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(b.below()).isAir()) return new Vec3(pos.x, b.getY() + 0.05, pos.z);
            b = b.below();
        }
        return pos;
    }

    static boolean isAttackerClose(Minecraft mc, Vec3 pos) {
        return mc.player != null && mc.player.position().distanceTo(pos) < 3.5;
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
        if (lod <= 0) return 0;
        return Math.max(1, Math.round(n * f * lod));
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

    /** Fading silhouette-ish cloud where someone just vanished. */
    static void afterimage(ClientLevel level, Vec3 pos) {
        for (int i = 0; i < q(16); i++) {
            Vec3 at = pos.add(gauss(0.25), gauss(0.6), gauss(0.25));
            add(level, at, new Vec3(0, 0.01, 0), Sprite.GLOW, BLUE_LIGHT, 0.5f, 0.25f, 0.02f, 10);
        }
        flashAt(level, pos, 1.2f, WHITE, 3);
    }

    static void victimFeedback(FxPayload p, float strength) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && p.entityId() == mc.player.getId()) {
            ScreenEffects.shake(strength, 8);
            ScreenEffects.flash(0x30FF2020, 4);
        }
    }

    static void distanceShake(Vec3 pos, double radius, float max) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(pos);
        if (d < radius) ScreenEffects.shake((float) (max * (1 - d / radius)), 12);
    }

    /**
     * A sound that travels with an entity (music belonging to someone: Jackpot, Hollow Purple, the 0.2 Domain), so it
     * isn't left behind where it started. Falls back to the local player when the entity isn't known here.
     */
    public static void follow(String name, int entityId, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float v = volume * JJKConfig.get().client.soundVolume;
        if (v <= 0 || mc.getSoundManager().getSoundEvent(ModSounds.get(name).location()) == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) e = mc.player;
        if (e == null) return;
        mc.getSoundManager().play(new net.minecraft.client.resources.sounds.EntityBoundSoundInstance(ModSounds.get(name), SoundSource.PLAYERS, v, 1f, e,
                RNG.nextLong()));
    }

    /** Cuts a playing sound short (domain music when the domain ends). */
    public static void stopSound(String name) {
        Minecraft.getInstance().getSoundManager().stop(dev.rick.jjk.JJK.id(name), SoundSource.PLAYERS);
    }

    public static void sound(String name, Vec3 pos, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float v = volume * JJKConfig.get().client.soundVolume;
        if (v <= 0) return;
        // Events with no audio yet (music whose Roblox asset needs a login) stay silent instead of logging a warning.
        if (mc.getSoundManager().getSoundEvent(ModSounds.get(name).location()) == null) return;
        mc.level.playLocalSound(pos.x, pos.y, pos.z, ModSounds.get(name), SoundSource.PLAYERS, v, pitch, false);
    }

    public static Vec3 randomUnit() {
        double z = RNG.nextDouble() * 2 - 1;
        double a = RNG.nextDouble() * Mth.TWO_PI;
        double r = Math.sqrt(1 - z * z);
        return new Vec3(r * Math.cos(a), z, r * Math.sin(a));
    }

    static double gauss(double s) {
        return RNG.nextGaussian() * s;
    }
}
