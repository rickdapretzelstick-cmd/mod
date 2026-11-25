package dev.rick.jjk.battle.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rick.jjk.battle.BattleKit;
import dev.rick.jjk.battle.BattleKits;
import dev.rick.jjk.battle.Plan;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts, runs and ends turn-based battles.
 * <pre>
 * /jjk battle ai [yourKit] [theirKit]   fight the AI (a training dummy steps up 6 blocks in front of you)
 * /jjk battle player &lt;target&gt;           fight another player (beta: starts at once)
 * /jjk battle forfeit                   give up
 * </pre>
 * While in a battle, the real-time moves and basic attacks are switched off for that player.
 */
public final class BattleManager {
    private static final List<BattleSession> SESSIONS = new ArrayList<>();

    private BattleManager() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(BattlePayloads.State.TYPE, BattlePayloads.State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BattlePayloads.BeatStart.TYPE, BattlePayloads.BeatStart.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BattlePayloads.Choice.TYPE, BattlePayloads.Choice.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BattlePayloads.Choice.TYPE, (p, ctx) -> onChoice(ctx.player(), p));
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        // Fighters can't be hurt by the world while a battle runs: their health is the battle's.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> !inBattle(e));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            BattleSession b = of(h.player);
            if (b != null) b.forfeit(b.sideOf(h.player));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (BattleSession b : List.copyOf(SESSIONS)) b.close();
            SESSIONS.clear();
        });
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));
    }

    private static void tick() {
        for (BattleSession b : List.copyOf(SESSIONS)) b.tick();
        SESSIONS.removeIf(b -> b.closed);
    }

    /** The battle {@code e} is in, or null. */
    @Nullable
    public static BattleSession of(LivingEntity e) {
        for (BattleSession b : SESSIONS) if (!b.closed && b.sideOf(e) >= 0) return b;
        return null;
    }

    public static boolean inBattle(LivingEntity e) {
        return of(e) != null;
    }

    public static List<BattleSession> sessions() {
        return List.copyOf(SESSIONS);
    }

    /** Starts a battle between two bodies. Returns it, or null (with why in {@code why[0]}) if either is busy. */
    @Nullable
    public static BattleSession start(LivingEntity a, LivingEntity b, BattleKit ka, BattleKit kb, boolean aiA, boolean aiB, String[] why) {
        if (inBattle(a) || inBattle(b)) {
            why[0] = "Already in a battle";
            return null;
        }
        if (!(a.level() instanceof ServerLevel level) || a.level() != b.level()) {
            why[0] = "Both fighters must be in the same world";
            return null;
        }
        BattleSession s = new BattleSession(level, a, b, ka, kb, aiA, aiB);
        SESSIONS.add(s);
        return s;
    }

    /** Starts a battle against an AI-played training dummy in front of {@code player}. */
    @Nullable
    public static BattleSession startAi(ServerPlayer player, BattleKit mine, BattleKit theirs, String[] why) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 at = player.position().add(look.scale(6.4));
        TrainingDummy d = ModEntities.TRAINING_DUMMY.create(level, EntitySpawnReason.COMMAND);
        if (d == null) {
            why[0] = "Couldn't spawn the opponent";
            return null;
        }
        d.snapTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        d.setPersistenceRequired();
        d.setLabelled(false); // the battle HUD names it
        level.addFreshEntity(d);
        BattleSession s = start(player, d, mine, theirs, false, true, why);
        if (s == null) d.discard();
        return s;
    }

    private static void onChoice(ServerPlayer player, BattlePayloads.Choice c) {
        BattleSession b = of(player);
        if (b == null || b.id != c.session()) return;
        int side = b.sideOf(player);
        if (c.forfeit()) {
            b.forfeit(side);
            return;
        }
        String why = b.choose(side, new Plan(c.chain(), c.boost()));
        if (why != null) player.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.RED));
    }

    /** The player's current character's battle kit, or {@code fallback} if it has none. */
    private static BattleKit kitOf(ServerPlayer p, BattleKit fallback) {
        var caster = Casters.get(p);
        var c = caster == null ? null : caster.character();
        BattleKit k = c == null ? null : BattleKits.get(c.id);
        return k != null ? k : fallback;
    }

    // ------------------------------------------------------------------------------------------------ command

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("battle")
                        .then(Commands.literal("ai").executes(c -> ai(c, null, null))
                                .then(Commands.argument("yours", StringArgumentType.word()).suggests(BattleManager::kits)
                                        .executes(c -> ai(c, StringArgumentType.getString(c, "yours"), null))
                                        .then(Commands.argument("theirs", StringArgumentType.word()).suggests(BattleManager::kits)
                                                .executes(c -> ai(c, StringArgumentType.getString(c, "yours"), StringArgumentType.getString(c, "theirs"))))))
                        .then(Commands.literal("player").then(Commands.argument("target", EntityArgument.player()).executes(BattleManager::pvp)))
                        .then(Commands.literal("forfeit").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            BattleSession b = of(p);
                            if (b == null) {
                                c.getSource().sendFailure(Component.literal("You're not in a battle"));
                                return 0;
                            }
                            b.forfeit(b.sideOf(p));
                            return 1;
                        }))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> kits(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        List<String> ids = new ArrayList<>();
        BattleKits.ids().forEach(ids::add);
        return SharedSuggestionProvider.suggest(ids, b);
    }

    private static int ai(CommandContext<CommandSourceStack> c, @Nullable String yours, @Nullable String theirs) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BattleKit mine = yours != null ? BattleKits.get(yours) : kitOf(p, BattleKits.YUJI);
        if (mine == null) {
            c.getSource().sendFailure(Component.literal("No battle kit '" + yours + "'"));
            return 0;
        }
        BattleKit foe = theirs != null ? BattleKits.get(theirs) : mine == BattleKits.GOJO ? BattleKits.YUJI : BattleKits.GOJO;
        if (foe == null) {
            c.getSource().sendFailure(Component.literal("No battle kit '" + theirs + "'"));
            return 0;
        }
        String[] why = {""};
        if (startAi(p, mine, foe, why) == null) {
            c.getSource().sendFailure(Component.literal(why[0]));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(mine.name + " vs " + foe.name + " — battle start!").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int pvp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayer t = EntityArgument.getPlayer(c, "target");
        if (t == p) {
            c.getSource().sendFailure(Component.literal("Pick someone else"));
            return 0;
        }
        BattleKit mine = kitOf(p, BattleKits.YUJI), theirs = kitOf(t, mine == BattleKits.GOJO ? BattleKits.YUJI : BattleKits.GOJO);
        String[] why = {""};
        if (start(p, t, mine, theirs, false, false, why) == null) {
            c.getSource().sendFailure(Component.literal(why[0]));
            return 0;
        }
        return 1;
    }
}
