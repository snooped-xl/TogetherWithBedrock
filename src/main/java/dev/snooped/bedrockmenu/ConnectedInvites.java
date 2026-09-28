// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import java.net.InetSocketAddress;
import java.util.UUID;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

/** Read-only detection runs off-thread; broadcasting starts only after opening Invite friends. */
final class ConnectedInvites {
    private static Connection connection;
    private static String identity="";
    private static JsonObject state=new JsonObject();
    private static long nextCheck;
    private static boolean checking;
    static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(ConnectedInvites::tick);
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(!(screen instanceof PauseScreen pause)||!pause.showsPauseMenu())return;
            Button button=Button.builder(Component.literal("Invite Xbox friends"),b->{
                Context current=context();if(current!=null)client.gui.setScreen(new ConnectedInviteScreen(screen,current,state));
            }).bounds(Math.max(4,w-146),32,138,20).build();
            button.setTooltip(Tooltip.create(Component.literal("Invite Xbox friends to this Bedrock world or verified Geyser server.")));
            button.visible=button.active=context()!=null;Screens.getWidgets(screen).add(button);
            ScreenEvents.beforeTick(screen).register(s->button.visible=button.active=context()!=null);
        });
    }
    private static void tick(Minecraft client){
        Connection current=client.level!=null&&!client.hasSingleplayerServer()&&client.getConnection()!=null?client.getConnection().getConnection():null;
        if(current!=connection){
            String previous=identity;connection=current;identity=current==null?"":UUID.randomUUID().toString();
            state=new JsonObject();nextCheck=0;checking=false;
            if(!previous.isBlank())INSTANCE.companion.request("connectedInvites.clear",object("connection",previous));
        }
        if(current==null||!current.isConnected()||checking||System.currentTimeMillis()<nextCheck||client.getCurrentServer()==null)return;
        // Our own host has access-aware invitations in Host leader.
        if(INSTANCE.hostedContext()!=null){state=new JsonObject();nextCheck=System.currentTimeMillis()+15000;return;}
        JsonObject params=INSTANCE.connectedInviteParameters(current);
        var server=client.getCurrentServer();
        // Never probe a logical proxy address during its JOIN/transfer transition.
        if(text(params,"kind").equals("java")&&server.ip.endsWith(".bedrock.local"))return;
        params.addProperty("connection",identity);params.addProperty("name",server.name);params.addProperty("address",server.ip);
        params.addProperty("motd",server.motd==null?"":server.motd.getString());
        if(current.getRemoteAddress() instanceof InetSocketAddress remote){
            params.addProperty("remoteHost",remote.getAddress()==null?remote.getHostString():remote.getAddress().getHostAddress());params.addProperty("remotePort",remote.getPort());
        }
        String key=identity;checking=true;nextCheck=System.currentTimeMillis()+15000;
        INSTANCE.companion.request("connectedInvites.check",params).whenComplete((result,error)->client.execute(()->{
            if(!identity.equals(key)||connection!=current)return;
            checking=false;state=error==null?result:new JsonObject();
        }));
    }
    static Context context(){return connection!=null&&connection.isConnected()&&MenuScreen.bool(state,"available")?new Context(identity,connection):null;}
    record Context(String identity,Connection connection){
        JsonObject parameters(){return object("connection",identity);}
        boolean active(){return identity.equals(ConnectedInvites.identity)&&connection==ConnectedInvites.connection&&connection.isConnected()&&Minecraft.getInstance().level!=null;}
    }
}
