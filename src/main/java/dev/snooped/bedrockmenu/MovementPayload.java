// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tick-end telemetry for our local ViaBedrock connection; never changes client physics. */
record MovementPayload(double x, double y, double z, int flags, Input input, int environment, int events, long sequence) implements CustomPacketPayload {
    MovementPayload(double x,double y,double z,int flags,Input input,int environment,int events) { this(x,y,z,flags,input,environment,events,0); }
    MovementPayload(double x, double y, double z, int flags) { this(x, y, z, flags, null, -1, -1); }
    MovementPayload(double x, double y, double z, int flags, Input input) { this(x,y,z,flags,input,-1,-1); }
    MovementPayload(double x,double y,double z,int flags,Input input,int environment) { this(x,y,z,flags,input,environment,-1); }
    record Input(float left, float forward, float rawLeft, float rawForward, float yaw, float pitch) {
        Input(float left,float forward,float rawLeft,float rawForward,float yaw) { this(left,forward,rawLeft,rawForward,yaw,Float.NaN); }
    }
    // createType(String) assumes the minecraft namespace; use our explicit namespace.
    static final Type<MovementPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bedrock_menu", "movement_v1"));
    static final StreamCodec<RegistryFriendlyByteBuf, MovementPayload> CODEC = CustomPacketPayload.codec(
            MovementPayload::write, MovementPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(input == null ? 1 : environment < 0 ? 2 : events < 0 ? 3 : sequence>0 ? 5 : 4);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeByte(flags);
        if (input != null) {
            buffer.writeFloat(input.left()); buffer.writeFloat(input.forward());
            buffer.writeFloat(input.rawLeft()); buffer.writeFloat(input.rawForward()); buffer.writeFloat(input.yaw());
            if (environment >= 0) { buffer.writeByte(environment); buffer.writeFloat(input.pitch()); if(events>=0) buffer.writeByte(events); }
        }
        if(input!=null && environment>=0 && events>=0 && sequence>0) buffer.writeLong(sequence);
    }

    private static MovementPayload read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length != 26 && length != 46 && length != 51 && length != 52 && length != 60) throw new IllegalArgumentException("Invalid movement telemetry length");
        int version = buffer.readUnsignedByte();
        if ((version != 1 || length != 26) && (version != 2 || length != 46)
                && (version != 3 || length != 51) && (version != 4 || length != 52) && (version != 5 || length != 60)) {
            throw new IllegalArgumentException("Invalid movement telemetry payload");
        }
        double x=buffer.readDouble(), y=buffer.readDouble(), z=buffer.readDouble(); int flags=buffer.readUnsignedByte();
        Input input=version>=2?new Input(buffer.readFloat(),buffer.readFloat(),buffer.readFloat(),buffer.readFloat(),buffer.readFloat()):null;
        int environment=version>=3?buffer.readUnsignedByte():-1;
        if (version>=3) input=new Input(input.left(),input.forward(),input.rawLeft(),input.rawForward(),input.yaw(),buffer.readFloat());
        int events=version>=4?buffer.readUnsignedByte():-1;
        if(events>1) throw new IllegalArgumentException("Invalid movement events");
        long sequence=version==5?buffer.readLong():0;
        if(version==5 && sequence<=0) throw new IllegalArgumentException("Invalid movement sequence");
        return new MovementPayload(x,y,z,flags,input,environment,events,sequence);
    }

    @Override public Type<MovementPayload> type() { return TYPE; }
}
