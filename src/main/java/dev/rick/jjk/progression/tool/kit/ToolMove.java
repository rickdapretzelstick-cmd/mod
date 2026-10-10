package dev.rick.jjk.progression.tool.kit;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.tool.CursedToolDefinition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One move of a cursed tool's kit. It is a physical action (never blocked by technique locks, costs no cursed energy),
 * may need a node of the tool's tree before it can be used at all ({@link #unlock}), and its cooldown and damage follow
 * the tree's parameters ({@code tool.<id>.<move>_cooldown}, {@code tool.<id>.<move>_damage}).
 */
public abstract class ToolMove extends Ability {
    protected final CursedToolDefinition def;
    /** The tree node that teaches this move (null: the weapon comes with it). */
    @Nullable protected final String unlock;

    protected ToolMove(String id, CursedToolDefinition def, @Nullable String unlock) {
        super(id);
        this.def = def;
        this.unlock = unlock;
    }

    protected static JJKConfig.CursedToolKits cfg() {
        return JJKConfig.get().cursedTools;
    }

    @Override
    public boolean isTechnique() {
        return false;
    }

    /** The base cooldown before Mastery. */
    protected abstract int baseCooldown();

    @Override
    public int cooldown(AbilityCaster caster) {
        return Math.max(1, (int) Math.round(baseCooldown() * param(caster.owner, "cooldown")));
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        if (unlock != null && !Mastery.unlocked(ctx.user(), def.unlockKey(unlock))) return "mastery";
        return null;
    }

    public boolean learned(LivingEntity e) {
        return unlock == null || Mastery.unlocked(e, def.unlockKey(unlock));
    }

    /** {@code tool.<id>.<move>_<name>}: this move's parameter on the tool's tree (1 untouched). */
    protected double param(LivingEntity e, String name) {
        return Mastery.param(e, def.paramKey(id + "_" + name));
    }

    /** {@code tool.<id>.<move>_<name>}: whether a node of this move is learned. */
    protected boolean has(LivingEntity e, String name) {
        return Mastery.unlocked(e, def.unlockKey(id + "_" + name));
    }

    /** A hit of this move: the move's id (so it is booked to the tool's tree), its damage scaled by its parameter. */
    protected Hit.Builder strike(LivingEntity user, float damage) {
        return Hit.builder(user, id).damage(damage * (float) param(user, "damage")).tag(AttackTag.MELEE);
    }

    /** A running move: R during its first ticks may turn it into its variant. */
    public abstract static class Instance extends AbilityInstance {
        private boolean varied;

        protected Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        /** Whether this move has an R variant at all. */
        public boolean hasVariant() {
            return false;
        }

        /** Turn into the R variant (once). Return true if it did. */
        public final boolean variant(@Nullable Entity hint) {
            if (varied) return false;
            varied = onVariant(hint);
            return varied;
        }

        protected boolean onVariant(@Nullable Entity hint) {
            return false;
        }

        public boolean varied() {
            return varied;
        }
    }
}
