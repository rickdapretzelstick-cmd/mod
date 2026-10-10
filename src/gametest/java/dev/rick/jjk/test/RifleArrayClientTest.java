package dev.rick.jjk.test;

import com.mojang.authlib.GameProfile;
import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.rifle.RifleClient;
import dev.rick.jjk.core.net.RifleStatePayload;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.RemotePlayer;

import java.util.UUID;

/** Diagnostic (not in the suite): the rifle's arm extension tick by tick through the array's whole sequence. */
public class RifleArrayClientTest implements FabricClientGameTest {
    static RemotePlayer dummy;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                dummy = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Arr"));
                dummy.setId(900_001);
                dummy.snapTo(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 3, 0, 0);
                mc.level.addEntity(dummy);
            });
            Object[][] seq = {{RifleServer.Phase.DEPLOY, 28}, {RifleServer.Phase.CHARGE, 30}, {RifleServer.Phase.READY, 0, 15},
                    {RifleServer.Phase.FIRE, 40}, {RifleServer.Phase.COOLDOWN, 14}, {RifleServer.Phase.RETRACT, 28}, {RifleServer.Phase.IDLE, 0, 10}};
            StringBuilder out = new StringBuilder();
            for (Object[] st : seq) {
                RifleServer.Phase ph = (RifleServer.Phase) st[0];
                int dur = (Integer) st[1];
                int len = st.length > 2 ? (Integer) st[2] : dur;
                ctx.runOnClient(mc -> RifleClient.apply(new RifleStatePayload(dummy.getId(), ph.ordinal(), dur, 1f, 80, 100, true, 1f, 16)));
                out.append("\n").append(ph).append(": ");
                for (int i = 0; i < len; i++) {
                    // Energy changes resend the state mid-phase, as the server does.
                    if (i % 5 == 3) ctx.runOnClient(mc -> RifleClient.apply(new RifleStatePayload(dummy.getId(), ph.ordinal(), dur, 1f, 70, 100, true, 1f, 16)));
                    ctx.waitTicks(1);
                    float z = ctx.computeOnClient(mc -> {
                        PoseFrame f = RifleClient.pose(dummy, mc.level.getGameTime());
                        float[] v = f == null ? null : f.named("arm_left_slider", Clip.POS);
                        return v == null ? 0f : v[2];
                    });
                    out.append(String.format(java.util.Locale.ROOT, "%.1f ", z));
                }
            }
            System.out.println("RIFLEARRAY" + out);

            // The real thing: the rifle equipped, G held through the real key until ready, then let go.
            dev.rick.jjk.config.JJKConfig.get().general.autoAssignGojo = false;
            var server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runOnServer(srv -> {
                var p = srv.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.character.CharacterService.assign(p, null);
                dev.rick.jjk.progression.tool.kit.CursedSlot.set(p, new net.minecraft.world.item.ItemStack(dev.rick.jjk.progression.ProgressionItems.CURSED_RIFLE));
                dev.rick.jjk.progression.tool.kit.CursedKits.update(p);
                var c = dev.rick.jjk.core.ability.Casters.get(p);
                c.setAwakening(c.maxAwakening());
            });
            ctx.waitTicks(20);
            var ult = ctx.computeOnClient(mc -> java.util.Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.jjk.ultimate")).findFirst().orElseThrow());
            StringBuilder real = new StringBuilder();
            ctx.getInput().holdKey(ult);
            for (int i = 0; i < 200; i++) {
                if (i == 70) ctx.getInput().releaseKey(ult);
                ctx.waitTicks(1);
                String ph = server.computeOnServer(srv -> RifleServer.phase(srv.getPlayerList().getPlayers().getFirst()).name());
                float z = ctx.computeOnClient(mc -> {
                    PoseFrame f = RifleClient.pose(mc.player, mc.level.getGameTime());
                    float[] v = f == null ? null : f.named("arm_left_slider", Clip.POS);
                    return v == null ? 0f : v[2];
                });
                real.append(String.format(java.util.Locale.ROOT, "%d:%s:%.1f ", i, ph.substring(0, 3), z));
            }
            System.out.println("RIFLEREAL " + real);
        }
    }
}
