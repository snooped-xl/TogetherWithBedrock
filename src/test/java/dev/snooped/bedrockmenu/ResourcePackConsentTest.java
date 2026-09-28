package dev.snooped.bedrockmenu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePackConsentTest {
    @TempDir Path directory;
    @Test void onlyTheExplicitProxyConsentMarkerIsSkipped() {
        String hash = "0123456789012345678901234567890123456789";
        UUID id = UUID.nameUUIDFromBytes(("viabedrock:" + hash).getBytes(StandardCharsets.UTF_8));
        assertTrue(ResourcePackConsent.isConsentMarker(id, "http://127.0.0.1:123/?token=test&viabedrock-consent=1", hash));
        assertFalse(ResourcePackConsent.isConsentMarker(id, "http://127.0.0.1:123/?token=test", hash));
        assertFalse(ResourcePackConsent.isConsentMarker(UUID.randomUUID(), "http://127.0.0.1:123/?token=test&viabedrock-consent=1", hash));
        assertFalse(ResourcePackConsent.isConsentMarker(id, "http://127.0.0.1:123/?token=test&viabedrock-consent=1", ""));
    }

    @Test void respectsConsentAndNeverInterceptsRealPacksOrJavaServers() {
        net.minecraft.SharedConstants.tryDetectVersion();
        String hash = "0123456789012345678901234567890123456789";
        UUID id = UUID.nameUUIDFromBytes(("viabedrock:" + hash).getBytes(StandardCharsets.UTF_8));
        var marker = new ClientboundResourcePackPushPacket(id, "http://127.0.0.1:123/?token=test&viabedrock-consent=1", hash, false, Optional.empty());
        var connection = new CapturingConnection();
        var server = new BedrockServerData("Test", "realm-1", "realm-1", new PackPreferences(directory.resolve("preferences.json")));
        assertFalse(ResourcePackConsent.handle(connection, server, marker));
        assertTrue(connection.sent.isEmpty());
        server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        assertTrue(ResourcePackConsent.handle(connection, server, marker));
        assertEquals(List.of(ServerboundResourcePackPacket.Action.ACCEPTED, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED), connection.sent);
        connection.sent.clear();
        server.setResourcePackStatus(ServerData.ServerPackStatus.DISABLED);
        assertTrue(ResourcePackConsent.handle(connection, server, marker));
        assertEquals(List.of(ServerboundResourcePackPacket.Action.DECLINED), connection.sent);
        connection.sent.clear();
        server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        assertFalse(ResourcePackConsent.handle(connection, server, new ClientboundResourcePackPushPacket(id, "http://127.0.0.1:123/?token=test", hash, false, Optional.empty())));
        assertFalse(ResourcePackConsent.handle(connection, new ServerData("Java", "localhost", ServerData.Type.OTHER), marker));
        assertFalse(ResourcePackConsent.handle(connection, server, new ClientboundResourcePackPushPacket(id, marker.url(), hash, true, Optional.empty())));
        connection.remote = new InetSocketAddress("192.0.2.1", 123);
        assertFalse(ResourcePackConsent.handle(connection, server, marker));
        assertTrue(connection.sent.isEmpty());
    }

    private static class CapturingConnection extends Connection {
        SocketAddress remote = new InetSocketAddress("127.0.0.1", 123);
        final List<ServerboundResourcePackPacket.Action> sent = new ArrayList<>();
        CapturingConnection() { super(PacketFlow.CLIENTBOUND); }
        @Override public SocketAddress getRemoteAddress() { return remote; }
        @Override public void send(Packet<?> packet) { sent.add(((ServerboundResourcePackPacket) packet).action()); }
    }
}
