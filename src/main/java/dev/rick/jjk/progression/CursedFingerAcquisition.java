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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Eating a Cursed Finger. The first player to do it in a world becomes its Yuji, permanently. A different player who eats
 * one afterwards gets nothing and is consumed by it: they die (the {@code jjk:cursed_overload} damage type, which nothing
 * in this mod lets them survive). Yuji's own owner eating another is kept apart for later Sukuna progression: for now it
 * is only counted. Nobody is warned beforehand.
 *
 * <p>The Cursed Finger item itself is not in this change (see TODO(26.3 API) in the commit message): whatever eats one
 * calls {@code TechniqueProgression.acquire(player, CursedFingerAcquisition.INSTANCE)} once the eating has finished, and
 * {@code /jjk kit acquire cursed_finger <player>} does the same for testing.
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
        Fx.play(level, "sfx:sukuna_awaken", player.getEyePosition(), Vec3.ZERO, 1f, player.getId());
        player.sendOverlayMessage(Component.literal("Something ancient settles inside you... and you are still yourself.")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public void onAlreadyOwned(ServerPlayer player) {
        // Yuji's own vessel: no second claim, no stranger's fate. Counted for the Sukuna progression to come.
        TechniqueProgression.addCounter(player, FINGERS_EATEN, 1);
    }

    @Override
    public void onTaken(ServerPlayer player, @Nullable KitOwnership.Owner owner) {
        TechniqueProgression.addCounter(player, FINGERS_EATEN, 1);
        ServerLevel level = player.level();
        Fx.play(level, "sfx:kokusen", player.getEyePosition(), Vec3.ZERO, 1f, player.getId());
        // Once the eating has finished, so the rest of their fingers drop with everything else.
        TechniqueProgression.endOfTick(() -> overwhelm(player, OVERWHELM_TICKS));
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

    /** How long (ticks) the finger keeps trying if the player can't be hurt yet (just joined or respawned). */
    static final int OVERWHELM_TICKS = 200;

    /**
     * Kills the player outright. The damage type bypasses armour, effects, enchantments, shields, cooldown and
     * invulnerability (data/minecraft/tags/damage_type), and CharacterService never lets a character's state refuse this
     * death (Jackpot, Decadence). A player the game won't let be hurt at all yet (the protection right after joining,
     * which no damage type gets through) is tried again every tick until it lands.
     */
    public static void overwhelm(ServerPlayer player, int triesLeft) {
        if (!player.isAlive() || player.isRemoved()) return;
        ServerLevel level = player.level();
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.CURSED_OVERLOAD, null, null);
        player.hurtServer(level, source, player.getMaxHealth() + player.getAbsorptionAmount() + 1000f);
        if (player.isAlive() && triesLeft > 0) TechniqueProgression.endOfTick(() -> overwhelm(player, triesLeft - 1));
        // TODO(26.3 API): need a guaranteed-kill call (e.g. LivingEntity.die(DamageSource) or a kill helper) as a fallback
        //  in case another mod cancels the damage; nothing in src/ kills an entity outright yet.
    }
}
