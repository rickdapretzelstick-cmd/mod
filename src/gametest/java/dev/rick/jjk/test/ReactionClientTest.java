package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Hit reactions on a two-legged entity (the training dummy): each blow picks its flinch from the way it pushed them
 * (struck in front, from behind, on the left, on the right) and how hard (a heavy blow's hitstun); a knockdown lies them
 * down and the wake-up gets them up.
 */
public class ReactionClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s rotated 0 0 run summon jjk:training_dummy ^ ^ ^4");
            ctx.waitTicks(20);
            int dummy = server.computeOnServer(srv -> dummy(srv.getPlayerList().getPlayers().getFirst()).getId());
            List<String> wrong = new ArrayList<>();
            // From each side, light: the flinch for that side (the front alternates between its two).
            expect(ctx, server, dummy, 0, 10, wrong, "reaction_hit_front", "reaction_hit_front_b");
            expect(ctx, server, dummy, 180, 10, wrong, "reaction_hit_back");
            expect(ctx, server, dummy, 90, 10, wrong, "reaction_hit_left");
            expect(ctx, server, dummy, -90, 10, wrong, "reaction_hit_right");
            // Heavy: the big flinch.
            expect(ctx, server, dummy, 0, 24, wrong, "reaction_hit_front_heavy");
            expect(ctx, server, dummy, 180, 24, wrong, "reaction_hit_back_heavy");
            // Floored, then up.
            server.runOnServer(srv -> Statuses.apply(dummy(srv.getPlayerList().getPlayers().getFirst()), CombatStatus.KNOCKDOWN, 20));
            ctx.waitTicks(5);
            String down = ctx.computeOnClient(mc -> ClientAnimations.reactionOf(dummy));
            if (!"reaction_knockdown".equals(down)) wrong.add("knockdown -> " + down);
            server.runOnServer(srv -> {
                LivingEntity d = dummy(srv.getPlayerList().getPlayers().getFirst());
                dev.rick.jjk.core.combat.Combat.state(d).remove(CombatStatus.KNOCKDOWN);
                Statuses.apply(d, CombatStatus.WAKEUP, 10);
            });
            ctx.waitTicks(3);
            String up = ctx.computeOnClient(mc -> ClientAnimations.reactionOf(dummy));
            if (!"reaction_getup".equals(up)) wrong.add("wake-up -> " + up);
            if (!wrong.isEmpty()) throw new AssertionError("wrong hit reactions: " + wrong);
        }
    }

    /** A blow from {@code side} degrees round the dummy (0: in front of it, 180: behind, 90: its left), {@code stun} ticks. */
    private static void expect(ClientGameTestContext ctx, TestServerContext server, int dummyId, float side, int stun, List<String> wrong,
                               String... want) {
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
            LivingEntity d = dummy(p);
            float yaw = (d.yBodyRot + side) * Mth.DEG_TO_RAD;
            // (A point 2 blocks out on that side of where it faces; for its left, the side of its left hand.)
            Vec3 out = side == 90 || side == -90
                    ? new Vec3(Mth.cos(d.yBodyRot * Mth.DEG_TO_RAD), 0, Mth.sin(d.yBodyRot * Mth.DEG_TO_RAD)).scale(side == 90 ? 1 : -1)
                    : new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 origin = d.getBoundingBox().getCenter().add(out.scale(2));
            HitResolver.resolve(Hit.builder(p, "reaction_test").damage(0.01f).knockback(Knockback.NONE).hitstun(stun).origin(origin).build(), d);
        });
        ctx.waitTicks(3);
        String got = ctx.computeOnClient(mc -> ClientAnimations.reactionOf(dummyId));
        if (!List.of(want).contains(got)) wrong.add(side + "deg x" + stun + " -> " + got + " (want " + String.join("/", want) + ")");
        // Out of the hitstun and the combo before the next blow.
        ctx.waitTicks(Math.max(stun, 10) + 60);
    }

    private static LivingEntity dummy(ServerPlayer p) {
        return (LivingEntity) p.level().getEntities(p, new AABB(p.blockPosition()).inflate(10), e -> e.getType() == ModEntities.TRAINING_DUMMY)
                .stream().findFirst().orElseThrow(() -> new AssertionError("no dummy"));
    }
}
