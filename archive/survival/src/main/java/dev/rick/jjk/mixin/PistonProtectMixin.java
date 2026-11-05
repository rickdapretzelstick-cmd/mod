package dev.rick.jjk.mixin;

import dev.rick.jjk.progression.investigation.ProtectedPlaces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Pistons can't push, pull or break the blocks of the places the mod builds ({@link ProtectedPlaces}). */
@Mixin(PistonStructureResolver.class)
public abstract class PistonProtectMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void jjk$immovable(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(level instanceof ServerLevel sl) || !ProtectedPlaces.enabled()) return;
        for (BlockPos p : toPush) if (ProtectedPlaces.isProtected(sl, p)) {
            cir.setReturnValue(false);
            return;
        }
        for (BlockPos p : toDestroy) if (ProtectedPlaces.isProtected(sl, p)) {
            cir.setReturnValue(false);
            return;
        }
    }
}
