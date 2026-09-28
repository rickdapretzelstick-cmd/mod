package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.clash.ClashClient;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.CombatStatus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Films a presentation of the whole kit with real input: melee, guard and dash, the base techniques, Awakening, the
 * MAX techniques and Hollow Purple, battle damage restoring itself, Infinite Void, and a domain counter into a clash.
 * Every screenshot is logged in {@code frames.txt} with the game tick it shows and the segment it belongs to, so a
 * captioned real-speed video can be rebuilt from them. Not registered by default: point the
 * {@code fabric-client-gametest} entrypoint at this class to record.
 */
public class PresentationClientTest implements FabricClientGameTest {
    private int frame;
    private final Set<Integer> pressed = new HashSet<>();
    private final StringBuilder log = new StringBuilder();
    private String section = "";
    private ClientGameTestContext ctx;
    private TestServerContext server;
    private TestInput in;
    /** Middle of the arena, where every segment starts. */
    private double[] center;
    /** Facing for most segments: between two of the arena's pillars. */
    private static final float YAW = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            server = sp.getServer();
            in = ctx.getInput();
            cmd("time set noon");
            cmd("gamerule advance_time false");
            cmd("weather clear");
            ctx.waitTicks(40);
            cmd("execute as @a at @s run jjk arena");
            cmd("execute as @a run jjk nocooldown true");
            center = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                return new double[] {p.getX(), p.getY(), p.getZ()};
            });
            clearDummies();
            resetPlayer();
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            KeyMapping z = key("key.jjk.skill_1"), x = key("key.jjk.skill_2"), c = key("key.jjk.skill_3"), v = key("key.jjk.skill_4");
            KeyMapping g = key("key.jjk.ultimate"), guard = key("key.jjk.guard"), dash = key("key.jjk.dash");

            // --- Melee ---
            section("Melee: light chain");
            ShowcaseCamera.set(62, 4.2f, 0.9f, 1.3f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.6");
            ctx.waitTicks(5);
            film(8);
            for (int i = 0; i < 4; i++) {
                in.pressKey(o -> o.keyAttack);
                film(i == 3 ? 20 : 7);
            }
            section("Melee: charged heavy (hold attack)");
            film(10);
            in.holdKey(o -> o.keyAttack);
            film(18);
            in.releaseKey(o -> o.keyAttack);
            film(26);
            section("Melee: uppercut (hold jump on the 4th hit)");
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^2.6");
            ctx.waitTicks(20);
            for (int i = 0; i < 3; i++) {
                in.pressKey(o -> o.keyAttack);
                film(7);
            }
            in.holdKey(o -> o.keyJump);
            in.pressKey(o -> o.keyAttack);
            film(24);
            in.releaseKey(o -> o.keyJump);
            film(14);
            section("Melee: sprint lunge");
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^8");
            ctx.waitTicks(20);
            in.holdKey(o -> o.keySprint);
            in.holdKey(o -> o.keyUp);
            film(8);
            in.pressKey(o -> o.keyAttack);
            film(6);
            in.releaseKey(o -> o.keyUp);
            in.releaseKey(o -> o.keySprint);
            film(24);

            // --- Defence and movement ---
            section("Guard (hold R): blocks a fighting dummy");
            ShowcaseCamera.set(75, 5f, 1f, 2f);
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s run jjk dummy fight 1");
            ctx.waitTicks(10);
            in.holdKey(guard);
            film(60);
            in.releaseKey(guard);
            section("Dash (Left Alt), any direction");
            ShowcaseCamera.set(90, 8f, 2f, 0f);
            film(4);
            ShowcaseCamera.frozen = true;
            clearDummies();
            film(6);
            in.pressKey(dash);
            film(14);
            in.holdKey(o -> o.keyLeft);
            in.pressKey(dash);
            film(14);
            in.releaseKey(o -> o.keyLeft);
            in.holdKey(o -> o.keyRight);
            in.pressKey(dash);
            film(14);
            in.releaseKey(o -> o.keyRight);
            in.holdKey(o -> o.keyDown);
            in.pressKey(dash);
            film(18);
            in.releaseKey(o -> o.keyDown);

            // --- Base techniques ---
            section("Infinity (C): attacks stop before they reach Gojo");
            ShowcaseCamera.set(75, 5f, 1f, 2f);
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s run jjk dummy fight 1");
            ctx.waitTicks(6);
            in.pressKey(c);
            film(80);
            in.pressKey(c);
            clearDummies();

            section("Lapse Blue (hold Z): pulls everything in");
            ShowcaseCamera.set(50, 8f, 2.5f, 4f);
            resetPlayer();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^2 ^ ^7");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-2 ^ ^7");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^9");
            ctx.waitTicks(10);
            film(4);
            in.holdKey(z);
            film(26);
            in.releaseKey(z);
            film(36);

            section("Reversal Red (hold X to charge): repels and blasts");
            ShowcaseCamera.set(55, 7f, 2f, 3f);
            resetPlayer();
            clearDummies();
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^6");
            ctx.waitTicks(10);
            film(4);
            in.holdKey(x);
            film(22);
            in.releaseKey(x);
            film(40);

            section("Teleport (V): appears behind the target, three charges");
            resetPlayer();
            clearDummies();
            // Fill in Red's crater (off camera) and face open ground.
            cmd("jjk restore now");
            cmd("execute as @a at @s run tp @s ~ ~ ~ 0 10");
            ctx.waitTicks(2);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^7");
            ShowcaseCamera.set(90, 9f, 2.5f, 3.5f);
            ctx.waitTicks(6);
            film(6);
            ShowcaseCamera.frozen = true;
            for (int i = 0; i < 3; i++) {
                in.pressKey(v);
                film(6);
                // Face the target again and hit it.
                cmd("execute as @a at @s facing entity @e[type=jjk:training_dummy,limit=1,sort=nearest] feet run tp @s ~ ~ ~ ~ 10");
                in.pressKey(o -> o.keyAttack);
                film(10);
            }
            film(10);

            // --- Awakening ---
            section("Awakening (G on a full meter): the blindfold comes off");
            resetPlayer();
            clearDummies();
            cmd("execute as @a run jjk awakening 100");
            ShowcaseCamera.set(165, 3.6f, 0.2f, 0f);
            ctx.waitTicks(10);
            film(8);
            in.pressKey(g);
            film(20);
            ShowcaseCamera.set(150, 7f, 1.5f, 0f);
            film(36);

            section("Lapse Blue: MAX");
            ShowcaseCamera.set(45, 11f, 3f, 5f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^8");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^3 ^ ^10");
            ctx.waitTicks(8);
            film(4);
            in.pressKey(z);
            film(70);

            // A small house in the line of fire, to show battle damage coming back.
            section("Reversal Red: MAX");
            ShowcaseCamera.set(45, 10f, 3f, 5f);
            clearDummies();
            resetPlayer();
            cmd("execute as @a at @s run jjk awakening 100");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-2 ~ ~-2 ~2 ~3 ~2 minecraft:bricks hollow");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-2 ~4 ~-2 ~2 ~4 ~2 minecraft:oak_planks");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-2 ~1 ~ ~2 ~2 ~ minecraft:glass_pane");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:air");
            cmd("execute as @a at @s rotated ~ 0 positioned ^ ^ ^11 run setblock ~ ~ ~ minecraft:chest");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^7");
            ctx.waitTicks(10);
            film(10);
            in.holdKey(x);
            film(38);
            in.releaseKey(x);
            film(50);

            section("Battle damage: every block comes back 3 minutes after it broke");
            clearDummies();
            film(20);
            section("3 minutes later (fast-forwarded)");
            cmd("jjk restore now");
            film(40);

            section("Hollow Purple (hold C, awakened only)");
            resetPlayer();
            ShowcaseCamera.set(35, 9f, 2.5f, 5f);
            cmd("execute as @a run jjk awakening 100");
            cmd("execute as @a at @s run tp @s ~ ~ ~ -120 5");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^12");
            ctx.waitTicks(10);
            film(4);
            in.holdKey(c);
            film(50);
            in.releaseKey(c);
            film(50);
            cmd("jjk restore now");

            // --- Domain ---
            section("Domain Expansion: Infinite Void");
            clearDummies();
            resetPlayer();
            cmd("execute as @a run jjk awakening 100");
            cmd("execute as @a at @s run tp @s ~ ~ ~ 60 8");
            ShowcaseCamera.set(40, 8f, 2.5f, 3f);
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^2 ^ ^5");
            cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-2 ^ ^5");
            ctx.waitTicks(10);
            film(6);
            in.pressKey(g);
            film(140);
            section("Sure-hit: everyone inside is overloaded with infinite information");
            ShowcaseCamera.off();
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            film(40);
            cmd("execute as @a at @s run tp @s ~ ~ ~ ~ -35");
            film(40);
            cmd("execute as @a at @s run tp @s ~ ~ ~ ~ 8");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ShowcaseCamera.set(40, 8f, 2.5f, 3f);
            section("The domain ends: the world is restored exactly");
            cmd("execute as @a run jjk domain cancel");
            film(60);

            // --- Counter and clash ---
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.combat.Statuses.remove(p, CombatStatus.BURNOUT);
                var cst = Casters.get(p);
                cst.endAwakening("presentation");
                cst.setAwakening(cst.maxAwakening());
            });
            clearDummies();
            resetPlayer();
            // Away from the house, so the rival stands in the open.
            cmd("execute as @a at @s run tp @s ~ ~ ~ 120 8");
            ShowcaseCamera.set(35, 9f, 2.5f, 6f);
            ctx.waitTicks(20);
            section("A rival opens their domain...");
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var level = p.level();
                var rival = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                rival.setPos(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
                rival.setYRot(p.getYRot() + 180);
                level.addFreshEntity(rival);
                dev.rick.jjk.core.character.CharacterService.assign(rival, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.gojo.GojoCharacter.ID));
                dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(rival, 0.5f);
                var rc = Casters.get(rival);
                rc.enterAwakening();
                rc.setAwakening(rc.maxAwakening());
                rc.setNoCost(true);
            });
            film(12);
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) {
                    if (e instanceof dev.rick.jjk.entity.TrainingDummy d && Casters.getOrNull(d) != null && Casters.get(d).isAwakened()) {
                        Casters.get(d).input(dev.rick.jjk.core.ability.AbilitySlot.ULTIMATE, true, 0, 0, null);
                    }
                }
            });
            film(8);
            section("...Gojo counters with the Awakening key: instant Awakening and his own domain");
            in.pressKey(g);
            film(70);
            section("Domain clash: hit the arrows on the beat to push your domain through");
            for (int i = 0; i < 900 && ctx.computeOnClient(mc -> ClashClient.view() != null); i++) film(1);
            section("The winner's domain swallows the loser's");
            film(60);
            section("Every block the domains replaced comes back exactly");
            cmd("execute as @a run jjk domain cancel all");
            film(50);
            section("");
            film(4);
            ShowcaseCamera.off();
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("screenshots", "frames.txt"), log.toString());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void cmd(String command) {
        server.runCommand(command);
    }

    private void section(String name) {
        section = name;
    }

    /** Puts the player back in the middle of the arena facing the same way, and clears any statuses from the last segment. */
    private void resetPlayer() {
        if (center != null) cmd(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f 10", center[0], center[1], center[2], YAW));
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            dev.rick.jjk.core.combat.Statuses.remove(p, CombatStatus.BURNOUT);
            p.setHealth(p.getMaxHealth());
        });
        ctx.waitTicks(2);
    }

    private void clearDummies() {
        cmd("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
        cmd("execute as @a at @s run kill @e[type=item,distance=..60]");
    }

    private KeyMapping key(String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst().orElseThrow());
    }

    /** Films {@code n} game ticks: a screenshot every client frame-step, pressing any clash prompts as they come due. */
    private void film(int n) {
        int[] keys = {InputConstants.KEY_LEFT, InputConstants.KEY_DOWN, InputConstants.KEY_UP, InputConstants.KEY_RIGHT};
        long target = ctx.computeOnClient(mc -> mc.level.getGameTime()) + n;
        double now = 0;
        while (now < target) {
            double[] info = ctx.computeOnClient(mc -> {
                var cv = ClashClient.view();
                java.util.List<Double> out = new java.util.ArrayList<>();
                out.add(mc.level.getGameTime() + (double) mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
                if (cv != null) {
                    double clock = mc.level.getGameTime() - cv.startTick;
                    for (int k = 0; k < cv.times.length; k++) {
                        if (Math.abs(cv.times[k] - clock) <= 0.5 && pressed.add(cv.round * 1000 + k)) out.add((double) cv.lanes[k]);
                    }
                }
                return out.stream().mapToDouble(Double::doubleValue).toArray();
            });
            now = info[0];
            for (int i = 1; i < info.length; i++) in.pressKey(keys[(int) info[i]]);
            String name = String.format("p%05d", frame++);
            ctx.takeScreenshot(name);
            log.append(name).append(' ').append(info[0]).append(' ').append(section).append('\n');
        }
    }
}
