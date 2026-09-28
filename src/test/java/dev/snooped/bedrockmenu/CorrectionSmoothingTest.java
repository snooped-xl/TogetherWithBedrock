package dev.snooped.bedrockmenu;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorrectionSmoothingTest {
    @Test void thresholdIncludesTwoPointEightAndLargerCorrectionsSnap() {
        var s=new CorrectionSmoothing();long now=1_000_000_000L;
        s.correct(new Vec3(2.8,0,0),Vec3.ZERO,now);
        assertEquals(2.8,s.offset(now).x,1e-12);
        s.correct(new Vec3(2.800001,0,0),Vec3.ZERO,now+1);
        assertEquals(Vec3.ZERO,s.offset(now+1));
        s.correct(new Vec3(2,2,0),Vec3.ZERO,now+2);
        assertEquals(Vec3.ZERO,s.offset(now+2)); // Full 3D distance, not per-axis limits.
    }
    @Test void smallCorrectionPreservesVisualPositionThenConvergesWithoutOvershoot() {
        var s=new CorrectionSmoothing();long now=2_000_000_000L;
        Vec3 before=new Vec3(50,64,-20),after=new Vec3(49.5,64.2,-20.1);
        s.correct(before,after,now);
        assertEquals(before,after.add(s.offset(now)));
        double previous=s.offset(now).length();
        for(long elapsed=1_000_000;elapsed<=CorrectionSmoothing.DURATION_NANOS;elapsed+=1_000_000) {
            var offset=s.offset(now+elapsed);
            assertTrue(offset.length()<=previous);assertTrue(offset.x>=0);assertTrue(offset.y<=0);assertTrue(offset.z>=0);
            previous=offset.length();
        }
        assertEquals(Vec3.ZERO,s.offset(now+CorrectionSmoothing.DURATION_NANOS));
        assertEquals(Vec3.ZERO,s.offset(now+1_000_000_000));
    }
    @Test void renderSamplingRateDoesNotChangeEasing() {
        var a=new CorrectionSmoothing();var b=new CorrectionSmoothing();long now=3_000_000_000L;
        a.correct(new Vec3(.5,0,0),Vec3.ZERO,now);b.correct(new Vec3(.5,0,0),Vec3.ZERO,now);
        for(long elapsed=1_000_000;elapsed<90_000_000;elapsed+=1_000_000) a.offset(now+elapsed);
        assertEquals(a.offset(now+90_000_000),b.offset(now+90_000_000));
        assertEquals(.0625,a.offset(now+90_000_000).x,1e-12);
    }
    @Test void successiveCorrectionsKeepVisualContinuityButCannotAccumulatePastLimit() {
        var s=new CorrectionSmoothing();long now=4_000_000_000L;
        s.correct(new Vec3(.4,0,0),Vec3.ZERO,now);
        long later=now+30_000_000;Vec3 previous=s.offset(later);
        s.correct(new Vec3(9.8,0,0),new Vec3(9.5,0,0),later);
        assertEquals(9.8+previous.x,9.5+s.offset(later).x,1e-12);
        s.correct(new Vec3(2.6,0,0),Vec3.ZERO,later);
        assertEquals(Vec3.ZERO,s.offset(later));
    }
    @Test void opposingCorrectionsCancelRatherThanCreatingAnotherCameraJump() {
        var s=new CorrectionSmoothing();long now=5_000_000_000L;
        s.correct(new Vec3(.5,0,0),Vec3.ZERO,now);
        s.correct(Vec3.ZERO,new Vec3(.5,0,0),now);
        assertEquals(Vec3.ZERO,s.offset(now));
    }
    @Test void resetAndInvalidStatesNeverLeakAnOffsetIntoAnotherWorld() {
        var s=new CorrectionSmoothing();long now=6_000_000_000L;
        s.correct(new Vec3(.3,0,0),Vec3.ZERO,now);s.reset();assertEquals(Vec3.ZERO,s.offset(now));
        s.correct(new Vec3(Double.NaN,0,0),Vec3.ZERO,now);assertEquals(Vec3.ZERO,s.offset(now));
        s.correct(Vec3.ZERO,new Vec3(0,Double.POSITIVE_INFINITY,0),now);assertEquals(Vec3.ZERO,s.offset(now));
        s.correct(new Vec3(.2,0,0),Vec3.ZERO,now);assertEquals(Vec3.ZERO,s.offset(now-1));
    }
}
