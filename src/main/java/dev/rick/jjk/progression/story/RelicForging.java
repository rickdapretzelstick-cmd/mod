package dev.rick.jjk.progression.story;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.StoryPayload;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.TechniqueProgression;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A character object dropped into a full cauldron of cursed energy (the cauldron's own ritual, {@link
 * dev.rick.jjk.progression.block.CursedCauldronBlock}). When the energy collapses into it, the world is asked, atomically,
 * for that character's one relic ({@link UniqueRelics#forge}): exactly one cauldron in the world can ever win it. A
 * cauldron that loses the race (or finds the character already has its owner) gives the dormant object back.
 *
 * <p>The keeper of a relic that has been lost (it is no longer on them) may forge it again: the new relic retires the old
 * one, so recovery never makes two. In Creative the cauldron makes a test relic: it plays the storyline but is never
 * registered and never claims anything.
 */
public final class RelicForging {
    /** Who offered what is in each cauldron right now (keyed by the cauldron's position). Lost on restart: no keeper. */
    private record Offer(UUID player, String name, boolean sandbox) {}

    private static final Map<Long, Offer> OFFERS = new HashMap<>();

    private RelicForging() {}

    /** Why the energy refuses this character's object now, or null. */
    @Nullable
    public static String refuse(ServerLevel level, CharacterStory story, @Nullable ServerPlayer player) {
        if (player != null && TechniqueProgression.isSandbox(player)) return null;
        KitOwnership.Owner owner = KitOwnership.get(level.getServer()).owner(story.kit());
        if (owner != null && (player == null || !owner.uuid().equals(player.getUUID()))) {
            return "The energy recoils. The one this belonged to already walks the world.";
        }
        UniqueRelics.Relic r = UniqueRelics.get(level.getServer()).relic(story.relicKey());
        if (r == null) return null;
        if (player != null && r.keeper().equals(player.getUUID()) && !carriesLive(player, story)) return null;
        return "The energy recoils. It already has a vessel somewhere in this world.";
    }

    /** It went in: remember who offered it. */
    public static void offered(ServerLevel level, BlockPos cauldron, @Nullable ServerPlayer player) {
        if (player == null) OFFERS.remove(cauldron.asLong());
        else OFFERS.put(cauldron.asLong(), new Offer(player.getUUID(), player.getName().getString(), TechniqueProgression.isSandbox(player)));
    }

    /** The collapse: the world's one relic, or (it lost the race) the dormant object back. */
    public static ItemStack make(ServerLevel level, BlockPos cauldron, CharacterStory story) {
        Offer o = OFFERS.remove(cauldron.asLong());
        Vec3 at = Vec3.atCenterOf(cauldron).add(0, 0.6, 0);
        if (o != null && o.sandbox) {
            erupt(level, at, story, null);
            return RelicItem.createTest(story.relic().get());
        }
        UniqueRelics reg = UniqueRelics.get(level.getServer());
        UUID player = o == null ? new UUID(0, 0) : o.player;
        String name = o == null ? "?" : o.name;
        UUID token = null;
        // The character's owner already exists (claimed meanwhile): nothing to forge.
        KitOwnership.Owner owner = KitOwnership.get(level.getServer()).owner(story.kit());
        if (owner == null || owner.uuid().equals(player)) {
            token = reg.forge(story.relicKey(), player, name);
            if (token == null && o != null) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(o.player);
                // A keeper re-forging a relic they lost: the old one goes cold.
                if (p != null && !carriesLive(p, story)) token = reg.reforge(story.relicKey(), o.player, name);
            }
        }
        if (token == null) {
            Fx.sound(level, at, SoundEvents.SHULKER_HURT_CLOSED, 1f, 0.5f);
            Fx.play(level, "curse_unharmed", at, Vec3.ZERO, 1.2f, -1);
            for (ServerPlayer p : level.players()) {
                if (p.position().distanceToSqr(at) < 16 * 16) {
                    p.sendOverlayMessage(Component.literal("The energy gutters out. Something like it already exists.").withStyle(ChatFormatting.GRAY));
                }
            }
            return new ItemStack(story.object().get());
        }
        erupt(level, at, story, o == null ? null : level.getServer().getPlayerList().getPlayer(o.player));
        return RelicItem.create(story.relic().get(), token);
    }

    /** Cursed energy erupts out of the cauldron: a column, a shockwave, the object's name on the forger's screen. */
    private static void erupt(ServerLevel level, Vec3 at, CharacterStory story, @Nullable ServerPlayer forger) {
        Fx.play(level, "prog_cauldron_full", at, Vec3.ZERO, 2f, -1);
        Fx.play(level, "curse_realm_pull", at.add(0, 1, 0), Vec3.ZERO, 2f, -1);
        Fx.shake(level, at, 18, 0.6f, 14);
        Fx.flash(level, at, 10, 0x80000000 | (story.color() & 0xFFFFFF), 8);
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 0.5f);
        Fx.sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.6f);
        if (forger != null) {
            String title = new ItemStack(story.relic().get()).getHoverName().getString().toUpperCase(java.util.Locale.ROOT);
            ServerPlayNetworking.send(forger, new StoryPayload(StoryPayload.CARD, title, "It is the only one of its kind.", story.color(), 70));
        }
    }

    /** Whether the player carries (inventory or ender chest) the live relic of this storyline. */
    public static boolean carriesLive(ServerPlayer p, CharacterStory story) {
        UniqueRelics reg = UniqueRelics.get(p.level().getServer());
        for (net.minecraft.world.Container c : new net.minecraft.world.Container[] {p.getInventory(), p.getEnderChestInventory()}) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.is(story.relic().get()) && reg.isLive(story.relicKey(), RelicItem.token(s))) return true;
            }
        }
        return false;
    }
}
