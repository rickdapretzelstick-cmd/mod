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
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Cursed Blade's moveset: a great cleaver of a blade held in both hands, black thorns bursting from its guard. Heavy,
 * committed slashes that throw crimson cursed energy, and the thorns themselves as a weapon. Its basic attacks are its
 * own two-handed cuts ({@code cb_light_1..4} and the rest), hitting harder and further than a fist.
 * <ol>
 *   <li><b>Heavy Slash</b> (1): a two-handed diagonal cleave that breaks a guard. <i>R variant, Crimson Cross:</i> the
 *   blade comes straight back across the first cut.</li>
 *   <li><b>Cursed Wave</b> (2, hold, learned): charge, then a crescent of cursed energy flies out, cutting everything it
 *   passes and the plants and loose blocks in its way (restored like all battle damage). <i>R variant, Twin Wave:</i>
 *   two crescents, splayed.</li>
 *   <li><b>Thorn Lunge</b> (3, learned): a driving thrust through everything in a line; black thorns then erupt under
 *   whoever it went through.</li>
 *   <li><b>Thorn Guard</b> (4, learned): the blade raised, thorns bristling: a blow caught in the window is stopped, and
 *   thorns burst up under the attacker (and, with Thorn Bloom, everyone near them).</li>
 *   <li><b>Rising Cut</b> (R): an upward cut that throws them into the air. During a move's opening, its R variant.</li>
 *   <li><b>Black Thorn</b> (G, the major node): the blade raised high and driven into the ground; rings of black thorns
 *   tear outward, launching everything they reach.</li>
 * </ol>
 */
public final class BladeKit extends CursedToolKit {
    /** Every move's source, for terrain the blade breaks (restored by the battle-damage system). */
    static final String SOURCE = "jjk:cursed_blade";

    @Override
    public GripProfile grip() {
        return GripProfile.TWO_HAND;
    }

    public BladeKit() {
        super(CursedTools.CURSED_BLADE, new MeleeMoveset("cb_", "cb_", 1.3f, 1.25f, 0.95f, 1.3f, true, false, null));
        bind(AbilitySlot.SKILL_1, new HeavySlash());
        bind(AbilitySlot.SKILL_2, new CursedWave());
        bind(AbilitySlot.SKILL_3, new ThornLunge());
        bind(AbilitySlot.SKILL_4, new ThornGuard());
        bind(AbilitySlot.SKILL_5, new RisingCut());
        bind(AbilitySlot.ULTIMATE, new BlackThorn());
    }

    static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.6, 0);
    }

    static boolean hasNode(LivingEntity e, String node) {
        return dev.rick.jjk.progression.mastery.Mastery.unlocked(e, CursedTools.CURSED_BLADE.unlockKey(node));
    }

    /** Black thorns burst up under someone: a hit that throws them up off the ground. */
    static HitResult thorns(ToolMove move, LivingEntity user, LivingEntity t, float damage, float scale) {
        ServerLevel level = (ServerLevel) user.level();
        Fx.play(level, "cb_thorns", t.position(), Vec3.ZERO, scale, t.getId());
        Fx.sound(level, t.position(), SoundEvents.POINTED_DRIPSTONE_LAND, 1.2f, 0.6f);
        return HakariCombat.hit(move.strike(user, damage).tag(AttackTag.AREA).origin(t.position())
                .knockback(Knockback.set(new Vec3(0, 0.85, 0))).status(CombatStatus.LAUNCHED, 24).hitstun(24).fx("cb_hit_launch", scale).build(), t);
    }

    // --- 1: Heavy Slash (R: Crimson Cross) ---

    static final class HeavySlash extends ToolMove {
        static final String ID = "cb_heavy_slash";

        HeavySlash() {
            super(ID, CursedTools.CURSED_BLADE, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().cbHeavyCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private boolean cross;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "cb_heavy_slash");
                setPhase(0, 7);
                Fx.play(level, "cb_charge", chest(user), HakariCombat.flat(user), 0.6f, user.getId());
            }

            @Override
            public boolean hasVariant() {
                return true;
            }

            @Override
            protected boolean onVariant(@Nullable Entity hint) {
                cross = true;
                return true;
            }

            @Override
            public float movementMultiplier() {
                return age < 7 ? 0.35f : 0.15f;
            }

            @Override
            public void tick() {
                if (age == 7) slash(false);
                if (cross && age == 12) Anim.play(user, "cb_cross");
                if (cross && age == 16) slash(true);
                if (age >= (cross ? 26 : 16)) finish();
            }

            private void slash(boolean second) {
                Vec3 dir = HakariCombat.flat(user);
                float dmg = second ? cfg().cbCrossDamage : cfg().cbHeavyDamage;
                Fx.play(level, second ? "cb_cross" : "cb_arc", chest(user).add(dir.scale(1.6)), dir, second ? 1.5f : 1.8f, user.getId());
                Fx.sound(level, user.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, second ? 0.9f : 0.6f);
                for (LivingEntity t : HakariCombat.front(user, 3.4 * param(user, "reach"), 3.4, 2.6)) {
                    var b = strike(user, dmg).tag(AttackTag.HEAVY).origin(user.getEyePosition())
                            .knockback(Knockback.directional(dir, second ? 1.1 : 0.5, 0.2)).hitstun(second ? 20 : 16).fx("cb_hit_heavy", second ? 1.3f : 1.1f);
                    if (!second) b.tag(AttackTag.GUARD_BREAK);
                    HakariCombat.hit(b.build(), t);
                }
            }
        }
    }

    // --- 2: Cursed Wave (hold; R: Twin Wave) ---

    static final class CursedWave extends ToolMove {
        static final String ID = "cb_cursed_wave";

        CursedWave() {
            super(ID, CursedTools.CURSED_BLADE, "cursed_wave");
        }

        @Override
        public Kind kind() {
            return Kind.HOLD;
        }

        @Override
        protected int baseCooldown() {
            return cfg().cbWaveCooldown;
        }

        @Override
        public boolean cooldownOnEnd() {
            return true;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        /** One crescent in flight. */
        private static final class Wave {
            Vec3 pos;
            final Vec3 dir;
            double travelled;
            boolean done;
            final Set<UUID> struck = new HashSet<>();

            Wave(Vec3 pos, Vec3 dir) {
                this.pos = pos;
                this.dir = dir;
            }
        }

        final class Run extends ToolMove.Instance {
            private int releasedAt = -1;
            private float charge;
            private boolean twin;
            private final List<Wave> waves = new ArrayList<>();

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "cb_wave_charge");
                setPhase(0, cfg().cbWaveMaxCharge);
            }

            @Override
            public boolean hasVariant() {
                return true;
            }

            @Override
            protected boolean onVariant(@Nullable Entity hint) {
                twin = true;
                return true;
            }

            @Override
            public float movementMultiplier() {
                return releasedAt < 0 ? 0.4f : 1f;
            }

            @Override
            public void tick() {
                int max = cfg().cbWaveMaxCharge;
                if (releasedAt < 0) {
                    if (age % 4 == 0) Fx.play(level, "cb_charge", chest(user), HakariCombat.flat(user), Math.min(1f, age / (float) max), user.getId());
                    if (!held || age >= max) release(Mth.clamp(age / (float) max, 0.25f, 1f));
                    return;
                }
                double speed = 1.25;
                double range = cfg().cbWaveRange * (0.5 + 0.5 * charge) * param(user, "range");
                boolean any = false;
                for (Wave w : waves) {
                    if (w.done) continue;
                    any = true;
                    w.pos = w.pos.add(w.dir.scale(speed));
                    w.travelled += speed;
                    Fx.play(level, "cb_wave", w.pos, w.dir, 1.2f + charge * 0.8f, user.getId());
                    for (LivingEntity t : HitboxQuery.targets(user, HitShape.orientedBox(w.pos.subtract(w.dir.scale(0.8)), w.dir, 1.6, 3.6, 2.4), 0.2, false)) {
                        if (!w.struck.add(t.getUUID())) continue;
                        HakariCombat.hit(strike(user, cfg().cbWaveDamage * (0.5f + 0.5f * charge)).tag(AttackTag.AREA).origin(w.pos)
                                .knockback(Knockback.directional(w.dir, 0.7 + 0.5 * charge, 0.25)).hitstun(18).fx("cb_hit_heavy", 1f).build(), t);
                    }
                    if (cut(w, speed) || w.travelled >= range) {
                        w.done = true;
                        Fx.play(level, "cb_wave_burst", w.pos, w.dir, 1f + charge, user.getId());
                    }
                }
                if (!any || age > releasedAt + 60) finish();
            }

            private void release(float c) {
                charge = c;
                releasedAt = age;
                Anim.play(user, "cb_wave_release");
                setPhase(1, 40);
                Vec3 dir = HakariCombat.flat(user);
                Vec3 from = chest(user).add(dir.scale(1.0)).add(0, -0.4, 0);
                if (twin) {
                    for (float a : new float[] {-14f, 14f}) {
                        float r = a * Mth.DEG_TO_RAD;
                        waves.add(new Wave(from, new Vec3(dir.x * Math.cos(r) - dir.z * Math.sin(r), 0, dir.x * Math.sin(r) + dir.z * Math.cos(r)).normalize()));
                    }
                } else {
                    waves.add(new Wave(from, dir));
                }
                Fx.play(level, "cb_wave_release", from, dir, 1f + charge, user.getId());
                Fx.sound(level, user.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                Fx.sound(level, user.position(), SoundEvents.WITHER_SHOOT, 0.6f, 1.4f);
            }

            /**
             * The crescent cuts through what is in its way at its height: plants, leaves, glass, loose blocks (restored
             * later). Something solid stops it. True if it stopped.
             */
            private boolean cut(Wave w, double step) {
                // It moves more than a block's half-width a tick: sweep the whole step so no column is skipped.
                for (double back = step / 2; back > 0; back -= 0.5) {
                    if (cutAt(w.pos.subtract(w.dir.scale(back)), w.dir)) {
                        w.pos = w.pos.subtract(w.dir.scale(back));
                        return true;
                    }
                }
                return cutAt(w.pos, w.dir);
            }

            private boolean cutAt(Vec3 pos, Vec3 dir) {
                BlockPos c = BlockPos.containing(pos);
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                boolean wall = false;
                for (int s = -1; s <= 1; s++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos b = BlockPos.containing(pos.add(side.scale(s * 1.2))).above(dy);
                        BlockState st = level.getBlockState(b);
                        if (st.isAir()) continue;
                        if (!Destruction.destroy(level, b, 0.6f, user, SOURCE) && s == 0 && !st.getCollisionShape(level, b).isEmpty()) wall = true;
                    }
                }
                return wall && !level.getBlockState(c).getCollisionShape(level, c).isEmpty();
            }
        }
    }

    // --- 3: Thorn Lunge ---

    static final class ThornLunge extends ToolMove {
        static final String ID = "cb_thorn_lunge";

        ThornLunge() {
            super(ID, CursedTools.CURSED_BLADE, "thorn_lunge");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().cbLungeCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final Vec3 dir;
            private final List<LivingEntity> pierced = new ArrayList<>();

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
                dir = HakariCombat.flat(ctx.user());
            }

            @Override
            public void start() {
                Anim.play(user, "cb_lunge");
                setPhase(0, 20);
                Fx.play(level, "cb_charge", chest(user), dir, 0.5f, user.getId());
            }

            @Override
            public void tick() {
                if (age >= 4 && age <= 9) {
                    double step = cfg().cbLungeDistance * param(user, "distance") / 6.0;
                    Vec3 before = user.position();
                    HakariCombat.drive(user, dir, step);
                    Fx.play(level, "cb_lunge_trail", chest(user), dir, 1f, user.getId());
                    Vec3 tip = chest(user).add(dir.scale(1.8));
                    for (LivingEntity t : HitboxQuery.targets(user, HitShape.capsule(before.add(0, user.getBbHeight() * 0.6, 0), tip, 1.1), 0.2, false)) {
                        if (pierced.contains(t)) continue;
                        pierced.add(t);
                        HakariCombat.hit(strike(user, cfg().cbLungeDamage).tag(AttackTag.HEAVY).origin(user.getEyePosition())
                                .knockback(Knockback.directional(dir, 0.15, 0.05)).hitstun(22).fx("cb_hit_heavy", 1f).build(), t);
                    }
                    if (age == 4) Fx.sound(level, user.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 1f, 1.2f);
                }
                if (age == 10) Motion.set(user, Vec3.ZERO);
                // Then the thorns, under everyone it went through.
                if (age == 13) {
                    for (LivingEntity t : pierced) if (t.isAlive()) thorns(ThornLunge.this, user, t, cfg().cbLungeThornDamage, 1f);
                }
                if (age >= 20) finish();
            }
        }
    }

    // --- 4: Thorn Guard ---

    /** Who is standing in Thorn Guard right now, and the attacker it caught (for the counter). */
    static final Map<UUID, LivingEntity> CAUGHT = new HashMap<>();

    static final class ThornGuard extends ToolMove {
        static final String ID = "cb_thorn_guard";

        ThornGuard() {
            super(ID, CursedTools.CURSED_BLADE, "thorn_guard");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().cbGuardCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private int counterAt = -1;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "cb_guard");
                setPhase(0, window());
                Fx.play(level, "cb_guard", chest(user), HakariCombat.flat(user), 1f, user.getId());
                Fx.sound(level, user.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 0.7f);
            }

            int window() {
                return (int) Math.round(cfg().cbGuardWindow * param(user, "window"));
            }

            boolean open() {
                return counterAt < 0 && age <= window();
            }

            @Override
            public float movementMultiplier() {
                return 0.1f;
            }

            @Override
            public void tick() {
                LivingEntity attacker = CAUGHT.remove(user.getUUID());
                if (attacker != null && counterAt < 0) {
                    counterAt = age;
                    Anim.play(user, "cb_guard_counter");
                    Fx.sound(level, user.position(), SoundEvents.SHIELD_BLOCK.value(), 1f, 0.6f);
                    if (attacker.isAlive() && attacker.distanceToSqr(user) < 8 * 8) {
                        thorns(ThornGuard.this, user, attacker, cfg().cbGuardCounterDamage, 1.3f);
                        if (hasNode(user, "thorn_bloom")) {
                            for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(attacker.position(), 3.5), 0, false)) {
                                if (t != attacker) thorns(ThornGuard.this, user, t, cfg().cbGuardCounterDamage * 0.6f, 0.9f);
                            }
                        }
                    }
                }
                if (counterAt >= 0 && age >= counterAt + 12) finish();
                if (counterAt < 0 && age > window()) finish();
            }

            @Override
            public void end() {
                CAUGHT.remove(user.getUUID());
            }
        }

        static boolean guarding(LivingEntity e) {
            var c = dev.rick.jjk.core.ability.Casters.getOrNull(e);
            return c != null && c.cast() instanceof Run r && !r.isFinished() && r.open();
        }
    }

    /** Thorn Guard's window: a blow (melee or a projectile) is stopped and its attacker answered by the thorns. */
    public static final class ThornGuardDefense implements DefenseLayer {
        @Override
        public String id() {
            return ThornGuard.ID;
        }

        @Override
        public int priority() {
            return 62;
        }

        @Override
        public boolean isActive(LivingEntity defender) {
            return ThornGuard.guarding(defender);
        }

        @Override
        public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
            if (attack.has(AttackTag.SURE_HIT) || attack.has(AttackTag.ENVIRONMENTAL) || attack.has(AttackTag.UNBLOCKABLE)) return DefenseResult.PASS;
            return DefenseResult.negate("cb_thorn_guard");
        }

        @Override
        public void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {
            if (attack.attacker instanceof LivingEntity le) CAUGHT.put(defender.getUUID(), le);
        }
    }

    // --- R: Rising Cut ---

    static final class RisingCut extends ToolMove {
        static final String ID = "cb_rising_cut";

        RisingCut() {
            super(ID, CursedTools.CURSED_BLADE, null);
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().cbRisingCooldown;
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
                Anim.play(user, "cb_rising");
                setPhase(0, 14);
            }

            @Override
            public void tick() {
                if (age == 4) {
                    Vec3 dir = HakariCombat.flat(user);
                    Fx.play(level, "cb_rising", chest(user).add(dir.scale(1.3)), dir, 1.3f, user.getId());
                    Fx.sound(level, user.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                    for (LivingEntity t : HakariCombat.front(user, 3.0, 2.2, 2.6)) {
                        HakariCombat.hit(strike(user, cfg().cbRisingDamage).origin(user.getEyePosition())
                                .knockback(Knockback.set(new Vec3(0, 1.0, 0).add(dir.scale(0.15)))).status(CombatStatus.LAUNCHED, 28).hitstun(28)
                                .fx("cb_hit_launch", 1.2f).build(), t);
                    }
                    if (user.onGround()) Motion.set(user, new Vec3(user.getDeltaMovement().x, 0.42, user.getDeltaMovement().z));
                }
                if (age >= 14) finish();
            }
        }
    }

    // --- G: Black Thorn ---

    static final class BlackThorn extends ToolMove {
        static final String ID = "cb_black_thorn";
        static final int PLUNGE = 18, SPREAD = 16;

        BlackThorn() {
            super(ID, CursedTools.CURSED_BLADE, "black_thorn");
        }

        @Override
        public Kind kind() {
            return Kind.INSTANT;
        }

        @Override
        protected int baseCooldown() {
            return cfg().cbUltimateCooldown;
        }

        @Override
        public @Nullable AbilityInstance activate(AbilityContext ctx) {
            return new Run(this, ctx);
        }

        final class Run extends ToolMove.Instance {
            private final Set<UUID> struck = new HashSet<>();
            private Vec3 at = Vec3.ZERO;
            private double reached;

            Run(Ability a, AbilityContext ctx) {
                super(a, ctx);
            }

            @Override
            public void start() {
                Anim.play(user, "cb_ult_raise");
                setPhase(0, PLUNGE);
                Fx.play(level, "cb_ult_charge", chest(user), HakariCombat.flat(user), 1f, user.getId());
                Fx.sound(level, user.position(), SoundEvents.WARDEN_ROAR, 0.6f, 1.4f);
            }

            @Override
            public boolean uninterruptible() {
                return true;
            }

            @Override
            public float movementMultiplier() {
                return 0f;
            }

            @Override
            public void tick() {
                if (age < PLUNGE && age % 4 == 0) Fx.play(level, "cb_ult_charge", chest(user), HakariCombat.flat(user), 1f + age / (float) PLUNGE, user.getId());
                if (age == PLUNGE) {
                    Anim.play(user, "cb_ult_plunge");
                    at = user.position().add(HakariCombat.flat(user).scale(1.2));
                    setPhase(1, SPREAD + 12);
                    Fx.play(level, "cb_ult_impact", at, Vec3.ZERO, 1.5f, user.getId());
                    Fx.shake(level, at, 24, 0.9f, 16);
                    Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.55f);
                    // The ground splits where the blade goes in (restored like all battle damage).
                    Destruction.sphere(level, at.add(0, -0.5, 0), 2.2, 1.5f, 30, user, null, SOURCE);
                }
                if (age > PLUNGE && age <= PLUNGE + SPREAD) {
                    double radius = cfg().cbUltimateRadius * param(user, "radius");
                    double r = radius * (age - PLUNGE) / (double) SPREAD;
                    Fx.play(level, "cb_thorn_ring", at, Vec3.ZERO, (float) r, user.getId());
                    for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(at.add(0, 0.5, 0), r + 0.8), 0, false)) {
                        if (t.position().distanceTo(at) < reached - 1.5 || !struck.add(t.getUUID())) continue;
                        HakariCombat.hit(strike(user, cfg().cbUltimateDamage).tag(AttackTag.ULTIMATE, AttackTag.AREA, AttackTag.GUARD_BREAK).noComboScaling()
                                .origin(at).knockback(Knockback.radial(at, 0.8, 0.95)).status(CombatStatus.LAUNCHED, 30).hitstun(30)
                                .fx("cb_hit_launch", 1.5f).build(), t);
                        Fx.play(level, "cb_thorns", t.position(), Vec3.ZERO, 1.4f, t.getId());
                    }
                    // Plants and loose blocks on the ring are torn up by the thorns.
                    for (int i = 0; i < 6; i++) {
                        double a = level.getRandom().nextDouble() * Mth.TWO_PI;
                        Destruction.destroy(level, BlockPos.containing(at.add(Math.cos(a) * r, 0.2, Math.sin(a) * r)), 0.6f, user, SOURCE);
                    }
                    reached = r;
                    if ((age - PLUNGE) % 4 == 1) Fx.sound(level, at, SoundEvents.POINTED_DRIPSTONE_LAND, 1.4f, 0.5f);
                }
                if (age >= PLUNGE + SPREAD + 12) finish();
            }
        }
    }
}
