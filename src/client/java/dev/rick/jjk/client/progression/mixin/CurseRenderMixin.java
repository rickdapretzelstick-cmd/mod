package dev.rick.jjk.client.progression.mixin;

import dev.rick.jjk.client.ClientProgression;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Curses that need perception aren't drawn for a player who can't perceive them (per player: the server tells each
 * client whether it can). The entity is still there, for everyone, on the server; only this player's view of it goes.
 * Renderers that override shouldRender without calling super (the mod's own curses) ask
 * {@link ClientProgression#canSee} themselves.
 */
@Mixin(EntityRenderer.class)
public abstract class CurseRenderMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void jjk$hideUnperceivedCurse(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partial,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!ClientProgression.canSee(entity)) cir.setReturnValue(false);
    }
}
