package dev.rick.jjk.client.hud;

import dev.rick.jjk.JJK;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.net.CharacterSelectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one character select screen. It builds a card for every registered character from the character's own identity
 * (name, title, description) and theme, with its portrait from {@code textures/gui/portrait/<id>.png}, so adding a
 * character never means touching this screen. The server decides whether the switch is allowed (neutral state only) and
 * says why not.
 */
public class CharacterSelectScreen extends Screen {
    @Nullable private final Screen parent;
    private final List<JJKCharacter> characters = new ArrayList<>();
    private int hovered = -1;
    private int selectedAt = -1;
    private long openedAt;

    public CharacterSelectScreen(@Nullable Screen parent) {
        super(Component.literal("Choose Your Sorcerer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        characters.clear();
        // In Survival (progression on) only the kits this player has earned are offered.
        boolean governed = dev.rick.jjk.client.ClientProgression.governed();
        for (String id : Characters.ids()) {
            if (!governed || dev.rick.jjk.client.ClientProgression.owned().contains(id)) characters.add(Characters.get(id));
        }
        openedAt = System.currentTimeMillis();
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 60, height - 28, 120, 20).build());
    }

    private int cardW() {
        return Math.min(150, (width - 40) / Math.max(1, characters.size()) - 12);
    }

    private int cardH() {
        return Math.min(220, height - 64);
    }

    private int cardX(int i) {
        int cw = cardW(), total = characters.size() * (cw + 12) - 12;
        return width / 2 - total / 2 + i * (cw + 12);
    }

    private int cardY() {
        return 28;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = cardAt(event.x(), event.y());
        if (i >= 0) {
            JJKCharacter c = characters.get(i);
            if (!c.id.equals(ClientState.character)) ClientPlayNetworking.send(new CharacterSelectPayload(c.id));
            selectedAt = i;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Where a character's card is (GUI coordinates), or null. */
    public int @Nullable [] cardCenter(String id) {
        for (int i = 0; i < characters.size(); i++) {
            if (characters.get(i).id.equals(id)) return new int[] {cardX(i) + cardW() / 2, cardY() + cardH() / 2};
        }
        return null;
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < characters.size(); i++) {
            int x = cardX(i), y = cardY();
            if (mx >= x && mx < x + cardW() && my >= y && my < y + cardH()) return i;
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xC0080810, 0xE0101018);
        hovered = cardAt(mouseX, mouseY);
        float time = (System.currentTimeMillis() - openedAt) / 50f;
        CinematicPanels.labelCentered(g, font, "CHOOSE YOUR SORCERER", width / 2f, 12, 1.4f, 0xFFFFFFFF, 1f);
        for (int i = 0; i < characters.size(); i++) card(g, i, time);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void card(GuiGraphicsExtractor g, int i, float time) {
        JJKCharacter c = characters.get(i);
        CharacterTheme theme = CharacterTheme.of(c.id);
        int x = cardX(i), y = cardY(), cw = cardW(), ch = cardH();
        boolean current = c.id.equals(ClientState.character);
        boolean hover = hovered == i;
        // Slide in, staggered.
        float in = Mth.clamp((time - i * 3) / 6f, 0, 1);
        int dy = Math.round((1 - in) * 30);
        y += dy - (hover ? 3 : 0);
        int accent = theme.accent();
        // Card: dark body with the character's colour washed up from the bottom, a slanted title band, a frame.
        g.fill(x - 2, y - 2, x + cw + 2, y + ch + 2, current ? accent : hover ? 0xFFFFFFFF : 0xFF2A2A34);
        g.fillGradient(x, y, x + cw, y + ch, 0xFF0C0C12, (theme.ceBottom() & 0xFFFFFF) | 0x90000000);
        // Portrait.
        Identifier portrait = JJK.id("textures/gui/portrait/" + c.id + ".png");
        // The portrait takes what the text below it leaves (name, title, a few description lines, the tag).
        int ps = Mth.clamp(Math.min(cw - 24, ch - 100), 32, 96);
        int px = x + cw / 2 - ps / 2, py = y + 10;
        g.fill(px - 2, py - 2, px + ps + 2, py + ps + 2, 0xFF000000);
        g.fillGradient(px, py, px + ps, py + ps, (theme.ceTop() & 0xFFFFFF) | 0x50000000, 0xFF101018);
        g.blit(RenderPipelines.GUI_TEXTURED, portrait, px, py, 0, 0, ps, ps, 32, 32, 32, 32, 0xFFFFFFFF);
        // Name, title, description.
        int ty = py + ps + 8;
        CinematicPanels.labelCentered(g, font, c.displayName().toUpperCase(java.util.Locale.ROOT), x + cw / 2f, ty, 1.6f, 0xFFFFFFFF, in);
        CinematicPanels.labelCentered(g, font, c.title(), x + cw / 2f, ty + 16, 0.9f, accent, in);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(c.description()), cw - 12);
        int ly = ty + 30;
        var pose = g.pose();
        for (var line : lines) {
            if (ly > y + ch - 22) break;
            pose.pushMatrix();
            pose.translate(x + 6, ly);
            pose.scale(0.8f, 0.8f);
            g.text(font, line, 0, 0, 0xFFC8C8D4, false);
            pose.popMatrix();
            ly += 8;
        }
        // Selection indicator.
        String tag = current ? "SELECTED" : hover ? "CLICK TO PLAY" : "";
        if (!tag.isEmpty()) {
            g.fill(x, y + ch - 16, x + cw, y + ch, current ? accent : 0xC0000000);
            CinematicPanels.labelCentered(g, font, tag, x + cw / 2f, y + ch - 12, 0.8f, 0xFFFFFFFF, 1f);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
