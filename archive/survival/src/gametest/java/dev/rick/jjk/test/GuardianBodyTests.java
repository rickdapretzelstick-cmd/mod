package dev.rick.jjk.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.guardian.GuardianBody;
import dev.rick.jjk.progression.guardian.Guardians;
import dev.rick.jjk.progression.investigation.Clearance;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The four Grade 1 guardian bodies (from the Blockbench pack, tools/install_guardians.py): each is its own registered
 * curse now, not a stand-in, fighting with its stand-in's moves; it keeps its look (three per body) through a save; it has
 * a model (the whole skeleton) and texture for every look and a clip for everything its moves play, keyed only to bones
 * its models have; and
 * a Grade 1 contract raises the real body, in the look the contract chose.
 */
public class GuardianBodyTests {
    /** What each body's moves play: its stand-in's own clips, plus the ones every curse uses and its phase beat. */
    private static final List<String> SHARED = List.of("idle", "move", "hurt", "stagger", "death", "grapple", "grapple_shove", "grapple_press",
            "grapple_fling", "unleash");
    private static final Map<String, List<String>> MOVES = Map.of(
            "guardian_claw_hunter", List.of("bite", "crouch", "attack", "stumble", "howl"),
            "guardian_pale_stalker", List.of("telegraph", "lunge", "exposed", "rake", "retreat"),
            "guardian_cyclops_bruiser", List.of("attack", "bite_quick", "lash", "slam", "recover"),
            "guardian_veiled_reacher", List.of("flicker", "grasp", "wail", "breath"));

    private static final List<String> SKELETON = List.of("hips", "torso", "head", "right_upper_arm", "right_forearm", "right_hand", "left_upper_arm",
            "left_forearm", "left_hand", "right_thigh", "right_shin", "right_foot", "left_thigh", "left_shin", "left_foot");

    private static Path asset(String path) {
        Optional<Path> p = FabricLoader.getInstance().getModContainer("jjk").flatMap(m -> m.findPath("assets/jjk/" + path));
        return p.filter(Files::exists).orElse(null);
    }

    private static JsonObject json(Path p) {
        try {
            return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void bones(JsonElement node, Set<String> out) {
        if (!node.isJsonObject()) return;
        out.add(node.getAsJsonObject().get("name").getAsString());
        for (JsonElement c : node.getAsJsonObject().getAsJsonArray("children")) bones(c, out);
    }

    @GameTest(environment = "jjk-test:guardian_a")
    public void everyBodyIsItsOwnCurseAndKeepsItsLook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.assertTrue(Guardians.BODIES.size() == 4, "four bodies");
        Set<Class<?>> classes = new HashSet<>();
        for (Guardians.Body b : Guardians.BODIES) {
            h.assertTrue(Guardians.present(b.id()), b.id() + ": its own entity, not the stand-in");
            CommonCurseEntity c = Guardians.type(b.id()).create(level, EntitySpawnReason.EVENT);
            h.assertTrue(c instanceof GuardianBody, b.id() + ": takes a look and reports its defeat");
            h.assertTrue(c.curseKind().equals(b.id()), b.id() + ": draws and animates as itself (" + c.curseKind() + ")");
            h.assertTrue(b.standIn().create(level, EntitySpawnReason.EVENT).getClass().isInstance(c), b.id() + ": fights with its stand-in's moves");
            h.assertTrue(c.getBbHeight() > 2.4f, b.id() + ": stands taller than a player (" + c.getBbHeight() + ")");
            classes.add(c.getClass());
            GuardianBody g = (GuardianBody) c;
            for (int v = 0; v < b.variants(); v++) {
                g.setGuardianVariant(v);
                h.assertTrue(g.guardianVariant() == v, b.id() + ": look " + v);
            }
            g.setGuardianVariant(b.variants() + 1);
            h.assertTrue(g.guardianVariant() == 1, b.id() + ": a look out of range wraps round");
            g.setGuardianVariant(2);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            c.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            CommonCurseEntity again = Guardians.type(b.id()).create(level, EntitySpawnReason.EVENT);
            again.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            h.assertTrue(((GuardianBody) again).guardianVariant() == 2, b.id() + ": its look survives a save");
        }
        h.assertTrue(classes.size() == 4, "four distinct bodies");
        h.succeed();
    }

    @GameTest(environment = "jjk-test:guardian_b")
    public void everyLookIsDrawnAndEveryMoveIsAnimated(GameTestHelper h) {
        for (Guardians.Body b : Guardians.BODIES) {
            String id = b.id();
            // A model and a texture for every look, each with the whole skeleton; the clips key only bones the looks have
            // (a look without one of them, a jaw or a strand of hair, just leaves it out).
            Set<String> shared = null, any = new HashSet<>();
            for (int v = 0; v < b.variants(); v++) {
                Path model = asset("models/bb/" + id + "_" + v + ".bbmodel");
                h.assertTrue(model != null, id + ": a model for look " + v);
                h.assertTrue(asset("textures/entity/" + id + "_" + v + ".png") != null, id + ": a texture for look " + v);
                Set<String> names = new HashSet<>();
                for (JsonElement n : json(model).getAsJsonArray("outliner")) bones(n, names);
                h.assertTrue(json(model).getAsJsonArray("elements").size() > 500, id + "_" + v + ": the full model, not a placeholder");
                if (shared == null) shared = new HashSet<>(names);
                else shared.retainAll(names);
                any.addAll(names);
            }
            for (String bone : SKELETON) h.assertTrue(shared.contains(bone), id + ": every look has its " + bone);
            for (String clip : concat(SHARED, MOVES.get(id))) {
                Path p = asset("animations/" + id + "/" + id + "_" + clip + ".json");
                h.assertTrue(p != null, id + ": a clip for " + clip);
                JsonObject c = json(p);
                h.assertTrue(c.get("rig").getAsString().equals(id), id + "_" + clip + ": on its own rig");
                for (JsonElement k : c.getAsJsonArray("keys")) {
                    for (String bone : k.getAsJsonObject().getAsJsonObject("bones").keySet()) {
                        h.assertTrue(any.contains(bone), id + "_" + clip + ": keys a bone of its models (" + bone + ")");
                    }
                }
            }
        }
        h.succeed();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:guardian_c")
    public void aContractRaisesTheRealBodyInItsLook(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        Clearance.reset(p);
        Clearance.set(p, 1);
        InvestigationState st = InvestigationState.get(server);
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-60, 1, -60)));
        Incident in = Investigations.create(level, v, st, IncidentTemplate.get("guardian_cliff"), new Sites.Site(h.absolutePos(new BlockPos(240, 1, 0)), 1, 0),
                level.getGameTime(), RandomSource.create(41));
        Investigations.challengeForTest(in, p);
        CursedRealms.enter(p, in, st);
        h.runAfterDelay(30, () -> {
            List<Entity> gs = in.curses().stream().map(id -> Investigations.curseEntity(server, id)).filter(e -> e != null).toList();
            h.assertTrue(gs.size() == 1, "one guardian (" + gs.size() + ")");
            Entity g = gs.get(0);
            h.assertTrue(g.getType() == Guardians.type(in.guardian()) && Guardians.present(in.guardian()), "the contract's own body, " + in.guardian());
            h.assertTrue(((GuardianBody) g).guardianVariant() == in.variant(), "in the look the contract chose (" + in.variant() + ")");
            h.assertTrue(((CommonCurseEntity) g).curseGrade() == CurseGrade.GRADE_1, "a Grade 1 curse");
            var arena = CursedRealms.arenaOf(st, in.id);
            if (arena != null) CursedRealms.close(server, st, arena, true);
            p.discard();
            h.succeed();
        });
    }
}
