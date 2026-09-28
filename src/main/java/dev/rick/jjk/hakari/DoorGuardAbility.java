package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.HakariDoorEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Special — Door Guard. Hold to raise a lacquered gamble door in front of Hakari. The door stops everything that comes at
 * it from the front — fists, techniques, projectiles — though sure-hits and true unblockables still pass. Catch a punch
 * in the first moments and the door swings open into the attacker, staggering them. See {@link DoorGuardDefense}.
 */
public final class DoorGuardAbility extends Ability {
    public static final String ID = "door_guard";
    /** Who has their door up, since when (game time), and the door itself. */
    static final Map<LivingEntity, Long> RAISED = new WeakHashMap<>();
    static final Map<LivingEntity, HakariDoorEntity> DOORS = new WeakHashMap<>();

    public DoorGuardAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.doorGuardCooldown;
    }

    @Override
    public boolean cooldownOnEnd() {
        return true;
    }

    static boolean isRaised(LivingEntity e) {
        return RAISED.containsKey(e);
    }

    static Vec3 doorSpot(LivingEntity user) {
        return user.position().add(HakariCombat.flat(user).scale(1.05));
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private HakariDoorEntity door;
            private int swing = -1;

            @Override
            public void start() {
                Anim.play(user, "door_guard");
                RAISED.put(user, level.getGameTime());
                door = HakariDoorEntity.spawn(level, user, HakariDoorEntity.GUARD, doorSpot(user), user.getYRot(), 6);
                DOORS.put(user, door);
                setPhase(0, JJKConfig.get().hakari.doorGuardMaxTicks);
                Fx.play(level, "door_guard_up", doorSpot(user).add(0, 1.2, 0), HakariCombat.flat(user), 1f, user.getId());
            }

            @Override
            public void tick() {
                if (door != null && !door.isRemoved()) {
                    Vec3 f = HakariCombat.flat(user);
                    door.place(doorSpot(user), (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG) - 90f);
                    // The counter swing plays out over a few ticks.
                    if (swing >= 0) door.setOpen(Math.min(1f, ++swing / 4f));
                }
                if (swing < 0 && door != null && door.open() > 0) swing = 0;
                if (!held || age >= JJKConfig.get().hakari.doorGuardMaxTicks || swing > 8) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0.25f;
            }

            @Override
            public void end() {
                RAISED.remove(user);
                DOORS.remove(user);
                if (door != null) {
                    Fx.play(level, "door_guard_down", door.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                    door.discard();
                }
            }
        };
    }
}
