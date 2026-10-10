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
        c2s.register(CharacterSelectPayload.TYPE, CharacterSelectPayload.CODEC);
        c2s.register(MasteryPurchasePayload.TYPE, MasteryPurchasePayload.CODEC);
        c2s.register(MovesetSwitchPayload.TYPE, MovesetSwitchPayload.CODEC);
        c2s.register(InvestigatePayload.TYPE, InvestigatePayload.CODEC);
        c2s.register(RhythmInputPayload.TYPE, RhythmInputPayload.CODEC);
        c2s.register(BeamClashInputPayload.TYPE, BeamClashInputPayload.CODEC);

        var s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(NewsBoardPayload.TYPE, NewsBoardPayload.CODEC);
        s2c.register(ScopeViewPayload.TYPE, ScopeViewPayload.CODEC);
        s2c.register(CompassPayload.TYPE, CompassPayload.CODEC);
        s2c.register(RifleStatePayload.TYPE, RifleStatePayload.CODEC);
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
        s2c.register(DomainCinematicPayload.TYPE, DomainCinematicPayload.CODEC);
        s2c.register(DomainCounterPayload.TYPE, DomainCounterPayload.CODEC);
        s2c.register(GamblePayload.TYPE, GamblePayload.CODEC);
        s2c.register(YutaPayload.TYPE, YutaPayload.CODEC);
        s2c.register(BeamCounterPayload.TYPE, BeamCounterPayload.CODEC);
        s2c.register(RyuPayload.TYPE, RyuPayload.CODEC);
        s2c.register(BeamClashStatePayload.TYPE, BeamClashStatePayload.CODEC);
        s2c.register(BeamClashCheckPayload.TYPE, BeamClashCheckPayload.CODEC);
        s2c.register(BeamClashJudgePayload.TYPE, BeamClashJudgePayload.CODEC);
        s2c.register(ProgressionPayload.TYPE, ProgressionPayload.CODEC);
        s2c.register(PrisonPayload.TYPE, PrisonPayload.CODEC);
        s2c.register(MasterySyncPayload.TYPE, MasterySyncPayload.CODEC);
        s2c.register(StoryPayload.TYPE, StoryPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MasteryPurchasePayload.TYPE, (p, ctx) -> {
            var r = dev.rick.jjk.progression.mastery.Mastery.purchase(ctx.player(), p.tree(), p.node());
            if (r != dev.rick.jjk.progression.mastery.Mastery.Result.OK) {
                String why = switch (r) {
                    case NOT_YOURS -> "That technique isn't yours to develop.";
                    case OWNED -> "Already learned.";
                    case NEEDS -> "Learn what it builds on first.";
                    case POINTS -> "Not enough Mastery.";
                    default -> "That can't be learned.";
                };
                ctx.player().sendOverlayMessage(net.minecraft.network.chat.Component.literal(why).withStyle(net.minecraft.ChatFormatting.GRAY));
                // The screen waits for an answer either way.
                dev.rick.jjk.progression.mastery.Mastery.sync(ctx.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(InvestigatePayload.TYPE, (p, ctx) ->
                dev.rick.jjk.progression.investigation.CursedCompass.investigate(ctx.player(), p.incident()));
        ServerPlayNetworking.registerGlobalReceiver(MovesetSwitchPayload.TYPE, (p, ctx) ->
                dev.rick.jjk.progression.tool.kit.CursedKits.switchMoveset(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(MeleeInputPayload.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            AbilityCaster caster = Casters.armed(player);
            if (caster == null) return;
            MeleeSystem.handleInput(player, caster, p.kind(), p.flags(), p.targetHint());
        });
        ServerPlayNetworking.registerGlobalReceiver(AbilityInputPayload.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            AbilityCaster caster = Casters.armed(player);
            AbilitySlot slot = AbilitySlot.byIndex(p.slot());
            if (caster == null || slot == null) return;
            Entity hint = p.targetHint() >= 0 ? player.level().getEntity(p.targetHint()) : null;
            if (hint != null && hint.distanceToSqr(player) > 64 * 64) hint = null;
            caster.input(slot, p.pressed(), Mth.clamp(p.forward(), -1f, 1f), Mth.clamp(p.strafe(), -1f, 1f), hint);
        });
        ServerPlayNetworking.registerGlobalReceiver(CharacterSelectPayload.TYPE, (p, ctx) -> {
            String why = dev.rick.jjk.core.character.CharacterService.select(ctx.player(), p.character());
            // Survival with nothing earned: the quiet answer, not an error.
            if (dev.rick.jjk.progression.TechniqueProgression.NOT_AWAKENED.equals(why)) {
                ctx.player().sendOverlayMessage(net.minecraft.network.chat.Component.literal(why).withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }
            var c = dev.rick.jjk.core.character.Characters.get(p.character());
            ctx.player().sendOverlayMessage(why != null
                    ? net.minecraft.network.chat.Component.literal(why).withStyle(net.minecraft.ChatFormatting.RED)
                    : net.minecraft.network.chat.Component.literal(c == null ? "No character" : c.displayName() + " — " + c.title())
                            .withStyle(net.minecraft.ChatFormatting.AQUA));
        });
        ServerPlayNetworking.registerGlobalReceiver(RhythmInputPayload.TYPE, (p, ctx) ->
                dev.rick.jjk.hakari.RhythmAbility.input(ctx.player(), p.time()));
        ServerPlayNetworking.registerGlobalReceiver(ClashInputPayload.TYPE, (p, ctx) ->
                dev.rick.jjk.core.domain.clash.ClashManager.input(ctx.player(), p.session(), p.lane(), p.time()));
        ServerPlayNetworking.registerGlobalReceiver(BeamClashInputPayload.TYPE, (p, ctx) ->
                dev.rick.jjk.core.clash.BeamClashManager.input(ctx.player(), p.session(), p.check(), p.elapsedMs()));
    }
}

