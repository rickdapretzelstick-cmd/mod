package dev.rick.jjk.mixin;

import dev.rick.jjk.progression.investigation.ProtectedPlaces;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The last word on a player breaking a block of the places the mod builds ({@link ProtectedPlaces}): whatever path the
 * break came by (Fabric's break event only runs when mining starts), a survival or adventure player's break there does
 * nothing, and their client is told the block is still there. Creative players (admins) still can.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerBreakProtectMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Shadow
    public abstract boolean isCreative();

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void jjk$holdProtected(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (isCreative() || player.isSpectator() || !ProtectedPlaces.isProtected(level, pos)) return;
        if (player.connection != null) player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        cir.setReturnValue(false);
    }
}
