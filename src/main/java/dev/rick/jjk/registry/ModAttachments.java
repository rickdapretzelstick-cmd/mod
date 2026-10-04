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
     * Survival progression: the kits a player has permanently acquired (checked against the world's kit ownership on
     * join), their current Survival kit, and progression flags. Survives death and relogging.
     */
    public static final AttachmentType<dev.rick.jjk.progression.PlayerProgression> PROGRESSION = AttachmentRegistry.create(JJK.id("progression"),
            b -> b.persistent(dev.rick.jjk.progression.PlayerProgression.CODEC).copyOnDeath());
    /**
     * On a curse: who it has turned hostile on (player UUID → game time it last had them as its target), so taking the
     * glasses off doesn't make it forget. Saved with the entity.
     */
    public static final AttachmentType<java.util.Map<String, Long>> CURSE_HOSTILITY = AttachmentRegistry.create(JJK.id("curse_hostility"),
            b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

    /** Mastery: unspent and earned Mastery per tree, nodes bought, the exorcism record. Survives death and relogging. */
    public static final AttachmentType<dev.rick.jjk.progression.mastery.MasteryData> MASTERY = AttachmentRegistry.create(JJK.id("mastery"),
            b -> b.persistent(dev.rick.jjk.progression.mastery.MasteryData.CODEC).copyOnDeath());

    private ModAttachments() {}

    public static void init() {}
}
