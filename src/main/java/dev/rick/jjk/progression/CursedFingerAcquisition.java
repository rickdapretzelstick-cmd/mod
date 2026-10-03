package dev.rick.jjk.progression;

import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.yuji.YujiCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Eating a Cursed Finger. The first player to do it in a world becomes its Yuji, permanently. A different player who eats
 * one afterwards gets nothing and is consumed by it: they die (a dedicated damage type that nothing, not a totem nor a
 * technique, can hold off). Yuji's own owner eating another is kept apart for later Sukuna progression: for now it is
 * only counted.
 *
 * <p>Nobody is warned beforehand: whether Yuji is taken is not something a player is told.
 */
public final class CursedFingerAcquisition implements KitAcquisition {
    public static final CursedFingerAcquisition INSTANCE = new CursedFingerAcquisition();
    /** Progression counter: fingers this player has eaten (their own record, for later progression). */
    public static final String FINGERS_EATEN = "cursed_fingers_eaten";

    private CursedFingerAcquisition() {}

    @Override
    public String id() {
        return "cursed_finger";
    }

    @Override
    public String kit() {
        return YujiCharacter.ID;
    }

    @Override
    public void onClaimed(ServerPlayer player) {
        TechniqueProgression.addCounter(player, FINGERS_EATEN, 1);
        ServerLevel level = player.level();
        Vec3 at = player.position().add(0, 1, 0);
        Fx.play(level, "prog_finger_claim", at, Vec3.ZERO, 1f, player.getId());
        Fx.sound(level, at, SoundEvents.WITHER_SPAWN, 0.6f, 1.6f);
        player.sendSystemMessage(Component.literal("Something ancient settles inside you... and you are still yourself.")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }

    @Override
    public void onAlreadyOwned(ServerPlayer player) {
        // Yuji's own vessel: no second claim, no stranger's fate. Counted for the Sukuna progression to come.
        TechniqueProgression.addCounter(player, FINGERS_EATEN, 1);
        ServerLevel level = player.level();
        Fx.play(level, "prog_finger_absorb", player.position().add(0, 1, 0), Vec3.ZERO, 1f, player.getId());
        Fx.sound(level, player.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.7f);
    }

    @Override
    public void onTaken(ServerPlayer player, @Nullable KitOwnership.Owner owner) {
        TechniqueProgression.addCounter(player, FINGERS_EATEN, 1);
        ServerLevel level = player.level();
        Vec3 at = player.position().add(0, 1, 0);
        Fx.play(level, "prog_finger_overload", at, Vec3.ZERO, 1f, player.getId());
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 0.6f);
        // Once the eating has finished (so the rest of their fingers drop with everything else).
        TechniqueProgression.endOfTick(() -> overwhelm(player));
    }

    @Override
    public void onSandbox(ServerPlayer player) {
        // Creative: try Yuji out without claiming anything (the same as picking him on the K screen).
        CharacterService.assign(player, Characters.get(YujiCharacter.ID));
        player.sendOverlayMessage(Component.literal("Creative: Yuji for testing (not claimed)").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onFailed(ServerPlayer player) {
        player.sendOverlayMessage(Component.literal("The finger crumbles to dust.").withStyle(ChatFormatting.GRAY));
    }

    /** Kills the player outright: no totem, armour, resistance, Jackpot or Decadence holds it off. */
    public static void overwhelm(ServerPlayer player) {
        if (!player.isAlive()) return;
        ServerLevel level = player.level();
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.CURSED_OVERLOAD, null, null);
        player.hurtServer(level, source, player.getMaxHealth() + player.getAbsorptionAmount() + 1000f);
        if (player.isAlive()) {
            // Something still stood in the way (another mod cancelling damage): the death is not optional.
            player.setHealth(0f);
            player.die(source);
        }
    }
}
