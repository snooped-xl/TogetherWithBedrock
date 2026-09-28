package dev.snooped.bedrockmenu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Connection-scoped physics/input compatibility, negotiated with the local proxy. */
public final class BedrockMovement {
    private static Connection connection;
    private static int negotiated;
    private static int capturedEnvironment;
    private static LocalPlayer jumpingPlayer;
    private static int jumpTick;
    private static LocalPlayer capturedPlayer;
    private static int capturedTick;
    private static MovementPayload.Input capturedInput;

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Capability.TYPE, Capability.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Capability.TYPE, Capability.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MovementCorrectionPayload.TYPE,MovementCorrectionPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MovementCorrectionPayload.TYPE,(payload,context)->MovementReplay.correct(payload));
        ClientPlayNetworking.registerGlobalReceiver(Capability.TYPE,(payload,context)->{
            if (active() && context.client().getConnection()!=null
                    && context.client().getConnection().getConnection()==connection
                    && (payload.version()>=2 && payload.version()<=5)) {
                negotiated=Math.max(negotiated,payload.version());
            }
        });
    }
    static void join(Connection value) {
        reset(); connection=value;
        if(active()) {
            // Keep older development services compatible; v4 enables actual jump events.
            Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new Capability(2)));
            Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new Capability(3)));
            Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new Capability(4)));
            Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new Capability(5)));
        }
    }
    static void reset() { connection=null; negotiated=0; capturedPlayer=null; capturedInput=null; jumpingPlayer=null; MovementReplay.reset(); BedrockSwimState.reset(); }
    private static boolean active() { return BedrockMenu.INSTANCE!=null && BedrockMenu.INSTANCE.isActiveProxyConnection(connection); }
    private static boolean local(LivingEntity entity) { return negotiated>=2 && active() && entity==Minecraft.getInstance().player; }
    public static boolean usesBedrockInput(LocalPlayer player) { return local(player); }
    static boolean reconciliation(LocalPlayer player) { return negotiated>=5 && local(player); }

    public static void captureInput(LivingEntity entity, Vec3 input) {
        if (!local(entity)) return;
        LocalPlayer player=(LocalPlayer)entity;
        var raw=player.input.getMoveVector();
        capturedPlayer=player; capturedTick=player.tickCount;
        capturedInput=new MovementPayload.Input((float)input.x,(float)input.z,raw.x,raw.y,player.getYRot(),negotiated>=3?player.getXRot():Float.NaN);
        capturedEnvironment=(player.isInWater()?1:0)|(player.isSwimming()?2:0)|(player.isUnderWater()?4:0)|(player.getAbilities().flying?8:0);
    }
    static MovementPayload.Input input(LocalPlayer player) {
        return local(player)&&capturedPlayer==player&&capturedTick==player.tickCount?capturedInput:null;
    }
    static int environment(LocalPlayer player) {
        return negotiated>=3 && input(player)!=null && Float.isFinite(capturedInput.pitch()) ? capturedEnvironment : -1;
    }
    static int events(LocalPlayer player) {
        if(negotiated<4 || input(player)==null) return -1;
        int events=jumpingPlayer==player && jumpTick==player.tickCount ? 1 : 0;
        jumpingPlayer=null; // A paused/repeated sample must not repeat an accepted jump.
        return events;
    }
    public static void jumped(LivingEntity entity) {
        if(local(entity)) { jumpingPlayer=(LocalPlayer)entity; jumpTick=entity.tickCount; MovementReplay.verticalScale(entity,0); }
    }
    public static boolean physics(LivingEntity entity) {
        return negotiated>=4 && local(entity) && !entity.isPassenger() && !entity.isFallFlying()
                && !((LocalPlayer)entity).getAbilities().flying && !entity.isSpectator();
    }
    public static boolean waterContact(net.minecraft.world.entity.Entity entity, net.minecraft.world.level.material.FluidState fluid,
                                       net.minecraft.core.BlockPos position) {
        if(!(entity instanceof LivingEntity living) || !physics(living) || !fluid.is(net.minecraft.tags.FluidTags.WATER)) return true;
        var box=entity.getBoundingBox();
        double min=waterProbeMin(box.minY,box.maxY), max=waterProbeMax(box.minY,box.maxY);
        return max>position.getY() && min<position.getY()+fluid.getHeight(entity.level(),position);
    }
    public static double waterProbeMin(double min,double max) { return Math.min(min+0.401,(min+max)*0.5); }
    public static double waterProbeMax(double min,double max) { return Math.max(max-0.401,(min+max)*0.5); }
    public static Vec3 waterGravity(Vec3 velocity, boolean swimming, double gravity) {
        return swimming || gravity==0 ? velocity : velocity.add(0,-0.005,0);
    }
    public static void liquidJump(LivingEntity entity) {
        Vec3 v=entity.getDeltaMovement(); float amount=BedrockSwimState.amount((LocalPlayer)entity);
        if(amount>0F && amount<1F) MovementReplay.verticalScale(entity,0);
        entity.setDeltaMovement(v.x,amount>0F && amount<1F ? 0D : v.y+0.04D,v.z);
    }
    public static void steerSwimming(net.minecraft.world.entity.player.Player player) {
        if(!player.isSwimming() || ((LocalPlayer)player).input.keyPresses.jump()) return;
        Vec3 v=player.getDeltaMovement();
        double target=-Math.sin(Math.toRadians(player.getXRot()));
        boolean descend=((LocalPlayer)player).input.keyPresses.shift();
        if(target>0 && !descend) {
            var high=net.minecraft.core.BlockPos.containing(player.getX(),player.getY()+0.52,player.getZ());
            var low=net.minecraft.core.BlockPos.containing(player.getX(),player.getY()+0.42,player.getZ());
            if(player.level().getBlockState(high).isAir() && player.level().getFluidState(low).isEmpty()) {
                MovementReplay.verticalScale(player,0);
                player.setDeltaMovement(v.x,0,v.z); return;
            }
        }
        double rate=target < -0.2 ? 0.085 : 0.06;
        MovementReplay.verticalScale(player,1-rate);
        player.setDeltaMovement(v.x,v.y+(target-v.y)*rate,v.z);
    }
    public static boolean ladderPhysics(LivingEntity entity) {
        if(!local(entity)) return false;
        LocalPlayer player=(LocalPlayer)entity;
        return !player.isPassenger() && !player.getAbilities().flying && !player.isSpectator()
                && !player.isInWater() && !player.isInLava() && !player.isFallFlying()
                && !player.hasEffect(MobEffects.LEVITATION) && !player.hasEffect(MobEffects.SLOW_FALLING)
                && player.onClimbable() && !player.getInBlockState().is(Blocks.SCAFFOLDING);
    }
    public static double ascendingVelocity(double vanilla, boolean wallCollision) {
        // Java applies gravity/drag after setting climb speed; Bedrock retains 0.2 against a wall.
        return wallCollision && Math.abs(vanilla-0.1176D)<0.000001D ? 0.2D : vanilla;
    }
    record Capability(int version) implements CustomPacketPayload {
        static final Type<Capability> TYPE=new Type<>(Identifier.fromNamespaceAndPath("bedrock_menu","movement_capability"));
        static final StreamCodec<RegistryFriendlyByteBuf,Capability> CODEC=CustomPacketPayload.codec((p,b)->b.writeByte(p.version()),b->new Capability(b.readUnsignedByte()));
        @Override public Type<Capability> type(){return TYPE;}
    }
}
