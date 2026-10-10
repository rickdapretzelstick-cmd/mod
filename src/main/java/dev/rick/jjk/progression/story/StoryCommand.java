package dev.rick.jjk.progression.story;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.StoryChains;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin and testing for the character storylines (op):
 * <pre>
 * /jjk story villages                  the storylines of the nearest villages, and how far along each is
 * /jjk story set &lt;kit|none&gt; [stage]    the nearest village's storyline (its next report follows)
 * /jjk story essence &lt;kit&gt; [player]    hand over an Essence, as a finale does
 * /jjk story trial &lt;kit&gt; [claim]       start a personal trial now (a test run unless "claim": then it claims the kit)
 * /jjk story complete                  complete your running trial now
 * /jjk story trials                    the trials and preludes running
 * /jjk story awaken &lt;kit&gt; &lt;player&gt; &lt;on|off&gt;   open or close a kit's Awakening (normally a later storyline's job)
 * /jjk relic list | reset &lt;kit&gt; | issue &lt;kit&gt; [player]   the world's one-of-a-kind relics
 * </pre>
 */
public final class StoryCommand {
    private StoryCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("story")
                        .then(Commands.literal("villages").executes(StoryCommand::villages))
                        .then(Commands.literal("set").then(Commands.argument("kit", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(kitsAndNone(), b))
                                .executes(c -> set(c, 1))
                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, StoryChains.DONE)).executes(c -> set(c, IntegerArgumentType.getInteger(c, "stage"))))))
                        .then(Commands.literal("essence").then(kitArg().executes(c -> essence(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player()).executes(c -> essence(c, EntityArgument.getPlayer(c, "player"))))))
                        .then(Commands.literal("trial").then(kitArg().executes(c -> trial(c, false))
                                .then(Commands.literal("claim").executes(c -> trial(c, true)))))
                        .then(Commands.literal("complete").executes(c -> {
                            boolean ok = PersonalTrials.completeNow(c.getSource().getPlayerOrException());
                            c.getSource().sendSuccess(() -> Component.literal(ok ? "Trial completed." : "No trial running."), true);
                            return ok ? 1 : 0;
                        }))
                        .then(Commands.literal("trials").executes(c -> {
                            List<String> lines = PersonalTrials.describe();
                            if (lines.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No trial running."), false);
                            for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l), false);
                            return lines.size();
                        }))
                        .then(Commands.literal("awaken").then(kitArg().then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("on").executes(c -> awaken(c, true)))
                                .then(Commands.literal("off").executes(c -> awaken(c, false)))))))
                .then(Commands.literal("relic")
                        .then(Commands.literal("list").executes(StoryCommand::relics))
                        .then(Commands.literal("reset").then(kitArg().executes(StoryCommand::reset)))
                        .then(Commands.literal("issue").then(kitArg().executes(c -> issue(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player()).executes(c -> issue(c, EntityArgument.getPlayer(c, "player"))))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> kitArg() {
        return Commands.argument("kit", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(kits(), b));
    }

    private static List<String> kits() {
        List<String> out = new ArrayList<>();
        for (CharacterStory s : CharacterStories.all()) out.add(s.kit());
        return out;
    }

    private static List<String> kitsAndNone() {
        List<String> out = kits();
        out.add("none");
        return out;
    }

    @Nullable
    private static CharacterStory story(CommandContext<CommandSourceStack> c) {
        CharacterStory s = CharacterStories.byKit(StringArgumentType.getString(c, "kit"));
        if (s == null) c.getSource().sendFailure(Component.literal("No storyline for " + StringArgumentType.getString(c, "kit")));
        return s;
    }

    /** The villages of this dimension, nearest first. */
    private static List<InvestigationState.Village> nearest(CommandContext<CommandSourceStack> c) {
        InvestigationState st = InvestigationState.get(c.getSource().getServer());
        String dim = c.getSource().getLevel().dimension().identifier().toString();
        var at = c.getSource().getPosition();
        List<InvestigationState.Village> out = new ArrayList<>();
        for (InvestigationState.Village v : st.villages().values()) if (v.dimension.equals(dim)) out.add(v);
        out.sort(Comparator.comparingDouble(v -> v.bell.distToCenterSqr(at)));
        return out;
    }

    private static int villages(CommandContext<CommandSourceStack> c) {
        List<InvestigationState.Village> vs = nearest(c);
        if (vs.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No village known in this dimension yet (walk into one)."), false);
        for (int i = 0; i < Math.min(10, vs.size()); i++) {
            InvestigationState.Village v = vs.get(i);
            String line = v.bell.toShortString() + ": " + (v.story().isEmpty() ? "no storyline"
                    : v.story() + " storyline, " + (v.storyStage() >= StoryChains.DONE ? "complete" : "event " + v.storyStage() + " of 4"));
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return vs.size();
    }

    private static int set(CommandContext<CommandSourceStack> c, int stage) {
        String kit = StringArgumentType.getString(c, "kit");
        if (!kit.equals("none") && CharacterStories.byKit(kit) == null) {
            c.getSource().sendFailure(Component.literal("No storyline for " + kit));
            return 0;
        }
        List<InvestigationState.Village> vs = nearest(c);
        if (vs.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No village known in this dimension yet (walk into one)."));
            return 0;
        }
        InvestigationState st = InvestigationState.get(c.getSource().getServer());
        InvestigationState.Village v = vs.get(0);
        StoryChains.setStory(st, v, kit.equals("none") ? "" : kit, stage);
        st.flush();
        c.getSource().sendSuccess(() -> Component.literal("The village at " + v.bell.toShortString() + " now holds "
                + (kit.equals("none") ? "no storyline" : "the " + kit + " storyline at event " + stage) + " (read its board)."), true);
        return 1;
    }

    private static int essence(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        CharacterStory s = story(c);
        if (s == null) return 0;
        StoryChains.present(p, s);
        return 1;
    }

    private static int trial(CommandContext<CommandSourceStack> c, boolean claim) throws CommandSyntaxException {
        CharacterStory s = story(c);
        if (s == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (PersonalTrials.inTrial(p)) {
            c.getSource().sendFailure(Component.literal("Already in a trial."));
            return 0;
        }
        PersonalTrials.startNow(p, s, !claim);
        c.getSource().sendSuccess(() -> Component.literal("Starting the " + s.kit() + " trial" + (claim ? " (it will claim the kit)" : " (test: claims nothing)")), true);
        return 1;
    }

    private static int awaken(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        String kit = StringArgumentType.getString(c, "kit");
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        TechniqueProgression.setAwakened(p, kit, on);
        c.getSource().sendSuccess(() -> Component.literal(kit + "'s Awakening " + (on ? "opened" : "closed") + " for " + p.getName().getString()), true);
        return 1;
    }

    private static int relics(CommandContext<CommandSourceStack> c) {
        Map<String, UniqueRelics.Relic> all = UniqueRelics.get(c.getSource().getServer()).all();
        for (CharacterStory s : CharacterStories.all()) {
            UniqueRelics.Relic r = all.get(s.relicKey());
            String line = s.relicKey() + " -> " + (r == null ? "none in this world" : "kept by " + r.keeperName() + " (" + r.keeper() + ")"
                    + (r.boundTo() == null ? "" : ", bound to " + r.boundTo()));
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        CharacterStory s = story(c);
        if (s == null) return 0;
        UniqueRelics.Relic old = UniqueRelics.get(c.getSource().getServer()).reset(s.relicKey());
        c.getSource().sendSuccess(() -> Component.literal(old == null ? "No " + s.kit() + " relic to forget."
                : "The " + s.kit() + " relic is forgotten: its item goes cold, and another may be forged."), true);
        return old == null ? 0 : 1;
    }

    private static int issue(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        CharacterStory s = story(c);
        if (s == null) return 0;
        UUID token = UniqueRelics.get(c.getSource().getServer()).forge(s.relicKey(), p.getUUID(), p.getName().getString());
        if (token == null) {
            c.getSource().sendFailure(Component.literal("The world already has its " + s.kit() + " relic (reset it first)."));
            return 0;
        }
        ItemStack relic = RelicItem.create(s.relic().get(), token);
        if (!p.getInventory().add(relic)) p.drop(relic, false, net.minecraft.util.Prediction.SERVER_ONLY);
        c.getSource().sendSuccess(() -> Component.literal("Forged the world's " + s.kit() + " relic for " + p.getName().getString()), true);
        return 1;
    }
}
