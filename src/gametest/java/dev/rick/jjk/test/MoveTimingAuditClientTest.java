package dev.rick.jjk.test;

import dev.rick.jjk.client.debug.AnimTrace;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.character.JJKCharacter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Casts every move of every character (tapped, at a training dummy) and traces it ({@link AnimTrace}): how long the move
 * runs on the server, which clips it plays and how long they show, and anything that looks wrong (pops, clips outliving
 * the move, the move outliving its clips, the body facing away from the aim, stuck poses). Writes one trace per move and
 * a summary, {@code anim-trace/AUDIT.txt}. Opt-in.
 */
public class MoveTimingAuditClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a run jjk nocooldown true");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            ctx.waitTicks(30);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            int playerId = ctx.computeOnClient(mc -> mc.player.getId());
            String home = server.computeOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                return String.format(Locale.ROOT, "%.2f %.2f %.2f", p.getX(), p.getY(), p.getZ());
            });

            StringBuilder audit = new StringBuilder("# Move timing audit\n\n");
            audit.append("Each move tapped once at a dummy 3 blocks ahead. 'overrun' is how long the move's one-shot clips would\n")
                    .append("run past the move's end on the server (they now fade at the end); 'shown' is how long they actually\n")
                    .append("showed past it; 'idle' is how long the move kept running with no clip and no held pose.\n\n");
            audit.append(String.format(Locale.ROOT, "%-7s %-9s %-26s %6s %8s %7s %6s  %s%n", "char", "slot", "ability", "ticks", "overrun", "shown", "idle", "anomalies / clips"));
            List<String> problems = new ArrayList<>();
            String only = System.getenv("JJK_AUDIT_CHARS");
            List<String> chars = only == null || only.isBlank() ? List.of("gojo", "hakari", "yuji", "yuta", "ryu") : List.of(only.split(","));
            for (String charId : chars) {
                server.runOnServer(srv -> CharacterService.assign(srv.getPlayerList().getPlayers().getFirst(), Characters.get(charId)));
                ctx.waitTicks(10);
                for (boolean awakened : new boolean[] {false, true}) {
                    for (AbilitySlot slot : AbilitySlot.values()) {
                        Ability ability = server.computeOnServer(srv -> {
                            JJKCharacter c = Characters.get(charId);
                            return c == null ? null : c.ability(slot, awakened);
                        });
                        if (ability == null || ability.kind() == Ability.Kind.TOGGLE) continue;
                        // In the awakened kit, only what differs from the base kit.
                        if (awakened && ability == server.computeOnServer(srv -> Characters.get(charId).ability(slot, false))) continue;
                        // A charge is checked tapped and held.
                        int[] holds = ability.kind() == Ability.Kind.HOLD ? new int[] {1, 30} : new int[] {1};
                        for (int hold : holds) {
                            String label = (awakened ? "awk_" : "") + slot.name().toLowerCase(Locale.ROOT) + (hold > 1 ? "_held" : "");
                            auditMove(ctx, server, home, playerId, charId, slot, ability.id, awakened, hold, label, audit, problems);
                        }
                    }
                }
            }
            audit.append("\n## Problems (").append(problems.size()).append(")\n");
            for (String p : problems) audit.append(p).append('\n');
            try {
                Path dir = Path.of(System.getProperty("jjk.traceDir", "anim-trace"));
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("AUDIT.txt"), audit.toString());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void auditMove(ClientGameTestContext ctx, TestServerContext server, String home, int playerId, String charId, AbilitySlot slot,
                                  String abilityId, boolean awakened, int hold, String label, StringBuilder audit, List<String> problems) {
        reset(ctx, server, home);
        if (awakened) {
            server.runCommand("execute as @a run jjk awakening 100");
            server.runOnServer(srv -> {
                AbilityCaster c = Casters.get(srv.getPlayerList().getPlayers().getFirst());
                if (!c.isAwakened()) c.enterAwakening();
            });
            ctx.waitTicks(40); // let the transformation play out
        }
        AnimTrace.start(charId + "_" + label + "_" + abilityId, playerId);
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
            AbilityCaster c = Casters.get(p);
            Entity dummy = p.level().getEntities(p, new AABB(p.blockPosition()).inflate(8), e -> e.getType() == dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY)
                    .stream().findFirst().orElse(null);
            c.input(slot, true, 0, 0, dummy);
        });
        ctx.waitTicks(hold);
        server.runOnServer(srv -> Casters.get(srv.getPlayerList().getPlayers().getFirst()).input(slot, false, 0, 0, null));
        // Until the move has been over for a second (a long move gets 15 s at most).
        int idle = 0;
        for (int t = 0; t < 300 && idle < 20; t++) {
            ctx.waitTicks(1);
            boolean casting = server.computeOnServer(srv -> Casters.get(srv.getPlayerList().getPlayers().getFirst()).isCasting());
            idle = casting ? 0 : idle + 1;
        }
        ctx.waitTicks(15);
        AnimTrace.Report r = AnimTrace.stop();
        AnimTrace.CastSummary cs = r.casts().stream().filter(c -> c.ability().equals(abilityId)).findFirst().orElse(null);
        StringBuilder notes = new StringBuilder();
        for (AnimTrace.Anomaly an : r.anomalies()) notes.append(an.kind()).append(": ").append(an.detail()).append("; ");
        String slotLabel = label.length() > 9 ? label.substring(0, 9) : label;
        if (cs == null) {
            audit.append(String.format(Locale.ROOT, "%-7s %-9s %-26s %6s %8s %7s %6s  %s%n", charId, slotLabel, abilityId, "-", "-", "-", "-",
                    "no cast (instant) " + notes));
        } else {
            audit.append(String.format(Locale.ROOT, "%-7s %-9s %-26s %6d %+7.0fms %+6.0fms %5.0fms  %s%s%n", charId, slotLabel, abilityId, cs.ticks(),
                    cs.overrunMs(), (cs.visualEndTick() - cs.end()) * 50, cs.idleGapMs(), notes, cs.clips()));
        }
        if (!r.anomalies().isEmpty()) problems.add(charId + "/" + label + "/" + abilityId + ": " + notes);
    }

    /** A clean slate between moves: no cast, no domain, full energy, the arena restored, one dummy 3 blocks ahead. */
    private static void reset(ClientGameTestContext ctx, TestServerContext server, String home) {
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
            Casters.get(p).interrupt("removed");
        });
        server.runCommand("jjk domain cancel all");
        server.runCommand("jjk restore now");
        server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..80]");
        server.runCommand("execute as @a run jjk reset");
        server.runCommand("execute as @a run tp @s " + home + " 0 0");
        ctx.waitTicks(15);
        server.runCommand("execute as @a at @s rotated 0 0 run summon jjk:training_dummy ^ ^ ^3");
        ctx.waitTicks(10);
    }
}
