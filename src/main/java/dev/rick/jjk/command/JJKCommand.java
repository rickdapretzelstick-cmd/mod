package dev.rick.jjk.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /jjk: test and admin commands.
 * <pre>
 * /jjk character <id|none> [targets]   become a character (gojo, hakari); admin override, skips the switching rules
 * /jjk nocooldown <true|false>         no cooldowns or energy costs for yourself
 * /jjk reset                           refill energy, clear cooldowns and statuses
 * /jjk energy <amount>                 set cursed energy
 * /jjk awakening <amount>|end          set the Awakening meter / leave Awakening
 * /jjk dummy [stand|jump|fight] [n]    spawn training dummies in front of you
 * /jjk arena                           build a flat test arena with dummies around you
 * /jjk domain cancel                   collapse your domain (or all domains with "all")
 * /jjk status [target]                 print combat statuses
 * /jjk config reload                   reload config/jjk.json
 * </pre>
 */
public final class JJKCommand {
    private JJKCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("character")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(concat(Characters.ids(), "none"), b))
                                .executes(c -> character(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(c -> character(c, EntityArgument.getEntities(c, "targets").stream()
                                                .filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e).toList())))))
                .then(Commands.literal("nocooldown")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            AbilityCaster caster = Casters.get(c.getSource().getPlayerOrException());
                            caster.setNoCost(BoolArgumentType.getBool(c, "on"));
                            caster.resetCooldowns();
                            c.getSource().sendSuccess(() -> Component.literal("No cooldown/cost: " + caster.noCost()), false);
                            return 1;
                        })))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    AbilityCaster caster = Casters.get(p);
                    caster.resetCooldowns();
                    caster.setEnergy(caster.maxEnergy());
                    Combat.state(p).clearAll();
                    Combat.state(p).markDirty();
                    c.getSource().sendSuccess(() -> Component.literal("Reset energy, cooldowns and statuses"), false);
                    return 1;
                }))
                .then(Commands.literal("energy")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(c -> {
                            Casters.get(c.getSource().getPlayerOrException()).setEnergy(FloatArgumentType.getFloat(c, "amount"));
                            return 1;
                        })))
                .then(Commands.literal("awakening")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(c -> {
                            Casters.get(c.getSource().getPlayerOrException()).setAwakening(FloatArgumentType.getFloat(c, "amount"));
                            return 1;
                        }))
                        .then(Commands.literal("end").executes(c -> {
                            Casters.get(c.getSource().getPlayerOrException()).endAwakening("command");
                            return 1;
                        })))
                .then(Commands.literal("dummy")
                        .executes(c -> dummy(c, TrainingDummy.Mode.STAND, 1))
                        .then(Commands.literal("domain")
                                .then(Commands.argument("domain", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("void", "idg", "shrine"), b))
                                        .executes(c -> dummyDomain(c, 0.8f))
                                        .then(Commands.argument("skill", FloatArgumentType.floatArg(0, 1))
                                                .executes(c -> dummyDomain(c, FloatArgumentType.getFloat(c, "skill"))))))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("stand", "jump", "fight"), b))
                                .executes(c -> dummy(c, mode(c), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                        .executes(c -> dummy(c, mode(c), IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("arena").executes(JJKCommand::arena))
                .then(Commands.literal("restore")
                        .then(Commands.literal("status").executes(c -> {
                            int n = dev.rick.jjk.core.world.WorldRestoration.pending(c.getSource().getLevel());
                            c.getSource().sendSuccess(() -> Component.literal(n + " damaged positions waiting to be restored"), false);
                            return n;
                        }))
                        .then(Commands.literal("now").executes(c -> {
                            int n = dev.rick.jjk.core.world.WorldRestoration.restoreAllNow(c.getSource().getLevel());
                            c.getSource().sendSuccess(() -> Component.literal("Restored " + n + " damaged positions"), true);
                            return n;
                        }))
                        .then(Commands.literal("forget").executes(c -> {
                            int n = dev.rick.jjk.core.world.WorldRestoration.forgetAll(c.getSource().getLevel());
                            c.getSource().sendSuccess(() -> Component.literal("Forgot " + n + " pending restorations (damage is now permanent)"), true);
                            return n;
                        })))
                .then(Commands.literal("domain")
                        .then(Commands.literal("cancel").executes(c -> {
                            DomainInstance dom = DomainManager.ownedBy(c.getSource().getPlayerOrException());
                            if (dom != null) DomainManager.cancel(dom, DomainInstance.EndReason.CANCELLED);
                            return dom != null ? 1 : 0;
                        }).then(Commands.literal("all").executes(c -> {
                            int n = 0;
                            for (DomainInstance dom : List.copyOf(DomainManager.all(c.getSource().getLevel()))) {
                                DomainManager.cancel(dom, DomainInstance.EndReason.CANCELLED);
                                n++;
                            }
                            int count = n;
                            c.getSource().sendSuccess(() -> Component.literal("Cancelled " + count + " domain(s)"), false);
                            return n;
                        }))))
                .then(Commands.literal("status")
                        .executes(c -> status(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> status(c, EntityArgument.getEntity(c, "target") instanceof LivingEntity l ? l : null))))
                .then(Commands.literal("config").then(Commands.literal("reload").executes(c -> {
                    JJKConfig.load();
                    c.getSource().sendSuccess(() -> Component.literal("Reloaded config/jjk.json"), true);
                    return 1;
                }))));
    }

    private static List<String> concat(Iterable<String> ids, String extra) {
        List<String> l = new java.util.ArrayList<>();
        ids.forEach(l::add);
        l.add(extra);
        return l;
    }

    private static TrainingDummy.Mode mode(CommandContext<CommandSourceStack> c) {
        try {
            return TrainingDummy.Mode.valueOf(StringArgumentType.getString(c, "mode").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TrainingDummy.Mode.STAND;
        }
    }

    private static int character(CommandContext<CommandSourceStack> c, Collection<LivingEntity> targets) {
        String id = StringArgumentType.getString(c, "id");
        JJKCharacter ch = id.equals("none") ? null : Characters.get(id);
        if (ch == null && !id.equals("none")) {
            c.getSource().sendFailure(Component.literal("Unknown character " + id));
            return 0;
        }
        for (LivingEntity t : targets) CharacterService.assign(t, ch);
        c.getSource().sendSuccess(() -> Component.literal("Set " + targets.size() + " to " + id), true);
        return targets.size();
    }

    private static int dummy(CommandContext<CommandSourceStack> c, TrainingDummy.Mode mode, int count) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        Vec3 look = p.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        for (int i = 0; i < count; i++) {
            Vec3 pos = p.position().add(fwd.scale(4)).add(side.scale((i - (count - 1) / 2.0) * 1.6));
            spawnDummy(level, pos, mode, p.getYRot() + 180);
        }
        c.getSource().sendSuccess(() -> Component.literal("Spawned " + count + " dummy(s) [" + mode.name().toLowerCase() + "]"), false);
        return count;
    }

    /**
     * Solo clash practice: the nearest training dummy (or a new one) opens a domain, and you get the counter window
     * a real opponent's domain would give. Press Awakening in time to answer it and clash; {@code skill} (0..1) is how
     * well the dummy hits its notes.
     */
    private static int dummyDomain(CommandContext<CommandSourceStack> c, float skill) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        String which = StringArgumentType.getString(c, "domain").toLowerCase(Locale.ROOT);
        dev.rick.jjk.core.domain.DomainDefinition def;
        String character;
        switch (which) {
            case "void", "unlimited_void", "gojo" -> {
                def = dev.rick.jjk.gojo.UnlimitedVoid.INSTANCE;
                character = "gojo";
            }
            case "idg", "idle_death_gamble", "hakari" -> {
                def = dev.rick.jjk.hakari.IdleDeathGamble.INSTANCE;
                character = "hakari";
            }
            case "shrine", "malevolent_shrine", "sukuna", "yuji" -> {
                def = dev.rick.jjk.yuji.MalevolentShrine.INSTANCE;
                character = "yuji";
            }
            default -> {
                c.getSource().sendFailure(Component.literal("Unknown domain " + which + " (void, idg, shrine)"));
                return 0;
            }
        }
        TrainingDummy dummy = null;
        for (TrainingDummy d : level.getEntitiesOfClass(TrainingDummy.class, p.getBoundingBox().inflate(16), TrainingDummy::isAlive)) {
            if (dummy == null || d.distanceToSqr(p) < dummy.distanceToSqr(p)) dummy = d;
        }
        if (dummy == null) {
            Vec3 look = p.getLookAngle();
            Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
            dummy = spawnDummy(level, p.position().add(fwd.scale(8)), TrainingDummy.Mode.STAND, p.getYRot() + 180);
            if (dummy == null) return 0;
        }
        if (DomainManager.ownedBy(dummy) != null) {
            c.getSource().sendFailure(Component.literal("That dummy already has a domain up (/jjk domain cancel all)"));
            return 0;
        }
        DomainInstance d = openDummyDomain(dummy, def, character, skill);
        if (d == null) {
            c.getSource().sendFailure(Component.literal("The dummy couldn't open " + def.displayName()));
            return 0;
        }
        AbilityCaster pc = Casters.getOrNull(p);
        boolean ready = pc != null && dev.rick.jjk.core.domain.DomainCounter.eligible(pc);
        c.getSource().sendSuccess(() -> Component.literal("Dummy opened " + def.displayName() + " (clash skill " + skill + "). "
                + (ready ? "Press Awakening now to counter!" : "You can't counter yet: pick a character with a domain, fill Awakening (/jjk nocooldown true), and don't be awakened.")), false);
        return 1;
    }

    /** A dummy opens a domain as the given character and offers everyone nearby the counter window. */
    @org.jetbrains.annotations.Nullable
    public static DomainInstance openDummyDomain(TrainingDummy dummy, dev.rick.jjk.core.domain.DomainDefinition def, String character, float skill) {
        JJKCharacter ch = Characters.get(character);
        if (ch != null && Casters.getOrNull(dummy) == null) CharacterService.assign(dummy, ch);
        dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(dummy, skill);
        dev.rick.jjk.core.anim.Anim.play(dummy, "domain_release");
        DomainInstance d = DomainManager.expand(dummy, def);
        if (d != null) dev.rick.jjk.core.domain.DomainCounter.opening(dummy, def);
        return d;
    }

    public static TrainingDummy spawnDummy(ServerLevel level, Vec3 pos, TrainingDummy.Mode mode, float yaw) {
        TrainingDummy dummy = ModEntities.TRAINING_DUMMY.create(level, EntitySpawnReason.COMMAND);
        if (dummy == null) return null;
        dummy.snapTo(pos.x, pos.y, pos.z, yaw, 0);
        dummy.setMode(mode);
        level.addFreshEntity(dummy);
        return dummy;
    }

    private static int arena(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        BlockPos center = p.blockPosition();
        int r = 20;
        // Flat floor with a clear dome of air above it, and a ring of blocks to knock things into.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                level.setBlock(center.offset(x, -1, z), (x + z) % 2 == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
                for (int y = 0; y < 12; y++) level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                if (x * x + z * z > (r - 1) * (r - 1)) {
                    level.setBlock(center.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    level.setBlock(center.offset(x, 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // Some destructible scenery for Blue/Red/Purple.
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            BlockPos pillar = center.offset((int) (Math.cos(a) * 13), 0, (int) (Math.sin(a) * 13));
            for (int y = 0; y < 5; y++) level.setBlock(pillar.above(y), (i % 2 == 0 ? Blocks.OAK_LOG : Blocks.COBBLESTONE).defaultBlockState(), 2);
            level.setBlock(pillar.above(5), Blocks.OAK_LEAVES.defaultBlockState(), 2);
        }
        Vec3 cp = Vec3.atBottomCenterOf(center);
        spawnDummy(level, cp.add(6, 0, 0), TrainingDummy.Mode.STAND, 90);
        spawnDummy(level, cp.add(6, 0, 2), TrainingDummy.Mode.STAND, 90);
        spawnDummy(level, cp.add(-6, 0, 0), TrainingDummy.Mode.JUMP, -90);
        spawnDummy(level, cp.add(0, 0, 8), TrainingDummy.Mode.FIGHT, 180);
        c.getSource().sendSuccess(() -> Component.literal("Arena built: 2 standing dummies (east), 1 jumping (west), 1 fighting (south)"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c, LivingEntity target) {
        if (target == null) return 0;
        CombatState s = Combat.state(target);
        StringBuilder sb = new StringBuilder(target.getName().getString()).append(": ");
        for (CombatStatus st : CombatStatus.all()) if (s.has(st)) sb.append(st.id).append('(').append(s.get(st)).append(") ");
        if (s.isGuarding()) sb.append("guarding ");
        AbilityCaster caster = Casters.getOrNull(target);
        if (caster != null && caster.character() != null) {
            sb.append(String.format("| %s CE %.0f/%.0f", caster.character().id, caster.energy(), caster.maxEnergy()));
            if (caster.isCasting()) sb.append(" casting ").append(caster.cast().ability.id);
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}
