// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class HostManageScreen extends MenuScreen {
    final JsonObject world;private JsonObject state=new JsonObject();private long nextPoll;private boolean operation,statusKnown;private String result="";private int tab;
    HostManageScreen(Screen parent,JsonObject world){super(parent,text(world,"name"));this.world=world;}
    private String id(){return text(world,"id");}
    @Override protected void init(){layout();poll();}
    private boolean thisWorld(){return id().equals(text(state,"worldId"));}
    private boolean running(){return thisWorld()&&bool(state,"running");}
    private boolean activeWorld(){return thisWorld()&&(!text(state,"state").equals("stopped")&&!text(state,"state").equals("error"));}
    private boolean broadcastEnabled(){return !world.has("broadcastEnabled")||bool(world,"broadcastEnabled");}
    private void applyState(JsonObject value){state=value;statusKnown=true;for(JsonElement e:array(value,"worlds")){JsonObject saved=e.getAsJsonObject();if(id().equals(text(saved,"id"))){world.addProperty("visibility",text(saved,"visibility"));if(saved.has("broadcastEnabled"))world.addProperty("broadcastEnabled",bool(saved,"broadcastEnabled"));}}}
    private int actionsY(){return Math.min(height-57,254);}
    private void layout(){
        clearWidgets();dimensions();int w=(contentWidth-8)/3,half=(contentWidth-20)/2,x=left+8,y=actionsY();
        boolean active=activeWorld(),ready=running()&&bool(state,"ready");
        String[] tabs={"Overview","Content","Sharing"};
        for(int i=0;i<tabs.length;i++){final int selected=i;button(tabs[i],left+i*(w+4),46,i==2?contentWidth-2*(w+4):w,()->{tab=selected;result="";layout();}).active=tab!=i;}
        if(tab==0){
            button("Players",x,y-26,half,()->minecraft.gui.setScreen(new HostPlayersScreen(this,id(),minecraft.getUser().getName()))).active=ready;
            button("Chat & console",x+half+4,y-26,half,()->minecraft.gui.setScreen(new HostConsoleScreen(this,id()))).active=thisWorld();
            button(active?"Stop world":"Start world",left,y,w,()->{if(active)stop();else command("hosting.start",object("id",id(),"ownerJavaName",minecraft.getUser().getName(),"ownerJavaUuid",minecraft.getUser().getProfileId().toString()));}).active=!operation&&(thisWorld()||java.util.Set.of("stopped","error").contains(text(state,"state")));
            var join=button("Join",left+w+4,y,w,()->INSTANCE.reconnectHosted(id(),this));join.active=ready&&minecraft.level==null&&!INSTANCE.busy();
            join.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Connect directly to this Java server.")));
            var bedrock=button("Join Via ViaBedrock",left+2*(w+4),y,contentWidth-2*(w+4),()->INSTANCE.join("hosted",id(),this));bedrock.active=join.active;
            bedrock.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Test this world's Bedrock connection through ViaBedrock and Geyser.")));
        }else if(tab==1){
            row("Server mods","Browse and manage server-side Fabric mods","Manage",Icon.MOD,108,false,true,()->minecraft.gui.setScreen(new HostModsScreen(this,id())));
            row("Datapacks","Import ZIPs or discover packs on Modrinth","Manage",Icon.WORLD,156,false,true,()->minecraft.gui.setScreen(new HostDatapacksScreen(this,id())));
        }else{
            boolean shared=broadcastEnabled();
            button(shared?"Xbox sharing: On":"Xbox sharing: Off",x,y-52,half,()->{JsonObject p=object("id",id());p.addProperty("enabled",!shared);command("hosting.broadcast",p);}).active=!loading&&!operation;
            button("World access",x+half+4,y-52,half,()->minecraft.gui.setScreen(new HostAccessScreen(this,world)));
            button("Public connection",x,y-26,half,()->minecraft.gui.setScreen(new TunnelScreen(this)));
            button("Invite friends",x+half+4,y-26,half,()->minecraft.gui.setScreen(new FriendsScreen(this))).active=ready&&bool(child(state,"broadcast"),"ready");
            button("Test Bedrock connection",left,y,contentWidth,()->{result="Checking Geyser…";request("hosting.geyserTest",object("id",id()),data->{result=first(data,"message","status","error");if(result.isBlank())result=bool(data,"ok")?"Geyser responded successfully.":"Geyser did not respond.";layout();});}).active=ready&&!loading;
        }
        button("Back",left,height-26,70,this::onClose);
        var delete=button("Delete world",left+contentWidth-84,height-26,84,this::confirmDelete);
        delete.active=statusKnown&&!loading&&!operation&&!active&&!running();
        delete.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(active||running()?"Stop this world before deleting it.":"Permanently delete this hosted world, its server mods and settings.")));
    }
    private void confirmDelete(){
        if(!statusKnown||loading||operation||activeWorld()||running())return;
        String name=text(world,"name");
        ConfirmScreen confirmation=new ConfirmScreen(yes->{
            // Suppress the status poll triggered by reopening this screen so it
            // cannot supersede the destructive request's completion callback.
            if(yes)operation=true;
            minecraft.gui.setScreen(this);
            if(!yes)return;
            JsonObject params=object("id",id(),"name",name);params.addProperty("confirmed",true);
            message="Deleting world…";
            request("hosting.delete",params,data->{
                operation=false;
                HostingScreen list=new HostingScreen(parent instanceof HostingScreen hosting?hosting.parent:parent);
                minecraft.gui.setScreen(list);
            });layout();
        },Component.literal("Delete "+name+"?"),Component.literal("This permanently deletes this hosted world's save, player progress, server mods and settings. This cannot be undone. Other hosted worlds are kept."),Component.literal("Delete world"),Component.literal("Cancel"));
        minecraft.gui.setScreen(confirmation);confirmation.setDelay(20);
    }
    private void stop(){minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)command("hosting.stop",object("id",id()));},Component.literal("Stop hosted world?"),Component.literal("Players will disconnect. The server saves the world before stopping.")));}
    private void command(String method,JsonObject params){if(operation)return;operation=true;request(method,params,data->{operation=false;applyState(data);layout();nextPoll=0;});layout();}
    private void poll(){if(loading||operation)return;nextPoll=System.currentTimeMillis()+2500;request("hosting.status",new JsonObject(),data->{applyState(data);layout();});}
    @Override void onRequestError(){operation=false;layout();}
    @Override public void tick(){if(minecraft.gui.screen()==this&&System.currentTimeMillis()>nextPoll)poll();}
    private void info(GuiGraphicsExtractor g,String label,String value,int y){
        int offset=Math.min(68,contentWidth/4);g.text(font,label,left+12,y,MUTED);
        g.text(font,font.plainSubstrByWidth(value,contentWidth-offset-24),left+12+offset,y,INK);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int bottom=tab==1?Math.max(actionsY()-6,204):actionsY()-6;
        g.fill(left,76,left+contentWidth,bottom,0xB00D100D);g.outline(left,76,contentWidth,bottom-76,0xFF454B41);
        g.fill(left+1,77,left+3,99,running()?GREEN:0xFF6D815F);
        super.extractRenderState(g,mx,my,delta);header(g,together("Java + Bedrock",text(world,"gamemode"),text(world,"difficulty")),false);
        String heading=tab==0?(running()&&bool(state,"ready")?"World online":activeWorld()?"Preparing your world":"World offline"):tab==1?"Make this world yours":"Play together";
        g.text(font,heading,left+12,86,INK);
        if(tab==0){
            String badge=running()?number(state,"playerCount",0)+" / "+number(world,"maxPlayers",10)+" players":thisWorld()?text(state,"state"):"Stopped";
            badge=font.plainSubstrByWidth(badge,Math.max(40,contentWidth/3));g.text(font,badge,left+contentWidth-font.width(badge)-12,86,running()?GREEN:MUTED);
            info(g,"Java",running()?first(state,"publicJava","javaAddress"):"Start the world to connect",107);
            info(g,"Bedrock",running()?first(state,"publicBedrock","bedrockAddress"):"Geyser starts automatically",123);
            if(actionsY()>210)info(g,"Access","invite-only".equals(text(world,"visibility"))?"Invite-only":"Public",139);
        }else if(tab==1){
            if(actionsY()>236)g.text(font,"Stop this world before changing its content.",left+12,215,MUTED);
        }else{
            JsonObject sharing=thisWorld()?child(state,"broadcast"):new JsonObject();
            String hint=!broadcastEnabled()?"Xbox sharing is off. Friends can use your public address.":bool(sharing,"ready")?"Ready for Xbox invitations.":activeWorld()?"Preparing public addresses and Xbox sharing…":"Xbox sharing starts with your world.";
            g.text(font,font.plainSubstrByWidth(hint,contentWidth-24),left+12,107,MUTED);
            if(actionsY()>210)info(g,"Access","invite-only".equals(text(world,"visibility"))?"Only you and invited players":"Anyone with the address",125);
        }
        String detail=!result.isBlank()?result:thisWorld()?first(state,"error"):"";
        if(message.isBlank()&&!detail.isBlank()){g.centeredText(font,font.plainSubstrByWidth(detail,Math.max(20,contentWidth-155)),width/2,height-20,AMBER);if(my>=height-29)g.setTooltipForNextFrame(font,Component.literal(detail),mx,my);}
        status(g,mx,my);
    }
}
