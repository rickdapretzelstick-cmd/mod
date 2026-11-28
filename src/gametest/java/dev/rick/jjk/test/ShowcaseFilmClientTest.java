package dev.rick.jjk.test;

import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.melee.MeleeSystem;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The full showcase (docs/SHOWCASE_RECORDING_PROMPT.md): every act of the script filmed from the real game, frame by
 * frame with the game frozen (see {@link DomainCinematicClientTest#film}), the sounds it plays logged for the soundtrack.
 * {@code JJK_SCENES} (space or comma separated) picks the scenes a run films: gojo, hakari, yuji, yuta, ryu, domains,
 * clash, beam, feel. Writes screenshots/frames.txt, screenshots/sounds.txt and screenshots/refused.txt (every cast the
 * game refused, so a run is checked, not assumed). Opt-in: not in the default client test list.
 */
public class ShowcaseFilmClientTest extends DomainCinematicClientTest {
    private final StringBuilder refused = new StringBuilder();
    /** The shot being filmed: a new one at every staging. Section names carry it so captions change within a shot. */
    private int shot;

    @Override
    protected void section(String name) {
        super.section(name.startsWith("SHOW") || name.startsWith("CLASH_") || name.startsWith("FINALE") || name.startsWith("FIRE")
                || name.startsWith("ENGULFED") ? name : "S" + shot + "_" + name);
    }

    // Camera recipes (ShowcaseCamera: angle round the hero from behind, distance, height, look-ahead).
    private static final float[] SIDE = {62, 5.6f, 1.3f, 2.2f};
    private static final float[] OTS = {18, 6.6f, 1.7f, 4f};
    private static final float[] FRONT_LOW = {148, 5.2f, 0.5f, 2.4f};
    private static final float[] QUARTER = {124, 5.4f, 1.0f, 1.4f};
    private static final float[] WIDE = {80, 9f, 3f, 3.5f};

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            server = sp.getServer();
            in = ctx.getInput();
            in.resizeWindow(1280, 720);
            ctx.runOnClient(mc -> {
                mc.options.fov().set(78);
                // No speed-driven FOV: a cast's speed boost would otherwise swing the lens wide mid-shot.
                mc.options.fovEffectScale().set(0.0);
            });
            cmd("time set noon");
            cmd("gamerule advance_time false");
            cmd("gamerule send_command_feedback false");
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
            sub = 3;
            SoundLog.recording = true;
            SoundLog.take();

            String env = System.getenv().getOrDefault("JJK_SCENES", "gojo hakari yuji yuta ryu domains clash feel");
            List<String> scenes = Arrays.asList(env.trim().split("[\\s,]+"));
            for (String s : scenes) {
                switch (s) {
                    case "gojo" -> gojo();
                    case "hakari" -> hakari();
                    case "yuji" -> yuji();
                    case "yuta" -> yuta();
                    case "ryu" -> ryu();
                    case "domains" -> domains();
                    case "clash" -> clash("gojo", "yuji", true, "DOMAIN CLASH", "255,225,140");
                    case "beam" -> finale();
                    case "feel" -> feel();
                    default -> System.out.println("[showcase] unknown scene " + s);
                }
                save();
            }
            SoundLog.recording = false;
        }
    }

    @Override
    protected void save() {
        super.save();
        try {
            Files.writeString(Path.of("screenshots", "sounds.txt"), SoundLog.LINES.toString());
            Files.writeString(Path.of("screenshots", "refused.txt"), refused.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    // --- Tools ---

    private void cam(float[] c) {
        ShowcaseCamera.set(c[0], c[1], c[2], c[3]);
    }

    /** Eases the camera from {@code a} to {@code b} over {@code ticks} filmed ticks. */
    private void move(float[] a, float[] b, int ticks) {
        for (int i = 0; i < ticks; i++) {
            float k = (i + 1) / (float) ticks;
            float e = k * k * (3 - 2 * k);
            ShowcaseCamera.set(Mth.lerp(e, a[0], b[0]), Mth.lerp(e, a[1], b[1]), Mth.lerp(e, a[2], b[2]), Mth.lerp(e, a[3], b[3]));
            film(1);
        }
    }

    /** Clears whatever the last shot left in the air (projectiles, orbs, debris), Rika aside. */
    private void sweep() {
        cmd("execute as @a at @s run kill @e[type=!minecraft:player,type=!jjk:rika,distance=..120]");
        // Long enough for the last dummy's death animation to finish off camera.
        ctx.waitTicks(24);
    }

    private void unfreeze() {
        cmd("tick unfreeze");
        ctx.waitTicks(2);
    }

    /** Clears the stage and puts one dummy {@code ahead} blocks in front of the hero (no AI, facing them). */
    private void target(double ahead) {
        shot++;
        unfreeze();
        sweep();
        clearDummies();
        cmd("execute as @a run jjk restore now");
        resetPlayer();
        rival(null, ahead, false, 0);
        ctx.waitTicks(6);
    }

    /** Like {@link #target}, but out on the open grass past the arena wall (no pillars for the camera to hit). */
    private void field(double ahead) {
        shot++;
        unfreeze();
        sweep();
        clearDummies();
        cmd("execute as @a run jjk restore now");
        resetPlayer();
        cmd("execute as @a at @s run tp @s ~90 ~ ~ -60 0");
        ctx.waitTicks(10);
        rival(null, ahead, false, 0);
        ctx.waitTicks(6);
    }

    private ServerPlayer hero(net.minecraft.server.MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static Entity nearestDummy(ServerPlayer p) {
        Entity best = null;
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(40), e -> e instanceof dev.rick.jjk.entity.TrainingDummy || e instanceof net.minecraft.world.entity.monster.zombie.Zombie)) {
            if (best == null || e.distanceTo(p) < best.distanceTo(p)) best = e;
        }
        return best;
    }

    /** Presses (or releases) a move on the server, aimed at the nearest dummy; a refusal is logged. */
    private void key(AbilitySlot slot, boolean down) {
        String sec = section;
        server.runOnServer(s -> {
            ServerPlayer p = hero(s);
            var c = Casters.get(p);
            boolean ok = c.input(slot, down, 0, 0, nearestDummy(p));
            if (down && !ok) refused.append(sec).append(' ').append(slot).append(' ').append(c.lastRefusal).append('\n');
        });
    }

    private void tap(AbilitySlot slot) {
        key(slot, true);
        key(slot, false);
    }

    private void m1() {
        server.runOnServer(s -> {
            ServerPlayer p = hero(s);
            Entity d = nearestDummy(p);
            MeleeSystem.light(p, Casters.get(p), 0, d == null ? -1 : d.getId());
        });
    }

    /** Into the Awakening (if not in it already), with a full meter. */
    private void awaken() {
        server.runOnServer(s -> {
            var c = Casters.get(hero(s));
            c.setAwakening(c.maxAwakening());
            if (!c.isAwakened()) c.enterAwakening();
        });
    }

    /** Out of the Awakening, meter refilled: the base kit. */
    private void calm() {
        server.runOnServer(s -> {
            var c = Casters.get(hero(s));
            if (c.isAwakened()) c.endAwakening("showcase");
            c.setAwakening(c.maxAwakening());
        });
    }

    /** Films until the hero's cast is over (at most {@code max} ticks), then {@code after} more. */
    private void filmCast(int max, int after) {
        int t = 0;
        while (t < max && server.computeOnServer(s -> Casters.get(hero(s)).isCasting())) {
            film(1);
            t++;
        }
        film(after);
    }

    /** A caption: {@code NAME;TOP;sub;r,g,b;speed}. */
    private void caption(String name, String top, String subLine, String rgb) {
        section(name + ";" + top + ";" + subLine + ";" + rgb + ";1");
    }

    private void slow(String name, float speed) {
        section(name + ";;;;" + speed);
    }

    // --- Acts ---

    private static final String GOJO = "140,200,255", HAKARI = "120,255,200", YUJI = "255,90,80", YUTA = "247,107,255", RYU = "120,170,255";

    private void gojo() {
        stage("gojo");
        calm();
        // M1s into Twofold Kick.
        target(2.6);
        freeze();
        caption("GOJO_M1", "SATORU GOJO", "THE HONORED ONE", GOJO);
        cam(SIDE);
        film(10);
        for (int i = 0; i < 3; i++) {
            m1();
            film(7);
        }
        caption("GOJO_TWOFOLD", "TWOFOLD KICK", "", GOJO);
        tap(AbilitySlot.SKILL_4);
        filmCast(80, 12);
        // Lapse Blue.
        target(9);
        freeze();
        caption("GOJO_BLUE", "LAPSE BLUE", "", GOJO);
        cam(OTS);
        film(6);
        tap(AbilitySlot.SKILL_1);
        filmCast(90, 10);
        // Reversal Red, Limitless in the wind-up: behind them upside down.
        field(8);
        freeze();
        caption("GOJO_RED", "REVERSAL RED", "LIMITLESS", GOJO);
        cam(new float[] {72, 7.5f, 1.4f, 4f});
        film(6);
        tap(AbilitySlot.SKILL_2);
        film(4);
        tap(AbilitySlot.SKILL_5);
        filmCast(80, 14);
        // Six Eyes.
        target(6);
        unfreeze();
        clearDummies();
        ctx.waitTicks(4);
        freeze();
        caption("GOJO_SIXEYES", "SIX EYES", "AWAKENING", GOJO);
        cam(FRONT_LOW);
        film(4);
        tap(AbilitySlot.ULTIMATE);
        move(FRONT_LOW, new float[] {170, 3.2f, 0.9f, 0.2f}, 50);
        // Max Blue, steered through a line of dummies.
        shot++;
        unfreeze();
        sweep();
        clearDummies();
        resetPlayer();
        rival(null, 6, false, 0);
        rival(null, 9, false, 0);
        rival(null, 12, false, 0);
        awaken();
        ctx.waitTicks(6);
        freeze();
        caption("GOJO_MAXBLUE", "LAPSE BLUE MAX", "", GOJO);
        cam(WIDE);
        film(4);
        key(AbilitySlot.SKILL_1, true);
        film(40);
        key(AbilitySlot.SKILL_1, false);
        filmCast(60, 10);
        // Max Red.
        target(10);
        awaken();
        freeze();
        caption("GOJO_MAXRED", "REVERSAL RED MAX", "", GOJO);
        cam(OTS);
        film(4);
        tap(AbilitySlot.SKILL_2);
        move(OTS, new float[] {40, 6f, 1.8f, 5f}, 40);
        filmCast(60, 12);
        // Hollow Purple across the open grass past the arena wall, then the terrain coming back.
        shot++;
        unfreeze();
        sweep();
        clearDummies();
        resetPlayer();
        cmd("execute as @a at @s run tp @s ~90 ~ ~ -60 0");
        ctx.waitTicks(10);
        rival(null, 14, false, 0);
        awaken();
        ctx.waitTicks(6);
        freeze();
        caption("GOJO_PURPLE", "HOLLOW PURPLE", "", "200,120,255");
        cam(OTS);
        film(4);
        tap(AbilitySlot.SKILL_3);
        move(OTS, new float[] {70, 7f, 2f, 4f}, 50);
        filmCast(120, 36);
        caption("GOJO_RESTORE", "THE WORLD COMES BACK", "", GOJO);
        cam(new float[] {55, 10f, 3.5f, 6f});
        film(12);
        cmd("execute as @a run jjk restore now");
        film(20);
        unfreeze();
        resetPlayer();
    }

    private void hakari() {
        stage("hakari");
        calm();
        target(2.6);
        freeze();
        caption("HAKARI_M1", "KINJI HAKARI", "THE RESTLESS GAMBLER", HAKARI);
        cam(QUARTER);
        film(10);
        for (int i = 0; i < 3; i++) {
            m1();
            film(7);
        }
        caption("HAKARI_ROUGH", "ROUGH ENERGY", "", HAKARI);
        cam(SIDE);
        tap(AbilitySlot.SKILL_3);
        filmCast(80, 12);
        target(6);
        freeze();
        caption("HAKARI_DOORS", "SHUTTER DOORS", "", HAKARI);
        cam(SIDE);
        film(4);
        tap(AbilitySlot.SKILL_2);
        filmCast(60, 16);
        target(3);
        freeze();
        caption("HAKARI_FEVER", "FEVER BREAKER", "", HAKARI);
        cam(QUARTER);
        film(4);
        tap(AbilitySlot.SKILL_4);
        filmCast(100, 14);
    }

    private void yuji() {
        stage("yuji");
        calm();
        target(4);
        freeze();
        caption("YUJI_STRIKES", "YUJI ITADORI", "THE VESSEL", YUJI);
        cam(SIDE);
        film(8);
        caption("YUJI_STRIKES2", "CURSED STRIKES", "", YUJI);
        tap(AbilitySlot.SKILL_1);
        filmCast(120, 12);
        target(2.2);
        freeze();
        caption("YUJI_CRUSH", "CRUSHING BLOW", "", YUJI);
        cam(QUARTER);
        film(4);
        tap(AbilitySlot.SKILL_2);
        filmCast(100, 16);
        // Divergent Fist with the Black Flash press, in slow motion around the flash.
        target(2.6);
        freeze();
        caption("YUJI_DIVERGENT", "DIVERGENT FIST", "", YUJI);
        cam(new float[] {120, 4.2f, 0.7f, 1.6f});
        film(4);
        tap(AbilitySlot.SKILL_3);
        film(6);
        tap(AbilitySlot.SKILL_3);
        film(2);
        caption("YUJI_BLACKFLASH", "BLACK FLASH", "", "255,60,60");
        int was = sub;
        sub = 12;
        section("YUJI_BLACKFLASH;BLACK FLASH;;255,60,60;0.25");
        film(8);
        sub = was;
        caption("YUJI_BLACKFLASH2", "", "", YUJI);
        filmCast(40, 14);
        // King of Curses.
        target(6);
        unfreeze();
        clearDummies();
        ctx.waitTicks(4);
        freeze();
        caption("YUJI_SUKUNA", "KING OF CURSES", "SUKUNA", YUJI);
        cam(FRONT_LOW);
        film(4);
        tap(AbilitySlot.ULTIMATE);
        move(FRONT_LOW, new float[] {175, 3f, 1f, 0.2f}, 60);
        filmCast(80, 10);
        target(6);
        awaken();
        ctx.waitTicks(40);
        freeze();
        caption("YUJI_DISMANTLE", "DISMANTLE", "", YUJI);
        cam(SIDE);
        film(4);
        tap(AbilitySlot.SKILL_1);
        filmCast(80, 10);
        target(3);
        freeze();
        caption("YUJI_CLEAVE", "CLEAVE", "", YUJI);
        cam(QUARTER);
        film(4);
        tap(AbilitySlot.SKILL_5);
        filmCast(80, 14);
    }

    private void yuta() {
        stage("yuta");
        calm();
        target(2.6);
        freeze();
        caption("YUTA_M1", "YUTA OKKOTSU", "CURSED PARTNERS", YUTA);
        cam(SIDE);
        film(10);
        for (int i = 0; i < 3; i++) {
            m1();
            film(8);
        }
        target(8);
        freeze();
        caption("YUTA_RESOLUTE", "RESOLUTE SLASH", "", YUTA);
        cam(WIDE);
        film(4);
        tap(AbilitySlot.SKILL_2);
        film(9);
        tap(AbilitySlot.SKILL_2);
        caption("YUTA_RESOLUTE_BF", "RESOLUTE BLACK FLASH", "", "255,60,60");
        filmCast(80, 14);
        // Rika: manifested, then her moveset.
        target(5);
        freeze();
        caption("YUTA_RIKA", "RIKA", "", YUTA);
        cam(FRONT_LOW);
        film(4);
        tap(AbilitySlot.SKILL_5);
        film(40);
        tap(AbilitySlot.SKILL_5);
        film(6);
        caption("YUTA_SMASH", "RIKA SMASH", "", YUTA);
        cam(WIDE);
        tap(AbilitySlot.SKILL_1);
        film(44);
        target(5);
        freeze();
        caption("YUTA_HAYMAKER", "RIKA HAYMAKER", "", YUTA);
        cam(WIDE);
        film(4);
        tap(AbilitySlot.SKILL_5);
        film(6);
        tap(AbilitySlot.SKILL_3);
        film(50);
        // True Love and Copy (Cursed Speech).
        target(6);
        unfreeze();
        clearDummies();
        ctx.waitTicks(4);
        freeze();
        caption("YUTA_TRUELOVE", "TRUE LOVE", "", YUTA);
        cam(FRONT_LOW);
        film(4);
        tap(AbilitySlot.ULTIMATE);
        move(FRONT_LOW, new float[] {175, 3f, 1f, 0.2f}, 60);
        filmCast(80, 10);
        target(6);
        awaken();
        freeze();
        caption("YUTA_COPY", "COPY", "CURSED SPEECH", YUTA);
        cam(SIDE);
        film(4);
        tap(AbilitySlot.SKILL_2);
        filmCast(80, 14);
    }

    private void ryu() {
        stage("ryu");
        calm();
        target(9);
        freeze();
        caption("RYU_GRANITE", "RYU ISHIGORI", "THE TRUE CANNON", RYU);
        cam(OTS);
        film(8);
        caption("RYU_GRANITE2", "GRANITE BLAST", "", RYU);
        tap(AbilitySlot.SKILL_1);
        filmCast(40, 10);
        target(9);
        freeze();
        caption("RYU_APPETIZER", "APPETIZER", "", RYU);
        cam(SIDE);
        film(4);
        tap(AbilitySlot.SKILL_4);
        filmCast(100, 14);
        target(3);
        freeze();
        caption("RYU_UNSATISFIED", "UNSATISFIED", "", RYU);
        cam(QUARTER);
        film(4);
        tap(AbilitySlot.SKILL_2);
        filmCast(100, 12);
        // Every Last Drop, across the open grass.
        shot++;
        unfreeze();
        sweep();
        clearDummies();
        resetPlayer();
        cmd("execute as @a at @s run tp @s ~90 ~ ~ -60 0");
        ctx.waitTicks(10);
        rival(null, 14, false, 0);
        ctx.waitTicks(6);
        freeze();
        caption("RYU_ELD", "EVERY LAST DROP", "", RYU);
        cam(OTS);
        film(4);
        tap(AbilitySlot.ULTIMATE);
        move(OTS, new float[] {75, 8f, 2.2f, 5f}, 60);
        filmCast(200, 20);
        unfreeze();
        cmd("execute as @a run jjk restore now");
        resetPlayer();
    }

    private void domains() {
        showcase("gojo", "INFINITE VOID", GOJO, 0);
        showcase("hakari", "IDLE DEATH GAMBLE", HAKARI, 2);
        showcase("yuji", "MALEVOLENT SHRINE", YUJI, 1);
        showcase("yuta", "AUTHENTIC MUTUAL LOVE", YUTA, 3);
        unfreeze();
        cmd("execute as @a run jjk domain cancel all");
        ctx.waitTicks(40);
        cmd("execute as @a run jjk restore now");
    }

    /** The feel of the fight: a flinch from every side, a heavy blow, a knockdown and getting up, a zombie's Black Flash. */
    private void feel() {
        stage("yuji");
        calm();
        target(3);
        freeze();
        caption("FEEL_FLINCH", "EVERY HIT LANDS", "AND EVERY BODY TAKES IT", "255,255,255");
        cam(new float[] {100, 5f, 1.1f, 3f});
        film(6);
        int was = sub;
        for (float side : new float[] {0, 180, 90, -90}) {
            strike(side, 10);
            sub = 6;
            slow("FEEL_FLINCH_" + (int) side, 0.5f);
            film(8);
            sub = was;
            caption("FEEL_FLINCH_R" + (int) side, "EVERY HIT LANDS", "AND EVERY BODY TAKES IT", "255,255,255");
            film(14);
        }
        caption("FEEL_HEAVY", "HEAVY BLOWS", "", "255,255,255");
        strike(0, 24);
        film(30);
        // A knockdown and the wake-up: Sukuna's Rush (knee, leap, slam).
        target(5);
        awaken();
        unfreeze();
        ctx.waitTicks(40);
        freeze();
        caption("FEEL_KNOCKDOWN", "KNOCKDOWNS", "AND GETTING BACK UP", "255,255,255");
        cam(SIDE);
        film(4);
        tap(AbilitySlot.SKILL_3);
        filmCast(120, 50);
        // Any two-legged mob: a zombie takes a Black Flash.
        shot++;
        unfreeze();
        sweep();
        clearDummies();
        cmd("execute as @a at @s run kill @e[type=minecraft:zombie,distance=..60]");
        stage("yuji");
        calm();
        cmd("execute as @a at @s rotated ~ 0 run summon minecraft:zombie ^ ^ ^2.6 {NoAI:1b,PersistenceRequired:1b}");
        ctx.waitTicks(6);
        server.runOnServer(s -> {
            ServerPlayer p = hero(s);
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(6), e -> e instanceof net.minecraft.world.entity.monster.zombie.Zombie)) {
                e.setYRot(p.getYRot() + 180);
                if (e instanceof LivingEntity le) le.setYBodyRot(p.getYRot() + 180);
            }
        });
        ctx.waitTicks(4);
        freeze();
        caption("FEEL_ZOMBIE", "ANY TWO-LEGGED MOB", "", "255,255,255");
        cam(new float[] {120, 4.2f, 0.8f, 1.6f});
        film(4);
        tap(AbilitySlot.SKILL_3);
        film(6);
        tap(AbilitySlot.SKILL_3);
        filmCast(60, 20);
        unfreeze();
        cmd("execute as @a at @s run kill @e[type=minecraft:zombie,distance=..60]");
    }

    /** A blow on the nearest dummy from {@code side} degrees round it, {@code stun} ticks of hitstun. */
    private void strike(float side, int stun) {
        server.runOnServer(s -> {
            ServerPlayer p = hero(s);
            LivingEntity d = (LivingEntity) nearestDummy(p);
            if (d == null) return;
            float body = d.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(body), 0, Mth.cos(body)), left = new Vec3(Mth.cos(body), 0, Mth.sin(body));
            Vec3 out = side == 0 ? fwd : side == 180 ? fwd.scale(-1) : side == 90 ? left : left.scale(-1);
            HitResolver.resolve(Hit.builder(p, "showcase").damage(0.01f).knockback(Knockback.NONE).hitstun(stun)
                    .origin(d.getBoundingBox().getCenter().add(out.scale(2))).build(), d);
        });
    }
}
