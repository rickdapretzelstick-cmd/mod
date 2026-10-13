package dev.rick.jjk.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rick.jjk.JJK;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.progression.CurseAggro;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /jjk test}: one place to playtest everything. It prints a menu of clickable categories in chat; each category
 * lists clickable actions (hover one to see the command it runs). Most actions run the mod's existing admin commands; a
 * few are shortcuts of their own here ({@code /jjk test realm|guardian|leave|spawn|weak|grab|clear|heal}).
 */
public final class TestCommand {
    private TestCommand() {}

    /** One action on a page: its button label, the command it runs, and a line on what it does. */
    private record Entry(String label, String command, String hint) {}

    private static final String[] KITS = {"gojo", "hakari", "yuji", "yuta", "ryu"};
    /** The curses you can spawn, by entity id. */
    private static final String[] CURSES = {"fly_head", "school_crawler", "school_maw", "forest_stalker", "grave_hound", "veiled_bride",
            "bloat_toad", "brood_mother", "guardian_claw_hunter", "guardian_pale_stalker", "guardian_cyclops_bruiser", "guardian_veiled_reacher"};
    /** Each cursed realm, by the incident that leads into it. */
    private static final Map<String, String> REALMS = new LinkedHashMap<>();
    static {
        REALMS.put("Gojo: fractured infinity", "gojo_chain_1");
        REALMS.put("Yuji: last screening", "yuji_chain_1");
        REALMS.put("Yuta: chapel courtyard", "jilted_bride");
        REALMS.put("Ryu: impact crater", "stalker_crater");
        REALMS.put("Hakari: fight club", "hakari_chain_1");
        REALMS.put("Rifle: watching woods", "hunting_lodge");
        REALMS.put("Mine", "old_mine");
        REALMS.put("House", "empty_house");
        REALMS.put("Pasture", "livestock");
        REALMS.put("Cliff", "cliff_fall");
    }

    private static final Map<String, List<Entry>> PAGES = new LinkedHashMap<>();
    static {
        PAGES.put("setup", List.of(
                new Entry("Heal + reset", "/jjk test heal", "Full health and food, energy, cooldowns and statuses reset."),
                new Entry("No cooldowns ON", "/jjk nocooldown true", "Abilities cost nothing and never cool down."),
                new Entry("No cooldowns OFF", "/jjk nocooldown false", "Back to normal costs and cooldowns."),
                new Entry("Full energy", "/jjk energy 10000", "Cursed energy to full."),
                new Entry("Full awakening", "/jjk awakening 100000", "The awakening meter to full."),
                new Entry("End awakening", "/jjk awakening end", "Ends an active awakening."),
                new Entry("Survival", "/gamemode survival", "Survival: curses grab, contests and damage apply."),
                new Entry("Creative", "/gamemode creative", "Creative: you can't be grabbed or hurt."),
                new Entry("Day + clear", "/time set day", "Daylight (then /weather clear)."),
                new Entry("Training arena", "/jjk arena", "Builds an arena with standing, jumping and fighting dummies."),
                new Entry("Dummy", "/jjk dummy", "One standing training dummy."),
                new Entry("Fighting dummy", "/jjk dummy fight", "A dummy that fights back.")));
        PAGES.put("characters", List.of(
                new Entry("Gojo", "/jjk character gojo @s", "Become Satoru Gojo (Honored One)."),
                new Entry("Hakari", "/jjk character hakari @s", "Become Kinji Hakari (Restless Gambler)."),
                new Entry("Yuji", "/jjk character yuji @s", "Become Yuji Itadori (Vessel): Black Flash on Divergent Fist."),
                new Entry("Yuta", "/jjk character yuta @s", "Become Yuta Okkotsu (Cursed Partners): Resolute Black Flash."),
                new Entry("Ryu", "/jjk character ryu @s", "Become Ryu Ishigori (True Cannon)."),
                new Entry("Kit list", "/jjk kit list", "Who owns which kit in this world.")));
        PAGES.put("trials", List.of(
                new Entry("Gojo trial", "/jjk training start gojo", "Gojo's relic trial (a test run: it doesn't claim the kit)."),
                new Entry("Hakari trial", "/jjk training start hakari", "Hakari's relic trial."),
                new Entry("Yuji trial", "/jjk training start yuji", "Yuji's relic trial (the VHS)."),
                new Entry("Yuta trial", "/jjk training start yuta", "Yuta's relic trial (Rika at the shrine)."),
                new Entry("Ryu trial", "/jjk training start ryu", "Ryu's relic trial (the long gallery)."),
                new Entry("Pass attempt", "/jjk training succeed", "Passes the running attempt now."),
                new Entry("Fail attempt", "/jjk training fail", "Fails the running attempt now."),
                new Entry("Leave trial", "/jjk training leave", "Leaves the trial room."),
                new Entry("Relic: Yuji VHS", "/jjk relic issue yuji", "Gives you the world's Yuji relic (if it isn't out yet)."),
                new Entry("Relics", "/jjk relic list", "Where each relic is.")));
        List<Entry> realms = new java.util.ArrayList<>();
        for (var e : REALMS.entrySet()) realms.add(new Entry(e.getKey(), "/jjk test realm " + e.getValue(), "Into this cursed realm now (incident " + e.getValue() + ")."));
        realms.add(new Entry("Leave realm", "/jjk test leave", "Closes the realm you're in and sends everyone home."));
        realms.add(new Entry("Realms in use", "/jjk incident realms", "Every realm arena that's open."));
        PAGES.put("realms", realms);
        PAGES.put("guardians", List.of(
                new Entry("Claw Hunter", "/jjk test guardian guardian_claw_hunter", "A Grade 1 contract fight in the Claw Hunter's arena."),
                new Entry("Pale Stalker", "/jjk test guardian guardian_pale_stalker", "The Pale Stalker's arena."),
                new Entry("Cyclops Bruiser", "/jjk test guardian guardian_cyclops_bruiser", "The Cyclops Bruiser's arena."),
                new Entry("Veiled Reacher", "/jjk test guardian guardian_veiled_reacher", "The Veiled Reacher's arena."),
                new Entry("Rematch", "/contract rematch", "Back into a lost guardian contract."),
                new Entry("Leave realm", "/jjk test leave", "Closes the arena you're in.")));
        List<Entry> curses = new java.util.ArrayList<>();
        for (String c : CURSES) curses.add(new Entry(pretty(c), "/jjk test spawn " + c, "Spawns a hostile " + pretty(c) + " a few blocks ahead."));
        curses.add(new Entry("Clear curses", "/jjk test clear", "Removes every curse and dummy near you."));
        PAGES.put("curses", curses);
        PAGES.put("contests", List.of(
                new Entry("Grab me now", "/jjk test grab", "A grave hound grabs you: the strength contest starts at once (survival)."),
                new Entry("Sparring hound", "/jjk test spawn grave_hound", "A hostile hound: block its bite for a defensive contest, or tap attack while blocking to counter."),
                new Entry("Sparring maw", "/jjk test spawn school_maw", "A hostile maw for blocking and counters."),
                new Entry("Survival", "/gamemode survival", "Contests only happen in survival."),
                new Entry("Clear curses", "/jjk test clear", "Removes the curses near you.")));
        PAGES.put("blackflash", List.of(
                new Entry("Become Yuji", "/jjk character yuji @s", "Divergent Fist, press again while his body flashes white."),
                new Entry("Become Yuta", "/jjk character yuta @s", "Resolute Slash, press again as he reappears."),
                new Entry("Target", "/jjk test spawn grave_hound", "A hostile curse to Black Flash."),
                new Entry("Finishable target", "/jjk test weak grave_hound", "A curse at low health: the lethal Black Flash."),
                new Entry("No cooldowns ON", "/jjk nocooldown true", "Repeat it as often as you like."),
                new Entry("Clear curses", "/jjk test clear", "Removes the curses near you.")));
        PAGES.put("tools", List.of(
                new Entry("Cursed rifle", "/jjk rifle issue", "The cursed rifle (guard with it: hold block)."),
                new Entry("Slaughter Demon", "/give @s jjk:slaughter_demon", "The short cursed blade."),
                new Entry("Cursed Cleaver", "/give @s jjk:cursed_cleaver", "The cleaver."),
                new Entry("Cursed Blade", "/give @s jjk:cursed_blade", "The cursed blade."),
                new Entry("Cursed glasses", "/give @s jjk:cursed_glasses", "See curses without a technique."),
                new Entry("Exorcism Seal", "/give @s jjk:exorcism_seal", "Seals a guardian's remnant."),
                new Entry("Energy bottles", "/give @s jjk:cursed_energy_bottle 8", "For infusing relics in a cauldron."),
                new Entry("Prison Realm", "/give @s jjk:prison_realm", "The Prison Realm cube.")));
        PAGES.put("domains", List.of(
                new Entry("Dummy: Infinite Void", "/jjk dummy domain void", "A dummy opens Infinite Void at you (clash it with your own)."),
                new Entry("Dummy: Idle Death Gamble", "/jjk dummy domain idg", "A dummy opens Idle Death Gamble."),
                new Entry("Dummy: Malevolent Shrine", "/jjk dummy domain shrine", "A dummy opens Malevolent Shrine."),
                new Entry("Cancel domains", "/jjk domain cancel all", "Closes every domain."),
                new Entry("Prison status", "/jjk prison status", "Where the Prison Realm is."),
                new Entry("Prison free", "/jjk prison free", "Releases whoever is sealed.")));
        PAGES.put("cleanup", List.of(
                new Entry("Clear curses", "/jjk test clear", "Removes every curse and dummy near you."),
                new Entry("Leave realm", "/jjk test leave", "Closes the realm you're in."),
                new Entry("Restore terrain", "/jjk restore now", "Puts back every block a fight broke."),
                new Entry("Cancel domains", "/jjk domain cancel all", "Closes every domain."),
                new Entry("Heal + reset", "/jjk test heal", "Full health, energy and cooldowns.")));
    }

    private static final Map<String, String> TITLES = Map.ofEntries(Map.entry("setup", "Setup"), Map.entry("characters", "Characters"),
            Map.entry("trials", "Relic trials"), Map.entry("realms", "Cursed realms"), Map.entry("guardians", "Guardians"), Map.entry("curses", "Curses"),
            Map.entry("contests", "Strength contests"), Map.entry("blackflash", "Black Flash"), Map.entry("tools", "Cursed tools & items"),
            Map.entry("domains", "Domains & Prison Realm"), Map.entry("cleanup", "Cleanup"));

    /** Under {@code /jjk}: the menu, its pages, and the shortcuts. */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("test").executes(TestCommand::menu)
                        .then(Commands.argument("page", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(PAGES.keySet(), b))
                                .executes(c -> page(c, StringArgumentType.getString(c, "page"))))
                        .then(Commands.literal("realm").then(Commands.argument("incident", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(IncidentTemplate.all().stream().map(IncidentTemplate::id), b))
                                .executes(c -> realm(c, StringArgumentType.getString(c, "incident"), null))))
                        .then(Commands.literal("guardian").then(Commands.argument("body", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(CURSES).stream().filter(s -> s.startsWith("guardian_")), b))
                                .executes(c -> realm(c, "guardian_cliff", StringArgumentType.getString(c, "body")))))
                        .then(Commands.literal("leave").executes(TestCommand::leave))
                        .then(Commands.literal("spawn").then(curseArg().executes(c -> spawn(c, StringArgumentType.getString(c, "curse"), false))))
                        .then(Commands.literal("weak").then(curseArg().executes(c -> spawn(c, StringArgumentType.getString(c, "curse"), true))))
                        .then(Commands.literal("grab").executes(TestCommand::grab))
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

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> curseArg() {
        return Commands.argument("curse", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(CURSES, b));
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

    private static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.replace("guardian_", "").split("_")) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    // --- Shortcuts ---

    /** An incident of this kind right here, set off at once: into its realm (a guardian contract: the body asked for). */
    private static int realm(CommandContext<CommandSourceStack> c, String template, String body) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        IncidentTemplate t = IncidentTemplate.get(template);
        if (t == null) {
            c.getSource().sendFailure(Component.literal("Unknown incident " + template));
            return 0;
        }
        if (CursedRealms.inRealm(p)) {
            c.getSource().sendFailure(Component.literal("Leave the realm you're in first: /jjk test leave"));
            return 0;
        }
        ServerLevel level = (ServerLevel) p.level();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, p.blockPosition().offset(-40, 0, -40));
        Incident in = Investigations.create(level, v, st, t, new Sites.Site(p.blockPosition(), 1, 0), level.getGameTime(), RandomSource.create());
        if (body != null && !Investigations.chooseGuardian(in, body)) {
            c.getSource().sendFailure(Component.literal("Not a guardian body: " + body));
            return 0;
        }
        st.flush();
        if (!Investigations.force(p, in.id)) {
            c.getSource().sendFailure(Component.literal("Couldn't open it here."));
            return 0;
        }
        String id = in.id;
        c.getSource().sendSuccess(() -> Component.literal("Into the realm (incident " + id + "). Leave with ").withStyle(ChatFormatting.GRAY)
                .append(button("Leave realm", "/jjk test leave", "Closes the realm", ChatFormatting.GREEN)), true);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        InvestigationState st = InvestigationState.get(p.level().getServer());
        var a = CursedRealms.arenaHere(p);
        if (a == null) {
            c.getSource().sendFailure(Component.literal("You're not in a realm."));
            return 0;
        }
        CursedRealms.close(p.level().getServer(), st, a, false);
        c.getSource().sendSuccess(() -> Component.literal("Realm closed."), true);
        return 1;
    }

    /** A curse a few blocks ahead of you, hostile to you at once (at a finishable sliver of health if {@code weak}). */
    private static int spawn(CommandContext<CommandSourceStack> c, String id, boolean weak) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        LivingEntity e = create(p, id);
        if (e == null) {
            c.getSource().sendFailure(Component.literal("Unknown curse " + id));
            return 0;
        }
        if (weak) e.setHealth(Math.max(1f, e.getMaxHealth() * 0.12f));
        c.getSource().sendSuccess(() -> Component.literal("Spawned " + pretty(id) + (weak ? " (finishable)" : "")), false);
        return 1;
    }

    private static LivingEntity create(ServerPlayer p, String id) {
        if (!java.util.Arrays.asList(CURSES).contains(id)) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(JJK.id(id));
        ServerLevel level = (ServerLevel) p.level();
        Entity e = type.create(level, EntitySpawnReason.COMMAND);
        if (!(e instanceof LivingEntity l)) return null;
        Vec3 at = p.position().add(HakariCombat.flat(p).scale(4));
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        double y = Math.abs(top.getY() - p.getY()) < 4 ? top.getY() : p.getY();
        l.setPos(at.x, y, at.z);
        l.setYRot(p.getYRot() + 180);
        level.addFreshEntity(l);
        if (l instanceof Mob m) {
            // Hostile first: a curse only takes a target it may (one that can't perceive it, it has to be provoked).
            CurseAggro.markHostile(m, p);
            m.setTarget(p);
        }
        if (l instanceof CommonCurseEntity cc) StrengthContests.forgetForTest(p, cc);
        return l;
    }

    private static int grab(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String why = StrengthContests.unavailable(p);
        if (why != null) {
            c.getSource().sendFailure(Component.literal("Can't be grabbed right now: " + why + (why.contains("survival") ? " (/gamemode survival)" : "")));
            return 0;
        }
        if (!(create(p, "grave_hound") instanceof CommonCurseEntity hound)) return 0;
        hound.setPos(p.position().add(HakariCombat.flat(p).scale(1.5)));
        if (!StrengthContests.begin(p.level().getServer(), hound, p)) {
            c.getSource().sendFailure(Component.literal("The contest couldn't start (" + StrengthContests.cannotGrab(hound) + ")"));
            return 0;
        }
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = 0;
        for (Entity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(64),
                x -> x instanceof CommonCurseEntity || x.getType() == dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY)) {
            e.discard();
            n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal("Removed " + count + " curse(s) and dummies."), false);
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
