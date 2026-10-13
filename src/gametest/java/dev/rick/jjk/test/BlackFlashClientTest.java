package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.BlackFlash;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Tuning aid (opt-in): the Black Flash as the striker sees it, each from a real resolved blow on a curse in front of them.
 * The full impact (contact, inked frame, branching lightning, shockwaves, streaks), its follow-through and decay; the
 * lethal escalation; a blocked one that only fizzles; and the lethal one again under Reduced Flashes. Screenshots
 * bf_*.png at the contact, a few ticks in, and after.
 */
public class BlackFlashClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            // A character perceives curses (the one being hit is in frame).
            server.runCommand("jjk character yuji @a");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            shot(ctx, server, "impact", false, false);
            shot(ctx, server, "lethal", true, false);
            shot(ctx, server, "fizzle", false, true);
            ctx.runOnClient(mc -> JJKConfig.get().client.reducedFlashes = true);
            shot(ctx, server, "lethal_reduced", true, false);
            ctx.runOnClient(mc -> JJKConfig.get().client.reducedFlashes = false);
        }
    }

    private static void shot(ClientGameTestContext ctx, TestServerContext server, String name, boolean lethal, boolean blocked) {
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            p.setGameMode(GameType.CREATIVE);
            Vec3 at = p.position();
            p.teleportTo(level, at.x, at.y, at.z, Set.of(), -90f, 8f, false);
            for (CommonCurseEntity old : level.getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(30))) old.discard();
            CommonCurseEntity c = ModEntities.GRAVE_HOUND.create(level, EntitySpawnReason.EVENT);
            c.setPos(at.x + 3.2, at.y, at.z + 0.4);
            c.setNoAi(true);
            level.addFreshEntity(c);
        });
        ctx.waitTicks(15);
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            CommonCurseEntity c = level.getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(10)).getFirst();
            if (blocked) Combat.state(c).startGuard(level.getGameTime() - 40);
            if (lethal) c.setHealth(1f);
            Hit.Builder b = Hit.builder(p, "test_black_flash").damage(lethal ? 50 : 3).tag(AttackTag.MELEE).origin(p.getEyePosition()).noComboScaling()
                    .knockback(Knockback.directional(HakariCombat.flat(p), 1.6, 0.4));
            if (!blocked) b.tag(AttackTag.UNBLOCKABLE);
            HitResult r = HitResolver.resolve(b.build(), c);
            BlackFlash.land(level, p, c, r, c.getBoundingBox().getCenter(), HakariCombat.flat(p), lethal ? 1.8f : 1.2f);
        });
        ctx.waitTicks(1);
        ctx.takeScreenshot("bf_" + name + "_contact");
        ctx.waitTicks(3);
        ctx.takeScreenshot("bf_" + name + "_lightning");
        ctx.waitTicks(14);
        ctx.takeScreenshot("bf_" + name + "_after");
        ctx.waitTicks(40);
    }
}
