package dev.rick.jjk.client.training;

import dev.rick.jjk.progression.training.TrainingNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The training's two small screens: the relic's offer, and Retry/Leave after a failed attempt. */
public final class TrainingScreens {
    private TrainingScreens() {}

    /** "Begin Training" (or "Practice Again" once the character is yours) / "Not now". */
    public static final class OfferScreen extends Screen {
        private final TrainingNet.Offer offer;
        private boolean answered;

        public OfferScreen(TrainingNet.Offer offer) {
            super(Component.literal(offer.name()));
            this.offer = offer;
        }

        @Override
        protected void init() {
            int bw = 180, x = width / 2 - bw / 2, y = height / 2 + 10;
            addRenderableWidget(Button.builder(Component.literal(offer.practice() ? "Practice Again" : "Begin Training"), b -> answer(true))
                    .bounds(x, y, bw, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Not now"), b -> answer(false)).bounds(x, y + 24, bw, 20).build());
        }

        private void answer(boolean accept) {
            if (!answered) {
                answered = true;
                ClientPlayNetworking.send(new TrainingNet.Answer(offer.kit(), accept));
            }
            minecraft.gui.setScreen(null);
        }

        @Override
        public void onClose() {
            answer(false);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            int y = height / 2 - 40;
            g.centeredText(font, offer.name().toUpperCase(java.util.Locale.ROOT), width / 2, y, offer.color() | 0xFF000000);
            g.centeredText(font, offer.practice() ? "Practice: nothing new is earned" : "Training: its first success unlocks the base kit", width / 2, y + 13, 0xFFB8C0D0);
            g.centeredText(font, offer.line(), width / 2, y + 30, 0xFFE8E8F0);
        }
    }

    /** After a failed attempt (or a lost connection): Retry (the relic is not consumed) or Leave. */
    public static final class ResultScreen extends Screen {
        private final boolean syncLost;

        public ResultScreen(boolean syncLost) {
            super(Component.literal("Training"));
            this.syncLost = syncLost;
        }

        @Override
        protected void init() {
            int bw = 160, x = width / 2 - bw / 2, y = height / 2 + 6;
            addRenderableWidget(Button.builder(Component.literal("Retry"), b -> {
                TrainingClient.retry();
                minecraft.gui.setScreen(null);
            }).bounds(x, y, bw, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Leave"), b -> {
                TrainingClient.leave();
                minecraft.gui.setScreen(null);
            }).bounds(x, y + 24, bw, 20).build());
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            int y = height / 2 - 34;
            g.centeredText(font, syncLost ? "CONNECTION UNSTABLE" : "✘ TRAINING FAILED", width / 2, y, 0xFFFF6A6A);
            g.centeredText(font, syncLost ? "Nothing counted. Try again when ready." : "The relic is unharmed and still yours to use.", width / 2, y + 14, 0xFFD0D6E0);
        }
    }
}
