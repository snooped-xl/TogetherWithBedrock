package dev.snooped.bedrockmenu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PackPreferencesTest {
    @TempDir Path directory;

    @Test
    void remembersBothDecisionsAcrossClientRestartsWithoutAcceptingOtherWorlds() throws Exception {
        Path file = directory.resolve("preferences.json");
        PackPreferences preferences = new PackPreferences(file);
        assertEquals(PackPreferences.Choice.PROMPT, preferences.get("realm-1"));
        preferences.set("realm-1", PackPreferences.Choice.ENABLED);
        preferences.set("world-2", PackPreferences.Choice.DISABLED);

        PackPreferences restored = new PackPreferences(file);
        assertEquals(PackPreferences.Choice.ENABLED, restored.get("realm-1"));
        assertEquals(PackPreferences.Choice.DISABLED, restored.get("world-2"));
        assertEquals(PackPreferences.Choice.PROMPT, restored.get("realm-3"));
        restored.set("realm-1", PackPreferences.Choice.PROMPT);
        assertEquals(PackPreferences.Choice.PROMPT, new PackPreferences(file).get("realm-1"));
    }

    @Test
    void unknownOrMalformedEntriesNeverImplyConsent() throws Exception {
        Path file = directory.resolve("preferences.json");
        Files.writeString(file, "{\"unknown\":\"ALLOW_ALL\",\"null\":null,\"object\":{},\"declined\":\"DISABLED\"}");
        PackPreferences preferences = new PackPreferences(file);
        assertEquals(PackPreferences.Choice.PROMPT, preferences.get("unknown"));
        assertEquals(PackPreferences.Choice.PROMPT, preferences.get("null"));
        assertEquals(PackPreferences.Choice.PROMPT, preferences.get("object"));
        assertEquals(PackPreferences.Choice.DISABLED, preferences.get("declined"));
    }

    @Test
    void hostedPortChangesPreserveConsentButChangedServerEndpointsDoNot() {
        assertEquals(PackPreferences.identity("hosted", "world-id", "hosted-world-id.old.bedrock.local"),
                PackPreferences.identity("hosted", "world-id", "hosted-world-id.new.bedrock.local"));
        assertNotEquals(PackPreferences.identity("server", "saved-id", "server-saved-id.old.bedrock.local"),
                PackPreferences.identity("server", "saved-id", "server-saved-id.new.bedrock.local"));
        assertNotEquals(PackPreferences.identity("hosted", "first", "unused"),
                PackPreferences.identity("hosted", "second", "unused"));
    }

    @Test
    void migratingRealmAndWorldAddressesPreservesDecisionsWithoutGrantingOtherWorlds() throws Exception {
        Path file = directory.resolve("preferences.json");
        Files.writeString(file, "{\"realm-123.354f95a5-c6a9-3459-b8f7-6aa25b5b0a3c.bedrock.local\":\"ENABLED\","
                + "\"world-456.354f95a5-c6a9-3459-b8f7-6aa25b5b0a3c.bedrock.local\":\"DISABLED\"}");
        PackPreferences preferences = new PackPreferences(file);
        assertEquals(PackPreferences.Choice.ENABLED, preferences.get(PackPreferences.identity("realm", "123", "new-address")));
        assertEquals(PackPreferences.Choice.DISABLED, preferences.get(PackPreferences.identity("world", "456", "new-address")));
        assertEquals(PackPreferences.Choice.PROMPT, preferences.get(PackPreferences.identity("realm", "456", "new-address")));
    }
}
