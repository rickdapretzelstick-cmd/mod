package dev.rick.jjk.test;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rick.jjk.command.TestCommand;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.GuardianArena;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /jjk test}: every button on every page runs a command that exists (it parses whole), the pages print, and the
 * shortcuts do what they say: a hostile curse ahead and cleared again, a grab straight into a contest, a realm entered
 * and left, a guardian contract in the body's own arena.
 */
public class TestMenuTests {
    private static ServerPlayer player(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.setYRot(-90);
        StrengthContests.setRandomGrabsForTest(false);
        StrengthContests.forgetForTest(p, null);
        return p;
    }

    private static CommandSourceStack as(MinecraftServer s, ServerPlayer p) {
        return s.createCommandSourceStack().withEntity(p).withPosition(p.position()).withLevel((net.minecraft.server.level.ServerLevel) p.level())
                .withSuppressedOutput();
    }

    private static int run(MinecraftServer s, ServerPlayer p, String cmd) {
        try {
            return s.getCommands().getDispatcher().execute(cmd.startsWith("/") ? cmd.substring(1) : cmd, as(s, p));
        } catch (CommandSyntaxException e) {
            throw new AssertionError(cmd + ": " + e.getMessage());
        }
    }

    @GameTest(environment = "jjk-test:test_menu_a")
    public void everyButtonRunsARealCommand(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        ServerPlayer p = player(h);
        for (String cmd : TestCommand.allCommands()) {
            ParseResults<CommandSourceStack> parse = s.getCommands().getDispatcher().parse(cmd.substring(1), as(s, p));
            h.assertTrue(parse.getExceptions().isEmpty() && !parse.getReader().canRead() && parse.getContext().getCommand() != null,
                    "a button runs a command that exists: " + cmd);
        }
        h.assertTrue(run(s, p, "/jjk test") == 1, "the menu prints");
        h.assertTrue(run(s, p, "/jjk test realms") > 5, "a page prints its actions");
        // A hostile curse ahead, then cleared.
        h.assertTrue(run(s, p, "/jjk test spawn grave_hound") == 1, "spawned");
        AABB near = new AABB(p.blockPosition()).inflate(12);
        var hounds = h.getLevel().getEntitiesOfClass(CommonCurseEntity.class, near);
        h.assertTrue(hounds.size() == 1 && hounds.getFirst().getTarget() == p, "one hound, after you: " + hounds.size() + (hounds.isEmpty() ? "" : " target " + hounds.getFirst().getTarget() + " at " + hounds.getFirst().position() + " vs " + p.position()));
        h.assertTrue(run(s, p, "/jjk test weak grave_hound") == 1, "a weak one");
        h.assertTrue(h.getLevel().getEntitiesOfClass(CommonCurseEntity.class, near, c -> c.getHealth() < c.getMaxHealth() * 0.2f).size() == 1, "finishable");
        run(s, p, "/jjk test clear");
        h.assertTrue(h.getLevel().getEntitiesOfClass(CommonCurseEntity.class, near).isEmpty(), "cleared");
        // Grabbed at once.
        StrengthContests.setManualClockForTest(true);
        try {
            h.assertTrue(run(s, p, "/jjk test grab") == 1 && StrengthContests.active(p), "straight into a contest");
            StrengthContests.finishForTest(p, true);
        } finally {
            StrengthContests.setManualClockForTest(false);
        }
        run(s, p, "/jjk test clear");
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:test_menu_b")
    public void realmsAndGuardiansOpenFromTheMenu(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        ServerPlayer p = player(h);
        p.setGameMode(GameType.CREATIVE);
        h.assertTrue(run(s, p, "/jjk test realm old_mine") == 1, "the mine incident set off");
        CursedRealms.finishPullForTest(p);
        h.assertTrue(CursedRealms.inRealm(p), "in its realm");
        h.assertTrue("mine_realm".equals(CursedRealms.arenaHere(p).layout), "the mine: " + CursedRealms.arenaHere(p).layout);
        h.assertTrue(run(s, p, "/jjk test leave") == 1, "left");
        h.runAfterDelay(5, () -> {
            h.assertTrue(!CursedRealms.inRealm(p), "home again");
            h.assertTrue(run(s, p, "/jjk test guardian guardian_cyclops_bruiser") == 1, "a guardian contract");
            CursedRealms.finishPullForTest(p);
            var a = CursedRealms.arenaHere(p);
            h.assertTrue(a != null && GuardianArena.idFor("guardian_cyclops_bruiser").equals(a.layout), "the body's own arena: " + (a == null ? null : a.layout));
            run(s, p, "/jjk test leave");
            h.runAfterDelay(5, () -> {
                p.discard();
                h.succeed();
            });
        });
    }
}
