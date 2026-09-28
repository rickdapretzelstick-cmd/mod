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
            // The opening, after Jujutsu Shenanigans: slash, helix band, the white flood, white-out, the rush of trains.
            int[][] opening = {{2, 0}, {7, 1}, {10, 2}, {22, 3}, {18, 4}, {9, 5}, {6, 6}, {12, 7}, {14, 8}, {12, 9}, {30, 10}};
            String[] names = {"slash", "band_open", "band", "band_close", "flood", "flood_walls", "white", "rush", "rush_trains", "tumble", "settled"};
            for (int[] step : opening) {
                ctx.waitTicks(step[0]);
                ctx.takeScreenshot("h12_idg_" + step[1] + "_" + names[step[1]]);
            }
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

            // --- Domain clash, Gojo vs Hakari: the space splits, the winner consumes the loser. Once each way. ---
            domainClash(ctx, server, in, ult, true, "h26");
            domainClash(ctx, server, in, ult, false, "h27");

            // --- Back to Gojo through the same screen (once Jackpot is over and the fight has cooled). ---
            server.runCommand("jjk domain cancel all");
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

    /**
     * A Gojo dummy opens Unlimited Void in front of the player, who answers with Idle Death Gamble. The player either
     * plays the chart (Hakari wins and his side consumes the Void) or doesn't press anything (the Void consumes the
     * casino). Screenshots: the split at the start, the duel with the lanes over it, the conquest, the result.
     */
    private static void domainClash(ClientGameTestContext ctx, TestServerContext server, TestInput in, KeyMapping ult, boolean play, String tag) {
        server.runCommand("jjk domain cancel all");
        server.runCommand("execute as @a run jjk awakening end");
        server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
        ctx.waitTicks(100);
        server.runCommand("execute as @a run jjk reset");
        server.runCommand("execute as @a run jjk restore now");
        server.runCommand("execute as @a run jjk awakening 100");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 6");
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            var level = p.level();
            var rival = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
            var look = p.getLookAngle().multiply(1, 0, 1).normalize();
            rival.setPos(p.getX() + look.x * 10, p.getY(), p.getZ() + look.z * 10);
            rival.setYRot(p.getYRot() + 180);
            rival.setCustomName(net.minecraft.network.chat.Component.literal("Gojo"));
            level.addFreshEntity(rival);
            dev.rick.jjk.core.character.CharacterService.assign(rival, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.gojo.GojoCharacter.ID));
            dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(rival, play ? 0.3f : 0.9f);
            var rc = Casters.get(rival);
            rc.enterAwakening();
            rc.setAwakening(rc.maxAwakening());
            rc.setNoCost(true);
            if (!rc.input(dev.rick.jjk.core.ability.AbilitySlot.ULTIMATE, true, 0, 0, null)) throw new AssertionError("Gojo should start opening a domain");
        });
        ctx.waitTicks(6);
        in.pressKey(ult);
        for (int i = 0; i < 60 && !ctx.computeOnClient(mc -> dev.rick.jjk.client.clash.ClashClient.playing()); i++) ctx.waitTick();
        if (!ctx.computeOnClient(mc -> dev.rick.jjk.client.clash.ClashClient.playing())) throw new AssertionError("the two domains should clash");
        ctx.waitTicks(24);
        if (!ctx.computeOnClient(mc -> mc.player != null && ClientState.domainOwnedBy(mc.player.getId()) != null
                && ClientState.domainOwnedBy(mc.player.getId()).splitWith >= 0)) throw new AssertionError("the clash should split the space");
        int[] keys = {InputConstants.KEY_LEFT, InputConstants.KEY_DOWN, InputConstants.KEY_UP, InputConstants.KEY_RIGHT};
        Set<Integer> pressed = new HashSet<>();
        int shots = 0, ticks = 0;
        for (int tick = 0; tick < 900 && ctx.computeOnClient(mc -> dev.rick.jjk.client.clash.ClashClient.view() != null); tick++, ticks++) {
            if (play) {
                int[] due = ctx.computeOnClient(mc -> {
                    var cv = dev.rick.jjk.client.clash.ClashClient.view();
                    if (cv == null) return new int[0];
                    double clock = cv.clock();
                    java.util.List<Integer> out = new java.util.ArrayList<>();
                    for (int i = 0; i < cv.times.length; i++) if (Math.abs(cv.times[i] - clock) <= 0.5) out.add(cv.round * 1000 + i);
                    return out.stream().mapToInt(Integer::intValue).toArray();
                });
                for (int id : due) {
                    if (!pressed.add(id)) continue;
                    int lane = ctx.computeOnClient(mc -> {
                        var cv = dev.rick.jjk.client.clash.ClashClient.view();
                        return cv == null || id % 1000 >= cv.lanes.length ? -1 : (int) cv.lanes[id % 1000];
                    });
                    if (lane >= 0) in.pressKey(keys[lane]);
                }
            }
            if (shots < 3 && ticks >= 16 + shots * 30) {
                ctx.takeScreenshot(tag + (shots == 0 ? "_clash_split" : "_clash_battle_" + shots));
                shots++;
            }
            ctx.waitTick();
        }
        // The duel is decided: the winner's side sweeps across (about three seconds).
        for (int i = 0; i < 4; i++) {
            ctx.waitTicks(i == 0 ? 8 : 15);
            ctx.takeScreenshot(tag + "_conquest_" + i);
        }
        ctx.waitTicks(30);
        ctx.takeScreenshot(tag + "_conquered");
        String result = server.computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            var mine = dev.rick.jjk.core.domain.DomainManager.ownedBy(p);
            if (play) return mine != null && mine.phase() == dev.rick.jjk.core.domain.DomainInstance.Phase.ACTIVE && mine.annexes().size() == 1
                    ? "" : "Hakari should have won and taken the Void's space (" + (mine == null ? "none" : mine.phase() + ", " + mine.annexes().size()) + ")";
            return mine == null ? "" : "Gojo should have won and consumed the casino";
        });
        if (!result.isEmpty()) throw new AssertionError(result);
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
