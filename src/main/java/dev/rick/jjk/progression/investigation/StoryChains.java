package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.StoryPayload;
import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.CharacterStory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The village character storylines, built on the investigations: a village may hold one character's storyline (Yuji,
 * Gojo, Yuta, Ryu or Hakari), a short supernatural campaign of four events (discovery, escalation, revelation, finale),
 * each an ordinary incident on its board ({@code <kit>_chain_<n>} templates) with its own place, trigger and realm.
 *
 * <ul>
 *   <li><b>Which storyline</b> is decided once per village, from the village itself and the world seed: never from who
 *   walks in or what they do, and never re-rolled. That makes exploring the selection: the first report of each
 *   storyline is recognisable (a watchtower that never gets closer is Gojo; a theater playing the same film every night
 *   is Yuji...) without ever naming anyone.</li>
 *   <li><b>Its current event</b> is always on the board (beside the village's ordinary news, which it doesn't crowd out)
 *   and never goes stale. Completing it (in its realm, like every cursed event) brings the next report.</li>
 *   <li><b>The finale</b> condenses the storyline's <b>Essence</b> for everyone who took part: presented when they are
 *   back in the world (a logged-out participant gets theirs when they return), never as mob loot.</li>
 * </ul>
 */
public final class StoryChains {
    /** Share of villages that hold a storyline at all (the rest only have ordinary news). */
    static final double STORY_CHANCE = 0.6;
    /** A storyline's stage when it is over. */
    public static final int DONE = 5;

    private StoryChains() {}

    /** Whether an incident is a storyline's event. */
    public static boolean isChain(Incident in) {
        return CharacterStories.ofTemplate(in.template) != null;
    }

    /** Decides (once) which storyline a village holds, from the village and the world alone. */
    static void decide(ServerLevel level, InvestigationState.Village v, InvestigationState st) {
        if (v.storyDecided) return;
        v.storyDecided = true;
        String kit = storyFor(level.getSeed(), v.bell.asLong());
        v.story = kit;
        v.storyStage = kit.isEmpty() ? 0 : 1;
        st.markDirty();
        if (!kit.isEmpty()) JJK.LOGGER.info("[storylines] the village at {} holds the {} storyline", v.bell.toShortString(), kit);
    }

    /** The storyline (a kit id, or "") for a village bell in a world: a fixed function of the two. */
    public static String storyFor(long seed, long bell) {
        long h = (bell * 0x9E3779B97F4A7C15L) ^ (seed * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        double roll = (h >>> 11) * 0x1.0p-53;
        if (roll >= STORY_CHANCE) return "";
        List<CharacterStory> all = CharacterStories.all();
        return all.get((int) Math.floorMod(h >>> 3, (long) all.size())).kit();
    }

    /** Keeps the storyline's current event on the village's board (re-reported if an earlier report of it was lost). */
    static void stock(ServerLevel level, InvestigationState.Village v, InvestigationState st, long now) {
        CharacterStory story = v.story.isEmpty() ? null : CharacterStories.byKit(v.story);
        if (story == null || v.storyStage < 1 || v.storyStage > story.chain().size()) return;
        String template = story.chain().get(v.storyStage - 1);
        for (String id : v.incidents) {
            Incident i = st.incidents.get(id);
            if (i != null && i.template.equals(template) && (i.state == Incident.State.OPEN || i.state == Incident.State.ACTIVE)) return;
        }
        Incident made = Investigations.generate(level, v, st, now, template);
        if (made != null) JJK.LOGGER.info("[storylines] {} event {} reported near {}: {}", story.kit(), v.storyStage, v.bell.toShortString(), made.headline);
    }

    /** One of a storyline's events is over: the village moves on to the next, and the finale condenses the Essence. */
    static void completed(MinecraftServer server, Incident in, InvestigationState st) {
        CharacterStory story = CharacterStories.ofTemplate(in.template);
        if (story == null) return;
        InvestigationState.Village v = st.villages.get(in.village.asLong());
        int stage = story.stageOf(in.template);
        if (v != null && v.storyStage == stage) {
            v.storyStage = stage + 1;
            st.markDirty();
        }
        if (stage < story.chain().size()) {
            for (java.util.UUID id : in.participants) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) p.sendSystemMessage(Component.literal("It isn't over. The village will have more to say about this.")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            return;
        }
        // The finale: everyone who took part is owed the Essence (delivered once they are back in the world).
        in.rewardsPending.addAll(in.participants);
        st.markDirty();
        JJK.LOGGER.info("[storylines] the {} storyline at {} is complete: Essence owed to {}", story.kit(),
                v == null ? "?" : v.bell.toShortString(), in.participants);
    }

    /** Hands a player any Essence they are owed, once they are out of the realm. */
    public static void deliver(ServerPlayer p, InvestigationState st) {
        if (CursedRealms.inRealm(p) || CursedRealms.pulling(p) || !p.isAlive()) return;
        for (Incident in : st.incidents.values()) {
            if (in.state != Incident.State.COMPLETE || !in.rewardsPending.contains(p.getUUID())) continue;
            CharacterStory story = CharacterStories.ofTemplate(in.template);
            if (story == null || story.stageOf(in.template) != story.chain().size()) continue;
            in.rewardsPending.remove(p.getUUID());
            in.rewardsClaimed.add(p.getUUID());
            st.markDirty();
            present(p, story);
        }
    }

    /** Something condenses out of what happened: the Essence forms in front of them, and is theirs. */
    public static void present(ServerPlayer p, CharacterStory story) {
        ServerLevel level = (ServerLevel) p.level();
        ItemStack essence = new ItemStack(story.essence().get());
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(1.2));
        Fx.play(level, "prog_infuse_done", at, Vec3.ZERO, 1.5f, p.getId());
        Fx.play(level, "curse_realm_pull", at, Vec3.ZERO, 0.8f, -1);
        Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f);
        Fx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.3f);
        String title = essence.getHoverName().getString().toUpperCase(java.util.Locale.ROOT);
        ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.CARD, title, "Something condenses out of what happened there.", story.color(), 90));
        p.sendSystemMessage(Component.literal("Something condenses out of what happened there, and settles in your hands.")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        if (!p.getInventory().add(essence)) {
            ItemEntity dropped = p.drop(essence, false, net.minecraft.util.Prediction.SERVER_ONLY);
            if (dropped != null) dropped.setUnlimitedLifetime();
        }
    }

    /** The village record for an incident (admin output). */
    @Nullable
    public static InvestigationState.Village villageOf(InvestigationState st, Incident in) {
        return st.villages.get(in.village.asLong());
    }

    /** Admin/test: sets a village's storyline and stage directly. */
    public static void setStory(InvestigationState st, InvestigationState.Village v, String kit, int stage) {
        v.storyDecided = true;
        v.story = kit;
        v.storyStage = kit.isEmpty() ? 0 : stage;
        st.markDirty();
    }
}
