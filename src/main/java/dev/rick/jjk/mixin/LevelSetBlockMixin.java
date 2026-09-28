package dev.rick.jjk.mixin;

import dev.rick.jjk.core.world.WorldRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets world restoration see the original of every block that technique damage changes, including knock-on effects. */
@Mixin(Level.class)
public abstract class LevelSetBlockMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void jjk$recordOriginal(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level) WorldRestoration.beforeSetBlock(level, pos, state);
    }
}
