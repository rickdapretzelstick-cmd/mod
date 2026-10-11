package dev.rick.jjk.test;

import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.guardian.EssenceCapture;
import dev.rick.jjk.progression.guardian.GuardianRemnantEntity;
import dev.rick.jjk.progression.investigation.Clearance;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.Vessels;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * The essence step as its challenger sees it: the guardian's remnant in its arena, the seal's containment (the shared
 * skill check over the world, no scene), a slipped containment, and the items (seal, sealed essence, an ordinary and an
 * essence-bearing vessel). Screenshots capture_*.png.
 */
public class CaptureClientTest implements FabricClientGameTest {
    private static String contract;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            ctx.waitTicks(40);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                InvestigationState st = InvestigationState.get(srv);
                Clearance.set(p, 1);
                InvestigationState.Village v = Investigations.village(level, p.blockPosition().offset(-80, 0, 0));
                Incident in = Investigations.create(level, v, st, IncidentTemplate.get("guardian_cliff"), new Sites.Site(p.blockPosition().offset(20, 0, 20), 1, 0),
                        level.getGameTime(), RandomSource.create(3));
                Investigations.challengeForTest(in, p);
                contract = in.id;
                CursedRealms.enter(p, in, st);
            });
            ctx.waitTicks(80);
            ctx.takeScreenshot("capture_guardian");
            server.runOnServer(srv -> {
                Incident in = InvestigationState.get(srv).incident(contract);
                if (Investigations.curseEntity(srv, Investigations.objectiveTargetForTest(in)) instanceof CommonCurseEntity g) g.kill((ServerLevel) g.level());
            });
            ctx.waitTicks(40);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                GuardianRemnantEntity r = remnant(p);
                if (r == null) throw new AssertionError("a remnant where the guardian fell");
                p.teleportTo(r.getX(), r.getY(), r.getZ() - 3.5);
                p.setYRot(0);
                p.setYHeadRot(0);
                p.setXRot(10);
                p.getInventory().setItem(0, new ItemStack(ProgressionItems.EXORCISM_SEAL, 3));
                p.getInventory().setItem(1, Vessels.sealed(contract, "the skeletal claw hunter"));
                p.getInventory().setItem(2, new ItemStack(ProgressionItems.CURSED_RING));
                p.getInventory().setItem(3, Vessels.essenceBearing(CharacterStories.YUTA, null));
                p.getInventory().setSelectedSlot(0);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("capture_remnant");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                if (!EssenceCapture.begin(p, remnant(p))) throw new AssertionError("the seal starts the containment");
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("capture_intro");
            ctx.waitTicks(40);
            ctx.takeScreenshot("capture_active");
            boolean on = ctx.computeOnClient(mc -> dev.rick.jjk.client.training.TrainingClient.containment());
            if (!on) throw new AssertionError("the client shows the containment");
            // Left alone, it slips loose (two misses): no seal spent, the remnant still there.
            ctx.waitTicks(120);
            ctx.takeScreenshot("capture_slipped");
            ctx.waitTicks(60);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                if (EssenceCapture.active(p)) throw new AssertionError("the slipped containment ended");
                if (p.getInventory().getItem(0).getCount() != 3) throw new AssertionError("no seal spent on a failure");
                if (remnant(p) == null) throw new AssertionError("the remnant waits");
            });
            boolean screen = ctx.computeOnClient(mc -> mc.gui.screen() != null);
            if (screen) throw new AssertionError("no Retry/Leave screen for a containment");
            ctx.takeScreenshot("capture_after");
        }
    }

    private static GuardianRemnantEntity remnant(ServerPlayer p) {
        var l = CursedRealms.level(p.level().getServer());
        var found = l.getEntitiesOfClass(GuardianRemnantEntity.class, new AABB(p.blockPosition()).inflate(40), e -> contract.equals(e.contract()));
        return found.isEmpty() ? null : found.getFirst();
    }
}
