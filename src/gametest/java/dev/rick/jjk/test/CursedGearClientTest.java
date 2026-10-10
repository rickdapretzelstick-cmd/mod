package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.investigation.CursedCompass;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.tool.kit.CursedKits;
import dev.rick.jjk.progression.tool.kit.CursedSlot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The cursed tools as gear, through a real client: the Cursed Item slot in the inventory; each tool holstered (the blade
 * at the hip, the cleaver and the rifle slung on the back) while the technique is in use, and drawn into the hands
 * (two-handed for the cleaver and the rifle) after the switch, in third and first person; then the Cursed Compass in
 * hand on an investigation, and the dark of a realm's pull. Opt-in: screenshots gear*.png.
 */
public class CursedGearClientTest implements FabricClientGameTest {
    private static void equip(TestServerContext server, Item item) {
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
            CursedSlot.set(p, new ItemStack(item));
            CursedKits.update(p);
        });
    }

    private static void switchTo(TestServerContext server, boolean tool) {
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
            var c = dev.rick.jjk.core.ability.Casters.get(p);
            for (int i = 0; i < 20; i++) c.tick();
            if (c.usingTool() != tool) CursedKits.switchMoveset(p);
            CursedKits.update(p);
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
            server.runOnServer(srv -> CharacterService.assign(srv.getPlayerList().getPlayers().getFirst(), Characters.get("gojo")));
            equip(server, ProgressionItems.SLAUGHTER_DEMON);
            ctx.waitTicks(30);

            // The slot, in the survival inventory.
            server.runCommand("gamemode survival @a");
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            ctx.waitTicks(5);
            ctx.takeScreenshot("gear1_inventory_slot");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            server.runCommand("gamemode creative @a");
            // (Survival without owning a kit sets the technique aside: give it back for the switch.)
            server.runOnServer(srv -> CharacterService.assign(srv.getPlayerList().getPlayers().getFirst(), Characters.get("gojo")));

            // Each tool: holstered (technique in use), then drawn.
            Item[] tools = {ProgressionItems.SLAUGHTER_DEMON, ProgressionItems.CURSED_CLEAVER, ProgressionItems.CURSED_RIFLE};
            String[] names = {"blade", "cleaver", "rifle"};
            for (int i = 0; i < tools.length; i++) {
                equip(server, tools[i]);
                switchTo(server, false);
                ctx.waitTicks(10);
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                server.runCommand("tp @a 0 ~ 0 150 0");
                ctx.runOnClient(mc -> {
                    mc.player.setYRot(150f);
                    mc.player.setYBodyRot(150f);
                    mc.player.setYHeadRot(150f);
                });
                ctx.waitTicks(10);
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                ctx.waitTicks(4);
                ctx.takeScreenshot("gear_" + names[i] + "_holstered_back");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                server.runCommand("tp @a 0 ~ 0 -30 0");
                ctx.waitTicks(10);
                ctx.takeScreenshot("gear_" + names[i] + "_holstered_front");
                switchTo(server, true);
                ctx.waitTicks(20);
                boolean drawn = ctx.computeOnClient(mc -> dev.rick.jjk.client.gear.CursedGear.drawn(mc.player));
                if (!drawn) throw new AssertionError(names[i] + " drawn after the switch");
                ctx.takeScreenshot("gear_" + names[i] + "_drawn_front");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
                ctx.waitTicks(10);
                ctx.takeScreenshot("gear_" + names[i] + "_drawn_first_person");
            }

            // The compass, on an investigation chosen at the board.
            server.runCommand("item replace entity @a weapon.mainhand with jjk:cursed_compass");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                InvestigationState st = InvestigationState.get(srv);
                InvestigationState.Village v = Investigations.village(level, p.blockPosition().offset(-80, 0, 0));
                Incident inc = Investigations.create(level, v, st, IncidentTemplate.get("old_mine"), new Sites.Site(p.blockPosition().offset(12, 0, -10), 1, 0),
                        level.getGameTime(), RandomSource.create(5));
                CursedCompass.investigate(p, inc.id);
            });
            ctx.waitTicks(40);
            int state = ctx.computeOnClient(mc -> dev.rick.jjk.client.investigation.CursedCompassClient.state());
            if (state != dev.rick.jjk.core.net.CompassPayload.TRAIL) throw new AssertionError("the compass has a trail: " + state);
            ctx.takeScreenshot("gear_compass_trail");

            // A pull into a realm: the dark closing in.
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                InvestigationState st = InvestigationState.get(srv);
                InvestigationState.Village v = Investigations.village(level, p.blockPosition().offset(60, 0, 60));
                Incident inc = Investigations.create(level, v, st, IncidentTemplate.get("livestock"), new Sites.Site(p.blockPosition(), 1, 0),
                        level.getGameTime(), RandomSource.create(2));
                Investigations.begin(level, p, inc, inc.def(), st, level.getGameTime());
            });
            ctx.waitTicks(14);
            ctx.takeScreenshot("gear_realm_transition");
            ctx.waitTicks(60);
            ctx.takeScreenshot("gear_realm_pasture");
        }
    }
}
