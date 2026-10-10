package dev.rick.jjk.progression.mastery;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.JJKCharacter;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.grade.CursedDamage;
import dev.rick.jjk.progression.grade.GradedCurse;
import dev.rick.jjk.progression.tool.CursedToolItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What exorcising a curse pays, and to whom.
 *
 * <ul>
 *   <li><b>Contribution.</b> Every hit a player lands on a graded curse is booked to the tree that did it: the cursed
 *   tool's ({@code tool/<id>}) or their technique's ({@code technique/<kit>}). Nothing else (a vanilla sword can't hurt a
 *   curse anyway) is booked.</li>
 *   <li><b>One reward, split.</b> At death the curse's reward (its grade's Mastery) is divided by damage share: first
 *   between the players, then each player's part between their own trees. Fighting with a tool and a technique together
 *   pays the same total as either alone, never both in full.</li>
 *   <li><b>Only your own technique.</b> The technique share goes to a tree only its legitimate kit owner can develop;
 *   anyone else's technique share is simply not paid (a Creative test kit earns tool Mastery only).</li>
 *   <li><b>Fatigue.</b> Each exorcism of a grade wears that grade's reward down ({@code 1 / (1 + fatigue)}), recovering
 *   with time: farming the same weak curses stops paying, moving up a grade or taking an investigation keeps it worth
 *   it. A curse tied to an investigation never pays less than {@link JJKConfig.MasteryRules#incidentFloor}.</li>
 * </ul>
 */
public final class CurseRewards {
    /** victim → player → tree → damage. */
    private static final Map<UUID, Map<UUID, Map<String, Float>>> BOOK = new HashMap<>();
    /** Test/observer hook: every payout as it happens. */
    @Nullable public static Consumer<Payout> listener;

    public record Payout(UUID player, String tree, int amount, CurseGrade grade, double fatigueFactor) {}

    private CurseRewards() {}

    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken > 0 && entity instanceof GradedCurse) book(entity, source, taken);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof GradedCurse g) payOut(entity, g);
            BOOK.remove(entity.getUUID());
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!(entity instanceof LivingEntity le) || !le.isDeadOrDying()) BOOK.remove(entity.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> BOOK.clear());
    }

    /** The tree a hit's damage counts toward, or null (not a player's, or not developable damage). */
    @Nullable
    static String treeOf(DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer p)) return null;
        return switch (CursedDamage.classify(source)) {
            case CURSED_TOOL -> {
                var t = CursedDamage.tool(source);
                yield t == null ? null : t.treeId();
            }
            case TECHNIQUE, CURSED_ENERGY -> {
                var caster = dev.rick.jjk.core.ability.Casters.getOrNull(p);
                JJKCharacter c = caster == null ? null : caster.character();
                yield c == null ? null : MasteryTree.techniqueId(c.id);
            }
            default -> null;
        };
    }

    static void book(LivingEntity victim, DamageSource source, float amount) {
        String tree = treeOf(source);
        if (tree == null) return;
        UUID who = source.getEntity().getUUID();
        float capped = Math.min(amount, victim.getMaxHealth());
        BOOK.computeIfAbsent(victim.getUUID(), k -> new LinkedHashMap<>())
                .computeIfAbsent(who, k -> new LinkedHashMap<>())
                .merge(tree, capped, Float::sum);
    }

    /** Test hook: books damage to a tree directly. */
    public static void bookForTest(LivingEntity victim, ServerPlayer p, String tree, float amount) {
        BOOK.computeIfAbsent(victim.getUUID(), k -> new LinkedHashMap<>()).computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>())
                .merge(tree, amount, Float::sum);
    }

    static void payOut(LivingEntity victim, GradedCurse g) {
        Map<UUID, Map<String, Float>> book = BOOK.remove(victim.getUUID());
        if (book == null || book.isEmpty() || victim.level().getServer() == null) return;
        CurseGrade grade = g.curseGrade();
        boolean incident = !g.incidentId().isEmpty();
        JJKConfig.MasteryRules cfg = JJKConfig.get().mastery;
        double total = 0;
        for (Map<String, Float> m : book.values()) for (float f : m.values()) total += f;
        if (total <= 0) return;
        for (Map.Entry<UUID, Map<String, Float>> e : book.entrySet()) {
            ServerPlayer p = victim.level().getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            MasteryData d = Mastery.data(p);
            long now = p.level().getGameTime();
            Map<String, Integer> fatigue = decayed(d, now, cfg);
            double f = fatigue.getOrDefault(grade.name(), 0) / 100.0;
            double factor = 1.0 / (1.0 + f);
            if (incident) factor = Math.max(factor, cfg.incidentFloor);
            fatigue.merge(grade.name(), (int) Math.round(cfg.fatiguePerKill * 100), Integer::sum);
            d = d.withFatigue(fatigue, now).recordExorcism(grade.name(), false);
            double mine = 0;
            for (float v : e.getValue().values()) mine += v;
            double reward = grade.mastery * cfg.rewardMultiplier * factor * (mine / total);
            StringBuilder said = new StringBuilder();
            for (Map.Entry<String, Float> t : e.getValue().entrySet()) {
                int amount = (int) Math.round(reward * t.getValue() / mine);
                MasteryTree tree = MasteryTrees.get(t.getKey());
                if (amount <= 0 || tree == null || !Mastery.mayDevelop(p, tree)) continue;
                d = d.earn(t.getKey(), amount);
                if (!said.isEmpty()) said.append(", ");
                said.append("+").append(amount).append(" ").append(tree.title());
                if (listener != null) listener.accept(new Payout(p.getUUID(), t.getKey(), amount, grade, factor));
                if (incident) dev.rick.jjk.progression.investigation.Investigations.credit(p.getUUID(), g.incidentId(), t.getKey(), amount);
            }
            Mastery.set(p, d);
            if (!said.isEmpty()) {
                p.sendOverlayMessage(Component.literal("Exorcised. " + said + " Mastery" + (factor < 0.75 ? " (familiar prey pays less)" : ""))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    /** The player's fatigue per grade with the recovery since it was last touched applied (hundredths). */
    static Map<String, Integer> decayed(MasteryData d, long now, JJKConfig.MasteryRules cfg) {
        Map<String, Integer> out = new LinkedHashMap<>();
        double minutes = d.fatigueAt() <= 0 || now < d.fatigueAt() ? 0 : (now - d.fatigueAt()) / 1200.0;
        int recover = (int) Math.floor(minutes * cfg.fatigueRecoveryPerMinute * 100);
        d.fatigue().forEach((k, v) -> {
            int left = v - recover;
            if (left > 0) out.put(k, left);
        });
        return out;
    }

    /** Whether a damage source would count for anyone (for tests). */
    public static boolean counts(DamageSource source) {
        return treeOf(source) != null;
    }
}
