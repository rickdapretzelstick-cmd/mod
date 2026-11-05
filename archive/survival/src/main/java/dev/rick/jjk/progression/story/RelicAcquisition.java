package dev.rick.jjk.progression.story;

import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.StoryPayload;
import dev.rick.jjk.progression.KitAcquisition;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.TechniqueProgression;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Completing a character's personal trial with that character's live relic: the one way a base kit is earned now. It
 * claims the kit through the same atomic one-owner registry as every path ({@link TechniqueProgression#acquire}), binds
 * the relic to its new owner ({@link UniqueRelics#bind}), and grants only the base kit: the Awakening stays closed
 * until that character's own later storyline opens it.
 */
public final class RelicAcquisition implements KitAcquisition {
    /** Progression counter: personal trials completed. */
    public static final String TRIALS = "personal_trials_completed";

    private final CharacterStory story;

    public RelicAcquisition(CharacterStory story) {
        this.story = story;
    }

    @Override
    public String id() {
        return "storyline";
    }

    @Override
    public String kit() {
        return story.kit();
    }

    @Override
    public void onClaimed(ServerPlayer player) {
        TechniqueProgression.addCounter(player, TRIALS, 1);
        UniqueRelics.get(player.level().getServer()).bind(story.relicKey(), player.getUUID());
        Vec3 at = player.position().add(0, 1, 0);
        Fx.play(player.level(), "prog_infuse_done", at, Vec3.ZERO, 2f, player.getId());
        Fx.sound(player.level(), at, SoundEvents.BEACON_ACTIVATE, 1f, 1.2f);
        Fx.sound(player.level(), at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
        ServerPlayNetworking.send(player, new StoryPayload(StoryPayload.CARD, story.name().toUpperCase(java.util.Locale.ROOT),
                "You have earned the right to begin.", story.color(), 110));
        player.sendSystemMessage(Component.literal("The world remembers this: you are its " + story.name() + ". "
                + "Your technique is yours now, and it is only the beginning.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public void onAlreadyOwned(ServerPlayer player) {
        player.sendOverlayMessage(Component.literal("It has nothing more to show you. Not yet.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onTaken(ServerPlayer player, @Nullable KitOwnership.Owner owner) {
        player.sendSystemMessage(Component.literal("Whatever lived in it has already chosen someone else.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void onFailed(ServerPlayer player) {
        player.sendOverlayMessage(Component.literal("The relic flickers and goes still. Try again.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onSandbox(ServerPlayer player) {
        // Creative: try the character out without claiming anything (the same as picking them on the K screen).
        CharacterService.assign(player, Characters.get(story.kit()));
        player.sendOverlayMessage(Component.literal("Creative: " + story.name() + " for testing (not claimed)").withStyle(ChatFormatting.GRAY));
    }
}
