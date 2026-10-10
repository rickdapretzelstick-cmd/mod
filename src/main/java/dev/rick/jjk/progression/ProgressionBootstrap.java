package dev.rick.jjk.progression;

import dev.rick.jjk.progression.block.CursedCauldronBlock;
import dev.rick.jjk.progression.worldgen.ProgressionWorldgen;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Survival progression set-up (both sides): blocks, items, brewing, the cauldron, world generation, perception, curse
 * hostility, the battle-room encounters (the Finger Bearer), the kit registry, the character storylines (village events,
 * relics, personal trials) and their commands.
 */
public final class ProgressionBootstrap {
    private ProgressionBootstrap() {}

    public static void init() {
        ProgressionBlocks.init();
        ProgressionItems.init();
        CursedCauldronBlock.registerInteractions();
        // Brewing Soul in a Bottle with a Ghast Tear is a 26.3 data recipe: data/jjk/recipe/brewing/cursed_energy_bottle.json.
        ProgressionWorldgen.init();
        TechniqueProgression.init();
        CurseAggro.init();
        CursedEncounters.init();
        dev.rick.jjk.progression.curse.FingerBearerEncounter.init();
        dev.rick.jjk.progression.prison.PrisonRealm.init();
        dev.rick.jjk.progression.grade.CursedDamage.init();
        dev.rick.jjk.progression.tool.CursedTools.init();
        dev.rick.jjk.progression.mastery.Mastery.init();
        dev.rick.jjk.progression.mastery.CurseRewards.init();
        dev.rick.jjk.progression.investigation.Investigations.init();
        dev.rick.jjk.progression.investigation.CursedCompass.init();
        dev.rick.jjk.progression.story.PersonalTrials.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> ProgressionCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dev.rick.jjk.progression.story.StoryCommand.register(dispatcher));
    }
}
