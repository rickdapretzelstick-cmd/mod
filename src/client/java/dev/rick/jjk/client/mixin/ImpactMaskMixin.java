package dev.rick.jjk.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Impact frames ink figures, not the ground ({@link dev.rick.jjk.client.fx.ImpactFrames}): for those few frames every
 * body near the camera is drawn into the game's silhouette buffer (as if glowing), and the level's outline pass draws
 * the inked frame from it instead of the glowing outlines.
 */
public final class ImpactMaskMixin {
    private ImpactMaskMixin() {}

    @Mixin(Minecraft.class)
    public abstract static class Figures {
        @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
        private void jjk$inkFigures(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (!(entity instanceof LivingEntity) || entity.isInvisible() || !dev.rick.jjk.client.fx.ImpactFrames.inking()) return;
            var cam = ((Minecraft) (Object) this).gameRenderer.mainCamera().position();
            double r = dev.rick.jjk.client.fx.ImpactFrames.FIGURE_RANGE;
            if (entity.position().distanceToSqr(cam) < r * r) cir.setReturnValue(true);
        }
    }

    @Mixin(LevelRenderer.class)
    public abstract static class Outline {
        @ModifyArg(method = "render", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"),
                index = 0)
        private Identifier jjk$inkChain(Identifier id) {
            Identifier ink = dev.rick.jjk.client.fx.ImpactFrames.maskChain();
            return ink != null ? ink : id;
        }
    }
}
