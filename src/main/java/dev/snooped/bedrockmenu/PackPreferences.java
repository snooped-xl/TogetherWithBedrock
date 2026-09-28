// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Resource-pack consent belongs to a logical Bedrock destination, never its temporary proxy port. */
final class PackPreferences {
    enum Choice { PROMPT, ENABLED, DISABLED }

    private final Path file;
    private final Map<String, Choice> choices = new HashMap<>();

    PackPreferences(Path file) {
        this.file = file;
        if (!Files.isRegularFile(file)) return;
        try {
            JsonObject entries = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var entry : entries.entrySet()) {
                try {
                    Choice choice = Choice.valueOf(entry.getValue().getAsString());
                    if (choice != Choice.PROMPT) choices.merge(normalizeIdentity(entry.getKey()), choice,
                            (previous, next) -> previous == Choice.DISABLED ? previous : next);
                } catch (RuntimeException ignored) {
                    // Unknown or invalid choices must ask again, rather than imply consent.
                }
            }
        } catch (IOException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not read Bedrock resource-pack preferences; new connections will ask again", e);
        }
    }

    synchronized Choice get(String identity) {
        return choices.getOrDefault(normalizeIdentity(identity), Choice.PROMPT);
    }

    synchronized void set(String identity, Choice choice) throws IOException {
        identity = normalizeIdentity(identity);
        if (get(identity) == choice) return;
        if (choice == Choice.PROMPT) choices.remove(identity);
        else choices.put(identity, choice);
        JsonObject entries = new JsonObject();
        choices.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.addProperty(entry.getKey(), entry.getValue().name()));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "bedrock-pack-preferences-", ".tmp");
        try {
            Files.writeString(temporary, entries.toString());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String identity(String kind, String id, String logicalAddress) {
        // A hosted world keeps its UUID when its independently allocated Bedrock port changes.
        // Saved remote servers retain the endpoint hash, so editing their address asks again.
        return switch (kind) {
            case "hosted", "realm", "realmcode", "world" -> kind + "-" + id;
            default -> logicalAddress;
        };
    }

    private static String normalizeIdentity(String identity) {
        // Migrate previously saved logical destinations without their transport endpoint hash.
        // Server entries keep that hash: changing a saved server address must ask again.
        return identity.replaceFirst("^((?:realm|realmcode|world)-.+)\\.[0-9a-fA-F-]{36}\\.bedrock\\.local$", "$1");
    }
}
