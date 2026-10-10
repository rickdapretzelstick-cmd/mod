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
    /** The Cursed Rifle's own reserve of cursed energy (it never needs a technique): kept per player, not on the item. */
    public static final AttachmentType<Float> RIFLE_ENERGY = AttachmentRegistry.create(JJK.id("rifle_energy"),
            b -> b.persistent(com.mojang.serialization.Codec.FLOAT).copyOnDeath());
    /** Which issued rifle is a player's live one (the lodge's recovery route replaces it, never adds a second). */
    public static final AttachmentType<String> RIFLE_CLAIM = AttachmentRegistry.create(JJK.id("rifle_claim"),
            b -> b.persistent(com.mojang.serialization.Codec.STRING).copyOnDeath());
    public static final AttachmentType<dev.rick.jjk.progression.mastery.MasteryData> MASTERY = AttachmentRegistry.create(JJK.id("mastery"),
            b -> b.persistent(dev.rick.jjk.progression.mastery.MasteryData.CODEC).copyOnDeath());

    /**
     * The Cursed Item slot: the cursed tool a player has equipped (its moveset is theirs while it is there). Saved with
     * the player, and synced to everyone (others see the weapon drawn). Dropped on death like the inventory, unless
     * keepInventory (then it carries over).
     */
    public static final AttachmentType<net.minecraft.world.item.ItemStack> CURSED_ITEM = AttachmentRegistry.create(JJK.id("cursed_item"),
            b -> b.persistent(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC)
                    .syncWith(net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
    /** With a technique and a cursed tool: whether the tool's moveset is the one in use (saved; synced for drawing it). */
    public static final AttachmentType<Boolean> TOOL_MOVESET = AttachmentRegistry.create(JJK.id("tool_moveset"),
            b -> b.persistent(Codec.BOOL).copyOnDeath()
                    .syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
    /** Whether a player's cursed tool is drawn (its moveset in use), for everyone's view of them. Not saved: recomputed. */
    public static final AttachmentType<Boolean> TOOL_DRAWN = AttachmentRegistry.create(JJK.id("tool_drawn"),
            b -> b.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
    /** The incident a player is investigating (chosen at a news board): what the Cursed Compass follows. */
    public static final AttachmentType<String> INVESTIGATING = AttachmentRegistry.create(JJK.id("investigating"),
            b -> b.persistent(Codec.STRING).copyOnDeath());

    private ModAttachments() {}

    public static void init() {}
}
