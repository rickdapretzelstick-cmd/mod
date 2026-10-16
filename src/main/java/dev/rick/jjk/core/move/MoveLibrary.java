package dev.rick.jjk.core.move;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rick.jjk.JJK;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.net.MoveSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The editor-made moves: every .json in config/jjk/moves (the move editor's exports), and which key each player has
 * bound them to (kept in config/jjk/moves/bindings.json). Moves reach clients as their clips ({@link MoveSyncPayload}).
 */
public final class MoveLibrary {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] EXAMPLES = {"example_grab_slam", "example_dash_punch"};
    private static volatile Map<String, Move> moves = new LinkedHashMap<>();
    private static final List<String> errors = new ArrayList<>();
    /** Player UUID → slot → move id. */
    private static final Map<String, Map<String, String>> bindings = new HashMap<>();
    @Nullable private static Path dirOverride;

    private MoveLibrary() {}

    public static Path dir() {
        return dirOverride != null ? dirOverride : FabricLoader.getInstance().getConfigDir().resolve("jjk").resolve("moves");
    }

    /** Tests: read moves from elsewhere. */
    public static void setDir(@Nullable Path p) {
        dirOverride = p;
    }

    @Nullable
    public static Move get(String id) {
        return moves.get(id);
    }

    public static Map<String, Move> all() {
        return moves;
    }

    public static List<String> errors() {
        return errors;
    }

    /** Adds or replaces one move (tests, and the command's paste). */
    public static Move put(JsonObject json) {
        Move m = Move.parse(json);
        Map<String, Move> next = new LinkedHashMap<>(moves);
        next.put(m.id(), m);
        moves = next;
        return m;
    }

    /** Reads every move file; returns how many loaded. */
    public static int reload() {
        Path dir = dir();
        Map<String, Move> next = new LinkedHashMap<>();
        errors.clear();
        try {
            Files.createDirectories(dir);
            boolean empty;
            try (Stream<Path> s = Files.list(dir)) {
                empty = s.noneMatch(p -> p.toString().endsWith(".json") && !p.getFileName().toString().equals("bindings.json"));
            }
            if (empty) copyExamples(dir);
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : s.sorted().toList()) {
                    String f = p.getFileName().toString();
                    if (!f.endsWith(".json") || f.equals("bindings.json")) continue;
                    try {
                        Move m = Move.parse(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
                        next.put(m.id(), m);
                    } catch (Exception e) {
                        errors.add(f + ": " + e.getMessage());
                    }
                }
            }
            loadBindings(dir.resolve("bindings.json"));
        } catch (IOException e) {
            errors.add(e.toString());
        }
        moves = next;
        for (String e : errors) JJK.LOGGER.warn("[moves] {}", e);
        JJK.LOGGER.info("[moves] {} editor move(s) from {}", next.size(), dir);
        return next.size();
    }

    private static void copyExamples(Path dir) throws IOException {
        for (String n : EXAMPLES) {
            try (InputStream in = MoveLibrary.class.getResourceAsStream("/jjk_moves/" + n + ".json")) {
                if (in != null) Files.write(dir.resolve(n + ".json"), in.readAllBytes());
            }
        }
    }

    public static MoveSyncPayload payload() {
        List<String> names = new ArrayList<>(), clips = new ArrayList<>();
        for (Move m : moves.values()) {
            names.add(Move.clipName(m.id()));
            clips.add(m.clipJson());
        }
        return new MoveSyncPayload(names, clips);
    }

    public static void sync(ServerPlayer p) {
        if (ServerPlayNetworking.canSend(p, MoveSyncPayload.TYPE)) ServerPlayNetworking.send(p, payload());
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    // --- Key bindings ---

    public static AbilitySlot slot(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "1", "skill1", "skill_1" -> AbilitySlot.SKILL_1;
            case "2", "skill2", "skill_2" -> AbilitySlot.SKILL_2;
            case "3", "skill3", "skill_3" -> AbilitySlot.SKILL_3;
            case "4", "skill4", "skill_4" -> AbilitySlot.SKILL_4;
            case "5", "skill5", "skill_5" -> AbilitySlot.SKILL_5;
            case "ultimate", "g" -> AbilitySlot.ULTIMATE;
            case "heavy", "r" -> AbilitySlot.HEAVY;
            case "dash", "q" -> AbilitySlot.DASH;
            case "guard", "f" -> AbilitySlot.GUARD;
            default -> null;
        };
    }

    public static void bind(ServerPlayer p, AbilitySlot slot, @Nullable String moveId) {
        Map<String, String> b = bindings.computeIfAbsent(p.getUUID().toString(), k -> new LinkedHashMap<>());
        if (moveId == null) b.remove(slot.name());
        else b.put(slot.name(), moveId);
        apply(p);
        saveBindings();
    }

    /** Puts a player's bound moves on their keys (on joining, and after a reload). */
    public static void apply(ServerPlayer p) {
        Map<AbilitySlot, MoveAbility> out = new EnumMap<>(AbilitySlot.class);
        Map<String, String> b = bindings.get(p.getUUID().toString());
        if (b != null) {
            for (var e : b.entrySet()) {
                Move m = moves.get(e.getValue());
                if (m != null) out.put(AbilitySlot.valueOf(e.getKey()), new MoveAbility(m));
            }
        }
        Casters.get(p).setCustomMoves(out);
    }

    public static Map<String, String> bindingsOf(ServerPlayer p) {
        return bindings.getOrDefault(p.getUUID().toString(), Map.of());
    }

    private static void loadBindings(Path f) {
        bindings.clear();
        if (!Files.exists(f)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (var e : o.entrySet()) {
                Map<String, String> b = new LinkedHashMap<>();
                for (var s : e.getValue().getAsJsonObject().entrySet()) b.put(s.getKey(), s.getValue().getAsString());
                bindings.put(e.getKey(), b);
            }
        } catch (Exception e) {
            errors.add("bindings.json: " + e.getMessage());
        }
    }

    private static void saveBindings() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("bindings.json"), GSON.toJson(bindings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            JJK.LOGGER.warn("[moves] could not save bindings: {}", e.toString());
        }
    }
}
