package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.domain.structure.DomainStructures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Drives a real client through Gojo's whole kit with real key/mouse input: the base moveset, building and spending the
 * Awakening, the awakened moveset, and Infinite Void's physical domain with exact world restoration. Screenshots land in
 * build/run/clientGameTest/screenshots.
 */
public class GojoClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a run jjk nocooldown true");
            // Infinity no longer shields Gojo, and the arena's fighting dummy never stops: keep the test player alive.
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -90 10");
            // Put back what the staged Red broke, so vanilla grass can't creep over it before the domain checks.
            server.runCommand("jjk restore now");
            ctx.waitTicks(40);
            if (!ctx.computeOnClient(mc -> ClientState.hasCharacter())) throw new AssertionError("player should have become Gojo on join");
            ctx.takeScreenshot("01_arena_hud");

            TestInput in = ctx.getInput();
            KeyMapping z = key(ctx, "key.jjk.skill_1"), x = key(ctx, "key.jjk.skill_2"), c = key(ctx, "key.jjk.skill_3");
            KeyMapping v = key(ctx, "key.jjk.skill_4"), g = key(ctx, "key.jjk.ultimate"), r = key(ctx, "key.jjk.skill_5");
            // JJS PC controls by default: 1-4 moves, R special, G awakening, F block, Q dash.
            String[][] defaults = {{"key.jjk.skill_1", "key.keyboard.1"}, {"key.jjk.skill_2", "key.keyboard.2"}, {"key.jjk.skill_3", "key.keyboard.3"},
                    {"key.jjk.skill_4", "key.keyboard.4"}, {"key.jjk.skill_5", "key.keyboard.r"}, {"key.jjk.ultimate", "key.keyboard.g"},
                    {"key.jjk.guard", "key.keyboard.f"}, {"key.jjk.dash", "key.keyboard.q"}};
            for (String[] d : defaults) {
                String bound = key(ctx, d[0]).saveString();
                if (!bound.equals(d[1])) throw new AssertionError(d[0] + " should default to " + d[1] + " (is " + bound + ")");
            }

            // --- Base kit ---
            in.holdKeyFor(o -> o.keyUp, 18);
            for (int i = 0; i < 4; i++) {
                in.pressKey(o -> o.keyAttack);
                ctx.waitTicks(i == 1 ? 3 : 7);
                if (i == 1) ctx.takeScreenshot("02_melee_hit");
            }
            ctx.waitTicks(3);
            ctx.takeScreenshot("03_melee_finisher");
            int combo = ctx.computeOnClient(mc -> ClientState.comboCount);
            if (combo < 2) throw new AssertionError("melee chain should connect (combo " + combo + ")");
            float meter = ctx.computeOnClient(mc -> ClientState.awakening);
            if (meter <= 0) throw new AssertionError("landing hits should build the Awakening meter");

            // --- Ability HUD: spend some CE for real so the bar, its trailing ghost and a cooldown show. ---
            server.runCommand("execute as @a run jjk nocooldown false");
            server.runCommand("execute as @a run jjk awakening 100");
            server.runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst();
                Casters.get(p).setEnergy(Casters.get(p).maxEnergy() * 0.62f);
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            // Face the open side of the arena (the dummies are east, west and south).
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 8");
            ctx.waitTicks(40);
            in.pressKey(v);
            ctx.waitTicks(6);
            in.holdKey(x);
            ctx.waitTicks(10);
            in.releaseKey(x);
            ctx.waitTicks(4);
            ctx.takeScreenshot("01b_ability_hud");
            if (!ctx.computeOnClient(mc -> ClientState.abilityIn(dev.rick.jjk.core.ability.AbilitySlot.SKILL_3).equals("rapid_punches"))) {
                throw new AssertionError("3 should be Rapid Punches in the base kit");
            }
            if (ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) != 0) {
                throw new AssertionError("pressing 2 in combat mode is a move, not a hotbar switch");
            }

            // --- Vanilla Minecraft mode: HUD gone and ability keys inert. ---
            ctx.runOnClient(mc -> dev.rick.jjk.client.CombatMode.set(false));
            float ceBefore = server.computeOnServer(s -> Casters.get(s.getPlayerList().getPlayers().getFirst()).energy());
            in.pressKey(z);
            in.pressKey(x);
            ctx.waitTicks(10);
            ctx.takeScreenshot("01c_vanilla_mode");
            boolean fired = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                return Casters.get(p).energy() < ceBefore - 1 || !p.level().getEntitiesOfClass(dev.rick.jjk.entity.BlueEntity.class, p.getBoundingBox().inflate(30)).isEmpty();
            });
            if (fired) throw new AssertionError("ability keys must do nothing in Vanilla Minecraft mode");
            if (ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) != 1) {
                throw new AssertionError("in Vanilla mode 2 selects hotbar slot 2 again");
            }
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            if (!ctx.computeOnClient(mc -> !dev.rick.jjk.client.input.InputHandler.inStance())) throw new AssertionError("no JJK melee in vanilla mode");
            ctx.runOnClient(mc -> dev.rick.jjk.client.CombatMode.set(true));
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -90 10");
            if (!ctx.computeOnClient(mc -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("jjk.json").toFile().exists()
                    && dev.rick.jjk.config.JJKConfig.get().client.combatMode)) throw new AssertionError("combat mode should be saved back on");
            server.runCommand("execute as @a run jjk nocooldown true");
            ctx.waitTicks(40);


            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runCommand("execute as @a at @s run tp @s ~-4 ~ ~ -90 5");
            ctx.waitTicks(10);
            in.holdKey(z);
            ctx.waitTicks(12);
            ctx.takeScreenshot("04_blue");
            ctx.waitTicks(10);
            in.releaseKey(z);
            ctx.waitTicks(30);
            in.holdKey(x);
            ctx.waitTicks(20);
            ctx.takeScreenshot("05_red_charge");
            in.releaseKey(x);
            ctx.waitTicks(5);
            ctx.takeScreenshot("06_red_explosion");
            ctx.waitTicks(30);
            // Rapid Punches, Twofold Kick and Limitless on the dummy in front.
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -90 5");
            in.pressKey(r);
            ctx.waitTicks(7);
            ctx.takeScreenshot("06b_limitless");
            ctx.waitTicks(5);
            ctx.takeScreenshot("06b2_limitless_shatter");
            in.pressKey(c);
            ctx.waitTicks(14);
            ctx.takeScreenshot("06c_rapid_punches");
            ctx.waitTicks(40);
            in.pressKey(r);
            ctx.waitTicks(8);
            in.pressKey(v);
            ctx.waitTicks(7);
            ctx.takeScreenshot("06d_twofold_kick");
            ctx.waitTicks(30);

            // --- Awakening ---
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);
            ctx.takeScreenshot("07_awakening_ready");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(3);
            ctx.takeScreenshot("08_blindfold");
            in.pressKey(g);
            ctx.waitTicks(12);
            ctx.takeScreenshot("09_awakening_transition");
            ctx.waitTicks(12);
            ctx.takeScreenshot("10_awakened");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(40);
            ctx.takeScreenshot("10b_awakened_hud");
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("pressing G on a full meter should awaken");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(20);

            // --- Awakened kit ---
            // One target straight ahead: Max Blue's reach would otherwise drag every dummy in the arena across the camera.
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^7");
            ctx.waitTicks(2);
            in.pressKey(z);
            ctx.waitTicks(8);
            ctx.takeScreenshot("11_max_blue_cast");
            ctx.waitTicks(16);
            ctx.takeScreenshot("12_max_blue");
            ctx.waitTicks(40);
            in.holdKey(x);
            ctx.waitTicks(36);
            ctx.takeScreenshot("13_max_red_charge");
            in.releaseKey(x);
            ctx.waitTicks(5);
            ctx.takeScreenshot("14_max_red_explosion");
            ctx.waitTicks(30);
            server.runCommand("execute as @a run jjk awakening 100");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -120 5");
            in.holdKey(c);
            ctx.waitTicks(10);
            ctx.takeScreenshot("15_purple_blue");
            ctx.waitTicks(16);
            ctx.takeScreenshot("16_purple_red");
            ctx.waitTicks(14);
            ctx.takeScreenshot("17_purple_fusion");
            ctx.waitTicks(10);
            in.releaseKey(c);
            ctx.waitTicks(4);
            ctx.takeScreenshot("18_purple_fired");
            ctx.waitTicks(40);

            // --- Infinite Void: record the world first, then check it comes back exactly. ---
            server.runCommand("execute as @a run jjk awakening 100");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 5");
            // Opponents for the domain to trap.
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^2 ^ ^5");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^-2 ^ ^5");
            ctx.waitTicks(10);
            Map<BlockPos, BlockState> before = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                Map<BlockPos, BlockState> m = new HashMap<>();
                BlockPos c0 = p.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(c0.offset(-21, -21, -21), c0.offset(21, 21, 21))) m.put(pos.immutable(), p.level().getBlockState(pos));
                return m;
            });
            in.pressKey(v); // Infinite Void is the awakened kit's 4
            ctx.waitTicks(15);
            ctx.takeScreenshot("19_domain_sign");
            // The domain builds itself from Gojo's feet: ground, walls, ceiling, then the seal and the title card.
            ctx.waitTicks(18);
            ctx.takeScreenshot("20a_forming_ground");
            ctx.waitTicks(14);
            ctx.takeScreenshot("20b_forming_walls");
            ctx.waitTicks(12);
            ctx.takeScreenshot("20c_forming_ceiling");
            ctx.waitTicks(16);
            ctx.takeScreenshot("20_infinite_void");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ -35");
            ctx.waitTicks(8);
            ctx.takeScreenshot("21_infinite_void_sky");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ 20");
            ctx.waitTicks(4);
            ctx.takeScreenshot("22_infinite_void_first_person");
            boolean overloaded = server.computeOnServer(s -> {
                for (var level : s.getAllLevels()) {
                    for (var e : level.getAllEntities()) {
                        if (e instanceof LivingEntity le && !(e instanceof net.minecraft.world.entity.player.Player) && Combat.has(le, CombatStatus.OVERLOAD)) return true;
                    }
                }
                return false;
            });
            if (!overloaded) throw new AssertionError("dummies inside Infinite Void should be overloaded");
            ctx.waitTicks(320);
            ctx.takeScreenshot("23_after_domain");
            String diff = server.computeOnServer(s -> {
                var level = s.getPlayerList().getPlayers().getFirst().level();
                if (!DomainStructures.all(level).isEmpty()) return "structure still active";
                int wrong = 0;
                String example = "";
                for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                    BlockState now = level.getBlockState(e.getKey());
                    // Leaves cut off from their logs (distance 7, not player-placed) decay on their own; that's vanilla, not the domain.
                    if (e.getValue().is(net.minecraft.tags.BlockTags.LEAVES) && e.getValue().hasProperty(net.minecraft.world.level.block.LeavesBlock.DISTANCE)
                            && e.getValue().getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE) == 7
                            && !e.getValue().getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) continue;
                    // Grass creeping onto bare dirt (left by an earlier crater) is vanilla growth too.
                    if (e.getValue().is(net.minecraft.world.level.block.Blocks.DIRT) && now.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) continue;
                    if (!now.equals(e.getValue())) {
                        if (wrong++ == 0) example = e.getKey() + " was " + e.getValue() + " now " + now;
                    }
                }
                return wrong == 0 ? "" : wrong + " blocks differ, e.g. " + example;
            });
            if (!diff.isEmpty()) throw new AssertionError("world should be restored exactly after Infinite Void: " + diff);
            boolean burnt = server.computeOnServer(s -> s.getPlayerList().getPlayers().stream().anyMatch(p -> Combat.has(p, CombatStatus.BURNOUT)
                    || Casters.get(p).character() == null));
            if (!burnt) throw new AssertionError("owner should be burnt out after the domain");

            // --- Domain counter: a rival starts opening their domain; with a full meter, the Awakening key answers it. ---
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.combat.Statuses.remove(p, CombatStatus.BURNOUT);
                var caster = Casters.get(p);
                caster.endAwakening("test");
                caster.setAwakening(caster.maxAwakening());
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ 5");
            ctx.waitTicks(5);
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var level = p.level();
                var rival = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                rival.setPos(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
                rival.setYRot(p.getYRot() + 180);
                rival.setCustomName(net.minecraft.network.chat.Component.literal("Rival"));
                level.addFreshEntity(rival);
                dev.rick.jjk.core.character.CharacterService.assign(rival, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.gojo.GojoCharacter.ID));
                dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(rival, 0.45f);
                var rc = Casters.get(rival);
                rc.enterAwakening();
                rc.setAwakening(rc.maxAwakening());
                rc.setNoCost(true);
                if (!rc.input(dev.rick.jjk.core.ability.AbilitySlot.SKILL_4, true, 0, 0, null)) throw new AssertionError("rival should start opening a domain");
            });
            ctx.waitTicks(6);
            ctx.takeScreenshot("24_counter_prompt");
            if (ctx.computeOnClient(mc -> mc.level.getGameTime() >= ClientState.counterUntilTick)) throw new AssertionError("a full meter should get a counter window");
            in.pressKey(g);
            ctx.waitTicks(3);
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("the counter awakens instantly");
            ctx.takeScreenshot("25_counter_versus");
            ctx.waitTicks(6);
            ctx.takeScreenshot("25b_counter_versus");
            for (int i = 0; i < 40 && !ctx.computeOnClient(mc -> dev.rick.jjk.client.clash.ClashClient.playing()); i++) ctx.waitTick();
            if (!ctx.computeOnClient(mc -> dev.rick.jjk.client.clash.ClashClient.playing())) throw new AssertionError("overlapping domains should start a clash");
            ctx.waitTicks(8);
            ctx.takeScreenshot("26_clash_intro");
            ctx.waitTicks(22);
            ctx.takeScreenshot("27_clash_countdown");
            // Play the chart with the arrow keys, pressing each prompt on the tick nearest its hit time.
            int[] keys = {InputConstants.KEY_LEFT, InputConstants.KEY_DOWN, InputConstants.KEY_UP, InputConstants.KEY_RIGHT};
            java.util.Set<Integer> pressed = new java.util.HashSet<>();
            int shots = 0;
            for (int tick = 0; tick < 900 && ctx.computeOnClient(mc -> dev.rick.jjk.client.clash.ClashClient.view() != null); tick++) {
                int[] due = ctx.computeOnClient(mc -> {
                    var cv = dev.rick.jjk.client.clash.ClashClient.view();
                    if (cv == null) return new int[0];
                    double clock = cv.clock();
                    java.util.List<Integer> out = new java.util.ArrayList<>();
                    for (int i = 0; i < cv.times.length; i++) {
                        if (Math.abs(cv.times[i] - clock) <= 0.5) out.add(cv.round * 1000 + i);
                    }
                    return out.stream().mapToInt(Integer::intValue).toArray();
                });
                for (int id : due) {
                    if (!pressed.add(id)) continue;
                    int lane = ctx.computeOnClient(mc -> {
                        var cv = dev.rick.jjk.client.clash.ClashClient.view();
                        return cv == null || id % 1000 >= cv.lanes.length ? -1 : (int) cv.lanes[id % 1000];
                    });
                    if (lane >= 0) in.pressKey(keys[lane]);
                }
                if (shots < 3 && pressed.size() >= 6 + shots * 6) {
                    ctx.takeScreenshot("28_clash_play_" + shots);
                    shots++;
                }
                ctx.waitTick();
            }
            ctx.takeScreenshot("29_clash_won");
            String result = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var mine = dev.rick.jjk.core.domain.DomainManager.ownedBy(p);
                return mine != null && mine.isLive() ? "" : "the player's domain should have won the clash";
            });
            if (!result.isEmpty()) throw new AssertionError(result);
            if (ctx.computeOnClient(mc -> dev.rick.jjk.client.clash.ClashClient.view() != null)) throw new AssertionError("clash UI should be gone");
            ctx.waitTicks(20);
            ctx.takeScreenshot("30_after_clash");
        }
    }

    private static KeyMapping key(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }
}
