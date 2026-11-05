package dev.rick.jjk.mixin;

import dev.rick.jjk.progression.investigation.ProtectedPlaces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Explosions (TNT, creepers, any mod's) leave the places the mod builds standing ({@link ProtectedPlaces}). */
@Mixin(ServerExplosion.class)
public abstract class ExplosionProtectMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void jjk$spareProtected(List<BlockPos> blocks, CallbackInfo ci) {
        if (!ProtectedPlaces.enabled() || blocks.isEmpty()) return;
        try {
            blocks.removeIf(p -> ProtectedPlaces.isProtected(level, p));
        } catch (UnsupportedOperationException ignored) {
            // An immutable list from some other mod: leave it be.
        }
    }
}
