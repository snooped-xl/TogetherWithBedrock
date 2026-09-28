package dev.snooped.bedrockmenu;

import net.minecraft.world.phys.Vec3;

/** Presentation-only correction error; simulation and outgoing positions never read this. */
final class CorrectionSmoothing {
    static final double MAX_DISTANCE=2.8;
    static final long DURATION_NANOS=180_000_000L;
    private Vec3 error=Vec3.ZERO;
    private long started;

    void correct(Vec3 before,Vec3 after,long now) {
        Vec3 delta=before.subtract(after);
        Vec3 combined=offset(now).add(delta);
        // Cap both one correction and a burst's accumulated visual separation.
        if(!small(delta) || !small(combined)) {reset();return;}
        error=combined;started=now;
    }
    Vec3 offset(long now) {
        long elapsed=now-started;
        if(elapsed<0 || elapsed>=DURATION_NANOS || error.lengthSqr()<1e-12) return Vec3.ZERO;
        double remaining=1D-(double)elapsed/DURATION_NANOS;
        return error.scale(remaining*remaining*remaining);
    }
    void reset() {error=Vec3.ZERO;started=0;}
    private static boolean small(Vec3 v) {
        return MovementCorrectionPayload.finite(v) && v.lengthSqr()<=MAX_DISTANCE*MAX_DISTANCE;
    }
}
