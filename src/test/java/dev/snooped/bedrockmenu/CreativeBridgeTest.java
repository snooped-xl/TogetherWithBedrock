package dev.snooped.bedrockmenu;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CreativeBridgeTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        // Offline codec fixtures do not load a world's datapack component defaults.
        for (var item : List.of(Items.DIAMOND_AXE, Items.STONE, Items.WRITABLE_BOOK))
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
    }
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)); }
    @Test void usesTheNegotiatedProxyChannels() {
        assertEquals("bedrockmenu:creative_bridge", CreativeBridge.Capability.TYPE.id().toString());
        assertEquals("bedrockmenu:creative_gesture", CreativeBridge.Gesture.TYPE.id().toString());
        assertEquals("bedrockmenu:creative_state", CreativeBridge.State.TYPE.id().toString());
    }
    @Test void preservesCreativePickupCursorWhileSourceBecomesEmpty() {
        var item = new ItemStack(Items.DIAMOND_AXE, 1);
        var gesture = new CreativeBridge.Gesture(7, List.of(new CreativeBridge.SlotValue(36, ItemStack.EMPTY)), item, List.of());
        var buffer = buffer();
        try {
            CreativeBridge.Gesture.CODEC.encode(buffer, gesture);
            assertEquals(1, buffer.readVarInt()); assertEquals(7, buffer.readVarInt()); assertEquals(1, buffer.readVarInt());
            assertEquals(36, buffer.readShort()); assertTrue(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(buffer).isEmpty());
            assertTrue(ItemStack.matches(item, ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(buffer)));
            assertEquals(0, buffer.readVarInt()); assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
    @Test void dropWithoutAnyInventoryChangeIsStillTransmitted() {
        var item = new ItemStack(Items.STONE, 64);
        var buffer = buffer();
        try {
            CreativeBridge.Gesture.CODEC.encode(buffer, new CreativeBridge.Gesture(8, List.of(), ItemStack.EMPTY, List.of(item)));
            var decoded = CreativeBridge.Gesture.CODEC.decode(buffer);
            assertEquals(8, decoded.sequence()); assertTrue(decoded.slots().isEmpty()); assertEquals(1, decoded.drops().size());
            assertTrue(ItemStack.matches(item, decoded.drops().getFirst())); assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
    @Test void rejectsOversizedAndDuplicateCorrectionSlots() {
        var buffer = buffer();
        try {
            buffer.writeVarInt(1); buffer.writeVarInt(9); buffer.writeBoolean(false); buffer.writeVarInt(47);
            assertThrows(IllegalArgumentException.class, () -> CreativeBridge.State.CODEC.decode(buffer));
            buffer.clear();
            buffer.writeVarInt(1); buffer.writeVarInt(9); buffer.writeBoolean(false); buffer.writeVarInt(2);
            for (int i=0;i<2;i++) { buffer.writeShort(36); ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(buffer, ItemStack.EMPTY); }
            assertThrows(IllegalArgumentException.class, () -> CreativeBridge.State.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void rejectReplyCarriesBothInventoryAndCursor() {
        var item = new ItemStack(Items.WRITABLE_BOOK,1);
        var buffer = buffer();
        try {
            CreativeBridge.State.CODEC.encode(buffer,new CreativeBridge.State(9,false,List.of(new CreativeBridge.SlotValue(36,item)),ItemStack.EMPTY));
            var result=CreativeBridge.State.CODEC.decode(buffer);
            assertFalse(result.accepted()); assertTrue(result.carried().isEmpty());
            assertTrue(ItemStack.matches(item,result.slots().getFirst().item())); assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
}
