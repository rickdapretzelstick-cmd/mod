package dev.rick.jjk.yuta;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One of Rika's own moves (her moveset, up while the special has her piloted). She does the work: Yuta only motions, so
 * her moves run alongside whatever he is doing, even in the middle of his swings. Using one puts her moveset away and
 * she comes back to his side once she is done (unless the special was held). In base her three moves share one
 * cooldown; fully manifested her four don't.
 */
public abstract class RikaMove extends Ability {
    private final boolean awakened;

    protected RikaMove(String id, boolean awakened) {
        super(id);
        this.awakened = awakened;
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
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
    public @Nullable String checkActivation(AbilityContext ctx) {
        YutaState s = YutaState.of(ctx.user());
        if (s.rika() == null) return "no_rika";
        if (s.rikaBusy(ctx.level().getGameTime())) return "rika_busy";
        return null;
    }

    @Override
    public final @Nullable AbilityInstance activate(AbilityContext ctx) {
        RikaEntity rika = YutaCombat.summonRika(ctx.user());
        LivingEntity target = YutaCombat.rikaTarget(ctx.user(), rika);
        if (target != null) YutaCombat.setTarget(ctx.user(), target);
        if (!awakened) YutaCombat.shareBaseRikaCooldown(ctx.caster(), cooldown(ctx.caster()));
        YutaCombat.backToYuta(ctx.caster());
        return create(ctx, rika, target);
    }

    protected abstract AbilityInstance create(AbilityContext ctx, RikaEntity rika, @Nullable LivingEntity target);

    /** A move of hers in progress: she is busy until it ends. */
    protected abstract static class Instance extends AbilityInstance {
        protected final RikaEntity rika;
        @Nullable protected LivingEntity target;
        private final int busyFor;

        protected Instance(Ability ability, AbilityContext ctx, RikaEntity rika, @Nullable LivingEntity target, int busyFor) {
            super(ability, ctx);
            this.rika = rika;
            this.target = target;
            this.busyFor = busyFor;
        }

        @Override
        public void start() {
            YutaCombat.busy(user, busyFor);
            begin();
        }

        protected abstract void begin();

        @Override
        public final void tick() {
            if (rika.isRemoved()) {
                finish();
                return;
            }
            step();
        }

        protected abstract void step();

        @Override
        public boolean exclusive() {
            return false;
        }

        @Override
        public boolean uninterruptible() {
            return true;
        }

        /** Hovers her to a spot facing the target from her side, {@code dist} away (and {@code up} above its feet). */
        protected void approach(LivingEntity t, double dist, double up, double speed) {
            Vec3 from = rika.position().subtract(t.position());
            Vec3 flat = new Vec3(from.x, 0, from.z);
            flat = flat.lengthSqr() < 1e-4 ? HakariCombat.flat(user).scale(-1) : flat.normalize();
            rika.moveTo(t.position().add(flat.scale(dist + t.getBbWidth() / 2)).add(0, up, 0), speed, 3);
        }

        /** Where her fist comes down in front of her (for a move without a target). */
        protected Vec3 front(double dist) {
            return rika.position().add(facing().scale(dist));
        }

        /** The way she faces, flat. */
        protected Vec3 facing() {
            float yaw = rika.getYRot() * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        }

        protected void anim(String name) {
            Anim.playOn(rika, name);
        }

        protected boolean airborne(LivingEntity t) {
            return Combat.isAirborne(t) || !t.onGround() && t.fallDistance > 0.3 || Combat.has(t, dev.rick.jjk.core.combat.CombatStatus.LAUNCHED);
        }

        /** She killed them: their technique is Yuta's to copy. */
        protected void copyOnKill(HitResult r) {
            if (!r.target().isAlive()) YutaCombat.copyFrom(user, r.target());
        }

        /** She attacked them: their technique is Yuta's to copy. */
        protected void copyOnHit(HitResult r) {
            if (r.outcome().contacted()) YutaCombat.copyFrom(user, r.target());
        }

        @Override
        public void end() {
            YutaCombat.free(user);
        }
    }
}
