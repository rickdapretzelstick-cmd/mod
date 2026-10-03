package dev.rick.jjk.progression.prison;

import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.progression.KitAcquisition;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.TechniqueProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Coming out of the Prison Realm after a genuine seal. The first player in the world to be sealed and then released
 * (by escaping alone or by a rescue from outside) becomes its Gojo, through the same atomic kit claim as every other
 * path ({@link TechniqueProgression#acquire}): once Gojo is anyone's, nobody else gets him this way, and nothing about the
 * release changes that. Everyone after is simply let out.
 */
public final class PrisonRealmAcquisition implements KitAcquisition {
    public static final PrisonRealmAcquisition INSTANCE = new PrisonRealmAcquisition();
    /** Progression counter: times this player has come out of the Prison Realm. */
    public static final String RELEASES = "prison_realm_releases";

    private PrisonRealmAcquisition() {}

    @Override
    public String id() {
        return "prison_realm";
    }

    @Override
    public String kit() {
        return GojoCharacter.ID;
    }

    @Override
    public void onClaimed(ServerPlayer player) {
        TechniqueProgression.addCounter(player, RELEASES, 1);
        Vec3 at = player.position().add(0, 1, 0);
        Fx.play(player.level(), "prog_prison_gojo", at, Vec3.ZERO, 1f, player.getId());
        Fx.sound(player.level(), at, SoundEvents.BEACON_ACTIVATE, 1f, 1.4f);
        Fx.sound(player.level(), at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
        player.sendSystemMessage(Component.literal("You stepped out of the Prison Realm... and the world looks different through your eyes. You are Gojo.")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
    }

    @Override
    public void onAlreadyOwned(ServerPlayer player) {
        TechniqueProgression.addCounter(player, RELEASES, 1);
        player.sendOverlayMessage(Component.literal("Out of the Prison Realm.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onTaken(ServerPlayer player, @Nullable KitOwnership.Owner owner) {
        TechniqueProgression.addCounter(player, RELEASES, 1);
        player.sendOverlayMessage(Component.literal("Out of the Prison Realm.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onFailed(ServerPlayer player) {
        player.sendOverlayMessage(Component.literal("Out of the Prison Realm.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onSandbox(ServerPlayer player) {
        // Creative: try Gojo out without claiming anything (the release itself is the same).
        CharacterService.assign(player, Characters.get(GojoCharacter.ID));
        player.sendOverlayMessage(Component.literal("Creative: Gojo for testing (not claimed)").withStyle(ChatFormatting.GRAY));
    }
}
