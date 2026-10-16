package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.CombatMode;
import dev.rick.jjk.config.JJKConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * JJK settings, opened from the pause menu: Combat Mode (ON / VANILLA) and a few HUD preferences. Saved to the config
 * file straight away.
 */
public class JJKSettingsScreen extends Screen {
    @Nullable private final Screen parent;

    public JJKSettingsScreen(@Nullable Screen parent) {
        super(Component.literal("JJK Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bw = 220, x = width / 2 - bw / 2, y = height / 4 + 10;
        addRenderableWidget(Button.builder(modeText(), b -> {
            CombatMode.toggle();
            b.setMessage(modeText());
        }).bounds(x, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Choose Character..."), b -> dev.rick.jjk.client.ClientProgression.openCharacterSelect(this))
                .bounds(x, y + 24, bw, 20).build());
        addRenderableWidget(Button.builder(toggleText("CE numbers", JJKConfig.get().client.showCeNumbers), b -> {
            JJKConfig.get().client.showCeNumbers = !JJKConfig.get().client.showCeNumbers;
            JJKConfig.save();
            b.setMessage(toggleText("CE numbers", JJKConfig.get().client.showCeNumbers));
        }).bounds(x, y + 52, bw, 20).build());
        addRenderableWidget(Button.builder(toggleText("Combo counter", JJKConfig.get().client.showComboCounter), b -> {
            JJKConfig.get().client.showComboCounter = !JJKConfig.get().client.showComboCounter;
            JJKConfig.save();
            b.setMessage(toggleText("Combo counter", JJKConfig.get().client.showComboCounter));
        }).bounds(x, y + 76, bw, 20).build());
        addRenderableWidget(Button.builder(toggleText("Reduced camera motion", JJKConfig.get().client.reducedCameraMotion), b -> {
            JJKConfig.get().client.reducedCameraMotion = !JJKConfig.get().client.reducedCameraMotion;
            JJKConfig.save();
            b.setMessage(toggleText("Reduced camera motion", JJKConfig.get().client.reducedCameraMotion));
        }).bounds(x, y + 100, bw, 20).build());
        addRenderableWidget(Button.builder(toggleText("Impact frames", JJKConfig.get().client.impactFrames), b -> {
            JJKConfig.get().client.impactFrames = !JJKConfig.get().client.impactFrames;
            JJKConfig.save();
            b.setMessage(toggleText("Impact frames", JJKConfig.get().client.impactFrames));
        }).bounds(x, y + 124, bw, 20).build());
        int half = (bw - 4) / 2;
        addRenderableWidget(Button.builder(toggleText("Reduced flashes", JJKConfig.get().client.reducedFlashes), b -> {
            JJKConfig.get().client.reducedFlashes = !JJKConfig.get().client.reducedFlashes;
            JJKConfig.save();
            b.setMessage(toggleText("Reduced flashes", JJKConfig.get().client.reducedFlashes));
        }).bounds(x, y + 148, half, 20).build());
        addRenderableWidget(Button.builder(toggleText("Reduced shake", JJKConfig.get().client.reducedShake), b -> {
            JJKConfig.get().client.reducedShake = !JJKConfig.get().client.reducedShake;
            JJKConfig.save();
            b.setMessage(toggleText("Reduced shake", JJKConfig.get().client.reducedShake));
        }).bounds(x + bw - half, y + 148, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y + 182, bw, 20).build());
    }

    private static Component modeText() {
        return CombatMode.enabled()
                ? Component.literal("Combat Mode: ").append(Component.literal("ON").withStyle(ChatFormatting.AQUA))
                : Component.literal("Combat Mode: ").append(Component.literal("VANILLA").withStyle(ChatFormatting.GRAY));
    }

    private static Component toggleText(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int y = height / 4 - 14;
        g.centeredText(font, title, width / 2, y, 0xFFFFFFFF);
        String hint = CombatMode.enabled()
                ? "The JJK HUD and ability keys are active."
                : "Vanilla mode: JJK HUD hidden and ability keys disabled.";
        g.centeredText(font, hint, width / 2, height / 4 + 170 + 40, 0xFFA0A8B8);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
