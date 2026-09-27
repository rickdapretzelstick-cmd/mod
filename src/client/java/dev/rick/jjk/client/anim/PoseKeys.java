package dev.rick.jjk.client.anim;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

public final class PoseKeys {
    public static final RenderStateDataKey<PoseFrame> FRAME = RenderStateDataKey.create(() -> "jjk:pose");

    private PoseKeys() {}
}
