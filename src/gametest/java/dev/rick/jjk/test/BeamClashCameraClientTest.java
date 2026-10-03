package dev.rick.jjk.test;

import dev.rick.jjk.client.clash.BeamClashCamera;
import dev.rick.jjk.client.clash.BeamClashClient;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.ryu.RyuCharacter;
import dev.rick.jjk.yuta.YutaCharacter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * The beam clash camera through a real client: the local player (Yuta) answers a Ryu's Every Last Drop the moment its
 * charge begins, and from the moment the beams meet the view must stay on the wide side-on shot every tick of the
 * intro, the whole struggle and the resolution, then hand back the player's own first-person view when it ends.
 * Opt-in: screenshots land in build/run/clientGameTest/screenshots as bcam*.png.
 */
public class BeamClashCameraClientTest implements FabricClientGameTest {
    private static final int[] RYU = new int[1];

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(20);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                CharacterService.assign(p, Characters.get(YutaCharacter.ID));
                Casters.get(p).setNoCost(true);
                p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), java.util.Set.of(), 0f, 0f, true);
                TrainingDummy r = ModEntities.TRAINING_DUMMY.create(p.level(), EntitySpawnReason.COMMAND);
                r.snapTo(p.getX(), p.getY(), p.getZ() + 14, 180f, 0f);
                r.setMode(TrainingDummy.Mode.STAND);
                r.setAutoHeal(false);
                p.level().addFreshEntity(r);
                CharacterService.assign(r, Characters.get(RyuCharacter.ID));
                Casters.get(r).setNoCost(true);
                Casters.get(r).setAwakening(Casters.get(r).maxAwakening());
                RYU[0] = r.getId();
            });
            ctx.waitTicks(10);
            // Ryu starts charging; the player's window opens with it, and they answer at once.
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                var r = p.level().getEntity(RYU[0]);
                if (!(r instanceof TrainingDummy ryu)) throw new AssertionError("Ryu is there");
                if (!Casters.get(ryu).input(AbilitySlot.ULTIMATE, true, 0, 0, null)) throw new AssertionError("Every Last Drop");
                if (!BeamClashManager.windowOpen(p)) throw new AssertionError("the player's window opens as his charge begins: " + BeamClashManager.lastMiss);
                if (!BeamClashManager.tryCounter(Casters.get(p))) throw new AssertionError("the counter");
            });
            int ticks = 0, onTicks = 0, inClash = 0, gaps = 0;
            boolean sawIntro = false, sawDuel = false, sawResolve = false;
            boolean shotIntro = false, shotDuel = false, shotResolve = false;
            int lastPhase = -1;
            while (ticks++ < 20 * 40) {
                ctx.waitTick();
                int phase = ctx.computeOnClient(mc -> {
                    BeamClashClient.View v = BeamClashClient.mine();
                    return v == null ? -1 : v.phase;
                });
                int phaseAge = ctx.computeOnClient(mc -> {
                    BeamClashClient.View v = BeamClashClient.mine();
                    return v == null ? -1 : v.phaseAge;
                });
                boolean on = ctx.computeOnClient(mc -> BeamClashCamera.active() && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
                boolean inPhase = phase == BeamClashClient.INTRO || phase == BeamClashClient.DUEL || phase == BeamClashClient.RESOLVE;
                if (inPhase) {
                    inClash++;
                    if (on) onTicks++;
                    // The first tick or two of the intro is the swing out; after that it must never let go.
                    else if (!(phase == BeamClashClient.INTRO && phaseAge < 3)) gaps++;
                }
                if (phase == BeamClashClient.INTRO) sawIntro = true;
                if (phase == BeamClashClient.DUEL) sawDuel = true;
                if (phase == BeamClashClient.RESOLVE) sawResolve = true;
                if (phase == BeamClashClient.INTRO && phaseAge >= 16 && !shotIntro) {
                    ctx.takeScreenshot("bcam1_intro");
                    shotIntro = true;
                }
                if (phase == BeamClashClient.DUEL && phaseAge >= 100 && !shotDuel) {
                    ctx.takeScreenshot("bcam2_struggle");
                    shotDuel = true;
                }
                if (phase == BeamClashClient.RESOLVE && phaseAge >= 24 && !shotResolve) {
                    ctx.takeScreenshot("bcam3_breakthrough");
                    shotResolve = true;
                }
                if (lastPhase >= 0 && phase < 0 && sawResolve) break;
                lastPhase = phase;
            }
            System.out.println("[beam camera] clash ticks " + inClash + ", wide shot held " + onTicks + ", gaps " + gaps);
            if (!sawIntro || !sawDuel || !sawResolve) throw new AssertionError("the clash ran intro, duel and resolve (" + sawIntro + sawDuel + sawResolve + ")");
            if (gaps > 0) throw new AssertionError("the wide shot let go for " + gaps + " ticks mid-clash");
            ctx.waitTicks(25);
            boolean restored = ctx.computeOnClient(mc -> !BeamClashCamera.active() && mc.options.getCameraType() == CameraType.FIRST_PERSON);
            ctx.takeScreenshot("bcam4_after");
            if (!restored) throw new AssertionError("the player's own first-person view is back after the clash");
        }
    }
}
