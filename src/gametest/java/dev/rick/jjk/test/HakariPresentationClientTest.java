package dev.rick.jjk.test;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.clash.ClashClient;
import dev.rick.jjk.client.hud.RhythmClient;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.net.GamblePayload;
import dev.rick.jjk.hakari.HakariState;
import dev.rick.jjk.hakari.RhythmAbility;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Films Hakari's presentation with real input: his whole base kit and its combinations, a domain clash against Gojo,
 * then how to hit the Jackpot (the domain, visual moves, a missed Riichi and the one that hits) and the Jackpot kit.
 * Same recording format as {@link PresentationClientTest} ({@code frames.txt}); build it with
 * {@code tools/make_hakari_video.py}. Not registered by default: point the {@code fabric-client-gametest} entrypoint at
 * this class to record.
 */
public class HakariPresentationClientTest extends PresentationClientTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            server = sp.getServer();
            in = ctx.getInput();
            cmd("time set noon");
            cmd("gamerule advance_time false");
            cmd("weather clear");
            ctx.waitTicks(40);
            cmd("execute as @a at @s run jjk arena");
            cmd("execute as @a run jjk nocooldown true");
            cmd("jjk character hakari @a");
            cmd("effect give @a minecraft:resistance infinite 4 true");
            ctx.waitTicks(5);
            if (!ctx.computeOnClient(mc -> "hakari".equals(ClientState.character))) throw new AssertionError("the player should be Hakari");
            center = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                return new double[] {p.getX(), p.getY(), p.getZ()};
            });
            clearDummies();
            resetPlayer();
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            KeyMapping z = key("key.jjk.skill_1"), x = key("key.jjk.skill_2"), c = key("key.jjk.skill_3"), v = key("key.jjk.skill_4");
            KeyMapping b = key("key.jjk.skill_5"), g = key("key.jjk.ultimate");

            // --- Base kit ---
            section("Reserve Balls (Z): a ricocheting steel ball that stuns from range");
            ShowcaseCamera.set(60, 7f, 2f, 3f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^8");
            ctx.waitTicks(10);
            film(6);
            in.pressKey(z);
            film(40);
            section("Reserve Balls up close (within 15 studs): ragdolls them away");
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^3");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(z);
            film(40);

            section("Shutter Doors (X): the doors close on them, and you're on your 3rd hit");
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^3.2");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(x);
            film(14);
            for (int i = 0; i < 2; i++) {
                in.pressKey(o -> o.keyAttack);
                film(8);
            }
            film(20);

            section("Combination: Shutter Doors during Reserve Balls' wind-up");
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^7");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(z);
            in.pressKey(x);
            film(70);

            section("Missed doors linger for 7s: jump on them to bounce high...");
            resetPlayer();
            clearDummies();
            cmd("jjk restore now");
            ShowcaseCamera.set(75, 8f, 2f, 2f);
            ctx.waitTicks(4);
            film(4);
            in.pressKey(x);
            film(16);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^6");
            // Onto the doors.
            cmd("execute as @a at @s rotated ~ 0 run tp @s ^ ^3.4 ^2.4");
            film(10);
            section("...then Rough Energy from that height: an unblockable, double-damage stomp");
            film(6);
            in.pressKey(c);
            film(44);

            section("Rough Energy (C): a long wind-up, then an unblockable punch");
            resetPlayer();
            clearDummies();
            ShowcaseCamera.set(62, 5f, 1.2f, 1.5f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.8");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(c);
            film(44);

            section("Fever Breaker (V): kick, hold them at the doors, dropkick where you face");
            resetPlayer();
            clearDummies();
            cmd("jjk restore now");
            ShowcaseCamera.set(65, 6f, 1.5f, 2f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.6");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(v);
            film(50);

            section("Fever Crush: Shutter Doors during Fever Breaker's wind-up, then an axe kick");
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.8");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(v);
            in.pressKey(x);
            film(50);

            section("Door Guard (hold B): a punch in the first 0.6s is punched back through the doors");
            resetPlayer();
            clearDummies();
            ShowcaseCamera.set(75, 5f, 1f, 2f);
            cmd("execute as @a at @s run jjk dummy fight 1");
            ctx.waitTicks(10);
            for (int i = 0; i < 4; i++) {
                in.holdKey(b);
                film(16);
                in.releaseKey(b);
                film(10);
            }
            clearDummies();

            // --- Domain clash with Gojo ---
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.combat.Statuses.remove(p, CombatStatus.BURNOUT);
                var cst = Casters.get(p);
                cst.setAwakening(cst.maxAwakening());
            });
            resetPlayer();
            cmd("jjk restore now");
            cmd("execute as @a at @s run tp @s ~ ~ ~ 120 8");
            ShowcaseCamera.set(35, 9f, 2.5f, 6f);
            ctx.waitTicks(20);
            section("Gojo opens Infinite Void...");
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var level = p.level();
                var rival = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                rival.setPos(p.getX() + look.x * 11, p.getY(), p.getZ() + look.z * 11);
                rival.setYRot(p.getYRot() + 180);
                rival.setCustomName(net.minecraft.network.chat.Component.literal("Gojo"));
                level.addFreshEntity(rival);
                dev.rick.jjk.core.character.CharacterService.assign(rival, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.gojo.GojoCharacter.ID));
                dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(rival, 0.45f);
                var rc = Casters.get(rival);
                rc.enterAwakening();
                rc.setAwakening(rc.maxAwakening());
                rc.setNoCost(true);
                rc.input(dev.rick.jjk.core.ability.AbilitySlot.SKILL_4, true, 0, 0, null);
            });
            film(8);
            section("...Hakari answers with Idle Death Gamble (G on a full meter)");
            in.pressKey(g);
            film(60);
            section("Domain clash: hit the arrows on the beat; the split between the domains follows the duel");
            for (int i = 0; i < 900 && ctx.computeOnClient(mc -> ClashClient.view() != null); i++) film(1);
            section("Hakari wins: his side sweeps across and consumes Infinite Void");
            film(70);
            section("The whole space is his");
            film(30);
            cmd("execute as @a run jjk domain cancel all");
            clearDummies();
            film(40);

            // --- How to hit the Jackpot ---
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.combat.Statuses.remove(p, CombatStatus.BURNOUT);
                var cst = Casters.get(p);
                cst.setAwakening(cst.maxAwakening());
                // The first Riichi misses, the second one hits (so both show).
                HakariState.of(p).oddsBonus = -5f;
            });
            resetPlayer();
            cmd("jjk restore now");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^2 ^ ^5");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-2 ^ ^5");
            ShowcaseCamera.set(40, 8f, 2.5f, 3f);
            ctx.waitTicks(20);
            section("How to hit the Jackpot: open Idle Death Gamble (G on a full meter)");
            film(6);
            in.pressKey(g);
            film(120);
            section("Everyone caught is frozen while the rules are imparted");
            film(30);
            section("Land 2 visual moves: Reserve Balls, Shutter Doors, the dropkick, Fever Crush, a Door Guard counter");
            visualMovesUntilRiichi(z, x);
            section("Riichi! Transit Card (1 star) or Travel Emergency (2 stars): the third reel decides");
            for (int i = 0; i < 150 && gambleState() != GamblePayload.MISS; i++) film(1);
            section("A miss: back to spinning. 4 attempts, and the 4th is a guaranteed pity jackpot");
            server.runOnServer(s -> HakariState.of(s.getPlayerList().getPlayers().getFirst()).oddsBonus = 5f);
            film(30);
            // (Reserve Balls again within 8s of the first ball hitting would be Renewal, the rewind; start this round fresh.)
            server.runOnServer(s -> HakariState.of(s.getPlayerList().getPlayers().getFirst()).renewal = null);
            section("Two more visual moves...");
            visualMovesUntilRiichi(z, x);
            section("...Riichi again...");
            for (int i = 0; i < 160 && !ctx.computeOnClient(mc -> ClientState.awakened()); i++) film(1);
            section("JACKPOT: 100s of infinite cursed energy; damage drains the meter instead of killing him");
            film(70);

            // --- Jackpot kit ---
            section("Lucky Volley (Z): a flurry, then an unblockable swipe");
            clearDummies();
            ShowcaseCamera.set(62, 5f, 1.2f, 1.5f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.6");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(z);
            film(45);
            section("Lucky Rushdown (X): run them down, drag them, throw them");
            clearDummies();
            resetPlayer();
            ShowcaseCamera.set(70, 9f, 2.5f, 4f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^7");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(x);
            film(55);
            section("Overwhelming Luck (C): a rushing strike, then the grab and a barrage");
            clearDummies();
            resetPlayer();
            ShowcaseCamera.set(65, 7f, 2f, 3f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^3");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(c);
            film(70);
            section("Energy Surge (V): launch them skyward, blink up, kick them down");
            clearDummies();
            resetPlayer();
            ShowcaseCamera.set(70, 9f, 3f, 3f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^4");
            ctx.waitTicks(10);
            film(4);
            in.pressKey(v);
            film(55);
            section("Rhythm (B, press on the beat): each finished dance stacks speed and cuts cooldowns");
            clearDummies();
            ShowcaseCamera.set(150, 5f, 1f, 0f);
            ctx.waitTicks(6);
            in.pressKey(b);
            filmRhythm(b, 70);
            section("");
            film(4);
            ShowcaseCamera.off();
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("screenshots", "frames.txt"), log.toString());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Makes visual moves (Reserve Balls, then Shutter Doors, and again if one didn't count) until a Riichi starts. */
    private void visualMovesUntilRiichi(KeyMapping z, KeyMapping x) {
        int presses = 0;
        for (int i = 0; i < 60 && gambleState() != GamblePayload.RIICHI; i++) {
            int progress = ctx.computeOnClient(mc -> {
                var gm = ClientState.gambleOf(mc.player.getId());
                return gm == null || gm.p == null ? 0 : gm.p.progress();
            });
            if (gambleState() == GamblePayload.SPINNING && progress < 2) in.pressKey(presses++ % 2 == 0 ? z : x);
            film(12);
        }
    }

    private int gambleState() {
        return ctx.computeOnClient(mc -> {
            var gm = ClientState.gambleOf(mc.player.getId());
            return gm == null || gm.p == null ? -1 : gm.p.state();
        });
    }

    /** Films Rhythm, pressing the Special key on each beat. */
    private void filmRhythm(KeyMapping key, int ticks) {
        Set<Integer> hit = new HashSet<>();
        int beats = dev.rick.jjk.config.JJKConfig.get().hakari.rhythmBeats;
        long end = ctx.computeOnClient(mc -> mc.level.getGameTime()) + ticks;
        while (ctx.computeOnClient(mc -> mc.level.getGameTime()) < end) {
            float t = ctx.computeOnClient(mc -> RhythmClient.clock());
            for (int bt = 0; bt < beats; bt++) {
                if (!hit.contains(bt) && t >= 0 && Math.abs(t - RhythmAbility.beatTime(bt)) <= 0.6f) {
                    in.pressKey(key);
                    hit.add(bt);
                }
            }
            film(1);
        }
    }
}
