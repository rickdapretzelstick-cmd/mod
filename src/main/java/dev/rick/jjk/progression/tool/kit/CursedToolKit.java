package dev.rick.jjk.progression.tool.kit;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.tool.CursedToolDefinition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A cursed tool's moveset: the same shape as a technique's kit (it is one, to the ability framework), bound to the
 * same keys (1-4 its moves, R its special and the R variants, G its ultimate, M1 its basic attacks), but coming from the
 * equipped weapon rather than the sorcerer. It costs no cursed energy (an ordinary person has none to spend): its moves
 * are paced by cooldowns, and its tree ({@code tool/<id>}) decides which moves, variants and ultimate are learned and
 * how they behave.
 *
 * <p>R variants: pressing R during the first few ticks of one of the kit's moves turns that move into its variant, once
 * {@code tool.<id>.<move>_r} is learned (the R key's own move then goes on cooldown, as with a technique's).
 */
public abstract class CursedToolKit extends JJKCharacter {
    public final CursedToolDefinition def;
    private final MeleeMoveset melee;

    protected CursedToolKit(CursedToolDefinition def, MeleeMoveset melee) {
        super("tool/" + def.id());
        this.def = def;
        this.melee = melee;
    }

    /** Whether this kit has the ability with this id. */
    public boolean has(String abilityId) {
        for (Ability a : abilitiesAllModes()) if (a.id.equals(abilityId)) return true;
        return false;
    }

    public boolean unlocked(LivingEntity e, String name) {
        return Mastery.unlocked(e, def.unlockKey(name));
    }

    public double param(LivingEntity e, String name) {
        return Mastery.param(e, def.paramKey(name));
    }

    @Override
    public boolean interceptInput(AbilityCaster caster, AbilitySlot slot, Ability ability, @Nullable Entity targetHint) {
        if (slot != AbilitySlot.SKILL_5) return false;
        if (!(caster.cast() instanceof ToolMove.Instance inst) || inst.isFinished() || !has(inst.ability.id)) return false;
        if (inst.age() > JJKConfig.get().cursedTools.variantWindow || !inst.hasVariant()) return false;
        if (!unlocked(caster.owner, inst.ability.id + "_r")) return false;
        if (!caster.isReady(slot)) return false;
        if (!inst.variant(targetHint)) return false;
        caster.startCooldown(slot, ability.cooldown(caster));
        return true;
    }

    @Override
    public float maxEnergy() {
        return 0;
    }

    @Override
    public float regenPerSecond() {
        return 0;
    }

    @Override
    public MeleeMoveset melee() {
        return melee;
    }

    @Override
    public String displayName() {
        return def.displayName();
    }

    @Override
    public String title() {
        return switch (def.rarity()) {
            case UNIQUE -> "Unique Cursed Tool";
            case RARE -> "Rare Cursed Tool";
            default -> "Cursed Tool";
        };
    }

    @Override
    public String description() {
        return def.description();
    }

    /** The ability on a slot of this kit (its single moveset). */
    @Nullable
    public Ability move(AbilitySlot slot) {
        return ability(slot, BASE);
    }
}
