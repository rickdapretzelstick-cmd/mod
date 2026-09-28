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
        // Inside Idle Death Gamble nothing casts a shadow: nothing shows where the floor is.
        if (!state.shadowPieces.isEmpty() && dev.rick.jjk.client.ClientState.inWhiteRoom(entity.position())) state.shadowPieces.clear();
        if (entity instanceof LivingEntity le) {
            float now = le.level().getGameTime() + partialTicks;
            PoseFrame f = ClientAnimations.compute(le, now);
            state.setData(PoseKeys.FRAME, f);
            int visual = 0;
            if (dev.rick.jjk.core.combat.Combat.has(le, dev.rick.jjk.core.combat.CombatStatus.BLINDFOLD)) visual |= PoseKeys.BLINDFOLD;
            if (dev.rick.jjk.core.combat.Combat.has(le, dev.rick.jjk.core.combat.CombatStatus.AWAKENED)) visual |= PoseKeys.AWAKENED;
            state.setData(PoseKeys.VISUAL, visual);
            float t = ClientAnimations.elapsed(le.getId(), "awaken", now);
            state.setData(PoseKeys.BLINDFOLD_OFF, t < 0 ? 0f : net.minecraft.util.Mth.clamp((t - 6f) / 8f, 0f, 1f));
        }
    }
}
