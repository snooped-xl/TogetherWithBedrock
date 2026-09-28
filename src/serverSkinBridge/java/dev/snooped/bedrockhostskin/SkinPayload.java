// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockhostskin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

record SkinPayload(UUID uuid, int flags, byte[] skin, byte[] cape) implements CustomPacketPayload {
    static final int MAX_BYTES = 600_000;
    static final Type<SkinPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bedrock_menu", "skin_v1"));
    static final StreamCodec<RegistryFriendlyByteBuf, SkinPayload> CODEC = CustomPacketPayload.codec(SkinPayload::write, SkinPayload::read);

    SkinPayload {
        if (uuid == null || (flags & ~7) != 0 || skin.length + (long) cape.length + 26 > MAX_BYTES) throw new IllegalArgumentException("Invalid skin payload");
    }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeByte(1); b.writeUUID(uuid); b.writeByte(flags);
        b.writeInt(skin.length); b.writeBytes(skin); b.writeInt(cape.length); b.writeBytes(cape);
    }
    private static SkinPayload read(RegistryFriendlyByteBuf b) {
        if (b.readableBytes() < 26 || b.readableBytes() > MAX_BYTES || b.readUnsignedByte() != 1) throw new IllegalArgumentException("Invalid skin payload size/version");
        UUID uuid = b.readUUID(); int flags = b.readUnsignedByte();
        byte[] skin = bytes(b), cape = bytes(b);
        if (b.isReadable()) throw new IllegalArgumentException("Trailing skin data");
        return new SkinPayload(uuid, flags, skin, cape);
    }
    private static byte[] bytes(RegistryFriendlyByteBuf b) {
        int length = b.readInt();
        if (length < 0 || length > MAX_BYTES - 26 || length > b.readableBytes()) throw new IllegalArgumentException("Invalid skin image size");
        byte[] bytes = new byte[length]; b.readBytes(bytes); return bytes;
    }
    static SkinPayload remove(UUID uuid) { return new SkinPayload(uuid, 4, new byte[0], new byte[0]); }
    int bytes() { return skin.length + cape.length; }
    @Override public Type<SkinPayload> type() { return TYPE; }

    record Capability(int version) implements CustomPacketPayload {
        static final Type<Capability> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bedrock_menu", "skin_capability"));
        static final StreamCodec<RegistryFriendlyByteBuf, Capability> CODEC = CustomPacketPayload.codec(
                (p, b) -> b.writeByte(p.version()), b -> {
                    if (b.readableBytes() != 1) throw new IllegalArgumentException("Invalid skin capability");
                    return new Capability(b.readUnsignedByte());
                });
        @Override public Type<Capability> type() { return TYPE; }
    }
}
