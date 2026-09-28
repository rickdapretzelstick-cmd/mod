package dev.rick.jjk.client;

import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.config.JJKConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Combat mode versus Vanilla Minecraft mode. In vanilla mode the mod stays out of the way completely: no custom HUD, and
 * none of its ability keys, melee or clash inputs are sent, so building and mining can't set off a technique. The choice
 * is stored in the client config ({@code client.combatMode}) and survives respawns, rejoins and restarts.
 */
public final class CombatMode {
    private CombatMode() {}

    public static boolean enabled() {
        return JJKConfig.get().client.combatMode;
    }

    public static void set(boolean on) {
        JJKConfig.Client cfg = JJKConfig.get().client;
        if (cfg.combatMode == on) return;
        cfg.combatMode = on;
        JJKConfig.save();
        Minecraft mc = Minecraft.getInstance();
        // Let go of anything being held so a charge doesn't keep going after the switch.
        InputHandler.releaseAll(mc.player);
        if (mc.player != null) {
            mc.player.sendOverlayMessage(on
                    ? Component.literal("JJK Combat Mode: ON").withStyle(ChatFormatting.AQUA)
                    : Component.literal("Vanilla Minecraft Mode: JJK abilities and HUD off").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void toggle() {
        set(!enabled());
    }
}
