package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import java.time.Instant;

/** Test-only navigation, synthetic invite and screenshots on a separate display/profile. */
public class UiSmoke implements ClientModInitializer {
    int stage;long next;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("twb.smoke.itemphysic")){ItemPhysicSmoke.register();return;}if(Boolean.getBoolean("twb.smoke.hosting")){hosting();return;}ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(client.gui.screen() instanceof AccessibilityOnboardingScreen){client.gui.setScreen(new TitleScreen());return;}
        if(client.gui.screen()==null||client.gui.overlay()!=null||System.currentTimeMillis()<next)return;
        if(stage==0&&!(client.gui.screen() instanceof TitleScreen))return;
        next=System.currentTimeMillis()+3500;
        try {
            switch(stage++) {
                case 0 -> {
                    boolean button=Screens.getWidgets(client.gui.screen()).stream().anyMatch(b->b.getMessage().getString().equals("Bedrock"));
                    if(!button)throw new AssertionError("Missing title button");
                    JsonObject invite=new JsonObject();invite.addProperty("id","00000000-0000-0000-0000-000000000001");invite.addProperty("host","Example friend");invite.addProperty("name","Example world");invite.addProperty("seen",false);invite.addProperty("expires",Instant.now().plusSeconds(600).toString());JsonArray rows=new JsonArray();rows.add(invite);BedrockMenu.INSTANCE.banners.accept(rows);
                }
                case 1 -> screenshot(client,"01-title-invite.png");
                case 2 -> client.gui.setScreen(new BedrockScreen(new TitleScreen()));
                case 3 -> screenshot(client,"02-servers.png");
                case 4 -> client.gui.setScreen(new EditServerScreen(new BedrockScreen(new TitleScreen()),null));
                case 5 -> screenshot(client,"03-add-server.png");
                case 6 -> client.gui.setScreen(new BedrockScreen(new TitleScreen(),1));
                case 7 -> screenshot(client,"04-worlds.png");
                case 8 -> client.gui.setScreen(new BedrockScreen(new TitleScreen(),2));
                case 9 -> screenshot(client,"05-invites.png");
                case 10 -> client.gui.setScreen(new BedrockScreen(new TitleScreen(),3));
                case 11 -> screenshot(client,"06-realms.png");
                case 12 -> client.gui.setScreen(new ProxyScreen(new BedrockScreen(new TitleScreen())));
                case 13 -> screenshot(client,"07-proxy.png");
                case 14 -> {System.out.println("BEDROCK_UI_SMOKE_PASS");client.stop();}
            }
        }catch(Throwable error){error.printStackTrace();client.stop();}
    });}
    void screenshot(Minecraft client,String name){Screenshot.grab(client.gameDirectory,name,client.gameRenderer.mainRenderTarget(),1,message->{});}

    /** Fixtures only: no companion, account, server, or user's Minecraft directory. */
    void hosting(){
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client->BedrockMenu.INSTANCE.companion.close());
        JsonObject world=JsonParser.parseString("{\"id\":\"fixture-world\",\"name\":\"A place for everyone\",\"gamemode\":\"survival\",\"difficulty\":\"normal\",\"maxPlayers\":8,\"visibility\":\"public\",\"broadcastEnabled\":true}").getAsJsonObject();
        JsonObject state=JsonParser.parseString("{\"worldId\":\"fixture-world\",\"name\":\"A place for everyone\",\"state\":\"running\",\"running\":true,\"ready\":true,\"playerCount\":3,\"javaAddress\":\"127.0.0.1:25565\",\"publicJava\":\"example.playit.gg:25565\",\"publicBedrock\":\"example.playit.gg:19132\",\"broadcast\":{\"ready\":true}}").getAsJsonObject();
        JsonArray worlds=new JsonArray();worlds.add(world);state.add("worlds",worlds);
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.gui.screen() instanceof AccessibilityOnboardingScreen){client.gui.setScreen(new TitleScreen());return;}
            if(client.gui.screen()==null||client.gui.overlay()!=null||System.currentTimeMillis()<next)return;
            next=System.currentTimeMillis()+900;
            try{
                switch(stage++){
                    case 0 -> client.gui.setScreen(new HostingScreen(new TitleScreen()));
                    case 1 -> {fixture(client,"worlds",worlds);fixture(client,"status",state);fixture(client,"nextPoll",Long.MAX_VALUE);fixture(client,"selected",0);layout(client);}
                    case 2 -> screenshot(client,"hosting-01-worlds.png");
                    case 3 -> client.gui.setScreen(new HostManageScreen(new TitleScreen(),world));
                    case 4 -> {fixture(client,"state",state);fixture(client,"nextPoll",Long.MAX_VALUE);layout(client);}
                    case 5 -> screenshot(client,"hosting-02-overview.png");
                    case 6 -> {fixture(client,"tab",1);layout(client);}
                    case 7 -> screenshot(client,"hosting-03-content.png");
                    case 8 -> {fixture(client,"tab",2);layout(client);}
                    case 9 -> screenshot(client,"hosting-04-sharing.png");
                    case 10 -> client.gui.setScreen(new HostDatapacksScreen(new TitleScreen(),"fixture-world"));
                    case 11 -> {fixture(client,"editable",true);fixture(client,"packs",JsonParser.parseString("[{\"id\":\"active:example.zip\",\"filename\":\"example.zip\",\"name\":\"Example recipes\",\"description\":\"Extra crafting recipes for friends\",\"version\":\"1.0\",\"valid\":true,\"enabled\":true},{\"id\":\"disabled:other.zip\",\"filename\":\"other.zip\",\"name\":\"Another pack\",\"description\":\"Optional content\",\"valid\":true,\"enabled\":false}]").getAsJsonArray());fixture(client,"selected",0);layout(client);}
                    case 12 -> screenshot(client,"hosting-05-datapacks.png");
                    case 13 -> {fixture(client,"browse",true);fixture(client,"packs",new JsonArray());layout(client);}
                    case 14 -> screenshot(client,"hosting-06-search.png");
                    case 15 -> client.gui.setScreen(new HostPackImportScreen(new TitleScreen(),"fixture-world","/tmp/example.zip"));
                    case 16 -> screenshot(client,"hosting-07-import.png");
                    case 17 -> {System.out.println("HOSTING_UI_SMOKE_PASS");client.stop();}
                }
            }catch(Throwable error){error.printStackTrace();client.stop();}
        });
    }
    void fixture(Minecraft client,String name,Object value)throws Exception{var field=client.gui.screen().getClass().getDeclaredField(name);field.setAccessible(true);field.set(client.gui.screen(),value);}
    void layout(Minecraft client)throws Exception{var screen=(MenuScreen)client.gui.screen();++screen.requestRevision;screen.loading=false;screen.message="";var method=screen.getClass().getDeclaredMethod("layout");method.setAccessible(true);method.invoke(screen);}
}
