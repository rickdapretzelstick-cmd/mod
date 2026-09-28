package dev.rick.jjk.client.anim;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

public final class PoseKeys {
    public static final RenderStateDataKey<PoseFrame> FRAME = RenderStateDataKey.create(() -> "jjk:pose");
    /** Visual state flags (blindfold / awakened). */
    public static final RenderStateDataKey<Integer> VISUAL = RenderStateDataKey.create(() -> "jjk:visual");
    /** 0..1: how far the blindfold has been pulled down (during the awakening animation). */
    public static final RenderStateDataKey<Float> BLINDFOLD_OFF = RenderStateDataKey.create(() -> "jjk:blindfold_off");
    public static final int BLINDFOLD = 1, AWAKENED = 2;

    private PoseKeys() {}
}
