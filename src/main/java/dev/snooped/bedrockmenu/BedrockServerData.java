// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;

/** Vanilla's pack confirmation updates this object, including during a proxy transfer. */
final class BedrockServerData extends ServerData {
    private final PackPreferences preferences;
    private final String preferenceIdentity;

    BedrockServerData(String name, String address, String preferenceIdentity, PackPreferences preferences) {
        super(name, address, Type.OTHER);
        this.preferences = preferences;
        this.preferenceIdentity = preferenceIdentity;
        super.setResourcePackStatus(ServerPackStatus.valueOf(preferences.get(preferenceIdentity).name()));
    }

    @Override
    public void setResourcePackStatus(ServerPackStatus status) {
        super.setResourcePackStatus(status);
        try {
            preferences.set(preferenceIdentity, PackPreferences.Choice.valueOf(status.name()));
        } catch (IOException e) {
            // Saving a preference must never interrupt joining or accepting the pack.
            com.mojang.logging.LogUtils.getLogger().warn("Could not save Bedrock resource-pack preference", e);
        }
    }
}
