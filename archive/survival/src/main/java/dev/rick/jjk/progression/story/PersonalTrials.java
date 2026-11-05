package dev.rick.jjk.progression.story;

import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.training.TrainingSessions;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.StoryPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The personal trial step: a character's infused relic, used the way that character would (the VHS played on a
 * jukebox, the Blindfold worn, the Ring, the Comb, the Scratch-Off), offers that character's training
 * ({@link TrainingSessions}). Its first success claims the base kit ({@link RelicAcquisition}); once the kit is yours the
 * relic offers "Practice Again", which grants nothing. A failure never costs the relic, its ownership or its place in
 * the world.
 *
 * <p>Who may train: the relic must be the world's live one (or a Creative test relic, which only ever echoes), its kit
 * unclaimed or claimed by you, and nobody else in a claiming session of the same character.
 */
public final class PersonalTrials {
    /** Ticks the Infused Blindfold must stay on before it offers its training. */
    static final int WEAR_TICKS = 100;

    private static final Map<UUID, Integer> WORN = new HashMap<>();
    /** Wearers who were already offered training this time the blindfold went on (offered once per wearing). */
    private static final Set<UUID> OFFERED = new HashSet<>();

    private PersonalTrials() {}

    public static void init() {
        TrainingSessions.init();
        ServerTickEvents.END_SERVER_TICK.register(PersonalTrials::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            WORN.clear();
            OFFERED.clear();
            UniqueRelics.unload();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            WORN.remove(handler.player.getUUID());
            OFFERED.remove(handler.player.getUUID());
        });
        // A one-of-a-kind relic never despawns on the ground.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().getItem() instanceof RelicItem) item.setUnlimitedLifetime();
        });
    }

    // --- Queries ---

    public static boolean inTrial(ServerPlayer p) {
        return TrainingSessions.inTraining(p);
    }

    public static List<String> describe() {
        return TrainingSessions.describe();
    }

    /** Who may use a relic for its training, and how. */
    record Eligibility(@Nullable String why, boolean practice, boolean test) {}

    static Eligibility eligibility(ServerPlayer p, ItemStack stack, CharacterStory story) {
        if (CursedRealms.inRealm(p) || CursedRealms.pulling(p) || TrainingSessions.inTraining(p) || dev.rick.jjk.progression.moment.Moments.active(p)) {
            return new Eligibility("", false, false);
        }
        boolean test = isTestUse(p, stack);
        if (!test && !UniqueRelics.get(p.level().getServer()).isLive(story.relicKey(), RelicItem.token(stack))) {
            return new Eligibility("It is cold and silent. Whatever lived in it has gone.", false, false);
        }
        KitOwnership.Owner owner = KitOwnership.get(p.level().getServer()).owner(story.kit());
        boolean practice = owner != null && owner.uuid().equals(p.getUUID());
        if (!test && owner != null && !practice) return new Eligibility("Whatever lived in it has already chosen someone else.", false, false);
        if (!test && !practice && TrainingSessions.claimingSessionOf(story)) return new Eligibility("Someone else is already inside its story.", false, false);
        return new Eligibility(null, practice, test);
    }

    /** Why this relic can't offer its training to this player now (null if it can; "" if they are busy). Tests and admin. */
    @Nullable
    public static String whyBlocked(ServerPlayer p, ItemStack stack) {
        if (!(stack.getItem() instanceof RelicItem relic)) return "not a relic";
        CharacterStory story = CharacterStories.byKit(relic.kit());
        return story == null ? "no storyline" : eligibility(p, stack, story).why();
    }

    /** A relic without the world's token: a Creative test relic (or any relic, used in Creative). */
    static boolean isTestUse(ServerPlayer p, ItemStack stack) {
        return RelicItem.isTest(stack) || (TechniqueProgression.isSandbox(p) && RelicItem.token(stack) == null);
    }

    /** The relic this player carries for {@code story} (hands, head, then the rest of the inventory), or empty. */
    static ItemStack carried(ServerPlayer p, CharacterStory story) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD}) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.getItem() == story.relic().get()) return s;
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == story.relic().get()) return s;
        }
        return ItemStack.EMPTY;
    }

    /**
     * When an offer is accepted: is it still valid (the relic still carried, still live, still theirs to use, in the
     * same mode)? Null if so.
     */
    @Nullable
    public static String recheck(ServerPlayer p, CharacterStory story, boolean practice, boolean test) {
        ItemStack stack = carried(p, story);
        if (stack.isEmpty()) return "You aren't carrying it any more.";
        Eligibility e = eligibility(p, stack, story);
        if (e.why() != null) return e.why();
        if (e.practice() != practice || e.test() != test) return "Something about it has changed. Use it again.";
        if (!test && !practice) {
            java.util.UUID token = RelicItem.token(stack);
            if (token != null) UniqueRelics.get(p.level().getServer()).keep(story.relicKey(), token, p.getUUID(), p.getName().getString());
        }
        return null;
    }

    // --- Using a relic ---

    /** A relic used (the item's use, or the VHS on a block). True if it offered its training. */
    static boolean use(ServerPlayer p, ItemStack stack, @Nullable BlockPos on) {
        if (!(stack.getItem() instanceof RelicItem relic)) return false;
        CharacterStory story = CharacterStories.byKit(relic.kit());
        if (story == null || TrainingSessions.inTraining(p)) return false;
        if (story.activation() == CharacterStory.Activation.PLAY && (on == null || !p.level().getBlockState(on).is(Blocks.JUKEBOX))) return false;
        return offer(p, stack, story);
    }

    private static boolean offer(ServerPlayer p, ItemStack stack, CharacterStory story) {
        Eligibility e = eligibility(p, stack, story);
        if (e.why() != null) {
            if (!e.why().isEmpty()) p.sendOverlayMessage(Component.literal(e.why()).withStyle(ChatFormatting.GRAY));
            return false;
        }
        Fx.sound((ServerLevel) p.level(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.8f);
        TrainingSessions.offer(p, story, e.practice(), e.test(), line(story, e.practice()));
        return true;
    }

    private static String line(CharacterStory story, boolean practice) {
        if (practice) return "It still answers you. Train again; it will teach you nothing new.";
        return switch (story.kit()) {
            case "yuji" -> "The tape whirs. Something on the screen is waiting for you to stay calm.";
            case "gojo" -> "Behind the cloth, everything is very clear. Can you hold it still?";
            case "yuta" -> "The ring is warm. Someone very close says your name.";
            case "ryu" -> "Are you satisfied? Show it what you can hold back.";
            case "hakari" -> "7 · 7 · ... the last panel won't stop moving.";
            default -> "It wants to teach you something.";
        };
    }

    // --- The Blindfold: worn, it shows cursed energy; kept on, it offers its training ---

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) wearing(p);
        }
    }

    private static void wearing(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        boolean on = head.is(ProgressionItems.INFUSED_BLINDFOLD);
        // Its owner has already learned to see without it: no vision for them, only the chance to practise.
        boolean owner = KitOwnership.get(p.level().getServer()).isOwner(CharacterStories.GOJO.kit(), p.getUUID());
        Integer worn = WORN.get(p.getUUID());
        boolean training = TrainingSessions.inTraining(p);
        if (!on) {
            if (worn != null) {
                WORN.remove(p.getUUID());
                OFFERED.remove(p.getUUID());
                if (!training) ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.BLIND, "", "", 0, 0));
            }
            return;
        }
        if (training) return;
        if (worn == null) {
            if (!owner) {
                ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.BLIND, "", "", 0, 1));
                p.sendOverlayMessage(Component.literal("Everything goes dark. Then, slowly, you begin to see.").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
            }
            worn = 0;
        }
        worn += 5;
        WORN.put(p.getUUID(), worn);
        if (worn >= WEAR_TICKS && OFFERED.add(p.getUUID())) {
            if (offer(p, head, CharacterStories.GOJO) && !owner) {
                // The vision lifts while they decide (and while they train).
                ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.BLIND, "", "", 0, 0));
            }
        }
    }

    // --- The realm's hooks for a trial arena ---

    /** The curses a trial arena counts (for the encounter watch): training raises none. */
    public static List<UUID> curses(String key) {
        return List.of();
    }

    /** Called by the realm every tick for a trial arena: 0 running, -1 close it. */
    public static int tickArena(MinecraftServer server, dev.rick.jjk.progression.investigation.InvestigationState.Arena a, String key, ServerLevel realm) {
        // A room opened just to look round (the test menu): it stays until it's left or abandoned.
        if (key.startsWith(VIEW)) return 0;
        return TrainingSessions.tickArena(key);
    }

    /** Rooms opened only to walk round ({@code /jjk test room}): no trial runs in them. */
    public static final String VIEW = "view_";

    /** The arena is gone (finished, abandoned, a restart). */
    public static void arenaClosed(MinecraftServer server, String key, boolean won) {
        TrainingSessions.arenaClosed(server, key);
    }

    // --- Admin and tests ---

    /** Starts a character's training now, skipping the offer (a test session claims nothing unless in Creative). */
    public static void startNow(ServerPlayer p, CharacterStory story, boolean test) {
        boolean practice = !test && KitOwnership.get(p.level().getServer()).isOwner(story.kit(), p.getUUID());
        TrainingSessions.begin(p, story, practice, test);
    }

    /** Completes the running training attempt now (as a success). */
    public static boolean completeNow(ServerPlayer p) {
        return TrainingSessions.completeNow(p);
    }
}
