package dev.rick.jjk.test;

import dev.rick.jjk.client.clash.ClashClient;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.domain.DomainAbility;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

/**
 * Films a cinematic domain clash, Gojo against Sukuna: Sukuna opens Malevolent Shrine, Gojo answers with Infinite Void,
 * and the two fight it out on the beat. The game runs at 3 ticks a second while filming, so every tick gets several
 * frames; {@code tools/make_clash_video.py} rebuilds real speed at 60 fps from {@code frames.txt}. Not registered by
 * default: point the {@code fabric-client-gametest} entrypoint at this class to record.
 */
public class ClashCinematicClientTest extends PresentationClientTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            server = sp.getServer();
            in = ctx.getInput();
            in.resizeWindow(1280, 720);
            ctx.runOnClient(mc -> mc.options.fov().set(80));
            cmd("time set noon");
            cmd("gamerule advance_time false");
            cmd("weather clear");
            ctx.waitTicks(40);
            cmd("execute as @a at @s run jjk arena");
            cmd("execute as @a run jjk character @s gojo");
            cmd("execute as @a run jjk nocooldown true");
            center = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                return new double[] {p.getX(), p.getY(), p.getZ()};
            });
            clearDummies();
            resetPlayer();
            cmd("execute as @a at @s run tp @s ~ ~ ~ 120 8");
            ctx.waitTicks(20);
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var c = Casters.get(p);
                c.setAwakening(c.maxAwakening());
                // Sukuna: the Vessel with the King of Curses awake, twelve blocks away, facing Gojo.
                var level = p.level();
                var rival = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                rival.setPos(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
                rival.setYRot(p.getYRot() + 180);
                rival.setYHeadRot(p.getYRot() + 180);
                rival.setYBodyRot(p.getYRot() + 180);
                level.addFreshEntity(rival);
                dev.rick.jjk.core.character.CharacterService.assign(rival, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.yuji.YujiCharacter.ID));
                dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(rival, 0.8f);
                var rc = Casters.get(rival);
                rc.setNoCost(true);
                rc.enterAwakening();
                rc.setAwakening(rc.maxAwakening());
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            hud(false);
            KeyMapping g = key("key.jjk.ultimate");
            ctx.waitTicks(60);

            // Film slowed down: 3 ticks a second, so each tick is caught several times over.
            cmd("tick rate 3");
            ctx.waitTicks(2);

            section("STANDOFF");
            ShowcaseCamera.set(150, 6f, 1.4f, 0f);
            film(10);
            ShowcaseCamera.set(25, 7.5f, 1.8f, 5f);
            film(8);

            section("MALEVOLENT SHRINE");
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) {
                    if (e instanceof dev.rick.jjk.entity.TrainingDummy d && Casters.getOrNull(d) != null) {
                        var rc = Casters.get(d);
                        for (AbilitySlot slot : AbilitySlot.values()) {
                            if (rc.character().ability(slot, true) instanceof DomainAbility) {
                                rc.input(slot, true, 0, 0, null);
                                break;
                            }
                        }
                    }
                }
            });
            ShowcaseCamera.set(10, 9f, 2.2f, 9f);
            film(10);

            section("INFINITE VOID");
            in.pressKey(g);
            ShowcaseCamera.set(200, 4.5f, 1f, 0f);
            film(24);
            ShowcaseCamera.set(90, 12f, 3.5f, 6f);
            film(50);

            section("DOMAIN CLASH");
            hud(true);
            for (int i = 0; i < 1200 && ctx.computeOnClient(mc -> ClashClient.view() != null); i++) film(1);
            hud(false);

            section("WINNER");
            ShowcaseCamera.set(60, 10f, 3f, 6f);
            film(60);
            ShowcaseCamera.off();
            cmd("tick rate 20");
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
