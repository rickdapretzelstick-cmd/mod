package dev.rick.jjk.progression;

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

/**
 * Eating a Cursed Finger. It is <b>no longer a way to become Yuji</b>: Yuji is earned through his storyline (a village's
 * theater, the Human Earthworm VHS and his personal trial, see {@link dev.rick.jjk.progression.story}). The fingers stay
 * for the Sukuna progression to come:
 * <ul>
 *   <li>The world's legitimate Yuji, its vessel, can hold one: it is absorbed and counted ({@link #FINGERS_EATEN}).</li>
 *   <li>Anyone else is consumed by it and dies (a dedicated damage type that nothing, not a totem nor a technique, can
 *   hold off), whether or not the world has a Yuji yet. Nobody is warned beforehand.</li>
 *   <li>In Creative nothing happens beyond the taste.</li>
 * </ul>
 */
public final class CursedFingerAcquisition {
    /** Progression counter: fingers this player has eaten (their own record, for later progression). */
    public static final String FINGERS_EATEN = "cursed_fingers_eaten";

    private CursedFingerAcquisition() {}

    /** What eating one does to {@code player} (the finger is already gone). */
    public static void eat(ServerPlayer player) {
        if (TechniqueProgression.isSandbox(player)) {
            player.sendOverlayMessage(Component.literal("Creative: the finger does nothing to you.").withStyle(ChatFormatting.GRAY));
            return;
        }
        ServerLevel level = player.level();
        Vec3 at = player.position().add(0, 1, 0);
        TechniqueProgression.addCounter(player, FINGERS_EATEN, 1);
        if (KitOwnership.get(level.getServer()).isOwner(YujiCharacter.ID, player.getUUID())) {
            // The vessel: it settles inside him. Counted for the Sukuna progression to come.
            Fx.play(level, "prog_finger_absorb", at, Vec3.ZERO, 1f, player.getId());
            Fx.sound(level, player.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.7f);
            player.sendSystemMessage(Component.literal("Something ancient settles inside you... and you are still yourself.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            return;
        }
        Fx.play(level, "prog_finger_overload", at, Vec3.ZERO, 1f, player.getId());
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 0.6f);
        // Once the eating has finished (so the rest of their fingers drop with everything else).
        TechniqueProgression.endOfTick(() -> overwhelm(player));
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
