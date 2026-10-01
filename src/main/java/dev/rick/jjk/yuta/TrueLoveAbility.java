package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainCounter;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * True Love (JJS Awakening; uninterruptible). "Come, Rika. Give me everything." He tears off his necklace and puts its
 * ring on to keep his connection with Rika, who manifests fully behind him, pulls a steel casing out of her chest and
 * wraps it around his right arm. 25 HP healed, 60 seconds.
 */
public final class TrueLoveAbility extends Ability {
    public static final String ID = "true_love";
    /** GIF beats: the ring goes on, Rika manifests, the casing comes out of her chest and wraps his arm. */
    static final int RING = 14, MANIFEST = 22, CASING = 40;

    public TrueLoveAbility() {
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
    public @Nullable String checkActivation(AbilityContext ctx) {
        AbilityCaster c = ctx.caster();
        if (c.isAwakened()) return "already_awakened";
        return c.noCost() || c.awakening() >= c.maxAwakening() ? null : "meter_not_full";
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private int total;

            @Override
            public void start() {
                // Someone nearby is opening a domain: this press answers it.
                if (DomainCounter.tryCounter(caster)) {
                    finish();
                    return;
                }
                total = YutaCombat.cfg().trueLoveTicks;
                Statuses.apply(user, CombatStatus.AWAKENING, total + 2);
                YutaState s = YutaState.of(user);
                s.rikaMode = false;
                Anim.play(user, "yuta_true_love");
                setPhase(0, total);
                // "Come, Rika. Give me everything."
                Fx.play(level, "true_love_start", user.position().add(0, 1.2, 0), Vec3.ZERO, total, user.getId());
            }

            @Override
            public void tick() {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.2, 0));
                if (age == RING) {
                    Fx.play(level, "true_love_ring", user.position().add(0, 1.3, 0), Vec3.ZERO, 1f, user.getId());
                }
                if (age == MANIFEST) {
                    caster.enterAwakening();
                    RikaEntity r = YutaCombat.summonRika(user);
                    if (r != null) {
                        r.set(RikaEntity.FULL, true);
                        r.set(RikaEntity.PILOTED, false);
                        r.recall();
                        Anim.playOn(r, "rika_true_love");
                    }
                    Fx.play(level, "true_love_manifest", user.position().add(0, 1.4, 0), Vec3.ZERO, 1f, user.getId());
                    Fx.shake(level, user.position(), 40, 1.0f, 20);
                    Fx.flash(level, user.position(), 32, 0x90F76BFF, 10);
                    float share = YutaCombat.cfg().trueLoveHeal / (100f * YutaCombat.cfg().maxHealthShare);
                    user.heal(user.getMaxHealth() * share);
                }
                if (age == CASING) {
                    Statuses.apply(user, CombatStatus.STEEL_ARM, 20 * 600);
                    YutaState.of(user).fists = true;
                    Statuses.remove(user, CombatStatus.KATANA);
                    Fx.play(level, "steel_arm", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                    caster.markDirty();
                }
                if (age >= total) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0f;
            }

            @Override
            public boolean uninterruptible() {
                return true;
            }

            @Override
            public void interrupt(String reason) {
                if (age >= MANIFEST && !caster.isAwakened()) caster.enterAwakening();
                super.interrupt(reason);
            }

            @Override
            public void end() {
                if (caster.isAwakened()) {
                    Statuses.apply(user, CombatStatus.STEEL_ARM, 20 * 600);
                    YutaState.of(user).fists = true;
                }
                YutaSync.send(user);
            }
        };
    }
}
