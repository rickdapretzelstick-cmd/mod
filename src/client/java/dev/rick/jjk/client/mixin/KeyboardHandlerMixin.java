package dev.rick.jjk.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.clash.ClashClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a domain clash the arrow keys and WASD are the four lanes. Presses are taken at the moment the key event
 * arrives (not on the next tick), which is what makes the timing windows meaningful.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void jjk$clashLanes(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (!ClashClient.playing() || Minecraft.getInstance().gui.screen() != null) return;
        int lane = switch (event.key()) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_A -> 0;
            case InputConstants.KEY_DOWN, InputConstants.KEY_S -> 1;
            case InputConstants.KEY_UP, InputConstants.KEY_W -> 2;
            case InputConstants.KEY_RIGHT, InputConstants.KEY_D -> 3;
            default -> -1;
        };
        if (lane < 0) return;
        if (action == 1) ClashClient.press(lane);
        // Lanes never leak into movement while duelling.
        ci.cancel();
    }
}
