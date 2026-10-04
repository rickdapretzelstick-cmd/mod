package dev.rick.jjk.progression.investigation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

/**
 * {@code /jjk incident}: an operator's view of investigations.
 * <ul>
 *   <li>{@code list}: every incident, its state and where;</li>
 *   <li>{@code here <template>}: an incident of that kind right where you stand (facing = its direction: stand on a cliff
 *   edge looking out for a cliff fall), reported at the nearest village bell;</li>
 *   <li>{@code start <id>}: sets an open incident off for you now;</li>
 *   <li>{@code realms}: the cursed-realm arenas in use.</li>
 * </ul>
 */
public final class InvestigationCommand {
    private InvestigationCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("incident")
                .then(Commands.literal("list").executes(InvestigationCommand::list))
                .then(Commands.literal("realms").executes(InvestigationCommand::realms))
                .then(Commands.literal("here").then(Commands.argument("template", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(IncidentTemplate.all().stream().map(IncidentTemplate::id), b))
                        .executes(InvestigationCommand::here)))
                .then(Commands.literal("start").then(Commands.argument("id", StringArgumentType.word()).executes(InvestigationCommand::start)));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        InvestigationState st = InvestigationState.get(c.getSource().getServer());
        StringBuilder sb = new StringBuilder(st.incidents().size() + " incidents, " + st.villages().size() + " villages");
        for (Incident i : st.incidents().values()) {
            sb.append("\n  ").append(i.id).append(" ").append(i.template).append(" ").append(i.grade.display).append(" ").append(i.state())
                    .append(" at ").append(i.site.toShortString()).append(" (").append(ReportWriter.direction(i.village, i.site)).append(" of ")
                    .append(i.village.toShortString()).append(") curses ").append(i.curses().size());
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return st.incidents().size();
    }

    private static int realms(CommandContext<CommandSourceStack> c) {
        InvestigationState st = InvestigationState.get(c.getSource().getServer());
        StringBuilder sb = new StringBuilder(st.arenas().size() + " realm arenas");
        for (InvestigationState.Arena a : st.arenas().values()) {
            sb.append("\n  slot ").append(a.slot).append(" ").append(a.layout).append(" incident ").append(a.incident).append(" inside ").append(a.inside().size());
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return st.arenas().size();
    }

    private static int here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        IncidentTemplate t = IncidentTemplate.get(StringArgumentType.getString(c, "template"));
        if (t == null) {
            c.getSource().sendFailure(Component.literal("Unknown incident template"));
            return 0;
        }
        ServerLevel level = (ServerLevel) p.level();
        BlockPos bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), p.blockPosition(), 256, PoiManager.Occupancy.ANY)
                .orElse(p.blockPosition().offset(60, 0, 60));
        Direction d = p.getDirection();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, bell);
        Incident in = Investigations.create(level, v, st, t, new Sites.Site(p.blockPosition(), d.getStepX(), d.getStepZ()),
                level.getGameTime(), RandomSource.create());
        st.flush();
        c.getSource().sendSuccess(() -> Component.literal("Incident " + in.id + " (" + t.id() + ") here. Report: " + in.headline), true);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        boolean ok = Investigations.force(p, StringArgumentType.getString(c, "id"));
        if (ok) c.getSource().sendSuccess(() -> Component.literal("Started."), true);
        else c.getSource().sendFailure(Component.literal("No open incident by that id."));
        return ok ? 1 : 0;
    }
}
