package dev.rick.jjk.yuji;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * King of Curses (JJS Awakening). Vessel faints and his alter ego takes over at once — "You're such an annoying brat,"
 * the black marks spreading over his face and a red aura rising off him. Uninterruptible. 45 HP healed, 60 seconds.
 */
public final class KingOfCursesAbility extends Ability {
    public static final String ID = "king_of_curses";

    public KingOfCursesAbility() {
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
            /** The takeover: the moment the red wings of cursed energy burst off him (GIF frame 20). */
            private int takeover;

            @Override
            public void start() {
                // Someone nearby is opening a domain: this press answers it (Sukuna and his Malevolent Shrine at once).
                if (dev.rick.jjk.core.domain.DomainCounter.tryCounter(caster)) {
                    finish();
                    return;
                }
                total = YujiCombat.cfg().awakenTicks;
                takeover = 8;
                Statuses.apply(user, CombatStatus.AWAKENING, total + 2);
                Anim.play(user, "sukuna_faint");
                setPhase(0, total);
                Fx.play(level, "sukuna_start", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
            }

            @Override
            public void tick() {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.2, 0));
                if (age == takeover) {
                    caster.enterAwakening();
                    setPhase(1, total - takeover);
                    Anim.play(user, "sukuna_takeover");
                    // "You're such an annoying brat."
                    Fx.play(level, "sukuna_awaken", user.position().add(0, 1.2, 0), Vec3.ZERO, total - takeover, user.getId());
                    Fx.shake(level, user.position(), 40, 1.0f, 20);
                    Fx.flash(level, user.position(), 32, 0x90A00010, 10);
                    float share = YujiCombat.cfg().awakenHeal / (100f * YujiCombat.cfg().maxHealthShare);
                    user.heal(user.getMaxHealth() * share);
                }
                if (age >= total) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0f;
            }

            @Override
            public void interrupt(String reason) {
                if (age >= takeover && !caster.isAwakened()) caster.enterAwakening();
                super.interrupt(reason);
            }
        };
    }
}
