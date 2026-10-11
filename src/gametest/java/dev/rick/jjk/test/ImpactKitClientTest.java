package dev.rick.jjk.test;

import dev.rick.jjk.JJK;
import dev.rick.jjk.client.fx.ImpactFrames;
import dev.rick.jjk.core.fx.Impact;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The impact kit: every impact-frame style and the slowed-time look on a player facing a curse (each must actually be
 * applied by the renderer, not fail to load), an impact frame's timing (it plays and ends on its own), the ground crack
 * as it splits and as it fades, the dust ring and the shove, and the settings screen's new toggles. Screenshots impact_*.
 */
public class ImpactKitClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            ctx.waitTicks(40);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(dev.rick.jjk.progression.ProgressionItems.CURSED_GLASSES));
                p.setYRot(0);
                p.setXRot(15);
                var c = ModEntities.SCHOOL_MAW.create(level, EntitySpawnReason.EVENT);
                c.setPos(p.getX() + 0.6, p.getY(), p.getZ() + 3.2);
                c.setNoAi(true);
                c.setYRot(180);
                level.addFreshEntity(c);
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(10);
            ctx.takeScreenshot("impact_00_plain");
            for (String effect : List.of("ink_impact_a", "ink_impact_b", "ink_reverse", "black_flash_impact", "negative_impact", "slowmo")) {
                ctx.runOnClient(mc -> ImpactFrames.holdForTest(effect));
                ctx.waitTicks(3);
                boolean applied = ctx.computeOnClient(mc -> mc.gameRenderer.getAppliedPostEffects().contains(JJK.id(effect)));
                if (!applied) throw new AssertionError("the " + effect + " post effect is applied (it loaded and compiled)");
                ctx.takeScreenshot("impact_frame_" + effect);
            }
            ctx.runOnClient(mc -> ImpactFrames.holdForTest(null));
            // The figures-only ink: the bodies inked, the ground left as paper.
            String[] masked = {"ink_mask_a", "ink_mask_b", "ink_mask_reverse", "ink_mask_black_flash"};
            for (int i = 0; i < masked.length; i++) {
                final int which = i;
                ctx.runOnClient(mc -> ImpactFrames.holdInkForTest(which));
                ctx.waitTicks(3);
                boolean inking = ctx.computeOnClient(mc -> ImpactFrames.inking());
                if (!inking) throw new AssertionError("the figures near the camera are inked (" + masked[i] + ")");
                ctx.takeScreenshot("impact_masked_" + masked[i]);
            }
            ctx.runOnClient(mc -> ImpactFrames.holdInkForTest(-1));
            // A real impact frame plays and then ends by itself.
            ctx.runOnClient(mc -> ImpactFrames.play(ImpactFrames.Style.INK, 1f));
            boolean playing = ctx.computeOnClient(mc -> ImpactFrames.playing() && ImpactFrames.inking());
            if (!playing) throw new AssertionError("an impact frame is playing");
            ctx.waitTicks(10);
            boolean over = ctx.computeOnClient(mc -> !ImpactFrames.playing());
            if (!over) throw new AssertionError("and over within half a second");
            // Turned off in the settings, it never plays.
            ctx.runOnClient(mc -> {
                dev.rick.jjk.config.JJKConfig.get().client.impactFrames = false;
                ImpactFrames.play(ImpactFrames.Style.INK, 1f);
            });
            boolean off = ctx.computeOnClient(mc -> !ImpactFrames.playing());
            ctx.runOnClient(mc -> dev.rick.jjk.config.JJKConfig.get().client.impactFrames = true);
            if (!off) throw new AssertionError("impact frames off: none plays");

            // The ground cracks, the dust ring and the shove, between the player and the curse.
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                Impact.crack((ServerLevel) p.level(), p.position().add(0, 0, 1.6), new Vec3(0, 0, 1), 1.4f);
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("impact_crack_split");
            ctx.waitTicks(40);
            ctx.takeScreenshot("impact_crack_fading");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                Impact.dust((ServerLevel) p.level(), p.position().add(0, 0, 1.6), 1.3f);
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("impact_dust");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                Impact.shove((ServerLevel) p.level(), p.position().add(0, 1, 1.6), new Vec3(0, 0, 1), 1.2f);
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("impact_shove");
            // The settings screen with its new toggles.
            ctx.runOnClient(mc -> mc.gui.setScreen(new dev.rick.jjk.client.hud.JJKSettingsScreen(null)));
            ctx.waitTicks(5);
            ctx.takeScreenshot("impact_settings");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }
}
