package dev.rick.jjk.core.combat;

import dev.rick.jjk.registry.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** A fully described attack: damage, knockback, hitstun, tags and follow-up statuses. Immutable once built. */
public final class Hit {
    public final LivingEntity attacker;
    @Nullable public final Entity direct;
    public final ResourceKey<DamageType> damageType;
    public final float damage;
    public final Set<AttackTag> tags;
    public final Knockback knockback;
    public final int hitstun;
    public final List<StatusApplication> statuses;
    public final Vec3 origin;
    public final String source;
    public final int guardDamage;
    public final String impactFx;
    public final float impactScale;
    public final boolean comboScaling;
    @Nullable public final Consumer<HitResult> onResolved;

    public record StatusApplication(CombatStatus status, int ticks) {}

    private Hit(Builder b) {
        this.attacker = b.attacker;
        this.direct = b.direct;
        this.damageType = b.damageType;
        this.damage = b.damage;
        this.tags = Collections.unmodifiableSet(b.tags);
        this.knockback = b.knockback;
        this.hitstun = b.hitstun;
        this.statuses = List.copyOf(b.statuses);
        this.origin = b.origin != null ? b.origin : b.attacker.position();
        this.source = b.source;
        this.guardDamage = b.guardDamage;
        this.impactFx = b.impactFx;
        this.impactScale = b.impactScale;
        this.comboScaling = b.comboScaling;
        this.onResolved = b.onResolved;
    }

    public boolean has(AttackTag tag) {
        return tags.contains(tag);
    }

    public static Builder builder(LivingEntity attacker, String source) {
        return new Builder(attacker, source);
    }

    public Builder toBuilder() {
        Builder b = new Builder(attacker, source);
        b.direct = direct;
        b.damageType = damageType;
        b.damage = damage;
        b.tags.addAll(tags);
        b.knockback = knockback;
        b.hitstun = hitstun;
        b.statuses.addAll(statuses);
        b.origin = origin;
        b.guardDamage = guardDamage;
        b.impactFx = impactFx;
        b.impactScale = impactScale;
        b.comboScaling = comboScaling;
        b.onResolved = onResolved;
        return b;
    }

    public static final class Builder {
        private final LivingEntity attacker;
        private final String source;
        private Entity direct;
        private ResourceKey<DamageType> damageType = ModDamageTypes.MELEE;
        private float damage;
        private final Set<AttackTag> tags = new HashSet<>();
        private Knockback knockback = Knockback.NONE;
        private int hitstun;
        private final List<StatusApplication> statuses = new ArrayList<>();
        private Vec3 origin;
        private int guardDamage = 1;
        private String impactFx = "hit_light";
        private float impactScale = 1f;
        private boolean comboScaling = true;
        private Consumer<HitResult> onResolved;

        private Builder(LivingEntity attacker, String source) {
            this.attacker = attacker;
            this.source = source;
        }

        public Builder direct(Entity e) { this.direct = e; return this; }
        public Builder type(ResourceKey<DamageType> t) { this.damageType = t; return this; }
        public Builder damage(float d) { this.damage = d; return this; }
        public Builder tag(AttackTag... t) { Collections.addAll(tags, t); return this; }
        public Builder knockback(Knockback k) { this.knockback = k; return this; }
        public Builder hitstun(int t) { this.hitstun = t; return this; }
        public Builder status(CombatStatus s, int ticks) { statuses.add(new StatusApplication(s, ticks)); return this; }
        public Builder origin(Vec3 o) { this.origin = o; return this; }
        public Builder guardDamage(int g) { this.guardDamage = g; return this; }
        public Builder fx(String fx, float scale) { this.impactFx = fx; this.impactScale = scale; return this; }
        public Builder noComboScaling() { this.comboScaling = false; return this; }
        public Builder onResolved(Consumer<HitResult> c) { this.onResolved = c; return this; }

        public Hit build() {
            return new Hit(this);
        }
    }
}
