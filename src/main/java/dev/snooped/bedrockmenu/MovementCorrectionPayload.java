package dev.snooped.bedrockmenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** An authoritative end-of-tick state, identified in this connection's client timeline. */
record MovementCorrectionPayload(long sequence, Vec3 position, Vec3 velocity, boolean onGround) implements CustomPacketPayload {
    static final Type<MovementCorrectionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("bedrock_menu","movement_correction"));
    static final StreamCodec<RegistryFriendlyByteBuf,MovementCorrectionPayload> CODEC=CustomPacketPayload.codec(
            (p,b)->{b.writeByte(1);b.writeLong(p.sequence);write(b,p.position);write(b,p.velocity);b.writeBoolean(p.onGround);},
            b->{if(b.readableBytes()!=58 || b.readUnsignedByte()!=1) throw new IllegalArgumentException("Invalid movement correction");
                long sequence=b.readLong();Vec3 position=read(b),velocity=read(b);int ground=b.readUnsignedByte();
                if(sequence<=0 || !finite(position) || !finite(velocity) || ground>1) throw new IllegalArgumentException("Invalid movement correction state");
                return new MovementCorrectionPayload(sequence,position,velocity,ground!=0);});
    private static void write(RegistryFriendlyByteBuf b,Vec3 v) {b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 read(RegistryFriendlyByteBuf b) {return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    static boolean finite(Vec3 v) {return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    @Override public Type<MovementCorrectionPayload> type(){return TYPE;}
}
