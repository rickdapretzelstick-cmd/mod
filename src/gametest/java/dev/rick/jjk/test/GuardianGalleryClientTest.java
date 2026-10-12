package dev.rick.jjk.test;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.guardian.GuardianBody;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * The four Grade 1 guardian bodies in each of their three looks, standing in a row (front and back), then each body
 * one at a time through its clips. Opt-in; JJK_GUARDIAN_CLIPS=idle,bite narrows the clips. Screenshots guardian_*.png.
 */
public class GuardianGalleryClientTest implements FabricClientGameTest {
    private static final List<EntityType<? extends CommonCurseEntity>> BODIES = List.of(ModEntities.GUARDIAN_CLAW_HUNTER, ModEntities.GUARDIAN_PALE_STALKER,
            ModEntities.GUARDIAN_CYCLOPS_BRUISER, ModEntities.GUARDIAN_VEILED_REACHER);
    private static final String[][] CLIPS = {
            {"idle", "move", "bite", "crouch", "attack", "stumble", "howl", "unleash", "hurt", "stagger", "grapple", "death"},
            {"idle", "move", "telegraph", "lunge", "exposed", "rake", "retreat", "unleash", "hurt", "stagger", "grapple", "death"},
            {"idle", "move", "attack", "bite_quick", "lash", "slam", "recover", "unleash", "hurt", "stagger", "grapple", "death"},
            {"idle", "move", "flicker", "grasp", "wail", "breath", "unleash", "hurt", "stagger", "grapple", "death"}};

    private static ServerPlayer me(net.minecraft.server.MinecraftServer srv) {
        return srv.getPlayerList().getPlayers().getFirst();
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String only = System.getenv("JJK_GUARDIAN_CLIPS");
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });
            server.runOnServer(srv -> CharacterService.assign(me(srv), Characters.get("yuji")));
            // Everything from one spot on the ground.
            double[] o = server.computeOnServer(srv -> {
                ServerPlayer p = me(srv);
                var at = p.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
                return new double[] {at.getX() + 0.5, at.getY(), at.getZ() + 0.5};
            });
            // All twelve in a row: a body to a column, its looks one behind the other... side by side, in rows by look.
            List<Integer> ids = server.computeOnServer(srv -> {
                ServerPlayer p = me(srv);
                ServerLevel level = (ServerLevel) p.level();
                List<Integer> out = new ArrayList<>();
                for (int b = 0; b < BODIES.size(); b++) {
                    for (int v = 0; v < 3; v++) {
                        CommonCurseEntity c = BODIES.get(b).create(level, EntitySpawnReason.EVENT);
                        c.setNoAi(true);
                        ((GuardianBody) c).setGuardianVariant(v);
                        c.snapTo(o[0] - 9 + b * 6 + (v - 1) * 1.8, o[1], o[2] + 9 + v * 3, 180, 0);
                        c.setYHeadRot(180);
                        c.setYBodyRot(180);
                        level.addFreshEntity(c);
                        out.add(c.getId());
                    }
                }
                return out;
            });
            server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", o[0], o[1] + 3, o[2] - 4, o[0], o[1] + 1, o[2] + 12));
            ctx.waitTicks(40);
            ctx.takeScreenshot("guardian_all_front");
            server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", o[0], o[1] + 3, o[2] + 22, o[0], o[1] + 1, o[2] + 10));
            ctx.waitTicks(20);
            ctx.takeScreenshot("guardian_all_back");
            server.runOnServer(srv -> {
                ServerLevel level = (ServerLevel) me(srv).level();
                for (int id : ids) if (level.getEntity(id) != null) level.getEntity(id).discard();
            });
            // Each body alone, close: its looks, then its clips.
            for (int b = 0; b < BODIES.size(); b++) {
                int body = b;
                int id = server.computeOnServer(srv -> {
                    ServerPlayer p = me(srv);
                    ServerLevel level = (ServerLevel) p.level();
                    CommonCurseEntity c = BODIES.get(body).create(level, EntitySpawnReason.EVENT);
                    c.setNoAi(true);
                    c.snapTo(o[0], o[1], o[2] + 5, 180, 0);
                    c.setYHeadRot(180);
                    c.setYBodyRot(180);
                    level.addFreshEntity(c);
                    return c.getId();
                });
                server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", o[0] - 1.2, o[1] + 0.4, o[2], o[0], o[1] + 1.5, o[2] + 5));
                for (int v = 0; v < 3; v++) {
                    int look = v;
                    server.runOnServer(srv -> ((GuardianBody) ((ServerLevel) me(srv).level()).getEntity(id)).setGuardianVariant(look));
                    ctx.waitTicks(v == 0 ? 30 : 8);
                    ctx.takeScreenshot("guardian_" + body + "_look" + v);
                }
                for (String clip : CLIPS[b]) {
                    if (only != null && !only.isBlank() && !List.of(only.split(",")).contains(clip)) continue;
                    String kind = server.computeOnServer(srv -> ((CommonCurseEntity) ((ServerLevel) me(srv).level()).getEntity(id)).curseKind());
                    server.runOnServer(srv -> Anim.playOn(((ServerLevel) me(srv).level()).getEntity(id), kind + "_" + clip));
                    ctx.waitTicks(6);
                    ctx.takeScreenshot("guardian_" + body + "_" + clip + "_a");
                    ctx.waitTicks(8);
                    ctx.takeScreenshot("guardian_" + body + "_" + clip + "_b");
                    ctx.waitTicks(10);
                }
                server.runOnServer(srv -> ((ServerLevel) me(srv).level()).getEntity(id).discard());
                ctx.waitTicks(5);
            }
        }
    }
}
