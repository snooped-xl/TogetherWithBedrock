// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Local notification history only; reading or dismissing a banner never changes an Xbox friendship. */
final class FriendRequestNotifications {
    private final Path file;
    private final Map<String, Set<String>> seenByAccount = new HashMap<>();
    private final Map<String, JsonObject> pending = new LinkedHashMap<>();
    private String account = "";
    private Instant snapshotTime = Instant.MIN;

    FriendRequestNotifications(Path file) {
        this.file = file;
        if (!Files.isRegularFile(file)) return;
        try {
            JsonObject data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var entry : data.entrySet()) {
                if (!validXuid(entry.getKey()) || !entry.getValue().isJsonArray()) continue;
                Set<String> seen = new HashSet<>();
                for (JsonElement value : entry.getValue().getAsJsonArray()) {
                    if (value.isJsonPrimitive() && validXuid(value.getAsString())) seen.add(value.getAsString());
                }
                seenByAccount.put(entry.getKey(), seen);
            }
        } catch (IOException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("TogetherWithBedrock could not read friend notification history", e);
        }
    }

    boolean setAccount(String next) {
        if (!validXuid(next)) next = "";
        if (account.equals(next)) return false;
        account = next;
        pending.clear();
        snapshotTime = Instant.MIN;
        return true;
    }

    String account() { return account; }

    boolean accept(String requestedAccount, JsonObject snapshot) {
        if (account.isEmpty() || !account.equals(requestedAccount) || !account.equals(text(snapshot, "accountXuid"))) return false;
        if (!snapshot.has("incoming") || !snapshot.get("incoming").isJsonArray()
                || !snapshot.has("friends") || !snapshot.get("friends").isJsonArray()) return false;
        final Instant updated;
        try { updated = Instant.parse(text(snapshot, "updatedAt")); }
        catch (RuntimeException e) { return false; }
        if (updated.isBefore(snapshotTime)) return false;
        snapshotTime = updated;

        Set<String> friends = new HashSet<>();
        for (JsonElement element : array(snapshot, "friends")) {
            if (element.isJsonObject()) friends.add(text(element.getAsJsonObject(), "xuid"));
        }
        pending.clear();
        for (JsonElement element : array(snapshot, "incoming")) {
            if (!element.isJsonObject()) continue;
            JsonObject request = element.getAsJsonObject();
            String xuid = text(request, "xuid"), relationship = text(request, "relationship");
            if (!validXuid(xuid) || xuid.equals(account) || friends.contains(xuid) || flag(request, "isFriend")) continue;
            if (!relationship.isBlank() && !relationship.equals("incoming")) continue;
            pending.putIfAbsent(xuid, request.deepCopy());
        }
        // A later request from the same player can notify again after the old request has disappeared.
        Set<String> seen = seenByAccount.computeIfAbsent(account, ignored -> new HashSet<>());
        if (seen.retainAll(pending.keySet())) save();
        return true;
    }

    JsonArray unseen() {
        JsonArray result = new JsonArray();
        Set<String> seen = seenByAccount.getOrDefault(account, Set.of());
        pending.forEach((xuid, request) -> { if (!seen.contains(xuid)) result.add(request.deepCopy()); });
        return result;
    }

    Set<String> pendingIds() { return Set.copyOf(pending.keySet()); }

    void shown(String xuid) {
        if (!account.isEmpty() && pending.containsKey(xuid)
                && seenByAccount.computeIfAbsent(account, ignored -> new HashSet<>()).add(xuid)) save();
    }

    void viewedRequests() {
        if (!account.isEmpty() && seenByAccount.computeIfAbsent(account, ignored -> new HashSet<>()).addAll(pending.keySet())) save();
    }

    private void save() {
        JsonObject data = new JsonObject();
        seenByAccount.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonArray ids = new JsonArray(); entry.getValue().stream().sorted().forEach(ids::add);
            data.add(entry.getKey(), ids);
        });
        Path temporary = null;
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "bedrock-friend-notifications-", ".tmp");
            Files.writeString(temporary, data.toString());
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("TogetherWithBedrock could not save friend notification history", e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private static boolean validXuid(String value) { return value != null && value.matches("[0-9]{1,20}"); }
    private static String text(JsonObject object, String key) { try { return object.get(key).getAsString(); } catch (RuntimeException e) { return ""; } }
    private static boolean flag(JsonObject object, String key) { try { return object.get(key).getAsBoolean(); } catch (RuntimeException e) { return false; } }
    private static JsonArray array(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray(); }
}
