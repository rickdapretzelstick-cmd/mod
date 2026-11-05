package dev.rick.jjk.client.training;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Music during a relic trial: each character's own JJS track, from the attempt's start until the trial ends (it comes
 * round again if the trial outlasts it). Vanilla's background music steps aside meanwhile. Off with {@code client.trialMusic}.
 */
public final class TrialMusic {
    /** The track per trial: Gojo's Unlimited Void, Yuji's "Entrusted", Yuta's Authentic Mutual Love, Ryu's Decadence, Hakari's Idle Death Gamble. */
    static final Map<String, String> TRACKS = Map.of("gojo", "uv_music", "yuji", "entrusted_music", "yuta", "aml_music", "ryu", "ryu_music",
            "hakari", "idg_music");
    private static final float VOLUME = 0.55f;

    @Nullable private static String track;
    @Nullable private static EntityBoundSoundInstance playing;

    private TrialMusic() {}

    /** An attempt began: its trial's track (kept playing across retries of the same trial). */
    static void start(String kit) {
        String t = TRACKS.get(kit);
        if (t == null || !JJKConfig.get().client.trialMusic) {
            stop();
            return;
        }
        if (t.equals(track) && playing != null && Minecraft.getInstance().getSoundManager().isActive(playing)) return;
        stop();
        track = t;
        play();
    }

    /** The trial is over (passed, failed, left, or lost): the music stops. */
    static void stop() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
        track = null;
    }

    /** Each client tick: round again when the track ends while the trial goes on. */
    public static void tick() {
        if (track == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !TrainingClient.active()) {
            stop();
            return;
        }
        mc.getMusicManager().stopPlaying();
        if (playing == null || !mc.getSoundManager().isActive(playing)) play();
    }

    private static void play() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || track == null) return;
        float v = VOLUME * JJKConfig.get().client.soundVolume;
        if (v <= 0 || mc.getSoundManager().getSoundEvent(ModSounds.get(track).location()) == null) return;
        playing = new EntityBoundSoundInstance(ModSounds.get(track), SoundSource.RECORDS, v, 1f, mc.player, mc.player.getRandom().nextLong());
        mc.getSoundManager().play(playing);
    }

    /** Tests: the track playing now (null if none). */
    @Nullable
    public static String track() {
        return track;
    }
}
