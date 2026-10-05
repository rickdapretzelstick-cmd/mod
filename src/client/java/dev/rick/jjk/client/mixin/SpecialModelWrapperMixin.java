package dev.rick.jjk.client.mixin;

import dev.rick.jjk.client.rifle.RifleItemRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the item's holder and display context to special renderers that pose by their holder (the Cursed Rifle). */
@Mixin(SpecialModelWrapper.class)
public class SpecialModelWrapperMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void jjk$owner(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext ctx, ClientLevel level, ItemOwner owner,
                           int seed, CallbackInfo ci) {
        RifleItemRenderer.enter(owner, ctx);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void jjk$done(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext ctx, ClientLevel level, ItemOwner owner,
                          int seed, CallbackInfo ci) {
        RifleItemRenderer.exit();
    }
}
