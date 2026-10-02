package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.clash.ClashClient;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.domain.DomainAbility;
import dev.rick.jjk.core.domain.DomainManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * The domain cinematic. Four domains shown on their own (Infinite Void, Malevolent Shrine, Idle Death Gamble, Authentic
 * Mutual Love), two domain clashes fought out with the rhythm lanes hidden (Gojo against Sukuna, Yuta against Hakari), and
 * a finale: Yuta and Rika's True Love Beam fired straight into the lens.
 *
 * <p>Everything is filmed frame by frame with the game frozen (see {@link #film}): each tick is drawn at several sub-tick
 * moments, so {@code tools/make_domain_cinematic.py} can rebuild real speed at 60 fps from {@code frames.txt}, or slow a
 * moment right down. Each section name carries its own caption and speed: {@code NAME;top line;sub line;r,g,b;speed}.
 * Not registered by default: point the {@code fabric-client-gametest} entrypoint at this class to record.
 */
public class DomainCinematicClientTest extends PresentationClientTest {
    /** Sub-tick moments drawn per game tick: 3 per tick is 60 fps at real speed, 12 is a quarter-speed slow motion at 60 fps. */
    private int sub = 3;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            server = sp.getServer();
            in = ctx.getInput();
            in.resizeWindow(1280, 720);
            ctx.runOnClient(mc -> mc.options.fov().set(80));
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
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            hud(false);

            // build/cinematic.txt (optional) lists the scenes to film: show0-show3, clash0, clash1, finale.
            java.nio.file.Path list = java.nio.file.Path.of("../../cinematic.txt");
            java.util.Set<String> only = new java.util.HashSet<>();
            if (java.nio.file.Files.exists(list)) {
                try {
                    for (String w : java.nio.file.Files.readString(list).trim().split("\\s+")) only.add(w);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // --- Each domain on its own ---
            if (on(only, "show0")) showcase("gojo", "INFINITE VOID", "140,200,255", 0);
            if (on(only, "show1")) showcase("yuji", "MALEVOLENT SHRINE", "255,90,80", 1);
            if (on(only, "show2")) showcase("hakari", "IDLE DEATH GAMBLE", "120,255,200", 2);
            if (on(only, "show3")) showcase("yuta", "AUTHENTIC MUTUAL LOVE", "247,107,255", 3);

            // --- Domain clashes: no arrows on screen ---
            if (on(only, "clash0")) clash("gojo", "yuji", true, "GOJO VS SUKUNA", "255,225,140");
            if (on(only, "clash1")) clash("yuta", "hakari", false, "YUTA VS HAKARI", "255,160,255");

            // --- Finale ---
            if (on(only, "finale")) finale();
            save();
        }
    }

    private static boolean on(java.util.Set<String> only, String scene) {
        return only.isEmpty() || only.contains(scene);
    }

    // --- Staging ---

    /** Clears the last scene (its domain and dummies), makes the player {@code id} and puts them back in the middle. */
    private void stage(String id) {
        cmd("tick unfreeze");
        ctx.waitTicks(3);
        cmd("execute as @a run jjk domain cancel all");
        ctx.waitTicks(40);
        clearDummies();
        cmd("execute as @a run jjk restore now");
        cmd("jjk character " + id + " @a");
        ctx.waitTicks(10);
        resetPlayer();
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            var c = Casters.get(p);
            c.setNoCost(true);
            c.setAwakening(c.maxAwakening());
        });
        ctx.waitTicks(10);
    }

    private void freeze() {
        cmd("tick freeze");
        ctx.waitTicks(2);
    }

    /** A training dummy {@code ahead} blocks in front of the player, as {@code character} if given (it fights as a bot). */
    private void rival(String character, double ahead, boolean awaken, float skill) {
        cmd("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^" + ahead);
        ctx.waitTicks(4);
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            for (var e : s.overworld().getAllEntities()) {
                if (!(e instanceof dev.rick.jjk.entity.TrainingDummy d) || d.position().distanceTo(p.position()) > ahead + 3) continue;
                d.setNoAi(true);
                d.setYRot(p.getYRot() + 180);
                d.setYHeadRot(p.getYRot() + 180);
                d.setYBodyRot(p.getYRot() + 180);
                if (character == null) continue;
                dev.rick.jjk.core.character.CharacterService.assign(d, dev.rick.jjk.core.character.Characters.get(character));
                dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(d, skill);
                var rc = Casters.get(d);
                rc.setNoCost(true);
                if (awaken) rc.enterAwakening();
                rc.setAwakening(rc.maxAwakening());
            }
        });
    }

    /** Opens a domain: the player's, or the bot dummy whose character is {@link #characterOf}. */
    private void openDomain(boolean player) {
        server.runOnServer(s -> {
            net.minecraft.world.entity.LivingEntity who = s.getPlayerList().getPlayers().getFirst();
            if (!player) {
                who = null;
                for (var e : s.overworld().getAllEntities()) {
                    if (e instanceof dev.rick.jjk.entity.TrainingDummy d && Casters.getOrNull(d) != null && Casters.get(d).character() != null
                            && Casters.get(d).character().id.equals(characterOf)) who = d;
                }
            }
            if (who == null) {
                System.out.println("[cinematic] no one to open a domain");
                return;
            }
            var c = Casters.get(who);
            for (AbilitySlot slot : AbilitySlot.values()) {
                if (c.character().ability(slot, c.isAwakened()) instanceof DomainAbility) {
                    if (!c.input(slot, true, 0, 0, null)) System.out.println("[cinematic] domain refused: " + c.lastRefusal);
                    return;
                }
            }
            System.out.println("[cinematic] no domain slot for " + c.character().id);
        });
    }

    private String characterOf = "";

    /** 0 nothing, 1 forming or opening, 2 sealed and live. */
    private int domainState() {
        return server.computeOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            var d = DomainManager.ownedBy(p);
            return d == null ? 0 : d.isLive() ? 2 : 1;
        });
    }

    // --- Scenes ---

    /** One domain on its own: the caster's hero shot, the opening, the structure forming round a pair of dummies, then the inside. */
    private void showcase(String id, String name, String rgb, int n) {
        stage(id);
        rival(null, 5, false, 0);
        rival(null, 8, false, 0);
        if (!id.equals("hakari")) server.runOnServer(s -> Casters.get(s.getPlayerList().getPlayers().getFirst()).enterAwakening());
        ctx.waitTicks(4);
        freeze();
        section("SHOW" + n + ";DOMAIN EXPANSION;" + name + ";" + rgb + ";1");
        ShowcaseCamera.set(160, 4.5f, 1.2f, 0.3f);
        film(8);
        characterOf = "";
        openDomain(true);
        int step = 0;
        while (domainState() != 2 && step < 300) {
            float k = Mth.clamp(step / 90f, 0, 1);
            ShowcaseCamera.set(160 - 120 * k, 4.5f + 7.5f * k, 1.2f + 1.8f * k, 0.3f + 3f * k);
            film(1);
            step++;
        }
        for (int i = 0; i < 70; i++) {
            ShowcaseCamera.set(40 - i * 2.2f, 12f - i * 0.05f, 3f + 0.5f * Mth.sin(i * 0.08f), 3.3f);
            film(1);
        }
        save();
    }

    /**
     * A domain clash: the rival opens theirs, the player answers with the Awakening key, and the two fight it out on the
     * beat (the notes are played for the player, with the HUD, and so the lanes, hidden).
     */
    private void clash(String me, String rivalId, boolean rivalAwakens, String title, String rgb) {
        stage(me);
        rival(rivalId, 12, rivalAwakens, 0.8f);
        characterOf = rivalId;
        ctx.waitTicks(8);
        freeze();
        section("CLASH_A" + me + ";;;;1");
        ShowcaseCamera.set(150, 6f, 1.4f, 0f);
        film(10);
        ShowcaseCamera.set(25, 7.5f, 1.8f, 5f);
        film(8);

        section("CLASH_B" + me + ";DOMAIN EXPANSION;" + rivalName(rivalId) + ";255,90,80;1");
        openDomain(false);
        ShowcaseCamera.set(10, 9f, 2.2f, 9f);
        film(10);

        section("CLASH_C" + me + ";DOMAIN EXPANSION;" + domainName(me) + ";" + rgb + ";1");
        KeyMapping g = key("key.jjk.ultimate");
        in.pressKey(g);
        film(3);
        server.runOnServer(s -> {
            // The key press can be lost while the game is frozen; answer the domain directly if it was.
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            if (DomainManager.ownedBy(p) == null) dev.rick.jjk.core.domain.DomainCounter.tryCounter(Casters.get(p));
        });
        ShowcaseCamera.set(200, 4.5f, 1f, 0f);
        film(21);
        ShowcaseCamera.set(90, 12f, 3.5f, 6f);
        film(50);

        section("CLASH_D" + me + ";" + title + ";;" + rgb + ";1");
        // The lanes stay hidden: the notes are pressed for the player and the clash is told by its light and its sides.
        for (int i = 0; i < 1200 && ctx.computeOnClient(mc -> ClashClient.view() != null); i++) film(1);

        section("CLASH_E" + me + ";;;;1");
        ShowcaseCamera.set(60, 10f, 3f, 6f);
        film(60);
        save();
    }

    /** Yuta awakened with Rika; her True Love Beam fired straight at the camera, which is set on the beam's axis. */
    private void finale() {
        stage("yuta");
        // Out on the open grass, past the arena's wall, so the camera can sit 26 blocks down the beam's axis.
        cmd("execute as @a at @s run tp @s ~90 ~ ~ -60 0");
        ctx.waitTicks(4);
        KeyMapping ult = key("key.jjk.ultimate"), s3 = key("key.jjk.skill_3"), s5 = key("key.jjk.skill_5");
        server.runCommand("execute as @a run jjk awakening 100");
        ctx.waitTicks(5);
        in.pressKey(ult);
        ctx.waitTicks(90);
        // Her moveset, then the beam; the game freezes just after it is cast so every tick of the charge is filmed.
        in.pressKey(s5);
        ctx.waitTicks(6);
        ShowcaseCamera.set(70, 8f, 2.4f, 0f);
        in.pressKey(s3);
        ctx.waitTicks(2);
        freeze();
        section("FINALE;TRUE LOVE;RIKA AND YUTA;255,170,255;1");
        int charge = 57;
        for (int i = 0; i < charge; i++) {
            float k = Mth.clamp(i / (float) (charge - 6), 0, 1);
            float e = k * k * (3 - 2 * k);
            ShowcaseCamera.set(Mth.lerp(e, 70, 180), Mth.lerp(e, 8f, 24f), Mth.lerp(e, 2.4f, 1.15f), Mth.lerp(e, 0f, 2f));
            film(1);
        }
        // It fires: slowed to a quarter speed as the front rushes down its length at the lens.
        section("FIRE;;;;0.25");
        sub = 12;
        film(5);
        sub = 3;
        section("ENGULFED;;;;1");
        film(34);
    }

    private void save() {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("screenshots", "frames.txt"), log.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String rivalName(String id) {
        return switch (id) {
            case "yuji" -> "MALEVOLENT SHRINE";
            case "hakari" -> "IDLE DEATH GAMBLE";
            default -> id.toUpperCase(java.util.Locale.ROOT);
        };
    }

    private static String domainName(String id) {
        return switch (id) {
            case "gojo" -> "INFINITE VOID";
            case "yuta" -> "AUTHENTIC MUTUAL LOVE";
            default -> id.toUpperCase(java.util.Locale.ROOT);
        };
    }

    private void hud(boolean on) {
        ctx.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() == on) mc.gui.hud.toggle();
        });
    }

    /**
     * Films {@code n} game ticks with the game frozen: step one tick, then draw it at {@code sub} evenly spaced sub-tick
     * moments (every clock the mod reads is pinned to the moment being drawn), pressing clash notes as they come due.
     */
    @Override
    protected void film(int n) {
        int[] keys = {InputConstants.KEY_LEFT, InputConstants.KEY_DOWN, InputConstants.KEY_UP, InputConstants.KEY_RIGHT};
        for (int t = 0; t < n; t++) {
            long before = ctx.computeOnClient(mc -> mc.level.getGameTime());
            cmd("tick step 1");
            for (int w = 0; w < 40 && ctx.computeOnClient(mc -> mc.level.getGameTime()) == before; w++) ctx.waitTick();
            for (int s = 0; s < sub; s++) {
                float partial = s / (float) sub;
                double[] info = ctx.computeOnClient(mc -> {
                    ShowcaseCamera.pinnedPartial = partial;
                    ClashClient.recordingPartial = partial;
                    var cv = ClashClient.view();
                    java.util.List<Double> out = new java.util.ArrayList<>();
                    out.add(mc.level.getGameTime() + (double) partial);
                    if (cv != null) {
                        double clock = cv.clock();
                        for (int k = 0; k < cv.times.length; k++) {
                            double dt = cv.times[k] - clock;
                            if (dt <= 0.2 && dt > -2.5 && pressed.add(cv.round * 1000 + k)) out.add((double) cv.lanes[k]);
                        }
                    }
                    return out.stream().mapToDouble(Double::doubleValue).toArray();
                });
                for (int i = 1; i < info.length; i++) in.pressKey(keys[(int) info[i]]);
                String name = String.format("p%05d", frame++);
                ctx.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of(name).withDeltaTicks(partial));
                log.append(name).append(' ').append(info[0]).append(' ').append(section).append('\n');
            }
        }
    }
}
