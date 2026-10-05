package dev.rick.jjk.progression.tool.rifle;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /jjk rifle}: the Cursed Rifle's debug tools.
 * <ul>
 *   <li>{@code state}: phase, reserve, output, unlocks, claim;</li>
 *   <li>{@code energy <amount>}: sets the reserve;</li>
 *   <li>{@code phase <phase>}: forces a phase (READY to test a clash answer without charging);</li>
 *   <li>{@code issue}: issues a claimed rifle as a lodge would (retiring your previous one).</li>
 * </ul>
 */
public final class RifleCommand {
    private RifleCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("rifle")
                .then(Commands.literal("state").executes(RifleCommand::state))
                .then(Commands.literal("energy").then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    RifleServer.setEnergy(p, FloatArgumentType.getFloat(c, "amount"));
                    return 1;
                })))
                .then(Commands.literal("phase").then(Commands.argument("phase", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RifleServer.Phase.values()).map(x -> x.name().toLowerCase(Locale.ROOT)), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            RifleServer.forcePhaseForTest(p, RifleServer.Phase.valueOf(StringArgumentType.getString(c, "phase").toUpperCase(Locale.ROOT)));
                            return 1;
                        })))
                .then(Commands.literal("issue").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getInventory().add(RifleClaims.issue(p));
                    c.getSource().sendSuccess(() -> Component.literal("Issued a claimed Cursed Rifle (any earlier one of yours is retired)."), true);
                    return 1;
                }));
    }

    private static int state(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String s = "phase " + RifleServer.phase(p) + ", reserve " + Math.round(RifleServer.energy(p)) + "/" + Math.round(RifleServer.cfg().capacity)
                + ", output " + String.format(Locale.ROOT, "%.2f", RifleRules.output(p)) + " (half-width " + String.format(Locale.ROOT, "%.2f", RifleRules.half(RifleRules.output(p)))
                + "), beam " + (RifleRules.beamUnlocked(p) ? "learned" : "not learned") + ", Maximum Output " + (RifleRules.maximumOutput(p) ? "yes" : "no")
                + ", shot " + String.format(Locale.ROOT, "%.1f", RifleRules.shotDamage(p)) + " dmg / " + RifleRules.shotInterval(p) + "t / " + String.format(Locale.ROOT, "%.1f", RifleRules.shotCost(p))
                + " cost, live claim " + (RifleClaims.carriesLive(p) ? "carried" : RifleClaims.everIssued(p) ? "not carried" : "none");
        c.getSource().sendSuccess(() -> Component.literal(s), false);
        return 1;
    }
}
