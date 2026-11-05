package dev.rick.jjk.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The playtesting menu: {@code /jjk test} shows clickable categories, each a page of one-click actions, so every
 * character, cursed tool and domain can be tried without typing commands. (The survival system's pages, realms, trials,
 * curses, contests and guardians, are archived under archive/survival with the systems they ran.)
 */
public final class TestCommand {
    private TestCommand() {}

    /** One action on a page: its button label, the command it runs, and a line on what it does. */
    private record Entry(String label, String command, String hint) {}

    private static final Map<String, List<Entry>> PAGES = new LinkedHashMap<>();
    static {
        PAGES.put("setup", List.of(
                new Entry("Heal + reset", "/jjk test heal", "Full health and food, energy, cooldowns and statuses reset."),
                new Entry("No cooldowns ON", "/jjk nocooldown true", "Abilities cost nothing and never cool down."),
                new Entry("No cooldowns OFF", "/jjk nocooldown false", "Back to normal costs and cooldowns."),
                new Entry("Full energy", "/jjk energy 10000", "Cursed energy to full."),
                new Entry("Full awakening", "/jjk awakening 100000", "The awakening meter to full."),
                new Entry("End awakening", "/jjk awakening end", "Ends an active awakening."),
                new Entry("Survival", "/gamemode survival", "Survival: damage and statuses apply."),
                new Entry("Creative", "/gamemode creative", "Creative: you can't be hurt."),
                new Entry("Day + clear", "/time set day", "Daylight (then /weather clear)."),
                new Entry("Training arena", "/jjk arena", "Builds an arena with standing, jumping and fighting dummies."),
                new Entry("Dummy", "/jjk dummy", "One standing training dummy."),
                new Entry("Fighting dummy", "/jjk dummy fight", "A dummy that fights back.")));
        PAGES.put("characters", List.of(
                new Entry("Gojo", "/jjk character gojo @s", "Become Satoru Gojo (Honored One)."),
                new Entry("Hakari", "/jjk character hakari @s", "Become Kinji Hakari (Restless Gambler)."),
                new Entry("Yuji", "/jjk character yuji @s", "Become Yuji Itadori (Vessel): Black Flash on Divergent Fist."),
                new Entry("Yuta", "/jjk character yuta @s", "Become Yuta Okkotsu (Cursed Partners): Resolute Black Flash."),
                new Entry("Ryu", "/jjk character ryu @s", "Become Ryu Ishigori (True Cannon).")));
        PAGES.put("blackflash", List.of(
                new Entry("Become Yuji", "/jjk character yuji @s", "Divergent Fist, press again while his body flashes white."),
                new Entry("Become Yuta", "/jjk character yuta @s", "Resolute Slash, press again as he reappears."),
                new Entry("Target", "/jjk dummy fight", "A dummy that fights back, to Black Flash."),
                new Entry("No cooldowns ON", "/jjk nocooldown true", "Repeat it as often as you like."),
                new Entry("Clear dummies", "/jjk test clear", "Removes the dummies near you.")));
        PAGES.put("tools", List.of(
                new Entry("Cursed rifle", "/jjk rifle issue", "The cursed rifle (guard with it: hold block)."),
                new Entry("Slaughter Demon", "/give @s jjk:slaughter_demon", "The short cursed blade."),
                new Entry("Cursed Cleaver", "/give @s jjk:cursed_cleaver", "The cleaver."),
                new Entry("Cursed Blade", "/give @s jjk:cursed_blade", "The cursed blade.")));
        PAGES.put("domains", List.of(
                new Entry("Dummy: Infinite Void", "/jjk dummy domain void", "A dummy opens Infinite Void at you (clash it with your own)."),
                new Entry("Dummy: Idle Death Gamble", "/jjk dummy domain idg", "A dummy opens Idle Death Gamble."),
                new Entry("Dummy: Malevolent Shrine", "/jjk dummy domain shrine", "A dummy opens Malevolent Shrine."),
                new Entry("Cancel domains", "/jjk domain cancel all", "Closes every domain.")));
        PAGES.put("cleanup", List.of(
                new Entry("Clear dummies", "/jjk test clear", "Removes every dummy near you."),
                new Entry("Restore terrain", "/jjk restore now", "Puts back every block a fight broke."),
                new Entry("Cancel domains", "/jjk domain cancel all", "Closes every domain."),
                new Entry("Heal + reset", "/jjk test heal", "Full health, energy and cooldowns.")));
    }

    private static final Map<String, String> TITLES = Map.ofEntries(Map.entry("setup", "Setup"), Map.entry("characters", "Characters"),
            Map.entry("blackflash", "Black Flash"), Map.entry("tools", "Cursed tools"), Map.entry("domains", "Domains"), Map.entry("cleanup", "Cleanup"));

    /** Under {@code /jjk}: the menu, its pages, and the shortcuts. */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("test").executes(TestCommand::menu)
                        .then(Commands.argument("page", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(PAGES.keySet(), b))
                                .executes(c -> page(c, StringArgumentType.getString(c, "page"))))
                        .then(Commands.literal("clear").executes(TestCommand::clear))
                        .then(Commands.literal("heal").executes(TestCommand::heal))));
    }

    /** Every command a button runs (each page's actions and the pages themselves), for checking they all exist. */
    public static List<String> allCommands() {
        List<String> out = new java.util.ArrayList<>();
        out.add("/jjk test");
        for (String key : PAGES.keySet()) out.add("/jjk test " + key);
        for (List<Entry> page : PAGES.values()) for (Entry e : page) out.add(e.command);
        return out;
    }

    // --- The menu ---

    private static int menu(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        s.sendSuccess(() -> Component.literal("— JJK Test Menu —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        s.sendSuccess(() -> Component.literal("Click a category (or type /jjk test <category>):").withStyle(ChatFormatting.GRAY), false);
        MutableComponent row = Component.empty();
        int n = 0;
        for (String key : PAGES.keySet()) {
            row.append(button(TITLES.get(key), "/jjk test " + key, PAGES.get(key).size() + " actions", ChatFormatting.AQUA)).append(" ");
            if (++n % 4 == 0) {
                MutableComponent line = row;
                s.sendSuccess(() -> line, false);
                row = Component.empty();
            }
        }
        if (n % 4 != 0) {
            MutableComponent line = row;
            s.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int page(CommandContext<CommandSourceStack> c, String key) {
        List<Entry> entries = PAGES.get(key);
        CommandSourceStack s = c.getSource();
        if (entries == null) {
            s.sendFailure(Component.literal("No test page \"" + key + "\": " + String.join(", ", PAGES.keySet())));
            return 0;
        }
        s.sendSuccess(() -> Component.literal("— " + TITLES.get(key) + " — ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(button("« Menu", "/jjk test", "Back to the categories", ChatFormatting.GRAY)), false);
        for (Entry e : entries) {
            s.sendSuccess(() -> button(e.label, e.command, e.hint + "\n" + e.command, ChatFormatting.GREEN)
                    .append(Component.literal(" " + e.hint).withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return entries.size();
    }

    private static MutableComponent button(String label, String command, String hover, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = 0;
        for (Entity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(64),
                x -> x.getType() == dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY)) {
            e.discard();
            n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal("Removed " + count + " dummies."), false);
        return n;
    }

    private static int heal(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(10f);
        p.level().getServer().getCommands().performPrefixedCommand(c.getSource(), "jjk reset");
        return 1;
    }
}
