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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /jjk kit: administration of Survival kit ownership.
 * <pre>
 * /jjk kit list                         every kit and who owns it
 * /jjk kit owner &lt;kit&gt;                 one kit's owner
 * /jjk kit info [player]                a player's progression record
 * /jjk kit grant &lt;kit&gt; &lt;player&gt;        claim a kit for a player (only if it is unclaimed)
 * /jjk kit transfer &lt;kit&gt; &lt;player&gt;     give a kit to a player, taking it from its owner
 * /jjk kit release &lt;kit&gt;               free a kit
 * /jjk kit repair                       re-check every online player's record against the world's
 * /jjk kit acquire cursed_finger &lt;player&gt;   run the Cursed Finger acquisition as if they ate one (testing)
 * </pre>
 */
public final class ProgressionCommand {
    private ProgressionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("kit")
                        .then(Commands.literal("list").executes(ProgressionCommand::list))
                        .then(Commands.literal("owner").then(Commands.argument("kit", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(kits(), b)).executes(c -> owner(c, kit(c)))))
                        .then(Commands.literal("info")
                                .executes(c -> info(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.entity()).executes(c -> {
                                    ServerPlayer p = player(c);
                                    return p == null ? 0 : info(c, p);
                                })))
                        .then(Commands.literal("grant").then(Commands.argument("kit", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(kits(), b))
                                .then(Commands.argument("player", EntityArgument.entity()).executes(c -> {
                                    ServerPlayer p = player(c);
                                    return p == null ? 0 : grant(c, kit(c), p);
                                }))))
                        .then(Commands.literal("transfer").then(Commands.argument("kit", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(kits(), b))
                                .then(Commands.argument("player", EntityArgument.entity()).executes(c -> {
                                    ServerPlayer p = player(c);
                                    return p == null ? 0 : transfer(c, kit(c), p);
                                }))))
                        .then(Commands.literal("release").then(Commands.argument("kit", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(kits(), b)).executes(c -> release(c, kit(c)))))
                        .then(Commands.literal("repair").executes(ProgressionCommand::repair))
                        .then(Commands.literal("acquire").then(Commands.literal("cursed_finger")
                                .then(Commands.argument("player", EntityArgument.entity()).executes(c -> {
                                    ServerPlayer p = player(c);
                                    if (p == null) return 0;
                                    KitOwnership.ClaimResult r = TechniqueProgression.acquire(p, CursedFingerAcquisition.INSTANCE);
                                    c.getSource().sendSuccess(() -> Component.literal("Cursed Finger for " + p.getName().getString() + ": " + r), true);
                                    return 1;
                                }))))));
    }

    private static List<String> kits() {
        List<String> l = new ArrayList<>();
        Characters.ids().forEach(l::add);
        return l;
    }

    private static String kit(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "kit");
    }

    /** The "player" argument, or null (with a failure message) when it isn't a player. */
    @Nullable
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (EntityArgument.getEntity(c, "player") instanceof ServerPlayer p) return p;
        c.getSource().sendFailure(Component.literal("Not a player"));
        return null;
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getLevel().getServer();
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        Map<String, KitOwnership.Owner> all = KitOwnership.get(server(c)).all();
        List<String> ids = kits();
        for (String k : all.keySet()) if (!ids.contains(k)) ids.add(k);
        for (String k : ids) {
            KitOwnership.Owner o = all.get(k);
            String line = k.toUpperCase(Locale.ROOT) + " -> " + (o == null ? "UNCLAIMED" : o.name() + " (" + o.uuid() + ", via " + o.source() + ")");
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
        c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": kits " + pr.kits() + ", current '" + pr.kit()
                + "', counters " + pr.counters() + ", governed " + TechniqueProgression.governs(p)), false);
        return pr.kits().size();
    }

    private static int grant(CommandContext<CommandSourceStack> c, String kit, ServerPlayer p) {
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

    private static int repair(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (var level : server(c).getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                PlayerProgression before = TechniqueProgression.progression(p);
                PlayerProgression after = TechniqueProgression.validate(p);
                TechniqueProgression.enforce(p);
                TechniqueProgression.sync(p, true);
                if (!before.equals(after)) n++;
            }
        }
        int fixed = n;
        c.getSource().sendSuccess(() -> Component.literal("Checked every online player against the kit registry; " + fixed + " record(s) corrected"), true);
        return n;
    }
}
