package dev.rick.jjk.progression.guardian;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.moment.Moment;
import dev.rick.jjk.progression.moment.Moments;
import dev.rick.jjk.progression.story.Vessels;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingEvaluator;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSchedule;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Capturing a defeated guardian's essence: its challenger uses an Exorcism Seal on its remnant and holds the essence
 * while it fights the seal, a short containment challenge on the shared skill check ({@link TrainingSchedule#containment}):
 * about seven seconds, three overlapping circles, one mistake allowed, the second fails. Failure leaves the remnant
 * where it is for another try at once (no seal is used, nothing is fought again). Success uses exactly one seal, binds
 * the essence into exactly one Sealed Grade 1 Essence (into the inventory, or an owner-bound claim if it's full), and
 * closes that contract's essence for good. The server decides everything; it runs as a {@link Moment}.
 */
public final class EssenceCapture {
    /** The skill check's kit name on the client (the HUD and keys are the training's). */
    public static final String KIT = "capture";

    private EssenceCapture() {}

    /** One containment: its challenger, the contract, and the remnant being contained. */
    public static final class Session extends Moment {
        public final String contract;
        final UUID remnant;

        Session(UUID player, String contract, UUID remnant) {
            super(player, remnant);
            this.contract = contract;
            this.remnant = remnant;
        }

        @Override
        public String kit() {
            return KIT;
        }

        @Override
        public JJKConfig.Training rules() {
            return EssenceCapture.rules();
        }

        @Nullable
        Entity remnant(ServerPlayer p) {
            return p.level() instanceof ServerLevel l ? l.getEntity(remnant) : null;
        }

        @Nullable
        @Override
        protected String broken(MinecraftServer server, @Nullable ServerPlayer p) {
            if (p == null || !p.isAlive()) return "";
            Entity r = remnant(p);
            return r == null || p.position().distanceToSqr(r.position()) > 12 * 12 ? "The remnant slips out of reach." : null;
        }

        @Override
        protected boolean holding() {
            return true;
        }

        @Override
        protected boolean allowDamage(LivingEntity entity, DamageSource source) {
            // Nobody hurts a challenger mid-containment (and the remnant can't be hurt at all).
            return false;
        }

        @Nullable
        @Override
        protected String cancelled() {
            return "You let the remnant be. It will wait.";
        }

        @Override
        protected int resultTicks(int state) {
            return state == TrainingNet.SUCCESS ? 30 : 40;
        }

        @Nullable
        @Override
        protected String resultMessage(int state) {
            return state == TrainingNet.FAILURE ? "It slips loose. The remnant is still there: use a seal on it again." : null;
        }

        @Override
        protected String syncLost() {
            return "The containment lost track of you (connection). Try again.";
        }

        @Override
        protected void onJudged(ServerPlayer p, TrainingEvaluator.Outcome o) {
            Entity r = remnant(p);
            if (r != null && p.level() instanceof ServerLevel l) {
                if (o.result() == TrainingCheck.MISS) {
                    l.sendParticles(p, ParticleTypes.SMOKE, true, false, r.getX(), r.getY() + 1, r.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
                    Fx.sound(l, r.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 1.6f);
                } else if (o.result() != TrainingCheck.HOLDING) {
                    Vec3 a = r.position().add(0, 1, 0), b = p.position().add(0, 1, 0);
                    for (int i = 0; i <= 8; i++) {
                        Vec3 q = a.lerp(b, i / 8.0);
                        l.sendParticles(p, new DustParticleOptions(0xB98BFF, 0.8f), true, false, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                    Fx.sound(l, r.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, o.result() == TrainingCheck.PERFECT ? 1.6f : 1.1f);
                }
            }
            if (eval.failed() && state == TrainingNet.ACTIVE) fail(p);
        }

        @Override
        protected void afterChecks(ServerPlayer p) {
            if (clock >= JJKConfig.get().training.captureDurationMs && eval.allDecided()) succeed(p);
        }

        private void fail(ServerPlayer p) {
            setState(p, TrainingNet.FAILURE);
            Entity r = remnant(p);
            if (r != null && p.level() instanceof ServerLevel l) {
                l.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, false, r.getX(), r.getY() + 1, r.getZ(), 20, 0.4, 0.6, 0.4, 0.04);
                Fx.sound(l, r.position(), SoundEvents.GLASS_BREAK, 0.8f, 0.6f);
            }
        }

        /** The essence is contained: one seal spent, one sealed essence, the contract's essence closed for good. */
        private void succeed(ServerPlayer p) {
            Incident in = Investigations.incident(p.level().getServer(), contract);
            if (Investigations.captureRefusal(in, p) != null || !consumeSeal(p)) {
                setState(p, TrainingNet.FAILURE);
                return;
            }
            String guardian = Investigations.guardianName(in);
            Investigations.markCaptured(p.level().getServer(), in, Vessels.sealed(contract, guardian), p);
            setState(p, TrainingNet.SUCCESS);
            Entity r = remnant(p);
            if (r != null && p.level() instanceof ServerLevel l) {
                // The energy pulls into the seal in the challenger's hands.
                Vec3 a = r.position().add(0, 1, 0), b = p.position().add(0, 1.1, 0);
                for (int i = 0; i <= 14; i++) {
                    Vec3 q = a.lerp(b, i / 14.0);
                    l.sendParticles(p, ParticleTypes.REVERSE_PORTAL, true, false, q.x, q.y, q.z, 3, 0.05, 0.05, 0.05, 0.01);
                }
                Fx.sound(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 0.8f);
                Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.5f);
                r.discard();
            }
            JJK.LOGGER.info("[guardians] {} captured the essence of contract {}", p.getName().getString(), contract);
        }

        @Override
        protected void onEnd(MinecraftServer server, @Nullable ServerPlayer p) {
            if (p != null && remnant(p) instanceof GuardianRemnantEntity g) g.setContaining(false);
        }
    }

    /** Nothing to set up of its own: it runs through {@link Moments}. */
    public static void init() {}

    public static boolean active(ServerPlayer p) {
        return Moments.of(p) instanceof Session;
    }

    @Nullable
    public static Session of(ServerPlayer p) {
        return Moments.of(p) instanceof Session s ? s : null;
    }

    /** The containment's settings. */
    public static JJKConfig.Training rules() {
        return TrainingSchedule.containmentRules(JJKConfig.get().training);
    }

    /** Whether a player carries an Exorcism Seal. */
    public static boolean hasSeal(ServerPlayer p) {
        return p.getInventory().contains(s -> s.is(ProgressionItems.EXORCISM_SEAL));
    }

    /** A seal used on a remnant. True if a containment began. */
    public static boolean begin(ServerPlayer p, GuardianRemnantEntity remnant) {
        if (Moments.busy(p) || remnant.containing()) return false;
        Incident in = Investigations.incident(p.level().getServer(), remnant.contract());
        String why = Investigations.captureRefusal(in, p);
        if (why != null) {
            p.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!hasSeal(p)) {
            p.sendOverlayMessage(Component.literal("You need an Exorcism Seal to contain it. It will wait.").withStyle(ChatFormatting.GRAY));
            return false;
        }
        JJKConfig.Training cfg = JJKConfig.get().training;
        JJKConfig.Training rules = rules();
        long seed = p.getRandom().nextLong();
        List<TrainingCheck> checks = TrainingSchedule.containment(seed, cfg);
        // Never a schedule that breaks the fairness rules (a bad seed is simply drawn again).
        for (int tries = 0; tries < 8 && !TrainingSchedule.problems(checks, rules).isEmpty(); tries++) {
            seed = p.getRandom().nextLong();
            checks = TrainingSchedule.containment(seed, cfg);
        }
        Session s = new Session(p.getUUID(), remnant.contract(), remnant.getUUID());
        s.prepare(checks, cfg.captureMaxMisses, cfg.captureIntroMs);
        final long chosen = seed;
        return Moments.begin(p, s, () -> {
            remnant.setContaining(true);
            ServerPlayNetworking.send(p, new TrainingNet.Start(s.id, s.attempt(), KIT, "CONTAIN THE ESSENCE", false, chosen, cfg.captureIntroMs,
                    cfg.captureDurationMs, rules.phase2AtMs, rules.phase3AtMs, cfg.captureMaxMisses, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle()),
                    List.of("BIND", "SEAL", "DRAW"), List.of(0xFFB98BFF, 0xFFFFE27A, 0xFF7FD3FF), s.checks()));
            s.setState(p, TrainingNet.INTRO);
            Fx.sound((ServerLevel) p.level(), p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 0.6f);
        });
    }

    /** A key timing (or READY) from the capturing player's client. False if they aren't containing anything. */
    public static boolean input(ServerPlayer p, TrainingNet.Input in) {
        return active(p) && Moments.input(p, in);
    }

    private static boolean consumeSeal(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(ProgressionItems.EXORCISM_SEAL)) {
                st.shrink(1);
                return true;
            }
        }
        return false;
    }

    // --- Tests ---

    public static void setManualClockForTest(boolean on) {
        Moments.setManualClockForTest(on);
    }

    public static void advanceForTest(ServerPlayer p, double ms) {
        if (active(p)) Moments.advanceForTest(p, ms);
    }

    public static void inputForTest(ServerPlayer p, TrainingNet.Input in) {
        input(p, in);
    }
}
