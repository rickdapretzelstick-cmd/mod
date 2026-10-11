package dev.rick.jjk.test;

import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.curse.FlyHeadEntity;
import dev.rick.jjk.progression.curse.SchoolCrawlerEntity;
import dev.rick.jjk.progression.curse.SchoolMawEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Tuning aid (not in the suite): how a common curse's fight reads on screen. The Crawler's pounce wind-up (its glow and
 * the lane on the floor), the Maw's slam wind-up (the ring), a staggered and exposed curse flashing, and a Fly Head's
 * spit in flight. Screenshots cf_*.png.
 */
public class CurseFightClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("item replace entity @a armor.head with jjk:cursed_glasses");
            ctx.waitTicks(40);
            server.runCommand("tp @a 0 ~ 0 0 25");
            ctx.waitTicks(10);

            server.runCommand("execute at @a run summon jjk:school_crawler ~0 ~ ~6 {Rotation:[180f,0f]}");
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                SchoolCrawlerEntity c = first(srv, SchoolCrawlerEntity.class);
                if (c != null) c.forceWindupForTest(player(srv), false);
            });
            ctx.waitTicks(6);
            ctx.takeScreenshot("cf_crawler_windup");
            ctx.waitTicks(30);
            // Staggered: a heavy cursed-tool hit.
            server.runOnServer(srv -> {
                ServerPlayer p = player(srv);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.SLAUGHTER_DEMON));
                SchoolCrawlerEntity c = first(srv, SchoolCrawlerEntity.class);
                if (c != null) c.hurtServer((ServerLevel) c.level(), c.damageSources().playerAttack(p), c.poise() + 1);
            });
            ctx.waitTicks(1);
            ctx.takeScreenshot("cf_crawler_hitstop");
            ctx.waitTicks(5);
            ctx.takeScreenshot("cf_crawler_exposed_a");
            ctx.waitTicks(3);
            ctx.takeScreenshot("cf_crawler_exposed_b");
            server.runCommand("kill @e[type=jjk:school_crawler]");
            ctx.waitTicks(30);

            server.runCommand("execute at @a run summon jjk:school_maw ~0 ~ ~6 {Rotation:[180f,0f]}");
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                SchoolMawEntity m = first(srv, SchoolMawEntity.class);
                if (m != null) m.forceSlamForTest();
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("cf_maw_slam_windup");
            ctx.waitTicks(9);
            ctx.takeScreenshot("cf_maw_slam");
            server.runCommand("kill @e[type=jjk:school_maw]");
            ctx.waitTicks(30);

            server.runCommand("execute at @a run summon jjk:fly_head ~0 ~1.5 ~8 {Rotation:[180f,0f]}");
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                FlyHeadEntity f = first(srv, FlyHeadEntity.class);
                if (f != null) f.forceSpitForTest(player(srv));
            });
            ctx.waitTicks(8);
            ctx.takeScreenshot("cf_fly_spit_windup");
            ctx.waitTicks(10);
            ctx.takeScreenshot("cf_fly_spit_flight");
            server.runCommand("kill @e[type=jjk:fly_head]");
            ctx.waitTicks(10);
        }
    }

    private static ServerPlayer player(MinecraftServer srv) {
        return srv.getPlayerList().getPlayers().get(0);
    }

    private static <T extends CommonCurseEntity> T first(MinecraftServer srv, Class<T> type) {
        for (Entity e : srv.overworld().getAllEntities()) if (type.isInstance(e)) return type.cast(e);
        return null;
    }
}
