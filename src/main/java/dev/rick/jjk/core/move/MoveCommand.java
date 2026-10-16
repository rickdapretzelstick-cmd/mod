package dev.rick.jjk.core.move;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /jjkmove: the move editor's moves in game. {@code list}, {@code reload} (re-reads config/jjk/moves),
 * {@code bind <key> <move>} (1-5, r, g, q, f), {@code unbind <key>}, {@code play <move>}, {@code folder}.
 */
public final class MoveCommand {
    private static final List<String> KEYS = List.of("1", "2", "3", "4", "5", "r", "g", "q", "f");

    private MoveCommand() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> MoveLibrary.reload());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MoveLibrary.sync(handler.player);
            MoveLibrary.apply(handler.player);
        });
        CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> register(d));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjkmove")
                .then(Commands.literal("list").executes(MoveCommand::list))
                .then(Commands.literal("folder").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Put move files from the editor in: " + MoveLibrary.dir().toAbsolutePath()), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(c -> {
                    int n = MoveLibrary.reload();
                    MoveLibrary.syncAll(c.getSource().getServer());
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) MoveLibrary.apply(p);
                    c.getSource().sendSuccess(() -> Component.literal(n + " move(s) loaded"
                            + (MoveLibrary.errors().isEmpty() ? "" : "; problems: " + String.join(" | ", MoveLibrary.errors()))), false);
                    return n;
                }))
                .then(Commands.literal("bind")
                        .then(Commands.argument("key", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(KEYS, b))
                                .then(Commands.argument("move", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(MoveLibrary.all().keySet(), b))
                                        .executes(c -> bind(c, StringArgumentType.getString(c, "move"))))))
                .then(Commands.literal("unbind")
                        .then(Commands.argument("key", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(KEYS, b))
                                .executes(c -> bind(c, null))))
                .then(Commands.literal("play")
                        .then(Commands.argument("move", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MoveLibrary.all().keySet(), b))
                                .executes(MoveCommand::play))));
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var b = MoveLibrary.bindingsOf(p);
        StringBuilder s = new StringBuilder(MoveLibrary.all().size() + " move(s):");
        for (Move m : MoveLibrary.all().values()) {
            s.append("\n  ").append(m.id()).append(" (").append(m.name()).append(", ").append(m.ticks() * 50).append(" ms)");
            for (var e : b.entrySet()) if (e.getValue().equals(m.id())) s.append(" on ").append(e.getKey());
        }
        c.getSource().sendSuccess(() -> Component.literal(s.toString()), false);
        return MoveLibrary.all().size();
    }

    private static int bind(CommandContext<CommandSourceStack> c, String move) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        AbilitySlot slot = MoveLibrary.slot(StringArgumentType.getString(c, "key"));
        if (slot == null) {
            c.getSource().sendFailure(Component.literal("Keys: 1-5, r (heavy), g (ultimate), q (dash), f (guard)"));
            return 0;
        }
        if (move != null && MoveLibrary.get(move) == null) {
            c.getSource().sendFailure(Component.literal("No move '" + move + "' (try /jjkmove reload, then /jjkmove list)"));
            return 0;
        }
        MoveLibrary.bind(p, slot, move);
        c.getSource().sendSuccess(() -> Component.literal(move == null ? "Key back to your character's move" : move + " is on that key now"), false);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Move m = MoveLibrary.get(StringArgumentType.getString(c, "move"));
        if (m == null) {
            c.getSource().sendFailure(Component.literal("No such move"));
            return 0;
        }
        var caster = Casters.get(p);
        caster.begin(new MoveAbility(m).activate(new AbilityContext(caster, p, (net.minecraft.server.level.ServerLevel) p.level(), AbilitySlot.SKILL_1, 0, 0, null)));
        return 1;
    }
}
