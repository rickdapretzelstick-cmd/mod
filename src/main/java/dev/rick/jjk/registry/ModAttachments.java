package dev.rick.jjk.registry;

import com.mojang.serialization.Codec;
import dev.rick.jjk.JJK;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
    /** Which character a player is playing; survives death and relogging. */
    public static final AttachmentType<String> CHARACTER = AttachmentRegistry.create(JJK.id("character"),
            b -> b.persistent(Codec.STRING).copyOnDeath());
    /**
     * Survival progression: the kits a player has permanently acquired, their current Survival kit and progression
     * counters ({@link dev.rick.jjk.progression.PlayerProgression#encode}). Checked against the world's kit ownership on
     * join; survives death and relogging.
     */
    public static final AttachmentType<String> PROGRESSION = AttachmentRegistry.create(JJK.id("progression"),
            b -> b.persistent(Codec.STRING).copyOnDeath());

    private ModAttachments() {}

    public static void init() {}
}
