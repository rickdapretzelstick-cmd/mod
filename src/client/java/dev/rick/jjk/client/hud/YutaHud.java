package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.net.YutaPayload;
import dev.rick.jjk.yuta.Copies;
import dev.rick.jjk.yuta.CopyWheelAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Cursed Partners' own HUD pieces (JJS):
 * <ul>
 *   <li>the Copy Wheel: two pages of four techniques around the crosshair, the selected one lit, each with its own
 *       cooldown, and the keys that pick them;</li>
 *   <li>the newest copied technique, revealed at his right for a moment;</li>
 *   <li>Outburst's 3-segment bar at his right while it is held;</li>
 *   <li>Jacob's Ladder: direct katana hits so far, on the domain's box;</li>
 *   <li>Rika's moveset being up: the ring's faint glow at the bottom of the screen.</li>
 * </ul>
 */
public final class YutaHud {
    public static int rikaId = -1;
    public static boolean rikaMode, wheelOpen;
    public static int page;
    public static List<String> copied = new ArrayList<>(List.of(Copies.SPEECH));
    public static String selected = Copies.SPEECH;
    public static List<Long> readyAt = new ArrayList<>();
    public static int ladderHits, ladderNeeded = 4, outburstStage = -1;
    private static String newest = "";
    private static long newestAt;
    private static long wheelChangedAt;

    private YutaHud() {}

    public static void apply(YutaPayload p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        if (p.copied().size() > 0 && !copied.contains(p.copied().getLast())) {
            newest = p.copied().getLast();
            newestAt = now;
        }
        if (p.wheelOpen() != wheelOpen) wheelChangedAt = now;
        rikaId = p.rikaId();
        rikaMode = p.rikaMode();
        wheelOpen = p.wheelOpen();
        page = p.page();
        copied = new ArrayList<>(p.copied());
        selected = p.selected();
        readyAt = new ArrayList<>(p.readyAt());
        ladderHits = p.ladderHits();
        ladderNeeded = Math.max(1, p.ladderNeeded());
        outburstStage = p.outburstStage();
    }

    public static void reset() {
        rikaId = -1;
        rikaMode = wheelOpen = false;
        page = 0;
        copied = new ArrayList<>(List.of(Copies.SPEECH));
        selected = Copies.SPEECH;
        readyAt = new ArrayList<>();
        ladderHits = 0;
        outburstStage = -1;
    }

    static boolean active() {
        return "yuta".equals(ClientState.character);
    }

    public static void render(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h, float partial) {
        if (!active() || mc.level == null) return;
        long now = mc.level.getGameTime();
        float time = now + partial;
        if (rikaMode) ringGlow(g, font, w, h, time);
        if (outburstStage >= 0) outburstBar(g, w, h, time);
        if (now - newestAt < 80 && !newest.isEmpty()) newestCopy(g, font, w, h, now - newestAt + partial);
        if (wheelOpen) wheel(g, font, w, h, now, time);
    }

    /** The ring's faint glow: Rika's moveset is up (her keys, his movement flies her). */
    private static void ringGlow(GuiGraphicsExtractor g, Font font, int w, int h, float time) {
        float a = 0.55f + 0.25f * Mth.sin(time * 0.15f);
        int al = Math.round(a * 255) << 24;
        int cx = w / 2, y = h / 2 + 40;
        g.fillGradient(cx - 46, y - 1, cx + 46, y + 11, al | 0x2A0A30, al | 0x14041A);
        g.fill(cx - 46, y - 1, cx + 46, y, al | 0xF76BFF);
        smallText(g, font, "RIKA", cx, y + 2, 0.75f, al | 0xF7C8FF);
    }

    /** Outburst's bar, beside him: three segments filling as the burst grows. */
    private static void outburstBar(GuiGraphicsExtractor g, int w, int h, float time) {
        int x = w / 2 + 22, y = h / 2 - 16;
        for (int i = 0; i < 3; i++) {
            int y0 = y + (2 - i) * 11;
            boolean lit = outburstStage > i;
            boolean full = outburstStage >= 3;
            int col = lit ? (full ? pulse(0xFFFFFFFF, 0xFFF76BFF, time) : 0xFFF76BFF) : 0x50303040;
            g.fill(x - 1, y0 - 1, x + 6, y0 + 10, 0xA0000000);
            g.fill(x, y0, x + 5, y0 + 9, col);
        }
    }

    private static void newestCopy(GuiGraphicsExtractor g, Font font, int w, int h, float age) {
        Copies.Technique t = Copies.ALL.get(newest);
        if (t == null) return;
        float a = age < 6 ? age / 6f : age > 65 ? 1 - (age - 65) / 15f : 1f;
        int al = Math.round(Mth.clamp(a, 0, 1) * 255) << 24;
        int x = w / 2 + 60, y = h / 2 + 20;
        smallText(g, font, "COPIED", x, y, 0.6f, al | 0xF7C8FF);
        smallText(g, font, t.name().toUpperCase(java.util.Locale.ROOT), x, y + 7, 0.9f, al | 0xFFFFFF);
    }

    /** The wheel: four techniques of the open page around the crosshair. */
    private static void wheel(GuiGraphicsExtractor g, Font font, int w, int h, long now, float time) {
        int cx = w / 2, cy = h / 2;
        float open = Mth.clamp((now - wheelChangedAt + 1) / 4f, 0, 1);
        float r = 46 * (0.6f + 0.4f * open);
        AbilitySlot[] keys = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4};
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x80FFFFFF);
        for (int i = 0; i < CopyWheelAbility.PER_PAGE; i++) {
            int index = page * CopyWheelAbility.PER_PAGE + i;
            double ang = -Math.PI / 2 + i * Math.PI / 2;
            int x = cx + (int) Math.round(Math.cos(ang) * r), y = cy + (int) Math.round(Math.sin(ang) * r);
            boolean has = index < copied.size();
            String id = has ? copied.get(index) : "";
            boolean sel = has && id.equals(selected);
            long ready = has && index < readyAt.size() ? readyAt.get(index) : 0;
            int left = (int) Math.max(0, ready - now);
            int bw = 64, bh = 20;
            int frame = sel ? pulse(0xFFF76BFF, 0xFFFFFFFF, time) : has ? 0xFF8A5C9A : 0xFF303040;
            g.fill(x - bw / 2 - 1, y - bh / 2 - 1, x + bw / 2 + 1, y + bh / 2 + 1, frame);
            g.fillGradient(x - bw / 2, y - bh / 2, x + bw / 2, y + bh / 2, 0xE01A0A22, 0xE00A0410);
            if (has) {
                Copies.Technique t = Copies.ALL.get(id);
                String name = t != null ? t.name() : id;
                smallText(g, font, name.toUpperCase(java.util.Locale.ROOT), x, y - 6, 0.6f, 0xFFFFFFFF);
                String sub = left > 0 ? String.format("%.0fs", left / 20f) : t != null ? t.move() : "";
                smallText(g, font, sub, x, y + 2, 0.5f, left > 0 ? 0xFFFFE08A : 0xFFC8B0D8);
            }
            String key = InputHandler.keyLabel(keys[i]);
            if (!key.isEmpty()) smallText(g, font, key, x - bw / 2 + 4, y - bh / 2 + 2, 0.5f, 0xFFE8EEF8);
        }
        String pageKey = InputHandler.keyLabel(AbilitySlot.SKILL_5);
        smallText(g, font, "PAGE " + (page + 1) + "/" + CopyWheelAbility.PAGES + (pageKey.isEmpty() ? "" : "  [" + pageKey + "]"), cx, cy + r + 18, 0.6f, 0xFFF7C8FF);
    }

    /** Jacob's Ladder's progress, for the domain's box. */
    public static String ladderLabel() {
        return Math.min(ladderHits, ladderNeeded) + "/" + ladderNeeded;
    }

    public static boolean ladderReady() {
        return ladderHits >= ladderNeeded;
    }

    private static int pulse(int a, int b, float time) {
        float t = 0.5f + 0.5f * Mth.sin(time * 0.4f);
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | Math.round(Mth.lerp(t, ar, br)) << 16 | Math.round(Mth.lerp(t, ag, bg)) << 8 | Math.round(Mth.lerp(t, ab, bb));
    }

    private static void smallText(GuiGraphicsExtractor g, Font font, String text, float x, float y, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, text, -font.width(text) / 2, 0, color, true);
        pose.popMatrix();
    }
}
