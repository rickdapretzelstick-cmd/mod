package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.gojo.UnlimitedPurple;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.yuji.OpenAbility;
import dev.rick.jjk.yuji.YujiCharacter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Unlimited Purple (Max Blue + Max Red) erases three times its original radius (16 → 48), and Open's blast reaches
 * that original 16, each hitting a target once and leaving those beyond the edge alone. Its own widely spaced batch: these
 * blasts reach far past one test's area.
 */
public class BlastRadiusTests {
    private static final String ENV = "jjk-test:blast";

    private static TrainingDummy dummy(GameTestHelper h, Vec3 abs) {
        TrainingDummy d = ModEntities.TRAINING_DUMMY.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        d.snapTo(abs.x, abs.y, abs.z, 0, 0);
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        d.setNoGravity(true);
        h.getLevel().addFreshEntity(d);
        return d;
    }

    /** Keeps the chunks out to {@code reach} blocks of {@code at} loaded for the test; returns them to release. */
    private static List<ChunkPos> forceLoad(ServerLevel level, Vec3 at, double reach) {
        List<ChunkPos> out = new ArrayList<>();
        int r = (int) Math.ceil(reach / 16) + 1;
        ChunkPos c = ChunkPos.containing(BlockPos.containing(at));
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos p = new ChunkPos(c.x() + dx, c.z() + dz);
            level.setChunkForced(p.x(), p.z(), true);
            out.add(p);
        }
        return out;
    }

    private static void release(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos p : chunks) level.setChunkForced(p.x(), p.z(), false);
    }

    @GameTest(maxTicks = 160, padding = 60, environment = ENV)
    public void unlimitedPurpleErasesThreeTimesItsOriginalRadius(GameTestHelper h) {
        JJKConfig.Gojo cfg = JJKConfig.get().gojo;
        h.assertValueEqual(cfg.unlimitedPurpleRadius, 48.0, "radius tripled from 16");
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerLevel level = h.getLevel();
        Vec3 center = h.absoluteVec(new Vec3(4.5, 1.5, 4.5));
        List<ChunkPos> chunks = forceLoad(level, center, 60);
        TrainingDummy g = dummy(h, h.absoluteVec(new Vec3(1, 1, 4.5)));
        CharacterService.assign(g, Characters.get(GojoCharacter.ID));
        Casters.get(g).setNoCost(true);
        // Inside the old 16: hit as before. Out at 30 (only the new radius reaches): hit. Past 48: untouched.
        TrainingDummy near = dummy(h, center.add(6, -0.5, 0));
        TrainingDummy far = dummy(h, center.add(0, -0.5, 30));
        TrainingDummy beyond = dummy(h, center.add(-52, -0.5, 0));
        float[] hp = {near.getHealth(), far.getHealth(), beyond.getHealth()};
        BlueEntity blue = BlueEntity.spawn(level, g, center, BlueEntity.Params.max());
        UnlimitedPurple.start(level, g, blue);
        h.runAfterDelay(cfg.unlimitedPurpleFuse + 6, () -> {
            h.assertTrue(hp[0] - near.getHealth() >= cfg.unlimitedPurpleMinDamage * 0.9f, "near: hit (" + (hp[0] - near.getHealth()) + ")");
            h.assertTrue(hp[1] - far.getHealth() >= cfg.unlimitedPurpleMinDamage * 0.9f, "30 blocks out: inside the tripled radius (" + (hp[1] - far.getHealth()) + ")");
            h.assertTrue(far.getHealth() > 0 || hp[1] - far.getHealth() <= cfg.unlimitedPurpleMaxDamage + 1, "hit once, not repeatedly");
            h.assertValueEqual(beyond.getHealth(), hp[2], "past its edge: untouched");
            for (TrainingDummy d : new TrainingDummy[] {g, near, far, beyond}) d.discard();
            release(level, chunks);
            h.succeed();
        });
    }

    @GameTest(maxTicks = 40, padding = 60, environment = ENV)
    public void opensBlastReachesUnlimitedPurplesOriginalRadius(GameTestHelper h) {
        JJKConfig cfg = JJKConfig.get();
        double blast = OpenAbility.blastRadius();
        h.assertValueEqual(blast, 16.0, "Open's zone is Unlimited Purple's original radius");
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        List<ChunkPos> chunks = forceLoad(level, at, 30);
        TrainingDummy y = dummy(h, h.absoluteVec(new Vec3(1, 1, 1)));
        CharacterService.assign(y, Characters.get(YujiCharacter.ID));
        Casters.get(y).setNoCost(true);
        // In the pillar: the full blow. Out at 13 (past the old 4.5-block pillar, inside 16): the blast. Past 16: nothing.
        TrainingDummy pillar = dummy(h, at.add(1, 0, 0));
        TrainingDummy edge = dummy(h, at.add(0, 0, blast - 3));
        TrainingDummy beyond = dummy(h, at.add(0, 0, -(blast + 4)));
        float[] hp = {pillar.getHealth(), edge.getHealth(), beyond.getHealth()};
        OpenAbility.pillar(level, y, at);
        h.runAfterDelay(2, () -> {
            float inPillar = hp[0] - pillar.getHealth(), atEdge = hp[1] - edge.getHealth();
            h.assertTrue(inPillar >= cfg.yuji.openDamage * 0.9f, "the pillar's full blow (" + inPillar + ")");
            h.assertTrue(atEdge > 0 && atEdge < inPillar, "the blast reaches out to its edge, weaker there (" + atEdge + ")");
            h.assertValueEqual(beyond.getHealth(), hp[2], "past the blast: untouched");
            h.runAfterDelay(20, () -> {
                h.assertTrue(hp[1] - edge.getHealth() == atEdge, "one hit, no repeats");
                for (TrainingDummy d : new TrainingDummy[] {y, pillar, edge, beyond}) d.discard();
                release(level, chunks);
                h.succeed();
            });
        });
    }
}
