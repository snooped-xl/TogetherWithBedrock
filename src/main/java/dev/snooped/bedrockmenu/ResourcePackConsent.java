// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The proxy's empty consent marker is not a resource pack that needs a second full reload. */
public final class ResourcePackConsent {
    private ResourcePackConsent() { }

    public static boolean handle(Connection connection, ServerData server, ClientboundResourcePackPushPacket packet) {
        if (!(server instanceof BedrockServerData) || packet.required()
                || !(connection.getRemoteAddress() instanceof InetSocketAddress remote)
                || remote.getAddress() == null || !remote.getAddress().isLoopbackAddress()
                || !isConsentMarker(packet.id(), packet.url(), packet.hash())) return false;
        ServerData.ServerPackStatus choice = server.getResourcePackStatus();
        // First-time consent stays with vanilla's prompt, including its normal preference saving.
        if (choice == ServerData.ServerPackStatus.PROMPT) return false;
        if (choice == ServerData.ServerPackStatus.DISABLED) {
            connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DECLINED));
        } else {
            connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
            connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        }
        // The real translated pack still goes through vanilla's SHA-1 checked disk cache and reload.
        return true;
    }

    static boolean isConsentMarker(UUID id, String url, String hash) {
        return url.endsWith("&viabedrock-consent=1") && hash.matches("[0-9a-f]{40}")
                && id.equals(UUID.nameUUIDFromBytes(("viabedrock:" + hash).getBytes(StandardCharsets.UTF_8)));
    }
}
