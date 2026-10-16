package dev.rick.jjk.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.move.Move;
import dev.rick.jjk.core.move.MoveAbility;
import dev.rick.jjk.core.move.MoveLibrary;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.yuji.YujiCharacter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Moves made in the move editor (tools/move_editor, core.move): the shipped examples load, and run as the editor shows
 * them -- the dash punch's hitbox lands, the grab slam lifts its target and throws it down, a whiff ends the move at
 * its logic event, and a key bound to a move runs it in place of the kit's ability.
 */
public class MoveEditorTests {
    private static JsonObject example(String name) {
        try (InputStream in = MoveLibrary.class.getResourceAsStream("/jjk_moves/" + name + ".json")) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("example move " + name + ": " + e);
        }
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = -2; x < size; x++) for (int z = -2; z < size; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        return d;
    }

    private static TrainingDummy user(GameTestHelper h, double x, double z, LivingEntity face, Move move) {
        TrainingDummy g = dummy(h, x, z);
        g.setOnGround(true);
        CharacterService.assign(g, Characters.get(YujiCharacter.ID));
        AbilityCaster c = Casters.get(g);
        c.setNoCost(true);
        c.setCustomMoves(Map.of(AbilitySlot.SKILL_1, new MoveAbility(move)));
        Vec3 d = face.getBoundingBox().getCenter().subtract(g.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        g.setYRot(yaw);
        g.setYHeadRot(yaw);
        g.setYBodyRot(yaw);
        return g;
    }

    @GameTest(maxTicks = 5)
    public void examplesLoad(GameTestHelper h) {
        Move dash = Move.parse(example("example_dash_punch"));
        Move grab = Move.parse(example("example_grab_slam"));
        h.assertValueEqual(dash.id(), "example_dash_punch", "dash punch id");
        h.assertValueEqual(dash.ticks(), 15, "750 ms is 15 ticks");
        h.assertTrue(dash.hitboxes().size() == 1 && dash.hitboxes().get(0).start() == 5 && dash.hitboxes().get(0).end() == 7, "hitbox 250-350 ms is ticks 5-7");
        h.assertTrue(grab.grabs().size() == 1 && grab.hitboxes().get(0).grab(), "grab slam grabs");
        h.assertTrue(dash.clipJson().contains("\"name\":\"move_example_dash_punch\"") && dash.clipJson().contains("\"keys\""), "the clip carries its keys under the move's clip name");
        h.succeed();
    }

    @GameTest(maxTicks = 60)
    public void dashPunchLands(GameTestHelper h) {
        floor(h, 12);
        Move m = MoveLibrary.put(example("example_dash_punch"));
        TrainingDummy target = dummy(h, 4.2, 2.5);
        TrainingDummy g = user(h, 1.5, 2.5, target, m);
        float hp = target.getHealth();
        h.assertTrue(Casters.get(g).input(AbilitySlot.SKILL_1, true, 0, 0, null), "the bound key runs the move (" + Casters.get(g).lastRefusal + ")");
        h.assertTrue(Casters.get(g).isBusy(), "the move is running");
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 5, "the palm landed (" + (hp - target.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 40)
    public void whiffEndsAtTheLogicEvent(GameTestHelper h) {
        floor(h, 12);
        Move m = MoveLibrary.put(example("example_dash_punch"));
        TrainingDummy far = dummy(h, 10.5, 10.5);
        TrainingDummy g = user(h, 1.5, 2.5, far, m);
        g.setYRot(180);
        Casters.get(g).input(AbilitySlot.SKILL_1, true, 0, 0, null);
        long start = h.getLevel().getGameTime();
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "ended");
            long ran = h.getLevel().getGameTime() - start;
            h.assertTrue(ran <= 10, "ended at the miss check (400 ms), not at 750 ms (" + ran + " ticks)");
        });
    }

    @GameTest(maxTicks = 80)
    public void grabSlamLiftsAndThrows(GameTestHelper h) {
        floor(h, 12);
        Move m = MoveLibrary.put(example("example_grab_slam"));
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = user(h, 1.5, 2.5, target, m);
        float hp = target.getHealth();
        double ground = target.getY();
        double[] top = {ground};
        boolean[] held = {false};
        Casters.get(g).input(AbilitySlot.SKILL_1, true, 0, 0, null);
        h.onEachTick(() -> {
            top[0] = Math.max(top[0], target.getY());
            if (Combat.has(target, CombatStatus.GRABBED)) held[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(held[0], "they were grabbed");
            h.assertTrue(top[0] - ground > 1.0, "lifted (" + (top[0] - ground) + ")");
            h.assertTrue(!Combat.has(target, CombatStatus.GRABBED), "let go");
            h.assertTrue(hp - target.getHealth() >= 10, "grab and slam damage (" + (hp - target.getHealth()) + ")");
        });
    }
}
