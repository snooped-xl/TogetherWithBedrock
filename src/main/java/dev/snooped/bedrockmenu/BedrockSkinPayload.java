package dev.snooped.bedrockmenu;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Optional appearance data. Reject malformed payloads without disconnecting gameplay. */
record BedrockSkinPayload(UUID player, int flags, byte[] skin, byte[] cape) implements CustomPacketPayload {
    static final int MAX_BYTES=600_000, SLIM=1, PERSONA=2, REMOVE=4;
    static final Type<BedrockSkinPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("bedrock_menu","skin_v1"));
    private static final byte[] EMPTY=new byte[0];
    private static final BedrockSkinPayload INVALID=new BedrockSkinPayload(null,0,EMPTY,EMPTY);
    static final StreamCodec<RegistryFriendlyByteBuf,BedrockSkinPayload> CODEC=CustomPacketPayload.codec(
            (p,b)->{b.writeByte(1);b.writeUUID(p.player);b.writeByte(p.flags);b.writeInt(p.skin.length);b.writeBytes(p.skin);b.writeInt(p.cape.length);b.writeBytes(p.cape);},
            BedrockSkinPayload::read);
    private static BedrockSkinPayload read(RegistryFriendlyByteBuf b) {
        if(b.readableBytes()<26 || b.readableBytes()>MAX_BYTES) return invalid(b);
        if(b.readUnsignedByte()!=1) return invalid(b);
        UUID uuid=b.readUUID();int flags=b.readUnsignedByte();
        if((flags&~7)!=0) return invalid(b);
        int skinLength=b.readInt();
        if(skinLength<0 || skinLength>b.readableBytes()-4) return invalid(b);
        // Validate both lengths before allocating either image.
        int capeLength=b.getInt(b.readerIndex()+skinLength);
        if(capeLength<0 || capeLength!=b.readableBytes()-skinLength-4
                || ((flags&REMOVE)!=0 ? skinLength!=0 || capeLength!=0 : skinLength==0)) return invalid(b);
        byte[] skin=new byte[skinLength],cape=new byte[capeLength];
        b.readBytes(skin);b.skipBytes(4);b.readBytes(cape);
        return new BedrockSkinPayload(uuid,flags,skin,cape);
    }
    private static BedrockSkinPayload invalid(RegistryFriendlyByteBuf b) { b.skipBytes(b.readableBytes());return INVALID; }
    boolean valid() { return player!=null; }
    boolean remove() { return (flags&REMOVE)!=0; }
    int bytes() { return skin.length+cape.length; }
    @Override public Type<BedrockSkinPayload> type() { return TYPE; }
    record Capability(int version) implements CustomPacketPayload {
        static final Type<Capability> TYPE=new Type<>(Identifier.fromNamespaceAndPath("bedrock_menu","skin_capability"));
        static final StreamCodec<RegistryFriendlyByteBuf,Capability> CODEC=CustomPacketPayload.codec(
                (p,b)->b.writeByte(p.version), b->{int version=b.readableBytes()==1?b.readUnsignedByte():0;b.skipBytes(b.readableBytes());return new Capability(version);});
        @Override public Type<Capability> type() { return TYPE; }
    }
}
