package dev.rick.jjk.progression;

import dev.rick.jjk.progression.block.CursedCauldronBlock;
import dev.rick.jjk.progression.worldgen.ProgressionWorldgen;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Survival progression set-up (both sides): blocks, items, brewing, the cauldron, world generation, perception, curse
 * hostility, the battle-room encounters, the kit registry and its commands.
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
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> ProgressionCommand.register(dispatcher));
    }
}
