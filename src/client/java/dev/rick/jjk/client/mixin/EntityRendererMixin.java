package dev.rick.jjk.client.mixin;

import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.anim.PoseKeys;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    /** Computes this frame's combat pose and stashes it on the render state for the model. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void jjk$pose(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity instanceof LivingEntity le) {
            PoseFrame f = ClientAnimations.compute(le, le.level().getGameTime() + partialTicks);
            state.setData(PoseKeys.FRAME, f);
        }
    }
}
