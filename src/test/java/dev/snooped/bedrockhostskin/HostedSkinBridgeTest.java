package dev.snooped.bedrockhostskin;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HostedSkinBridgeTest {
    @Test void rgbaConversionPreservesColorsAlphaAndBoundedDimensions() throws Exception {
        byte[] rgba = new byte[]{(byte) 0xab, 0x23, 0x45, (byte) 0xff, 0x11, 0x22, 0x33, 0x44};
        var png = ImageIO.read(new ByteArrayInputStream(HostedSkinBridge.png(rgba, 2, 1)));
        assertEquals(0xffab2345, png.getRGB(0, 0));
        assertEquals(0x44112233, png.getRGB(1, 0));
        assertFalse(HostedSkinBridge.validRgba(rgba, 0, 1));
        assertFalse(HostedSkinBridge.validRgba(rgba, 257, 1));
        assertFalse(HostedSkinBridge.validRgba(rgba, 2, 2));
        assertThrows(java.io.IOException.class, () -> HostedSkinBridge.png(rgba, Integer.MAX_VALUE, 1));
    }

    @Test void payloadUsesSharedBigEndianContractAndRemovalHasNoImages() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        UUID uuid = UUID.fromString("12345678-1234-1234-9234-123456789abc");
        try {
            var payload = new SkinPayload(uuid, 3, new byte[]{11, 22}, new byte[]{33});
            SkinPayload.CODEC.encode(buffer, payload);
            assertEquals(29, buffer.readableBytes());
            assertEquals(1, buffer.getByte(0));
            assertEquals(uuid.getMostSignificantBits(), buffer.getLong(1));
            assertEquals(uuid.getLeastSignificantBits(), buffer.getLong(9));
            assertEquals(3, buffer.getByte(17));
            assertEquals(2, buffer.getInt(18));
            var decoded = SkinPayload.CODEC.decode(buffer);
            assertEquals(uuid, decoded.uuid());
            assertArrayEquals(payload.skin(), decoded.skin());
            assertArrayEquals(payload.cape(), decoded.cape());
            buffer.clear();
            SkinPayload.CODEC.encode(buffer, SkinPayload.remove(uuid));
            assertEquals(26, buffer.readableBytes());
            assertEquals(4, SkinPayload.CODEC.decode(buffer).flags());
        } finally { buffer.release(); }
    }

    @Test void invalidCapabilityAndOversizedPayloadAreRejectedBeforeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> new SkinPayload(UUID.randomUUID(), 8, new byte[0], new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new SkinPayload(UUID.randomUUID(), 0, new byte[600_001], new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new SkinPayload(UUID.randomUUID(), 0, new byte[599_975], new byte[0]));
        assertDoesNotThrow(() -> new SkinPayload(UUID.randomUUID(), 0, new byte[599_974], new byte[0]));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeByte(1); buffer.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> SkinPayload.Capability.CODEC.decode(buffer));
            buffer.clear();
            buffer.writeByte(1); buffer.writeUUID(UUID.randomUUID()); buffer.writeByte(0);
            buffer.writeInt(Integer.MAX_VALUE); buffer.writeInt(0);
            assertThrows(IllegalArgumentException.class, () -> SkinPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
