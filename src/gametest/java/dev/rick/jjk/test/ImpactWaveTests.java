package dev.rick.jjk.test;

import dev.rick.jjk.core.fx.ImpactWave;
import dev.rick.jjk.core.fx.ImpactWaves;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The impact-wave library: every profile a move names exists, every basic attack resolves, and a slam shocks the floor once. */
public class ImpactWaveTests {
    @GameTest(maxTicks = 20, environment = "jjk-test:blast")
    public void everyNamedWaveExists(GameTestHelper h) {
        List<String> missing = new ArrayList<>();
        // Profile names are dotted (theme.move); MeleeSystem's set.wave("light") passes a move key, resolved by the set.
        Pattern call = Pattern.compile("\\.wave\\(\"([a-z0-9_]+\\.[a-z0-9_.]+)\"\\)");
        Path src = Path.of("../../../src/main/java");
        if (Files.isDirectory(src)) {
            try (Stream<Path> files = Files.walk(src)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher m = call.matcher(Files.readString(f));
                    while (m.find()) if (!ImpactWaves.exists(m.group(1))) missing.add(m.group(1));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!missing.isEmpty()) h.fail("waves named but not registered: " + missing);
        for (String theme : new String[] {"", "gojo", "hakari", "yuji", "sukuna", "yuta", "steel", "ryu"}) {
            for (String move : new String[] {"light", "air", "finisher", "uppercut", "downslam", "sprint", "stomp", "heavy", "heavy_full"}) {
                if (!ImpactWaves.exists(ImpactWaves.melee(theme, move))) h.fail("no wave for " + theme + "." + move);
            }
        }
        if (ImpactWave.all().size() < 100) h.fail("only " + ImpactWave.all().size() + " profiles");
        h.succeed();
    }

    @GameTest(maxTicks = 20, environment = "jjk-test:blast")
    public void aSlamShocksTheFloorOncePerTick(GameTestHelper h) {
        ImpactWave.reset();
        Vec3 at = h.absoluteVec(new Vec3(2, 1, 2));
        if (!ImpactWave.play(h.getLevel(), "rika.smash", at, new Vec3(0, -1, 0), 1f)) h.fail("the wave did not play");
        if (ImpactWave.play(h.getLevel(), "no.such.wave", at, new Vec3(0, -1, 0), 1f)) h.fail("an unknown wave played");
        if (ImpactWave.fired != 1) h.fail("fired " + ImpactWave.fired);
        h.succeed();
    }
}
