package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.ClientAnimations;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;

/**
 * Tuning aid (not in the suite): each common curse's model from the front, the side, three-quarters and behind, then
 * every clip it has, sampled at a few moments (seen three-quarters on). Screenshots gal_<kind>_*.png. The kinds and
 * clips to show come from the system property jjk.gallery ("kind:clip@ms,clip@ms;kind2:..."), or the defaults below.
 */
public class CurseGalleryClientTest implements FabricClientGameTest {
    private static final String DEFAULT =
            "school_maw:idle@600,move@250,attack@500,attack@700,lash@520,slam@820,slam@920,recover@600,stagger@200,death@1200;"
                    + "school_crawler:idle@400,move@150,move@450,crouch@560,attack@250,sprawl@600,swipe@380,swipe@520,feint@200,stagger@200,death@1200;"
                    + "fly_head:idle@0,idle@50,attack@400,attack@800,spit@500,spit@650,grounded@800,hurt@100,death@1200;"
                    + "forest_stalker:idle@600,move@200,move@650,telegraph@900,lunge@300,exposed@800,rake@350,rake@500,retreat@400,stagger@200,death@1200";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String spec = System.getenv("JJK_GALLERY") != null ? System.getenv("JJK_GALLERY") : System.getProperty("jjk.gallery", DEFAULT);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("item replace entity @a armor.head with jjk:cursed_glasses");
            ctx.waitTicks(40);
            for (String part : spec.split(";")) {
                String[] kv = part.split(":");
                String kind = kv[0];
                boolean flies = kind.equals("fly_head");
                double back = kind.equals("fly_head") ? 2.4 : kind.equals("school_crawler") ? 2.6 : 3.2;
                server.runCommand("tp @a 0.5 -60 " + (0.5 - back) + " 0 " + (flies ? 4 : 22));
                server.runCommand("summon jjk:" + kind + " 0.5 " + (flies ? -58.6 : -60) + " 0.5 {NoAI:1b,NoGravity:1b,Rotation:[180f,0f],Tags:[\"gal\"]}");
                ctx.waitTicks(25);
                int[] yaws = {180, 90, 135, 0};
                String[] names = {"front", "side", "threequarter", "back"};
                for (int i = 0; i < yaws.length; i++) {
                    turn(server, yaws[i]);
                    turnClient(ctx, yaws[i]);
                    ctx.waitTicks(4);
                    ctx.takeScreenshot("gal_" + kind + "_" + names[i]);
                }
                turn(server, 145);
                turnClient(ctx, 145);
                if (kv.length > 1) {
                    for (String cs : kv[1].split(",")) {
                        String[] ct = cs.split("@");
                        String clip = kind + "_" + ct[0];
                        int ms = Integer.parseInt(ct[1]);
                        ctx.runOnClient(mc -> {
                            for (Entity e : mc.level.entitiesForRendering()) {
                                if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(kind)) {
                                    ClientAnimations.play(e.getId(), clip, 1f, mc.level.getGameTime());
                                }
                            }
                        });
                        ctx.waitTicks(Math.max(1, Math.round(ms / 50f)));
                        ctx.takeScreenshot("gal_" + kind + "_" + ct[0] + "_" + ms);
                        ctx.waitTicks(2);
                    }
                }
                server.runCommand("kill @e[tag=gal]");
                ctx.waitTicks(10);
            }
        }
    }

    private static void turn(TestServerContext server, float yaw) {
        server.runOnServer(srv -> {
            for (Entity e : srv.overworld().getAllEntities()) {
                if (e instanceof dev.rick.jjk.progression.curse.CommonCurseEntity c) {
                    c.setYRot(yaw);
                    c.yRotO = yaw;
                    c.setYBodyRot(yaw);
                    c.yBodyRotO = yaw;
                    c.setYHeadRot(yaw);
                }
            }
        });
    }

    private static void turnClient(ClientGameTestContext ctx, float yaw) {
        ctx.runOnClient(mc -> {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof dev.rick.jjk.progression.curse.CommonCurseEntity c) {
                    c.setYRot(yaw);
                    c.yRotO = yaw;
                    c.setYBodyRot(yaw);
                    c.yBodyRotO = yaw;
                    c.setYHeadRot(yaw);
                    c.yHeadRotO = yaw;
                }
            }
        });
    }
}
