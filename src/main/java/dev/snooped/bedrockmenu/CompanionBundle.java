// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Ships the companion service (ViaProxy build, broadcast bot and Via templates)
 * inside the mod jar and materializes it inside the game instance on demand.
 * The service always launches with the Java runtime the game itself uses, so
 * there is no separate application directory and no bundled JRE: one mod jar
 * serves every platform the game runs on.
 */
final class CompanionBundle {
    private static final String BASE = "/assets/bedrockmenu/companion/";
    private static final String SERVICE_JAR = "ViaProxy.jar";
    private static final String BROADCAST_JAR = "MCXboxBroadcastStandalone.jar";
    private static final List<String> TEMPLATES = List.of(
            "viaaprilfools.yml", "viabackwards.yml", "viabedrock.yml",
            "vialegacy.yml", "viaproxy.yml", "viarewind.yml", "viaversion.yml");

    private static Path root, profilePath, descriptorPath;
    private static String contentHash;

    private CompanionBundle() {}

    /** Data root inside the game instance; hosting profiles live here too. */
    private static Path root() throws IOException {
        Path r = root;
        if (r == null) {
            Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            r = game.resolve("bedrock-companion");
            profilePath = r.resolve("profile-v2");
            descriptorPath = r.resolve("current.json");
            Files.createDirectories(profilePath);
            root = r;
        }
        return r;
    }

    /** The companion's private data/profile directory, inside the instance. */
    static Path profile() throws IOException { root(); return profilePath; }

    /** Launch descriptor consumed by the companion; rewritten on every start. */
    static Path descriptor() throws IOException { root(); return descriptorPath; }

    /**
     * Extracts the embedded service build (versioned by content hash) and writes
     * the launch descriptor. Safe to call on every companion start: existing,
     * matching files are reused and stale runtime versions are cleaned up.
     */
    static synchronized Path install() throws IOException {
        Path r = root();
        Path runtime = r.resolve("runtime-" + hash());
        extractIfMissing(SERVICE_JAR, runtime.resolve(SERVICE_JAR));
        extractIfMissing(BROADCAST_JAR, runtime.resolve(BROADCAST_JAR));
        for (String template : TEMPLATES) {
            extractIfMissing("templates/" + template, runtime.resolve("templates").resolve(template));
        }
        pruneOldRuntimes(r, runtime);

        String java = Path.of(System.getProperty("java.home"), "bin", javaBinary()).toString();
        JsonObject release = new JsonObject();
        release.addProperty("version", "embedded-" + hash().substring(0, 12));
        release.addProperty("api", 2);
        release.addProperty("java", java);
        release.addProperty("java25", java);
        release.addProperty("gameDirectory", FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().toString());
        release.addProperty("jar", runtime.resolve(SERVICE_JAR).toString());
        release.addProperty("broadcaster", runtime.resolve(BROADCAST_JAR).toString());
        release.addProperty("templates", runtime.resolve("templates").toString());
        Files.writeString(descriptorPath, new Gson().toJson(release));
        return descriptorPath;
    }

    /** SHA-256 of the embedded service jar; identifies the extracted runtime. */
    private static String hash() throws IOException {
        String h = contentHash;
        if (h == null) {
            try (InputStream input = resource(SERVICE_JAR)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65_536];
                for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
                StringBuilder hex = new StringBuilder();
                for (byte b : digest.digest()) hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
                h = hex.toString();
                contentHash = h;
            } catch (Exception e) {
                throw new IOException("Could not read the embedded companion build", e);
            }
        }
        return h;
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = CompanionBundle.class.getResourceAsStream(BASE + name);
        if (input == null) throw new IOException("The mod jar is missing its embedded companion file " + name);
        return input;
    }

    private static void extractIfMissing(String name, Path target) throws IOException {
        if (Files.isRegularFile(target)) return; // content-addressed directory; presence implies match
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream input = resource(name)) {
            Files.copy(input, part, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Best-effort cleanup of runtime versions left by older mod builds. */
    private static void pruneOldRuntimes(Path root, Path current) {
        List<Path> stale = new ArrayList<>();
        try (var entries = Files.list(root)) {
            entries.filter(p -> p.getFileName().toString().startsWith("runtime-") && !p.equals(current)).forEach(stale::add);
        } catch (Exception ignored) {}
        for (Path dir : stale) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {try {Files.deleteIfExists(p);} catch (Exception ignored) {}});
            } catch (Exception ignored) {}
        }
    }

    private static String javaBinary() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
    }
}
