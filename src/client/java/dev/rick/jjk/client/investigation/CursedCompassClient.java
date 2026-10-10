package dev.rick.jjk.client.investigation;

import com.mojang.serialization.MapCodec;
import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.CompassPayload;
import dev.rick.jjk.progression.ProgressionItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Cursed Compass's needle and what it does near what it follows. The server sends what the compass feels
 * ({@link CompassPayload}); the needle is a damped spring towards it, like a real needle: steady when the source is far,
 * trembling as it gets near, thrashing right on top of it. Dormant it hangs still; with nothing in range it wanders;
 * once what it followed is gone it goes slack.
 *
 * <p>Near the source: faint dark particles drift off the compass, and a low heartbeat quickens with closeness. All of it
 * runs on the client from one position the server sends twice a second: nothing per frame crosses the network.
 */
public final class CursedCompassClient {
    private static int state = CompassPayload.DORMANT;
    private static Vec3 source = Vec3.ZERO;
    private static long lastHeard = -1000;
    /** The needle, in turns (0..1, vanilla compass convention: 0.5 points straight ahead), and its speed. */
    private static float angle, speed;
    private static long lastTick = Long.MIN_VALUE;
    private static float wander;
    private static final RandomSource RANDOM = RandomSource.create();
    private static int beat;

    private CursedCompassClient() {}

    public static void init() {
        RangeSelectItemModelProperties.ID_MAPPER.put(JJK.id("cursed_compass"), Needle.MAP_CODEC);
        ClientTickEvents.END_CLIENT_TICK.register(CursedCompassClient::tick);
    }

    public static void apply(CompassPayload p) {
        state = p.state();
        source = new Vec3(p.x(), p.y(), p.z());
        Minecraft mc = Minecraft.getInstance();
        lastHeard = mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static int state() {
        return state;
    }

    /** The needle's current reading (for tests). */
    public static float angle() {
        return angle;
    }

    private static int stateNow(ClientLevel level) {
        // Not heard from in a while (dropped it, changed dimension): it has nothing to say.
        return level.getGameTime() - lastHeard > 60 ? CompassPayload.DORMANT : state;
    }

    /** How near the source the player is, 0 (at the edge of its range) to 1 (on it); -1 if it isn't tracking anything. */
    private static double closeness(Player p, ClientLevel level) {
        if (stateNow(level) != CompassPayload.TRAIL) return -1;
        double d = Math.sqrt(p.distanceToSqr(source));
        return Mth.clamp(1 - d / JJKConfig.get().realms.compassRange, 0, 1);
    }

    /** One step of the needle (a tick): a damped spring towards where it wants to point. */
    private static void step(Player p, ClientLevel level) {
        long now = level.getGameTime();
        if (now == lastTick) return;
        int steps = lastTick == Long.MIN_VALUE ? 1 : (int) Math.min(10, Math.max(1, now - lastTick));
        lastTick = now;
        for (int i = 0; i < steps; i++) {
            int s = stateNow(level);
            float target;
            float stiffness = 0.12f, damping = 0.78f, kick = 0;
            switch (s) {
                case CompassPayload.TRAIL -> {
                    double dx = source.x - p.getX(), dz = source.z - p.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    float toward = (float) (Math.atan2(dz, dx) / (Math.PI * 2));
                    float yaw = Mth.positiveModulo(p.getVisualRotationYInDegrees() / 360f, 1f);
                    target = 0.5f - (yaw - 0.25f - toward);
                    JJKConfig.Realms r = JJKConfig.get().realms;
                    if (dist < r.compassHere) {
                        // Right on top of it: it can't settle on anything.
                        target += now * 0.11f;
                        kick = 0.06f;
                    } else if (dist < r.compassNear) {
                        kick = (float) (0.025 * (1 - (dist - r.compassHere) / (r.compassNear - r.compassHere)));
                    }
                }
                case CompassPayload.FAINT -> {
                    if (RANDOM.nextInt(30) == 0) wander = RANDOM.nextFloat();
                    target = wander;
                    stiffness = 0.02f;
                    damping = 0.9f;
                }
                // Dormant or slack: it hangs (pointing back at the holder).
                default -> {
                    target = 0f;
                    stiffness = s == CompassPayload.GONE ? 0.01f : 0.05f;
                    damping = 0.85f;
                }
            }
            float diff = Mth.positiveModulo(target - angle + 0.5f, 1f) - 0.5f;
            speed = speed * damping + diff * stiffness + (kick > 0 ? (RANDOM.nextFloat() - 0.5f) * kick : 0);
            angle = Mth.positiveModulo(angle + speed, 1f);
        }
    }

    private static boolean holding(Player p) {
        return p.getMainHandItem().is(ProgressionItems.CURSED_COMPASS) || p.getOffhandItem().is(ProgressionItems.CURSED_COMPASS);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        Player p = mc.player;
        if (!holding(p)) return;
        step(p, mc.level);
        double c = closeness(p, mc.level);
        if (c < 0) return;
        double dist = Math.sqrt(p.distanceToSqr(source));
        JJKConfig.Realms r = JJKConfig.get().realms;
        if (dist < r.compassNear) {
            double near = 1 - dist / r.compassNear;
            // Dark motes off the compass, more of them the nearer it is.
            if (RANDOM.nextFloat() < 0.15 + near * 0.6) {
                Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.35, 0);
                mc.level.addParticle(RANDOM.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.WITCH, hand.x + (RANDOM.nextFloat() - 0.5) * 0.3,
                        hand.y, hand.z + (RANDOM.nextFloat() - 0.5) * 0.3, 0, 0.01, 0);
            }
            // A heartbeat: slow at the edge, quick on top of it.
            int interval = (int) Mth.lerp(near, 40, 8);
            if (++beat >= interval) {
                beat = 0;
                float vol = (float) (0.25 + near * 0.55);
                mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, vol, 0.8f + (float) near * 0.3f, false);
            }
        } else {
            beat = 0;
        }
    }

    /** The {@code jjk:cursed_compass} item model property: the needle's frame. */
    public record Needle() implements RangeSelectItemModelProperty {
        public static final MapCodec<Needle> MAP_CODEC = MapCodec.unit(new Needle());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            if (level == null || owner == null || !(owner.asLivingEntity() instanceof Player p) || !p.isLocalPlayer()) {
                // Someone else's, or in a frame: it simply hangs.
                return 0f;
            }
            step(p, level);
            return angle;
        }

        @Override
        public MapCodec<Needle> type() {
            return MAP_CODEC;
        }
    }
}
