package dev.rick.jjk.progression;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rick.jjk.core.character.Characters;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /jjk kit: administration of Survival kit ownership.
 * <pre>
 * /jjk kit list                       every kit and who owns it
 * /jjk kit owner &lt;kit&gt;               one kit's owner
 * /jjk kit info [player]              a player's progression record
 * /jjk kit grant &lt;kit&gt; &lt;player&gt;      claim a kit for a player (only if it is unclaimed)
 * /jjk kit transfer &lt;kit&gt; &lt;player&gt;   give a kit to a player, taking it from its owner
 * /jjk kit release &lt;kit&gt;             free a kit
 * /jjk kit repair                     re-check every online player's record against the world's
 * /jjk kit rooms                      the cursed battle rooms found so far and their encounter state
 * /jjk prison status                  the world's Prison Realm: where it is, who is inside, their escape
 * /jjk prison free                    let the captive out (grants nothing)
 * /jjk prison reset                   forget the realm entirely (a cube lost beyond recovery); frees any captive
 * </pre>
 */
public final class ProgressionCommand {
    private ProgressionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("kit")
                        .then(Commands.literal("list").executes(ProgressionCommand::list))
                        .then(Commands.literal("owner").then(kitArg().executes(c -> owner(c, kit(c)))))
                        .then(Commands.literal("info")
                                .executes(c -> info(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player()).executes(c -> info(c, EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("grant").then(kitArg().then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> grant(c, kit(c), EntityArgument.getPlayer(c, "player"))))))
                        .then(Commands.literal("transfer").then(kitArg().then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> transfer(c, kit(c), EntityArgument.getPlayer(c, "player"))))))
                        .then(Commands.literal("release").then(kitArg().executes(c -> release(c, kit(c)))))
                        .then(Commands.literal("repair").executes(ProgressionCommand::repair))
                        .then(Commands.literal("rooms").executes(ProgressionCommand::rooms)))
                .then(Commands.literal("prison")
                        .then(Commands.literal("status").executes(ProgressionCommand::prisonStatus))
                        .then(Commands.literal("free").executes(c -> {
                            boolean ok = dev.rick.jjk.progression.prison.PrisonRealm.adminFree(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(ok ? "Releasing the Prison Realm's captive (nothing granted)."
                                    : "Nobody is sealed in the Prison Realm."), true);
                            return ok ? 1 : 0;
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            dev.rick.jjk.progression.prison.PrisonRealm.adminReset(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("The Prison Realm is forgotten: another may be forged."), true);
                            return 1;
                        })))
                .then(dev.rick.jjk.progression.mastery.MasteryCommand.node())
                .then(dev.rick.jjk.progression.investigation.InvestigationCommand.node()));
    }

    private static int prisonStatus(CommandContext<CommandSourceStack> c) {
        var st = dev.rick.jjk.progression.prison.PrisonRealm.state(c.getSource().getServer());
        String where = st.pos() == null ? "" : " at " + st.dimension() + " " + st.pos().toShortString();
        String who = st.captive() == null ? "" : ", captive " + st.captiveName() + " (" + st.captive() + ")";
        String esc = st.phase() == dev.rick.jjk.progression.prison.PrisonRealmState.Phase.SEALED
                ? ", escape stage " + Math.min(st.stage() + 1, 3) + "/3 with " + Integer.bitCount(st.broken()) + "/4 seals" + (st.stage() >= 3 ? " (core open)" : "") : "";
        String owed = st.pending().isEmpty() ? "" : ", " + st.pending().size() + " release(s) owed to offline players";
        String msg = "Prison Realm: " + st.phase() + (st.realmId() == null ? " (none forged)" : " " + st.realmId()) + where + who + esc + owed;
        c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> kitArg() {
        return Commands.argument("kit", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(kits(), b));
    }

    private static List<String> kits() {
        List<String> l = new ArrayList<>();
        Characters.ids().forEach(l::add);
        return l;
    }

    private static String kit(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "kit");
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        Map<String, KitOwnership.Owner> all = KitOwnership.get(server(c)).all();
        List<String> ids = kits();
        for (String k : all.keySet()) if (!ids.contains(k)) ids.add(k);
        for (String k : ids) {
            KitOwnership.Owner o = all.get(k);
            String line = k.toUpperCase(java.util.Locale.ROOT) + " -> " + (o == null ? "UNCLAIMED" : o.name() + " (" + o.uuid() + ", via " + o.source() + ")");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int owner(CommandContext<CommandSourceStack> c, String kit) {
        KitOwnership.Owner o = KitOwnership.get(server(c)).owner(kit);
        c.getSource().sendSuccess(() -> Component.literal(kit + ": " + (o == null ? "unclaimed" : o.name() + " (" + o.uuid() + ")")), false);
        return o == null ? 0 : 1;
    }

    private static int info(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        PlayerProgression pr = TechniqueProgression.validate(p);
        c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": kits " + pr.kits() + ", current '" + pr.kit() + "', flags "
                + pr.flags() + ", counters " + pr.counters() + ", governed " + TechniqueProgression.governs(p) + ", perceives curses "
                + CursePerception.canPerceive(p)), false);
        return pr.kits().size();
    }

    private static int grant(CommandContext<CommandSourceStack> c, String kit, ServerPlayer p) throws CommandSyntaxException {
        if (Characters.get(kit) == null) {
            c.getSource().sendFailure(Component.literal("Unknown kit " + kit));
            return 0;
        }
        KitOwnership.ClaimResult r = TechniqueProgression.tryClaimKit(p, kit, "admin");
        switch (r) {
            case CLAIMED -> c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + " now owns " + kit), true);
            case ALREADY_OWNER -> c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + " already owns " + kit), false);
            case TAKEN -> c.getSource().sendFailure(Component.literal(kit + " already belongs to someone else (use transfer)"));
            case FAILED -> c.getSource().sendFailure(Component.literal("Could not claim " + kit + " (see the server log)"));
        }
        return r == KitOwnership.ClaimResult.CLAIMED ? 1 : 0;
    }

    private static int transfer(CommandContext<CommandSourceStack> c, String kit, ServerPlayer p) {
        if (Characters.get(kit) == null) {
            c.getSource().sendFailure(Component.literal("Unknown kit " + kit));
            return 0;
        }
        if (!TechniqueProgression.transfer(server(c), kit, p)) {
            c.getSource().sendFailure(Component.literal("Could not transfer " + kit + " (see the server log)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(kit + " now belongs to " + p.getName().getString()), true);
        return 1;
    }

    private static int release(CommandContext<CommandSourceStack> c, String kit) {
        KitOwnership.Owner old = TechniqueProgression.release(server(c), kit);
        c.getSource().sendSuccess(() -> Component.literal(old == null ? kit + " was not claimed" : kit + " released from " + old.name()), true);
        return old == null ? 0 : 1;
    }

    private static int rooms(CommandContext<CommandSourceStack> c) {
        var rooms = CursedEncounters.rooms(server(c));
        if (rooms.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No cursed battle room has been loaded yet"), false);
        for (CursedEncounters.Room r : rooms) {
            String line = r.site + " room at " + r.dimension + " " + r.seal.toShortString() + ": " + r.encounter + ", " + r.state
                    + (r.discoveredAt < 0 ? ", undiscovered" : ", discovered");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return rooms.size();
    }

    private static int repair(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (ServerPlayer p : server(c).getPlayerList().getPlayers()) {
            PlayerProgression before = TechniqueProgression.progression(p);
            PlayerProgression after = TechniqueProgression.validate(p);
            TechniqueProgression.enforce(p);
            TechniqueProgression.sync(p, true);
            if (!before.equals(after)) n++;
        }
        int fixed = n;
        c.getSource().sendSuccess(() -> Component.literal("Checked every online player against the kit registry; " + fixed + " record(s) corrected"), true);
        return n;
    }
}
