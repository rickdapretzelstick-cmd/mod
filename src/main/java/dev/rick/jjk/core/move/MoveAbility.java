package dev.rick.jjk.core.move;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.core.net.CameraPayload;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runs a {@link Move} made in the move editor: its clip, then its events tick by tick. */
public final class MoveAbility extends Ability {
    public final Move move;

    public MoveAbility(Move move) {
        super("move." + move.id());
        this.move = move;
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(dev.rick.jjk.core.ability.AbilityCaster caster) {
        return move.cost();
    }

    @Override
    public int cooldown(dev.rick.jjk.core.ability.AbilityCaster caster) {
        return move.cooldown();
    }

    @Override
    public boolean isTechnique() {
        return move.technique();
    }

    @Nullable
    @Override
    public String checkActivation(AbilityContext ctx) {
        if (!move.airOk() && !ctx.user().onGround()) return "grounded_only";
        return null;
    }

    @Override
    public AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    /** A follow-up (a logic event's "move"): starts at once, with no cost or cooldown of its own. */
    static void chain(Instance from, Move next) {
        AbilityContext ctx = new AbilityContext(from.caster, from.user, from.level, from.slot, 0, 0, null);
        from.caster.begin(new Instance(new MoveAbility(next), ctx));
    }

    public static final class Instance extends AbilityInstance {
        final Move move;
        final AbilitySlot slot;
        private final Map<String, Map<UUID, Integer>> lastHit = new HashMap<>();
        private final Set<String> connected = new LinkedHashSet<>();
        private final Map<UUID, LivingEntity> grabbed = new HashMap<>();
        @Nullable private LivingEntity lastTarget;
        private boolean pressed;
        private boolean endedEarly;

        Instance(MoveAbility ability, AbilityContext ctx) {
            super(ability, ctx);
            this.move = ability.move;
            this.slot = ctx.slot();
        }

        @Override
        public void start() {
            // Its own clip, or one of the existing ones by name ("use").
            Anim.play(user, move.useClip().isEmpty() ? Move.clipName(move.id()) : move.useClip());
            setPhase(1, move.ticks());
            run(0);
        }

        @Override
        public void tick() {
            run(age);
            if (age >= move.ticks()) finish();
        }

        /** The same key pressed again while the move runs: a "press" logic event may take it. */
        public boolean press(AbilitySlot s) {
            if (s != slot) return false;
            for (Move.Logic l : move.logic()) {
                if ("press".equals(l.when()) && age >= l.start() && age <= l.end()) {
                    pressed = true;
                    act(l);
                    return true;
                }
            }
            return false;
        }

        private void run(int t) {
            for (Move.SelfStatus s : move.statuses()) {
                CombatStatus st = status(s.status());
                if (st != null && t >= s.start() && t <= s.end()) Statuses.apply(user, st, 2);
            }
            for (Move.Motion m : move.motions()) {
                boolean sustained = m.duration() > 1;
                if (t == m.t() || (sustained && t > m.t() && t < m.t() + m.duration())) {
                    Vec3 v = world(m.velocity(), false);
                    if ("set".equals(m.mode()) || (sustained && t > m.t())) dev.rick.jjk.util.Motion.set(user, v);
                    else dev.rick.jjk.util.Motion.add(user, v);
                }
            }
            for (Move.Hitbox h : move.hitboxes()) if (t >= h.start() && t <= h.end()) hitbox(h, t);
            for (Move.Grab g : move.grabs()) grab(g, t);
            for (Move.Effect e : move.effects()) {
                if (e.t() != t) continue;
                Vec3 at = e.onTarget() && lastTarget != null ? lastTarget.getBoundingBox().getCenter() : user.position().add(world(e.offset(), false));
                Fx.play(level, e.fx(), at, world(e.dir(), false), e.scale(), user.getId());
            }
            for (Move.PlayClip c : move.clips()) if (c.t() == t && !c.clip().isEmpty()) Anim.play(user, c.clip(), c.speed());
            for (Move.Sound s : move.sounds()) {
                if (s.t() != t) continue;
                SoundEvent ev = sound(s.sound());
                if (ev != null) Fx.sound(level, user.position(), ev, s.volume(), s.pitch());
            }
            for (Move.Camera c : move.cameras()) if (c.t() == t) camera(c);
            for (Move.Logic l : move.logic()) {
                if ("press".equals(l.when()) || l.start() != t || isFinished()) continue;
                if (holds(l)) act(l);
            }
        }

        private void hitbox(Move.Hitbox h, int t) {
            HitShape shape = shape(h);
            Map<UUID, Integer> seen = lastHit.computeIfAbsent(h.id(), k -> new HashMap<>());
            for (LivingEntity target : HitboxQuery.targets(user, shape, 0.1, true)) {
                if (target == user || grabbed.containsKey(target.getUUID())) continue;
                Integer last = seen.get(target.getUUID());
                if (last != null && (h.rehit() <= 0 || t - last < Move.ticks(h.rehit()))) continue;
                seen.put(target.getUUID(), t);
                Hit.Builder b = Hit.builder(user, ability.id).type(move.technique() ? ModDamageTypes.TECHNIQUE : ModDamageTypes.MELEE)
                        .damage(h.damage()).hitstun(h.hitstun()).fx(h.impactFx(), h.impactScale())
                        .knockback(h.grab() ? Knockback.HOLD : Knockback.set(world(h.knockback(), false)))
                        .tag(move.technique() ? AttackTag.TECHNIQUE : AttackTag.MELEE);
                for (String tag : h.tags()) b.tag(AttackTag.of(tag));
                if (!h.blockable()) b.tag(AttackTag.UNBLOCKABLE);
                CombatStatus st = status(h.status());
                if (st != null && h.statusTicks() > 0) b.status(st, h.statusTicks());
                HitResult r = HitResolver.resolve(b.build(), target);
                if (!r.connected()) continue;
                connected.add(h.id());
                lastTarget = target;
                if (h.grab() || grabFrom(h.id())) {
                    grabbed.put(target.getUUID(), target);
                    Statuses.apply(target, CombatStatus.GRABBED, 3);
                }
            }
        }

        private boolean grabFrom(String hitbox) {
            for (Move.Grab g : move.grabs()) if (g.from().equals(hitbox) && age <= g.end()) return true;
            return false;
        }

        private void grab(Move.Grab g, int t) {
            if (grabbed.isEmpty() || t < g.start() || t > g.end()) return;
            for (LivingEntity v : grabbed.values()) {
                if (!v.isAlive()) continue;
                if (t < g.end()) {
                    Statuses.apply(v, CombatStatus.GRABBED, 3);
                    Vec3 anchor = user.position().add(world(g.hold(), false));
                    dev.rick.jjk.util.Motion.set(v, anchor.subtract(v.position()).scale(0.6));
                } else {
                    Statuses.remove(v, CombatStatus.GRABBED);
                    if (g.releaseDamage() > 0) {
                        HitResolver.resolve(Hit.builder(user, ability.id).type(ModDamageTypes.MELEE).damage(g.releaseDamage()).hitstun(12)
                                .tag(AttackTag.MELEE, AttackTag.UNBLOCKABLE).knockback(Knockback.set(world(g.release(), false)))
                                .fx(g.releaseFx().isEmpty() ? "hit_heavy" : g.releaseFx(), 1f).build(), v);
                    }
                    dev.rick.jjk.util.Motion.set(v, world(g.release(), false));
                }
            }
            if (t >= g.end()) grabbed.clear();
        }

        private boolean holds(Move.Logic l) {
            boolean hit = l.from().isEmpty() ? !connected.isEmpty() : connected.contains(l.from());
            return switch (l.when()) {
                case "always" -> true;
                case "hit" -> hit;
                case "miss" -> !hit;
                case "grabbed" -> !grabbed.isEmpty();
                case "notGrabbed" -> grabbed.isEmpty();
                case "airborne" -> !user.onGround();
                case "grounded" -> user.onGround();
                default -> false;
            };
        }

        private void act(Move.Logic l) {
            switch (l.action()) {
                case "end" -> {
                    endedEarly = true;
                    finish();
                }
                case "move" -> {
                    Move next = MoveLibrary.get(l.move());
                    if (next != null) chain(this, next);
                }
                default -> {}
            }
        }

        private void camera(Move.Camera c) {
            Vec3 at = user.position();
            switch (c.kind()) {
                case "shake" -> Fx.shake(level, at, c.radius(), c.intensity(), c.duration());
                case "flash" -> Fx.flash(level, at, c.radius(), c.color(), c.duration());
                default -> {
                    int type = switch (c.kind()) {
                        case "fov" -> CameraPayload.FOV;
                        case "hitstop" -> CameraPayload.HITSTOP;
                        default -> CameraPayload.IMPACT;
                    };
                    for (ServerPlayer p : level.players()) {
                        if (p.distanceToSqr(at) <= c.radius() * c.radius()) Fx.camera(p, type, c.intensity(), c.duration(), c.color());
                    }
                }
            }
        }

        HitShape shape(Move.Hitbox h) {
            Vec3 center = user.position().add(world(h.offset(), false));
            if ("sphere".equals(h.shape())) return HitShape.sphere(center, h.radius());
            Vec3 f = h.aim() ? user.getLookAngle() : forward();
            return HitShape.orientedBox(center.subtract(f.scale(h.size().z / 2)), f, h.size().z, h.size().x, h.size().y);
        }

        private Vec3 forward() {
            float yaw = user.getYRot() * ((float) Math.PI / 180f);
            return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }

        /** (right, up, forward) from the user's facing to world space. */
        Vec3 world(Vec3 local, boolean aim) {
            Vec3 f = aim ? user.getLookAngle() : forward();
            Vec3 flat = forward();
            Vec3 right = new Vec3(-flat.z, 0, flat.x);
            return right.scale(local.x).add(0, local.y, 0).add(f.scale(local.z));
        }

        @Override
        public void end() {
            for (LivingEntity v : grabbed.values()) Statuses.remove(v, CombatStatus.GRABBED);
            grabbed.clear();
            if (endedEarly || move.holds()) Anim.stop(user);
        }

        @Override
        public boolean uninterruptible() {
            return move.uninterruptible();
        }

        @Override
        public float movementMultiplier() {
            return move.movement();
        }

        public boolean pressedAgain() {
            return pressed;
        }
    }

    @Nullable
    static CombatStatus status(String id) {
        if (id == null || id.isEmpty() || "none".equals(id)) return null;
        for (CombatStatus s : CombatStatus.all()) if (s.id.equals(id)) return s;
        return null;
    }

    @Nullable
    static SoundEvent sound(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.contains(":")) return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(name));
        try {
            return ModSounds.get(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
