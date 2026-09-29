package dev.rick.jjk.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.client.anim.rig.Rig;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    /** The first-person arm is drawn straight, whatever pose the shared model was left in by the last player drawn. */
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void jjk$straightArm(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
        Rig.Parts parts = Rig.parts((PlayerModel) ((AvatarRenderer<?>) (Object) this).getModel());
        Rig.resetSegments(parts);
        Rig.syncOverlays(parts);
    }
}
