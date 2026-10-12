package dev.rick.jjk.test;

import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.finish.Finishers;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.List;

/**
 * A finisher as its finisher sees it, for each character: the cinematic's cuts (over the shoulder, low on them, the
 * blow side-on) and the blow landing. Screenshots finisher_<character>_<n>.png.
 */
public class FinisherClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            for (String character : List.of("yuji", "gojo", "hakari", "yuta", "ryu", "")) {
                server.runOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                    ServerLevel level = (ServerLevel) p.level();
                    StrengthContests.setRandomGrabsForTest(false);
                    p.setHealth(p.getMaxHealth());
                    p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(dev.rick.jjk.progression.ProgressionItems.CURSED_GLASSES));
                    CharacterService.assign(p, character.isEmpty() ? null : Characters.get(character));
                    Finishers.forgetForTest(p);
                    p.setYRot(0);
                    p.setYHeadRot(0);
                    CommonCurseEntity c = ModEntities.SCHOOL_MAW.create(level, EntitySpawnReason.EVENT);
                    c.setPos(p.getX(), p.getY(), p.getZ() + 2.2);
                    c.setYRot(180);
                    level.addFreshEntity(c);
                    c.staggerBy(level, p);
                    c.setHealth(c.getMaxHealth() * 0.2f);
                    HitResolver.resolve(Hit.builder(p, "test_jab").damage(2).tag(AttackTag.MELEE).build(), c);
                    if (!Finishers.finishing(p)) throw new AssertionError(character + ": the finisher starts");
                });
                String name = character.isEmpty() ? "plain" : character;
                for (int shot = 0; shot < 3; shot++) {
                    ctx.waitTicks(shot == 0 ? 6 : 9);
                    ctx.takeScreenshot("finisher_" + name + "_" + shot);
                }
                ctx.waitTicks(20);
                boolean over = ctx.computeOnClient(mc -> !dev.rick.jjk.client.finish.FinisherCamera.active());
                if (!over) throw new AssertionError(name + ": the cinematic ends");
                server.runOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                    for (CommonCurseEntity c : ((ServerLevel) p.level()).getEntitiesOfClass(CommonCurseEntity.class, p.getBoundingBox().inflate(30), e -> true)) c.discard();
                });
                ctx.waitTicks(10);
            }
        }
    }
}
