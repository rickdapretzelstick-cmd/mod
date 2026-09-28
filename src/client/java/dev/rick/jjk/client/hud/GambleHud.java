package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.core.net.GamblePayload;
import dev.rick.jjk.hakari.Gamble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The Idle Death Gamble HUD, for everyone in or near the domain (the gambler also sees his odds and bonus):
 * <ul>
 *   <li>A slot machine at the top of the screen: three reels, the visual moves toward the next Riichi, the attempt.</li>
 *   <li>A Riichi is a major event: a slanted cut-in band (the shared cinematic panels) with Hakari and the scenario's
 *   name slams across the screen, the signal colour lights the machine, the first two reels lock on the same number and
 *   the third spins, slows... and stops.</li>
 *   <li>The result: JACKPOT in flashing casino lettering, or a quiet miss.</li>
 * </ul>
 */
public final class GambleHud {
    private static final String[] SIGNAL_NAME = {"GREEN", "RED", "GOLD", "RAINBOW"};
    private static final int[] SIGNAL_COLOR = {0xFF3CE08A, 0xFFFF3040, 0xFFF0C040, 0xFFFF7FC0};

    private GambleHud() {}

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ClientState.GAMBLES.isEmpty()) return;
        if (dev.rick.jjk.client.clash.ClashClient.playing() || dev.rick.jjk.client.cinematic.DomainCinematic.fullscreen()) return;
        ClientState.Gamble gamble = pick(mc);
        if (gamble == null || gamble.p == null) return;
        GamblePayload p = gamble.p;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        float age = now - gamble.stateStart + partial;
        float time = now + partial;
        boolean mine = p.ownerId() == mc.player.getId();

        if (p.state() == GamblePayload.RIICHI && age < 22) cutIn(g, font, p, w, h, age, time);
        machine(g, font, gamble, p, w, age, time, mine, now, partial);
        result(g, font, p, w, h, age, time);
    }

    /** The gamble that matters to this player: their own, or the domain they are standing in. */
    private static ClientState.Gamble pick(Minecraft mc) {
        ClientState.Gamble own = ClientState.gambleOf(mc.player.getId());
        if (own != null) return own;
        ClientState.Gamble best = null;
        double bd = Double.MAX_VALUE;
        for (ClientState.Gamble gm : ClientState.GAMBLES.values()) {
            ClientState.Domain d = ClientState.DOMAINS.get(gm.p.domainId());
            if (d == null || d.center == null) continue;
            double dist = mc.player.position().distanceTo(d.center);
            if (dist < d.radius + 4 && dist < bd) {
                bd = dist;
                best = gm;
            }
        }
        return best;
    }

    private static void machine(GuiGraphicsExtractor g, Font font, ClientState.Gamble gamble, GamblePayload p, int w, float age, float time,
                                boolean mine, long now, float partial) {
        int pw = 168, ph = 62, x = w / 2 - pw / 2, y = 4;
        boolean riichi = p.state() == GamblePayload.RIICHI;
        int signal = riichi && age >= 16 ? SIGNAL_COLOR[Mth.clamp(p.signal(), 0, 3)] : 0;
        // Cabinet: black lacquer, a gold frame, pink neon that chases around the edge (or the signal colour in a Riichi).
        g.fill(x - 2, y - 2, x + pw + 2, y + ph + 2, 0xE0000000);
        g.fill(x, y, x + pw, y + ph, 0xF0140810);
        int frame = signal != 0 ? (p.signal() == 3 ? rainbow(time, 0) : pulseTo(signal, 0xFFFFFFFF, time)) : 0xFFE8B840;
        outline(g, x - 1, y - 1, x + pw + 1, y + ph + 1, frame);
        for (int i = 0; i < pw; i += 6) {
            boolean on = ((int) (time / 2) + i / 6) % 4 == 0;
            g.fill(x + i, y + 1, x + i + 2, y + 2, on ? 0xFFFF3FA0 : 0x60FF3FA0);
            g.fill(x + pw - i - 2, y + ph - 2, x + pw - i, y + ph - 1, on ? 0xFFFF3FA0 : 0x60FF3FA0);
        }
        CinematicPanels.labelCentered(g, font, "IDLE DEATH GAMBLE", w / 2f, y + 4, 0.75f, 0xFFFF7FC0, 1f);
        // Reels.
        int rw = 34, rh = 30, gap = 8, rx = w / 2 - (rw * 3 + gap * 2) / 2, ry = y + 14;
        boolean jolt = now - gamble.visualTick < 6;
        int[] reels = {p.reel0(), p.reel1(), p.reel2()};
        int reveal = dev.rick.jjk.config.JJKConfig.get().hakari.riichiTicks - Gamble.REVEAL_OFFSET;
        for (int i = 0; i < 3; i++) {
            int x0 = rx + i * (rw + gap);
            boolean locked = riichi && i < 2 && age >= 8;
            boolean spinning = p.state() == GamblePayload.SPINNING || (riichi && (i == 2 ? age < reveal : age < 8 + i * 4));
            g.fill(x0 - 1, ry - 1, x0 + rw + 1, ry + rh + 1, locked ? 0xFFF0C040 : 0xFF6A5060);
            g.fillGradient(x0, ry, x0 + rw, ry + rh, 0xFFFFFFFF, 0xFFD8D0E0);
            int digit;
            float scroll = 0;
            if (spinning) {
                // A blur of numbers; the third reel slows down toward the reveal.
                float speed = riichi && i == 2 ? Mth.lerp(Mth.clamp(age / reveal, 0, 1), 2.2f, 0.35f) : 1.6f;
                float t = time * speed + i * 3.3f;
                digit = 1 + Math.floorMod((int) t + reels[i], 7);
                scroll = (t - (int) t) * rh;
            } else {
                digit = reels[i] == 0 ? 7 : reels[i];
            }
            if (jolt && !riichi) scroll += (6 - (now - gamble.visualTick)) * 1.5f;
            g.enableScissor(x0, ry, x0 + rw, ry + rh);
            numeral(g, font, digit, x0 + rw / 2f, ry + rh / 2f - 8 + scroll);
            if (spinning) numeral(g, font, 1 + Math.floorMod(digit, 7), x0 + rw / 2f, ry + rh / 2f - 8 + scroll - rh);
            g.disableScissor();
            if (locked) g.fill(x0, ry, x0 + rw, ry + 2, 0xFFF0C040);
        }
        // Visual moves toward the Riichi, and the attempt.
        int iy = y + ph - 12;
        StringBuilder pips = new StringBuilder();
        for (int i = 0; i < p.required(); i++) pips.append(i < p.progress() ? '●' : '○');
        String left = riichi ? SIGNAL_NAME[Mth.clamp(p.signal(), 0, 3)] + " SIGNAL" : "VISUAL " + pips;
        CinematicPanels.label(g, font, left, x + 6, iy, 0.7f, riichi && age >= 16 ? signal | 0xFF000000 : 0xFFFFFFFF, riichi && age < 16 ? 0 : 1f, false);
        String right = "ATTEMPT " + Math.max(1, Math.min(p.maxAttempts(), p.attempt() + (p.state() == GamblePayload.SPINNING ? 1 : 0))) + "/" + p.maxAttempts();
        CinematicPanels.label(g, font, right, x + pw - 6, iy, 0.7f, 0xFFE8D8F0, 1f, true);
        // The gambler's own odds and any carried bonus, just under the machine.
        if (mine && p.chance() >= 0) {
            String odds = (riichi ? "JACKPOT ODDS " : "EST. ODDS ") + Math.round(p.chance() * 100) + "%";
            if (p.bonus().isEmpty()) CinematicPanels.labelCentered(g, font, odds, w / 2f, y + ph + 5, 0.75f, 0xFFF0C040, 1f);
            else CinematicPanels.label(g, font, odds, w / 2f - 4, y + ph + 5, 0.75f, 0xFFF0C040, 1f, true);
            if (!p.bonus().isEmpty()) CinematicPanels.label(g, font, p.bonus(), w / 2f + 4, y + ph + 5, 0.75f, 0xFF5CFFA8, 1f, false);
        }
        if (p.state() == GamblePayload.SPINNING && p.progress() >= p.required() - 1 && mine) {
            float a = 0.5f + 0.5f * Mth.sin(time * 0.4f);
            CinematicPanels.labelCentered(g, font, "ONE MORE VISUAL MOVE FOR RIICHI", w / 2f, y + ph + 16, 0.7f, 0xFFFF7FC0, a);
        }
    }

    /** A big red or black number in a reel (7 is always red). */
    private static void numeral(GuiGraphicsExtractor g, Font font, int n, float cx, float cy) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(2.4f, 2.4f);
        String s = String.valueOf(n);
        g.text(font, s, -font.width(s) / 2, 0, n == 7 ? 0xFFE0102A : 0xFF141018, false);
        pose.popMatrix();
    }

    /** The Riichi announcement: a slanted cut-in slams across the screen with Hakari and the scenario's name. */
    private static void cutIn(GuiGraphicsExtractor g, Font font, GamblePayload p, int w, int h, float age, float time) {
        float in = Mth.clamp(age / 5f, 0, 1), out = Mth.clamp((22 - age) / 5f, 0, 1);
        float slide = (1 - ease(in)) * w - (1 - out) * w;
        CinematicPanels.Band band = new CinematicPanels.Band(h * 0.36f, h * 0.64f);
        int color = p.signal() == 3 ? 0xFFFF3FA0 : p.signal() == 2 ? 0xFFC89020 : p.signal() == 1 ? 0xFFC01830 : 0xFFB0206C;
        CinematicPanels.band(g, band, w, color, p.ownerId(), w * 0.28f, slide, time);
        CinematicPanels.border(g, band.top(), w, 4);
        CinematicPanels.border(g, band.bottom(), w, 4);
        float a = Mth.clamp((age - 4) / 3f, 0, 1) * out;
        String title = Gamble.Scenario.values()[Mth.clamp(p.scenario(), 0, 2)].title;
        CinematicPanels.labelCentered(g, font, "RIICHI!", w * 0.58f + slide, band.topAt(w * 0.58f, w) + 10, 3f, 0xFFFFFFFF, a);
        CinematicPanels.labelCentered(g, font, title, w * 0.58f + slide, band.bottomAt(w * 0.58f, w) - 24, 1.5f, 0xFFF0C040, a);
    }

    /** JACKPOT, or the miss, in the middle of the screen. */
    private static void result(GuiGraphicsExtractor g, Font font, GamblePayload p, int w, int h, float age, float time) {
        int reveal = dev.rick.jjk.config.JJKConfig.get().hakari.riichiTicks - Gamble.REVEAL_OFFSET;
        boolean jackpot = p.state() == GamblePayload.JACKPOT || p.state() == GamblePayload.RIICHI && age >= reveal && p.reel2() == p.reel0() && p.reel2() != 0;
        boolean miss = p.state() == GamblePayload.MISS || p.state() == GamblePayload.DONE && age < 30
                || p.state() == GamblePayload.RIICHI && age >= reveal && p.reel2() != 0 && p.reel2() != p.reel0();
        var pose = g.pose();
        if (jackpot) {
            float pop = 3.6f + Math.max(0, 1.5f - (age - reveal) / 4f);
            pose.pushMatrix();
            pose.translate(w / 2f, h * 0.36f);
            pose.scale(pop, pop);
            String word = "JACKPOT!!";
            int cx = -font.width(word) / 2;
            for (int i = 0; i < word.length(); i++) {
                String ch = String.valueOf(word.charAt(i));
                g.text(font, ch, cx, (int) (Mth.sin(time * 0.6f + i) * 1.5f), rainbow(time, i), true);
                cx += font.width(ch);
            }
            pose.popMatrix();
            CinematicPanels.labelCentered(g, font, p.reel0() + " " + p.reel1() + " " + p.reel2() + (p.reel0() % 2 == 1 ? "  —  PROBABILITY CHANGE" : ""),
                    w / 2f, h * 0.36f + 34, 1f, 0xFFF0C040, 1f);
        } else if (miss) {
            float a = p.state() == GamblePayload.RIICHI ? 1f : Mth.clamp(1 - age / 24f, 0, 1);
            CinematicPanels.labelCentered(g, font, p.state() == GamblePayload.DONE ? "NO ATTEMPTS LEFT" : "MISS", w / 2f, h * 0.36f, 2.2f, 0xFFB0A8B8, a);
            if (p.state() == GamblePayload.MISS) {
                CinematicPanels.labelCentered(g, font, "KEEP GAMBLING — USE YOUR TECHNIQUES", w / 2f, h * 0.36f + 24, 0.8f, 0xFFFF7FC0, a);
            }
        }
    }

    private static float ease(float x) {
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static int rainbow(float time, int i) {
        int[] cols = {0xFFFF3FA0, 0xFFF0C040, 0xFF5CFFA8, 0xFFFFFFFF, 0xFF7FD4FF};
        return cols[Math.floorMod((int) (time / 2) + i, cols.length)];
    }

    private static int pulseTo(int a, int b, float time) {
        float t = Mth.sin(time * 0.5f) * 0.5f + 0.5f;
        return 0xFF000000 | Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255)) << 16 | Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255)) << 8
                | Math.round(Mth.lerp(t, a & 255, b & 255));
    }

    private static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }
}
