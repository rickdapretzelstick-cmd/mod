package dev.rick.jjk.client;

import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.net.ProgressionPayload;
import dev.rick.jjk.progression.CursePerception;
import dev.rick.jjk.progression.TechniqueProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the server says about this player's Survival progression: whether the character select screen may hand out kits,
 * the kits they own, and whether they can perceive curses. Only shapes what is shown; the server decides everything.
 */
public final class ClientProgression {
    private static boolean governed;
    private static String kit = "";
    private static List<String> owned = List.of();
    private static boolean perceive;

    private ClientProgression() {}

    public static void apply(ProgressionPayload p) {
        governed = p.governed();
        kit = p.kit();
        owned = p.owned();
        perceive = p.perceive();
    }

    public static void reset() {
        governed = false;
        kit = "";
        owned = List.of();
        perceive = false;
    }

    /** Survival with progression on: kits come from the world, not from the select screen. */
    public static boolean governed() {
        return governed;
    }

    public static List<String> owned() {
        return owned;
    }

    public static boolean perceivesCurses() {
        return perceive;
    }

    /** Whether this client draws {@code entity}: curses that need perception only for a player who has it. */
    public static boolean canSee(Entity entity) {
        return perceive || !CursePerception.requiresPerception(entity);
    }

    /**
     * The K key / "Choose Character...": the full screen in Creative (or with progression off); in Survival, a choice
     * only between kits the player has earned, and a quiet line when there is nothing to choose.
     */
    public static void openCharacterSelect(@Nullable Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (!governed || owned.size() > 1) {
            mc.gui.setScreen(new dev.rick.jjk.client.hud.CharacterSelectScreen(parent));
            return;
        }
        if (mc.player == null) return;
        if (owned.isEmpty()) {
            mc.player.sendOverlayMessage(Component.literal(TechniqueProgression.NOT_AWAKENED).withStyle(ChatFormatting.GRAY));
            return;
        }
        JJKCharacter c = Characters.get(kit.isEmpty() ? owned.get(0) : kit);
        mc.player.sendOverlayMessage(Component.literal(c == null ? "Your cursed technique is your own." : "Your cursed technique: " + c.displayName()
                + (c.title().isEmpty() ? "" : " — " + c.title())).withStyle(ChatFormatting.GRAY));
    }
}
