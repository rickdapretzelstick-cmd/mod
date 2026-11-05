package dev.rick.jjk.progression.training;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.yuta.RikaEntity;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The five scenes, each in its own room ({@link TrialRooms}). Each answers the same hooks with its own feedback: a
 * success steadies something, a PERFECT steadies it completely, a miss disturbs it (more with each mistake), the fifth
 * miss ends it harmlessly, and the victory is the scene's own payoff. Nothing here hurts the participant, costs cursed
 * energy, touches their inventory, or changes anything outside the room.
 */
public final class TrainingScenes {
    private TrainingScenes() {}

    static TrainingScene create(TrainingSessions.Session s) {
        return switch (s.story.kit()) {
            case "gojo" -> new Gojo(s);
            case "yuta" -> new Yuta(s);
            case "ryu" -> new Ryu(s);
            case "hakari" -> new Hakari(s);
            default -> new Yuji(s);
        };
    }

    // ================================================================================================================
    // Yuji: emotional control. The Cursed Screening Room: on the centre sofa facing the CRT, a cursed corpse doll on the
    // lap. The television's mood (shown on its animated screen) announces faster or slower checks.
    // ================================================================================================================

    static final class Yuji extends TrainingScene {
        static final int CALM = 0, SUSPENSE = 1, ACTION = 2, SAD = 3;
        private static final int CE = 0x5FA8FF;
        @Nullable private Display.BlockDisplay seat;
        @Nullable private ArmorStand doll;
        private int mood = -1, disturbedCheck = -1, disturbances;
        private float agitation;
        private float headX, headY, armX, armTargetX, headTargetX;
        private boolean quiet;

        Yuji(TrainingSessions.Session s) {
            super(s);
        }

        /** The seat: on the centre sofa's cushions (the participant's spot is the cushion top). */
        private Vec3 seatAt() {
            return rel(0, 0.12, 0.05);
        }

        private Vec3 tv() {
            return marker("television", new Vec3(0, 2.5, 17));
        }

        @Override
        protected void build() {
            seat = display(Blocks.AIR.defaultBlockState(), seatAt(), 1f, false);
            ArmorStand d = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, realm);
            d.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS,
                    (byte) (ArmorStand.CLIENT_FLAG_SMALL | ArmorStand.CLIENT_FLAG_SHOW_ARMS | ArmorStand.CLIENT_FLAG_NO_BASEPLATE | ArmorStand.CLIENT_FLAG_MARKER));
            d.refreshDimensions();
            d.setNoGravity(true);
            d.setPermanentlyInvulnerable(true);
            var scale = d.getAttribute(Attributes.SCALE);
            if (scale != null) scale.setBaseValue(0.8);
            d.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
            d.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            Vec3 lap = seatAt().add(0, 0.3, 0.37);
            d.setPos(lap.x, lap.y, lap.z);
            d.setYRot(0f);
            d.setYBodyRot(0f);
            d.setLeftLegPose(new Rotations(-90, 0, 0));
            d.setRightLegPose(new Rotations(-90, 0, 0));
            realm.addFreshEntity(d);
            owned.add(d);
            doll = d;
            // The screen's glow on the room, and a little warm light over the sofa (the lanterns do the rest).
            lightAt(tv().add(0, -0.5, -1.2), 12);
            lightAt(rel(0, 2.2, 1.5), 7);
            lightAt(rel(-3, 2.5, 4), 6);
            lightAt(rel(3, 2.5, 4), 6);
        }

        @Override
        @Nullable
        Entity seat() {
            return seat;
        }

        @Override
        float pitch() {
            return 4f;
        }

        @Override
        Vec3 camera() {
            // Behind the sofa, over the shoulder: the participant, the doll and the television across the room.
            return new Vec3(-2.3, 2.2, -2.6);
        }

        @Override
        Vec3 look() {
            return new Vec3(0.2, 1.7, 6.0);
        }

        @Override
        void reset(ServerPlayer p) {
            setMood(CALM);
            disturbedCheck = -1;
            agitation = 0;
            quiet = false;
            headX = headTargetX = 10;
            armX = armTargetX = -10;
            headY = 0;
            screen(0.2f);
            pose();
        }

        private void setMood(int m) {
            if (m == mood) return;
            mood = m;
            TrialPropEntity crt = prop("trial_yuji_crt");
            if (crt != null) crt.setState(m);
        }

        /** How restless the static is (0 calm .. 1.5 agitated). */
        private void screen(float a) {
            TrialPropEntity crt = prop("trial_yuji_crt");
            if (crt != null) crt.setParam(a);
        }

        private void pose() {
            if (doll == null) return;
            doll.setHeadPose(new Rotations(headX, headY, 0));
            doll.setRightArmPose(new Rotations(armX, 0, 8));
            doll.setLeftArmPose(new Rotations(quiet ? -10 : armX * 0.4f, 0, -8));
        }

        @Override
        void intro(ServerPlayer p, int tick) {
            if (tick == 1) {
                sound(p, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 0.8f);
                sound(p, SoundEvents.BEACON_AMBIENT, 0.4f, 0.6f);
            }
            // The doll's head comes up slowly, as if it had heard something.
            headTargetX = tick < 25 ? 10 : -12;
            ease();
        }

        @Override
        void cue(ServerPlayer p, TrainingCheck c) {
            if (c.mod() == TrainingCheck.FAST) {
                // Suspense (or action) on the screen: the next check comes in faster.
                setMood(disturbances++ % 2 == 0 ? SUSPENSE : ACTION);
                disturbedCheck = c.id();
                sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 1.3f);
            } else if (c.mod() == TrainingCheck.SLOW) {
                setMood(SAD);
                disturbedCheck = c.id();
                sound(p, SoundEvents.NOTE_BLOCK_FLUTE, 0.7f, 0.55f);
            }
        }

        @Override
        void active(ServerPlayer p, int tick, double clock, int misses) {
            int ph = phase(clock);
            if (tick % 20 == 1) lighting(ph == 1 ? 7 : ph == 2 ? 8 : 10);
            if (tick % 40 == 1) sound(p, SoundEvents.BEACON_AMBIENT, 0.25f, 0.55f);
            // The disturbance is over once its check is decided: the screen calms.
            if (disturbedCheck >= 0 && session.eval.result(disturbedCheck) >= 0 && session.eval.result(disturbedCheck) != TrainingCheck.HOLDING) {
                disturbedCheck = -1;
                setMood(CALM);
            }
            if (agitation > 0) {
                agitation = Math.max(0, agitation - 0.02f);
                if (tick % 3 == 0) headY = (realm.getRandom().nextFloat() - 0.5f) * agitation * 18f;
            } else {
                headY *= 0.8f;
            }
            if (tick % 5 == 0) screen(0.2f + agitation * 0.6f + (ph - 1) * 0.1f);
            if (tick % 6 == 0) particles(p, dust(CE, 0.5f), seatAt().add(0, 0.6, 0.15), 1, 0.15, 0);
            ease();
        }

        private void ease() {
            headX += (headTargetX - headX) * 0.25f;
            armX += (armTargetX - armX) * 0.3f;
            pose();
            if (Math.abs(armX - armTargetX) < 2) armTargetX = quiet ? -10 : armTargetX * 0.95f - 10 * 0.05f;
        }

        @Override
        void judged(ServerPlayer p, TrainingCheck c, int result, int misses) {
            Vec3 chest = seatAt().add(0, 0.63, 0.13), lap = seatAt().add(0, 0.5, 0.4);
            if (result == TrainingCheck.MISS) {
                agitation = Math.min(1.5f, 0.3f + misses * 0.3f);
                screen(0.4f + agitation * 0.6f);
                // A twitch; then it lifts its head; then it raises an arm.
                if (misses <= 1) headTargetX = -28;
                else if (misses == 2) headTargetX = -45;
                else armTargetX = -120 - misses * 6;
                sound(p, SoundEvents.ZOMBIE_AMBIENT, 0.35f + misses * 0.08f, 1.7f);
                sound(p, SoundEvents.ARMOR_STAND_HIT, 0.4f, 0.8f);
                particles(p, ParticleTypes.SMOKE, lap.add(0, 0.25, 0), 4, 0.1, 0.01);
                return;
            }
            if (result == TrainingCheck.HOLDING) return;
            // A steady pulse of cursed energy into the doll.
            stream(p, dust(CE, result == TrainingCheck.PERFECT ? 0.9f : 0.7f), chest, lap, 6);
            agitation = Math.max(0, agitation - 0.35f);
            if (result == TrainingCheck.PERFECT) {
                // Clean: the doll relaxes.
                headTargetX = 22;
                armTargetX = -10;
                ring(p, ParticleTypes.END_ROD, lap.add(0, 0.1, 0), 0.25, 8);
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            } else {
                headTargetX = Math.max(headTargetX, 0);
                sound(p, SoundEvents.NOTE_BLOCK_CHIME, 0.45f, 1.0f);
            }
        }

        @Override
        void failure(ServerPlayer p, int tick) {
            if (tick == 1) {
                // The doll's harmless punch, and the screen goes to red static.
                setMood(SUSPENSE);
                screen(1.5f);
                armTargetX = -95;
                armX = -60;
                headTargetX = -35;
                sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
                particles(p, ParticleTypes.CRIT, seatAt().add(0, 0.98, 0.2), 8, 0.12, 0.2);
            }
            if (tick == 8) armTargetX = -10;
            ease();
        }

        @Override
        void victory(ServerPlayer p, int tick) {
            if (tick == 1) {
                setMood(CALM);
                screen(0f);
                quiet = true;
                agitation = 0;
                headTargetX = 25;
                armTargetX = -10;
                sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
            }
            // A steady aura: slow rings rising round the body.
            if (tick % 4 == 0) ring(p, dust(CE, 0.8f), seatAt().add(0, -0.4 + (tick % 24) / 24.0, 0), 0.6, 12);
            ease();
        }
    }

    // ================================================================================================================
    // Gojo: precision. The Hall of Impossible Precision: the participant before the stepped dais, the Blue held over it
    // with the hall's rings turning round it, and three stones (one per gauge) drawn into orbit round the Blue.
    // ================================================================================================================

    static final class Gojo extends TrainingScene {
        private static final double OUTER = 2.6, INNER = 1.45;
        private static final float STONE = 0.75f;
        private static final net.minecraft.world.level.block.Block[] STONES = {Blocks.COBBLESTONE, Blocks.STONE, Blocks.ANDESITE};
        private final Display.BlockDisplay[] stones = new Display.BlockDisplay[3];
        private final double[] progress = new double[3], shown = new double[3], wobble = new double[3], drop = new double[3];
        private final double[] base = {90, 210, 330};
        private double orbScale = 1;
        private int clockTick;

        Gojo(TrainingSessions.Session s) {
            super(s);
        }

        private Vec3 orbAt() {
            return marker("blue_core", new Vec3(0, 4.9, 9));
        }

        @Override
        float pitch() {
            Vec3 d = orbAt().subtract(at.add(0, 1.62, 0));
            return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        }

        @Override
        protected void build() {
            // The Blue's own light, and a cool fill over the dais.
            lightAt(orbAt().add(0, -1.6, 0), 12);
            lightAt(orbAt().add(-3, -2.5, -3), 9);
            lightAt(orbAt().add(3, -2.5, -3), 9);
            lightAt(rel(0, 2.5, 1), 8);
            TrainingPreset preset = TrainingPreset.of("gojo");
            for (int i = 0; i < 3; i++) {
                stones[i] = display(STONES[i].defaultBlockState(), stonePos(i, 0), STONE, false);
                stones[i].setGlowingTag(true);
                stones[i].setGlowColorOverride(preset.colors().get(i) & 0xFFFFFF);
            }
        }

        @Override
        Vec3 camera() {
            // Behind the participant's right shoulder, low: the Blue over the dais, the colonnade round it.
            return new Vec3(-2.6, 2.3, -3.4);
        }

        @Override
        Vec3 look() {
            return new Vec3(0.3, 3.4, 6.0);
        }

        @Override
        void reset(ServerPlayer p) {
            for (int i = 0; i < 3; i++) {
                progress[i] = shown[i] = wobble[i] = drop[i] = 0;
            }
            orbScale = 1;
            clockTick = 0;
            place(0);
        }

        private Vec3 stonePos(int i, double radius) {
            double a = Math.toRadians(base[i] + clockTick * 1.1);
            double r = radius <= 0 ? OUTER : radius;
            double w = wobble[i] * Math.sin(clockTick * 1.3 + i);
            return orbAt().add(Math.cos(a) * (r + w), Math.sin(a) * (r + w) * 0.35 - drop[i], Math.sin(a) * (r + w) * 0.9);
        }

        private void place(double collapse) {
            for (int i = 0; i < 3; i++) {
                if (stones[i] == null) continue;
                double r = Mth.lerp(shown[i], OUTER, INNER) * (1 - collapse);
                Vec3 pos = stonePos(i, Math.max(0.05, r));
                double floor = orbAt().y - 4.2;
                if (pos.y < floor) pos = new Vec3(pos.x, floor, pos.z);
                move(stones[i], pos);
                shape(stones[i], STONE, (float) (clockTick * (2 + i)), (float) (wobble[i] * 40));
            }
            TrialPropEntity blue = prop("trial_gojo_blue");
            if (blue != null) blue.setParam((float) orbScale);
        }

        @Override
        void intro(ServerPlayer p, int tick) {
            clockTick++;
            if (tick == 1) {
                Fx.play(realm, "blue_spawn", orbAt(), Vec3.ZERO, 0.8f, p.getId());
                sound(p, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
            }
            orbScale = Math.min(1, tick / 30.0);
            place(0);
        }

        @Override
        void active(ServerPlayer p, int tick, double clock, int misses) {
            clockTick++;
            int ph = phase(clock);
            if (tick % 20 == 1) lighting(ph == 1 ? 11 : ph == 2 ? 13 : 15);
            for (int i = 0; i < 3; i++) {
                wobble[i] *= 0.9;
                shown[i] += (progress[i] - shown[i]) * 0.15;
            }
            place(0);
            if (tick % 2 == 0) {
                double a = tick * 0.4;
                particles(p, dust(0x3D7BFF, 0.9f), orbAt().add(Math.cos(a) * 1.25, Math.sin(a * 0.7) * 0.3, Math.sin(a) * 1.25), 1, 0.02, 0);
            }
            if (tick % 30 == 1) sound(p, SoundEvents.BEACON_AMBIENT, 0.3f, 1.5f);
        }

        @Override
        void judged(ServerPlayer p, TrainingCheck c, int result, int misses) {
            int i = c.gauge();
            Vec3 pos = stones[i] != null ? stones[i].position() : orbAt();
            if (result == TrainingCheck.MISS) {
                // A wobble and an outward drift; it finds its path again.
                wobble[i] = 0.7;
                progress[i] = Math.max(0, progress[i] - 0.08);
                particles(p, ParticleTypes.CLOUD, pos, 3, 0.1, 0.02);
                sound(p, SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.6f);
                return;
            }
            if (result == TrainingCheck.HOLDING) return;
            int col = TrainingPreset.of("gojo").colors().get(i);
            if (result == TrainingCheck.PERFECT) {
                progress[i] = Math.min(1, progress[i] + 0.13);
                wobble[i] = 0;
                particles(p, ParticleTypes.END_ROD, pos, 5, 0.12, 0.01);
                sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
            } else {
                progress[i] = Math.min(1, progress[i] + 0.09);
                wobble[i] *= 0.5;
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.2f);
            }
            stream(p, dust(col, 0.7f), pos, orbAt(), 5);
        }

        @Override
        void failure(ServerPlayer p, int tick) {
            clockTick++;
            if (tick == 1) {
                // Blue comes apart; the stones fall.
                sound(p, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.4f);
                particles(p, ParticleTypes.CLOUD, orbAt(), 16, 0.4, 0.03);
            }
            orbScale = Math.max(0, 1 - tick / 10.0);
            for (int i = 0; i < 3; i++) drop[i] = Math.min(4.2, drop[i] + 0.05 * tick);
            place(0);
        }

        @Override
        void victory(ServerPlayer p, int tick) {
            if (tick < 40) clockTick++;
            for (int i = 0; i < 3; i++) {
                // Evenly spaced, held; then drawn inward as Blue collapses.
                double even = i * 120 + 90;
                base[i] += Mth.wrapDegrees(even - (base[i] + clockTick * 1.1) % 360) * 0.12;
                progress[i] = 1;
                shown[i] += (1 - shown[i]) * 0.2;
                wobble[i] = 0;
            }
            double collapse = tick < 40 ? 0 : Math.min(1, (tick - 40) / 14.0);
            if (tick >= 40) orbScale = Math.max(0, 1 - collapse);
            place(collapse);
            if (tick == 40) {
                Anim.play(p, "blue_cast");
                sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
            }
            if (tick == 54) {
                Fx.play(realm, "blue_spawn", orbAt(), Vec3.ZERO, 1.2f, p.getId());
                sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
                for (Display.BlockDisplay s : stones) if (s != null) shape(s, 0f, 0, 0);
            }
        }
    }

    // ================================================================================================================
    // Yuta: the Shrine of the Bound Promise. A practice katana in hand, the oath circle round him, the altar's blade
    // glowing with his control, and Rika (the mod's own) on the dais at the shrine's rear, facing him.
    // ================================================================================================================

    static final class Yuta extends TrainingScene {
        private static final int BLADE = 0xC9B6FF, RIKA = 0xFF86D8;
        @Nullable private RikaEntity rika;
        private double glow = 0.4;
        private int flicker, restless, holdHum;
        private boolean broken;

        Yuta(TrainingSessions.Session s) {
            super(s);
        }

        /** Rika's place: over the dais at the shrine's rear, behind the altar. */
        private Vec3 home() {
            return marker("rika", new Vec3(0, 1.2, 15.6));
        }

        private Vec3 sword() {
            return marker("sword", new Vec3(0, 1.9, 7.5));
        }

        @Override
        protected void build() {
            // Purple gloom: the candles and lanterns, a little light on the circle, a backlight behind Rika.
            lightAt(rel(0, 2.0, 2.5), 7);
            lightAt(sword().add(0, 1.0, -1.0), 8);
            lightAt(home().add(0, 1.5, -1.6), 9);
            lightAt(home().add(0, 5.5, 0.8), 7);
        }

        @Override
        Vec3 camera() {
            // Behind his left shoulder: the blade in his hand, the altar and Rika beyond it.
            return new Vec3(2.4, 2.3, -3.2);
        }

        @Override
        Vec3 look() {
            return new Vec3(-0.3, 2.4, 6.5);
        }

        @Override
        void reset(ServerPlayer p) {
            glow = 0.4;
            flicker = restless = holdHum = 0;
            broken = false;
            if (rika == null || rika.isRemoved()) {
                rika = RikaEntity.summon(realm, p);
                owned.add(rika);
            }
            Vec3 h = home();
            rika.recall();
            rika.setPos(h.x, h.y, h.z);
            rika.station();
            rika.setTarget(null);
            // Fully manifested and turned to face him: only how she looks (nothing of Yuta's kit is touched).
            rika.set(RikaEntity.FULL, true);
            rika.faceToward(at);
            Anim.playOn(rika, "rika_idle");
            blade();
        }

        /** The altar's blade: its glow follows his control; it flickers after a mistake and goes dark on failure. */
        private void blade() {
            TrialPropEntity b = prop("trial_yuta_blade");
            if (b == null) return;
            b.setParam((float) (broken ? 0.05 : 0.25 + glow * 0.75));
            b.setState(broken ? 2 : flicker > 0 ? 1 : 0);
        }

        /** The practice katana's blade, from the right hand forward (the participant faces south: their right is west). */
        private Vec3[] handBlade() {
            Vec3 hand = rel(-0.36, 0.78, 0.18);
            return new Vec3[] {hand, hand.add(-0.05, -0.28, 1.1)};
        }

        @Override
        void intro(ServerPlayer p, int tick) {
            if (tick == 1) {
                sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.8f);
                Anim.playOn(rika, "rika_summon");
            }
            if (tick % 3 == 0) {
                Vec3[] b = handBlade();
                stream(p, dust(BLADE, 0.4f + tick / 80f), b[0], b[1], 5);
            }
        }

        @Override
        void active(ServerPlayer p, int tick, double clock, int misses) {
            int ph = phase(clock);
            if (tick % 20 == 1) lighting(ph == 1 ? 7 : ph == 2 ? 9 : 11);
            Vec3[] b = handBlade();
            if (flicker > 0) {
                flicker--;
                if (flicker % 3 == 0) particles(p, ParticleTypes.ELECTRIC_SPARK, b[0].lerp(b[1], realm.getRandom().nextDouble()), 1, 0.05, 0.01);
            } else if (tick % 2 == 0) {
                stream(p, dust(BLADE, (float) (0.35 + glow * 0.6)), b[0], b[1], 6);
            }
            if (holdHum > 0 && --holdHum % 5 == 0) particles(p, dust(BLADE, 0.9f), b[1], 2, 0.08, 0);
            blade();
            // Rika: restless after a mistake, still when calmed (never far from her dais).
            if (rika != null) {
                if (restless > 0) {
                    restless--;
                    if (tick % 6 == 0) {
                        RandomSource r = realm.getRandom();
                        rika.moveTo(home().add((r.nextDouble() - 0.5) * 0.9, r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.6), 0.25, 6);
                    }
                } else if (tick % 20 == 0) {
                    rika.moveTo(home(), 0.15, 20);
                }
            }
        }

        @Override
        void judged(ServerPlayer p, TrainingCheck c, int result, int misses) {
            Vec3[] b = handBlade();
            if (result == TrainingCheck.MISS) {
                flicker = 14;
                glow = Math.max(0, glow - 0.15);
                restless = 40 + misses * 10;
                for (int i = 0; i < 4; i++) particles(p, ParticleTypes.CRIT, b[0].lerp(b[1], realm.getRandom().nextDouble()), 2, 0.05, 0.05);
                particles(p, ParticleTypes.ELECTRIC_SPARK, sword().add(0, 0.6, 0), 4, 0.1, 0.02);
                sound(p, SoundEvents.GLASS_BREAK, 0.25f, 1.8f);
                blade();
                return;
            }
            if (result == TrainingCheck.HOLDING) {
                holdHum = 40;
                sound(p, SoundEvents.BEACON_AMBIENT, 0.5f, 1.6f);
                return;
            }
            glow = Math.min(1, glow + 0.1);
            if (c.gauge() == 2) {
                restless = 0;
                if (rika != null) stream(p, dust(RIKA, 0.7f), rika.position().add(0, 2.2, 0), rel(0, 1.2, 0), 10);
            } else if (c.gauge() == 1) {
                ring(p, dust(BLADE, 0.7f), rel(0, 0.1, 0), 0.7, 14);
            }
            if (result == TrainingCheck.PERFECT) {
                stream(p, ParticleTypes.END_ROD, b[0], b[1], 7);
                particles(p, ParticleTypes.END_ROD, sword().add(0, 0.8, 0), 3, 0.1, 0.01);
                sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.5f);
            } else {
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.1f);
            }
            blade();
        }

        @Override
        void failure(ServerPlayer p, int tick) {
            if (tick == 1) {
                // The blade's energy breaks apart (the blade itself, and everything carried, is untouched).
                broken = true;
                glow = 0;
                flicker = 60;
                Vec3[] b = handBlade();
                for (int i = 0; i <= 6; i++) particles(p, dust(BLADE, 1f), b[0].lerp(b[1], i / 6.0), 3, 0.12, 0.05);
                sound(p, SoundEvents.GLASS_BREAK, 0.8f, 1.1f);
                restless = 60;
            }
            active(p, tick, session.cfg.durationMs, session.eval.misses());
        }

        @Override
        void victory(ServerPlayer p, int tick) {
            Vec3[] b = handBlade();
            if (tick == 1) {
                glow = 1;
                flicker = 0;
                restless = 0;
                blade();
                sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.3f);
                // She comes forward to the altar's edge, still behind the blade.
                if (rika != null) rika.moveTo(home().add(0, 0.4, -3.2), 0.08, 70);
            }
            if (tick % 2 == 0) stream(p, dust(BLADE, 1f), b[0], b[1], 7);
            if (tick % 10 == 0 && rika != null) particles(p, ParticleTypes.HEART, rika.position().add(0, 3.6, 0), 1, 0.2, 0);
        }
    }

    // ================================================================================================================
    // Ryu: output stabilization. The Granite Output Crucible: on the firing platform at the end of the long gallery, the
    // concentric target down range; the charge gathers at his head, and only a clean run fires.
    // ================================================================================================================

    static final class Ryu extends TrainingScene {
        private static final int GOLD = 0xFFD45A;
        private double charge, target;
        private int vent;
        private boolean dispersed;

        Ryu(TrainingSessions.Session s) {
            super(s);
        }

        /** The bullseye, on the target wall's face. */
        private Vec3 bullseye() {
            return marker("target", new Vec3(0, 5.9, 32.6));
        }

        @Override
        protected void build() {
            // Warm work lights along the range (the lanterns do the rest), and one on the target.
            lightAt(rel(0, 2.5, 2), 8);
            lightAt(rel(0, 3.5, 12), 7);
            lightAt(rel(0, 3.5, 22), 7);
            lightAt(bullseye().add(0, -2.5, -3), 10);
            shattered(false);
        }

        /** The target's face: whole for every attempt; shattered only by the victory's beam (the wall behind stays). */
        private void shattered(boolean gone) {
            TrialPropEntity t = prop("trial_ryu_target");
            if (t != null) t.setFlag(TrialPropEntity.HIDDEN, gone);
        }

        @Override
        Vec3 camera() {
            // Behind and beside the platform, down the gallery to the target.
            return new Vec3(-2.6, 2.6, -3.8);
        }

        @Override
        Vec3 look() {
            return new Vec3(0.4, 2.8, 12.0);
        }

        @Override
        float pitch() {
            Vec3 d = bullseye().subtract(at.add(0, 1.62, 0));
            return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        }

        @Override
        void reset(ServerPlayer p) {
            shattered(false);
            charge = target = 0.15;
            vent = 0;
            dispersed = false;
        }

        private Vec3 head(ServerPlayer p) {
            return p.getEyePosition().add(0, 0.3, 0).add(p.getLookAngle().scale(0.45));
        }

        @Override
        void intro(ServerPlayer p, int tick) {
            if (tick == 1) sound(p, SoundEvents.BEACON_AMBIENT, 0.4f, 0.6f);
            if (tick % 4 == 0) particles(p, ParticleTypes.POOF, rel(0, 0.1, 0), 2, 0.4, 0.01);
        }

        @Override
        void active(ServerPlayer p, int tick, double clock, int misses) {
            int ph = phase(clock);
            if (tick % 20 == 1) lighting(ph == 1 ? 7 : ph == 2 ? 9 : 12);
            if (tick == 1) Fx.play(realm, "granite_charge", head(p), p.getLookAngle(), 0f, p.getId());
            double done = session.eval.checks().isEmpty() ? 0 : session.eval.successes() / (double) session.eval.checks().size();
            target = 0.15 + 0.3 * clock / session.cfg.durationMs + 0.55 * done;
            charge += (target - charge) * 0.08;
            if (vent > 0) vent--;
            if (dispersed) return;
            Vec3 h = head(p);
            double strength = vent > 0 ? charge * 0.4 : charge;
            int n = 1 + (int) (strength * 5);
            double radius = 0.95 - 0.55 * strength;
            for (int i = 0; i < n; i++) {
                double a = tick * 0.35 + i * Math.PI * 2 / n;
                particles(p, dust(GOLD, (float) (0.5 + strength * 0.8)), h.add(Math.cos(a) * radius, Math.sin(a * 1.3) * 0.25, Math.sin(a) * radius), 1, 0.01, 0);
            }
            if (tick % 5 == 0) particles(p, ParticleTypes.POOF, rel(0, 0.05, 0), 1, 0.5, 0.01);
            if (tick % 10 == 0) sound(p, SoundEvents.BEACON_AMBIENT, (float) (0.2 + charge * 0.4), (float) (0.6 + charge * 1.2));
        }

        @Override
        void judged(ServerPlayer p, TrainingCheck c, int result, int misses) {
            Vec3 h = head(p);
            if (result == TrainingCheck.MISS) {
                // The energy vents out sideways and the charge dims.
                vent = 12;
                charge = Math.max(0.05, charge - 0.1);
                Vec3 side = c.gauge() == 0 ? new Vec3(1, 0, 0) : c.gauge() == 2 ? new Vec3(-1, 0, 0) : new Vec3(realm.getRandom().nextBoolean() ? 1 : -1, 0, 0);
                for (int i = 0; i < 6; i++) {
                    realm.sendParticles(p, ParticleTypes.CLOUD, true, false, h.x, h.y, h.z, 0, side.x, 0.1, side.z, 0.25);
                }
                sound(p, SoundEvents.FIRE_EXTINGUISH, 0.45f, 1.5f);
                return;
            }
            if (result == TrainingCheck.HOLDING) return;
            particles(p, ParticleTypes.END_ROD, h, result == TrainingCheck.PERFECT ? 8 : 4, 0.12, 0.01);
            if (result == TrainingCheck.PERFECT) {
                ring(p, dust(0xFFFFFF, 0.8f), h, 0.55, 12);
                sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.8f);
            } else {
                sound(p, SoundEvents.NOTE_BLOCK_BIT, 0.5f, 1.2f);
            }
        }

        @Override
        void failure(ServerPlayer p, int tick) {
            if (tick == 1) {
                // The charge disperses: nothing fires, nothing breaks.
                dispersed = true;
                charge = 0;
                particles(p, ParticleTypes.POOF, head(p), 20, 0.4, 0.04);
                sound(p, SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.7f, 0.9f);
            }
        }

        @Override
        void victory(ServerPlayer p, int tick) {
            Vec3 h = head(p);
            if (tick == 1) {
                Anim.play(p, "ryu_granite_aim");
                Fx.play(realm, "granite_charge", h, p.getLookAngle(), 1f, p.getId());
            }
            if (tick < 14 && tick % 2 == 0) particles(p, dust(0xFFFFFF, 1.1f), h, 3, 0.1, 0);
            if (tick == 14) {
                // One scripted beam down the gallery into the session's own target: no damage, no terrain touched.
                Vec3 to = bullseye();
                Anim.play(p, "ryu_granite_fire_held");
                Fx.play(realm, "granite_blast_held", h, to.subtract(h), 1f, p.getId());
            }
            if (tick == 17) {
                Vec3 t = bullseye();
                shattered(true);
                realm.sendParticles(p, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POLISHED_ANDESITE.defaultBlockState()), true, false,
                        t.x, t.y, t.z, 90, 2.0, 2.0, 0.3, 0.25);
                Fx.play(realm, "granite_hit", t, new Vec3(0, 0, -1), 1.6f, p.getId());
                sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.1f);
            }
            if (tick > 17 && tick < 60 && tick % 4 == 0) particles(p, ParticleTypes.LARGE_SMOKE, bullseye().add(0, 0, -0.4), 2, 1.2, 0.01);
        }
    }

    // ================================================================================================================
    // Hakari: the Restless Jackpot Palace. Down the red carpet, the stage's three giant reels (one per gauge, left to
    // right): they spin while a check is live and lock on a seven for a hit, slip onto the bust bar for a miss. The reels
    // show the server's symbols (TrialPropEntity's state); the client only animates the spin and the settle.
    // ================================================================================================================

    static final class Hakari extends TrainingScene {
        /** Reel symbols (trial_reel_symbols.png, top to bottom): 0 the seven, 1 emerald, 2 diamond, 3 the bust bar, 4 coin, 5 star. */
        static final int SEVEN = 0, BUST = 3, SYMBOLS = 6;
        /** A reel's result light: none, a hit, a miss, a PERFECT. */
        static final int NONE = 0, HIT = 1, MISSED = 2, PERFECT = 3;
        private static final float[] SCALE = {0.5f, 0.56f, 0.63f, 0.67f, 0.75f, 0.84f, 0.94f, 1f};
        private final int[] payline = new int[3], below = new int[3], result = new int[3];
        private final boolean[] spinning = new boolean[3];
        private final int[] slip = new int[3];
        private int dip, beat, flare;
        private boolean celebrating;

        Hakari(TrainingSessions.Session s) {
            super(s);
        }

        /** The reel windows' centre (the middle tower's upper window), from the room's marker. */
        private Vec3 reelAt(int i) {
            return marker("reels", new Vec3(0, 5.9, 18)).add(5.4 * (1 - i), 2.1, -0.6);
        }

        @Override
        protected void build() {
            // Warm pavilion light: the lanterns, a fill on the carpet, and light on the stage.
            lightAt(rel(0, 2.5, 2), 8);
            lightAt(reelAt(1).add(0, -4.5, -2.5), 10);
            lightAt(reelAt(0).add(0, -4.5, -2.5), 8);
            lightAt(reelAt(2).add(0, -4.5, -2.5), 8);
        }

        @Override
        Vec3 camera() {
            // Behind and above, down the carpet to the stage and its reels.
            return new Vec3(-2.6, 2.7, -4.2);
        }

        @Override
        Vec3 look() {
            return new Vec3(0.2, 3.8, 9.0);
        }

        @Override
        float pitch() {
            return -10f;
        }

        @Override
        void reset(ServerPlayer p) {
            RandomSource r = realm.getRandom();
            for (int i = 0; i < 3; i++) {
                payline[i] = 1 + r.nextInt(SYMBOLS - 1);
                below[i] = 1 + r.nextInt(SYMBOLS - 1);
                spinning[i] = true;
                slip[i] = 0;
                result[i] = NONE;
            }
            dip = beat = flare = 0;
            celebrating = false;
            sync();
            Anim.stop(p);
        }

        /** The reels' state for every client: per reel, the payline and lower symbols, spinning, and its result light. */
        private void sync() {
            TrialPropEntity reels = prop("trial_hakari");
            if (reels == null) return;
            int packed = 0;
            for (int i = 0; i < 3; i++) {
                int v = payline[i] | below[i] << 3 | (spinning[i] ? 1 : 0) << 6 | result[i] << 7;
                packed |= v << (i * 9);
            }
            reels.setState(packed);
        }

        /** Packs as {@link #sync} does (the renderer's side of it: {@code TrialPropRenderer}). */
        static int symbolOf(int packed, int reel, boolean lower) {
            return packed >> (reel * 9 + (lower ? 3 : 0)) & 7;
        }

        private void lock(int i, int symbol, int res) {
            RandomSource r = realm.getRandom();
            spinning[i] = false;
            payline[i] = symbol;
            below[i] = 1 + r.nextInt(SYMBOLS - 1);
            result[i] = res;
        }

        @Override
        void intro(ServerPlayer p, int tick) {
            if (tick == 1) sound(p, SoundEvents.BELL_BLOCK, 0.8f, 1.2f);
        }

        @Override
        void cue(ServerPlayer p, TrainingCheck c) {
            spinning[c.gauge()] = true;
            slip[c.gauge()] = 0;
            result[c.gauge()] = NONE;
            sync();
        }

        @Override
        void active(ServerPlayer p, int tick, double clock, int misses) {
            int ph = phase(clock);
            if (tick % 20 == 1) lighting(ph == 1 ? 9 : ph == 2 ? 12 : 15);
            boolean changed = false;
            for (int i = 0; i < 3; i++) {
                if (slip[i] > 0 && --slip[i] == 0) {
                    spinning[i] = true;
                    result[i] = NONE;
                    changed = true;
                }
            }
            if (changed) sync();
            // The music: a steady beat, more of it each phase; it dips after a miss.
            if (tick % 8 == 0) {
                beat++;
                float vol = dip > 0 ? 0.3f : 0.75f;
                sound(p, SoundEvents.NOTE_BLOCK_HAT, vol * 0.7f, 1.2f);
                if (beat % 2 == 0) sound(p, SoundEvents.NOTE_BLOCK_BASEDRUM, vol, 0.9f);
                if (ph >= 2 && beat % 4 == 2) sound(p, SoundEvents.NOTE_BLOCK_SNARE, vol, 1.0f);
                if (ph >= 3) sound(p, SoundEvents.NOTE_BLOCK_PLING, vol * 0.6f, SCALE[beat % SCALE.length] * 1.4f);
                if (flare > 0 || ph >= 3) particles(p, ParticleTypes.WAX_ON, reelAt(beat % 3).add(0, 2.8, 0), 2, 0.8, 0.02);
            }
            if (dip > 0) dip--;
            if (flare > 0) flare--;
        }

        @Override
        void judged(ServerPlayer p, TrainingCheck c, int result, int misses) {
            int i = c.gauge();
            if (result == TrainingCheck.MISS) {
                // The reel slips onto the bust bar, and the music dips.
                lock(i, BUST, MISSED);
                slip[i] = 10;
                dip = 24;
                sync();
                particles(p, ParticleTypes.SMOKE, reelAt(i), 6, 0.6, 0.01);
                sound(p, SoundEvents.NOTE_BLOCK_DIDGERIDOO, 0.7f, 0.6f);
                return;
            }
            if (result == TrainingCheck.HOLDING) return;
            lock(i, SEVEN, result == TrainingCheck.PERFECT ? PERFECT : HIT);
            sync();
            particles(p, ParticleTypes.HAPPY_VILLAGER, reelAt(i), 8, 0.8, 0);
            if (result == TrainingCheck.PERFECT) {
                flare = 8;
                particles(p, ParticleTypes.FIREWORK, reelAt(i), 12, 0.8, 0.05);
                sound(p, SoundEvents.NOTE_BLOCK_BELL, 0.8f, 2.0f);
            } else {
                sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.7f, 1.5f);
            }
        }

        @Override
        void failure(ServerPlayer p, int tick) {
            if (tick == 1) {
                int[] wrong = {SEVEN, BUST, 4};
                for (int i = 0; i < 3; i++) lock(i, wrong[i], i == 0 ? HIT : MISSED);
                sync();
                lighting(3);
                sound(p, SoundEvents.NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.5f);
            }
            if (tick == 6) sound(p, SoundEvents.NOTE_BLOCK_BASS, 0.8f, 0.5f);
            if (tick == 12) sound(p, SoundEvents.NOTE_BLOCK_BASS, 0.8f, 0.4f);
        }

        @Override
        void victory(ServerPlayer p, int tick) {
            if (tick == 1) {
                celebrating = true;
                for (int i = 0; i < 3; i++) {
                    lock(i, SEVEN, PERFECT);
                    below[i] = SEVEN;
                }
                sync();
                lighting(15);
                Fx.play(realm, "jackpot_aura", p.position().add(0, 1, 0), Vec3.ZERO, 1f, p.getId());
                Anim.play(p, "rhythm_dance");
                sound(p, SoundEvents.PLAYER_LEVELUP, 0.9f, 1.0f);
            }
            if (tick % 4 == 0) {
                beat++;
                sound(p, SoundEvents.NOTE_BLOCK_BELL, 0.6f, SCALE[beat % SCALE.length] * 1.6f);
            }
            if (tick % 6 == 0) for (int i = 0; i < 3; i++) particles(p, ParticleTypes.FIREWORK, reelAt(i), 6, 0.9, 0.06);
        }

        @Override
        void cleanup(@Nullable ServerPlayer p) {
            if (p != null && celebrating) Anim.stop(p);
            super.cleanup(p);
        }
    }
}
