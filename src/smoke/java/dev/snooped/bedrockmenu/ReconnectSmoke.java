package dev.snooped.bedrockmenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Same saved transport + identity as Meteor, without a real account, server or proxy process. */
final class ReconnectSmoke {
    static void run(Minecraft client)throws Exception {
        var menu=BedrockMenu.INSTANCE;
        var address=ServerAddress.parseString("127.0.0.1:1");
        var data=new ServerData("Reconnect fixture","realm-123.00000000-0000-0000-0000-000000000001.bedrock.local",ServerData.Type.OTHER);
        var guard=BedrockMenu.class.getDeclaredField("connectingPrepared");guard.setAccessible(true);
        guard.setBoolean(menu,true);
        try {if(menu.routeReconnect(new TitleScreen(),address,data,null))throw new AssertionError("Fresh prepared connection would recurse");}
        finally {guard.setBoolean(menu,false);}
        if(menu.routeReconnect(new TitleScreen(),address,new ServerData("Java","127.0.0.1:25565",ServerData.Type.OTHER),null))throw new AssertionError("Ordinary Java reconnect intercepted");
        ConnectScreen.startConnecting(new TitleScreen(),client,address,data,false,null);
        // Companion is deliberately closed by ItemPhysicSmoke. The closed-service error stays
        // on our startup screen, proving no attempt went to the stale socket or vanilla ConnectScreen.
        if(!(client.gui.screen() instanceof ConnectingBedrockScreen))throw new AssertionError("Saved proxy reconnect bypassed startup");
        var realms=new BedrockScreen(new TitleScreen(),3);
        var available=BedrockScreen.class.getDeclaredMethod("available",com.google.gson.JsonObject.class);available.setAccessible(true);
        var badge=BedrockScreen.class.getDeclaredMethod("badge",com.google.gson.JsonObject.class);badge.setAccessible(true);
        var full=com.google.gson.JsonParser.parseString("{\"available\":true,\"players\":10,\"capacity\":10,\"full\":true}").getAsJsonObject();
        if(!(Boolean)available.invoke(realms,full) || !badge.invoke(realms,full).equals("10 / 10 players · Full"))throw new AssertionError("Full Realm cannot retry or has no count");
        client.gui.setScreen(null);
        System.out.println("RECONNECT_AND_FULL_REALM_SMOKE_PASS");
    }
}
