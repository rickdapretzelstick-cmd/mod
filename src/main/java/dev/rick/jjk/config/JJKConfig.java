package dev.rick.jjk.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.rick.jjk.JJK;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All tunable values for the mod. Loaded from config/jjk.json; missing fields keep their defaults and
 * the file is rewritten on load so new options appear automatically.
 *
 * Server-authoritative values live in every section except {@link Client}, which only affects the local game.
 * Time values are in ticks (20 ticks = 1 second) unless the name says otherwise.
 */
public final class JJKConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static JJKConfig instance = new JJKConfig();

    /**
     * The gameplay tuning this file was written for. When the mod's defaults change (the JJS wiki pass), older files have
     * their gameplay sections reset to the new defaults; the player's client settings are kept.
     */
    public static final int CURRENT_VERSION = 11;
    public int version;

    public General general = new General();
    public Resources resources = new Resources();
    public Melee melee = new Melee();
    public Guard guard = new Guard();
    public Dash dash = new Dash();
    public Infinity infinity = new Infinity();
    public Blue blue = new Blue();
    public Red red = new Red();
    public Purple purple = new Purple();
    public Teleport teleport = new Teleport();
    public Domain domain = new Domain();
    public Awakening awakening = new Awakening();
    public MaxBlue maxBlue = new MaxBlue();
    public MaxRed maxRed = new MaxRed();
    public Restoration restoration = new Restoration();
    public Clash clash = new Clash();
    public Client client = new Client();
    public Hakari hakari = new Hakari();
    public Gojo gojo = new Gojo();
    public Yuji yuji = new Yuji();
    public Yuta yuta = new Yuta();
    public Ryu ryu = new Ryu();
    public BeamClash beamClash = new BeamClash();
    public Progression progression = new Progression();
    public MasteryRules mastery = new MasteryRules();
    public Rifle rifle = new Rifle();
    public CursedToolKits cursedTools = new CursedToolKits();
    public Realms realms = new Realms();

    public static JJKConfig get() {
        return instance;
    }

    public static class General {
        /** Players joining for the first time become Gojo automatically (phase 1 playtest convenience). */
        public boolean autoAssignGojo = true;
        /** Whether players can hurt each other with techniques and melee (vanilla pvp setting still applies). */
        public boolean playerVsPlayer = true;
        /** Whether techniques can hit tamed pets owned by the caster. */
        public boolean hitOwnPets = false;
        /** Global damage multiplier for everything this mod deals. */
        public float damageMultiplier = 1.0f;
        /** Global multiplier on knockback dealt by this mod. */
        public float knockbackMultiplier = 1.0f;
        /** Extra reach tolerance (blocks) granted to hits the client confirmed, to absorb network latency. */
        public double latencyTolerance = 0.75;
        /** Hits within this many ticks of each other count as the same combo. */
        public int comboWindow = 40;
        /** After this many hits in one combo, hitstun is heavily reduced so the target can escape. */
        public int comboHitstunDecayStart = 10;
        /** Damage multiplier lost per hit in a combo, floored at comboMinDamageScale. */
        public float comboDamageDecay = 0.035f;
        public float comboMinDamageScale = 0.45f;
        /** Invulnerability to melee after getting up from a knockdown. */
        public int wakeupInvulnerability = 10;
        /** Allow techniques to break blocks at all (also requires the mobGriefing gamerule). */
        public boolean allowBlockDestruction = true;
        /** Blocks with hardness above this are never destroyed by techniques (obsidian is 50). */
        public float maxDestructibleHardness = 20f;
        /** Hard cap on blocks destroyed per server tick across all techniques, to protect TPS. */
        public int maxBlocksPerTick = 400;
        /** Drop items for destroyed blocks. */
        public boolean destroyedBlocksDropItems = false;
    }

    public static class Resources {
        public float gojoMaxCursedEnergy = 1000f;
        /** Cursed energy regained per second. */
        public float gojoRegenPerSecond = 16f;
        /** Delay (ticks) after spending cursed energy before regeneration resumes. */
        public int regenDelay = 30;
        /** Duration of technique burnout after a domain collapses (ticks). */
        public int domainBurnout = 160;
        /** Disable all cooldowns and costs (testing). Also toggled per player with /jjk nocooldown. */
        public boolean creativeNoCost = false;
    }

    public static class Melee {
        public float lightDamage = 3.0f;
        public float lightFinisherDamage = 4.5f;
        public int lightHitstun = 13;
        public int lightInterval = 6;
        public int chainResetTicks = 22;
        public int chainEndlag = 16;
        public double lightRange = 3.4;
        public double lightWidth = 1.5;
        public double finisherKnockback = 1.55;
        public double uppercutLaunch = 1.05;
        public double downslamSpeed = 1.6;
        public int knockdownTicks = 26;
        public float sprintAttackDamage = 4.0f;
        public double sprintLunge = 1.25;
        public float heavyMinDamage = 7.0f;
        public float heavyMaxDamage = 11.0f;
        public int heavyMinCharge = 7;
        public int heavyMaxCharge = 22;
        public int heavyCooldown = 80;
        public double heavyKnockback = 2.1;
        public float groundAttackDamage = 5.0f;
        public float airHitHover = 0.25f;
    }

    public static class Guard {
        public int maxGuardHits = 6;
        public int guardRegenInterval = 30;
        public int perfectBlockWindow = 4;
        public int parryStun = 22;
        public int guardBreakStun = 34;
        public float blockedTechniqueDamageScale = 0.5f;
        public float guardMoveSpeed = 0.35f;
    }

    public static class Dash {
        public double speed = 1.35;
        public double airSpeed = 1.05;
        public int cooldown = 28;
        public int invulnerabilityTicks = 4;
        /** Ticks between ragdoll escapes (dash while launched / spiked / knocked down). */
        public int ragdollEscapeCooldown = 200;
    }

    public static class Infinity {
        public boolean enabled = true;
        /**
         * Whether Infinity is part of Gojo's active moveset (its key, its HUD slot, switching on by default and while
         * awakened). Off for now: the technique stays implemented and can be brought back by setting this to true.
         */
        public boolean inMoveset = false;
        /** Radius (blocks) at which approaching things start slowing down. */
        public double radius = 2.6;
        /** Distance at which things are stopped entirely. */
        public double stopDistance = 0.9;
        /** Cursed energy drained per second while Infinity is active. */
        public float upkeepPerSecond = 18f;
        public float costPerBlockedMelee = 12f;
        public float costPerBlockedProjectile = 5f;
        /** Cursed energy spent per point of technique damage stopped. */
        public float costPerTechniqueDamage = 6f;
        /** Fraction of explosion damage that still gets through. */
        public float explosionPassThrough = 0.25f;
        /** How long projectiles hang in the air before dropping (ticks). */
        public int projectileHangTicks = 30;
        /** Cooldown after Infinity collapses from running out of cursed energy. */
        public int collapseCooldown = 100;
        public int toggleCooldown = 10;
        /** Mobs that walk into Infinity are held back at the boundary. */
        public boolean repelWalkingEntities = true;
    }

    public static class Blue {
        public float cost = 120f;
        public int cooldown = 150;
        public double range = 16;
        public double pullRadius = 6;
        public double pullStrength = 0.34;
        public int duration = 44;
        /** Extra ticks the orb may be steered while the key is held. */
        public int maxSteerTicks = 50;
        public float steerCostPerSecond = 40f;
        public float tickDamage = 0.6f;
        public int tickDamageInterval = 5;
        public float collapseDamage = 5f;
        public int collapseStun = 20;
        /** Radius around the core in which plants, leaves and loose blocks are torn up. */
        public double blockPullRadius = 2.5;
        public boolean pullsBlocks = true;
    }

    public static class Red {
        public float cost = 150f;
        /** 20 seconds. */
        public int cooldown = 400;
        /** JJS: a short wind-up, no charging. Limitless pressed inside it sets up a special variant. */
        public int windup = 12;
        public double speed = 2.2;
        /** 40 studs. */
        public double range = 11;
        public float damage = 12.5f;
        /** 15 studs. */
        public double radius = 4.2;
        public double knockback = 2.6;
        public double launch = 0.75;
        public int hitstun = 24;
        /** Red detonating inside an active Blue triggers a bigger, amplified blast. */
        public float blueAmplifyMultiplier = 1.5f;
        public int maxBlocksDestroyed = 60;
    }

    public static class Purple {
        public float cost = 150f;
        /** 40 seconds. */
        public int cooldown = 800;
        public int blueFormTicks = 14;
        public int redFormTicks = 14;
        public int fusionTicks = 16;
        /** JJS releases Hollow Purple about 3 seconds in: after the fusion it charges this long, then fires by itself. */
        public int maxHoldTicks = 16;
        public double speed = 1.9;
        public double range = 90;
        public double radius = 3.2;
        public double chargedRadius = 4.6;
        public float damage = 70f;
        public double knockback = 3.2;
        public float impactDamage = 16f;
        public double impactRadius = 9.0;
        public boolean destroysBlocks = true;
        public int maxBlocksDestroyed = 4000;
        public float casterMoveSpeed = 0.25f;
    }

    /** Limitless' arrival (its cooldown, range and wind-up are in the gojo section). */
    public static class Teleport {
        public float cost = 30f;
        /** Degrees off the crosshair within which a target is picked. */
        public double targetAssistAngle = 9;
        public int airHoverTicks = 12;
        public int invulnerabilityTicks = 3;
    }

    public static class Awakening {
        /** Size of the meter. Every other value here is in meter points. */
        public float max = 100f;
        public float gainPerDamageDealt = 0.55f;
        public float gainPerHitLanded = 0.6f;
        public float gainPerDamageTaken = 0.35f;
        public float gainPerBlock = 0.8f;
        public float gainPerParry = 4f;
        /** Hits that connect on a guard-broken, knocked-down or launched target count extra (rewarding combos). */
        public float comboBonusMultiplier = 1.5f;
        /** Meter lost per second while awakened. 100 / 3 = about 33 seconds of Awakening if no moves are used. */
        public float drainPerSecond = 3f;
        /** Ticks of the transition (invulnerable, rooted) when awakening. */
        public int transitionTicks = 50;
        /** Cooldown before the meter starts filling again after Awakening ends (ticks). */
        public int refillDelay = 200;
        /** Keep the meter between deaths/relogs. */
        public boolean keepOnDeath = false;
    }

    public static class MaxBlue {
        public int startup = 16;
        public double range = 22;
        public double pullRadius = 18;
        public double pullStrength = 0.6;
        /** JJS: 20 damage ticks of 2.2 (44). */
        public int duration = 80;
        /** Visual scale of the anomaly relative to base Blue. */
        public float power = 5f;
        public float tickDamage = 2.2f;
        public int tickDamageInterval = 4;
        public float collapseDamage = 0f;
        public int collapseStun = 30;
        public double blockPullRadius = 7;
        /** 17 seconds. */
        public int cooldown = 340;
        /** After killing someone the orb lingers, and 19 more ticks at half damage go to everyone else it holds. */
        public int lingerTicks = 76;
    }

    public static class MaxRed {
        /** "A little over a second" of charge, then it fires by itself. */
        public int charge = 24;
        /** 100 studs, piercing; 30 damage falling to 7 at the end of its range. */
        public double range = 28;
        public double speed = 2.0;
        public float nearDamage = 30f;
        public float farDamage = 7f;
        /** Rebound variant: Black Flash on the caught target, damage to Gojo if it comes back empty. */
        public float blackFlashDamage = 10f;
        public float reboundSelfDamage = 15f;
        public float damageMultiplier = 1.8f;
        public float radiusMultiplier = 3.0f;
        public float knockbackMultiplier = 2.2f;
        public int maxBlocksDestroyed = 400;
        /** 10 seconds. */
        public int cooldown = 200;
    }

    /**
     * Temporary battle damage: every block a technique destroys or alters comes back exactly, a fixed time after that
     * block was damaged. Domain structures are separate and restore the moment their domain ends.
     */
    public static class Restoration {
        public boolean enabled = true;
        /** Per-block delay from damage to restoration. 3600 ticks = 3 minutes at 20 TPS. */
        public int delayTicks = 3600;
        /** Blocks restored per tick at most (bigger batches are spread over the following ticks). */
        public int blocksPerTick = 1500;
        /**
         * What to do when a damaged position was changed by something else (a player placed a block, a crop grew into it)
         * before it was due: "preserve" keeps that change and hands the original block back as drops, "force" puts the
         * original back regardless.
         */
        public String conflictPolicy = "preserve";
        /** Restoration also covers containers and other block entities (destroyed with their data kept, never dropped). */
        public boolean destroyBlockEntities = true;
        /** How long after a change its neighbours are watched for knock-on effects (falling sand, popped torches, water). */
        public int cascadeWatchTicks = 60;
        /** How many steps a knock-on chain is followed (a 12-high sand column needs 12). */
        public int cascadeDepth = 24;
        /** Restore paintings and item frames that hung on destroyed blocks. */
        public boolean restoreHangingEntities = true;
        /** A block damaged again before it came back restarts its timer (the original snapshot is always kept). */
        public boolean rearmOnRepeatDamage = false;
    }

    /** Domain clash: a rhythm duel. Players' timing decides it; domain strength and stats do not. */
    public static class Clash {
        /** Ticks of countdown before the first note. */
        public int countdownTicks = 50;
        /** Tempo of the chart. Notes land on beats and half beats. */
        public float bpm = 132f;
        /** Notes per participant in the main sequence. */
        public int notes = 28;
        /** Timing windows in milliseconds either side of the note. */
        public int perfectWindowMs = 45;
        public int greatWindowMs = 90;
        public int goodWindowMs = 140;
        public int scorePerfect = 100;
        public int scoreGreat = 75;
        public int scoreGood = 40;
        public int scoreMiss = 0;
        /** How far one judgement moves the shared meter (the meter runs from -1 to 1; reaching the end ends the clash). */
        public float pushPerfect = 0.075f;
        public float pushGreat = 0.05f;
        public float pushGood = 0.02f;
        public float pushMiss = -0.045f;
        /** A press with no note to hit: breaks the streak and costs a little ground. */
        public float pushGhostTap = -0.015f;
        /** Streak bonus on pushes: +streakBonusPerNote per consecutive hit, capped at streakBonusCap (0.35 = +35%). */
        public float streakBonusPerNote = 0.035f;
        public float streakBonusCap = 0.35f;
        /** Meter within this of the centre, with scores this close (fraction), counts as a tie. */
        public float tieMeter = 0.06f;
        public float tieScoreFraction = 0.02f;
        /** Tie breaking: "sudden_death" plays short extra sequences; "final_sequence" plays one longer decider. */
        public String tieMode = "sudden_death";
        public int suddenDeathNotes = 6;
        public int suddenDeathRounds = 3;
        public int finalSequenceNotes = 12;
        /** Latency allowance: how far in the past a reported input time may be (ticks) before it is judged at arrival. */
        public int maxInputLatencyTicks = 12;
        /** Ticks after a note's window before an unanswered note counts as missed (lets late packets arrive). */
        public int missGraceTicks = 6;
        /** Skill of non-player participants (0 = misses everything, 1 = frame-perfect). */
        public float botSkill = 0.6f;
        /** Participants can't move, act or be hit while they duel. */
        public boolean freezeParticipants = true;
    }

    public static class Domain {
        public float cost = 0f;
        public int cooldown = 200;
        public int startup = 28;
        /** About 14 seconds, like the reference game. */
        public int duration = 280;
        /**
         * The hard ceiling on any domain's whole life (forming, active, clashing; 4 minutes). Whatever happens (a clash
         * that never settles, a huge configured duration), it collapses by then.
         */
        public int maxLifetimeTicks = 4800;
        /** Time a domain may spend clashing on top of its own duration before the ceiling ends it anyway. */
        public int clashAllowanceTicks = 1200;
        /**
         * Counter window: while someone nearby starts opening a domain, a sorcerer with a full Awakening meter can press
         * their Awakening button within this many ticks to awaken instantly and answer with their own domain.
         */
        public int counterWindowTicks = 36;
        /** How close (blocks) a sorcerer must be to counter an opening domain. */
        public double counterRange = 34;
        /** Players within this many blocks of an opening domain see the (light) observer presentation. */
        public double observerRange = 64;
        /** Ticks the physical domain takes to build itself, from the caster's feet to the final seal. */
        public int formationTicks = 44;
        public double radius = 18;
        public int sureHitDamageInterval = 20;
        public float sureHitDamage = 1.0f;
        /** Damage multiplier on the owner's hits against overloaded targets (information overload leaves them defenceless, not dead). */
        public float overloadedDamageScale = 0.75f;
        /** Ticks victims stay overloaded after leaving or the domain ending. */
        public int lingeringOverload = 30;
        /** The barrier prevents entities from leaving (and outsiders from entering). */
        public boolean closedBarrier = true;
        /** Draw a floor inside the domain so the battlefield visibly changes. */
        public boolean voidFloor = true;
        /** Build a physical block structure (sealed sphere + floor). Everything replaced is restored exactly afterwards. */
        public boolean physicalStructure = true;
        /** Clear the interior above the floor so the battlefield becomes the void. */
        public boolean clearInterior = true;
        /** Blocks placed per tick while the structure forms (and restored per tick while it collapses). */
        public int blocksPerTick = 3000;
        /** Thickness of the barrier shell in blocks. */
        public int shellThickness = 2;
    }

    public static class Client {
        /** 0 = minimal, 1 = reduced, 2 = full, 3 = extreme */
        public int particleQuality = 2;
        /** Which default key layout the player's binds were last moved to (2 = the JJS layout). */
        public int controlsLayout = 0;
        public boolean screenShake = true;
        public float screenShakeScale = 1.0f;
        public boolean screenFlashes = true;
        public boolean fovEffects = true;
        public boolean overloadOverlay = true;
        public float soundVolume = 1.0f;
        public boolean showHud = true;
        public boolean showComboCounter = true;
        /**
         * Combat mode (true) or Vanilla Minecraft mode (false). In vanilla mode every custom HUD element is hidden and
         * none of this mod's ability keys, melee or clash inputs do anything, so ordinary play can't trigger techniques.
         */
        public boolean combatMode = true;
        /** The small "current / max CE" readout beside the CE bar. */
        public boolean showCeNumbers = true;
    }

    /**
     * Gojo (Honored One), tuned after the Jujutsu Shenanigans wiki: its seconds in ticks, its studs in blocks (about 3.6
     * studs to a block), its damage as written.
     */
    public static class Gojo {
        /** A target at or under this share of max health is finished off by a finisher. */
        public float finisherThreshold = 0.2f;
        /** Six Eyes: 25 HP (of 100) healed on awakening, and the Awakening lasts 60 seconds. */
        public float awakenHealShare = 0.25f;
        public int awakeningSeconds = 60;

        // Timings below follow the wiki's GIFs of each move (recorded at 50 fps: 2.5 frames to a tick).
        // --- 1: Lapse Blue (13s) — pull within 35 studs, suspend, unblockable kick ---
        public int blueCooldown = 260;
        public int blueWindup = 11;
        public double blueRange = 9.7;
        public float bluePullDamage = 5f;
        public float blueKickDamage = 7.5f;
        public int bluePullTicks = 6;
        public int blueSuspendTicks = 16;
        public double blueKickKnockback = 1.5;

        // --- 3: Rapid Punches (15s) — spin kick lock, 15 punches, 3 heavy, final blow ---
        public float punchesCost = 80f;
        public int punchesCooldown = 300;
        public int punchesWindup = 11;
        public double punchesReach = 3.0;
        public float punchesGrabDamage = 1.25f;
        public int punchesBarrage = 15;
        public float punchesBarrageDamage = 0.5f;
        public int punchesHeavy = 3;
        public float punchesHeavyDamage = 1.5f;
        public float punchesFinalDamage = 4f;
        public double punchesFinalKnockback = 1.5;
        /** Face Grater: Limitless right after Rapid Punches lands (this many ticks to press it). */
        public int faceGraterWindow = 16;
        public float faceGraterDamage = 10.2f;
        public int faceGraterDragTicks = 14;

        // --- 4: Twofold Kick (18s) ---
        public float twofoldCost = 70f;
        public int twofoldCooldown = 360;
        public int twofoldWindup = 8;
        public double twofoldReach = 3.0;
        public float twofoldFirstDamage = 6f;
        public float twofoldSecondDamage = 4f;
        public int twofoldAnchorTicks = 14;
        /** The finisher's point-blank Red. */
        public float twofoldRedDamage = 10f;

        // --- R: Limitless (15s) ---
        public int limitlessCooldown = 300;
        public double limitlessRange = 32;
        /** Hand raised until the glass shatters: turning the camera in this time picks where Gojo appears. */
        public int limitlessWindup = 11;
        /** 6% of the Awakening meter, in the base kit. */
        public float limitlessMeterCost = 6f;
        public float limitlessAirKickDamage = 8f;

        // --- Reversal Red special variants ---
        public float redAirKickDamage = 5f;
        public float redInterruptDamage = 15f;
        public int redInterruptStun = 30;

        /** Infinite Void: 120 seconds (it lasts 14, see domain.duration). */
        public int infiniteVoidCooldown = 2400;

        // --- 0.2 Domain: Special during the Awakening sequence ---
        public double zeroTwoRadius = 40;
        /** Exposed to the sure hit for 7 seconds. */
        public int zeroTwoStun = 140;
        public int zeroTwoPhase1Hits = 7;
        public float zeroTwoPhase1Damage = 5f;
        public int zeroTwoPhase2Hits = 6;
        public float zeroTwoPhase2Damage = 20f;
        public float zeroTwoFinalDamage = 65f;

        // --- Unlimited Purple: Red MAX into the orb Lapse Blue MAX left behind ---
        public int unlimitedPurpleFuse = 60;
        /** Blast radius: three times the original 16. Damage, knockback, the dome and the crater (0.8 of it) all follow it. */
        public double unlimitedPurpleRadius = 48;
        public float unlimitedPurpleMinDamage = 50f;
        public float unlimitedPurpleMaxDamage = 100f;
        /** Crater cap (carved over several ticks within maxBlocksPerTick). */
        public int unlimitedPurpleMaxBlocks = 18000;
    }

    /** Kinji Hakari / Restless Gambler. Every number here can be rebalanced without code changes. */
    /**
     * Hakari (Restless Gambler), tuned after the Jujutsu Shenanigans wiki. Cooldowns and durations are the wiki's seconds
     * in ticks; distances are its studs in blocks (about 3.6 studs to a block).
     */
    public static class Hakari {
        public float maxCursedEnergy = 900f;
        public float regenPerSecond = 20f;
        /** A target at or under this share of max health is finished off by a finisher move. */
        public float finisherThreshold = 0.2f;

        // --- 1: Reserve Balls (12s) ---
        public float ballsCost = 45f;
        public int ballsCooldown = 240;
        /** Wind-up before the flick: Shutter Doors pressed in this window sets up the doors combination. */
        public int ballsWindup = 7;
        public double ballSpeed = 1.8;
        /** 65 studs; ricochets count toward it. Inside Hakari's domain it keeps going this many times longer. */
        public double ballRange = 18;
        public double ballDomainRangeMultiplier = 3;
        /** Within 15 studs the ball ragdolls instead of stunning. */
        public double ballRagdollRange = 4.2;
        public float ballDamage = 7.5f;
        public int ballHitstun = 16;
        public double ballRagdollKnockback = 1.3;
        /** The doors combination: the doors' hit and each bounce of a stunned target on them. */
        public float comboDoorDamage = 3f;
        public float doorBounceDamage = 2f;
        public int doorBounces = 3;

        // --- 2: Shutter Doors (15s) ---
        public float shutterCost = 70f;
        public int shutterCooldown = 300;
        /** 25 studs. */
        public double shutterRange = 7;
        public int shutterRiseTicks = 5;
        public int shutterCloseTicks = 3;
        public int shutterHoldTicks = 12;
        public float shutterDamage = 8f;
        public int shutterHitstun = 24;
        /** Missed doors linger this long (7s): jump on them to bounce high, or a ragdolled enemy bounces on them. */
        public int shutterLingerTicks = 140;
        public double shutterBounceLaunch = 1.35;
        public float shutterMissDamage = 6f;

        // --- 3: Rough Energy (14s) ---
        public float roughCost = 80f;
        public int roughCooldown = 280;
        public int roughWindup = 16;
        public float roughDamage = 12.5f;
        public double roughReach = 3.4;
        public double roughKnockback = 1.7;
        public int roughHitstun = 20;
        /** In the air: a hover, then a stomp whose shockwave launches everyone around upward. */
        public float roughStompDamage = 8f;
        public double roughStompRadius = 3.6;
        /** Stomping from higher than a jump: unblockable and double damage. */
        public double roughHighAirHeight = 2.5;

        // --- 4: Fever Breaker (23s) ---
        public float feverCost = 90f;
        public int feverCooldown = 460;
        public int feverWindup = 5;
        public float feverKickDamage = 5f;
        /** Ticks the target hangs in front of the doors before the dropkick. */
        public int feverSuspendTicks = 12;
        public float feverFinishDamage = 10f;
        public double feverFinishKnockback = 2.0;
        /** Fever Crush (Shutter Doors during the wind-up): the doors' hold, the axe kick, and the axe kick on a ragdolled target. */
        public float crushDoorDamage = 8f;
        public float crushStompDamage = 12f;
        public float crushRagdollStompDamage = 24f;

        // --- Special: Door Guard (16s) ---
        public int doorGuardMaxTicks = 40;
        public int doorGuardCooldown = 320;
        /** 0.6s: a melee hit in this window is punched back through the doors. */
        public int doorGuardCounterWindow = 12;
        public float doorGuardCounterDamage = 5f;
        public int doorGuardCounterStun = 30;
        public float doorGuardBlockCost = 10f;

        // --- Idle Death Gamble ---
        public double domainRadius = 14;
        /** 80s, though it breaks after its last scenario. */
        public int domainDuration = 1600;
        public int domainFormationTicks = 44;
        public int domainStartup = 26;
        public float domainCost = 200f;
        public int domainCooldown = 900;
        /** Everyone caught is frozen in place this long while the rules are imparted. */
        public int domainFreezeTicks = 30;
        /** Healing on cast (15 of 100 HP in JJS), as a share of max health. */
        public float domainHealShare = 0.15f;
        /** Visual moves needed before a Riichi scenario. */
        public int visualMovesRequired = 2;
        /** Scenarios per domain; the last one is the pity jackpot if anyone was caught. */
        public int maxAttempts = 4;
        public int riichiTicks = 76;
        public int missTicks = 24;
        /** Odds of each scenario (Transit Card one star, Travel Emergency two) and how often Travel Emergency is drawn. */
        public float transitCardOdds = 0.3f;
        public float travelEmergencyOdds = 0.5f;
        public float travelEmergencyChance = 0.4f;
        /** Chance a Riichi plays the rainbow (a certain jackpot). Not in JJS: off by default. */
        public float rainbowChance = 0f;
        /** Extra odds on a forced attempt as the domain runs out. */
        public float finalAttemptBonus = 0.1f;
        /** Share of the Awakening meter refunded when the domain ends without a jackpot (none in JJS). */
        public float missRefund = 0f;
        /** After an odd jackpot: extra odds on the next domain's Riichi. After an even one its Riichi play twice as fast. */
        public float oddJackpotBonus = 0.25f;
        /** Renewal: pressing Reserve Balls again within this long after a ball lands rewinds to that moment (8s). */
        public int renewalWindow = 160;

        // --- Jackpot ---
        /** 100s (half after a pity jackpot). */
        public int jackpotSeconds = 100;
        /** Reverse Cursed Technique: share of max health healed per second. */
        public float jackpotRegenShare = 0.08f;
        /** Damage taken drains the Jackpot meter: it empties after this many times max health (333 of 100 HP). */
        public float jackpotHitDrainHealths = 3.33f;
        /** Surviving to the end of a Jackpot refunds this much Awakening, and this much more for each consecutive Jackpot. */
        public float jackpotRefund = 0.4f;
        public float jackpotRefundChain = 0.25f;

        // --- Jackpot 1: Lucky Volley (10s) ---
        public int volleyCooldown = 200;
        public float volleyOpenerDamage = 2.3f;
        public int volleyFlurryHits = 8;
        public float volleyFlurryDamage = 2.3f;
        public float volleyFinalDamage = 8f;
        public double volleyFinalKnockback = 1.8;

        // --- Jackpot 2: Lucky Rushdown (15s) ---
        public int rushdownCooldown = 300;
        public int rushdownRunTicks = 22;
        public double rushdownSpeed = 1.05;
        public int rushdownDragTicks = 14;
        public float rushdownGrabDamage = 14f;
        public float rushdownThrowDamage = 10f;
        public double rushdownThrowKnockback = 2.2;
        /** Finisher: a longer drag, then the target is hurled up and Hakari leaps after them. */
        public int rushdownFinisherDragTicks = 26;

        // --- Jackpot 3: Overwhelming Luck (20s) ---
        public int overwhelmCooldown = 400;
        public float overwhelmOpenerDamage = 10f;
        public int overwhelmPunches = 6;
        public int overwhelmInterval = 4;
        public float overwhelmPunchDamage = 3f;
        public float overwhelmFinalDamage = 12f;
        public double overwhelmFinalKnockback = 2.6;

        // --- Jackpot 4: Energy Surge (25s) ---
        public int surgeCooldown = 500;
        public int surgeDashTicks = 7;
        public float surgeDashDamage = 10f;
        public float surgeKickDamage = 10f;

        // --- Jackpot special: Rhythm (8s) ---
        public int rhythmCooldown = 160;
        public int rhythmBeats = 4;
        /** Ticks between beats (10 = 120 BPM). */
        public int rhythmBeatTicks = 10;
        public int rhythmLeadIn = 14;
        /** Each finished dance: a stack of speed (moves and special), and every cooldown finishes this much sooner. */
        public int rhythmMaxStacks = 5;
        public float rhythmSpeedPerStack = 0.08f;
        public int rhythmCooldownCut = 12;
    }

    /**
     * Yuji Itadori / Vessel, and Sukuna's King of Curses, after the Jujutsu Shenanigans wiki. Damage is the wiki's (out of
     * 100 HP), cooldowns and durations its seconds in ticks, distances its studs in blocks (about 3.6 studs to a block);
     * timings follow the wiki's GIFs (50 fps: 2.5 frames to a tick).
     */
    public static class Yuji {
        public float maxCursedEnergy = 800f;
        public float regenPerSecond = 20f;
        /** Vessel has 85 max HP (of the usual 100). */
        public float maxHealthShare = 0.85f;
        public float finisherThreshold = 0.2f;

        // --- 1: Cursed Strikes (14s) ---
        public int strikesCooldown = 280;
        public float strikesCost = 60f;
        public int strikesWindup = 9;
        public int strikesSlideTicks = 12;
        public double strikesSlideSpeed = 0.95;
        public float strikesGrabDamage = 4f;
        public int strikesPunches = 6;
        public int strikesPunchInterval = 5;
        public float strikesPunchDamage = 1.75f;
        public float strikesKickDamage = 3f;
        /** The calf kick leaves them stunned in place. */
        public int strikesKickStun = 22;
        /** Front dash is disabled this long after it lands. */
        public int strikesFrontDashLock = 30;
        /** Airborne: a dropkick straight down at the ground that grounds whoever it lands on. */
        public float strikesAirDamage = 14f;
        public double strikesAirSpeed = 1.3;
        public int strikesAirMaxTicks = 60;
        public float strikesFinisherKickDamage = 1.75f;
        public float strikesFinisherLaunchDamage = 3f;

        // --- 2: Crushing Blow (15s) ---
        public int crushingCooldown = 300;
        public float crushingCost = 70f;
        public int crushingWindup = 7;
        public double crushingReach = 3.0;
        public float crushingSlamDamage = 6f;
        public float crushingShockwaveDamage = 3f;
        public double crushingShockwaveRadius = 3.4;
        public double crushingLaunch = 1.3;
        /** Airborne: a dash across the air toward the target (this far) before the grab. */
        public double crushingAirDash = 8;

        // --- 3: Divergent Fist (18s) ---
        public int divergentCooldown = 360;
        public float divergentCost = 70f;
        public int divergentWindup = 10;
        public double divergentReach = 3.2;
        public float divergentPunchDamage = 5f;
        public float divergentImpactDamage = 5f;
        /** The cursed energy lags this long behind the punch. */
        public int divergentImpactDelay = 5;
        public int divergentInterruptStun = 26;
        /** Black Flash: pressed again while his body flashes white (these ticks of the wind-up). */
        public int blackFlashWindowStart = 5;
        public int blackFlashWindowEnd = 9;
        public float blackFlashDamage = 10f;
        public float blackFlashChainDamage = 7f;
        public float blackFlashFourthDamage = 15f;
        public int blackFlashChainMax = 4;
        /** Stun on a chained (from behind) Black Flash: long enough for the next one. */
        public int blackFlashChainStun = 36;
        /** Chain window: the next Divergent Fist within this long continues the chain. */
        public int blackFlashChainWindow = 60;

        // --- 4: Manji Kick (20s) ---
        public int manjiCooldown = 400;
        public float manjiCost = 50f;
        public int manjiWindow = 12;
        public float manjiDamage = 8.5f;
        public float manjiSlamDamage = 4f;

        // --- Special: Combat Instincts (2s) ---
        public int instinctsCooldown = 40;
        /** 3% of the Awakening meter when there is any (not required, except to throw). */
        public float instinctsMeterCost = 3f;
        public double throwableRange = 3.2;
        public float throwableDamage = 15f;
        public double throwableSpeed = 1.6;

        // --- King of Curses (Awakening, 60s) ---
        public int awakeningSeconds = 60;
        /** 45 HP healed (of Vessel's 85). */
        public float awakenHeal = 45f;
        public int awakenTicks = 44;
        /** Shrine: basic attacks reach 24 studs instead of 8. */
        public float shrineRangeMultiplier = 3f;

        // --- Special: Cleave (12s) ---
        public int cleaveCooldown = 240;
        public int cleaveWindup = 6;
        public double cleaveReach = 3.2;
        /** Grabbed, a pause, then the slashes. */
        public int cleavePause = 16;
        public float cleaveShare = 0.4f;
        public float cleaveMinDamage = 10f;
        public double cleaveKnockback = 1.8;

        // --- 1: Dismantle (13s) ---
        public int dismantleCooldown = 260;
        public int dismantleWindup = 8;
        /** 30 studs. */
        public double dismantleRange = 8.3;
        public float dismantleDamage = 17.5f;
        public float dismantleBlockedDamage = 10f;
        public int dismantleSlashes = 5;
        /** Airborne: hover, flip, one long slash (explosion) down the line. */
        public float dismantleAirDamage = 25f;
        public double dismantleAirLength = 16;
        public int dismantleAirWindup = 20;

        // --- World Cutting Slash: Rush during Dismantle's wind-up, then Open, then Cleave ---
        public int worldSlashLineTicks = 17;
        /** Each line waits this long for the next press before the chant falls apart. */
        public int worldSlashChantWindow = 40;
        public float worldSlashDamage = 80f;
        public double worldSlashLength = 30;
        public double worldSlashWidth = 18;

        // --- 2: Open (40s) ---
        public int openCooldown = 800;
        /** Fire in the hands, the clap, the arrow drawn: fired this many ticks in. */
        public int openWindup = 42;
        public double openSpeed = 2.4;
        public double openRange = 60;
        public float openDamage = 30f;
        public double openPillarRadius = 4.5;
        /**
         * The blast round the pillar: everyone within this many blocks of where the arrow lands is caught (full damage in
         * the pillar, falling off to {@code openBlastEdgeDamage} at the edge). Unlimited Purple's original radius, 16.
         */
        public double openBlastRadius = 16;
        public float openBlastEdgeDamage = 8f;
        /** The crater Open blows out of the terrain: the whole blast radius, up to this many blocks, this hard. */
        public int openBlastMaxBlocks = 18000;
        public float openBlastMaxHardness = 50f;
        public double openLift = 1.4;

        // --- 3: Rush (15s) ---
        public int rushCooldown = 300;
        public int rushTicks = 12;
        public double rushSpeed = 1.6;
        public float rushImpactDamage = 5f;
        public float rushKneeDamage = 15f;
        public float rushSlamDamage = 5f;

        // --- 4: Malevolent Shrine (120s cooldown, 18s) ---
        public int shrineCooldown = 2400;
        public float shrineCost = 200f;
        public int shrineStartup = 26;
        public double shrineRadius = 18;
        public int shrineDuration = 360;
        public int shrineFormationTicks = 40;
        /** 109 slashes over the 18 seconds, 2 each (0.5 through a guard, which also can't be executed). */
        public int shrineSlashInterval = 3;
        /** The shrine rises and its colour floods in before the slashes begin (GIF). */
        public int shrineRevealTicks = 26;
        public float shrineSlashDamage = 2f;
        public float shrineBlockedDamage = 0.5f;
    }

    /**
     * Yuta Okkotsu and Rika (Cursed Partners), after the Jujutsu Shenanigans wiki: damage as written (of a 100 HP
     * scale), cooldowns and durations its seconds in ticks, distances its studs in blocks (about 3.6 studs to a block).
     */
    public static class Yuta {
        public float maxCursedEnergy = 800f;
        public float regenPerSecond = 20f;
        /** Cursed Partners has 90 max HP (of the usual 100). */
        public float maxHealthShare = 0.9f;
        public float finisherThreshold = 0.2f;
        /** Swordsmanship: the katana goes back in its holster after 8 seconds without a katana move or M1. */
        public int katanaHolsterTicks = 160;

        // --- 1: Severing Path (15s) — slide 18 studs sweeping, lock, 3 swings, the last launches ---
        public int severingCooldown = 300;
        public float severingCost = 60f;
        public int severingWindup = 6;
        public int severingSlideTicks = 9;
        public double severingSlide = 5.0;
        public float severingSweepDamage = 4f;
        public float severingSwingDamage = 2.3f;
        /** Veilstep: a back roll of 27 studs, melee i-frames, launching whoever is in the way. */
        public double veilstepDistance = 7.5;
        public int veilstepTicks = 12;
        public float veilstepDamage = 9f;

        // --- 2: Resolute Slash (15s) — vanish, reappear at a spot within 25 studs, slash at the neck ---
        public int resoluteCooldown = 300;
        public float resoluteCost = 60f;
        public double resoluteRange = 7.0;
        public int resoluteVanishTicks = 10;
        public float resoluteDamage = 12f;
        /** The window to use it again as he reappears (Resolute Black Flash). */
        public int resoluteAgainWindow = 6;
        public float resoluteBlackFlashDamage = 12f;

        // --- 3: Outburst (16s) — swing 2, burst 4 (+2 per stage, up to +6), 13 stud radius (+2 per stage) ---
        public int outburstCooldown = 320;
        public float outburstCost = 60f;
        public int outburstWindup = 8;
        public int outburstStageTicks = 12;
        public float outburstSwingDamage = 2f;
        public float outburstBurstDamage = 4f;
        public float outburstStageDamage = 2f;
        public double outburstRadius = 3.6;
        public double outburstStageRadius = 0.55;
        /** Parry: hit within 0.25 s of using it. */
        public int outburstParryTicks = 5;
        public int outburstParryCooldown = 80;
        public int outburstParryStun = 30;
        /** A melee parry takes 2 s off Rika's cooldowns. */
        public int outburstRikaRefund = 40;

        // --- 4: Second Wind (16s) — rush 20 studs, grab 2, slam 8; a second try if it whiffs ---
        public int secondWindCooldown = 320;
        public float secondWindCost = 60f;
        public double secondWindDistance = 5.5;
        public int secondWindTicks = 10;
        public float secondWindGrabDamage = 2f;
        public float secondWindSlamDamage = 8f;
        /** The Severing Path variant: a pummelling, 3 per hit, five hits. */
        public float secondWindPummelDamage = 3f;

        // --- Special: Rika ---
        public float rikaSummonCost = 40f;
        /** Base Rika's moves share one cooldown: 10 s (6 s for a Rika Launch feint). */
        public int rikaSmashCooldown = 200;
        public int rikaLaunchCooldown = 200;
        public int rikaLaunchFeintCooldown = 120;
        public int rikaHaymakerCooldown = 200;
        public float rikaSmashDamage = 10f;
        public float rikaSmashAirDamage = 8f;
        public float rikaHaymakerDamage = 12f;
        public float rikaHaymakerBlockedDamage = 18f;
        /** Haymaker: she hovers over to a target within 10 studs of her. */
        public double rikaHaymakerReach = 2.8;
        /** How far she reaches for a target when she attacks (she flies there). */
        public double rikaAttackRange = 14;
        public double rikaLaunchSpeed = 1.6;

        // --- Awakening: True Love (60 s, heals 25) ---
        public int awakeningSeconds = 60;
        public float trueLoveHeal = 25f;
        public int trueLoveTicks = 60;

        // --- Awakened 1: Elbow Rush (15s) — dash 38.5 studs; elbow 4, flurry 5 (8 with Rika), last 6 ---
        public int elbowCooldown = 300;
        public double elbowDistance = 10.7;
        public int elbowDashTicks = 10;
        public float elbowDamage = 4f;
        public float elbowFlurryDamage = 5f;
        public float elbowFlurryRikaDamage = 8f;
        public float elbowFinalDamage = 6f;

        // --- Awakened 2: Copy (15s; 25s for an Awakening move) ---
        public int copyCooldown = 300;
        public int copyAwakenedCooldown = 500;
        /** Cursed Speech, "Don't move!": everyone within 35 studs, 2.5 s. */
        public double speechRadius = 9.7;
        public int speechStun = 50;
        public int copySlots = 8;

        // --- Awakened 3: Energy Ripple (18s) — 19, a 27 stud field; Fakeout 7 + 12 ---
        public int rippleCooldown = 360;
        public int rippleWindup = 14;
        public double rippleRadius = 7.5;
        public float rippleDamage = 19f;
        public float fakeoutSwingDamage = 7f;
        public float fakeoutBurstDamage = 12f;

        // --- Awakened 4: Authentic Mutual Love (120s, 45s) ---
        public int domainCooldown = 2400;
        public float domainCost = 200f;
        /** "A longer windup than normal". */
        public int domainStartup = 36;
        public double domainRadius = 18;
        public int domainDuration = 900;
        public int domainFormationTicks = 40;
        /** Four blades land within reach; another falls after every pickup. */
        public int domainBlades = 4;
        public double bladePickupRange = 1.6;
        /** With a blade he runs about 55 studs before he swings. */
        public double bladeRun = 15;
        public int bladeRunTicks = 16;
        public float bladeDamage = 8f;
        public float shrineSlashDamage = 5f;
        public float shrineMissDamage = 20f;
        public float thinIceDamage = 20f;
        public float thinIceMissDamage = 15f;
        public int clairvoyanceTicks = 200;
        public int clairvoyanceMissTicks = 100;
        public float plummetDamage = 15f;
        public int stopTicks = 60;
        public float shikigamiDamage = 27f;
        public int shikigamiTicks = 60;
        /** Jacob's Ladder: unlocked by 4 direct katana swings; 62.5, drains 35% of the Awakening meter (50% if awakened). */
        public int ladderHits = 4;
        public float ladderDamage = 62.5f;
        public float ladderDrain = 0.35f;
        public float ladderDrainAwakened = 0.5f;
        public int ladderTicks = 60;

        // --- Awakened Rika (separate cooldowns) ---
        public int downslamCooldown = 260;
        public float downslamDamage = 8f;
        public float downslamSecondDamage = 4f;
        public float downslamAirDamage = 8f;
        public int rikaSlamCooldown = 260;
        public float rikaSlamGrabDamage = 1f;
        public float rikaSlamDamage = 2f;
        public float rikaSlamLastDamage = 3f;
        // True Love Beam (40s): Yuta conjures the orb (aiming), Rika plants herself and charges, then the beam.
        public int beamCooldown = 800;
        public int beamQuickCooldown = 300;
        public float beamCost = 120f;
        /** Ticks Yuta spends conjuring the orb, aiming; after this the path is locked and he can move. */
        public int beamConjureTicks = 22;
        /** Ticks from the start until the beam fires (the rest of it is Rika's charge, the path telegraphed). */
        public int beamWindup = 60;
        /** How long the blast holds at full size once it appears (exactly 5 seconds); the collapse comes after, harmless. */
        public int beamTicks = 100;
        public int beamGrowTicks = 3;
        public int beamCollapseTicks = 8;
        public double beamRange = 44;
        /** Half the beam's square side: 2.5 makes the visible torrent and its hitbox 5 blocks wide and 5 tall. */
        public double beamRadius = 2.5;
        /** Damage the first time the beam catches someone, then every {@code beamDamageInterval} ticks they stay in it. */
        public float beamDamage = 20f;
        public float beamTickDamage = 8f;
        public int beamDamageInterval = 10;
        /** How far each tick of contact carries someone along it (blocks a tick): it bores them along, it doesn't fling them. */
        public double beamPush = 0.35;
        /** The square path it carves (temporary, restored like all battle damage) and the ragged edge round it. */
        public float beamMaxHardness = 60f;
        public double beamCarveEdge = 1.2;
        /** The quick beam: the orb let go from his hands at once (pressed again in the wind-up, or Rika is busy). */
        public int beamQuickWindup = 8;
        public int beamQuickTicks = 7;
        public double beamQuickRange = 28;
        public double beamQuickRadius = 0.9;
        public float beamQuickDamage = 22.4f;
        public int throwCooldown = 260;
        public int throwMaxAirtime = 26;
        public double throwSpeed = 1.5;
        public float throwMinDamage = 8f;
        public float throwMaxDamage = 18f;
        public float throwMissMinDamage = 0.5f;
        public float throwMissMaxDamage = 22f;
    }

    /**
     * Ryu Ishigori (JJS "True Cannon"), from the Jujutsu Shenanigans wiki: its seconds in ticks, its studs in blocks
     * (about 3.6 studs to a block), its damage as written.
     */
    public static final class Ryu {
        public float maxCursedEnergy = 900f;
        public float regenPerSecond = 22f;
        public float finisherThreshold = 0.2f;
        /** Every Last Drop's awakening: 90 seconds. */
        public int awakeningSeconds = 90;
        // Overheat (0-100): what each discharge adds, where it shuts things off.
        public float heatM1 = 10f, heatGranite = 20f, heatGraniteHeld = 40f, heatAppetizer = 10f;
        public float m1RayHeatLimit = 90f;
        /** Every Last Drop awakens him only if he fired it with the meter in [awakenFrom, 100). */
        public float awakenFrom = 80f;
        public float heatCoolPerSecond = 0f;
        // Cursed Energy Discharge: 3 hits (3 + 3 + 4), the neutral third a 24-stud ray for 8.
        public float m1Damage = 3f, m1FinisherDamage = 4f, m1RayDamage = 8f;
        public double m1RayRange = 6.7;
        // Granite Blast: 0.5s; 78.5 studs, 5.5, stuns the first it meets; held 1.1s: 100 studs, piercing, unblockable,
        // ragdolling, 12 falling to 5.5 with distance; during a front dash: a looping blast into a second dash (4, 6s).
        public int graniteCooldown = 10;
        public int graniteHoldTicks = 22;
        public double graniteRange = 21.8, graniteHeldRange = 27.8;
        public float graniteDamage = 5.5f, graniteHeldDamage = 12f, graniteHeldMinDamage = 5.5f;
        public int graniteStun = 18;
        public int graniteDashCooldown = 120;
        public float graniteDashDamage = 4f;
        public int graniteDashWindow = 8;
        // Unsatisfied: 20s; three quick blows (3 each), the back clash (3) and the toss (6).
        public int unsatisfiedCooldown = 400;
        public float unsatisfiedHit = 3f, unsatisfiedClash = 3f, unsatisfiedToss = 6f;
        public int unsatisfiedRestyleRefund = 10;
        // Second Helping: 15s; a target within 70 studs, a dash over them and a slam (12); airborne: punch 6, impact 6.
        public int secondHelpingCooldown = 300;
        public double secondHelpingRange = 19.5;
        public float secondHelpingDamage = 12f, secondHelpingAirPunch = 6f, secondHelpingAirImpact = 6f;
        public int secondHelpingRestyleRefund = 60;
        // Appetizer: 18s; two blasts (4, 80 studs, stun) and the vertical ray (8, 60 studs, ragdolls them up and in).
        public int appetizerCooldown = 360;
        public double appetizerRange = 22.2, appetizerRayRange = 16.7;
        public float appetizerBlast = 4f, appetizerRay = 8f;
        // Restyle: 17s; -60% (1s) or, overheated, the comb: -100% (2.75s); awakened: +10 HP, +10% meter (2s).
        public int restyleCooldown = 340;
        public float restyleCool = 60f;
        public int restyleTicks = 20, restyleCombTicks = 55, restyleAwakenedTicks = 40;
        public float restyleHeal = 10f, restyleMeter = 0.1f;
        // Every Last Drop.: 104 (less the more it catches, and the farther), 100% overheat; awakening heals 25.
        public int eldCharge = 50;
        public int eldTicks = 40;
        /** Its length, and the half-width of its square: the same five-by-five as True Love Beam. */
        public double eldRange = 46, eldRadius = 2.5;
        public float eldDamage = 104f, eldMinDamage = 45f, eldHeal = 25f;
        public int eldCooldown = 600;
        public float eldCost = 150f;
        // Decadence: the meter takes the blows once he is critical (200 damage' worth); feints cost 5 HP (6s cooldown).
        public float decadenceCritical = 0.3f;
        public float decadenceMeterHealth = 200f;
        public float feintCost = 5f;
        public int feintCooldown = 120;
        // "What are you after?": 18s; slam 10, punch 20, launch 5, 5 to himself.
        public int afterCooldown = 360;
        public float afterSlam = 10f, afterPunch = 20f, afterLaunch = 5f, afterSelf = 5f;
        // "I had no idea...": 15s; grab 3, seven punches of 2, push 3; 9 to himself.
        public int noIdeaCooldown = 300;
        public float noIdeaGrab = 3f, noIdeaPunch = 2f, noIdeaPush = 3f, noIdeaSelfGrab = 2f, noIdeaSelfPunch = 1f;
        public int noIdeaWindup = 18;
        // "This is what dessert is like!": 20s; kick 7, swing 3, four blows of 5, push 6; 10 to himself.
        public int dessertCooldown = 400;
        public float dessertKick = 7f, dessertSwing = 3f, dessertBlow = 5f, dessertPush = 6f, dessertSelf = 2f;
        // "You weren't invited.": 20s; 20, 40 held 1.9s; a wall's debris flies 100 studs (250 held), 20 a wall.
        public int invitedCooldown = 400;
        public int invitedHoldTicks = 38;
        public float invitedDamage = 20f, invitedWallDamage = 20f;
        public double invitedWallRange = 27.8, invitedWallHeldRange = 69.4;
    }

    /**
     * Survival progression: Survival players start with no technique and earn kits in the world, each kit belonging to one
     * player per world. Creative stays the free-select sandbox. Turning {@link #enabled} off brings back free selection
     * everywhere (the ownership registry is kept, just not enforced).
     */
    public static final class Progression {
        public boolean enabled = true;
        /** Cursed Soul Sand: the chance each bone block standing on soul sand/soil turns the block under it, and a per-chunk cap. */
        public float cursedSoulSandChance = 0.18f;
        public int cursedSoulSandPerChunk = 4;
        /** Ticks the cauldron ritual takes from the glasses landing in the energy to the Cursed Glasses rising out. */
        public int infusionTicks = 60;
        /** How long (seconds) a curse remembers someone it turned hostile on after it last had them as a target. */
        public int curseHostilityMemorySeconds = 600;
        /** Battle rooms under Woodland Mansions and Igloos. */
        public boolean mansionBattleRooms = true;
        public boolean iglooBattleRooms = true;
        /** The Finger Bearer, the curse waiting in each battle room: its health and a scale on every hit it lands. */
        public double fingerBearerHealth = 300;
        public float fingerBearerDamage = 1f;
    }

    /**
     * Mastery's economy. Each node's own cost is in its tree's JSON ({@code data/jjk/mastery}); these scale the whole
     * economy at once, and set how quickly repeated kills of the same grade stop paying (fatigue).
     */
    public static final class MasteryRules {
        /**
         * The Mastery trees. Off (the default): no trees, no Mastery earned, and every cursed tool comes whole (every
         * move and every upgrade, the rifle's beam at Maximum Output). On: tools are developed through their trees.
         */
        public boolean enabled = false;
        /** Every node's cost is multiplied by this. */
        public double costMultiplier = 1.0;
        /** Every exorcism's Mastery is multiplied by this. */
        public double rewardMultiplier = 1.0;
        /** Each exorcism of a grade adds this much fatigue to it; a kill pays {@code 1 / (1 + fatigue)}. */
        public double fatiguePerKill = 0.35;
        /** Fatigue recovered per real minute (all grades). */
        public double fatigueRecoveryPerMinute = 0.5;
        /** A curse tied to an investigation pays at least this share even when fatigued. */
        public double incidentFloor = 0.6;
        /** Investigations: how many open reports a village board shows, and how far (blocks) an incident may lie. */
        public int reportsPerBoard = 3;
        public int incidentMinDistance = 90;
        public int incidentMaxDistance = 420;
        /** Village news boards are placed by the bell of a village a player walks into. */
        public boolean newsBoards = true;
    }

    /**
     * The Cursed Rifle. Its ammunition is its own reserve of cursed energy ({@code capacity}, refilling over time), never a
     * technique's. Mastery multiplies these (tool/cursed_rifle in data/jjk/mastery); the floors and caps here keep every
     * upgrade sensible: no free shots, no instant charge, no beam that never ends.
     */
    public static final class Rifle {
        public float capacity = 100f;
        public float regenPerSecond = 5f;
        /** Normal shots: damage, reserve cost, ticks between shots, reach. */
        public float shotDamage = 7f;
        public float shotCost = 12f;
        public int shotInterval = 18;
        public double shotRange = 72;
        /** Aim: the scope's sway (degrees) and how long it takes to settle; hip shots sway more. */
        public float swayDegrees = 2.2f;
        public float hipSwayDegrees = 5.5f;
        public int settleTicks = 16;
        /** Floors on Mastery: shots never cost less than this share, nor come faster than this many ticks. */
        public float minCostShare = 0.55f;
        public int minShotInterval = 9;
        /** The beam: reserve it takes, the sequence's ticks (deploy, charge, cooldown, retract), how long it fires. */
        public float beamCost = 70f;
        public int deployTicks = 28, chargeTicks = 30, minChargeTicks = 16, cooldownTicks = 14, retractTicks = 28;
        public int beamTicks = 40, maxBeamTicks = 70;
        /** Held ready, it slowly drains the reserve; empty, it lets go. */
        public float readyDrainPerSecond = 4f;
        public int beamCooldown = 240;
        public double beamRange = 44;
        /** Output 0..1: the first beam is 0.45; lens upgrades raise it to at most {@code outputCap}; Maximum Output is 1. */
        public float baseOutput = 0.45f, outputCap = 0.8f;
        /** Half-width at the first unlock and at full output (True Love Beam and Every Last Drop are 2.5). */
        public double baseHalf = 1.1, maxHalf = 2.5;
        /** Full-output damage on first contact and then every interval (scaled down with output); terrain it may carve. */
        public float beamDamage = 20f, beamTickDamage = 8f;
        public int beamDamageInterval = 10;
        public float beamMaxHardness = 50f;
        public double beamCarveEdge = 0.8;
    }

    /**
     * Cursed tools as equippable movesets (the Cursed Item slot): switching, and each kit's numbers. Damage figures are
     * before the tool's Mastery and a curse's grade.
     */
    public static final class CursedToolKits {
        /** Ticks after a technique/tool moveset switch before another switch (switching never resets a cooldown). */
        public int switchLockTicks = 6;
        /** Ticks into a move during which R turns it into its R variant (once that variant is learned). */
        public int variantWindow = 8;
        // Slaughter Demon.
        public float sdQuickstepDamage = 6f, sdQuickstepReach = 5f;
        public int sdQuickstepCooldown = 100;
        public float sdFlurryDamage = 2.2f;
        public int sdFlurryHits = 4, sdFlurryCooldown = 160;
        public float sdSeverDamage = 8f;
        public int sdSeverCooldown = 200;
        public int sdParryWindow = 14, sdParryCooldown = 220;
        public float sdRiposteDamage = 9f;
        public float sdDrawCutDamage = 4f;
        public int sdDrawCutCooldown = 60;
        public float sdUltimateDamage = 7f;
        public int sdUltimateTargets = 5, sdUltimateCooldown = 1200;
        // Cursed Cleaver.
        public float clHeavyDamage = 10f;
        public int clHeavyMaxCharge = 30, clHeavyCooldown = 120;
        public float clChargeDamage = 7f;
        public int clChargeCooldown = 180;
        public float clSplitDamage = 9f;
        public int clSplitCooldown = 220;
        public int clWallTicks = 50, clWallCooldown = 300;
        public float clWallReduction = 0.6f, clWallCounterDamage = 10f;
        public float clHaftDamage = 3f;
        public int clHaftCooldown = 80;
        public float clUltimateDamage = 16f;
        public int clUltimateCooldown = 1200;
        // Cursed Rifle (its shots and beam use the Rifle section).
        public int rfVolleyShots = 3, rfVolleyCooldown = 140;
        public int rfFlareCooldown = 260;
    }

    /**
     * Cursed realms: every cursed-event fight happens in one. The pull (its length), the compass (how near a reported
     * place it starts to read, and how near it starts to react), and orphan safety.
     */
    public static final class Realms {
        /** Ticks between setting an event off and arriving in its realm (the transition). */
        public int transitionTicks = 36;
        /** Within this many blocks of the event, a Cursed Compass held on it points at its source. */
        public double compassRange = 160;
        /** Closer than this, the compass begins to tremble; closer than {@code compassHere}, it reacts to it. */
        public double compassNear = 40, compassHere = 6;
    }

    /** Beam clashes (True Love Beam against Every Last Drop): the duel's tuning lives in the session; these are reach. */
    public static final class BeamClash {
        public boolean enabled = true;
        public double counterRange = 64;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("jjk.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                JJKConfig loaded = GSON.fromJson(r, JJKConfig.class);
                if (loaded != null && loaded.version < CURRENT_VERSION) {
                    JJK.LOGGER.info("{} is from an older version (tuning {} < {}): gameplay values reset to the new defaults, client settings kept",
                            p, loaded.version, CURRENT_VERSION);
                    JJKConfig fresh = new JJKConfig();
                    if (loaded.client != null) fresh.client = loaded.client;
                    loaded = fresh;
                }
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                JJK.LOGGER.error("Failed to read {}, using defaults", p, e);
                instance = new JJKConfig();
            }
        }
        instance.fillNulls();
        instance.version = CURRENT_VERSION;
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            JJK.LOGGER.error("Failed to write {}", path(), e);
        }
    }

    /** Test hook: replace the active config. */
    public static void set(JJKConfig config) {
        instance = config;
        instance.fillNulls();
    }

    private void fillNulls() {
        if (general == null) general = new General();
        if (resources == null) resources = new Resources();
        if (melee == null) melee = new Melee();
        if (guard == null) guard = new Guard();
        if (dash == null) dash = new Dash();
        if (infinity == null) infinity = new Infinity();
        if (blue == null) blue = new Blue();
        if (red == null) red = new Red();
        if (purple == null) purple = new Purple();
        if (teleport == null) teleport = new Teleport();
        if (domain == null) domain = new Domain();
        if (awakening == null) awakening = new Awakening();
        if (maxBlue == null) maxBlue = new MaxBlue();
        if (maxRed == null) maxRed = new MaxRed();
        if (client == null) client = new Client();
        if (hakari == null) hakari = new Hakari();
        if (gojo == null) gojo = new Gojo();
        if (yuji == null) yuji = new Yuji();
        if (yuta == null) yuta = new Yuta();
        if (progression == null) progression = new Progression();
        if (mastery == null) mastery = new MasteryRules();
        if (rifle == null) rifle = new Rifle();
        if (cursedTools == null) cursedTools = new CursedToolKits();
        if (realms == null) realms = new Realms();
    }
}
