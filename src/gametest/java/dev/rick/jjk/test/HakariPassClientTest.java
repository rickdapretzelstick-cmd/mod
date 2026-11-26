package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.ClientAnimations;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every Hakari clip stepped frame by frame (the world frozen, one tick per step) from the side, then tiled into contact
 * sheets: one row per clip, the contact frame outlined. Opt-in; sheets anim-sheets/hakari_*.png.
 */
public class HakariPassClientTest implements FabricClientGameTest {
    private static final String[] CLIPS = {"reserve_balls", "shutter_sign", "rough_charge", "rough_strike", "fever_kick", "fever_finish",
            "fever_crush", "fever_raise", "fever_axe", "door_guard", "idg_sign", "jackpot", "volley_open", "volley_flurry", "volley_final",
            "rushdown_run", "rushdown_drag", "rushdown_throw", "energy_leap", "overwhelm_ready", "overwhelm_left", "overwhelm_right",
            "overwhelm_final", "surge_dash", "surge_kick", "rhythm_dance"};
    private static final int FRAMES = 8, TW = 240, TH = 180;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            ctx.waitTicks(30);
            ctx.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            ShowcaseCamera.set(72, 4.2f, 1.2f, 0);
            ShowcaseCamera.enabled = true;
            server.runCommand("tick freeze");
            ctx.waitTicks(5);
            List<BufferedImage> rows = new ArrayList<>();
            for (String name : CLIPS) {
                Clip clip = ctx.computeOnClient(mc -> AnimLibrary.get(name));
                if (clip == null) continue;
                float span = clip.endless() ? Math.max(clip.duration, 600) : clip.duration + 100;
                int stepTicks = Math.max(1, Math.round(span / 50f / FRAMES));
                float contact = -1;
                for (Clip.Marker m : clip.markers) if (m.label().equals("contact")) contact = m.time();
                ctx.runOnClient(mc -> ClientAnimations.play(mc.player.getId(), name, 1f, mc.level.getGameTime()));
                List<BufferedImage> frames = new ArrayList<>();
                List<Integer> times = new ArrayList<>();
                for (int i = 0; i < FRAMES; i++) {
                    int t = i * stepTicks * 50;
                    String shot = "hk_" + name + "_" + i;
                    ctx.takeScreenshot(shot);
                    frames.add(load(shot));
                    times.add(t);
                    server.runCommand("tick step " + stepTicks);
                    ctx.waitTicks(stepTicks + 1);
                }
                ctx.runOnClient(mc -> ClientAnimations.play(mc.player.getId(), "", 1f, mc.level.getGameTime()));
                ctx.runOnClient(mc -> ClientAnimations.forget(mc.player.getId()));
                server.runCommand("tick step 20");
                ctx.waitTicks(22);
                rows.add(row(name, frames, times, contact));
            }
            server.runCommand("tick unfreeze");
            ShowcaseCamera.off();
            // Six clips per sheet.
            try {
                Path dir = Path.of("anim-sheets");
                Files.createDirectories(dir);
                for (int s = 0; s * 6 < rows.size(); s++) {
                    List<BufferedImage> part = rows.subList(s * 6, Math.min(rows.size(), (s + 1) * 6));
                    BufferedImage sheet = new BufferedImage(part.getFirst().getWidth(), part.size() * (TH + 18), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = sheet.createGraphics();
                    for (int r = 0; r < part.size(); r++) g.drawImage(part.get(r), 0, r * (TH + 18), null);
                    g.dispose();
                    ImageIO.write(sheet, "png", dir.resolve("hakari_" + s + ".png").toFile());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** The newest screenshot whose name ends with {@code shot}. */
    private static BufferedImage load(String shot) {
        try (Stream<Path> files = Files.list(Path.of("screenshots"))) {
            Path p = files.filter(f -> f.getFileName().toString().endsWith("_" + shot + ".png")).max(java.util.Comparator.comparing(Path::toString))
                    .orElseThrow(() -> new AssertionError("no screenshot " + shot));
            return ImageIO.read(p.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage row(String name, List<BufferedImage> frames, List<Integer> times, float contact) {
        BufferedImage out = new BufferedImage(frames.size() * TW, TH + 18, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        // The contact: the frame closest to the marked contact time.
        int ci = -1;
        if (contact >= 0) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < times.size(); i++) if (Math.abs(times.get(i) - contact) < best) { best = Math.abs(times.get(i) - contact); ci = i; }
        }
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage f = frames.get(i);
            // The middle of the frame (the fighter), scaled into the tile.
            int cw = f.getWidth() * 3 / 5, ch = f.getHeight() * 4 / 5, cx = (f.getWidth() - cw) / 2, cy = (f.getHeight() - ch) / 2;
            g.drawImage(f, i * TW, 18, i * TW + TW, 18 + TH, cx, cy, cx + cw, cy + ch, null);
            g.setColor(i == ci ? Color.ORANGE : Color.WHITE);
            g.drawString((i == 0 ? name + "  " : "") + times.get(i) + "ms" + (i == ci ? " CONTACT" : ""), i * TW + 4, 13);
            if (i == ci) {
                g.drawRect(i * TW, 18, TW - 1, TH - 1);
                g.drawRect(i * TW + 1, 19, TW - 3, TH - 3);
            }
        }
        g.dispose();
        return out;
    }
}
