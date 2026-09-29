package dev.rick.jjk.test;

import dev.rick.jjk.client.fx.ClientFx;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Every client effect, one after another, in front of a fixed camera: each is played on a training dummy (so the ones
 * that follow an entity have one) and screenshotted twice, just after it starts and a few ticks on; then the long
 * sequences (Unlimited Purple's whole fuse) shot by shot. For judging and comparing the effects side by side, not for
 * asserting anything. Not run by default: list it under {@code fabric-client-gametest} in the test mod's
 * fabric.mod.json to run it. A {@code build/fxgallery.txt} (the run directory is wiped before each run) limits it to the
 * ids and sequences listed there, one per line. Screenshots land in build/run/clientGameTest/screenshots as
 * {@code fx_<n>_<id>_a/b} and {@code seq_<name>_<tick>}.
 */
public class FxGalleryClientTest implements FabricClientGameTest {
    /** Effects too big for the close camera: played farther off. */
    private static final List<String> FAR = List.of("domain_charge", "domain_expand", "domain_counter", "domain_sealed", "domain_clash",
            "domain_clash_end", "domain_collapse", "awaken_start", "awaken", "max_blue_cast", "max_blue_spawn", "max_blue_collapse",
            "max_red_explosion", "purple_fire", "purple_end", "red_amplified", "clash_start", "clash_sudden_death", "clash_win", "jackpot",
            "idg_charge", "overwhelm_final", "sukuna_awaken", "world_slash", "open_pillar", "dismantle_line", "shrine_charge", "shrine_slash",
            "finisher", "purple_charged", "purple_fusion");

    /** What each effect's scale means on the server, where it isn't 1. */
    private static final Map<String, Float> SCALE = Map.ofEntries(Map.entry("purple_fire", 3.2f), Map.entry("purple_trail", 3.2f),
            Map.entry("purple_end", 3.2f), Map.entry("purple_charged", 2.5f), Map.entry("purple_fusion", 3f), Map.entry("domain_expand", 20f),
            Map.entry("domain_sealed", 12f), Map.entry("domain_collapse", 20f), Map.entry("clash_start", 12f), Map.entry("clash_win", 12f),
            Map.entry("open_pillar", 4f), Map.entry("world_slash", 30f), Map.entry("dismantle_line", 20f), Map.entry("dismantle_slash", 5f),
            Map.entry("jackpot", 7f), Map.entry("rhythm_start", 10f), Map.entry("rhythm_streak", 3f), Map.entry("sukuna_awaken", 30f),
            Map.entry("wcs_chant", 1f), Map.entry("max_red_fire", 2f), Map.entry("red_fire", 1.5f), Map.entry("hit_heavy", 1.2f),
            Map.entry("black_flash_kokusen", 2f), Map.entry("teleport_in", 1f), Map.entry("teleport_out", 1f));

    private static final String[] IDS = {"limitless_shatter", "swing", "hit_light", "hit_heavy", "hit_launch", "hit_slam", "heavy_charge",
            "block", "parry", "guard_break", "evade", "dash", "ground_impact", "infinity_on", "infinity_off", "infinity_collapse",
            "infinity_ripple", "infinity_hold", "blue_cast", "blue_spawn", "blue_debris", "blue_hit", "blue_collapse", "red_charge", "red_full",
            "red_fire", "red_pointblank", "red_fizzle", "red_explosion", "red_amplified", "red_hit", "purple_blue", "purple_red",
            "purple_fusion", "purple_charged", "purple_fire", "purple_trail", "purple_hit", "purple_end", "teleport_out", "teleport_in",
            "domain_charge", "domain_expand", "domain_counter", "domain_sealed", "domain_clash", "domain_collapse", "domain_surehit",
            "domain_surehit_tick", "awaken_start", "awaken", "awaken_end", "max_blue_cast", "max_blue_spawn", "max_blue_collapse",
            "max_red_charge", "max_red_fire", "max_red_explosion", "finisher", "clash_start", "clash_sudden_death", "clash_perfect", "clash_hit",
            "clash_miss", "clash_win", "ball_throw", "ball_hit", "ball_ricochet", "shutter_appear", "shutter_appear_upright", "shutter_slam",
            "shutter_crush", "rough_charge", "rough_impact", "rough_hit", "fever_swing", "fever_kick", "fever_rush", "fever_impact",
            "fever_break", "door_guard_up", "door_guard_block", "door_guard_counter", "door_guard_down", "idg_charge", "idg_ambient",
            "gamble_visual", "gamble_riichi", "gamble_signal", "gamble_hit", "gamble_miss", "jackpot", "jackpot_aura", "jackpot_heal",
            "jackpot_end", "lucky_hit", "lucky_flurry", "lucky_final", "rushdown_throw", "overwhelm_final_hit", "surge_kick_hit",
            "rushdown_start", "rushdown_step", "rushdown_grab", "rushdown_drag", "overwhelm_charge", "overwhelm_punch", "overwhelm_hit",
            "overwhelm_final", "surge_dash", "surge_hit", "surge_vanish", "surge_appear", "surge_kick", "rhythm_start", "rhythm_beat",
            "rhythm_streak", "combo_doors", "shutter_linger", "door_bounce", "shutter_shatter", "shutter_finisher", "door_counter_punch",
            "rough_stomp", "fever_suspend", "fever_crush", "lucky_finisher", "rushdown_finisher", "renewal_mark", "renewal_trail", "renewal",
            "cursed_strikes_start", "cursed_strikes_slide", "cursed_strikes_punch", "cursed_strikes_kick", "cursed_strikes_floor",
            "cursed_strikes_finisher", "cursed_strikes_spin", "cursed_strikes_dive", "cursed_strikes_impact", "crushing_charge",
            "crushing_dash", "crushing_grab", "crushing_slam", "crushing_impact", "crushing_suplex", "crushing_finisher", "divergent_charge",
            "divergent_flash", "divergent_punch", "divergent_impact", "divergent_hit", "divergent_shatter", "black_flash_ready", "black_flash",
            "black_flash_kokusen", "black_flash_finisher", "manji_startup", "manji_dodge", "manji_swoop", "manji_hit", "manji_crush",
            "instincts_feint", "instincts_throw", "prop_smash", "sukuna_start", "sukuna_awaken", "sukuna_aura", "sukuna_end", "shrine_swing",
            "shrine_hit_light", "shrine_hit_heavy", "shrine_hit_launch", "shrine_hit_slam", "cleave_reach", "cleave_grab", "cleave",
            "cleave_finisher", "cleave_hit", "cleave_dice", "dismantle_windup", "dismantle_air_start", "dismantle_swing", "dismantle_slash",
            "dismantle_hit", "dismantle_line", "dismantle_finisher", "dismantle_dismember", "dismantle_halve", "world_slash_halve",
            "wcs_chant", "world_slash", "world_slash_hit", "open_hands", "open_clap", "open_draw", "open_fire", "open_trail", "open_pillar",
            "open_hit", "open_burn", "rush_start", "rush_step", "rush_hit", "rush_knee", "rush_slam", "shrine_charge", "shrine_slash"};

    /** Sequences played start to finish: {name, distance}. */
    private static final String[][] SEQUENCES = {{"unlimited_purple_far", "17"}, {"unlimited_purple_near", "10"}};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        java.util.Set<String> only = new java.util.HashSet<>();
        try {
            java.nio.file.Path list = java.nio.file.Path.of("../../fxgallery.txt");
            if (java.nio.file.Files.exists(list)) for (String l : java.nio.file.Files.readAllLines(list)) if (!l.isBlank()) only.add(l.trim());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 4");
            ctx.waitTicks(30);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^4");
            ctx.waitTicks(10);
            int n = 0;
            String near = "near";
            for (String id : IDS) {
                if (!only.isEmpty() && !only.contains(id)) {
                    n++;
                    continue;
                }
                boolean far = FAR.contains(id);
                String place = far ? "far" : "near";
                if (!place.equals(near)) {
                    near = place;
                    dummyAt(server, far ? 16 : 4);
                    ctx.waitTicks(6);
                }
                float scale = SCALE.getOrDefault(id, 1f);
                int index = n++;
                ctx.runOnClient(mc -> {
                    Flashes.clear();
                    mc.particleEngine.clearParticles();
                    mc.getSoundManager().stop();
                });
                ctx.waitTicks(3);
                ctx.runOnClient(mc -> {
                    Entity dummy = null;
                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (e != mc.player && e.distanceTo(mc.player) < 20 && e.getType() == dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY) dummy = e;
                    }
                    Vec3 at = dummy != null ? dummy.position().add(0, 1, 0) : mc.player.getEyePosition().add(0, -0.6, far ? 16 : 5);
                    int entity = id.equals("blue_debris") || id.equals("prop_smash") ? Block.getId(Blocks.STONE.defaultBlockState())
                            : dummy != null ? dummy.getId() : -1;
                    Vec3 dir = id.startsWith("teleport") ? new Vec3(3, 0, 0) : new Vec3(1, 0, 0);
                    ClientFx.handle(new FxPayload(id, at, dir, scale, entity));
                });
                ctx.waitTicks(1);
                ctx.takeScreenshot(String.format("fx_%03d_%s_a", index, id));
                ctx.waitTicks(5);
                ctx.takeScreenshot(String.format("fx_%03d_%s_b", index, id));
            }
            // From the arena's edge, looking back across it.
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~-17 0 -6");
            ctx.waitTicks(10);
            for (String[] seq : SEQUENCES) {
                if (!only.isEmpty() && !only.contains(seq[0])) continue;
                int distance = Integer.parseInt(seq[1]);
                dummyAt(server, distance);
                ctx.waitTicks(8);
                ctx.runOnClient(mc -> {
                    Flashes.clear();
                    mc.particleEngine.clearParticles();
                });
                if (seq[0].startsWith("unlimited_purple")) unlimitedPurple(ctx, seq[0], distance);
            }
        }
    }

    private static void dummyAt(TestServerContext server, int distance) {
        server.runCommand("execute as @a at @s rotated ~ 0 run tp @e[type=jjk:training_dummy,distance=..60] ^ ^ ^" + distance);
    }

    /** Max Red into the lingering Max Blue orb: the fuse (60 ticks, radius 16) and the detonation, shot by shot. */
    private static void unlimitedPurple(ClientGameTestContext ctx, String name, int distance) {
        Vec3[] at = new Vec3[1];
        long start = ctx.computeOnClient(mc -> {
            at[0] = mc.player.getEyePosition().add(mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(distance)).add(0, 0.5, 0);
            ClientFx.handle(new FxPayload("unlimited_purple", at[0], new Vec3(60, 0, 0), 16f, -1));
            return mc.level.getGameTime();
        });
        // Timed by the game clock: a screenshot costs the test harness a tick of its own.
        for (int shot : new int[] {1, 3, 8, 16, 30, 45, 52, 57}) {
            ctx.waitFor(mc -> mc.level.getGameTime() - start >= shot);
            ctx.takeScreenshot(String.format("seq_%s_%02d", name, shot));
        }
        ctx.waitFor(mc -> mc.level.getGameTime() - start >= 60);
        long boom = ctx.computeOnClient(mc -> {
            ClientFx.handle(new FxPayload("unlimited_purple_end", at[0], Vec3.ZERO, 16f, -1));
            return mc.level.getGameTime();
        });
        for (int shot : new int[] {1, 3, 7, 15, 35}) {
            ctx.waitFor(mc -> mc.level.getGameTime() - boom >= shot);
            ctx.takeScreenshot(String.format("seq_%s_%02d", name, 60 + shot));
        }
        ctx.waitTicks(30);
    }
}
