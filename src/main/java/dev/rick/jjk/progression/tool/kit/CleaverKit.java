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
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.util.Motion;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Cursed Cleaver's moveset: slow, heavy, built to break a guard and hold ground. Its basic attacks hit harder and
 * slower than a fist's.
 * <ol>
 *   <li><b>Heavy Swing</b> (1, hold): wind up and bring it down; a full charge breaks a guard, and with Shockwave it
 *   hits everything round the impact. <i>R variant, Whirl:</i> the release becomes a spin hitting all around.</li>
 *   <li><b>Shoulder Charge</b> (2): barrel forward, carrying the first foe and driving them into whatever stops you.</li>
 *   <li><b>Ground Splitter</b> (3, learned): an overhead slam that sends a crack along the ground.</li>
 *   <li><b>Iron Wall</b> (4, learned): plant your feet; what hits you is blunted and can't stagger you, and what you
 *   took comes back in the swing that ends it.</li>
 *   <li><b>Haft Strike</b> (R): a quick jab with the haft that interrupts. During a move's opening, its R variant.</li>
 *   <li><b>Executioner's Arc</b> (G, the major node): leap and crash down in a wide arc.</li>
 * </ol>
 */
public final class CleaverKit extends CursedToolKit {
    @Override
    public GripProfile grip() {
        return GripProfile.HEAVY;
    }

    public CleaverKit() {
        super(CursedTools.CURSED_CLEAVER, new MeleeMoveset("", "", 1.35f, 1.4f, 0.85f));
        bind(AbilitySlot.SKILL_1, new Heavy());
        bind(AbilitySlot.SKILL_2, new Charge());
        bind(AbilitySlot.SKILL_3, new Splitter());
        bind(AbilitySlot.SKILL_4, new IronWall());
        bind(AbilitySlot.SKILL_5, new Haft());
        bind(AbilitySlot.ULTIMATE, new Executioner());
    }

    private static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    // --- 1: Heavy Swing ---

    static final class Heavy extends ToolMove {
        static final String ID = "cl_heavy";

        Heavy() {
            super(ID, CursedTools.CURSED_CLEAVER, null);
        }

        @Override
        public Kind kind() {
            return Kind.HOLD;
        }

        @Override
        protected int baseCooldown() {
            return cfg().clHeavyCooldown;
        }

        @Override
        public boolean cooldownOnEnd() {
            return true;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private int swingAt = -1;
            private float charge;
            private boolean whirl;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "cl_heavy_charge");
                setPhase(0, cfg().clHeavyMaxCharge);
            }

            @Override
            public boolean hasVariant() {
                return true;
            }

            @Override
            protected boolean onVariant(@Nullable Entity hint) {
                whirl = true;
                return true;
            }

            @Override
            public float movementMultiplier() {
                return swingAt < 0 ? 0.45f : 0.1f;
            }

            @Override
            public void tick() {
                int max = cfg().clHeavyMaxCharge;
                if (swingAt < 0) {
                    if (age % 5 == 0) Fx.play(level, "kit_charge", chest(user), HakariCombat.flat(user), Math.min(1f, age / (float) max), user.getId());
                    if (!held || age >= max) {
                        swingAt = age;
                        charge = Mth.clamp(age / (float) max, 0.2f, 1f);
                        Anim.play(user, whirl ? "cl_whirl" : "cl_heavy");
                        setPhase(1, 10);
                    }
                    return;
                }
                if (age == swingAt + 3) swing();
                if (age >= swingAt + 12) finish();
            }

            private void swing() {
                Vec3 dir = HakariCombat.flat(user);
                boolean full = charge >= 0.99f;
                float dmg = cfg().clHeavyDamage * (0.45f + 0.55f * charge);
                List<LivingEntity> hit = whirl ? HitboxQuery.targets(user, HitShape.sphere(chest(user), 3.0), 0.2, true)
                        : HakariCombat.front(user, 2.8 * param(user, "reach"), 2.4, 2.4);
                for (LivingEntity t : hit) {
                    Vec3 away = whirl ? t.position().subtract(user.position()).multiply(1, 0, 1).normalize() : dir;
                    var b = strike(user, dmg).tag(AttackTag.HEAVY).origin(user.getEyePosition()).knockback(Knockback.directional(away, 0.6 + 0.8 * charge, 0.2))
                            .hitstun(14 + Math.round(10 * charge)).fx("tool_heavy", 0.8f + charge * 0.6f);
                    if (full) b.tag(AttackTag.GUARD_BREAK);
                    HakariCombat.hit(b.build(), t);
                }
                if (full && !whirl && learned(user) && CleaverKit.has(user, "shockwave")) {
                    Vec3 at = user.position().add(dir.scale(2));
                    Fx.play(level, "tool_shockwave", at, Vec3.ZERO, 1.3f, user.getId());
                    for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(at, 4.0), 0, false)) {
                        if (hit.contains(t)) continue;
                        HakariCombat.hit(strike(user, dmg * 0.6f).tag(AttackTag.AREA).origin(at)
                                .knockback(Knockback.radial(at, 0.9, 0.5)).status(CombatStatus.LAUNCHED, 20).hitstun(20).build(), t);
                    }
                }
                Fx.sound(level, user.position(), SoundEvents.ANVIL_LAND, 0.6f + 0.4f * charge, 0.6f);
            }
        }
    }

    static boolean has(LivingEntity e, String node) {
        return dev.rick.jjk.progression.mastery.Mastery.unlocked(e, CursedTools.CURSED_CLEAVER.unlockKey(node));
    }

    // --- 2: Shoulder Charge ---

    static final class Charge extends ToolMove {
        static final String ID = "cl_charge";

        Charge() {
            super(ID, CursedTools.CURSED_CLEAVER, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().clChargeCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final Vec3 dir;
            @Nullable private LivingEntity carried;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
                dir = HakariCombat.flat(ctx.user());
            }

            @Override
            public void start() {
                Anim.play(user, "cl_charge");
                setPhase(0, 16);
                Fx.sound(level, user.position(), SoundEvents.RAVAGER_ROAR, 0.5f, 1.4f);
            }

            @Override
            public void tick() {
                if (age <= 14) {
                    HakariCombat.drive(user, dir, 0.75 * param(user, "speed"));
                    if (carried == null) {
                        LivingEntity t = HakariCombat.firstInFront(user, 1.6, 1.6, 2.0);
                        if (t != null) {
                            HitResult r = HakariCombat.hit(strike(user, cfg().clChargeDamage * 0.4f).tag(AttackTag.HEAVY).origin(user.getEyePosition())
                                    .knockback(Knockback.HOLD).hitstun(20).build(), t);
                            if (r.connected()) carried = t;
                        }
                    } else if (carried.isAlive()) {
                        HakariCombat.carry(user, carried, 1.4);
                        Statuses.apply(carried, CombatStatus.GRABBED, 3);
                    }
                    // Stopped by a wall: whoever is carried is driven into it.
                    if (user.horizontalCollision && age > 2) {
                        slam(true);
                        return;
                    }
                }
                if (age == 15) slam(false);
                if (age >= 20) finish();
            }

            private void slam(boolean wall) {
                if (carried != null && carried.isAlive()) {
                    Statuses.remove(carried, CombatStatus.GRABBED);
                    HakariCombat.hit(strike(user, cfg().clChargeDamage * (wall ? 1.4f : 1f)).tag(AttackTag.HEAVY).noComboScaling().origin(user.getEyePosition())
                            .knockback(Knockback.directional(dir, wall ? 0.2 : 1.1, 0.3)).hitstun(wall ? 30 : 18).fx("tool_heavy", wall ? 1.3f : 1f).build(), carried);
                    if (wall) Fx.play(level, "tool_shockwave", carried.position(), Vec3.ZERO, 0.8f, user.getId());
                }
                carried = null;
                Motion.set(user, Vec3.ZERO);
                if (age < 15) finish();
            }

            @Override
            public void end() {
                if (carried != null) Statuses.remove(carried, CombatStatus.GRABBED);
            }
        }
    }

    // --- 3: Ground Splitter ---

    static final class Splitter extends ToolMove {
        static final String ID = "cl_splitter";

        Splitter() {
            super(ID, CursedTools.CURSED_CLEAVER, "splitter");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().clSplitCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final Vec3 dir;
            private final Set<UUID> struck = new HashSet<>();

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
                dir = HakariCombat.flat(ctx.user());
            }

            @Override
            public void start() {
                Anim.play(user, "cl_splitter");
                setPhase(0, 24);
            }

            @Override
            public float movementMultiplier() {
                return 0f;
            }

            @Override
            public void tick() {
                // The crack runs out a block and a half a tick from the impact.
                if (age >= 8 && age <= 14) {
                    double length = 8 * param(user, "length");
                    double reach = Math.min(length, (age - 7) * length / 7);
                    Vec3 at = user.position().add(dir.scale(reach));
                    if (age == 8) Fx.sound(level, user.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 0.8f);
                    Fx.play(level, "tool_shockwave", at, Vec3.ZERO, 0.6f, user.getId());
                    for (LivingEntity t : HitboxQuery.targets(user, HitShape.orientedBox(user.position().add(0, 0.5, 0), dir, reach, 2.2, 2.0), 0.2, false)) {
                        if (!struck.add(t.getUUID())) continue;
                        HakariCombat.hit(strike(user, cfg().clSplitDamage).tag(AttackTag.AREA, AttackTag.OTG).origin(user.getEyePosition())
                                .knockback(Knockback.set(new Vec3(0, 0.75, 0).add(dir.scale(0.2)))).status(CombatStatus.LAUNCHED, 22).hitstun(22)
                                .fx("tool_heavy", 1f).build(), t);
                    }
                }
                if (age >= 22) finish();
            }
        }
    }

    // --- 4: Iron Wall ---

    static final class IronWall extends ToolMove {
        static final String ID = "cl_iron_wall";

        IronWall() {
            super(ID, CursedTools.CURSED_CLEAVER, "iron_wall");
        }

        @Override
        public Kind kind() {
            return Kind.HOLD;
        }

        @Override
        protected int baseCooldown() {
            return cfg().clWallCooldown;
        }

        @Override
        public boolean cooldownOnEnd() {
            return true;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            int absorbed;
            private int swingAt = -1;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "cl_wall");
                setPhase(0, cfg().clWallTicks);
                Fx.play(level, "kit_wall", chest(user), HakariCombat.flat(user), 1f, user.getId());
            }

            boolean standing() {
                return swingAt < 0;
            }

            @Override
            public boolean uninterruptible() {
                return swingAt < 0;
            }

            @Override
            public float movementMultiplier() {
                return 0.15f;
            }

            @Override
            public void tick() {
                if (swingAt < 0) {
                    if (!held || age >= cfg().clWallTicks * param(user, "duration")) {
                        swingAt = age;
                        Anim.play(user, "cl_counter");
                        setPhase(1, 10);
                    }
                    return;
                }
                if (age == swingAt + 3) {
                    Vec3 dir = HakariCombat.flat(user);
                    float dmg = cfg().clWallCounterDamage * (0.4f + 0.3f * Math.min(4, absorbed));
                    for (LivingEntity t : HakariCombat.front(user, 3.0, 3.0, 2.4)) {
                        HakariCombat.hit(strike(user, dmg).tag(AttackTag.HEAVY).origin(user.getEyePosition())
                                .knockback(Knockback.directional(dir, 0.9, 0.25)).hitstun(18).fx("tool_heavy", 1.1f).build(), t);
                    }
                    Fx.sound(level, user.position(), SoundEvents.ANVIL_LAND, 0.8f, 0.7f);
                }
                if (age >= swingAt + 12) finish();
            }
        }

        static @Nullable Run standing(LivingEntity e) {
            var c = dev.rick.jjk.core.ability.Casters.getOrNull(e);
            return c != null && c.cast() instanceof Run r && !r.isFinished() && r.standing() ? r : null;
        }
    }

    /** Iron Wall: blows are blunted (not stopped), and each one blunted feeds the counter-swing. */
    public static final class WallDefense implements DefenseLayer {
        @Override
        public String id() {
            return IronWall.ID;
        }

        @Override
        public int priority() {
            return 20;
        }

        @Override
        public boolean isActive(LivingEntity defender) {
            return IronWall.standing(defender) != null;
        }

        @Override
        public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
            if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.ENVIRONMENTAL)) return DefenseResult.PASS;
            float keep = 1f - dev.rick.jjk.config.JJKConfig.get().cursedTools.clWallReduction;
            return DefenseResult.reduce(keep, "cl_iron_wall");
        }

        @Override
        public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
            IronWall.Run r = IronWall.standing(defender);
            if (r != null) r.absorbed++;
        }
    }

    // --- R: Haft Strike ---

    static final class Haft extends ToolMove {
        static final String ID = "cl_haft";

        Haft() {
            super(ID, CursedTools.CURSED_CLEAVER, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().clHaftCooldown;
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
                Anim.play(user, "cl_haft");
                setPhase(0, 8);
            }

            @Override
            public void tick() {
                if (age == 2) {
                    LivingEntity t = HakariCombat.firstInFront(user, 2.4, 1.4, 2.0);
                    if (t != null) {
                        var c = dev.rick.jjk.core.ability.Casters.getOrNull(t);
                        if (c != null) c.interrupt("stunned");
                        HakariCombat.hit(strike(user, cfg().clHaftDamage).origin(user.getEyePosition())
                                .knockback(Knockback.directional(HakariCombat.flat(user), 0.3, 0.05)).hitstun(16).fx("hit_light", 0.8f).build(), t);
                    }
                }
                if (age >= 8) finish();
            }
        }
    }

    // --- G: Executioner's Arc ---

    static final class Executioner extends ToolMove {
        static final String ID = "cl_executioner";

        Executioner() {
            super(ID, CursedTools.CURSED_CLEAVER, "executioner");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().clUltimateCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final Vec3 dir;
            private boolean crashed;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
                dir = HakariCombat.flat(ctx.user());
            }

            @Override
            public void start() {
                Anim.play(user, "cl_exec_leap");
                setPhase(0, 30);
                Fx.play(level, "kit_ultimate", chest(user), dir, 1f, user.getId());
                Motion.set(user, dir.scale(0.6).add(0, 0.95, 0));
            }

            @Override
            public boolean uninterruptible() {
                return true;
            }

            @Override
            public void tick() {
                if (!crashed && age > 6) {
                    Motion.set(user, new Vec3(user.getDeltaMovement().x, Math.min(user.getDeltaMovement().y, -1.2), user.getDeltaMovement().z));
                    if (user.onGround() || age > 26) {
                        crashed = true;
                        user.fallDistance = 0;
                        Anim.play(user, "cl_exec_crash");
                        Vec3 at = user.position();
                        Fx.play(level, "tool_shockwave", at, Vec3.ZERO, 2.2f, user.getId());
                        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
                        for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(at.add(0, 0.5, 0), 5.0 * param(user, "radius")), 0, false)) {
                            HakariCombat.hit(strike(user, cfg().clUltimateDamage).tag(AttackTag.ULTIMATE, AttackTag.AREA, AttackTag.GUARD_BREAK).noComboScaling()
                                    .origin(at).knockback(Knockback.radial(at, 1.1, 0.6)).status(CombatStatus.LAUNCHED, 26).hitstun(26)
                                    .fx("tool_heavy", 1.5f).build(), t);
                        }
                        setPhase(1, 10);
                    }
                }
                user.fallDistance = 0;
                if (crashed && age > 40 || crashed && phase() == 1 && age > 12) {
                    if (age > 14) finish();
                }
                if (age > 60) finish();
            }
        }
    }
}
