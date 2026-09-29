package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.AnimDebug;
import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.client.anim.AnimPlayer;
import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.Easing;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.anim.rig.Bone;
import dev.rick.jjk.client.anim.rig.Rig;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The animation framework's own checks: the data loads cleanly and covers every clip gameplay asks for, the skeleton
 * reproduces vanilla at rest and moves the way its conventions say, and playback follows its blending, priority,
 * interrupt and loop rules. Ends with a debugger screenshot (skeleton overlay and readout) of a paused clip.
 */
public class AnimFrameworkClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            data();
            rig(mc);
            playback();
        });
        debuggerShot(ctx);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    // ---------------------------------------------------------------- data

    /** Every clip name the gameplay code passes to Anim.play exists, and nothing failed to load. */
    private static void data() {
        check(AnimLibrary.errors().isEmpty(), "animation load errors: " + AnimLibrary.errors());
        Set<String> used = new TreeSet<>();
        Pattern call = Pattern.compile("Anim\\.play\\([^;]*?\"([a-z0-9_]*[a-z0-9])\"(?!\\s*\\+)");
        Pattern held = Pattern.compile("\"([a-z0-9_]+)\"");
        Path src = Path.of("../../../src/main/java");
        if (Files.isDirectory(src)) {
            try (Stream<Path> files = Files.walk(src)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(f);
                    Matcher m = call.matcher(text);
                    while (m.find()) used.add(m.group(1));
                    int h = text.indexOf("Set<String> HELD");
                    if (h >= 0) {
                        Matcher hm = held.matcher(text.substring(h, text.indexOf(';', h)));
                        while (hm.find()) used.add(hm.group(1));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            check(used.size() > 60, "found only " + used.size() + " clip names in the gameplay code");
        }
        List<String> missing = new ArrayList<>();
        for (String n : used) if (AnimLibrary.get(n) == null) missing.add(n);
        for (String n : List.of("reaction_hitstun", "reaction_hit_launched", "reaction_knockdown", "reaction_overload", "reaction_guard_broken", "reaction_pulled")) {
            if (AnimLibrary.get(n) == null) missing.add(n);
        }
        check(missing.isEmpty(), "clips missing for " + missing);
    }

    // ---------------------------------------------------------------- rig

    private static PlayerModel model(Minecraft mc) {
        ModelPart root = mc.getEntityModels().bakeLayer(ModelLayers.PLAYER);
        return new PlayerModel(root, false);
    }

    private static float[][] snapshot(PlayerModel m) {
        List<ModelPart> parts = m.root().getAllParts();
        float[][] out = new float[parts.size()][];
        for (int i = 0; i < parts.size(); i++) {
            ModelPart p = parts.get(i);
            out[i] = new float[]{p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale};
        }
        return out;
    }

    private static Vector3f at(Matrix4f[] world, Bone b) {
        return world[b.ordinal()].transformPosition(new Vector3f());
    }

    private static Matrix4f[] solve(PlayerModel m, PoseFrame f) {
        m.resetPose();
        Matrix4f[] world = new Matrix4f[Bone.COUNT];
        Rig.solve(Rig.parts(m), f, world);
        return world;
    }

    private static void rig(Minecraft mc) {
        PlayerModel m = model(mc);
        Rig.Parts parts = Rig.parts(m);
        check(parts.jointed, "the player model has no elbows/knees");
        check(m.rightArm.hasChild("jjk_lower") && m.rightArm.getChild("jjk_lower").hasChild("right_sleeve"), "the sleeve wasn't split onto the forearm");

        // At rest, and under vanilla's own swing and look, an empty pose changes nothing.
        m.resetPose();
        float[][] before = snapshot(m);
        solve(m, new PoseFrame());
        float[][] after = snapshot(m);
        for (int i = 0; i < before.length; i++) {
            for (int k = 0; k < 9; k++) check(Math.abs(before[i][k] - after[i][k]) < 1e-4f, "rest pose changed part " + i + " channel " + k);
        }
        m.resetPose();
        m.head.yRot = 0.4f;
        m.head.xRot = -0.3f;
        m.rightArm.xRot = -0.8f;
        m.leftLeg.xRot = 0.6f;
        before = snapshot(m);
        Matrix4f[] w = new Matrix4f[Bone.COUNT];
        Rig.solve(parts, new PoseFrame(), w);
        after = snapshot(m);
        for (int i = 0; i < before.length; i++) {
            for (int k = 0; k < 9; k++) check(Math.abs(before[i][k] - after[i][k]) < 1e-4f, "vanilla pose changed part " + i + " channel " + k);
        }

        Matrix4f[] rest = solve(m, new PoseFrame());
        Vector3f headRest = at(rest, Bone.HEAD), handRest = at(rest, Bone.RIGHT_HAND);
        // Chest pitched forward: the head goes forward (-z) and down (+y).
        Vector3f head = at(solve(m, new PoseFrame().rot(Bone.CHEST, 30, 0, 0)), Bone.HEAD);
        check(head.z < headRest.z - 2 && head.y > headRest.y + 0.5f, "chest x+ should bow forward: " + head);
        // Root moved forward 5 px: every part moves forward.
        solve(m, new PoseFrame().pos(Bone.ROOT, 0, 0, 5));
        check(Math.abs(m.body.z - (-5)) < 1e-3f && Math.abs(m.leftLeg.z - (-5)) < 1e-3f, "root forward should move the body to z -5: " + m.body.z);
        // Root up 3 px: the whole body rises (-y).
        solve(m, new PoseFrame().pos(Bone.ROOT, 0, 3, 0));
        check(Math.abs(m.head.y - (-3)) < 1e-3f, "root up should lift the head: " + m.head.y);
        // Root right 2 px: the body moves to the character's right (-x).
        solve(m, new PoseFrame().pos(Bone.ROOT, 2, 0, 0));
        check(Math.abs(m.body.x - (-2)) < 1e-3f, "root right should move to -x: " + m.body.x);
        // Hips turned right: the right shoulder swings back (+z).
        solve(m, new PoseFrame().rot(Bone.HIPS, 0, 30, 0));
        check(m.rightArm.z > 1.5f && m.leftArm.z < -1.5f, "hips y+ should turn right: " + m.rightArm.z);
        // Right arm out sideways: the hand goes to the right (-x) and up.
        Vector3f side = at(solve(m, new PoseFrame().rot(Bone.RIGHT_ARM, 0, 0, 90)), Bone.RIGHT_HAND);
        check(side.x < handRest.x - 6, "right arm z+ should raise it out to the side: " + side);
        // Arm forward (x -90) with the elbow bent a further 90: the hand ends up above the elbow, ahead of the body.
        Matrix4f[] bent = solve(m, new PoseFrame().rot(Bone.RIGHT_ARM, -90, 0, 0).rot(Bone.RIGHT_FOREARM, -90, 0, 0));
        Vector3f elbow = at(bent, Bone.RIGHT_FOREARM), wrist = at(bent, Bone.RIGHT_HAND);
        check(elbow.z < -3 && wrist.y < elbow.y - 3, "elbow bend should fold the forearm up: elbow " + elbow + " wrist " + wrist);
        // Knee bend (shin x+): the foot swings back.
        Matrix4f[] knee = solve(m, new PoseFrame().rot(Bone.LEFT_SHIN, 60, 0, 0));
        check(at(knee, Bone.LEFT_FOOT).z > at(knee, Bone.LEFT_SHIN).z + 2, "shin x+ should kick the foot back");
        // The torso bends at the waist: the belly stays with the hips.
        solve(m, new PoseFrame().rot(Bone.CHEST, 45, 0, 0));
        ModelPart belly = m.body.getChild("jjk_lower");
        Matrix4f bellyWorld = new Matrix4f().translate(m.body.x, m.body.y, m.body.z).rotateZYX(m.body.zRot, m.body.yRot, m.body.xRot)
                .translate(belly.x, belly.y, belly.z).rotateZYX(belly.zRot, belly.yRot, belly.xRot);
        Vector3f bellyAxis = bellyWorld.transformDirection(new Vector3f(0, 1, 0));
        check(Math.abs(bellyAxis.y - 1) < 1e-3f, "the abdomen should stay upright under a chest bow: " + bellyAxis);
    }

    // ---------------------------------------------------------------- playback

    private static void playback() {
        // Easing ends where it should; STEP holds, then jumps.
        for (Easing e : Easing.values()) check(Math.abs(e.apply(0)) < 1e-4f && Math.abs(e.apply(1) - 1) < 1e-4f, e + " endpoints");
        check(Easing.STEP.apply(0.99f) == 0, "STEP should hold");
        check(Easing.OVERSHOOT.apply(0.7f) > 1, "OVERSHOOT should overshoot");
        check(Easing.EASE_IN.apply(0.5f) < 0.5f && Easing.EASE_OUT.apply(0.5f) > 0.5f, "in/out shapes");

        // Frame-based keys at 30 fps, per-bone ease, and a pose reference being mirrored.
        Clip c = AnimLibrary.parse("t_frames", "test", """
                {"fps": 30, "blendIn": 100, "keys": [
                  {"f": 0, "bones": {"rightArm": [0, 0, 0], "head": [0, 0, 0]}},
                  {"f": 3, "ease": "LINEAR", "bones": {"rightArm": [-90, 20, 10], "head": {"rot": [10, 0, 0], "ease": "STEP"}}}
                ]}""");
        check(Math.abs(c.duration - 100) < 1e-3f, "3 frames at 30 fps is 100 ms: " + c.duration);
        float[] v = new float[3];
        c.track(Bone.RIGHT_ARM, Clip.ROT).sample(50, v);
        check(Math.abs(Math.toDegrees(v[0]) + 45) < 0.1, "linear halfway: " + Math.toDegrees(v[0]));
        c.track(Bone.HEAD, Clip.ROT).sample(99, v);
        check(v[0] == 0, "STEP head holds until its frame");
        Clip m = c.mirrored("t_frames_m");
        m.track(Bone.LEFT_ARM, Clip.ROT).sample(100, v);
        check(Math.abs(Math.toDegrees(v[0]) + 90) < 0.1 && Math.abs(Math.toDegrees(v[1]) + 20) < 0.1 && Math.abs(Math.toDegrees(v[2]) + 10) < 0.1,
                "mirror swaps sides and flips turn and roll");
        check(m.track(Bone.RIGHT_ARM, Clip.ROT) == null, "mirror leaves the right arm free");

        // Blend in: halfway through a 100 ms blend the clip shows at half weight.
        AnimPlayer p = new AnimPlayer();
        p.play(c, 1);
        p.advance(50);
        check(Math.abs(p.top().weight() - 0.5f) < 1e-3f, "blend weight at 50/100 ms: " + p.top().weight());

        // Loops wrap between loopStart and loopEnd; a looping clip never ends on its own.
        Clip loop = AnimLibrary.parse("t_loop", "test", """
                {"loop": true, "loopStart": 100, "loopEnd": 300, "blendIn": 0, "keys": [{"t": 0, "bones": {"head": [0,0,0]}}, {"t": 400, "bones": {"head": [40,0,0]}}]}""");
        check(Math.abs(loop.localTime(350) - 150) < 1e-3f, "loop wraps: " + loop.localTime(350));
        AnimPlayer lp = new AnimPlayer();
        lp.play(loop, 1);
        lp.advance(5000);
        check(!lp.idle() && !lp.top().stopping, "loops keep playing");
        lp.release();
        lp.advance(1000);
        check(lp.idle(), "released loop fades out");

        // Priority: an uninterruptible awakening can't be cut off by an M1, but a higher priority clip can.
        Clip awaken = AnimLibrary.get("awaken"), jab = AnimLibrary.get("light_1");
        AnimPlayer ap = new AnimPlayer();
        check(ap.play(awaken, 1), "awaken plays");
        ap.advance(100);
        check(!ap.play(jab, 1), "an M1 must not interrupt the awakening");
        check(ap.top().clip == awaken, "awakening still on top");
        // Interrupt window: interruptible only between 100 and 200 ms.
        Clip windowed = AnimLibrary.parse("t_window", "test", """
                {"priority": "SPECIAL", "interruptWindow": [100, 200], "duration": 1000, "keys": [{"t": 0, "bones": {"head": [0,0,0]}}]}""");
        AnimPlayer wp = new AnimPlayer();
        wp.play(windowed, 1);
        wp.advance(50);
        check(!wp.play(jab, 1), "outside the window the special holds");
        wp.advance(100);
        check(wp.play(jab, 1), "inside the window an attack cuts in");
        // Hard transition: blendIn 0 replaces the previous clip on the same frame.
        Clip hard = AnimLibrary.parse("t_hard", "test", """
                {"priority": "ATTACK", "blendIn": 0, "keys": [{"t": 0, "bones": {"head": [0,0,0]}}]}""");
        wp.play(hard, 1);
        check(wp.ordered().size() == 1 && wp.top().clip == hard && wp.top().weight() == 1, "blendIn 0 is a hard cut");
    }

    // ---------------------------------------------------------------- debugger

    private static void debuggerShot(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 12");
            ctx.waitTicks(20);
            server.runCommand("execute as @a at @s run summon jjk:training_dummy ~ ~ ~3.2 {NoAI:1b,Rotation:[-60f,0f]}");
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                Entity d = null;
                for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == ModEntities.TRAINING_DUMMY) d = e;
                check(d != null, "no dummy");
                AnimDebug.target = d.getId();
                AnimDebug.skeleton = true;
                AnimDebug.axes = true;
                AnimDebug.play("rapid_heavy");
            });
            ctx.waitTicks(6);
            ctx.runOnClient(mc -> AnimDebug.togglePause());
            ctx.waitTicks(2);
            ctx.takeScreenshot("anim_debugger");
            ctx.runOnClient(mc -> {
                AnimDebug.enabled = false;
                AnimDebug.paused = false;
                AnimDebug.skeleton = false;
                AnimDebug.axes = false;
                AnimDebug.target = Integer.MIN_VALUE;
                ClientAnimations.clear();
            });
        }
    }
}
