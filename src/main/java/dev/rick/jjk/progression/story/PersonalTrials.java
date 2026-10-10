package dev.rick.jjk.progression.story;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.StoryPayload;
import dev.rick.jjk.progression.CursePerception;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.investigation.CurseKinds;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.InvestigationState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Act II: the personal storylines. A character's infused relic, used the way that character would (the VHS played, the
 * Blindfold worn, the Ring, the Comb, the Scratch-Off), opens a trial that is that character's own: a short scripted
 * fight in a cursed realm ({@link CursedRealms#enterTrial}), with its own rules, presentation and solution.
 *
 * <ul>
 *   <li><b>Yuji</b> (the VHS on a jukebox): an ordinary film, until something in it notices you. In the theater no weapon
 *   touches what comes out of the seats: only your body does (bare fists strike with cursed energy, and hard).</li>
 *   <li><b>Gojo</b> (the Blindfold, worn): everything goes dark, and only cursed energy shows. Fight by perception on a
 *   road that folds back on itself, then the blindfold comes off: everything at once (the Six Eyes).</li>
 *   <li><b>Yuta</b> (the Ring): a looming presence that seems to hunt you, until you see it only ever strikes what comes
 *   for you. Stay close, let it protect you, protect it back; then accept the bond (use the Ring). Never by killing it:
 *   it can't be hurt.</li>
 *   <li><b>Ryu</b> (the Comb): "Are you satisfied?" Wave after harder wave; after each, choose MORE (the gold pillar) or
 *   SATISFIED (the grey one, which ends it in failure). The last wave is answered with enormous output.</li>
 *   <li><b>Hakari</b> (the Scratch-Off): scratch, lose, scratch, lose, and things get strange; then the fight club. Each
 *   round draws a wager against you (health, cursed energy, healing, your grip, your legs). Then JACKPOT: a fever.</li>
 * </ul>
 *
 * Completing a trial with the world's live relic claims the base kit ({@link RelicAcquisition}); a Creative player tries
 * the kit out without claiming it; a test relic in Survival is only an echo. Dying, leaving or a restart fails the trial
 * (the arena is cleared) and the relic waits to be used again. The server decides everything.
 */
public final class PersonalTrials {
    /** Ticks a player may be missing from their trial's arena (dead, logged out) before it fails. */
    static final int GONE_TICKS = 60;
    /** Ticks the Infused Blindfold must stay on before the trial takes the wearer. */
    static final int WEAR_TICKS = 100;

    private static final Map<String, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Prelude> PRELUDES = new HashMap<>();
    private static final Map<UUID, Integer> WORN = new HashMap<>();
    private static final Map<UUID, Integer> SCRATCHES = new HashMap<>();

    private PersonalTrials() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PersonalTrials::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            RUNS.clear();
            PRELUDES.clear();
            WORN.clear();
            SCRATCHES.clear();
            UniqueRelics.unload();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PRELUDES.remove(handler.player.getUUID());
            WORN.remove(handler.player.getUUID());
        });
        // A one-of-a-kind relic never despawns on the ground.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().getItem() instanceof RelicItem) item.setUnlimitedLifetime();
        });
        // Everyone inside a trial perceives the curses in it (the trial is theirs to see).
        CursePerception.register(p -> p instanceof ServerPlayer sp && inTrial(sp));
        // A trial's own rules about what may hurt its curses (Yuji's: only the body).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof CommonCurseEntity c) || !(source.getEntity() instanceof ServerPlayer p)) return true;
            Run run = runOf(p);
            return run == null || !run.curses.contains(c.getUUID()) || run.allowHit(p, source);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!(entity instanceof CommonCurseEntity c) || !(source.getEntity() instanceof ServerPlayer p) || taken <= 0) return;
            Run run = runOf(p);
            if (run != null && run.curses.contains(c.getUUID())) run.landed(p, c);
        });
    }

    // --- Queries ---

    @Nullable
    static Run runOf(ServerPlayer p) {
        for (Run r : RUNS.values()) if (r.player.equals(p.getUUID())) return r;
        return null;
    }

    public static boolean inTrial(ServerPlayer p) {
        return runOf(p) != null;
    }

    /** Whether this player's bare fists strike with cursed energy right now (a trial gave them that). */
    public static boolean cursedFists(ServerPlayer p) {
        Run r = runOf(p);
        return r != null && r.cursedFists();
    }

    /** Admin readout. */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Run r : RUNS.values()) out.add(r.key + " phase " + r.phase + " (" + r.age + " ticks)" + (r.test ? " [test]" : ""));
        for (Map.Entry<UUID, Prelude> e : PRELUDES.entrySet()) out.add("prelude " + e.getValue().story.kit() + " for " + e.getKey());
        return out;
    }

    // --- Using a relic ---

    /** Why this relic can't open its trial for this player now, or null if it can. */
    @Nullable
    static String blocked(ServerPlayer p, ItemStack stack, CharacterStory story) {
        if (CursedRealms.inRealm(p) || CursedRealms.pulling(p) || inTrial(p) || PRELUDES.containsKey(p.getUUID())) return "";
        boolean test = isTestUse(p, stack);
        if (!test && !UniqueRelics.get(p.level().getServer()).isLive(story.relicKey(), RelicItem.token(stack))) {
            return "It is cold and silent. Whatever lived in it has gone.";
        }
        KitOwnership.Owner owner = KitOwnership.get(p.level().getServer()).owner(story.kit());
        if (!test && owner != null) {
            return owner.uuid().equals(p.getUUID()) ? "It has nothing more to show you. Not yet." : "Whatever lived in it has already chosen someone else.";
        }
        for (Run r : RUNS.values()) {
            if (r.story == story && !r.test && !test) return "Someone else is already inside its story.";
        }
        return null;
    }

    /** Why this relic can't open its trial for this player now (null if it can; "" if they are busy). Tests and admin. */
    @Nullable
    public static String whyBlocked(ServerPlayer p, ItemStack stack) {
        if (!(stack.getItem() instanceof RelicItem relic)) return "not a relic";
        CharacterStory story = CharacterStories.byKit(relic.kit());
        return story == null ? "no storyline" : blocked(p, stack, story);
    }

    /** A relic without the world's token: a Creative test relic (or any relic, used in Creative). */
    static boolean isTestUse(ServerPlayer p, ItemStack stack) {
        return RelicItem.isTest(stack) || (TechniqueProgression.isSandbox(p) && RelicItem.token(stack) == null);
    }

    /**
     * A relic used (the item's use, or the VHS on a block). True if something happened: the trial's prelude began, or the
     * Scratch-Off was scratched, or (inside Yuta's trial) the Ring was offered.
     */
    static boolean use(ServerPlayer p, ItemStack stack, @Nullable BlockPos on) {
        if (!(stack.getItem() instanceof RelicItem relic)) return false;
        CharacterStory story = CharacterStories.byKit(relic.kit());
        if (story == null) return false;
        Run run = runOf(p);
        if (run != null) return run.used(p, stack);
        if (story.activation() == CharacterStory.Activation.PLAY && (on == null || !p.level().getBlockState(on).is(Blocks.JUKEBOX))) return false;
        String why = blocked(p, stack, story);
        if (why != null) {
            if (!why.isEmpty()) p.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
            return false;
        }
        begin(p, stack, story);
        return true;
    }

    /** Starts the prelude in the world (the film, the scratching, the question) that ends in the pull. */
    static void begin(ServerPlayer p, ItemStack stack, CharacterStory story) {
        boolean test = isTestUse(p, stack);
        UUID token = RelicItem.token(stack);
        if (!test && token != null) UniqueRelics.get(p.level().getServer()).keep(story.relicKey(), token, p.getUUID(), p.getName().getString());
        ServerLevel level = (ServerLevel) p.level();
        Vec3 at = p.position().add(0, 1, 0);
        int ticks;
        switch (story.kit()) {
            case "yuji" -> {
                ticks = 130;
                film(p, "An ordinary film. A family at dinner, a man reading a paper.", 130);
                Fx.sound(level, at, SoundEvents.NOTE_BLOCK_HARP, 0.8f, 0.6f);
            }
            case "hakari" -> {
                int n = SCRATCHES.merge(p.getUUID(), 1, Integer::sum);
                Fx.sound(level, at, SoundEvents.BOOK_PAGE_TURN, 1f, 1.6f);
                if (n == 1) {
                    p.sendOverlayMessage(Component.literal("You scratch the panels: 3 · 8 · 1. No win.").withStyle(ChatFormatting.GOLD));
                    return;
                }
                if (n == 2) {
                    p.sendOverlayMessage(Component.literal("7 · 7 · 2. No win. ...weren't those different a moment ago?").withStyle(ChatFormatting.GOLD));
                    Fx.sound(level, at, SoundEvents.BELL_BLOCK, 0.7f, 1.4f);
                    return;
                }
                SCRATCHES.remove(p.getUUID());
                ticks = 60;
                Fx.sound(level, at, SoundEvents.BELL_BLOCK, 1.2f, 1.0f);
                Fx.sound(level, at, SoundEvents.BELL_RESONATE, 1f, 1.0f);
                card(p, "PLACE YOUR BETS", "The numbers won't stop moving.", story.color(), 60);
            }
            case "ryu" -> {
                ticks = 60;
                Fx.sound(level, at, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1f, 0.6f);
                card(p, "ARE YOU SATISFIED?", "", story.color(), 60);
            }
            case "yuta" -> {
                ticks = 70;
                Fx.sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.8f);
                p.sendOverlayMessage(Component.literal("The ring is warm. Someone very close says your name.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
            default -> ticks = 30; // Gojo: the blindfold has already been on for a while.
        }
        PRELUDES.put(p.getUUID(), new Prelude(story, test, ticks));
    }

    /** The prelude before the pull. */
    private static final class Prelude {
        final CharacterStory story;
        final boolean test;
        final int length;
        int age;

        Prelude(CharacterStory story, boolean test, int length) {
            this.story = story;
            this.test = test;
            this.length = length;
        }
    }

    // --- Ticking: preludes, the blindfold, then the pull ---

    private static void tick(MinecraftServer server) {
        if (!PRELUDES.isEmpty()) {
            for (Map.Entry<UUID, Prelude> e : List.copyOf(PRELUDES.entrySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                Prelude pr = e.getValue();
                if (p == null || !p.isAlive()) {
                    PRELUDES.remove(e.getKey());
                    continue;
                }
                pr.age++;
                if (pr.story == CharacterStories.YUJI) {
                    if (pr.age == 55) film(p, "The man on the screen stops eating.", pr.length - pr.age);
                    if (pr.age == 95) {
                        film(p, "He turns, and looks straight out of the screen at you.", pr.length - pr.age);
                        Fx.sound((ServerLevel) p.level(), p.position(), SoundEvents.WARDEN_EMERGE, 0.6f, 1.4f);
                    }
                }
                if (pr.age >= pr.length) {
                    PRELUDES.remove(e.getKey());
                    pull(p, pr.story, pr.test);
                }
            }
        }
        if (server.getTickCount() % 5 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) wearing(p);
        }
    }

    /** The Infused Blindfold: on, everything goes dark and cursed energy shows; kept on, the trial takes them. */
    private static void wearing(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        // Its owner has already learned to see without it: for them it is only a blindfold they chose to keep.
        boolean on = head.is(ProgressionItems.INFUSED_BLINDFOLD) && !KitOwnership.get(p.level().getServer()).isOwner(CharacterStories.GOJO.kit(), p.getUUID());
        Integer worn = WORN.get(p.getUUID());
        if (!on) {
            if (worn != null) {
                WORN.remove(p.getUUID());
                if (!inTrial(p)) ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.BLIND, "", "", 0, 0));
            }
            return;
        }
        if (worn == null) {
            ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.BLIND, "", "", 0, 1));
            p.sendOverlayMessage(Component.literal("Everything goes dark. Then, slowly, you begin to see.").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
            worn = 0;
        }
        worn += 5;
        WORN.put(p.getUUID(), worn);
        if (worn == WEAR_TICKS && !inTrial(p)) {
            String why = blocked(p, head, CharacterStories.GOJO);
            if (why == null) begin(p, head, CharacterStories.GOJO);
            else if (!why.isEmpty()) p.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void pull(ServerPlayer p, CharacterStory story, boolean test) {
        String key = story.kit() + ":" + p.getUUID();
        CursedRealms.pull(p, p.position(), () -> {
            Run run = create(story, key, p.getUUID(), test);
            RUNS.put(key, run);
            InvestigationState st = InvestigationState.get(p.level().getServer());
            InvestigationState.Arena a = CursedRealms.enterTrial(p, key, run.layout(), st);
            run.arena = a;
            run.start((ServerLevel) p.level(), p);
            JJK.LOGGER.info("[storylines] {} began the {} trial{}", p.getName().getString(), story.kit(), test ? " (test)" : "");
        });
    }

    private static Run create(CharacterStory story, String key, UUID player, boolean test) {
        return switch (story.kit()) {
            case "gojo" -> new GojoTrial(story, key, player, test);
            case "yuta" -> new YutaTrial(story, key, player, test);
            case "ryu" -> new RyuTrial(story, key, player, test);
            case "hakari" -> new HakariTrial(story, key, player, test);
            default -> new YujiTrial(story, key, player, test);
        };
    }

    /** Called by the realm every tick for a trial arena: 1 won, -1 lost (close it), 0 running. */
    public static int tickArena(MinecraftServer server, InvestigationState.Arena a, String key, ServerLevel realm) {
        Run run = RUNS.get(key);
        if (run == null) return -1;
        run.arena = a;
        if (run.won) return 1;
        ServerPlayer p = server.getPlayerList().getPlayer(run.player);
        if (p == null || !p.isAlive() || !a.inside().contains(run.player)) {
            if (++run.gone >= GONE_TICKS) {
                run.failed = "The trial lets you go. The relic will wait.";
                return -1;
            }
            return 0;
        }
        run.gone = 0;
        run.age++;
        run.phaseAge++;
        int r = run.tick(realm, p);
        if (r > 0) {
            run.won = true;
            victory(run, p);
            return 1;
        }
        return r;
    }

    /** The arena is gone (won and over, failed, abandoned, a restart): clean up after the trial. */
    public static void arenaClosed(MinecraftServer server, String key, boolean won) {
        Run run = RUNS.remove(key);
        if (run == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(run.player);
        run.cleanup(CursedRealms.level(server), p);
        if (p != null) {
            ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.BLIND, "", "", 0, 0));
            ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.METER, "", "", 0, 0));
            if (!won) p.sendSystemMessage(Component.literal(run.failed != null ? run.failed : "The trial lets you go. The relic will wait.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** The trial is complete: claim the base kit (or, in Creative, try it out; a test relic in Survival is an echo). */
    static void victory(Run run, ServerPlayer p) {
        JJK.LOGGER.info("[storylines] {} completed the {} trial{}", p.getName().getString(), run.story.kit(), run.test ? " (test)" : "");
        if (run.test && !TechniqueProgression.isSandbox(p)) {
            p.sendSystemMessage(Component.literal("It was only an echo of the real thing. Nothing of it stays with you.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        TechniqueProgression.acquire(p, new RelicAcquisition(run.story));
    }

    // --- Shared presentation ---

    static void card(ServerPlayer p, String title, String sub, int color, int ticks) {
        ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.CARD, title, sub, color, ticks));
    }

    static void meter(ServerPlayer p, String text, int color) {
        ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.METER, text, "", color, 0));
    }

    static void film(ServerPlayer p, String line, int ticks) {
        ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.FILM, "", line, 0, ticks));
    }

    static void say(ServerPlayer p, String line, ChatFormatting color) {
        p.sendOverlayMessage(Component.literal(line).withStyle(color, ChatFormatting.ITALIC));
    }

    static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp, false, false));
    }

    // --- A trial in progress ---

    /** One player's run through a character trial: its phases, the curses it raised, and its rules. */
    abstract static class Run {
        final CharacterStory story;
        final String key;
        final UUID player;
        final boolean test;
        final List<UUID> curses = new ArrayList<>();
        InvestigationState.Arena arena;
        int phase, age, phaseAge, gone;
        boolean won;
        @Nullable String failed;

        Run(CharacterStory story, String key, UUID player, boolean test) {
            this.story = story;
            this.key = key;
            this.player = player;
            this.test = test;
        }

        abstract String layout();

        /** The arena has just been entered. */
        abstract void start(ServerLevel realm, ServerPlayer p);

        /** One tick: 1 won, -1 lost, 0 running. */
        abstract int tick(ServerLevel realm, ServerPlayer p);

        /** The relic used again inside the trial (Yuta's bond). */
        boolean used(ServerPlayer p, ItemStack stack) {
            return false;
        }

        /** Whether a hit from the player may land on one of the trial's curses. */
        boolean allowHit(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
            return true;
        }

        /** The player hit one of the trial's curses. */
        void landed(ServerPlayer p, CommonCurseEntity c) {}

        boolean cursedFists() {
            return false;
        }

        /** Undo what the trial did to the player and the arena. */
        void cleanup(ServerLevel realm, @Nullable ServerPlayer p) {
            if (p == null) return;
            for (Holder<MobEffect> e : List.of(MobEffects.BLINDNESS, MobEffects.STRENGTH, MobEffects.SPEED, MobEffects.RESISTANCE,
                    MobEffects.REGENERATION, MobEffects.WEAKNESS, MobEffects.HUNGER, MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE)) {
                p.removeEffect(e);
            }
        }

        void next() {
            phase++;
            phaseAge = 0;
        }

        /** Raises one curse of {@code kind} at {@code at}, leashed to the arena and counted by the trial. */
        @Nullable
        CommonCurseEntity spawn(ServerLevel realm, String kind, CurseGrade grade, Vec3 at) {
            EntityType<? extends CommonCurseEntity> type = CurseKinds.type(kind);
            if (type == null) return null;
            CommonCurseEntity c = type.create(realm, EntitySpawnReason.EVENT);
            if (c == null) return null;
            c.setPos(at.x, at.y, at.z);
            c.setYRot(realm.getRandom().nextFloat() * 360f);
            c.setGrade(grade);
            c.bind("", arena.origin, CursedRealms.arenaRadius(arena));
            if (!realm.addFreshEntity(c)) return null;
            curses.add(c.getUUID());
            Fx.play(realm, "fb_manifest", at.add(0, 1, 0), Vec3.ZERO, 0.8f, c.getId());
            return c;
        }

        /** A wave: kinds and counts at the layout's curse spots. */
        void wave(ServerLevel realm, CurseGrade grade, String... kinds) {
            List<Vec3> spots = CursedRealms.curseSpotsOf(arena);
            int i = 0;
            for (String k : kinds) spawn(realm, k, grade, spots.get(i++ % spots.size()));
            Fx.sound(realm, Vec3.atCenterOf(arena.origin), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8f, 0.6f);
        }

        /** The trial's curses still standing. */
        List<CommonCurseEntity> living(ServerLevel realm) {
            List<CommonCurseEntity> out = new ArrayList<>();
            curses.removeIf(id -> {
                Entity e = realm.getEntity(id);
                if (e instanceof CommonCurseEntity c && c.isAlive() && !c.isRemoved()) {
                    out.add(c);
                    return false;
                }
                return true;
            });
            return out;
        }

        boolean cleared(ServerLevel realm) {
            return living(realm).isEmpty();
        }
    }

    // ================================================================================================================
    // Yuji: the body itself becomes the weapon.
    // ================================================================================================================

    static final class YujiTrial extends Run {
        private int warned;

        YujiTrial(CharacterStory s, String k, UUID p, boolean t) {
            super(s, k, p, t);
        }

        @Override
        String layout() {
            return "theater_realm";
        }

        @Override
        void start(ServerLevel realm, ServerPlayer p) {
            card(p, "THE FILM IS WATCHING", "Put your weapons away. Your body is enough.", story.color(), 80);
        }

        @Override
        boolean cursedFists() {
            return true;
        }

        @Override
        int tick(ServerLevel realm, ServerPlayer p) {
            // The body as the weapon: strong, fast, every punch carries cursed energy.
            if (age % 40 == 1) {
                effect(p, MobEffects.STRENGTH, 60, 1);
                effect(p, MobEffects.SPEED, 60, 0);
            }
            return switch (phase) {
                case 0 -> {
                    if (phaseAge == 60) {
                        wave(realm, CurseGrade.GRADE_4, "school_crawler", "school_crawler");
                        meter(p, "The audience rises: 1 / 3", story.color());
                        next();
                    }
                    yield 0;
                }
                case 1, 3 -> {
                    if (cleared(realm)) {
                        p.heal(6f);
                        say(p, phase == 1 ? "More of them stand up in the seats. Their faces fold in on themselves." : "The projector is running faster.", ChatFormatting.RED);
                        next();
                    }
                    yield 0;
                }
                case 2 -> {
                    if (phaseAge == 50) {
                        wave(realm, CurseGrade.GRADE_4, "school_crawler", "school_crawler", "school_crawler", "school_maw");
                        meter(p, "The audience rises: 2 / 3", story.color());
                        next();
                    }
                    yield 0;
                }
                case 4 -> {
                    if (phaseAge == 50) {
                        card(p, "FINAL REEL", "Dodge. Block. Hit back harder.", story.color(), 60);
                        wave(realm, CurseGrade.GRADE_3, "school_maw", "school_crawler", "school_crawler");
                        meter(p, "The audience rises: 3 / 3", story.color());
                        next();
                    }
                    yield 0;
                }
                default -> cleared(realm) ? 1 : 0;
            };
        }

        @Override
        boolean allowHit(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
            boolean bare = source.getDirectEntity() == p && p.getMainHandItem().isEmpty() && !dev.rick.jjk.registry.ModDamageTypes.isOurs(source);
            if (!bare && age - warned > 60) {
                warned = age;
                say(p, "It doesn't even feel that. Your body. Use your body.", ChatFormatting.RED);
            }
            return bare;
        }

        @Override
        void landed(ServerPlayer p, CommonCurseEntity c) {
            if (p.level() instanceof ServerLevel l && l.getRandom().nextInt(6) == 0) {
                // Now and then the timing is perfect: a spark of black.
                Fx.play(l, "black_flash", c.position().add(0, 1, 0), p.getLookAngle(), 1f, c.getId());
                c.hurtServer(l, l.damageSources().fellOutOfWorld(), 6f);
            }
        }
    }

    // ================================================================================================================
    // Gojo: perception, precision, space; then the blindfold comes off.
    // ================================================================================================================

    static final class GojoTrial extends Run {
        GojoTrial(CharacterStory s, String k, UUID p, boolean t) {
            super(s, k, p, t);
        }

        @Override
        String layout() {
            return "distance_realm";
        }

        @Override
        void start(ServerLevel realm, ServerPlayer p) {
            ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.BLIND, "", "", 0, 1));
            card(p, "SEE IT", "Not with your eyes.", story.color(), 70);
        }

        @Override
        int tick(ServerLevel realm, ServerPlayer p) {
            boolean blindPhase = phase < 5;
            if (blindPhase) {
                // The blindfold must stay on until it comes off by itself.
                if (!p.getItemBySlot(EquipmentSlot.HEAD).is(ProgressionItems.INFUSED_BLINDFOLD)) {
                    failed = "You took it off too soon. The light goes out of the road, and you are back where you began.";
                    return -1;
                }
                if (age % 20 == 1) effect(p, MobEffects.BLINDNESS, 40, 0);
                // Cursed energy is the only light: every curse glows, and what is about to strike flares.
                for (CommonCurseEntity c : living(realm)) {
                    if (age % 20 == 2) effect(c, MobEffects.GLOWING, 30, 0);
                    if (age % 10 == 0 && c.distanceToSqr(p) < 6 * 6) {
                        ServerPlayNetworking.send(p, new dev.rick.jjk.core.net.FxPayload("curse_tell", c.position().add(0, c.getBbHeight() * 0.7, 0), Vec3.ZERO, 1f, c.getId()));
                    }
                }
            }
            switch (phase) {
                case 0 -> {
                    if (phaseAge == 60) {
                        wave(realm, CurseGrade.GRADE_4, "fly_head", "fly_head", "fly_head", "fly_head");
                        meter(p, "Perceive", story.color());
                        next();
                    }
                }
                case 1 -> {
                    if (cleared(realm)) next();
                }
                case 2 -> {
                    if (phaseAge == 30) {
                        say(p, "The road folds. Distance stops meaning anything.", ChatFormatting.AQUA);
                        wave(realm, CurseGrade.GRADE_4, "school_crawler", "school_crawler", "fly_head", "fly_head", "fly_head");
                        meter(p, "Space", story.color());
                        next();
                    }
                }
                case 3 -> {
                    // Space folds: every few seconds the road puts them somewhere else on it.
                    if (phaseAge % 120 == 119) fold(realm, p);
                    if (cleared(realm)) next();
                }
                case 4 -> {
                    if (phaseAge == 40) {
                        // The climax: the blindfold comes off.
                        ItemStack fold = p.getItemBySlot(EquipmentSlot.HEAD).copy();
                        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        if (!p.getInventory().add(fold)) p.drop(fold, false, net.minecraft.util.Prediction.SERVER_ONLY);
                        p.removeEffect(MobEffects.BLINDNESS);
                        ServerPlayNetworking.send(p, new StoryPayload(StoryPayload.SIX_EYES, "", "", 0, 120));
                        Fx.flash(realm, p.position(), 4, 0xFFFFFFFF, 10);
                        Fx.sound(realm, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.5f, 1.6f);
                        Fx.sound(realm, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.8f);
                        effect(p, MobEffects.SPEED, 900, 1);
                        effect(p, MobEffects.STRENGTH, 900, 1);
                        effect(p, MobEffects.RESISTANCE, 900, 0);
                        next();
                    }
                }
                case 5 -> {
                    if (phaseAge == 70) {
                        card(p, "THE BLINDFOLD COMES OFF", "You can see everything.", story.color(), 70);
                        wave(realm, CurseGrade.GRADE_3, "school_maw", "school_crawler", "school_crawler", "fly_head", "fly_head");
                        meter(p, "Everything", story.color());
                        next();
                    }
                }
                case 6 -> {
                    // Everything is seen at once: every curse stays lit, wherever it is.
                    for (CommonCurseEntity c : living(realm)) if (age % 20 == 2) effect(c, MobEffects.GLOWING, 30, 0);
                    if (cleared(realm)) return 1;
                }
                default -> {}
            }
            return 0;
        }

        /** The road folds: set down somewhere else on it, facing the other way. */
        private void fold(ServerLevel realm, ServerPlayer p) {
            Vec3 o = Vec3.atBottomCenterOf(arena.origin);
            double x = o.x + (realm.getRandom().nextInt(31) - 15);
            Fx.play(realm, "curse_realm_pull", p.position().add(0, 1, 0), Vec3.ZERO, 0.8f, -1);
            p.teleportTo(x, o.y + 1, o.z + realm.getRandom().nextInt(5) - 2);
            p.setYRot(p.getYRot() + 180f);
            p.fallDistance = 0;
            Fx.sound(realm, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.5f);
        }
    }

    // ================================================================================================================
    // Yuta: a presence that seems hostile, and is protecting you.
    // ================================================================================================================

    static final class YutaTrial extends Run {
        @Nullable private dev.rick.jjk.yuta.RikaEntity rika;
        private int trust;
        private int far;
        @Nullable private UUID guarding;
        private int guardTicks;

        YutaTrial(CharacterStory s, String k, UUID p, boolean t) {
            super(s, k, p, t);
        }

        @Override
        String layout() {
            return "chapel_realm";
        }

        @Override
        void start(ServerLevel realm, ServerPlayer p) {
            rika = dev.rick.jjk.yuta.RikaEntity.summon(realm, p);
            // She starts far off, across the garden, watching.
            rika.moveTo(Vec3.atBottomCenterOf(arena.origin).add(0, 2, -10), 0.6, 40);
            rika.station();
            card(p, "DON'T LEAVE ME", "", story.color(), 60);
            effect(p, MobEffects.DARKNESS, 80, 0);
            Fx.sound(realm, p.position(), SoundEvents.WARDEN_HEARTBEAT, 2f, 0.6f);
        }

        @Override
        int tick(ServerLevel realm, ServerPlayer p) {
            if (rika == null || rika.isRemoved()) {
                rika = dev.rick.jjk.yuta.RikaEntity.summon(realm, p);
                rika.station();
            }
            double d = rika.distanceTo(p);
            // Running from her only makes it worse: the dark closes in and the bond frays.
            if (d > 14) {
                if (++far % 40 == 0) {
                    effect(p, MobEffects.DARKNESS, 60, 0);
                    Fx.sound(realm, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.5f);
                    trust = Math.max(0, trust - 1);
                    if (far >= 200) say(p, "It follows you wherever you run. It always will.", ChatFormatting.LIGHT_PURPLE);
                }
            } else {
                far = 0;
            }
            protect(realm, p);
            meter(p, "Trust " + Math.min(6, trust) + " / 6", story.color());
            switch (phase) {
                case 0 -> {
                    // The haunting: she closes in on them while they look away.
                    if (phaseAge % 30 == 0) {
                        Vec3 look = p.getLookAngle();
                        Vec3 toHer = rika.position().subtract(p.position()).normalize();
                        if (look.dot(toHer) < 0.3) {
                            rika.moveTo(p.position().add(toHer.scale(Math.max(4, d - 4))).add(0, 1.5, 0), 0.8, 20);
                            Fx.sound(realm, rika.position(), SoundEvents.SOUL_ESCAPE.value(), 1f, 0.5f);
                        }
                    }
                    if (phaseAge == 80) {
                        wave(realm, CurseGrade.GRADE_4, "school_crawler", "fly_head", "fly_head", "fly_head");
                        next();
                    }
                }
                case 1 -> {
                    if (cleared(realm) || trust >= 3) {
                        say(p, "It only ever struck what came for you.", ChatFormatting.LIGHT_PURPLE);
                        card(p, "IT IS PROTECTING YOU", "Protect it back.", story.color(), 80);
                        rika.recall();
                        next();
                    }
                }
                case 2 -> {
                    if (phaseAge == 60) {
                        wave(realm, CurseGrade.GRADE_3, "school_maw", "school_crawler", "school_crawler");
                        next();
                    }
                }
                case 3 -> {
                    // Defending one another: she holds what comes near her, and they finish it.
                    for (CommonCurseEntity c : living(realm)) {
                        if (c.distanceTo(rika) < 4) {
                            effect(c, MobEffects.SLOWNESS, 30, 3);
                            if (age % 20 == 0) Fx.play(realm, "curse_tell", c.position().add(0, 1, 0), Vec3.ZERO, 0.6f, c.getId());
                        }
                    }
                    if (cleared(realm)) {
                        rika.recall();
                        card(p, "ACCEPT IT", "Use the ring.", story.color(), 100);
                        say(p, "She stays beside you, waiting.", ChatFormatting.LIGHT_PURPLE);
                        next();
                    }
                }
                case 4 -> {
                    if (phaseAge % 200 == 199) say(p, "She is still waiting. Use the ring.", ChatFormatting.LIGHT_PURPLE);
                }
                case 5 -> {
                    return phaseAge >= 40 ? 1 : 0;
                }
                default -> {}
            }
            return 0;
        }

        /** Anything that comes close to them, she reaches first. */
        private void protect(ServerLevel realm, ServerPlayer p) {
            if (guarding != null) {
                Entity e = realm.getEntity(guarding);
                if (!(e instanceof CommonCurseEntity c) || !c.isAlive()) {
                    guarding = null;
                } else if (++guardTicks >= 10) {
                    Fx.play(realm, "hit_heavy", c.position().add(0, 1, 0), Vec3.ZERO, 1.2f, c.getId());
                    Fx.sound(realm, c.position(), SoundEvents.WARDEN_ATTACK_IMPACT, 1.2f, 0.8f);
                    c.hurtServer(realm, realm.damageSources().fellOutOfWorld(), phase < 2 ? 999f : 8f);
                    guarding = null;
                    trust++;
                    if (rika != null) rika.recall();
                }
                return;
            }
            for (CommonCurseEntity c : living(realm)) {
                if (c.distanceTo(p) < 3.5 && rika != null) {
                    guarding = c.getUUID();
                    guardTicks = 0;
                    rika.moveTo(c.position().add(0, 1, 0), 2.0, 10);
                    rika.setTarget(c);
                    break;
                }
            }
        }

        @Override
        boolean used(ServerPlayer p, ItemStack stack) {
            if (phase != 4 || rika == null) {
                say(p, "Not yet.", ChatFormatting.GRAY);
                return false;
            }
            next();
            ServerLevel realm = (ServerLevel) p.level();
            rika.set(dev.rick.jjk.yuta.RikaEntity.FULL, true);
            Fx.play(realm, "prog_infuse_done", rika.position().add(0, 1, 0), Vec3.ZERO, 2f, rika.getId());
            Fx.sound(realm, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.0f);
            card(p, "TOGETHER", "You accepted the bond.", story.color(), 60);
            return true;
        }

        @Override
        void cleanup(ServerLevel realm, @Nullable ServerPlayer p) {
            super.cleanup(realm, p);
            if (rika != null) rika.discard();
            if (p != null) p.removeEffect(MobEffects.DARKNESS);
        }
    }

    // ================================================================================================================
    // Ryu: are you satisfied?
    // ================================================================================================================

    static final class RyuTrial extends Run {
        static final int WAVES = 5;
        private int wave;
        @Nullable private BlockPos more, enough;
        private int lastBlast;

        RyuTrial(CharacterStory s, String k, UUID p, boolean t) {
            super(s, k, p, t);
        }

        @Override
        String layout() {
            return "crater_realm";
        }

        @Override
        void start(ServerLevel realm, ServerPlayer p) {
            card(p, "ARE YOU SATISFIED?", "", story.color(), 50);
        }

        @Override
        boolean cursedFists() {
            return wave == WAVES;
        }

        @Override
        int tick(ServerLevel realm, ServerPlayer p) {
            switch (phase) {
                case 0 -> {
                    // A wave, harder each time.
                    if (phaseAge == 50) {
                        wave++;
                        meter(p, "Helping " + wave + " / " + WAVES, story.color());
                        switch (wave) {
                            case 1 -> wave(realm, CurseGrade.GRADE_4, "fly_head", "fly_head", "school_crawler");
                            case 2 -> wave(realm, CurseGrade.GRADE_4, "school_crawler", "school_crawler", "fly_head", "fly_head", "fly_head");
                            case 3 -> wave(realm, CurseGrade.GRADE_3, "school_crawler", "school_crawler", "school_maw");
                            case 4 -> wave(realm, CurseGrade.GRADE_3, "school_maw", "school_maw", "school_crawler", "fly_head", "fly_head");
                            default -> {
                                card(p, "EVERY LAST DROP", "Pour all of it out.", story.color(), 70);
                                effect(p, MobEffects.STRENGTH, 2400, 2);
                                effect(p, MobEffects.RESISTANCE, 2400, 1);
                                wave(realm, CurseGrade.GRADE_2, "school_maw", "school_crawler", "school_crawler", "school_crawler");
                            }
                        }
                        next();
                    }
                }
                case 1 -> {
                    if (cleared(realm)) {
                        if (wave >= WAVES) return 1;
                        card(p, "SATISFIED?", "Gold for more. Grey if you've had enough.", story.color(), 80);
                        Fx.sound(realm, p.position(), SoundEvents.PLAYER_LEVELUP, 1f, 0.6f);
                        p.heal(8f);
                        pillars(realm, true);
                        next();
                    }
                }
                case 2 -> {
                    // The choice is made by walking to it.
                    if (more != null && near(p, more)) {
                        pillars(realm, false);
                        say(p, "More.", ChatFormatting.GOLD);
                        Fx.sound(realm, p.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
                        phase = 0;
                        phaseAge = 0;
                    } else if (enough != null && near(p, enough)) {
                        pillars(realm, false);
                        failed = "You were satisfied. That was never going to be enough.";
                        return -1;
                    }
                }
                default -> {}
            }
            return 0;
        }

        private static boolean near(ServerPlayer p, BlockPos b) {
            double dx = p.getX() - (b.getX() + 0.5), dz = p.getZ() - (b.getZ() + 0.5);
            return dx * dx + dz * dz < 2.2 * 2.2;
        }

        /** The two pillars of the choice: gold (MORE) and grey (SATISFIED), up out of the crater floor, or gone again. */
        private void pillars(ServerLevel realm, boolean up) {
            if (!up) {
                for (BlockPos b : new BlockPos[] {more, enough}) {
                    if (b == null) continue;
                    for (int y = 0; y < 4; y++) realm.setBlock(b.above(y), Blocks.AIR.defaultBlockState(), 3);
                }
                more = enough = null;
                return;
            }
            more = floor(realm, arena.origin.offset(-5, 0, 2));
            enough = floor(realm, arena.origin.offset(5, 0, 2));
            for (int y = 0; y < 3; y++) {
                realm.setBlock(more.above(y), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                realm.setBlock(enough.above(y), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
            realm.setBlock(more.above(3), Blocks.LANTERN.defaultBlockState(), 3);
            Fx.play(realm, "ground_impact", Vec3.atBottomCenterOf(more), Vec3.ZERO, 1f, -1);
            Fx.play(realm, "ground_impact", Vec3.atBottomCenterOf(enough), Vec3.ZERO, 1f, -1);
        }

        /** The first open block above the crater floor in a column. */
        private static BlockPos floor(ServerLevel realm, BlockPos col) {
            for (int y = 12; y > -6; y--) {
                BlockPos b = col.above(y);
                if (!realm.getBlockState(b.below()).isAir() && realm.getBlockState(b).isAir()) return b;
            }
            return col.above(1);
        }

        @Override
        void landed(ServerPlayer p, CommonCurseEntity c) {
            // The last wave: every blow is a blast of overwhelming output.
            if (wave != WAVES || age - lastBlast < 10 || !(p.level() instanceof ServerLevel realm)) return;
            lastBlast = age;
            Vec3 at = c.position().add(0, 0.5, 0);
            Fx.play(realm, "granite_blast", p.getEyePosition(), at.subtract(p.getEyePosition()), 1.4f, p.getId());
            Fx.play(realm, "ground_impact", at, Vec3.ZERO, 2f, -1);
            Fx.shake(realm, at, 16, 0.7f, 10);
            Fx.sound(realm, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.7f);
            for (CommonCurseEntity o : living(realm)) {
                if (o.distanceToSqr(at) > 6 * 6) continue;
                o.hurtServer(realm, realm.damageSources().fellOutOfWorld(), 10f);
                Vec3 push = o.position().subtract(at).normalize().scale(1.2);
                o.push(push.x, 0.5, push.z);
            }
        }

        @Override
        void cleanup(ServerLevel realm, @Nullable ServerPlayer p) {
            super.cleanup(realm, p);
            if (arena != null) pillars(realm, false);
        }
    }

    // ================================================================================================================
    // Hakari: wagers, rounds, and the jackpot.
    // ================================================================================================================

    static final class HakariTrial extends Run {
        static final String[] WAGERS = {"HEALTH", "CURSED ENERGY", "HEALING", "YOUR GRIP", "YOUR LEGS"};
        private int round;
        private int wager = -1;
        private boolean fever;

        HakariTrial(CharacterStory s, String k, UUID p, boolean t) {
            super(s, k, p, t);
        }

        @Override
        String layout() {
            return "fight_club_realm";
        }

        @Override
        void start(ServerLevel realm, ServerPlayer p) {
            card(p, "FIGHT NIGHT", "House rules: every round, you wager something.", story.color(), 70);
        }

        @Override
        boolean cursedFists() {
            return fever;
        }

        @Override
        int tick(ServerLevel realm, ServerPlayer p) {
            if (fever && age % 10 == 0) Fx.play(realm, "jackpot_aura", p.position().add(0, 1, 0), Vec3.ZERO, 1f, p.getId());
            switch (phase) {
                case 0 -> {
                    if (phaseAge == 60) {
                        round++;
                        if (round == 4) {
                            jackpot(realm, p);
                        } else {
                            spin(realm, p);
                        }
                        next();
                    }
                }
                case 1 -> {
                    if (wager >= 0 && phaseAge % 40 == 1) keepWager(p);
                    if (phaseAge == 50) {
                        Fx.sound(realm, p.position(), SoundEvents.BELL_BLOCK, 1.5f, 1f);
                        switch (round) {
                            case 1 -> wave(realm, CurseGrade.GRADE_4, "school_crawler", "fly_head", "fly_head");
                            case 2 -> wave(realm, CurseGrade.GRADE_4, "school_crawler", "school_crawler", "school_maw");
                            case 3 -> wave(realm, CurseGrade.GRADE_3, "school_maw", "school_crawler", "fly_head", "fly_head");
                            default -> wave(realm, CurseGrade.GRADE_2, "school_maw", "school_crawler", "school_crawler", "fly_head", "fly_head");
                        }
                        meter(p, "Round " + round + (wager >= 0 ? " · wagered: " + WAGERS[wager] : " · FEVER"), story.color());
                        next();
                    }
                }
                case 2 -> {
                    if (wager >= 0 && phaseAge % 40 == 1) keepWager(p);
                    if (cleared(realm)) {
                        if (round >= 4) return 1;
                        payout(realm, p);
                        phase = 0;
                        phaseAge = 0;
                    }
                }
                default -> {}
            }
            return 0;
        }

        /** The reels spin and a wager is drawn against them for the round. */
        private void spin(ServerLevel realm, ServerPlayer p) {
            int a = 1 + realm.getRandom().nextInt(9), b = 1 + realm.getRandom().nextInt(9), c = 1 + realm.getRandom().nextInt(9);
            if (a == b && b == c) c = c % 9 + 1; // not yet
            wager = Math.floorMod(a + b * 3 + c * 7 + round, WAGERS.length);
            card(p, a + " · " + b + " · " + c, "No win. The house takes " + WAGERS[wager] + " for this round.", story.color(), 70);
            Fx.sound(realm, p.position(), SoundEvents.NOTE_BLOCK_PLING, 1f, 0.7f);
            if (wager == 0) p.setHealth(Math.min(p.getHealth(), p.getMaxHealth() / 2f));
            keepWager(p);
        }

        /** The wager's toll, kept up for the round. */
        private void keepWager(ServerPlayer p) {
            switch (wager) {
                case 1 -> effect(p, MobEffects.WEAKNESS, 60, 1);
                case 2 -> effect(p, MobEffects.HUNGER, 60, 4);
                case 3 -> effect(p, MobEffects.MINING_FATIGUE, 60, 2);
                case 4 -> effect(p, MobEffects.SLOWNESS, 60, 1);
                default -> {}
            }
        }

        /** A round won: the wager comes back, and a little luck with it. */
        private void payout(ServerLevel realm, ServerPlayer p) {
            for (Holder<MobEffect> e : List.of(MobEffects.WEAKNESS, MobEffects.HUNGER, MobEffects.MINING_FATIGUE, MobEffects.SLOWNESS)) p.removeEffect(e);
            wager = -1;
            p.heal(6f);
            effect(p, MobEffects.ABSORPTION, 600, 1);
            say(p, "The crowd roars. The wager comes back to you.", ChatFormatting.GREEN);
            Fx.sound(realm, p.position(), SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
        }

        /** 7 · 7 · 7: the fever. */
        private void jackpot(ServerLevel realm, ServerPlayer p) {
            wager = -1;
            fever = true;
            card(p, "7 · 7 · 7", "JACKPOT", 0xF0C040, 90);
            Fx.play(realm, "jackpot_heal", p.position().add(0, 1, 0), Vec3.ZERO, 1f, p.getId());
            Fx.flash(realm, p.position(), 6, 0x9050FFA8, 12);
            Fx.sound(realm, p.position(), SoundEvents.BELL_BLOCK, 2f, 1.2f);
            Fx.sound(realm, p.position(), SoundEvents.TOTEM_USE, 1f, 1.2f);
            p.setHealth(p.getMaxHealth());
            effect(p, MobEffects.REGENERATION, 2400, 3);
            effect(p, MobEffects.STRENGTH, 2400, 1);
            effect(p, MobEffects.SPEED, 2400, 1);
            effect(p, MobEffects.RESISTANCE, 2400, 1);
        }
    }

    /** Tests: whether a trial is running for a key. */
    public static boolean runningForTest(String kit, UUID player) {
        return RUNS.containsKey(kit + ":" + player);
    }

    /** Tests/admin: starts a trial now for {@code p}, skipping the relic and the prelude (a test run unless claiming). */
    public static void startNow(ServerPlayer p, CharacterStory story, boolean test) {
        pull(p, story, test);
    }

    /** Tests/admin: completes the running trial now (as if its last curse fell). */
    public static boolean completeNow(ServerPlayer p) {
        Run r = runOf(p);
        if (r == null || r.won) return false;
        r.won = true;
        victory(r, p);
        return true;
    }
}
