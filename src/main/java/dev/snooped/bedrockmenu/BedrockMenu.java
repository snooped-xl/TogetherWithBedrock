// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import java.util.concurrent.*;

public final class BedrockMenu implements ClientModInitializer {
    private static final java.util.regex.Pattern HOSTED_ID=java.util.regex.Pattern.compile("^(host|hosted)-([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})(?:\\.[a-fA-F0-9-]+)?\\.bedrock\\.local$");
    public static BedrockMenu INSTANCE;
    final Companion companion=new Companion();
    private final PackPreferences packPreferences=new PackPreferences(FabricLoader.getInstance().getConfigDir().resolve("bedrock-pack-preferences.json"));
    final InviteBanners banners=new InviteBanners();
    private final FriendRequestNotifications friendNotifications=new FriendRequestNotifications(FabricLoader.getInstance().getConfigDir().resolve("bedrock-friend-notifications.json"));
    JsonObject status=new JsonObject();JsonArray invitations=new JsonArray();
    String serviceError="";
    private long nextPoll,joinGeneration,connectStarted,traceNext,nextFriendPoll,notificationRevision;
    private boolean polling,started,joining,connected,restarting,transferring,pollingFriends;
    private int activePort;
    private Screen returnScreen;
    private String activeSession="";
    private String activeIdentity="";
    private net.minecraft.network.Connection activeProxyConnection;
    private boolean connectingPrepared;
    @Override public void onInitializeClient() {
        INSTANCE=this;
        CreativeBridge.register();
        BedrockMovement.register();
        BedrockSkins.register();
        ConnectedInvites.register();
        PayloadTypeRegistry.serverboundPlay().register(MovementPayload.TYPE,MovementPayload.CODEC);
        ScreenEvents.AFTER_INIT.register((minecraft,screen,width,height)->{if(screen instanceof TitleScreen){title(screen);started=true;}else if(screen instanceof CreateWorldScreen create){hostShortcut(create);}else if(screen instanceof PauseScreen pause&&pause.showsPauseMenu()){hostLeader(pause);}});
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{com.mojang.logging.LogUtils.getLogger().info("[TogetherWithBedrock JoinTrace] client JOIN event: level={}", client.level!=null);if(!activeSession.isEmpty()){connected=true;joining=false;transferring=false;if(isProxySessionConnection(client,handler.getConnection())){activeProxyConnection=handler.getConnection();CreativeBridge.join(activeProxyConnection);BedrockMovement.join(activeProxyConnection);}}});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{CreativeBridge.reset();BedrockMovement.reset();if(connected&&!transferring)stop();else if(transferring)connected=false;});
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->companion.close());
        Runtime.getRuntime().addShutdownHook(new Thread(companion::close,"TogetherWithBedrock shutdown"));
    }
    private void hostShortcut(CreateWorldScreen screen) {
        var widgets=Screens.getWidgets(screen);int x=screen.width-82,y=32,w=76;
        for(var widget:widgets)if(widget.getMessage().getString().equals(Component.translatable("selectWorld.create").getString())&&widget.getWidth()>=140){
            int original=widget.getWidth();widget.setWidth((original-4)/2);x=widget.getX()+widget.getWidth()+4;y=widget.getY();w=original-widget.getWidth()-4;break;
        }
        Button host=Button.builder(Component.literal("Host"),b->Minecraft.getInstance().gui.setScreen(new HostCreateScreen(screen,screen.getUiState().getName(),screen.getUiState().getSeed()))).bounds(x,y,w,20).build();
        host.setTooltip(Tooltip.create(Component.literal("Create a separate Fabric world for Java and Bedrock friends.")));host.active=Minecraft.getInstance().allowsMultiplayer();widgets.add(host);
    }
    private void hostLeader(PauseScreen screen) {
        nextPoll=0;
        Button button=Button.builder(Component.literal("Host leader"),b->{HostedContext context=hostedContext();if(context!=null)Minecraft.getInstance().gui.setScreen(new HostLeaderScreen(screen,context));})
                .bounds(screen.width-114,8,106,20).build();
        button.setTooltip(Tooltip.create(Component.literal("Manage the hosted world you are playing on.")));
        button.visible=button.active=hostedContext()!=null;Screens.getWidgets(screen).add(button);
        long[] refreshAt={0};boolean[] refreshing={false};
        ScreenEvents.beforeTick(screen).register(s->{
            button.visible=button.active=hostedContext()!=null;
            Minecraft client=Minecraft.getInstance();ServerData data=client.getCurrentServer();var listener=client.getConnection();
            if(client.level==null||listener==null||data==null||!HOSTED_ID.matcher(data.ip).matches()||refreshing[0]||System.currentTimeMillis()<refreshAt[0])return;
            // Hosting controls must not depend on successful Xbox invitation polling.
            refreshing[0]=true;refreshAt[0]=System.currentTimeMillis()+2500;var connection=listener.getConnection();
            companion.request("hosting.status").whenComplete((hosting,error)->client.execute(()->{
                refreshing[0]=false;if(error==null&&client.getConnection()!=null&&client.getConnection().getConnection()==connection)status.add("hosting",hosting);
            }));
        });
    }
    /** Both the logical world identity and the actual local transport must agree. */
    HostedContext hostedContext() {
        Minecraft client=Minecraft.getInstance();var listener=client.getConnection();ServerData server=client.getCurrentServer();
        if(client.level==null||client.player==null||client.hasSingleplayerServer()||listener==null||server==null)return null;
        var connection=listener.getConnection();
        if(!connection.isConnected()||!(connection.getRemoteAddress() instanceof java.net.InetSocketAddress remote)||remote.getAddress()==null||!remote.getAddress().isLoopbackAddress())return null;
        var match=HOSTED_ID.matcher(server.ip);
        if(!match.matches())return null;
        String id=match.group(2),route=match.group(1).equals("host")?"java":"bedrock";
        JsonObject hosting=MenuScreen.child(status,"hosting");
        if(!MenuScreen.bool(hosting,"ready")||!id.equalsIgnoreCase(text(hosting,"worldId")))return null;
        int expectedPort;
        if(route.equals("java")){
            String address=text(hosting,"javaAddress");if(address.isBlank())return null;
            expectedPort=ServerAddress.parseString(address).getPort();
        }else{
            if(activeSession.isBlank()||!connected)return null;expectedPort=activePort;
        }
        if(remote.getPort()!=expectedPort)return null;
        return new HostedContext(id.toLowerCase(java.util.Locale.ROOT),route,client.getUser().getProfileId().toString(),connection);
    }
    record HostedContext(String id,String route,String ownerJavaUuid,net.minecraft.network.Connection connection) {
        JsonObject parameters(){return object("id",id,"route",route,"ownerJavaUuid",ownerJavaUuid);}
        boolean active(){HostedContext current=INSTANCE.hostedContext();return current!=null&&equals(current);}
    }
    /** All identity checks happen at dispatch; a stale session cannot affect Java play. */
    private boolean isProxySessionConnection(Minecraft client,net.minecraft.network.Connection connection) {
        ServerData server=client.getCurrentServer();
        return !activeSession.isBlank()&&!activeIdentity.isBlank()&&!client.hasSingleplayerServer()
                &&server!=null&&activeIdentity.equals(server.ip)&&connection.isConnected()
                &&connection.getRemoteAddress() instanceof java.net.InetSocketAddress remote
                &&remote.getAddress()!=null&&remote.getAddress().isLoopbackAddress()&&remote.getPort()==activePort;
    }
    public boolean isActiveProxyConnection(net.minecraft.network.Connection connection) {
        return connection != null && connected && !transferring && connection == activeProxyConnection
                && isProxySessionConnection(Minecraft.getInstance(), connection);
    }
    JsonObject connectedInviteParameters(net.minecraft.network.Connection connection) {
        return isActiveProxyConnection(connection)?object("kind","bedrock","session",activeSession):object("kind","java");
    }
    public void sendMovementTelemetry() {
        Minecraft client=Minecraft.getInstance();var player=client.player;var listener=client.getConnection();
        if(!connected||transferring||client.level==null||player==null||listener==null
                ||player.connection!=listener||listener.getConnection()!=activeProxyConnection
                ||!isProxySessionConnection(client,listener.getConnection())||player.isPassenger())return;
        var velocity=player.getDeltaMovement();
        if(!Double.isFinite(velocity.x)||!Double.isFinite(velocity.y)||!Double.isFinite(velocity.z)
                ||Math.abs(velocity.x)>64||Math.abs(velocity.y)>64||Math.abs(velocity.z)>64)return;
        int flags=(player.onGround()?1:0)|(player.horizontalCollision?2:0)|(player.verticalCollision?4:0)|(player.isSprinting()?8:0);
        long sequence=MovementReplay.finish(player);
        if(BedrockMovement.reconciliation(player) && sequence==0) return;
        listener.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new MovementPayload(velocity.x,velocity.y,velocity.z,flags,BedrockMovement.input(player),BedrockMovement.environment(player),BedrockMovement.events(player),sequence)));
    }
    public void joinHosted(String id,String name,String address,Screen parent) {
        Minecraft client=Minecraft.getInstance();if(client.level!=null||address.isBlank()||!client.allowsMultiplayer())return;
        ServerData server=new ServerData(name,"host-"+id+".bedrock.local",ServerData.Type.OTHER);
        connectPrepared(parent,ServerAddress.parseString(address),server);
    }
    private void connectPrepared(Screen parent,ServerAddress address,ServerData server) {
        // Bypass only this synchronous dispatch. A later manual/automatic retry must restart startup.
        connectingPrepared=true;
        try {ConnectScreen.startConnecting(parent,Minecraft.getInstance(),address,server,false,null);}
        finally {connectingPrepared=false;}
    }
    public boolean routeReconnect(Screen parent,ServerAddress address,ServerData server,net.minecraft.client.multiplayer.TransferState transfer) {
        if(transfer!=null){transfer(address);return false;}
        if(connectingPrepared)return false;
        ReconnectTarget target=ReconnectTarget.resolve(address.getHost(),server==null?null:server.ip);
        if(target==null)return false;
        if(target.kind().equals("host"))reconnectHosted(target.id(),parent);
        else join(target.kind(),target.id(),parent,true);
        return true;
    }
    public void reconnectHosted(String id,Screen parent) {
        Minecraft client=Minecraft.getInstance();if(client.level!=null)return;
        ConnectingBedrockScreen progress=new ConnectingBedrockScreen(parent,"Connecting to hosted world","Connecting directly to your Java server…");client.gui.setScreen(progress);
        companion.request("hosting.status").whenComplete((state,error)->client.execute(()->{
            if(client.gui.screen()!=progress)return;
            if(error!=null){progress.failed(Companion.error(error));return;}
            if(!id.equals(text(state,"worldId"))||!MenuScreen.bool(state,"ready")){progress.failed("This hosted world is stopped. Start it from Bedrock → Hosting.");return;}
            joinHosted(id,text(state,"name"),text(state,"javaAddress"),parent);
        }));
    }
    private void title(Screen screen) {
        var widgets=Screens.getWidgets(screen);int x=screen.width-106,y=6;
        for(var widget:widgets)if(widget.getMessage().getString().equals(Component.translatable("menu.online").getString())&&widget.getWidth()>=190) {
            int original=widget.getWidth();widget.setWidth((original-4)/2);x=widget.getX()+widget.getWidth()+4;y=widget.getY();break;
        }
        Button bedrock=Button.builder(Component.literal("Bedrock"),b->Minecraft.getInstance().gui.setScreen(new BedrockScreen(screen)))
                .bounds(x,y,98,20).build();bedrock.active=Minecraft.getInstance().allowsMultiplayer();widgets.add(bedrock);
        Button join=Button.builder(Component.literal("Join"),b->{JsonObject invite=banners.current();if(invite!=null){banners.dismiss();if(InviteBanners.isFriendRequest(invite))Minecraft.getInstance().gui.setScreen(new FriendsScreen(screen,1,text(invite,"id")));else join("world",text(invite,"id"),new BedrockScreen(screen));}}).bounds(0,0,68,20).build();
        Button inbox=Button.builder(Component.literal("Invites"),b->{JsonObject invite=banners.current();banners.dismiss();if(invite!=null&&!InviteBanners.isFriendRequest(invite))Minecraft.getInstance().gui.setScreen(new BedrockScreen(screen,2));}).bounds(0,0,54,20).build();
        join.visible=false;inbox.visible=false;widgets.add(join);widgets.add(inbox);
        ScreenEvents.beforeTick(screen).register(s->{
            JsonObject notification=banners.current();boolean visible=notification!=null,friend=visible&&InviteBanners.isFriendRequest(notification);join.visible=inbox.visible=visible;
            int left=Math.max(4,s.width-242);join.setPosition(left+100,38);inbox.setPosition(left+172,38);
            join.setMessage(Component.literal(friend?"Requests":"Join"));inbox.setMessage(Component.literal(friend?"Later":"Invites"));
            join.active=friend||!joining&&!restarting&&Minecraft.getInstance().allowsMultiplayer();
        });
        ScreenEvents.afterExtract(screen).register((s,g,mx,my,delta)->{
            JsonObject invite=banners.current();if(invite==null)return;
            g.nextStratum();
            int left=Math.max(4,s.width-242);g.fill(left,4,s.width-4,62,0xF0202925);g.fill(left,4,left+3,62,0xFF82C78B);
            boolean friend=InviteBanners.isFriendRequest(invite);var font=Screens.getFont(s);g.text(font,friend?"Friend request":"Bedrock invitation",left+10,10,0xFF9EE0A6);
            String sender=friend?MenuScreen.first(invite,"name","gamertag"):text(invite,"host");
            String description=friend?(sender.isBlank()?"A player wants to add you":sender+" wants to add you"):(sender.isBlank()?"A friend invited you":sender+" invited you");
            g.text(font,font.plainSubstrByWidth(description,216),left+10,24,0xFFFFFFFF);
            g.text(font,banners.seconds()+"s · "+(friend?"Friends":"Invites"),left+10,44,0xFFB8C7BD);
            join.extractRenderState(g,mx,my,delta);inbox.extractRenderState(g,mx,my,delta);
        });
    }
    private void tick(Minecraft client) {
        syncNotificationAccount();
        boolean title=client.gui.screen() instanceof TitleScreen;
        if(!title&&activeSession!=null&&!activeSession.isEmpty()&&System.currentTimeMillis()-traceNext>10000){traceNext=System.currentTimeMillis();
            var log=com.mojang.logging.LogUtils.getLogger();
            var scr=client.gui.screen();log.info("[TogetherWithBedrock JoinTrace] client state: level="+(client.level!=null)+" player="+(client.player!=null)+" connection="+(client.getConnection()!=null)+" screen="+(scr!=null?scr.getClass().getSimpleName():"none"));
        }
        banners.tick(title,System.currentTimeMillis(),id->{if(InviteBanners.isFriendRequest(banners.current()))friendNotifications.shown(id);else companion.request("seenInvite",object("id",id));});
        pollFriendNotifications(client);
        if(!activeSession.isEmpty()&&!connected&&!joining&&client.getConnection()==null&&System.currentTimeMillis()-connectStarted>2000) {
            Screen screen=client.gui.screen();
            if(screen instanceof DisconnectedScreen||screen instanceof TitleScreen||screen instanceof BedrockScreen)stop();
        }
        if(started&&!polling&&!restarting&&System.currentTimeMillis()>=nextPoll) {
            polling=true;nextPoll=System.currentTimeMillis()+(client.level==null?3000:10000);
            companion.request("status").thenCombine(companion.request("invites"),(state,invites)->new JsonObject[]{state,invites}).whenComplete((data,error)->client.execute(()->{
                polling=false;
                if(error!=null){serviceError=Companion.error(error);nextPoll=System.currentTimeMillis()+15000;return;}
                status=data[0];syncNotificationAccount();serviceError="";invitations=array(data[1],"entries");banners.accept(invitations);
            }));
        }
    }
    private void syncNotificationAccount() {
        JsonObject auth=MenuScreen.child(status,"auth");String account=MenuScreen.bool(auth,"signedIn")?text(auth,"accountXuid"):"";
        if(friendNotifications.setAccount(account)){banners.clear();notificationRevision++;nextFriendPoll=0;}
    }
    private void pollFriendNotifications(Minecraft client) {
        if(!started||pollingFriends||restarting||joining||client.level!=null||!activeSession.isBlank()
                ||friendNotifications.account().isBlank()||System.currentTimeMillis()<nextFriendPoll
                ||!(client.gui.screen() instanceof TitleScreen||client.gui.screen() instanceof MenuScreen))return;
        String account=friendNotifications.account();long revision=notificationRevision;
        pollingFriends=true;nextFriendPoll=System.currentTimeMillis()+20_000;
        companion.request("friends.requests").whenComplete((data,error)->client.execute(()->{
            pollingFriends=false;syncNotificationAccount();
            if(revision!=notificationRevision||!account.equals(friendNotifications.account()))return;
            if(error!=null){nextFriendPoll=System.currentTimeMillis()+60_000;return;}
            observeFriendRequests(data);
        }));
    }
    boolean observeFriendRequests(JsonObject data) {
        syncNotificationAccount();
        if(friendNotifications.accept(friendNotifications.account(),data)){
            banners.retainFriendRequests(friendNotifications.pendingIds());banners.acceptFriendRequests(friendNotifications.unseen());
            return true;
        }
        return false;
    }
    void friendRequestsViewed() {
        syncNotificationAccount();friendNotifications.viewedRequests();banners.clearFriendRequests();
    }
    public void join(String kind,String id,Screen parent) {
        join(kind,id,parent,false);
    }
    private void join(String kind,String id,Screen parent,boolean reconnect) {
        Minecraft client=Minecraft.getInstance();if(joining||restarting||client.level!=null||!client.allowsMultiplayer())return;
        long ticket=++joinGeneration;joining=true;returnScreen=parent;
        connected=false;transferring=false;activeSession="";activeIdentity="";activeProxyConnection=null;
        ConnectingBedrockScreen progress=reconnect?new ConnectingBedrockScreen(parent,"Reconnecting to Bedrock","Restarting your Bedrock proxy…"):new ConnectingBedrockScreen(parent);client.gui.setScreen(progress);
        // The companion's start operation stops the old gameplay child and waits for a new listener.
        // Keep the companion itself alive: restarting it would also stop a hosted world.
        companion.request("start",object("kind",kind,"id",id)).whenComplete((ready,error)->client.execute(()->{
            if(ticket!=joinGeneration)return;
            if(error!=null){joining=false;progress.failed(Companion.error(error));return;}
            activeSession=text(ready,"session");joining=false;connected=false;connectStarted=System.currentTimeMillis();
            activeIdentity=text(ready,"identity");activeProxyConnection=null;
            int port=ready.get("port").getAsInt();
            activePort=port;
            // A stable logical identity preserves per-world map/settings caches across ephemeral proxy ports.
            ServerData server=new BedrockServerData(text(ready,"name"),activeIdentity,
                    PackPreferences.identity(kind,id,activeIdentity),packPreferences);
            connectPrepared(parent,ServerAddress.parseString("127.0.0.1:"+port),server);
        }));
    }
    public void transfer(ServerAddress address) {
        if(!activeSession.isEmpty()&&address.getHost().equals("127.0.0.1")&&address.getPort()==activePort){transferring=true;connectStarted=System.currentTimeMillis();}
    }
    void stop(){++joinGeneration;joining=false;connected=false;transferring=false;activeSession="";activeIdentity="";activeProxyConnection=null;companion.request("stop");}
    void cancelJoin(){stop();Minecraft.getInstance().gui.setScreen(returnScreen);}
    CompletableFuture<Void> restart() {
        if(restarting||!activeSession.isEmpty()||joining)return CompletableFuture.failedFuture(new IllegalStateException("Disconnect before restarting the service."));
        restarting=true;return companion.restart().whenComplete((ignored,error)->Minecraft.getInstance().execute(()->{restarting=false;nextPoll=0;}));
    }
    boolean busy(){return joining||restarting;}
    static String text(JsonObject j,String key){try{return j.get(key).getAsString();}catch(Exception e){return "";}}
    static JsonArray array(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonArray()?j.getAsJsonArray(key):new JsonArray();}
    static JsonObject object(String... values){JsonObject j=new JsonObject();for(int i=0;i<values.length;i+=2)j.addProperty(values[i],values[i+1]);return j;}
}
