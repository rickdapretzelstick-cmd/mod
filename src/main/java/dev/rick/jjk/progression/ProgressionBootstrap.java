package dev.rick.jjk.progression;

/**
 * Set-up for what is left of the old survival progression: the cursed tools (items and their movesets). The rest of it,
 * the news board, contracts, curses, cursed realms, relics and trials, mastery, finishers and contests, is archived
 * under archive/survival (see its README).
 */
public final class ProgressionBootstrap {
    private ProgressionBootstrap() {}

    public static void init() {
        ProgressionItems.init();
        dev.rick.jjk.progression.tool.CursedTools.init();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dispatcher.register(
                net.minecraft.commands.Commands.literal("jjk").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(dev.rick.jjk.progression.tool.rifle.RifleCommand.node())));
    }
}
