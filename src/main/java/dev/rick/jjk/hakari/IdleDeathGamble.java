package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.structure.StructureSpec;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModBlocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Domain Expansion: Idle Death Gamble. Built by the same domain framework as every other domain (feet-first physical
 * formation, snapshots and exact restoration, barrier, clashes, cinematics, counters) out of its own blocks: the white
 * room of the Jujutsu Shenanigans domain. Its sure-hit is harmless: everyone caught inside is frozen in place for the
 * opening while the rules of the pachinko game are imparted, and what it really does is run Hakari's gamble
 * ({@link Gamble}) toward a Jackpot. It lasts 80 seconds but breaks after its last scenario.
 */
public final class IdleDeathGamble implements DomainDefinition {
    public static final IdleDeathGamble INSTANCE = new IdleDeathGamble();
    public static final String ID = "idle_death_gamble";
    private static final Map<Integer, Gamble> GAMBLES = new HashMap<>();

    private IdleDeathGamble() {}

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Idle Death Gamble";
    }

    @Override
    public int clashColor() {
        return 0xFFFF4FA8;
    }

    @Override
    public double radius(LivingEntity owner) {
        return JJKConfig.get().hakari.domainRadius;
    }

    @Override
    public int duration(LivingEntity owner) {
        return JJKConfig.get().hakari.domainDuration;
    }

    @Override
    public int formingTicks() {
        return JJKConfig.get().hakari.domainFormationTicks;
    }

    @Override
    public StructureSpec structure(LivingEntity owner) {
        JJKConfig.Domain dc = JJKConfig.get().domain;
        return new StructureSpec(JJKConfig.get().hakari.domainRadius, Math.max(1, dc.shellThickness), ModBlocks.IDG_BARRIER.defaultBlockState(),
                ModBlocks.IDG_FLOOR.defaultBlockState(), dc.clearInterior);
    }

    @Override
    public boolean closedBarrier() {
        return true;
    }

    /** The gamble running in the domain Hakari owns, if any. */
    @Nullable
    public static Gamble gambleOf(LivingEntity owner) {
        for (Gamble g : GAMBLES.values()) if (g.domain.owner == owner && g.domain.isLive()) return g;
        return null;
    }

    @Override
    public void onActivated(DomainInstance domain) {
        Gamble g = new Gamble(domain);
        GAMBLES.put(domain.id, g);
        g.sync();
    }

    @Override
    public void onTick(DomainInstance domain) {
        Gamble g = GAMBLES.get(domain.id);
        // The reels run only while the domain runs its own rules (paused through a clash, resumed after).
        if (g != null && domain.phase() == DomainInstance.Phase.ACTIVE) g.tick();
        // The casino hums: lights sweep and balls pour down every so often.
        if (domain.age() % 30 == 0) Fx.play(domain.level, "idg_ambient", domain.center, Vec3.ZERO, (float) domain.radius, domain.owner.getId());
    }

    @Override
    public void applySureHit(DomainInstance domain, LivingEntity target, int ticksInside) {
        // The rules of the game are imparted: harmless, but everyone inside knows exactly what is happening.
        if (ticksInside == 1) Fx.play(domain.level, "idg_rules", target.getBoundingBox().getCenter(), Vec3.ZERO, 1f, target.getId());
        Statuses.apply(target, CombatStatus.IN_DOMAIN, 5);
        // The neutral stage: frozen in place while the rules are explained.
        if (ticksInside <= JJKConfig.get().hakari.domainFreezeTicks) Statuses.apply(target, CombatStatus.HITSTUN, 2);
        // Someone was caught: the pity jackpot is possible.
        Gamble g = GAMBLES.get(domain.id);
        if (g != null) g.caught();
    }

    @Override
    public void onCollapse(DomainInstance domain, DomainInstance.EndReason reason) {
        Gamble g = GAMBLES.remove(domain.id);
        if (g != null) {
            g.ended();
            g.removed();
        }
    }

    @Override
    public boolean burnoutOnCollapse(DomainInstance domain, DomainInstance.EndReason reason) {
        // Hitting the jackpot is the point of the domain: Hakari walks out of it at full power.
        Gamble g = GAMBLES.get(domain.id);
        return !(g != null && g.hitJackpot()) && !dev.rick.jjk.core.combat.Combat.has(domain.owner, CombatStatus.JACKPOT);
    }

    public static void clearAll() {
        GAMBLES.clear();
    }
}
