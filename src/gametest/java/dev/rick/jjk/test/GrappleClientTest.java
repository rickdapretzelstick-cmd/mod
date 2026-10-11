package dev.rick.jjk.test;

import dev.rick.jjk.client.training.ContestCamera;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Every grapple as its contestant sees it: each curse kind's grip in the wide, the first shot a good tap cuts to and the
 * first a miss cuts to (screenshots grapple_<kind>_wide / _good / _miss.png); a verdict really cuts (and a second inside
 * the hold doesn't); the reversal when one is broken free (grapple_win) and the follow when one overpowers you
 * (grapple_loss). The contest's clock is held still while the shots are taken.
 */
public class GrappleClientTest implements FabricClientGameTest {
    record Kind(String name, EntityType<? extends CommonCurseEntity> type) {}

    static final List<Kind> KINDS = List.of(new Kind("school_maw", ModEntities.SCHOOL_MAW), new Kind("school_crawler", ModEntities.SCHOOL_CRAWLER),
            new Kind("forest_stalker", ModEntities.FOREST_STALKER), new Kind("grave_hound", ModEntities.GRAVE_HOUND),
            new Kind("veiled_bride", ModEntities.VEILED_BRIDE), new Kind("bloat_toad", ModEntities.BLOAT_TOAD),
            new Kind("brood_mother", ModEntities.BROOD_MOTHER));

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(srv -> StrengthContests.setManualClockForTest(true));
            try {
                for (int i = 0; i < KINDS.size(); i++) {
                    Kind k = KINDS.get(i);
                    grab(server, k);
                    ctx.waitTicks(12);
                    ctx.runOnClient(mc -> ContestCamera.pinForTest(ContestCamera.Shot.WIDE));
                    ctx.waitTicks(10);
                    ctx.takeScreenshot("grapple_" + k.name + "_wide");
                    String kindHere = ctx.computeOnClient(mc -> dev.rick.jjk.client.training.TrainingClient.kind);
                    if (!k.name.equals(kindHere)) throw new AssertionError("the client knows what it's grappling: " + kindHere);
                    if (i == 0) {
                        // A verdict cuts, by the kind's own list; another inside the hold doesn't.
                        ctx.runOnClient(mc -> {
                            ContestCamera.pinForTest(null);
                            ContestCamera.verdictForTest(100, TrainingCheck.GOOD);
                        });
                        ContestCamera.Shot first = ctx.computeOnClient(mc -> ContestCamera.shot());
                        if (first == ContestCamera.Shot.WIDE) throw new AssertionError("a good tap cuts away from the wide");
                        ctx.runOnClient(mc -> ContestCamera.verdictForTest(101, TrainingCheck.MISS));
                        ContestCamera.Shot held = ctx.computeOnClient(mc -> ContestCamera.shot());
                        if (held != first) throw new AssertionError("a cut is held a moment before the next: " + first + " -> " + held);
                    }
                    for (String which : List.of("good", "miss")) {
                        ctx.runOnClient(mc -> ContestCamera.pinForTest(shotFor(k.name, which)));
                        ctx.waitTicks(8);
                        ctx.takeScreenshot("grapple_" + k.name + "_" + which);
                        boolean clear = ctx.computeOnClient(mc -> {
                            var cam = mc.gameRenderer.mainCamera().position();
                            BlockPos b = BlockPos.containing(cam);
                            return mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty();
                        });
                        if (!clear) throw new AssertionError(k.name + " " + which + ": the shot sits inside a block");
                    }
                    ctx.runOnClient(mc -> ContestCamera.pinForTest(null));
                    // The first is broken free (the reversal), the second overpowers (the follow); the rest just end.
                    boolean win = i != 1;
                    server.runOnServer(srv -> StrengthContests.finishForTest(srv.getPlayerList().getPlayers().getFirst(), win));
                    if (i <= 1) {
                        ctx.waitTicks(i == 0 ? 6 : 8);
                        ctx.takeScreenshot(i == 0 ? "grapple_win" : "grapple_loss");
                        ContestCamera.Shot payoff = ctx.computeOnClient(mc -> ContestCamera.shot());
                        if (payoff != (i == 0 ? ContestCamera.Shot.REVERSAL : ContestCamera.Shot.THROWN)) throw new AssertionError("the result's own shot: " + payoff);
                    }
                    ctx.waitTicks(60);
                    server.runOnServer(srv -> {
                        ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                        for (CommonCurseEntity c : ((ServerLevel) p.level()).getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(16), e -> true)) c.discard();
                        p.setHealth(p.getMaxHealth());
                    });
                    ctx.waitTicks(10);
                    boolean over = ctx.computeOnClient(mc -> !dev.rick.jjk.client.training.TrainingClient.active());
                    if (!over) throw new AssertionError(k.name + ": the contest ends");
                }
            } finally {
                server.runOnServer(srv -> StrengthContests.setManualClockForTest(false));
            }
        }
    }

    static ContestCamera.Shot shotFor(String kind, String which) {
        return switch (kind + "/" + which) {
            case "school_maw/good", "grave_hound/good", "veiled_bride/good", "bloat_toad/good" -> ContestCamera.Shot.LOW_ON_YOU;
            case "school_crawler/good" -> ContestCamera.Shot.HANDS;
            case "forest_stalker/good", "brood_mother/good" -> ContestCamera.Shot.OVER_ITS_SHOULDER;
            case "school_maw/miss", "veiled_bride/miss" -> ContestCamera.Shot.OVER_YOUR_SHOULDER;
            case "grave_hound/miss" -> ContestCamera.Shot.HANDS;
            case "bloat_toad/miss" -> ContestCamera.Shot.ABOVE;
            default -> ContestCamera.Shot.LOW_UNDER_IT;
        };
    }

    /** A curse of {@code k}'s kind grabs the player (past the telegraph), the contest held on its first checks. */
    private static void grab(TestServerContext server, Kind k) {
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
            ServerLevel level = (ServerLevel) p.level();
            StrengthContests.setRandomGrabsForTest(false);
            p.setHealth(p.getMaxHealth());
            // Curses are only seen through cursed eyes.
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(dev.rick.jjk.progression.ProgressionItems.CURSED_GLASSES));
            p.setYRot(0);
            p.setYHeadRot(0);
            CommonCurseEntity c = k.type.create(level, EntitySpawnReason.EVENT);
            c.setPos(p.getX(), p.getY(), p.getZ() + 2.4);
            c.setYRot(180);
            level.addFreshEntity(c);
            c.setTarget(p);
            StrengthContests.forgetForTest(p, c);
            if (!StrengthContests.begin(srv, c, p)) throw new AssertionError(k.name + " grabbed: " + StrengthContests.unavailable(p));
            // Past the intro, its clock then held still (the contest stays open while the shots are taken).
            StrengthContests.Session s = StrengthContests.of(p);
            StrengthContests.input(p, new TrainingNet.Input(s.id, 1, 1, TrainingNet.READY, -1, -1, 0));
            StrengthContests.advanceForTest(p, JJKConfig.get().training.contestIntroMs + 1);
        });
    }
}
