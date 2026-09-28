package dev.snooped.bedrockmenu;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementReplayTest {
    private static final Vec3 ONE=new Vec3(1,1,1);
    @Test void roundTripsSequenceWithoutChangingLegacyLayout() {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            var p=new MovementPayload(.1,.2,.3,9,new MovementPayload.Input(0,.98F,0,1,90,0),7,1,0x102030405L);
            MovementPayload.CODEC.encode(b,p);
            assertEquals(60,b.readableBytes());assertEquals(5,b.getUnsignedByte(0));assertEquals(p.sequence(),b.getLong(52));
            assertEquals(p,MovementPayload.CODEC.decode(b));b.clear();
            MovementPayload.CODEC.encode(b,p);b.setLong(52,0);
            assertThrows(IllegalArgumentException.class,()->MovementPayload.CODEC.decode(b));b.clear();
            MovementPayload.CODEC.encode(b,p);b.setByte(0,4);
            assertThrows(IllegalArgumentException.class,()->MovementPayload.CODEC.decode(b));
        } finally {b.release();}
    }
    @Test void correctionCodecMatchesProxyAndRejectsCorruption() {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            var p=new MovementCorrectionPayload(29,new Vec3(-4900,64.00001,-1650),new Vec3(.12,-.0784,.15),true);
            MovementCorrectionPayload.CODEC.encode(b,p);assertEquals(58,b.readableBytes());assertEquals(29,b.getLong(1));
            assertEquals(p,MovementCorrectionPayload.CODEC.decode(b));b.clear();
            MovementCorrectionPayload.CODEC.encode(b,p);b.setDouble(9,Double.NaN);
            assertThrows(IllegalArgumentException.class,()->MovementCorrectionPayload.CODEC.decode(b));b.clear();
            MovementCorrectionPayload.CODEC.encode(b,p);b.setByte(57,2);
            assertThrows(IllegalArgumentException.class,()->MovementCorrectionPayload.CODEC.decode(b));
        } finally {b.release();}
    }
    @Test void historicalCorrectionRetainsPendingMotionAndTurns() {
        // Correct tick N's velocity, then use two different recorded inputs. A direct teleport
        // loses both ticks; merely adding the old displacements retains the incorrect velocity.
        Vec3 pos=new Vec3(10,64,20),corrected=new Vec3(.15,-.0784,0),drag=new Vec3(.546,.98,.546),gravity=new Vec3(0,-.0784,0);
        var first=MovementReplay.step(pos,corrected,ONE,new Vec3(.1,0,0),drag,gravity,v->new Vec3(v.x,0,v.z));
        var second=MovementReplay.step(first.position(),first.velocity(),ONE,new Vec3(0,0,.1),drag,gravity,v->new Vec3(v.x,0,v.z));
        assertEquals(10.3865,second.position().x,1e-9);assertEquals(20.1,second.position().z,1e-9);
        assertEquals(.074529,second.velocity().x,1e-9);assertEquals(.0546,second.velocity().z,1e-9);
        assertTrue(second.ground());assertTrue(second.vertical());assertFalse(second.horizontal());
    }
    @Test void jumpResetAndWaterSteeringDoNotReapplyOldVerticalVelocity() {
        var jump=MovementReplay.step(Vec3.ZERO,new Vec3(.1,-.3,0),new Vec3(1,0,1),new Vec3(0,.42,0),new Vec3(.91,.98,.91),new Vec3(0,-.0784,0),v->v);
        assertEquals(.42,jump.position().y,1e-9);assertEquals(.3332,jump.velocity().y,1e-9);assertFalse(jump.ground());
        var swim=MovementReplay.step(Vec3.ZERO,new Vec3(0,.1,0),new Vec3(1,.94,1),new Vec3(0,.03,0),new Vec3(.9,.8,.9),Vec3.ZERO,v->v);
        assertEquals(.124,swim.position().y,1e-9);assertEquals(.0992,swim.velocity().y,1e-9);
    }
    @Test void wallCollisionZerosOnlyBlockedVelocityAndNeverRepeatsAnAction() {
        int[] calls={0};
        var state=MovementReplay.step(Vec3.ZERO,new Vec3(.3,0,.2),ONE,Vec3.ZERO,new Vec3(.8,.8,.8),Vec3.ZERO,v->{calls[0]++;return new Vec3(.05,v.y,v.z);});
        assertEquals(1,calls[0]);assertEquals(.05,state.position().x);assertEquals(0,state.velocity().x);assertEquals(.16,state.velocity().z,1e-9);assertTrue(state.horizontal());
    }
    @Test void epochsOrderingAndUnsentFramesCannotBeCorrected() {
        assertTrue(MovementReplay.accepts(25,20,24,30));
        assertFalse(MovementReplay.accepts(24,20,24,30));
        assertFalse(MovementReplay.accepts(19,20,18,30));
        assertFalse(MovementReplay.accepts(31,20,24,30));
        assertFalse(MovementReplay.accepts(0,0,-1,30));
        assertFalse(BedrockMovement.physics(null));
    }
}
