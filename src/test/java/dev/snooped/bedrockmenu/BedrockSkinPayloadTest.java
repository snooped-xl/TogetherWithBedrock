package dev.snooped.bedrockmenu;

import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockSkinPayloadTest {
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY); }
    @Test void roundTripsSlimPersonaAndCapeWithoutChangingUuidOrBytes() {
        var b=buffer();UUID uuid=UUID.fromString("fedcba98-7654-3210-1234-56789abcdef0");
        try {
            byte[] skin={1,2,3},cape={4,5};
            var p=new BedrockSkinPayload(uuid,3,skin,cape);BedrockSkinPayload.CODEC.encode(b,p);
            assertEquals(31,b.readableBytes());assertEquals(1,b.readUnsignedByte());assertEquals(uuid,b.readUUID());
            assertEquals(3,b.readUnsignedByte());assertEquals(3,b.readInt());b.readerIndex(0);
            var actual=BedrockSkinPayload.CODEC.decode(b);
            assertEquals(uuid,actual.player());assertEquals(3,actual.flags());assertArrayEquals(skin,actual.skin());assertArrayEquals(cape,actual.cape());
            assertFalse(b.isReadable());assertFalse(actual.remove());
        } finally { b.release(); }
    }
    @Test void malformedAndOversizedOptionalPayloadsAreDiscardedWithoutDisconnecting() {
        var source=buffer();
        try {
            BedrockSkinPayload.CODEC.encode(source,new BedrockSkinPayload(UUID.randomUUID(),0,new byte[]{7,8,9},new byte[]{10}));
            byte[] wire=new byte[source.readableBytes()];source.getBytes(0,wire);
            for(int length=0;length<wire.length;length++) {
                var b=buffer();try {b.writeBytes(wire,0,length);assertFalse(BedrockSkinPayload.CODEC.decode(b).valid());assertFalse(b.isReadable());} finally{b.release();}
            }
            for(int offset:new int[]{18,25}) {
                var b=buffer();try {b.writeBytes(wire);b.setInt(offset,Integer.MAX_VALUE);assertFalse(BedrockSkinPayload.CODEC.decode(b).valid());} finally{b.release();}
            }
            for(int[] mutation:new int[][]{{0,2},{17,8},{17,4}}) {
                var b=buffer();try {b.writeBytes(wire);b.setByte(mutation[0],mutation[1]);assertFalse(BedrockSkinPayload.CODEC.decode(b).valid());} finally{b.release();}
            }
            source.clear();source.writeZero(BedrockSkinPayload.MAX_BYTES+1);
            assertFalse(BedrockSkinPayload.CODEC.decode(source).valid());assertFalse(source.isReadable());
        } finally {source.release();}
    }
    @Test void removalsAreAtomicAndCapabilitiesRequireExactVersionByte() {
        var b=buffer();try {
            var p=new BedrockSkinPayload(UUID.randomUUID(),4,new byte[0],new byte[0]);BedrockSkinPayload.CODEC.encode(b,p);
            assertTrue(BedrockSkinPayload.CODEC.decode(b).remove());
            b.clear();b.writeByte(1);assertEquals(1,BedrockSkinPayload.Capability.CODEC.decode(b).version());
            b.clear();b.writeByte(1);b.writeByte(0);assertEquals(0,BedrockSkinPayload.Capability.CODEC.decode(b).version());assertFalse(b.isReadable());
        } finally {b.release();}
    }
    @Test void hostedProducerUsesTheSameWireFormat() throws Exception {
        Class<?> producer=Class.forName("dev.snooped.bedrockhostskin.SkinPayload");
        var ctor=producer.getDeclaredConstructor(UUID.class,int.class,byte[].class,byte[].class);ctor.setAccessible(true);
        var field=producer.getDeclaredField("CODEC");field.setAccessible(true);
        @SuppressWarnings("unchecked") var codec=(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,Object>)field.get(null);
        var b=buffer();try {
            UUID uuid=UUID.randomUUID();codec.encode(b,ctor.newInstance(uuid,1,new byte[]{42},new byte[0]));
            var decoded=BedrockSkinPayload.CODEC.decode(b);assertTrue(decoded.valid());assertEquals(uuid,decoded.player());assertEquals(1,decoded.flags());assertArrayEquals(new byte[]{42},decoded.skin());
        } finally {b.release();}
    }
}
