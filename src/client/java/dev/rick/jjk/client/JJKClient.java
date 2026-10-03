package dev.rick.jjk.client;

import dev.rick.jjk.JJK;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.fx.ClientFx;
import dev.rick.jjk.client.fx.ScreenEffects;
import dev.rick.jjk.client.hud.CombatHud;
import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.client.particle.EnergyParticle;
import dev.rick.jjk.client.render.DummyRenderer;
import dev.rick.jjk.client.render.TechniqueRenderer;
import dev.rick.jjk.client.render.WorldEffectsRenderer;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.net.AnimPayload;
import dev.rick.jjk.core.net.CameraPayload;
import dev.rick.jjk.core.net.CastPayload;
import dev.rick.jjk.core.net.CasterSyncPayload;
import dev.rick.jjk.core.net.ComboPayload;
import dev.rick.jjk.core.net.DomainPayload;
import dev.rick.jjk.core.net.FxPayload;
import dev.rick.jjk.core.net.StatusPayload;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.gojo.HollowPurpleAbility;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class JJKClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.BLUE, TechniqueRenderer.blue());
        EntityRendererRegistry.register(ModEntities.RED, TechniqueRenderer.red());
        EntityRendererRegistry.register(ModEntities.HOLLOW_PURPLE, TechniqueRenderer.purple());
        EntityRendererRegistry.register(ModEntities.TRAINING_DUMMY, DummyRenderer::new);
        EntityRendererRegistry.register(ModEntities.RIKA, dev.rick.jjk.client.render.RikaRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOMAIN_BLADE, dev.rick.jjk.client.render.DomainBladeRenderer::new);
        EntityRendererRegistry.register(ModEntities.PACHINKO_BALL, dev.rick.jjk.client.render.HakariRenderers.ball());
        EntityRendererRegistry.register(ModEntities.HAKARI_DOOR, dev.rick.jjk.client.render.HakariRenderers.door());
        EntityRendererRegistry.register(ModEntities.THROWN_PROP, dev.rick.jjk.client.render.YujiRenderers.prop());
        EntityRendererRegistry.register(ModEntities.FIRE_ARROW, dev.rick.jjk.client.render.YujiRenderers.arrow());
        EntityRendererRegistry.register(ModEntities.FINGER_BEARER, dev.rick.jjk.client.render.FingerBearerRenderer::new);
        EntityRendererRegistry.register(ModEntities.CURSED_ENERGY_SHOT, dev.rick.jjk.client.render.FingerBearerRenderer.Shot::new);

        InputHandler.init();
        dev.rick.jjk.client.anim.AnimLibrary.init();
        dev.rick.jjk.client.model.BbModels.init();
        dev.rick.jjk.client.anim.AnimDebug.init();
        dev.rick.jjk.client.render.DummyRenderer.registerLayer();
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) {
                helper.register(new dev.rick.jjk.client.render.BlindfoldLayer(renderer));
                helper.register(new dev.rick.jjk.client.render.YutaGearLayer(renderer));
            }
        });
        // The animation debugger works in either mode (it only shows once turned on).
        HudElementRegistry.addLast(JJK.id("anim_debug"), (g, delta) -> dev.rick.jjk.client.anim.AnimDebug.renderHud(g));
        // Every custom HUD layer is skipped in Vanilla Minecraft mode.
        HudElementRegistry.addLast(JJK.id("combat_hud"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) CombatHud.render(g, delta);
        });
        HudElementRegistry.addLast(JJK.id("gamble_hud"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.hud.GambleHud.render(g);
        });
        HudElementRegistry.addLast(JJK.id("rhythm_hud"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.hud.RhythmClient.render(g);
        });
        HudElementRegistry.addLast(JJK.id("clash_hud"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.clash.ClashHud.render(g);
        });
        HudElementRegistry.addLast(JJK.id("beam_clash_hud"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.clash.BeamClashHud.render(g);
        });
        HudElementRegistry.addLast(JJK.id("gojo_presentation"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.hud.GojoPresentation.render(g);
        });
        HudElementRegistry.addLast(JJK.id("idg_opening"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.hud.IdgOpening.render(g);
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.hud.ShrineOverlay.render(g);
        });
        HudElementRegistry.addLast(JJK.id("domain_cinematic"), (g, delta) -> {
            if (dev.rick.jjk.client.CombatMode.enabled()) dev.rick.jjk.client.cinematic.DomainCinematic.render(g, delta.getGameTimeDeltaPartialTick(false));
        });
        // "JJK Settings" on the pause menu (Combat Mode ON / VANILLA and HUD options).
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(net.minecraft.client.gui.components.Button.builder(
                        net.minecraft.network.chat.Component.literal("JJK Settings"),
                        b -> client.gui.setScreen(new dev.rick.jjk.client.hud.JJKSettingsScreen(screen))).bounds(6, 6, 90, 20).build());
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(WorldEffectsRenderer::render);
        registerReceivers();

        ClientTickEvents.END_CLIENT_TICK.register(JJKClient::tick);
        ClientTickEvents.START_CLIENT_TICK.register(InputHandler::beforeVanillaKeys);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            InputHandler.releaseAll(null);
            ClientState.reset();
            ClientProgression.reset();
            dev.rick.jjk.client.hud.YutaHud.reset();
            ClientAnimations.clear();
            ScreenEffects.reset();
            dev.rick.jjk.client.render.Flashes.clear();
            dev.rick.jjk.client.fx.UnlimitedPurpleFx.clear();
            dev.rick.jjk.client.clash.ClashClient.reset();
            dev.rick.jjk.client.cinematic.DomainCinematic.reset();
            dev.rick.jjk.client.clash.ClashCamera.reset();
            dev.rick.jjk.client.clash.BeamClashClient.reset();
            dev.rick.jjk.client.clash.BeamClashCamera.reset();
            dev.rick.jjk.client.hud.RyuHud.reset();
        });
    }

    private static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (p, ctx) -> ClientFx.handle(p));
        ClientPlayNetworking.registerGlobalReceiver(CasterSyncPayload.TYPE, (p, ctx) -> ClientState.apply(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.YutaPayload.TYPE, (p, ctx) -> dev.rick.jjk.client.hud.YutaHud.apply(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.RyuPayload.TYPE, (p, ctx) -> dev.rick.jjk.client.hud.RyuHud.apply(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.ProgressionPayload.TYPE, (p, ctx) -> ClientProgression.apply(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.BeamClashStatePayload.TYPE, (p, ctx) -> dev.rick.jjk.client.clash.BeamClashClient.state(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.BeamClashCheckPayload.TYPE, (p, ctx) -> dev.rick.jjk.client.clash.BeamClashClient.check(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.BeamClashJudgePayload.TYPE, (p, ctx) -> dev.rick.jjk.client.clash.BeamClashClient.judge(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.BeamCounterPayload.TYPE, (p, ctx) -> dev.rick.jjk.client.clash.BeamClashClient.counter(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.GamblePayload.TYPE, (p, ctx) -> {
            if (ctx.client().level != null) ClientState.applyGamble(p, ctx.client().level.getGameTime());
        });
        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE, (p, ctx) -> {
            switch (p.kind()) {
                case CameraPayload.SHAKE -> ScreenEffects.shake(p.intensity(), p.duration());
                case CameraPayload.FLASH -> ScreenEffects.flash(p.color(), p.duration());
                case CameraPayload.FOV -> ScreenEffects.fovPunch(p.intensity());
                case CameraPayload.IMPACT -> ScreenEffects.impact(p.duration());
                default -> {}
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(AnimPayload.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) ClientAnimations.play(p.entityId(), p.anim(), p.speed(), mc.level.getGameTime());
        });
        ClientPlayNetworking.registerGlobalReceiver(StatusPayload.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(p.entityId()) instanceof LivingEntity le) {
                Combat.state(le).load(p.ticks(), p.guarding());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CastPayload.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            long now = mc.level.getGameTime();
            if (p.ability().isEmpty()) {
                ClientState.CASTS.remove(p.entityId());
                if (mc.player != null && p.entityId() == mc.player.getId()) ScreenEffects.fovHold(0);
                return;
            }
            ClientState.Cast old = ClientState.CASTS.get(p.entityId());
            long start = old != null && old.ability().equals(p.ability()) ? old.startTick() : now;
            ClientState.CASTS.put(p.entityId(), new ClientState.Cast(p.ability(), p.phase(), p.duration(), start, now));
            if (mc.player != null && p.entityId() == mc.player.getId()) {
                // Charging narrows the view; Purple and the domain sign pull it in hardest.
                float hold = switch (p.ability()) {
                    case "red" -> -0.05f;
                    case HollowPurpleAbility.ID -> p.phase() >= HollowPurpleAbility.PHASE_FUSION ? -0.12f : -0.06f;
                    case "unlimited_void" -> -0.1f;
                    default -> 0f;
                };
                ScreenEffects.fovHold(p.phase() == HollowPurpleAbility.PHASE_FIRED && p.ability().equals(HollowPurpleAbility.ID) ? 0 : hold);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ComboPayload.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            ClientState.comboCount = p.count();
            ClientState.comboDamage = p.damage();
            ClientState.comboTime = mc.level.getGameTime();
            ClientState.lastHitOutcome = p.outcome();
            // Hit confirm: a small kick so connecting feels different from whiffing.
            if (p.outcome() == HitResult.Outcome.HIT.ordinal() || p.outcome() == HitResult.Outcome.GUARD_BROKEN.ordinal()) {
                ScreenEffects.shake(0.12f, 3);
                ScreenEffects.fovPunch(0.012f);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.ClashStartPayload.TYPE, (p, ctx) -> dev.rick.jjk.client.clash.ClashClient.start(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.ClashUpdatePayload.TYPE, (p, ctx) -> dev.rick.jjk.client.clash.ClashClient.update(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.ClashEndPayload.TYPE, (p, ctx) -> dev.rick.jjk.client.clash.ClashClient.end(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.DomainCinematicPayload.TYPE,
                (p, ctx) -> dev.rick.jjk.client.cinematic.DomainCinematic.start(p));
        ClientPlayNetworking.registerGlobalReceiver(dev.rick.jjk.core.net.DomainCounterPayload.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            ClientState.counterUntilTick = mc.level.getGameTime() + p.ticks();
            ClientState.counterWindow = Math.max(1, p.ticks());
            ClientState.counterDomain = p.domain();
            if (p.ticks() > 0 && mc.player != null) ClientFx.sound("clash_countdown", mc.player.position(), 1f, 1.4f);
        });
        ClientPlayNetworking.registerGlobalReceiver(DomainPayload.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) ClientState.applyDomain(p, mc.level.getGameTime());
        });
    }


    /** Max Blue owns the battlefield: a wide field of light and dust pouring in, a sub-bass drone, the ground trembling. */
    private static void maxBlueAmbient(Minecraft mc, BlueEntity blue, Vec3 core) {
        var rnd = mc.level.getRandom();
        for (int i = 0; i < ClientFx.q(10); i++) {
            Vec3 from = core.add(ClientFx.randomUnit().multiply(1, 0.5, 1).scale(8 + rnd.nextDouble() * 10));
            Vec3 tangent = from.subtract(core).cross(new Vec3(0, 1, 0)).normalize().scale(0.25);
            float[] c = i % 4 == 0 ? ClientFx.WHITE : i % 2 == 0 ? ClientFx.BLUE_LIGHT : ClientFx.BLUE;
            ClientFx.add(mc.level, from, tangent, EnergyParticle.Sprite.GLOW, c, 0.8f, 0.3f, 0.05f, 30).attract(core, 0.035).fadeIn();
        }
        // Grit torn off the ground and dragged toward the core.
        if (blue.tickCount % 2 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 5 + rnd.nextDouble() * 12;
            net.minecraft.core.BlockPos ground = net.minecraft.core.BlockPos.containing(core.x + Math.cos(a) * r, core.y - 1, core.z + Math.sin(a) * r);
            var st = mc.level.getBlockState(ground);
            if (!st.isAir()) {
                Vec3 at = Vec3.atCenterOf(ground).add(0, 0.6, 0);
                Vec3 v = core.subtract(at).normalize().scale(0.5).add(0, 0.25, 0);
                for (int i = 0; i < ClientFx.q(3); i++) {
                    mc.level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, st),
                            at.x, at.y, at.z, v.x, v.y, v.z);
                }
            }
        }
        if ((blue.tickCount + blue.getId()) % 36 == 0) ClientFx.sound("max_blue_hum", core, 3f, 1f);
        double d = mc.player.position().distanceTo(core);
        if (d < 24 && blue.tickCount % 4 == 0) ScreenEffects.shake((float) (0.25 * (1 - d / 24)), 5);
    }

    /** Awakened: light rising off the body like heat, and a faint rim of blue around it. */
    private static void awakenedAura(Minecraft mc, LivingEntity le) {
        boolean self = le == mc.player && mc.options.getCameraType().isFirstPerson();
        var rnd = mc.level.getRandom();
        int n = self ? 1 : ClientFx.q(3);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 0.35 + rnd.nextDouble() * 0.3;
            Vec3 at = le.position().add(Math.cos(a) * r, rnd.nextDouble() * le.getBbHeight(), Math.sin(a) * r);
            ClientFx.add(mc.level, at, new Vec3(0, 0.05 + rnd.nextDouble() * 0.05, 0), EnergyParticle.Sprite.GLOW,
                    i % 3 == 0 ? ClientFx.WHITE : ClientFx.BLUE_LIGHT, 0.6f, 0.12f, 0.02f, 14).fadeIn();
        }
    }

    private static int ambientTicks;

    private static void tick(Minecraft mc) {
        ScreenEffects.tick();
        if (mc.level == null || mc.player == null) return;
        ClientState.tick();
        dev.rick.jjk.client.clash.ClashClient.tick(mc);
        dev.rick.jjk.client.cinematic.DomainCinematic.tick(mc);
        dev.rick.jjk.client.fx.UnlimitedPurpleFx.tick(mc);
        dev.rick.jjk.client.clash.ClashCamera.tick(mc);
        dev.rick.jjk.client.clash.BeamClashClient.tick(mc);
        dev.rick.jjk.client.clash.BeamClashCamera.tick(mc);
        InputHandler.tick(mc);
        ambientTicks++;
        ambient(mc);
    }

    /** Continuous effects around live techniques: Blue's inward spiral and hum, the domain's ambience. */
    private static void roughArm(Minecraft mc, LivingEntity le) {
        float yaw = le.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 arm = le.position().add(0, le.getBbHeight() * 0.62, 0).add(right.scale(0.4)).add(fwd.scale(-0.1));
        for (int i = 0; i < ClientFx.q(4); i++) {
            Vec3 at = arm.add(ClientFx.randomUnit().scale(0.3)).add(0, -mc.level.getRandom().nextDouble() * 0.5, 0);
            ClientFx.add(mc.level, at, new Vec3(0, 0.02 + mc.level.getRandom().nextDouble() * 0.03, 0), EnergyParticle.Sprite.GLOW,
                    i % 4 == 0 ? ClientFx.WHITE : new float[] {0.35f, 1f, 0.62f}, 0.9f, 0.16f + mc.level.getRandom().nextFloat() * 0.12f, 0.04f, 8).fadeIn();
        }
        if (mc.level.getRandom().nextInt(3) == 0) {
            Vec3 at = arm.add(ClientFx.randomUnit().scale(0.35));
            ClientFx.add(mc.level, at, Vec3.ZERO, EnergyParticle.Sprite.SMOKE, new float[] {0.05f, 0.12f, 0.08f}, 0.7f, 0.2f, 0.02f, 6);
        }
    }

    private static void ambient(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof BlueEntity blue && !blue.isCollapsing()) {
                Vec3 core = blue.position().add(0, 0.5, 0);
                boolean max = blue.scale() >= dev.rick.jjk.client.render.TechniqueRenderer.MAX_BLUE_SCALE;
                if (max) {
                    maxBlueAmbient(mc, blue, core);
                } else {
                    for (int i = 0; i < ClientFx.q(3); i++) {
                        Vec3 from = core.add(ClientFx.randomUnit().scale(2 + mc.level.getRandom().nextDouble() * 3));
                        Vec3 tangent = from.subtract(core).cross(new Vec3(0, 1, 0)).normalize().scale(0.1);
                        ClientFx.add(mc.level, from, tangent, EnergyParticle.Sprite.GLOW, i % 3 == 0 ? ClientFx.WHITE : ClientFx.BLUE,
                                0.7f, 0.1f, 0.03f, 18).attract(core, 0.05).fadeIn();
                    }
                    if ((blue.tickCount + blue.getId()) % 30 == 0) ClientFx.sound("blue_hum", core, 1.2f, 1f);
                }
            } else if (e instanceof LivingEntity le && Combat.has(le, CombatStatus.AWAKENED)) {
                awakenedAura(mc, le);
            }
            // Rough Energy's wind-up (JJS GIF): green, black-edged cursed energy boiling up the right arm, growing.
            if (e instanceof LivingEntity le) {
                ClientState.Cast cast = ClientState.CASTS.get(le.getId());
                if (cast != null && "rough_energy".equals(cast.ability())) roughArm(mc, le);
            }
        }
        for (ClientState.Domain d : ClientState.DOMAINS.values()) {
            if (d.center == null || d.phase != DomainPayload.ACTIVE) continue;
            if (mc.player.position().distanceTo(d.center) < d.radius) {
                if (ambientTicks % 60 == 0) ClientFx.sound("domain_ambient", mc.player.position(), 0.8f, 1f);
                // Motes of information drifting through the void.
                for (int i = 0; i < ClientFx.q(3); i++) {
                    Vec3 at = d.center.add(ClientFx.randomUnit().scale(mc.level.getRandom().nextDouble() * d.radius));
                    ClientFx.add(mc.level, at, ClientFx.randomUnit().scale(0.02), EnergyParticle.Sprite.STAR, ClientFx.WHITE, 0.6f, 0.08f, 0.02f, 40).fadeIn();
                }
            }
        }
        // Local player overloaded: keep the white noise in the ears.
        if (Combat.has(mc.player, CombatStatus.OVERLOAD) && ambientTicks % 25 == 0) {
            ClientFx.sound("domain_surehit", mc.player.position(), 0.35f, 0.8f + mc.level.getRandom().nextFloat() * 0.4f);
        }
    }
}
