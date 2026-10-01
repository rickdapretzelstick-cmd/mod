package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rika (JJS special; uninterruptible). The first press manifests her partly at his right, by his side. After that:
 * <ul>
 *   <li>a press switches to her moveset (a faint glow from his ring): his movement keys fly her about, even while he is
 *       stunned, up to 100 studs from him;</li>
 *   <li>pressed again (or once one of her moves is used) his own moveset comes back and she returns to his side, unless
 *       the special is held, which leaves her where she is, or he is inside a foreign domain she isn't in;</li>
 *   <li>a double press dismisses her (not while fully manifested: then she stays until the Awakening ends).</li>
 * </ul>
 * Whoever the cursor is on when it's pressed becomes her target.
 */
public final class RikaAbility extends Ability {
    public static final String ID = "rika";
    /** Two presses this close are a double press. */
    static final int DOUBLE_PRESS = 7;
    /** Held this long while recalling her: she stays where she is. */
    static final int HOLD_TICKS = 6;

    public RikaAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
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
    public float cost(AbilityCaster caster) {
        return YutaState.of(caster.owner).rika() == null ? YutaCombat.cfg().rikaSummonCost : 0;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        LivingEntity user = ctx.user();
        AbilityCaster caster = ctx.caster();
        YutaState s = YutaState.of(user);
        long now = ctx.level().getGameTime();
        LivingEntity aimed = HakariCombat.aim(user, RikaEntity.MAX_RANGE, ctx.targetHint());
        RikaEntity r = s.rika();
        if (r == null) {
            r = YutaCombat.summonRika(user);
            s.specialPressedAt = now;
            if (aimed != null) YutaCombat.setTarget(user, aimed);
            Anim.playOn(r, "rika_summon");
            // "Yuta..."
            Fx.play(ctx.level(), "rika_voice", r.position().add(0, 2.4, 0), Vec3.ZERO, 1f, r.getId());
            caster.markDirty();
            YutaSync.send(user);
            return null;
        }
        if (aimed != null) YutaCombat.setTarget(user, aimed);
        boolean doublePress = now - s.specialPressedAt <= DOUBLE_PRESS;
        s.specialPressedAt = now;
        if (doublePress && !caster.isAwakened()) {
            dismiss(user);
            caster.markDirty();
            return null;
        }
        if (!s.rikaMode) {
            s.rikaMode = true;
            r.recall();
            r.set(RikaEntity.PILOTED, true);
            Fx.play(ctx.level(), "rika_ring_glow", user.position().add(0, 1.1, 0), Vec3.ZERO, 1f, user.getId());
            caster.markDirty();
            YutaSync.send(user);
            return null;
        }
        // Back to his own moveset: she returns, unless the key stays held.
        s.rikaMode = false;
        r.set(RikaEntity.PILOTED, false);
        caster.markDirty();
        YutaSync.send(user);
        // She holds still for a moment: released quickly she comes back, held she stays.
        r.station();
        if (outsideForeignDomain(user, r)) return null;
        return new Recall(this, ctx, r);
    }

    /** He is in someone else's domain and she isn't: she stays where she is. */
    private static boolean outsideForeignDomain(LivingEntity user, RikaEntity r) {
        if (!DomainManager.isInsideEnemyDomain(user) || !(user.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
        for (var d : DomainManager.at(sl, user.position())) {
            if (d.owner != user && !DomainManager.at(sl, r.position()).contains(d)) return true;
        }
        return false;
    }

    /** She thins away (the double press, or the Awakening ending). */
    public static void dismiss(LivingEntity user) {
        YutaState s = YutaState.of(user);
        RikaEntity r = s.rika();
        s.rikaMode = false;
        if (r != null) {
            if (user.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                Fx.play(sl, "rika_dismiss", r.position().add(0, 1.6, 0), Vec3.ZERO, 1f, r.getId());
            }
            r.discard();
        }
        s.rika = null;
        YutaSync.send(user);
    }

    /** Watches the key after switching back: held, she is left where she is; released, she comes back. */
    static final class Recall extends AbilityInstance {
        private final RikaEntity rika;

        Recall(Ability ability, AbilityContext ctx, RikaEntity rika) {
            super(ability, ctx);
            this.rika = rika;
        }

        @Override
        public void tick() {
            if (!held) {
                rika.recall();
                finish();
                return;
            }
            if (age >= HOLD_TICKS) {
                Fx.play(level, "rika_station", rika.position().add(0, 1.6, 0), Vec3.ZERO, 1f, rika.getId());
                finish();
            }
        }

        @Override
        public boolean exclusive() {
            return false;
        }

        @Override
        public boolean uninterruptible() {
            return true;
        }
    }
}
