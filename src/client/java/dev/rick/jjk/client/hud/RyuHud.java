package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.core.net.RyuPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * True Cannon's Overheat meter (JJS): a bar above the hotbar that fills as he discharges, running from cool blue through
 * orange to red; at 100% it flashes OVERHEATED (his discharges are shut off until he Restyles). Under Decadence it is
 * locked full and burns gold.
 */
public final class RyuHud {
    public static float heat = 0, shown = 0;
    public static boolean decadence;
    private static boolean known;

    private RyuHud() {}

    public static void apply(RyuPayload p) {
        if (p.heat() < 0) {
            // No longer True Cannon: his Decadence music stops with him.
            dev.rick.jjk.client.fx.ClientFx.stopSound("ryu_music");
            reset();
            return;
        }
        heat = p.heat();
        decadence = p.decadence();
        known = true;
    }

    public static void reset() {
        heat = shown = 0;
        decadence = false;
        known = false;
    }

    public static void render(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h, float partial) {
        if (!"ryu".equals(ClientState.character) || !known || mc.level == null) return;
        shown += (heat - shown) * 0.2f;
        long now = mc.level.getGameTime();
        int bw = 120, bh = 6;
        int x = w / 2 - bw / 2, y = h - 92;
        float f = Mth.clamp(shown / 100f, 0, 1);
        boolean over = heat >= 99.9f;
        g.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, 0xC0000000);
        int from = decadence ? 0xFFFFE08A : 0xFF5AB4FF;
        int to = decadence ? 0xFFFFB040 : f > 0.8f ? 0xFFFF3A2A : f > 0.5f ? 0xFFFF9A3A : 0xFF8AD8FF;
        if (over && !decadence && (now / 4) % 2 == 0) to = from = 0xFFFFFFFF;
        g.fillGradient(x, y, x + Math.round(bw * f), y + bh, from, to);
        // The 80% mark: firing Every Last Drop from here on awakens him.
        int mark = x + Math.round(bw * 0.8f);
        g.fill(mark, y - 2, mark + 1, y + bh + 2, 0xA0FFFFFF);
        String label = decadence ? "DECADENCE" : over ? "OVERHEATED" : "OVERHEAT " + Math.round(shown) + "%";
        int col = decadence ? 0xFFFFD88A : over ? 0xFFFF5A4A : 0xFFD8ECFF;
        g.centeredText(font, label, w / 2, y - 11, col);
    }
}
