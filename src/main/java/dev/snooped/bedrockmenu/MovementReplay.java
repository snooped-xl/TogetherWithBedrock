package dev.snooped.bedrockmenu;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded replay of observed movement forces. Never calls tick/move or repeats gameplay effects. */
public final class MovementReplay {
    private static final int HISTORY=128, MAX_REPLAY=12;
    private static final Frame[] frames=new Frame[HISTORY];
    private static long sequence, minimumSequence=1, accepted, lastLog;
    private static int sampledTick=Integer.MIN_VALUE;
    private static LocalPlayer owner;
    private static Object level;
    private static Capture capture;

    static void reset() {
        sequence=0;accepted=0;minimumSequence=1;owner=null;level=null;lastLog=0;
        clear();MovementVisualCorrection.reset();
    }
    private static void clear() {
        java.util.Arrays.fill(frames,null);capture=null;sampledTick=Integer.MIN_VALUE;
    }
    /** Real teleports/respawns supersede any older correction still in flight. */
    public static void invalidate() {clear();minimumSequence=sequence+1;MovementVisualCorrection.reset();}
    public static void begin(LocalPlayer p) {
        if(!BedrockMovement.reconciliation(p)) return;
        if(owner!=p || level!=p.level()) {invalidate();owner=p;level=p.level();}
        capture=new Capture(p);
        Frame previous=get(sequence);
        if(previous!=null) capture.safe &= near(previous.velocity,capture.startVelocity,1e-6);
    }
    public static void verticalScale(Entity p,double scale) {
        if(capture!=null && owner==p) capture.scaleY*=scale;
    }
    public static void beforeMove(Entity entity,MoverType type,Vec3 delta) {
        if(capture==null || owner!=entity) return;
        Capture c=capture;c.moves++;
        c.safe &= type==MoverType.SELF && c.moves==1;
        c.requested=delta;c.moveStart=entity.position();c.box=entity.getBoundingBox();
        c.safe &= c.water==entity.isInWater() && c.pose==entity.getPose() && near(c.position,c.moveStart,1e-6);
    }
    public static void afterMove(Entity entity) {
        if(capture==null || owner!=entity || capture.requested==null) return;
        Capture c=capture;
        c.displacement=entity.position().subtract(c.moveStart);
        c.collisionVelocity=entity.getDeltaMovement();
        // Bounce blocks, webs, edge-sneaking and modded move hooks need the direct correction path.
        Vec3 expected=clippedVelocity(c.requested,c.displacement);
        c.safe &= near(expected,c.collisionVelocity,1e-6);
    }
    public static void drag(Entity entity,double x,double y,double z) {
        if(capture!=null && owner==entity) capture.drag=new Vec3(x,y,z);
    }
    static long finish(LocalPlayer p) {
        if(!BedrockMovement.reconciliation(p) || BedrockMovement.input(p)==null) return 0;
        if(owner!=p || level!=p.level()) {invalidate();owner=p;level=p.level();}
        if(sampledTick==p.tickCount) return 0;
        sampledTick=p.tickCount;
        long id=++sequence;
        Capture c=capture;capture=null;
        Frame f=null;
        if(c!=null && c.safe && c.moves==1 && c.drag!=null && c.collisionVelocity!=null
                && c.pose==p.getPose() && c.water==p.isInWater() && supported(p)) {
            Vec3 scale=new Vec3(1,c.scaleY,1);
            Vec3 impulse=c.requested.subtract(c.startVelocity.multiply(scale));
            Vec3 gravity=p.getDeltaMovement().subtract(c.collisionVelocity.multiply(c.drag));
            // Large post-move impulses (fluid escape, external forces) are not affine movement.
            if(gravity.lengthSqr()<.04 && impulse.lengthSqr()<4)
                f=new Frame(id,c.position,p.position(),p.getDeltaMovement(),c.box,scale,impulse,c.drag,gravity,
                        c.ground,p.onGround(),c.water,c.pose,c.support);
        }
        frames[(int)(id%HISTORY)]=f;
        return id;
    }
    static void correct(MovementCorrectionPayload packet) {
        LocalPlayer p=Minecraft.getInstance().player;
        if(p==null || !BedrockMovement.reconciliation(p) || owner!=p || level!=p.level()
                || !accepts(packet.sequence(),minimumSequence,accepted,sequence)) return;
        accepted=packet.sequence();
        State state=new State(packet.position(),packet.velocity(),packet.onGround(),false,false);
        Frame base=get(packet.sequence());
        int replayed=0;
        String reason="replayed";
        if(base==null || sequence-packet.sequence()>MAX_REPLAY || !supported(p)) reason="unsupported-history";
        else {
            State replay=state;
            Frame[] replacements=new Frame[(int)(sequence-packet.sequence())];
            for(long id=packet.sequence()+1;id<=sequence;id++) {
                Frame f=get(id);
                if(f==null || f.groundBefore!=replay.ground || f.water!=p.isInWater() || f.pose!=p.getPose()
                        || !loaded(p,replay.position) || replay.position.distanceToSqr(f.start)>16
                        || !p.level().getBlockState(supportPosition(replay.position)).equals(f.support)
                        || waterAt(p,f.box.move(replay.position.subtract(f.start)))!=f.water) {
                    reason="changed-environment";break;
                }
                AABB box=f.box.move(replay.position.subtract(f.start));
                boolean ground=replay.ground;
                State next=step(replay.position,replay.velocity,f.scale,f.impulse,f.drag,f.gravity,
                        delta->MovementCollision.collide(p,delta,box,ground));
                if(!MovementCorrectionPayload.finite(next.position) || !MovementCorrectionPayload.finite(next.velocity)) {
                    reason="invalid-replay";break;
                }
                replacements[(int)(id-packet.sequence()-1)]=f.rebased(replay,next,box);
                replay=next;replayed++;
            }
            if(reason.equals("replayed")) {
                state=replay;
                frames[(int)(base.id%HISTORY)]=base.corrected(packet);
                for(Frame f:replacements) frames[(int)(f.id%HISTORY)]=f;
            } else replayed=0;
        }
        if(!reason.equals("replayed")) clear();
        MovementVisualCorrection.corrected(p,p.position(),state.position);
        p.setPos(state.position);p.setDeltaMovement(state.velocity);
        p.setOnGround(state.ground);p.horizontalCollision=state.horizontal;p.verticalCollision=state.vertical;
        p.verticalCollisionBelow=state.ground;
        if(state.ground) p.resetFallDistance();
        p.setOldPosAndRot();
        // Reports the reconciled current position without inventing a teleport acknowledgement.
        p.connection.send(new ServerboundMovePlayerPacket.PosRot(p.position(),p.getYRot(),p.getXRot(),p.onGround(),p.horizontalCollision));
        long now=System.nanoTime();
        if(now-lastLog>=5_000_000_000L) {
            lastLog=now;
            LogUtils.getLogger().info("[TWB Movement] correction sequence={} pending={} replayed={} result={}",
                    packet.sequence(),sequence-packet.sequence(),replayed,reason);
        }
    }
    static boolean accepts(long incoming,long minimum,long accepted,long current) {
        return incoming>0 && incoming>=minimum && incoming>accepted && incoming<=current;
    }
    static State step(Vec3 position,Vec3 velocity,Vec3 scale,Vec3 impulse,Vec3 drag,Vec3 gravity,
                      java.util.function.UnaryOperator<Vec3> collision) {
        Vec3 requested=request(velocity,scale,impulse), displacement=collision.apply(requested);
        boolean vertical=Math.abs(requested.y-displacement.y)>1e-7;
        boolean horizontal=Math.abs(requested.x-displacement.x)>1e-7 || Math.abs(requested.z-displacement.z)>1e-7;
        return new State(position.add(displacement),clippedVelocity(requested,displacement).multiply(drag).add(gravity),
                vertical&&requested.y<0,horizontal,vertical);
    }
    static Vec3 request(Vec3 velocity,Vec3 scale,Vec3 impulse) {return velocity.multiply(scale).add(impulse);}
    static Vec3 clippedVelocity(Vec3 requested,Vec3 actual) {
        return new Vec3(Math.abs(requested.x-actual.x)>1e-7?0:requested.x,
                Math.abs(requested.y-actual.y)>1e-7?0:requested.y,
                Math.abs(requested.z-actual.z)>1e-7?0:requested.z);
    }
    private static Frame get(long id) {Frame f=frames[(int)(id%HISTORY)];return f!=null&&f.id==id?f:null;}
    private static boolean near(Vec3 a,Vec3 b,double tolerance) {return a.distanceToSqr(b)<tolerance*tolerance;}
    private static boolean loaded(LocalPlayer p,Vec3 pos) {return p.level().hasChunkAt(net.minecraft.core.BlockPos.containing(pos));}
    private static net.minecraft.core.BlockPos supportPosition(Vec3 pos) {return net.minecraft.core.BlockPos.containing(pos.x,pos.y-.5000001,pos.z);}
    private static boolean waterAt(LocalPlayer p,AABB box) {
        double min=BedrockMovement.waterProbeMin(box.minY,box.maxY),max=BedrockMovement.waterProbeMax(box.minY,box.maxY);
        for(var pos:net.minecraft.core.BlockPos.betweenClosed((int)Math.floor(box.minX+.001),(int)Math.floor(min),(int)Math.floor(box.minZ+.001),
                (int)Math.floor(box.maxX-.001),(int)Math.floor(max),(int)Math.floor(box.maxZ-.001))) {
            var fluid=p.level().getFluidState(pos);
            if(fluid.is(net.minecraft.tags.FluidTags.WATER) && max>pos.getY() && min<pos.getY()+fluid.getHeight(p.level(),pos)) return true;
        }
        return false;
    }
    private static boolean supported(LocalPlayer p) {
        return BedrockMovement.physics(p) && !p.noPhysics && !p.onClimbable() && !p.isInLava()
                && !p.isShiftKeyDown() && !p.isSleeping() && !p.isAutoSpinAttack()
                && !p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION);
    }
    private static final class Capture {
        final Vec3 position,startVelocity;
        final boolean ground,water;
        final Pose pose;
        final net.minecraft.world.level.block.state.BlockState support;
        boolean safe;
        int moves;
        double scaleY=1;
        Vec3 requested,moveStart,displacement,collisionVelocity,drag;
        AABB box;
        Capture(LocalPlayer p) {position=p.position();startVelocity=p.getDeltaMovement();ground=p.onGround();water=p.isInWater();pose=p.getPose();support=p.level().getBlockState(supportPosition(position));safe=supported(p);}
    }
    record State(Vec3 position,Vec3 velocity,boolean ground,boolean horizontal,boolean vertical) {}
    private record Frame(long id,Vec3 start,Vec3 end,Vec3 velocity,AABB box,Vec3 scale,Vec3 impulse,Vec3 drag,Vec3 gravity,
                         boolean groundBefore,boolean groundAfter,boolean water,Pose pose,net.minecraft.world.level.block.state.BlockState support) {
        Frame rebased(State from,State to,AABB bounds) {return new Frame(id,from.position,to.position,to.velocity,bounds,scale,impulse,drag,gravity,from.ground,to.ground,water,pose,support);}
        Frame corrected(MovementCorrectionPayload p) {return new Frame(id,start,p.position(),p.velocity(),box,scale,impulse,drag,gravity,groundBefore,p.onGround(),water,pose,support);}
    }
}
