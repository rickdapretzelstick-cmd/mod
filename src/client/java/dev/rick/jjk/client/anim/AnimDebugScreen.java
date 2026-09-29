package dev.rick.jjk.client.anim;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * The animation debugger's control panel. It doesn't pause the game or blur the world, so the clip keeps playing (or
 * stays frozen) in full view behind the buttons.
 */
public class AnimDebugScreen extends Screen {
    private EditBox clip;
    private Button pause;
    private Button skeleton;
    private List<String> matches = List.of();

    public AnimDebugScreen() {
        super(Component.literal("Animation Debugger"));
    }

    @Override
    protected void init() {
        int y = height - 54, x = 6, bw = 52, gap = 4;
        clip = new EditBox(font, x, y - 26, 220, 18, Component.literal("clip"));
        clip.setHint(Component.literal("clip name, e.g. rapid_barrage"));
        clip.setResponder(s -> matches = s.isEmpty() ? List.of() : AnimLibrary.names().stream().filter(n -> n.contains(s)).limit(8).toList());
        addRenderableWidget(clip);
        addRenderableWidget(Button.builder(Component.literal("Play"), b -> playTyped()).bounds(x + 224, y - 26, 44, 18).build());

        pause = addRenderableWidget(Button.builder(pauseText(), b -> {
            AnimDebug.togglePause();
            b.setMessage(pauseText());
        }).bounds(x, y, bw, 20).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.literal("Restart"), b -> AnimDebug.restart()).bounds(x, y, bw, 20).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.literal("< Frame"), b -> {
            AnimDebug.step(-1);
            pause.setMessage(pauseText());
        }).bounds(x, y, bw, 20).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.literal("Frame >"), b -> {
            AnimDebug.step(1);
            pause.setMessage(pauseText());
        }).bounds(x, y, bw, 20).build());
        x += bw + gap;
        for (float s : new float[]{0.25f, 0.5f, 1f, 2f}) {
            addRenderableWidget(Button.builder(Component.literal(String.format(Locale.ROOT, "%sx", s == (int) s ? String.valueOf((int) s) : String.valueOf(s))),
                    b -> AnimDebug.setSpeed(s)).bounds(x, y, 36, 20).build());
            x += 36 + gap;
        }
        x = 6;
        y += 24;
        skeleton = addRenderableWidget(Button.builder(skeletonText(), b -> {
            AnimDebug.skeleton = !AnimDebug.skeleton;
            b.setMessage(skeletonText());
        }).bounds(x, y, 80, 20).build());
        x += 84;
        addRenderableWidget(Button.builder(Component.literal("Axes"), b -> {
            AnimDebug.skeleton = true;
            AnimDebug.axes = !AnimDebug.axes;
            skeleton.setMessage(skeletonText());
        }).bounds(x, y, 44, 20).build());
        x += 48;
        addRenderableWidget(Button.builder(Component.literal("Names"), b -> AnimDebug.names = !AnimDebug.names).bounds(x, y, 48, 20).build());
        x += 52;
        addRenderableWidget(Button.builder(Component.literal("Target"), b -> AnimDebug.retarget()).bounds(x, y, 52, 20).build());
        x += 56;
        addRenderableWidget(Button.builder(Component.literal("Reload"), b -> AnimLibrary.reload(minecraft.getResourceManager())).bounds(x, y, 52, 20).build());
        x += 56;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x, y, 52, 20).build());
    }

    private void playTyped() {
        String name = clip.getValue().trim();
        if (AnimLibrary.get(name) == null && matches.size() == 1) name = matches.getFirst();
        if (AnimLibrary.get(name) != null) {
            AnimDebug.paused = false;
            pause.setMessage(pauseText());
            AnimDebug.play(name);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && clip.isFocused()) {
            playTyped();
            return true;
        }
        return super.keyPressed(event);
    }

    private static Component pauseText() {
        return Component.literal(AnimDebug.paused ? "Play" : "Pause");
    }

    private static Component skeletonText() {
        return Component.literal("Skeleton: " + (AnimDebug.skeleton ? "ON" : "OFF"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // No blur or dimming: the point is to watch the model.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int y = clip.getY() - 12 - matches.size() * 10;
        for (String m : matches) {
            g.text(font, m, clip.getX() + 2, y, 0xFFB0E0FF, true);
            y += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
