package dev.rick.jjk.progression.tool.kit;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.grade.GradedCurse;
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.util.Motion;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slaughter Demon's moveset: a fast blade for someone who has to stay close and keep moving. Basic attacks are the
 * katana's quick chain.
 * <ol>
 *   <li><b>Quickstep</b> (1): dart five blocks through the first foe in line, cutting them. <i>R variant, Return Cut:</i>
 *   dart straight back through them.</li>
 *   <li><b>Flurry</b> (2, learned): four rapid cuts in front, the last staggering. <i>R variant, Rising Flurry:</i> the
 *   last cut launches.</li>
 *   <li><b>Severing Point</b> (3, learned): a lunging thrust; a curse of Grade 3 or weaker on its last quarter is cut
 *   apart outright.</li>
 *   <li><b>Mirror Parry</b> (4, learned): hold a stance for a moment; a blow or a bullet that meets it is turned aside and
 *   answered with a riposte from behind the attacker.</li>
 *   <li><b>Draw Cut</b> (R): a quick draw-and-slash in a short cone. Pressed during a move's opening, it is that move's
 *   R variant instead.</li>
 *   <li><b>Thousand Cuts</b> (G, the major node): flicker between up to five foes nearby, cutting each, and land behind
 *   the last.</li>
 * </ol>
 */
public final class SlaughterDemonKit extends CursedToolKit {
    public SlaughterDemonKit() {
        super(CursedTools.SLAUGHTER_DEMON, new MeleeMoveset("yuta_", "yuta_", 1.0f, 0.9f, 1.1f, 1.0f, true, false, null));
        bind(AbilitySlot.SKILL_1, new Quickstep());
        bind(AbilitySlot.SKILL_2, new Flurry());
        bind(AbilitySlot.SKILL_3, new Sever());
        bind(AbilitySlot.SKILL_4, new Parry());
        bind(AbilitySlot.SKILL_5, new DrawCut());
        bind(AbilitySlot.ULTIMATE, new ThousandCuts());
    }

    private static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /** Darts {@code reach} blocks along {@code dir} (stopped by walls), hitting what's in the path; returns who was met. */
    static List<LivingEntity> dart(LivingEntity user, Vec3 dir, double reach) {
        Vec3 from = user.position().add(0, 0.6, 0);
        var hit = user.level().clip(new ClipContext(from, from.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        double go = hit.getType() == Type.MISS ? reach : Math.max(0, hit.getLocation().distanceTo(from) - 0.6);
        Vec3 to = user.position().add(dir.scale(go));
        List<LivingEntity> met = HitboxQuery.targets(user, HitShape.capsule(from, from.add(dir.scale(go + 0.8)), 0.9), 0.2, true);
        user.teleportTo(to.x, to.y, to.z);
        Motion.set(user, dir.scale(0.3));
        user.fallDistance = 0;
        if (user.level() instanceof net.minecraft.server.level.ServerLevel level) {
            Fx.play(level, "tool_quickstep", from, dir.scale(go), 1f, user.getId());
            Fx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        }
        return met;
    }

    // --- 1: Quickstep ---

    static final class Quickstep extends ToolMove {
        static final String ID = "sd_quickstep";

        Quickstep() {
            super(ID, CursedTools.SLAUGHTER_DEMON, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().sdQuickstepCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final Vec3 dir;
            private Vec3 start;
            private boolean back;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
                dir = ctx.hasMovementInput() ? new Vec3(ctx.inputDirection().x, 0, ctx.inputDirection().z).normalize() : HakariCombat.flat(ctx.user());
                start = ctx.user().position();
            }

            @Override
            public void start() {
                Anim.play(user, "yuta_blade_run");
                setPhase(0, 12);
                cut(dart(user, dir, cfg().sdQuickstepReach * param(user, "reach")), dir);
            }

            private void cut(List<LivingEntity> met, Vec3 d) {
                for (LivingEntity t : met) {
                    HakariCombat.hit(strike(user, cfg().sdQuickstepDamage).knockback(Knockback.directional(d, 0.35, 0.15)).hitstun(14)
                            .origin(user.getEyePosition()).fx("tool_flurry", 0.9f).build(), t);
                }
            }

            @Override
            public boolean hasVariant() {
                return true;
            }

            @Override
            protected boolean onVariant(@Nullable Entity hint) {
                back = true;
                return true;
            }

            @Override
            public void tick() {
                if (back && age == 4) {
                    // Return Cut: straight back through them to where it began.
                    Vec3 d = start.subtract(user.position());
                    Vec3 flat = new Vec3(d.x, 0, d.z);
                    if (flat.lengthSqr() > 0.25) {
                        Anim.play(user, "yuta_blade_run");
                        cut(dart(user, flat.normalize(), flat.length()), flat.normalize());
                    }
                }
                if (age >= (back ? 14 : 8)) finish();
            }
        }
    }

    // --- 2: Flurry ---

    static final class Flurry extends ToolMove {
        static final String ID = "sd_flurry";

        Flurry() {
            super(ID, CursedTools.SLAUGHTER_DEMON, "flurry");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().sdFlurryCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private int done;
            private boolean rising;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                setPhase(0, 22);
            }

            @Override
            public boolean hasVariant() {
                return true;
            }

            @Override
            protected boolean onVariant(@Nullable Entity hint) {
                rising = true;
                return true;
            }

            @Override
            public float movementMultiplier() {
                return 0.35f;
            }

            @Override
            public void tick() {
                int hits = cfg().sdFlurryHits + (int) Math.round(param(user, "hits") - 1);
                if (age % 4 == 1 && done < hits) {
                    done++;
                    boolean last = done == hits;
                    Anim.play(user, "yuta_light_" + (1 + (done - 1) % 4));
                    Vec3 dir = HakariCombat.flat(user);
                    Knockback kb = !last ? Knockback.HOLD : rising ? Knockback.set(new Vec3(0, 0.95, 0).add(dir.scale(0.1)))
                            : Knockback.directional(dir, 0.6, 0.15);
                    for (LivingEntity t : HakariCombat.front(user, 2.6 * param(user, "reach"), 2.4, 2.2)) {
                        var b = strike(user, cfg().sdFlurryDamage).knockback(kb).hitstun(last ? 20 : 10).origin(user.getEyePosition()).fx("tool_flurry", last ? 1.2f : 0.8f);
                        if (last && rising) b.status(CombatStatus.LAUNCHED, 24);
                        HitResult r = HakariCombat.hit(b.build(), t);
                        if (last && r.connected() && !rising) Statuses.apply(t, CombatStatus.HITSTUN, 18);
                    }
                    Fx.sound(level, user.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.3f + done * 0.1f);
                }
                if (age >= hits * 4 + 6) finish();
            }
        }
    }

    // --- 3: Severing Point ---

    static final class Sever extends ToolMove {
        static final String ID = "sd_sever";

        Sever() {
            super(ID, CursedTools.SLAUGHTER_DEMON, "severing_point");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().sdSeverCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final Vec3 dir;
            private boolean landed;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
                dir = HakariCombat.flat(ctx.user());
            }

            @Override
            public void start() {
                Anim.play(user, "yuta_resolute_windup");
                setPhase(0, 18);
                Fx.play(level, "curse_tell", chest(user).add(dir), dir, 0.6f, user.getId());
            }

            @Override
            public void tick() {
                if (age < 6) {
                    Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                    return;
                }
                if (age == 6) Anim.play(user, "yuta_resolute_slash");
                if (age <= 10 && !landed) {
                    HakariCombat.drive(user, dir, 0.9);
                    for (LivingEntity t : HakariCombat.front(user, 1.8, 1.6, 2.0)) {
                        landed = true;
                        boolean execute = t instanceof GradedCurse g && g.curseGrade().rank() <= CurseGrade.GRADE_3.rank()
                                && t.getHealth() <= t.getMaxHealth() * 0.25f * param(user, "threshold");
                        if (execute) {
                            HakariCombat.hit(strike(user, t.getHealth() + 1f).tag(AttackTag.UNBLOCKABLE).noComboScaling().origin(user.getEyePosition())
                                    .knockback(Knockback.directional(dir, 0.8, 0.3)).fx("tool_sever", 1.4f).build(), t);
                        } else {
                            HakariCombat.hit(strike(user, cfg().sdSeverDamage).tag(AttackTag.HEAVY).origin(user.getEyePosition())
                                    .knockback(Knockback.directional(dir, 1.0, 0.25)).hitstun(18).fx("tool_sever", 1f).build(), t);
                        }
                        Motion.set(user, Vec3.ZERO);
                        break;
                    }
                }
                if (age >= (landed ? 16 : 22)) finish();
            }
        }
    }

    // --- 4: Mirror Parry ---

    /** Who is in Mirror Parry's stance right now, and the attacker it turned aside (for the riposte). */
    static final Map<UUID, LivingEntity> PARRIED = new HashMap<>();

    static final class Parry extends ToolMove {
        static final String ID = "sd_parry";

        Parry() {
            super(ID, CursedTools.SLAUGHTER_DEMON, "parry");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().sdParryCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private int riposteAt = -1;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "yuta_outburst_grip");
                setPhase(0, cfg().sdParryWindow);
                Fx.play(level, "kit_parry_stance", chest(user), HakariCombat.flat(user), 1f, user.getId());
            }

            boolean open() {
                return riposteAt < 0 && age <= cfg().sdParryWindow * param(user, "window");
            }

            @Override
            public float movementMultiplier() {
                return 0.2f;
            }

            @Override
            public void tick() {
                LivingEntity attacker = PARRIED.remove(user.getUUID());
                if (attacker != null && riposteAt < 0) {
                    riposteAt = age;
                    Anim.play(user, "yuta_outburst_parry");
                    Fx.sound(level, user.position(), SoundEvents.SHIELD_BLOCK.value(), 1f, 1.6f);
                    if (attacker.isAlive() && attacker.distanceToSqr(user) < 8 * 8) {
                        // Behind them, and the cut.
                        Vec3 behind = attacker.position().subtract(HakariCombat.flat(attacker).scale(1.4));
                        user.teleportTo(behind.x, attacker.getY(), behind.z);
                        HakariCombat.faceTowards(user, attacker.getEyePosition());
                        HakariCombat.hit(strike(user, cfg().sdRiposteDamage).tag(AttackTag.UNBLOCKABLE).origin(user.getEyePosition())
                                .knockback(Knockback.directional(HakariCombat.flat(user), 0.7, 0.2)).hitstun(20).fx("tool_sever", 1f).build(), attacker);
                    }
                }
                if (riposteAt >= 0 ? age >= riposteAt + 10 : age > cfg().sdParryWindow * param(user, "window") + 8) finish();
            }

            @Override
            public void end() {
                PARRIED.remove(user.getUUID());
            }
        }

        static boolean parrying(LivingEntity e) {
            var c = dev.rick.jjk.core.ability.Casters.getOrNull(e);
            return c != null && c.cast() instanceof Run r && !r.isFinished() && r.open();
        }
    }

    /** Mirror Parry's stance: a melee blow or a bullet is turned aside (never a domain's sure hit or the ground). */
    public static final class ParryDefense implements DefenseLayer {
        @Override
        public String id() {
            return Parry.ID;
        }

        @Override
        public int priority() {
            return 63;
        }

        @Override
        public boolean isActive(LivingEntity defender) {
            return Parry.parrying(defender);
        }

        @Override
        public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
            if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.ENVIRONMENTAL) || attack.has(AttackTag.UNBLOCKABLE)) return DefenseResult.PASS;
            return DefenseResult.negate("sd_parry");
        }

        @Override
        public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
            if (attack.attacker instanceof LivingEntity le) PARRIED.put(defender.getUUID(), le);
        }
    }

    // --- R: Draw Cut ---

    static final class DrawCut extends ToolMove {
        static final String ID = "sd_drawcut";

        DrawCut() {
            super(ID, CursedTools.SLAUGHTER_DEMON, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().sdDrawCutCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "yuta_light_4");
                setPhase(0, 8);
            }

            @Override
            public void tick() {
                if (age == 3) {
                    Vec3 dir = HakariCombat.flat(user);
                    Fx.play(level, "yuta_swing", chest(user), dir, 1.2f, user.getId());
                    for (LivingEntity t : HitboxQuery.targets(user, HitShape.cone(user.getEyePosition(), dir, 3.4, 50), 0.2, true)) {
                        HakariCombat.hit(strike(user, cfg().sdDrawCutDamage).knockback(Knockback.directional(dir, 0.45, 0.1)).hitstun(12)
                                .origin(user.getEyePosition()).fx("tool_flurry", 0.8f).build(), t);
                    }
                    Fx.sound(level, user.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.7f);
                }
                if (age >= 8) finish();
            }
        }
    }

    // --- G: Thousand Cuts ---

    static final class ThousandCuts extends ToolMove {
        static final String ID = "sd_thousand_cuts";

        ThousandCuts() {
            super(ID, CursedTools.SLAUGHTER_DEMON, "thousand_cuts");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().sdUltimateCooldown;
        }

        @Override
        public @Nullable String checkActivation(AbilityContext ctx) {
            String base = super.checkActivation(ctx);
            if (base != null) return base;
            return targets(ctx.user()).isEmpty() ? "no_target" : null;
        }

        List<LivingEntity> targets(LivingEntity user) {
            List<LivingEntity> out = new ArrayList<>();
            for (LivingEntity e : HitboxQuery.targets(user, HitShape.sphere(user.position(), 10), 0, true)) {
                if (out.size() >= cfg().sdUltimateTargets) break;
                out.add(e);
            }
            out.sort((a, b) -> Double.compare(a.distanceToSqr(user), b.distanceToSqr(user)));
            return out;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final List<LivingEntity> victims;
            private int next;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
                victims = targets(ctx.user());
            }

            @Override
            public void start() {
                setPhase(0, victims.size() * 5 + 12);
                Fx.play(level, "kit_ultimate", chest(user), HakariCombat.flat(user), 1f, user.getId());
                Fx.sound(level, user.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.9f, 1.6f);
            }

            @Override
            public boolean uninterruptible() {
                return true;
            }

            @Override
            public void tick() {
                if (age >= 6 && (age - 6) % 5 == 0 && next < victims.size()) {
                    LivingEntity t = victims.get(next++);
                    if (t.isAlive()) {
                        Vec3 from = user.position();
                        Vec3 side = t.position().subtract(from);
                        Vec3 flat = new Vec3(side.x, 0, side.z);
                        Vec3 at = t.position().add(flat.lengthSqr() > 1e-4 ? flat.normalize().scale(1.3) : Vec3.ZERO);
                        user.teleportTo(at.x, t.getY(), at.z);
                        user.fallDistance = 0;
                        HakariCombat.faceTowards(user, t.getEyePosition());
                        Anim.play(user, "yuta_light_" + (1 + next % 4));
                        Fx.play(level, "tool_quickstep", from.add(0, 1, 0), at.subtract(from), 1.4f, user.getId());
                        HakariCombat.hit(strike(user, cfg().sdUltimateDamage).tag(AttackTag.ULTIMATE).noComboScaling().origin(user.getEyePosition())
                                .knockback(Knockback.HOLD).hitstun(30).fx("tool_sever", 1.2f).build(), t);
                    }
                }
                if (next >= victims.size() && age >= 6 + victims.size() * 5 + 6) finish();
            }
        }
    }
}
