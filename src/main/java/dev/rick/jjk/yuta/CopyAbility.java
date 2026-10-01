package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Copy (JJS True Love, 15s; 25s for an Awakening move). The technique picked on the Copy Wheel. His own, from the
 * start, is Cursed Speech: the Snake Eyes and Fangs marks around his mouth, and everyone within 35 studs is commanded
 * "動くな!" ("Don't move!") and stunned in place for 2.5 seconds (360-blockable; can't reach anyone ragdolled). The others
 * are the original sorcerer's own moves with Yuta as their user. Each technique keeps its own cooldown.
 */
public final class CopyAbility extends Ability {
    public static final String ID = "copy";
    private static final int SPEECH_WINDUP = 12;

    public CopyAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Nullable
    private static Copies.Technique selected(AbilityCaster caster) {
        return Copies.ALL.get(YutaState.of(caster.owner).selected);
    }

    @Override
    public float cost(AbilityCaster caster) {
        Copies.Technique t = selected(caster);
        return t != null && t.ability() != null ? t.ability().cost(caster) : 40f;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        Copies.Technique t = selected(caster);
        return t != null && t.awakened() ? YutaCombat.cfg().copyAwakenedCooldown : YutaCombat.cfg().copyCooldown;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        YutaState s = YutaState.of(ctx.user());
        if (s.copyReadyAt.getOrDefault(s.selected, 0L) > ctx.level().getGameTime() && !ctx.caster().noCost()) return "cooldown";
        Copies.Technique t = selected(ctx.caster());
        if (t == null) return "no_technique";
        return t.ability() != null ? t.ability().checkActivation(ctx) : null;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaState s = YutaState.of(ctx.user());
        Copies.Technique t = selected(ctx.caster());
        s.copyReadyAt.put(s.selected, ctx.level().getGameTime() + cooldown(ctx.caster()));
        Fx.play(ctx.level(), "copy_use", ctx.user().position().add(0, 1.2, 0), Vec3.ZERO, 1f, ctx.user().getId());
        YutaSync.send(ctx.user());
        if (t == null || t.ability() == null) return new Speech(this, ctx);
        // The original move, with Yuta as its user.
        return t.ability().activate(ctx);
    }

    /** Cursed Speech: "Don't move!" */
    static final class Speech extends AbilityInstance {
        Speech(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        @Override
        public void start() {
            Anim.play(user, "yuta_cursed_speech");
            setPhase(0, SPEECH_WINDUP);
            // The Snake Eyes and Fangs marks appear around his mouth.
            Fx.play(level, "speech_marks", user.getEyePosition(), Vec3.ZERO, 1f, user.getId());
        }

        @Override
        public void tick() {
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            if (age == SPEECH_WINDUP) {
                double r = YutaCombat.cfg().speechRadius;
                Fx.play(level, "speech_dont_move", user.getEyePosition(), HakariCombat.flat(user), (float) r, user.getId());
                Fx.shake(level, user.position(), r * 2, 0.4f, 8);
                for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(user.position().add(0, 1, 0), r), 0.3, false)) {
                    if (YutaCombat.ragdolled(t)) continue;
                    HakariCombat.hit(YutaCombat.strike(user, CopyAbility.ID, 0, false).tag(AttackTag.BLOCKABLE_360, AttackTag.TECHNIQUE)
                            .knockback(Knockback.set(Vec3.ZERO)).hitstun(YutaCombat.cfg().speechStun).noComboScaling()
                            .fx("speech_bound", 1f).build(), t);
                }
            }
            if (age >= SPEECH_WINDUP + 10) finish();
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }
    }
}
