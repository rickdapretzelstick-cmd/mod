package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.hud.CharacterSelectScreen;
import dev.rick.jjk.core.ability.Casters;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;

import java.util.Arrays;

/**
 * The K menu's No kit card, through a real client: in Creative it is the first card and clicking it makes the player an
 * ordinary person (no character on the server, none on the client); in Survival it isn't offered (no swapping there).
 * Opt-in: screenshots in build/run/clientGameTest/screenshots as nk*.png.
 */
public class NoKitClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            ctx.waitTicks(40);
            TestInput in = ctx.getInput();
            KeyMapping menu = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.jjk.character_menu"))
                    .findFirst().orElseThrow());
            // Pick Gojo first, then No kit.
            in.pressKey(menu);
            ctx.waitTicks(12);
            click(ctx, in, "gojo");
            ctx.waitTicks(10);
            if (!ctx.computeOnClient(mc -> "gojo".equals(ClientState.character))) throw new AssertionError("Gojo picked");
            ctx.takeScreenshot("nk1_menu_with_no_kit");
            click(ctx, in, "");
            ctx.waitTicks(10);
            ctx.takeScreenshot("nk2_no_kit_selected");
            if (!ctx.computeOnClient(mc -> ClientState.character.isEmpty())) throw new AssertionError("No kit: the client has no character");
            boolean serverNone = ctx.computeOnClient(mc -> true) && serverHasNone(server);
            if (!serverNone) throw new AssertionError("No kit: the server has no character either");
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            // Survival: the card isn't there.
            server.runCommand("gamemode survival @a");
            ctx.waitTicks(10);
            in.pressKey(menu);
            ctx.waitTicks(12);
            boolean offered = ctx.computeOnClient(mc -> mc.gui.screen() instanceof CharacterSelectScreen s && s.cardCenter("") != null);
            ctx.takeScreenshot("nk3_survival_menu");
            if (offered) throw new AssertionError("Survival doesn't offer the No kit card");
            in.pressKey(InputConstants.KEY_ESCAPE);
        }
    }

    private static boolean serverHasNone(TestServerContext server) {
        boolean[] none = new boolean[1];
        server.runOnServer(srv -> none[0] = Casters.get(srv.getPlayerList().getPlayers().getFirst()).character() == null);
        return none[0];
    }

    private static void click(ClientGameTestContext ctx, TestInput in, String id) {
        double[] at = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof CharacterSelectScreen s)) throw new AssertionError("select screen not open");
            int[] c = s.cardCenter(id);
            if (c == null) throw new AssertionError("no card for '" + id + "'");
            double scale = mc.getWindow().getGuiScale();
            return new double[] {c[0] * scale, c[1] * scale};
        });
        in.setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        in.pressMouse(0);
    }
}
