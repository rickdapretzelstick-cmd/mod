package dev.rick.jjk.test.mixin;

import dev.rick.jjk.test.ShowcaseCamera;
import dev.rick.jjk.test.SoundLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/** While a recording runs, logs each sound the game asks to play (headless, the sound engine itself never starts). */
@Mixin(SoundManager.class)
public abstract class SoundLogMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"))
    private void jjk$log(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        if (!SoundLog.recording) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float volume = 1f, pitch = 1f;
        try {
            volume = sound.getVolume();
            pitch = sound.getPitch();
        } catch (RuntimeException ignored) {
            // (not resolved yet: its file's own volume isn't known; 1)
        }
        double t = mc.level.getGameTime() + Math.max(0, ShowcaseCamera.pinnedPartial);
        SoundLog.add(String.format(Locale.ROOT, "%.3f %s %.2f %.2f %.2f %b %.3f %.3f", t, sound.getIdentifier(), sound.getX(), sound.getY(), sound.getZ(),
                sound.isRelative(), volume, pitch));
    }
}
