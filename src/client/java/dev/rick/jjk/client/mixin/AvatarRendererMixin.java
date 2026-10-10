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
    /**
     * Third person: a drawn cursed tool goes in the main hand (in place of whatever is there), held by its grip profile (two
     * hands brought together on anything two-handed); a holstered one is handed to the gear layer.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void jjk$cursedGear(net.minecraft.world.entity.Avatar entity, net.minecraft.client.renderer.entity.state.AvatarRenderState state, float partialTicks,
                                CallbackInfo ci) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player p)) return;
        net.minecraft.world.item.ItemStack tool = dev.rick.jjk.client.gear.CursedGear.equipped(p);
        if (tool.isEmpty()) {
            state.setData(dev.rick.jjk.client.gear.CursedGear.HOLSTERED, null);
            return;
        }
        var grip = dev.rick.jjk.client.gear.CursedGear.grip(tool);
        state.setData(dev.rick.jjk.client.gear.CursedGear.GRIP, grip);
        var resolver = net.minecraft.client.Minecraft.getInstance().getItemModelResolver();
        net.minecraft.world.entity.HumanoidArm main = p.getMainArm();
        // Drawn, the tool is what the hands hold (whatever sits in the hotbar slot is put away meanwhile).
        boolean drawn = dev.rick.jjk.client.gear.CursedGear.drawn(p);
        if (drawn) {
            var ctx = main == net.minecraft.world.entity.HumanoidArm.RIGHT ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            if (main == net.minecraft.world.entity.HumanoidArm.RIGHT) {
                resolver.updateForLiving(state.rightHandItemState, tool, ctx, p);
                state.rightHandItemStack = tool.copy();
            } else {
                resolver.updateForLiving(state.leftHandItemState, tool, ctx, p);
                state.leftHandItemStack = tool.copy();
            }
            // The rifle's held model is posed for the plain held arm (its own clips raise it); so is everything else.
            var pose = net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
            if (main == net.minecraft.world.entity.HumanoidArm.RIGHT) state.rightArmPose = pose;
            else state.leftArmPose = pose;
            // A two-handed tool leaves nothing in the other hand.
            if (grip.twoHanded) {
                if (main == net.minecraft.world.entity.HumanoidArm.RIGHT) {
                    state.leftHandItemState.clear();
                    state.leftHandItemStack = net.minecraft.world.item.ItemStack.EMPTY;
                } else {
                    state.rightHandItemState.clear();
                    state.rightHandItemStack = net.minecraft.world.item.ItemStack.EMPTY;
                }
            }
            // Both hands on it: the off arm comes in to the haft, the stock or the grip.
            if (grip.twoHanded && grip != dev.rick.jjk.progression.tool.kit.GripProfile.RANGED) {
                if (main == net.minecraft.world.entity.HumanoidArm.RIGHT) state.leftArmPose = pose;
                else state.rightArmPose = pose;
            }
            state.setData(dev.rick.jjk.client.gear.CursedGear.HOLSTERED, null);
        } else {
            net.minecraft.client.renderer.item.ItemStackRenderState held = state.getData(dev.rick.jjk.client.gear.CursedGear.HOLSTERED);
            if (held == null) held = new net.minecraft.client.renderer.item.ItemStackRenderState();
            resolver.updateForLiving(held, tool, net.minecraft.world.item.ItemDisplayContext.FIXED, p);
            state.setData(dev.rick.jjk.client.gear.CursedGear.HOLSTERED, held);
        }
    }

    /** The first-person arm is drawn straight, whatever pose the shared model was left in by the last player drawn. */
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void jjk$straightArm(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
        Rig.Parts parts = Rig.parts((PlayerModel) ((AvatarRenderer<?>) (Object) this).getModel());
        Rig.resetSegments(parts);
        Rig.syncOverlays(parts);
    }
}
