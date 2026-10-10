package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.mastery.MasteryData;
import dev.rick.jjk.progression.mastery.MasteryNode;
import dev.rick.jjk.progression.mastery.MasteryTrees;
import dev.rick.jjk.progression.tool.kit.CursedKits;
import dev.rick.jjk.progression.tool.kit.CursedSlot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * The Cursed Blade through a real client: its icon in the hotbar, dropped on the ground, slung on the back, drawn two-
 * handed in third and first person, and its moves mid-swing (Heavy Slash, Cursed Wave charging and released, Thorn
 * Guard, Black Thorn). Opt-in: screenshots blade_*.png.
 */
public class BladeClientTest implements FabricClientGameTest {
    private static void onPlayer(TestServerContext server, Consumer<ServerPlayer> f) {
        server.runOnServer(srv -> f.accept(srv.getPlayerList().getPlayers().getFirst()));
    }

    private static void input(TestServerContext server, AbilitySlot slot, boolean down) {
        onPlayer(server, p -> Casters.get(p).input(slot, down, 0, 0, null));
    }

    private static void face(ClientGameTestContext ctx, TestServerContext server, float yaw) {
        server.runCommand("tp @a 0 ~ 0 " + yaw + " 0");
        ctx.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setYBodyRot(yaw);
            mc.player.setYHeadRot(yaw);
        });
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        JJKConfig.get().general.autoAssignGojo = false;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 ~ 0 0 0");
            onPlayer(server, p -> {
                CharacterService.assign(p, Characters.get("gojo"));
                CursedSlot.set(p, new ItemStack(ProgressionItems.CURSED_BLADE));
                CursedKits.update(p);
                MasteryData d = Mastery.data(p);
                for (MasteryNode n : MasteryTrees.get("tool/cursed_blade").nodes()) d = d.buy("tool/cursed_blade", n.id(), 0);
                Mastery.set(p, d);
                Casters.get(p).setNoCost(true);
                p.getInventory().setItem(1, new ItemStack(ProgressionItems.CURSED_BLADE));
            });
            ctx.waitTicks(30);

            // Holstered on the back while the technique is in use.
            onPlayer(server, p -> {
                AbilityCaster c = Casters.get(p);
                if (c.usingTool()) CursedKits.switchMoveset(p);
                CursedKits.update(p);
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            face(ctx, server, -30f);
            ctx.waitTicks(10);
            ctx.takeScreenshot("blade_holstered_back");

            // On the ground, beside the wielder.
            onPlayer(server, p -> {
                ItemEntity e = new ItemEntity(p.level(), p.getX() + 1.2, p.getY() + 0.2, p.getZ() - 1.5, new ItemStack(ProgressionItems.CURSED_BLADE));
                e.setNoPickUpDelay();
                e.setPickUpDelay(32767);
                e.setDeltaMovement(0, 0, 0);
                p.level().addFreshEntity(e);
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            face(ctx, server, -30f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("blade_ground_and_hotbar");

            // Drawn: the switch to the blade's moveset.
            onPlayer(server, p -> {
                AbilityCaster c = Casters.get(p);
                for (int i = 0; i < 20; i++) c.tick();
                if (!c.usingTool()) CursedKits.switchMoveset(p);
                CursedKits.update(p);
            });
            ctx.waitTicks(20);
            boolean drawn = ctx.computeOnClient(mc -> dev.rick.jjk.client.gear.CursedGear.drawn(mc.player));
            if (!drawn) throw new AssertionError("the blade is drawn after the switch");
            ctx.takeScreenshot("blade_drawn_front");
            face(ctx, server, 60f);
            ctx.waitTicks(6);
            ctx.takeScreenshot("blade_drawn_side");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(10);
            ctx.takeScreenshot("blade_drawn_first_person");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            face(ctx, server, 60f);
            ctx.waitTicks(6);

            // Heavy Slash, mid-cut.
            input(server, AbilitySlot.SKILL_1, true);
            input(server, AbilitySlot.SKILL_1, false);
            ctx.waitTicks(4);
            ctx.takeScreenshot("blade_heavy_windup");
            ctx.waitTicks(4);
            ctx.takeScreenshot("blade_heavy_cut");
            ctx.waitTicks(40);

            // Cursed Wave: charging, then released.
            input(server, AbilitySlot.SKILL_2, true);
            ctx.waitTicks(16);
            ctx.takeScreenshot("blade_wave_charge");
            input(server, AbilitySlot.SKILL_2, false);
            ctx.waitTicks(3);
            ctx.takeScreenshot("blade_wave_release");
            ctx.waitTicks(4);
            ctx.takeScreenshot("blade_wave_travel");
            ctx.waitTicks(40);

            // Thorn Guard.
            input(server, AbilitySlot.SKILL_4, true);
            input(server, AbilitySlot.SKILL_4, false);
            ctx.waitTicks(6);
            ctx.takeScreenshot("blade_thorn_guard");
            ctx.waitTicks(40);

            // Black Thorn: raised, then the plunge and the rings.
            input(server, AbilitySlot.ULTIMATE, true);
            input(server, AbilitySlot.ULTIMATE, false);
            ctx.waitTicks(12);
            ctx.takeScreenshot("blade_ult_raise");
            ctx.waitTicks(8);
            ctx.takeScreenshot("blade_ult_plunge");
            ctx.waitTicks(6);
            ctx.takeScreenshot("blade_ult_rings");
            ctx.waitTicks(40);
        }
    }
}
