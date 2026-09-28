package dev.rick.jjk.core.net;

import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.melee.MeleeSystem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Payload registration and server-side input handling. */
public final class Network {
    private Network() {}

    public static void init() {
        var c2s = PayloadTypeRegistry.serverboundPlay();
        c2s.register(MeleeInputPayload.TYPE, MeleeInputPayload.CODEC);
        c2s.register(AbilityInputPayload.TYPE, AbilityInputPayload.CODEC);
        c2s.register(ClashInputPayload.TYPE, ClashInputPayload.CODEC);

        var s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(FxPayload.TYPE, FxPayload.CODEC);
        s2c.register(CameraPayload.TYPE, CameraPayload.CODEC);
        s2c.register(AnimPayload.TYPE, AnimPayload.CODEC);
        s2c.register(StatusPayload.TYPE, StatusPayload.CODEC);
        s2c.register(CastPayload.TYPE, CastPayload.CODEC);
        s2c.register(ComboPayload.TYPE, ComboPayload.CODEC);
        s2c.register(CasterSyncPayload.TYPE, CasterSyncPayload.CODEC);
        s2c.register(DomainPayload.TYPE, DomainPayload.CODEC);
        s2c.register(ClashStartPayload.TYPE, ClashStartPayload.CODEC);
        s2c.register(ClashUpdatePayload.TYPE, ClashUpdatePayload.CODEC);
        s2c.register(ClashEndPayload.TYPE, ClashEndPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MeleeInputPayload.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            AbilityCaster caster = Casters.active(player);
            if (caster == null) return;
            MeleeSystem.handleInput(player, caster, p.kind(), p.flags(), p.targetHint());
        });
        ServerPlayNetworking.registerGlobalReceiver(AbilityInputPayload.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            AbilityCaster caster = Casters.active(player);
            AbilitySlot slot = AbilitySlot.byIndex(p.slot());
            if (caster == null || slot == null) return;
            Entity hint = p.targetHint() >= 0 ? player.level().getEntity(p.targetHint()) : null;
            if (hint != null && hint.distanceToSqr(player) > 64 * 64) hint = null;
            caster.input(slot, p.pressed(), Mth.clamp(p.forward(), -1f, 1f), Mth.clamp(p.strafe(), -1f, 1f), hint);
        });
        ServerPlayNetworking.registerGlobalReceiver(ClashInputPayload.TYPE, (p, ctx) ->
                dev.rick.jjk.core.domain.clash.ClashManager.input(ctx.player(), p.session(), p.lane(), p.time()));
    }
}

