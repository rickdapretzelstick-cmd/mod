package dev.rick.jjk.test.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Test-only (never shipped): software X servers like Xvfb offer no sRGB-capable framebuffer, so the game can't open a
 * window for headless client tests. Requesting a plain framebuffer lets the client test run on CI.
 */
@Mixin(GlBackend.class)
public abstract class HeadlessGlBackendMixin {
    @ModifyArg(method = "createWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z", ordinal = 4), index = 1)
    private int jjkTest$noSrgb(int value) {
        return 0;
    }
}
