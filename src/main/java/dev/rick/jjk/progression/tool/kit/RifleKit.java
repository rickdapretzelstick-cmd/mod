package dev.rick.jjk.progression.tool.kit;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.progression.tool.rifle.RifleRules;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Cursed Rifle's moveset (the one unique cursed tool so far: one in the world). Its shots, reserve and beam are the
 * rifle's own ({@link RifleServer}); equipped, the keys drive them.
 * <ol>
 *   <li><b>Snap Shot</b> (1): a hip shot at once (wide sway).</li>
 *   <li><b>Aimed Shot</b> (2, hold): raise the scope, let it settle, let go to fire.</li>
 *   <li><b>Suppressing Volley</b> (3, learned): three quick shots, each paying the reserve.</li>
 *   <li><b>Lens Flare</b> (4, learned): one of the arms' lenses flashes at what you aim at: anyone close to it is
 *   blinded and staggered.</li>
 *   <li><b>Stock Bash</b> (R): a shove with the stock that gives you room.</li>
 *   <li><b>Unfolding Array</b> (G, the major node; hold): the beam. The arms deploy and charge while held; let go once
 *   ready to fire. Maximum Output is its capstone.</li>
 * </ol>
 */
public final class RifleKit extends CursedToolKit {
    public RifleKit() {
        super(CursedTools.CURSED_RIFLE, new MeleeMoveset("", "", 0.7f, 1.2f, 0.95f));
        bind(AbilitySlot.SKILL_1, new Snap());
        bind(AbilitySlot.SKILL_2, new Aimed());
        bind(AbilitySlot.SKILL_3, new Volley());
        bind(AbilitySlot.SKILL_4, new Flare());
        bind(AbilitySlot.SKILL_5, new Bash());
        bind(AbilitySlot.ULTIMATE, new Array());
    }

    static final class Snap extends ToolMove {
        Snap() {
            super("rf_snap", CursedTools.CURSED_RIFLE, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return 4;
        }

        @Override
        public @Nullable String checkActivation(AbilityContext ctx) {
            if (!(ctx.user() instanceof ServerPlayer p) || RifleServer.busy(p)) return "busy";
            return super.checkActivation(ctx);
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            if (ctx.user() instanceof ServerPlayer p) RifleServer.kitShot(p);
            return null;
        }
    }

    static final class Aimed extends ToolMove {
        Aimed() {
            super("rf_aimed", CursedTools.CURSED_RIFLE, null);
        }

        @Override
        public Kind kind() {
            return Kind.HOLD;
        }

        @Override
        protected int baseCooldown() {
            return 4;
        }

        @Override
        public @Nullable String checkActivation(AbilityContext ctx) {
            if (!(ctx.user() instanceof ServerPlayer p) || RifleServer.busy(p)) return "busy";
            return super.checkActivation(ctx);
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            if (!(ctx.user() instanceof ServerPlayer p) || !RifleServer.kitAim(p)) return null;
            return new Held(this, ctx, RifleServer.Phase.AIM);
        }
    }

    /** A rifle phase held through a kit key: the key's release is the rifle's release. Not exclusive (the rifle runs it). */
    static final class Held extends ToolMove.Instance {
        private final RifleServer.Phase start;

        Held(Ability a, AbilityContext ctx, RifleServer.Phase start) {
            super(a, ctx);
            this.start = start;
        }

        @Override
        public void start() {
            setPhase(0, 0);
        }

        @Override
        public boolean exclusive() {
            return false;
        }

        @Override
        public boolean longAction() {
            return true;
        }

        @Override
        public void release() {
            super.release();
            if (user instanceof ServerPlayer p) RifleServer.kitRelease(p);
        }

        @Override
        public void tick() {
            // Over once the rifle is back at rest (fired, cancelled, cooled down).
            if (user instanceof ServerPlayer p && age > 2 && RifleServer.phase(p) == RifleServer.Phase.IDLE) finish();
            if (age > 1200) finish();
        }

        @Override
        public void interrupt(String reason) {
            if (user instanceof ServerPlayer p) RifleServer.kitRelease(p);
            super.interrupt(reason);
        }
    }

    static final class Volley extends ToolMove {
        Volley() {
            super("rf_volley", CursedTools.CURSED_RIFLE, "volley");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().rfVolleyCooldown;
        }

        @Override
        public @Nullable String checkActivation(AbilityContext ctx) {
            if (!(ctx.user() instanceof ServerPlayer p) || RifleServer.busy(p)) return "busy";
            return super.checkActivation(ctx);
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new ToolMove.Instance(this, ctx) {
                private int fired;

                @Override
                public void start() {
                    setPhase(0, cfg().rfVolleyShots * 4 + 2);
                }

                @Override
                public float movementMultiplier() {
                    return 0.6f;
                }

                @Override
                public void tick() {
                    if (age % 4 == 1 && fired < cfg().rfVolleyShots && user instanceof ServerPlayer p) {
                        fired++;
                        // Each round cycles the bolt on its own; the volley ignores the interval between them.
                        p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(RifleServer.weapon(p)));
                        RifleServer.kitShot(p);
                    }
                    if (age >= cfg().rfVolleyShots * 4 + 2) finish();
                }
            };
        }
    }

    static final class Flare extends ToolMove {
        Flare() {
            super("rf_flare", CursedTools.CURSED_RIFLE, "lens_flare");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().rfFlareCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            LivingEntity user = ctx.user();
            Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
            var clip = ctx.level().clip(new ClipContext(eye, eye.add(look.scale(32)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            Vec3 at = clip.getLocation();
            Fx.play(ctx.level(), "rifle_ready", at, look.scale(-1), 1f, user.getId());
            Fx.sound(ctx.level(), at, SoundEvents.BEACON_POWER_SELECT, 1.4f, 2f);
            for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(at, 4.0 * param(user, "radius")), 0, false)) {
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
                Statuses.apply(t, CombatStatus.HITSTUN, 16);
                var c = dev.rick.jjk.core.ability.Casters.getOrNull(t);
                if (c != null) c.interrupt("stunned");
            }
            return null;
        }
    }

    static final class Bash extends ToolMove {
        Bash() {
            super("rf_bash", CursedTools.CURSED_RIFLE, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return 70;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new ToolMove.Instance(this, ctx) {
                @Override
                public void start() {
                    Anim.play(user, "yuta_steel_jab");
                    setPhase(0, 8);
                }

                @Override
                public void tick() {
                    if (age == 2) {
                        Vec3 dir = HakariCombat.flat(user);
                        for (LivingEntity t : HakariCombat.front(user, 2.2, 1.6, 2.0)) {
                            HakariCombat.hit(strike(user, 3f).origin(user.getEyePosition()).knockback(Knockback.directional(dir, 1.1, 0.25)).hitstun(12)
                                    .fx("hit_light", 0.9f).build(), t);
                        }
                    }
                    if (age >= 8) finish();
                }
            };
        }
    }

    static final class Array extends ToolMove {
        Array() {
            super("rf_array", CursedTools.CURSED_RIFLE, "beam");
        }

        @Override
        public Kind kind() {
            return Kind.HOLD;
        }

        @Override
        protected int baseCooldown() {
            return 4;
        }

        @Override
        public @Nullable String checkActivation(AbilityContext ctx) {
            if (!(ctx.user() instanceof ServerPlayer p)) return "busy";
            if (!RifleRules.beamUnlocked(p)) return "mastery";
            if (RifleServer.phase(p) != RifleServer.Phase.IDLE && RifleServer.phase(p) != RifleServer.Phase.AIM) return "busy";
            return null;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            if (!(ctx.user() instanceof ServerPlayer p) || !RifleServer.kitBeam(p)) return null;
            return new Held(this, ctx, RifleServer.Phase.DEPLOY);
        }
    }

    @SuppressWarnings("unused")
    private static final AttackTag UNUSED = AttackTag.PROJECTILE;
}
