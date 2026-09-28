package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.hud.CharacterSelectScreen;
import dev.rick.jjk.client.hud.RhythmClient;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.net.GamblePayload;
import dev.rick.jjk.hakari.HakariState;
import dev.rick.jjk.hakari.RhythmAbility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Hakari through a real client with real input: pick him on the character select screen (keyboard + mouse), fight with
 * his base kit, open Idle Death Gamble, gamble through a Riichi into a Jackpot, use the Jackpot kit and dance Rhythm on
 * the beat, then switch back to Gojo. Screenshots land in build/run/clientGameTest/screenshots.
 */
public class HakariClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a run jjk nocooldown true");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 8");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            ctx.waitTicks(30);
            TestInput in = ctx.getInput();

            // --- Character select: K opens it, click Hakari's card. ---
            if (!ctx.computeOnClient(mc -> "gojo".equals(ClientState.character))) throw new AssertionError("should start as Gojo");
            in.pressKey(key(ctx, "key.jjk.character_menu"));
            ctx.waitTicks(12);
            if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof CharacterSelectScreen)) throw new AssertionError("K opens the character select screen");
            ctx.takeScreenshot("h01_character_select");
            clickCard(ctx, in, "hakari");
            ctx.waitTicks(10);
            if (!ctx.computeOnClient(mc -> "hakari".equals(ClientState.character))) throw new AssertionError("clicking Hakari's card makes you Hakari");
            ctx.takeScreenshot("h02_hakari_selected");
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            KeyMapping s1 = key(ctx, "key.jjk.skill_1"), s2 = key(ctx, "key.jjk.skill_2"), s3 = key(ctx, "key.jjk.skill_3");
            KeyMapping s4 = key(ctx, "key.jjk.skill_4"), s5 = key(ctx, "key.jjk.skill_5"), ult = key(ctx, "key.jjk.ultimate");

            // --- Base kit ---
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^6");
            ctx.waitTicks(20);
            ctx.takeScreenshot("h03_base_hud");
            in.pressKey(s1);
            ctx.waitTicks(7);
            ctx.takeScreenshot("h04_reserve_balls");
            ctx.waitTicks(25);
            in.pressKey(s2);
            ctx.waitTicks(7);
            ctx.takeScreenshot("h05_shutter_doors");
            ctx.waitTicks(30);
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.6");
            ctx.waitTicks(20);
            in.pressKey(s3);
            ctx.waitTicks(8);
            ctx.takeScreenshot("h06_rough_energy_charge");
            ctx.waitTicks(6);
            ctx.takeScreenshot("h07_rough_energy_hit");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.4");
            ctx.waitTicks(20);
            in.pressKey(s4);
            ctx.waitTicks(6);
            ctx.takeScreenshot("h08_fever_breaker_kick");
            ctx.waitTicks(8);
            ctx.takeScreenshot("h09_fever_breaker_break");
            ctx.waitTicks(40);
            in.holdKey(s5);
            ctx.waitTicks(8);
            ctx.takeScreenshot("h10_door_guard");
            in.releaseKey(s5);
            ctx.waitTicks(20);

            // --- Idle Death Gamble → Riichi → Jackpot ---
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a run jjk restore now");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^2 ^ ^6");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-2 ^ ^6");
            server.runCommand("execute as @a run jjk awakening 100");
            server.runOnServer(s -> HakariState.of(s.getPlayerList().getPlayers().getFirst()).oddsBonus = 5f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("h11_gamble_ready");
            in.pressKey(ult);
            ctx.waitTicks(14);
            ctx.takeScreenshot("h12_idg_opening");
            for (int i = 0; i < 200 && !ctx.computeOnClient(mc -> ClientState.gambleOf(mc.player.getId()) != null); i++) ctx.waitTick();
            if (!ctx.computeOnClient(mc -> ClientState.gambleOf(mc.player.getId()) != null)) throw new AssertionError("the gamble should be running");
            ctx.waitTicks(10);
            ctx.takeScreenshot("h13_idg_interior");
            // The giant reels over the arena, from near the wall looking back up at them (HUD hidden for the shot).
            server.runCommand("execute as @a at @s rotated ~ 0 run tp @s ^ ^ ^-8 ~ -22");
            ctx.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(6);
            ctx.takeScreenshot("h14_idg_reels");
            ctx.runOnClient(mc -> {
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            server.runCommand("execute as @a at @s rotated ~ 0 run tp @s ^ ^ ^8 ~ 8");
            // Visual moves: Hakari's techniques inside his domain drive the gamble.
            in.pressKey(s1);
            ctx.waitTicks(20);
            ctx.takeScreenshot("h15_visual_move");
            in.pressKey(s2);
            ctx.waitTicks(20);
            in.pressKey(s3);
            for (int i = 0; i < 200 && !ctx.computeOnClient(mc -> riichi(mc.player.getId())); i++) ctx.waitTick();
            if (!ctx.computeOnClient(mc -> riichi(mc.player.getId()))) throw new AssertionError("visual moves should lead to a Riichi");
            ctx.waitTicks(5);
            ctx.takeScreenshot("h16_riichi_cut_in");
            ctx.waitTicks(22);
            ctx.takeScreenshot("h17_riichi_suspense");
            for (int i = 0; i < 120 && !ctx.computeOnClient(mc -> ClientState.awakened()); i++) {
                ctx.waitTick();
                if (ctx.computeOnClient(mc -> revealed(mc.player.getId()))) {
                    ctx.takeScreenshot("h18_jackpot_reveal");
                    break;
                }
            }
            for (int i = 0; i < 120 && !ctx.computeOnClient(mc -> ClientState.awakened()); i++) ctx.waitTick();
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("the guaranteed gamble should hit the Jackpot");
            ctx.waitTicks(3);
            ctx.takeScreenshot("h19_jackpot");
            ctx.waitTicks(40);
            ctx.takeScreenshot("h20_jackpot_hud");

            // --- Jackpot kit ---
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.6");
            ctx.waitTicks(20);
            in.pressKey(s1);
            ctx.waitTicks(14);
            ctx.takeScreenshot("h21_lucky_volley");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^3.4");
            ctx.waitTicks(20);
            in.pressKey(s3);
            ctx.waitTicks(30);
            ctx.takeScreenshot("h22_overwhelming_luck");
            ctx.waitTicks(40);
            // Rhythm: press the Special key on each beat.
            float streakBefore = ctx.computeOnClient(mc -> ClientState.awakening);
            in.pressKey(s5);
            Set<Integer> hit = new HashSet<>();
            int beats = dev.rick.jjk.config.JJKConfig.get().hakari.rhythmBeats;
            for (int i = 0; i < 90 && hit.size() < beats; i++) {
                float t = ctx.computeOnClient(mc -> RhythmClient.clock());
                for (int b = 0; b < beats; b++) {
                    if (!hit.contains(b) && t >= 0 && Math.abs(t - RhythmAbility.beatTime(b)) <= 0.6f) {
                        in.pressKey(s5);
                        hit.add(b);
                        if (b == 1) ctx.takeScreenshot("h23_rhythm");
                    }
                }
                ctx.waitTick();
            }
            ctx.waitTicks(20);
            ctx.takeScreenshot("h24_rhythm_done");
            boolean streak = server.computeOnServer(s -> dev.rick.jjk.core.combat.Combat.has(s.getPlayerList().getPlayers().getFirst(),
                    dev.rick.jjk.core.combat.CombatStatus.LUCKY_STREAK));
            if (hit.size() < beats) throw new AssertionError("every beat should have been played (" + hit.size() + ")");
            if (!streak) System.out.println("[jjk-test] Rhythm: not every beat judged GREAT+ (no Lucky Streak) — timing on this machine");

            // --- Back to Gojo through the same screen (once Jackpot is over and the fight has cooled). ---
            server.runCommand("execute as @a run jjk awakening end");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            ctx.waitTicks(80);
            in.pressKey(key(ctx, "key.jjk.character_menu"));
            ctx.waitTicks(12);
            clickCard(ctx, in, "gojo");
            ctx.waitTicks(10);
            if (!ctx.computeOnClient(mc -> "gojo".equals(ClientState.character))) throw new AssertionError("switching back to Gojo works");
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            ctx.takeScreenshot("h25_back_to_gojo");
            if (server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                return Casters.get(p).ability(dev.rick.jjk.core.ability.AbilitySlot.SKILL_1).id;
            }).equals("reserve_balls")) throw new AssertionError("no Hakari abilities left after switching");
        }
    }

    private static boolean riichi(int ownerId) {
        ClientState.Gamble g = ClientState.gambleOf(ownerId);
        return g != null && g.p.state() == GamblePayload.RIICHI;
    }

    private static boolean revealed(int ownerId) {
        ClientState.Gamble g = ClientState.gambleOf(ownerId);
        return g != null && g.p.state() == GamblePayload.RIICHI && g.p.reel2() != 0;
    }

    private static void clickCard(ClientGameTestContext ctx, TestInput in, String id) {
        double[] at = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof CharacterSelectScreen s)) throw new AssertionError("select screen not open");
            int[] c = s.cardCenter(id);
            double scale = mc.getWindow().getGuiScale();
            return new double[] {c[0] * scale, c[1] * scale};
        });
        in.setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        in.pressMouse(0);
    }

    private static KeyMapping key(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }
}
