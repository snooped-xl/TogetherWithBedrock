package dev.snooped.bedrockmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;

class MovementPayloadTest {
    @Test void explicitJumpRoundTripsAndOldVersionsCannotMasqueradeAsV4() {
        var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            for(int event=0;event<=1;event++) {
                var p=new MovementPayload(0,.3332,.2,8,new MovementPayload.Input(0,.98F,0,1,30,10),0,event);
                MovementPayload.CODEC.encode(b,p);
                assertEquals(52,b.readableBytes()); assertEquals(4,b.getUnsignedByte(0));
                assertEquals(p,MovementPayload.CODEC.decode(b)); b.clear();
                MovementPayload.CODEC.encode(b,p); b.setByte(0,3);
                assertThrows(IllegalArgumentException.class,()->MovementPayload.CODEC.decode(b)); b.clear();
                MovementPayload.CODEC.encode(b,p); b.setByte(51,2);
                assertThrows(IllegalArgumentException.class,()->MovementPayload.CODEC.decode(b)); b.clear();
            }
        } finally {b.release();}
    }
    @Test void waterSurfaceUsesBodyContactAndCompactProbeNeverInverts() {
        // Feet at 0.6 in a full source (surface 8/9): Java detects contact;
        // Bedrock's inset probe does not until the player sinks further.
        assertTrue(.601 < 8D/9D);
        assertTrue(BedrockMovement.waterProbeMin(.6,2.4)>8D/9D);
        assertTrue(BedrockMovement.waterProbeMin(.4,2.2)<8D/9D);
        assertEquals(.3,BedrockMovement.waterProbeMin(0,.6),1e-8);
        assertEquals(.3,BedrockMovement.waterProbeMax(0,.6),1e-8);
        assertEquals(.401,BedrockMovement.waterProbeMin(0,1.8),1e-8);
        assertEquals(1.399,BedrockMovement.waterProbeMax(0,1.8),1e-8);
    }
    @Test void waterGravityFollowsSwimmingRatherThanSprintKeyOrJavaDeadzone() {
        var v=new net.minecraft.world.phys.Vec3(.1,.004,.2);
        assertEquals(-.001,BedrockMovement.waterGravity(v,false,.08).y,1e-9);
        assertEquals(v,BedrockMovement.waterGravity(v,true,.08));
        assertEquals(v,BedrockMovement.waterGravity(v,false,0));
        assertFalse(BedrockMovement.physics(null));
    }
    @Test
    void initializesWithTheProxyChannelNamespace() {
        // Exercises the static initializer which previously crashed client startup.
        assertEquals("bedrock_menu:movement_v1", MovementPayload.TYPE.id().toString());
        assertNotNull(MovementPayload.CODEC);
    }
    @Test void retainsV1AndRoundTripsNegotiatedInput() {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            var old=new MovementPayload(.1,.3332,.2,8);
            MovementPayload.CODEC.encode(buffer,old);
            assertEquals(26,buffer.readableBytes()); assertEquals(old,MovementPayload.CODEC.decode(buffer));
            buffer.clear();
            var detailed=new MovementPayload(.1,.2,.3,10,new MovementPayload.Input(-.588F,.784F,-.6F,.8F,37.5F));
            MovementPayload.CODEC.encode(buffer,detailed);
            assertEquals(46,buffer.readableBytes()); assertEquals(detailed,MovementPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeZero(46);buffer.setByte(0,1);
            assertThrows(IllegalArgumentException.class,()->MovementPayload.CODEC.decode(buffer));
        } finally {buffer.release();}
    }
    @Test void waterAndMovementPitchRoundTripWithStrictVersionLength() {
        var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            var p=new MovementPayload(.1,-.005,.2,8,new MovementPayload.Input(.1F,.97F,.102F,.994F,30F,-45F),7);
            MovementPayload.CODEC.encode(b,p);
            assertEquals(51,b.readableBytes()); assertEquals(3,b.getUnsignedByte(0));
            assertEquals(p,MovementPayload.CODEC.decode(b));
            b.clear();MovementPayload.CODEC.encode(b,p);b.setByte(0,2);
            assertThrows(IllegalArgumentException.class,()->MovementPayload.CODEC.decode(b));
            b.clear();b.writeZero(50);b.setByte(0,3);
            assertThrows(IllegalArgumentException.class,()->MovementPayload.CODEC.decode(b));
        } finally {b.release();}
    }
    @Test void ladderAscentPreservesOtherMotionAndJavaConnections() {
        assertEquals(.2,BedrockMovement.ascendingVelocity(.1176,true),1e-8);
        assertEquals(.1176,BedrockMovement.ascendingVelocity(.1176,false),1e-8);
        assertEquals(.3332,BedrockMovement.ascendingVelocity(.3332,true),1e-8);
        assertFalse(BedrockMovement.ladderPhysics(null)); // no proxy/negotiation: ordinary Java stays untouched
    }
}
