package dev.rick.jjk.client.mixin.rig;

import dev.rick.jjk.client.anim.rig.MeshSplit;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the player's model (skin, overlays and armour) elbows, wrists, knees, ankles and a waist. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMeshMixin {
    @Inject(method = "createMesh", at = @At("RETURN"))
    private static void jjk$split(CubeDeformation deformation, boolean slim, CallbackInfoReturnable<MeshDefinition> cir) {
        MeshSplit.split(cir.getReturnValue());
    }

    @Inject(method = "createArmorMeshSet", at = @At("RETURN"))
    private static void jjk$splitArmor(CubeDeformation inner, CubeDeformation outer, CallbackInfoReturnable<ArmorModelSet<MeshDefinition>> cir) {
        ArmorModelSet<MeshDefinition> set = cir.getReturnValue();
        MeshSplit.split(set.head());
        MeshSplit.split(set.chest());
        MeshSplit.split(set.legs());
        MeshSplit.split(set.feet());
    }
}
