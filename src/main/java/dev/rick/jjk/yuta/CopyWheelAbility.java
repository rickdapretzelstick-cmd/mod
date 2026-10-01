package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.core.domain.DomainCounter;
import dev.rick.jjk.core.fx.Fx;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Copy Wheel (JJS True Love, on the Awakening key; uninterruptible). The techniques Copy can use, two pages of four:
 * the skill keys pick one on the open page, the Special turns the page, the Awakening key closes it. The one picked
 * becomes Copy's move (with its own cooldown; the others stay usable meanwhile). Past eight, the newest takes the oldest
 * one's place. All of them are lost on death.
 */
public final class CopyWheelAbility extends Ability {
    public static final String ID = "copy_wheel";
    public static final int PER_PAGE = 4, PAGES = 2;

    public CopyWheelAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public boolean isTechnique() {
        return false;
    }

    @Override
    public boolean usableWhileCasting() {
        return true;
    }

    @Override
    public boolean usableDuringMelee() {
        return true;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        // Someone nearby is opening a domain: the Awakening key answers it.
        if (DomainCounter.tryCounter(ctx.caster())) return null;
        YutaState s = YutaState.of(ctx.user());
        s.wheelOpen = !s.wheelOpen;
        Fx.play(ctx.level(), s.wheelOpen ? "copy_wheel_open" : "copy_wheel_close", ctx.user().position().add(0, 1.2, 0),
                net.minecraft.world.phys.Vec3.ZERO, 1f, ctx.user().getId());
        YutaSync.send(ctx.user());
        return null;
    }

    /** A key pressed while the wheel is open. */
    static void press(AbilityCaster caster, AbilitySlot slot) {
        YutaState s = YutaState.of(caster.owner);
        int i = switch (slot) {
            case SKILL_1 -> 0;
            case SKILL_2 -> 1;
            case SKILL_3 -> 2;
            case SKILL_4 -> 3;
            default -> -1;
        };
        if (slot == AbilitySlot.SKILL_5) {
            s.page = (s.page + 1) % PAGES;
        } else if (i >= 0) {
            int index = s.page * PER_PAGE + i;
            if (index < s.copied.size()) {
                select(caster, s.copied.get(index));
                s.wheelOpen = false;
            }
        }
        if (caster.owner.level() instanceof ServerLevel sl) {
            Fx.play(sl, "copy_wheel_tick", caster.owner.position().add(0, 1.2, 0), net.minecraft.world.phys.Vec3.ZERO, 1f, caster.owner.getId());
        }
        YutaSync.send(caster.owner);
    }

    /** Picks a technique for Copy; its skill box shows that technique's own cooldown. */
    public static void select(AbilityCaster caster, String technique) {
        YutaState s = YutaState.of(caster.owner);
        if (!s.copied.contains(technique)) return;
        s.selected = technique;
        long left = s.copyReadyAt.getOrDefault(technique, 0L) - caster.owner.level().getGameTime();
        caster.resetSlot(AbilitySlot.SKILL_2, JJKCharacter.AWAKENED);
        if (left > 0) caster.startCooldown(AbilitySlot.SKILL_2, JJKCharacter.AWAKENED, (int) left);
        caster.markDirty();
    }
}
