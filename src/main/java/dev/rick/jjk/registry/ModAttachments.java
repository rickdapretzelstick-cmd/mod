package dev.rick.jjk.registry;

import com.mojang.serialization.Codec;
import dev.rick.jjk.JJK;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
    /** Which character a player is playing; survives death and relogging. */
    public static final AttachmentType<String> CHARACTER = AttachmentRegistry.create(JJK.id("character"),
            b -> b.persistent(Codec.STRING).copyOnDeath());

    private ModAttachments() {}

    public static void init() {}
}
