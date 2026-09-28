package dev.rick.jjk.test;

import dev.rick.jjk.client.clash.ClashClient;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.CombatStatus;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

/**
 * Films a vertical (9:16) short: base Lapse Blue and Reversal Red, then everything awakened (Awakening, MAX Blue,
 * MAX Red, Hollow Purple, Infinite Void) and a domain counter into a full clash. The HUD is hidden for the technique
 * shots and shown for the domain and the clash. Not registered by default: point the {@code fabric-client-gametest}
 * entrypoint at this class to record, then build the video with {@code tools/make_short.py}.
 */
public class ShortClientTest extends PresentationClientTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            server = sp.getServer();
            in = ctx.getInput();
            in.resizeWindow(720, 1280);
            ctx.runOnClient(mc -> mc.options.fov().set(90));
            cmd("time set noon");
            cmd("gamerule advance_time false");
            cmd("weather clear");
            ctx.waitTicks(40);
            cmd("execute as @a at @s run jjk arena");
            cmd("execute as @a run jjk nocooldown true");
            center = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                return new double[] {p.getX(), p.getY(), p.getZ()};
            });
            clearDummies();
            resetPlayer();
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            KeyMapping z = key("key.jjk.skill_1"), x = key("key.jjk.skill_2"), c = key("key.jjk.skill_3"), g = key("key.jjk.ultimate");
            hud(false);

            section("LAPSE BLUE");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^2 ^ ^7");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-2 ^ ^7");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^9");
            ShowcaseCamera.set(35, 9f, 2.5f, 4.5f);
            ctx.waitTicks(10);
            film(3);
            in.holdKey(z);
            film(26);
            in.releaseKey(z);
            film(18);

            section("REVERSAL RED");
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^6");
            ShowcaseCamera.set(40, 8f, 2f, 3.5f);
            ctx.waitTicks(10);
            film(3);
            in.holdKey(x);
            film(22);
            in.releaseKey(x);
            film(30);

            section("AWAKENING");
            resetPlayer();
            clearDummies();
            cmd("jjk restore now");
            cmd("execute as @a run jjk awakening 100");
            hud(true);
            ShowcaseCamera.set(168, 4.4f, 0.3f, 0f);
            ctx.waitTicks(10);
            film(4);
            in.pressKey(g);
            film(22);
            ShowcaseCamera.set(155, 8f, 1.8f, 0f);
            film(22);
            hud(false);

            section("MAX BLUE");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^8");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^3 ^ ^10");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-3 ^ ^10");
            ShowcaseCamera.set(35, 12f, 3.5f, 5f);
            ctx.waitTicks(8);
            film(3);
            in.pressKey(z);
            film(55);

            section("MAX RED");
            clearDummies();
            resetPlayer();
            cmd("execute as @a at @s run jjk awakening 100");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-2 ~ ~-2 ~2 ~3 ~2 minecraft:bricks hollow");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-2 ~4 ~-2 ~2 ~4 ~2 minecraft:oak_planks");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-2 ~1 ~ ~2 ~2 ~ minecraft:glass_pane");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:air");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^7");
            ShowcaseCamera.set(35, 13f, 3.5f, 6f);
            ctx.waitTicks(10);
            film(3);
            in.holdKey(x);
            film(36);
            in.releaseKey(x);
            film(40);

            section("HOLLOW PURPLE");
            clearDummies();
            resetPlayer();
            cmd("jjk restore now");
            cmd("execute as @a run jjk awakening 100");
            cmd("execute as @a at @s run tp @s ~ ~ ~ -120 5");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^12");
            ShowcaseCamera.set(30, 10f, 3f, 5f);
            ctx.waitTicks(10);
            film(3);
            in.holdKey(c);
            film(50);
            in.releaseKey(c);
            film(40);

            section("DOMAIN EXPANSION");
            clearDummies();
            resetPlayer();
            cmd("jjk restore now");
            cmd("execute as @a run jjk awakening 100");
            cmd("execute as @a at @s run tp @s ~ ~ ~ 60 8");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^2 ^ ^5");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-2 ^ ^5");
            hud(true);
            ShowcaseCamera.set(35, 9f, 2.5f, 3f);
            ctx.waitTicks(10);
            film(3);
            in.pressKey(g);
            film(80);

            // Reset for the clash (not filmed).
            cmd("execute as @a run jjk domain cancel all");
            ctx.waitTicks(60);
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.combat.Statuses.remove(p, CombatStatus.BURNOUT);
                var cst = Casters.get(p);
                cst.endAwakening("short");
                cst.setAwakening(cst.maxAwakening());
            });
            clearDummies();
            resetPlayer();
            cmd("execute as @a at @s run tp @s ~ ~ ~ 120 8");
            ShowcaseCamera.set(30, 11f, 3f, 6f);
            ctx.waitTicks(20);

            section("RIVAL DOMAIN");
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var level = p.level();
                var rival = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                rival.setPos(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
                rival.setYRot(p.getYRot() + 180);
                level.addFreshEntity(rival);
                dev.rick.jjk.core.character.CharacterService.assign(rival, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.gojo.GojoCharacter.ID));
                dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(rival, 0.8f);
                var rc = Casters.get(rival);
                rc.enterAwakening();
                rc.setAwakening(rc.maxAwakening());
                rc.setNoCost(true);
            });
            film(6);
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) {
                    if (e instanceof dev.rick.jjk.entity.TrainingDummy d && Casters.getOrNull(d) != null && Casters.get(d).isAwakened()) {
                        Casters.get(d).input(dev.rick.jjk.core.ability.AbilitySlot.ULTIMATE, true, 0, 0, null);
                    }
                }
            });
            film(8);
            section("COUNTER");
            in.pressKey(g);
            film(70);
            section("DOMAIN CLASH");
            for (int i = 0; i < 900 && ctx.computeOnClient(mc -> ClashClient.view() != null); i++) film(1);
            section("WINNER");
            film(50);
            ShowcaseCamera.off();
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("screenshots", "frames.txt"), log.toString());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void hud(boolean on) {
        ctx.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() == on) mc.gui.hud.toggle();
        });
    }
}
