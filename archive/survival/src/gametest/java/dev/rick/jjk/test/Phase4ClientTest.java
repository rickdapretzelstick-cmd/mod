package dev.rick.jjk.test;

import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.finish.Binds;
import dev.rick.jjk.progression.finish.Finishers;
import dev.rick.jjk.progression.finish.GuardianPhases;
import dev.rick.jjk.progression.finish.ParryChains;
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
import net.minecraft.world.phys.Vec3;

/**
 * Phase 4's moments as the player sees them: a bind (its two-shot and meter, then the win), a parry chain's glint and
 * the shatter when its posture breaks, a guardian's phase beat (the low cut under its title card, then close on it), and
 * the slash of a rescue. Screenshots phase4_*.png.
 */
public class Phase4ClientTest implements FabricClientGameTest {
    private static ServerPlayer me(net.minecraft.server.MinecraftServer srv) {
        return srv.getPlayerList().getPlayers().getFirst();
    }

    private static <T extends CommonCurseEntity> int spawn(TestServerContext server, EntityType<T> type, double ahead) {
        return server.computeOnServer(srv -> {
            ServerPlayer p = me(srv);
            ServerLevel level = (ServerLevel) p.level();
            for (CommonCurseEntity c : level.getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(40), e -> true)) c.discard();
            p.setHealth(p.getMaxHealth());
            p.setYRot(0);
            p.setYHeadRot(0);
            p.setXRot(0);
            T c = type.create(level, EntitySpawnReason.EVENT);
            c.setPos(p.getX(), p.getY(), p.getZ() + ahead);
            c.setYRot(180);
            level.addFreshEntity(c);
            c.setTarget(p);
            return c.getId();
        });
    }

    private static CommonCurseEntity curse(net.minecraft.server.MinecraftServer srv, int id) {
        return (CommonCurseEntity) ((ServerLevel) me(srv).level()).getEntity(id);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("effect give @a minecraft:resistance 600 4 true");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(srv -> {
                ServerPlayer p = me(srv);
                CharacterService.assign(p, Characters.get("yuji"));
                StrengthContests.setRandomGrabsForTest(false);
                ParryChains.setRandomChainsForTest(false);
                Finishers.forgetForTest(p);
                Binds.forgetForTest(p);
            });

            // A bind: its two-shot and meter, pressed home, then the win.
            int maw = spawn(server, ModEntities.SCHOOL_MAW, 2.2);
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                ServerPlayer p = me(srv);
                CommonCurseEntity c = curse(srv, maw);
                c.markWoundUpForTest();
                HitResolver.resolve(Hit.builder(p, "test_heavy").damage(1).tag(AttackTag.MELEE, AttackTag.HEAVY).build(), c);
                if (!Binds.binding(p)) throw new AssertionError("the heavy into its wind-up binds them");
            });
            ctx.waitTicks(6);
            if (!ctx.computeOnClient(mc -> dev.rick.jjk.client.finish.BindClient.active())) throw new AssertionError("the bind is on the client");
            ctx.takeScreenshot("phase4_bind_0");
            for (int i = 0; i < 4; i++) {
                server.runOnServer(srv -> Binds.tapForTest(me(srv)));
                ctx.waitTicks(1);
            }
            ctx.takeScreenshot("phase4_bind_1");
            for (int i = 0; i < 12 && server.computeOnServer(srv -> Binds.binding(me(srv))); i++) {
                server.runOnServer(srv -> Binds.tapForTest(me(srv)));
                ctx.waitTicks(1);
            }
            ctx.waitTicks(2);
            ctx.takeScreenshot("phase4_bind_2");
            if (ctx.computeOnClient(mc -> dev.rick.jjk.client.finish.BindClient.active())) throw new AssertionError("the bind is over on the client");
            ctx.waitTicks(30);

            // A parry chain: the glint before a blow, each parried, and the shatter.
            int hound = spawn(server, ModEntities.GRAVE_HOUND, 1.8);
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                CommonCurseEntity c = curse(srv, hound);
                // Whatever it had started on its own is dropped: the string starts now.
                c.returnedToFight(0);
                c.setRestForTest(0);
                if (!ParryChains.start(c, me(srv), 3)) throw new AssertionError("a string begins");
            });
            for (int t = 0; t < CommonCurseEntity.CHAIN_LEAD + 2 * 12 + 14; t++) {
                int tick = t;
                server.runOnServer(srv -> {
                    ServerPlayer p = me(srv);
                    CommonCurseEntity c = curse(srv, hound);
                    if (c == null || !c.chaining()) return;
                    for (int i = 0; i < c.chainBlows(); i++) {
                        if (c.actionTicksForTest() == c.chainBlowAt(i) - 1) {
                            Combat.state(p).stopGuard();
                            Combat.state(p).startGuard(p.level().getGameTime());
                        }
                    }
                });
                if (tick == CommonCurseEntity.CHAIN_LEAD - CommonCurseEntity.CHAIN_GLINT + 1) ctx.takeScreenshot("phase4_chain_glint");
                ctx.waitTicks(1);
            }
            server.runOnServer(srv -> Combat.state(me(srv)).stopGuard());
            boolean broke = server.computeOnServer(srv -> curse(srv, hound) != null && curse(srv, hound).isStaggered() && Finishers.opened(curse(srv, hound)));
            ctx.takeScreenshot("phase4_chain_break");
            if (!broke) throw new AssertionError("a whole string parried breaks its posture");
            ctx.waitTicks(20);

            // A guardian's phase beat: the low cut under its title card, then close on it.
            int bride = spawn(server, ModEntities.VEILED_BRIDE, 5);
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                CommonCurseEntity c = curse(srv, bride);
                GuardianPhases.trackForTest(c);
                c.setHealth(c.getMaxHealth() * 0.6f);
                GuardianPhases.checkForTest((ServerLevel) c.level(), c);
                if (!c.inBeat()) throw new AssertionError("past two thirds: its beat");
            });
            ctx.waitTicks(12);
            if (!ctx.computeOnClient(mc -> dev.rick.jjk.client.finish.EyecatchCamera.active())) throw new AssertionError("the eyecatch camera is on");
            ctx.takeScreenshot("phase4_eyecatch_0");
            ctx.waitTicks(22);
            ctx.takeScreenshot("phase4_eyecatch_1");
            ctx.waitTicks(30);

            // A rescue's slash, through a curse in front of the camera.
            int hound2 = spawn(server, ModEntities.GRAVE_HOUND, 3);
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                CommonCurseEntity c = curse(srv, hound2);
                Fx.play((ServerLevel) c.level(), "impact_rescue", c.position().add(0, c.getBbHeight() * 0.6, 0), new Vec3(0, 0, 1), 1.2f);
            });
            ctx.waitTicks(2);
            ctx.takeScreenshot("phase4_rescue");
        }
    }
}
