package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.rifle.RifleClient;
import dev.rick.jjk.core.net.RifleStatePayload;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.tool.kit.CursedKits;
import dev.rick.jjk.progression.tool.kit.CursedSlot;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Tuning aid (not in the suite): the drawn rifle in first person through its states. Screenshots fp_*.png. */
public class RifleFpClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        dev.rick.jjk.config.JJKConfig.get().general.autoAssignGojo = false;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 ~ 0 0 10");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                CursedSlot.set(p, new ItemStack(ProgressionItems.CURSED_RIFLE));
                CursedKits.update(p);
            });
            ctx.waitTicks(60);
            shot(ctx, "ready");
            phase(ctx, RifleServer.Phase.AIM, 0);
            ctx.waitTicks(1);
            shot(ctx, "ads_1");
            ctx.waitTicks(1);
            shot(ctx, "ads_2");
            phase(ctx, RifleServer.Phase.IDLE, 0);
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> RifleClient.shot(mc.player.getId()));
            ctx.waitTicks(1);
            shot(ctx, "kick");
            ctx.waitTicks(10);
            phase(ctx, RifleServer.Phase.CHARGE, 30);
            ctx.waitTicks(20);
            shot(ctx, "charge");
            phase(ctx, RifleServer.Phase.FIRE, 40);
            ctx.waitTicks(4);
            shot(ctx, "fire");
            phase(ctx, RifleServer.Phase.IDLE, 0);
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> mc.player.setSprinting(true));
            ctx.waitTicks(10);
            shot(ctx, "sprint");
            ctx.runOnClient(mc -> mc.player.setSprinting(false));
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> ClientAnimations.play(mc.player.getId(), "rf_bash", 1, mc.level.getGameTime()));
            ctx.waitTicks(3);
            shot(ctx, "bash");
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> ClientAnimations.play(mc.player.getId(), "rf_flare", 1, mc.level.getGameTime()));
            ctx.waitTicks(4);
            shot(ctx, "flare");
        }
    }

    private static void phase(ClientGameTestContext ctx, RifleServer.Phase ph, int duration) {
        ctx.runOnClient(mc -> RifleClient.apply(new RifleStatePayload(mc.player.getId(), ph.ordinal(), duration, 1f, 100, 100, true, 1f, 16)));
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot("fp_" + name);
    }
}
