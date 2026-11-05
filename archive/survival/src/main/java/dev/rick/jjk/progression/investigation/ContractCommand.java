package dev.rick.jjk.progression.investigation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Contracts, for players:
 * <pre>
 * /contract clearance      your News clearance and progress toward the next grade
 * /contract rematch        straight back into the Grade 1 contract you just lost (same guardian; nothing to pay)
 * </pre>
 * and for operators: {@code /contract set <player> <grade>}, {@code /contract reset <player>}.
 */
public final class ContractCommand {
    private ContractCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("contract")
                .then(Commands.literal("clearance").executes(ContractCommand::clearance))
                .then(Commands.literal("rematch").executes(ContractCommand::rematch))
                .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("grade", IntegerArgumentType.integer(1, 4))
                                .executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    Clearance.set(p, IntegerArgumentType.getInteger(c, "grade"));
                                    c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": " + Clearance.name(Clearance.access(p)) + " clearance"), true);
                                    return 1;
                                }))))
                .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            Clearance.reset(p);
                            c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": clearance reset"), true);
                            return 1;
                        }))));
    }

    private static int clearance(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        c.getSource().sendSuccess(() -> Component.literal(Clearance.name(Clearance.access(p)) + " clearance. " + Clearance.progress(p) + ".")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /** The open Grade 1 contract this player is the challenger of, or null. */
    @org.jetbrains.annotations.Nullable
    static Incident challengeOf(ServerPlayer p) {
        for (Incident in : InvestigationState.get(p.level().getServer()).incidents.values()) {
            if (in.grade == dev.rick.jjk.progression.grade.CurseGrade.GRADE_1 && p.getUUID().equals(in.challenger) && in.state == Incident.State.OPEN) return in;
        }
        return null;
    }

    private static int rematch(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!rematch(p)) {
            c.getSource().sendFailure(Component.literal("No lost guardian contract to return to right now."));
            return 0;
        }
        return 1;
    }

    /** Back into the same Grade 1 contract after losing it: same guardian, nothing consumed. */
    public static boolean rematch(ServerPlayer p) {
        Incident in = challengeOf(p);
        if (in == null || !p.isAlive() || CursedRealms.inRealm(p) || CursedRealms.pulling(p)) return false;
        IncidentTemplate t = in.def();
        ServerLevel level = p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(in.dimension)));
        if (t == null || level == null) return false;
        InvestigationState st = InvestigationState.get(p.level().getServer());
        Investigations.begin(level, p, in, t, st, level.getServer().overworld().getGameTime());
        return true;
    }

    /** The prompt after a lost guardian fight: a clickable rematch. */
    public static void offerRematch(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("The guardian waits for you, and so does its contract. ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[Rematch]").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/contract rematch")))));
    }
}
