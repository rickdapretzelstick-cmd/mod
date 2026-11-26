package dev.rick.jjk.core.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every held (or looping) clip the server plays must be on the server's held list ({@link Anim}): that list is how the
 * server knows to release the pose when its move ends. A clip marked {@code hold} in its JSON but missing there stays up
 * after the move (Every Last Drop's did, for over a second). Client-driven clips (hit reactions, tool stances, the rifle,
 * Rika's model) are the client's own business.
 */
class HeldClipsTest {
    private static final Path ANIMS = Path.of("src/main/resources/assets/jjk/animations");
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void everyHeldClipTheServerPlaysIsReleasedWithItsMove() throws IOException {
        Set<String> held = new HashSet<>();
        try (Stream<Path> files = Files.walk(ANIMS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonElement root = JsonParser.parseString(Files.readString(f));
                if (!root.isJsonObject()) continue;
                JsonObject o = root.getAsJsonObject();
                List<JsonObject> clips = new ArrayList<>();
                if (o.has("clips")) for (JsonElement c : o.getAsJsonArray("clips")) clips.add(c.getAsJsonObject());
                else clips.add(o);
                for (JsonObject c : clips) {
                    boolean hold = c.has("hold") && c.get("hold").getAsBoolean() || c.has("loop") && c.get("loop").getAsBoolean();
                    if (hold && c.has("name")) held.add(c.get("name").getAsString());
                }
            }
        }
        // Names the server's code mentions (string literals), except the held list itself.
        Set<String> mentioned = new HashSet<>();
        Pattern literal = Pattern.compile("\"([a-z0-9_]+)\"");
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java") && !p.endsWith(Path.of("core/anim/Anim.java"))).toList()) {
                Matcher m = literal.matcher(Files.readString(f));
                while (m.find()) mentioned.add(m.group(1));
            }
        }
        List<String> missing = new ArrayList<>();
        for (String clip : held) {
            if (clip.startsWith("reaction_") || clip.startsWith("stance_") || clip.startsWith("cursed_rifle_") || clip.startsWith("rika_")) continue;
            if (mentioned.contains(clip) && !Anim.isHeldClip(clip)) missing.add(clip);
        }
        missing.sort(null);
        assertTrue(missing.isEmpty(), "held clips the server plays but never releases (add them to Anim.HELD): " + missing);
    }
}
