package dev.snooped.bedrockmenu;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Negotiated, connection-local textures for remote Bedrock players, including the tab list. */
public final class BedrockSkins {
    private static final int MAX_PLAYERS=256, MAX_MEMORY=32*1024*1024;
    private static final LinkedHashMap<UUID,Slot> SKINS=new LinkedHashMap<>(16,.75f,true);
    private static final ExecutorService DECODER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"TogetherWithBedrock skins");t.setDaemon(true);return t;});
    private static Connection connection;
    private static long epoch,nextRequest,nextWarning,textureSerial;
    private static int attempts,pendingBytes,textureBytes;
    private static boolean enabled,busy;
    private static volatile boolean stopping;
    private static final class Slot {
        long revision; BedrockSkinPayload pending; PlayerSkin skin;
        Identifier body,cape; int bytes;
    }
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(BedrockSkinPayload.TYPE,BedrockSkinPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BedrockSkinPayload.Capability.TYPE,BedrockSkinPayload.Capability.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BedrockSkinPayload.Capability.TYPE,BedrockSkinPayload.Capability.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BedrockSkinPayload.Capability.TYPE,(p,c)->{
            if(active(c.client()) && attempts>0 && p.version()==1) enabled=true;
        });
        ClientPlayNetworking.registerGlobalReceiver(BedrockSkinPayload.TYPE,(p,c)->{
            if(enabled && active(c.client())) accept(p);
        });
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{reset();connection=handler.getConnection();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(BedrockSkins::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{stopping=true;reset();DECODER.shutdown();});
    }
    private static boolean active(Minecraft client) {
        return connection!=null && connection.isConnected() && client.getConnection()!=null
                && client.getConnection().getConnection()==connection;
    }
    private static void tick(Minecraft client) {
        if(!active(client) || enabled || attempts>=5 || System.nanoTime()<nextRequest) return;
        boolean proxy=BedrockMenu.INSTANCE!=null && BedrockMenu.INSTANCE.isActiveProxyConnection(connection);
        if(!proxy && !ClientPlayNetworking.canSend(BedrockSkinPayload.Capability.TYPE)) return;
        attempts++;nextRequest=System.nanoTime()+1_000_000_000L;
        client.getConnection().send(new ServerboundCustomPayloadPacket(new BedrockSkinPayload.Capability(1)));
    }
    private static boolean self(UUID uuid) {
        Minecraft client=Minecraft.getInstance();
        return uuid.equals(client.getUser().getProfileId()) || client.player!=null && uuid.equals(client.player.getUUID());
    }
    /** Null means preserve the server's Java skin/default. Local Java appearance is never replaced. */
    public static PlayerSkin lookup(UUID uuid) {
        if(uuid==null || !enabled || !active(Minecraft.getInstance()) || self(uuid)) return null;
        Slot slot=SKINS.get(uuid);return slot==null?null:slot.skin;
    }
    private static void accept(BedrockSkinPayload payload) {
        if(!payload.valid()) { warn("malformed skin payload");return; }
        UUID uuid=payload.player();if(self(uuid)) return;
        if(payload.remove()) { remove(uuid);return; }
        Slot slot=SKINS.computeIfAbsent(uuid,k->new Slot());
        if(slot.pending!=null) pendingBytes-=slot.pending.bytes();
        slot.revision++;slot.pending=payload;pendingBytes+=payload.bytes();
        trim();dispatch();
    }
    private static void dispatch() {
        if(busy || stopping) return;
        for(var entry:SKINS.entrySet()) {
            Slot slot=entry.getValue();if(slot.pending==null) continue;
            UUID uuid=entry.getKey();BedrockSkinPayload payload=slot.pending;
            slot.pending=null;pendingBytes-=payload.bytes();busy=true;
            long generation=epoch,revision=slot.revision;
            DECODER.execute(()->{
                Decoded decoded=null;String error=null;
                try { decoded=decode(payload); } catch(Exception e) { error="unsupported or invalid Bedrock skin image"; }
                if(stopping) { if(decoded!=null) decoded.close();return; }
                Decoded ready=decoded;String failure=error;
                Minecraft.getInstance().execute(()->{
                    try {
                        if(generation==epoch && SKINS.get(uuid)==slot && revision==slot.revision && enabled && active(Minecraft.getInstance())) {
                            if(ready!=null) upload(uuid,slot,payload,ready);else warn(failure);
                        }
                    } finally { if(ready!=null) ready.close();busy=false;dispatch(); }
                });
            });
            return;
        }
    }
    private static final class Decoded implements AutoCloseable {
        NativeImage body,cape;
        @Override public void close() { if(body!=null) body.close();if(cape!=null) cape.close(); }
    }
    private static Decoded decode(BedrockSkinPayload payload) throws java.io.IOException {
        Decoded d=new Decoded();
        try {
            d.body=BedrockSkinImages.decodeSkin(payload.skin());
            if(payload.cape().length>0) {
                // An unsupported cape must not discard an otherwise usable skin.
                try { d.cape=BedrockSkinImages.decodeCape(payload.cape()); } catch(java.io.IOException ignored) { }
            }
            return d;
        } catch(Exception e) { d.close();throw e; }
    }
    private static void upload(UUID uuid,Slot slot,BedrockSkinPayload payload,Decoded images) {
        var textures=Minecraft.getInstance().getTextureManager();
        String path="bedrock_skins/"+uuid+"/"+(++textureSerial);
        Identifier body=Identifier.fromNamespaceAndPath("bedrock_menu",path+"/body");
        Identifier cape=images.cape==null?null:Identifier.fromNamespaceAndPath("bedrock_menu",path+"/cape");
        int bytes=images.body.getWidth()*images.body.getHeight()*4;
        if(images.cape!=null) bytes+=images.cape.getWidth()*images.cape.getHeight()*4;
        try {
            textures.register(body,new DynamicTexture(()->"TogetherWithBedrock body",images.body));images.body=null;
            if(cape!=null) { textures.register(cape,new DynamicTexture(()->"TogetherWithBedrock cape",images.cape));images.cape=null; }
            release(slot);slot.body=body;slot.cape=cape;slot.bytes=bytes;textureBytes+=bytes;
            slot.skin=PlayerSkin.insecure(new ClientAsset.ResourceTexture(body,body),
                    cape==null?null:new ClientAsset.ResourceTexture(cape,cape),null,
                    (payload.flags()&BedrockSkinPayload.SLIM)!=0?PlayerModelType.SLIM:PlayerModelType.WIDE);
            trim();
        } catch(Exception e) { textures.release(body);if(cape!=null) textures.release(cape);warn("could not upload a Bedrock skin texture"); }
    }
    private static void trim() {
        Iterator<Map.Entry<UUID,Slot>> entries=SKINS.entrySet().iterator();
        while(entries.hasNext() && (SKINS.size()>MAX_PLAYERS || pendingBytes>MAX_MEMORY || textureBytes>MAX_MEMORY)) {
            Slot slot=entries.next().getValue();entries.remove();discard(slot);
        }
    }
    private static void remove(UUID uuid) { Slot slot=SKINS.remove(uuid);if(slot!=null) discard(slot); }
    private static void discard(Slot slot) {
        slot.revision++;if(slot.pending!=null) pendingBytes-=slot.pending.bytes();slot.pending=null;release(slot);
    }
    private static void release(Slot slot) {
        var textures=Minecraft.getInstance().getTextureManager();
        if(slot.body!=null) textures.release(slot.body);if(slot.cape!=null) textures.release(slot.cape);
        textureBytes-=slot.bytes;slot.body=slot.cape=null;slot.skin=null;slot.bytes=0;
    }
    static void reset() {
        epoch++;connection=null;enabled=false;attempts=0;nextRequest=0;
        for(Slot slot:SKINS.values()) discard(slot);
        SKINS.clear();pendingBytes=textureBytes=0;
    }
    private static void warn(String message) {
        long now=System.nanoTime();if(now<nextWarning) return;nextWarning=now+5_000_000_000L;
        LogUtils.getLogger().warn("[TogetherWithBedrock] {}; keeping the available Java appearance",message);
    }
}
