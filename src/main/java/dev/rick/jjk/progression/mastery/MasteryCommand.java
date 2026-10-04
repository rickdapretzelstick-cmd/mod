package dev.rick.jjk.progression.mastery;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /jjk mastery}: an operator's view of and hand on a player's Mastery.
 * <ul>
 *   <li>{@code info <player>}: points, purchases, exorcisms and fatigue per tree;</li>
 *   <li>{@code give <player> <tree> <amount>}: Mastery straight into a tree (no ownership check bypass: a technique tree
 *   still takes Mastery only for its owner);</li>
 *   <li>{@code buy <player> <tree> <node>}: a purchase as if the player clicked it (same rules);</li>
 *   <li>{@code respec <player> <tree>}: refunds a tree.</li>
 * </ul>
 */
public final class MasteryCommand {
    private static final SuggestionProvider<CommandSourceStack> TREES = (c, b) ->
            SharedSuggestionProvider.suggest(MasteryTrees.all().stream().map(MasteryTree::id), b);
    private static final SuggestionProvider<CommandSourceStack> NODES = (c, b) -> {
        MasteryTree t = MasteryTrees.get(tree(c));
        return t == null ? b.buildFuture() : SharedSuggestionProvider.suggest(t.nodes().stream().map(MasteryNode::id), b);
    };

    private MasteryCommand() {}

    /** The tree argument: {@code technique/gojo}, {@code tool/slaughter_demon} (read as an id so the slash is allowed). */
    private static String tree(CommandContext<CommandSourceStack> c) {
        return net.minecraft.commands.arguments.IdentifierArgument.getId(c, "tree").getPath();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("mastery")
                .then(Commands.literal("info").then(Commands.argument("player", EntityArgument.player()).executes(MasteryCommand::info)))
                .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tree", net.minecraft.commands.arguments.IdentifierArgument.id()).suggests(TREES)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000)).executes(MasteryCommand::give)))))
                .then(Commands.literal("buy").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tree", net.minecraft.commands.arguments.IdentifierArgument.id()).suggests(TREES)
                                .then(Commands.argument("node", StringArgumentType.word()).suggests(NODES).executes(MasteryCommand::buy)))))
                .then(Commands.literal("respec").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tree", net.minecraft.commands.arguments.IdentifierArgument.id()).suggests(TREES).executes(MasteryCommand::respec))));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        MasteryData d = Mastery.data(p);
        StringBuilder sb = new StringBuilder(p.getName().getString()).append(" — ").append(SorcererGrade.of(d.grade()).display)
                .append(Mastery.gated(p) ? " (gated)" : " (ungated: Creative or progression off)");
        for (MasteryTree t : MasteryTrees.all()) {
            int pts = d.points(t.id());
            int n = d.bought(t.id()).size();
            if (pts == 0 && n == 0 && d.earned().getOrDefault(t.id(), 0) == 0) continue;
            sb.append("\n  ").append(t.id()).append(": ").append(pts).append(" Mastery, ").append(n).append("/").append(t.nodes().size())
                    .append(" nodes ").append(d.bought(t.id()));
        }
        sb.append("\n  exorcised ").append(d.exorcised()).append(", investigations ").append(d.incidents()).append(", fatigue ").append(d.fatigue());
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        String tree = tree(c);
        int amount = IntegerArgumentType.getInteger(c, "amount");
        MasteryTree t = MasteryTrees.get(tree);
        if (t == null) {
            c.getSource().sendFailure(Component.literal("Unknown tree " + tree));
            return 0;
        }
        if (!Mastery.mayDevelop(p, t)) {
            c.getSource().sendFailure(Component.literal(p.getName().getString() + " doesn't own the " + t.owner() + " kit, so can't develop " + tree));
            return 0;
        }
        Mastery.award(p, tree, amount);
        c.getSource().sendSuccess(() -> Component.literal("+" + amount + " " + tree + " Mastery to " + p.getName().getString()), true);
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        Mastery.Result r = Mastery.purchase(p, tree(c), StringArgumentType.getString(c, "node"));
        if (r == Mastery.Result.OK) c.getSource().sendSuccess(() -> Component.literal("Bought."), true);
        else c.getSource().sendFailure(Component.literal("Not bought: " + r));
        return r == Mastery.Result.OK ? 1 : 0;
    }

    private static int respec(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        Mastery.respec(p, tree(c));
        c.getSource().sendSuccess(() -> Component.literal("Refunded."), true);
        return 1;
    }
}
