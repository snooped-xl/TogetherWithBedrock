// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class HostingScreen extends MenuScreen {
    private JsonArray worlds=new JsonArray();private JsonObject status=new JsonObject();private int selected=-1,page,perPage=1;private long nextPoll;
    HostingScreen(Screen parent){super(parent,"Hosting");}
    @Override protected void init(){layout();refresh();}
    private void layout(){clearWidgets();chrome(5);perPage=Math.max(1,(height-196)/46);page=Math.clamp(page,0,Math.max(0,(worlds.size()-1)/perPage));
        for(int i=page*perPage;i<Math.min(worlds.size(),(page+1)*perPage);i++){final int index=i;JsonObject world=worlds.get(i).getAsJsonObject();boolean running=text(status,"worldId").equals(text(world,"id"))&&bool(status,"running");row(text(world,"name"),together("Fabric 26.2",text(world,"gamemode"),"invite-only".equals(text(world,"visibility"))?"Invite-only":"Public",world.has("broadcastEnabled")&&!bool(world,"broadcastEnabled")?"Xbox off":""),text(status,"worldId").equals(text(world,"id"))?(bool(status,"ready")?number(status,"playerCount",0)+" / "+number(world,"maxPlayers",10)+" players":text(status,"state")):"Offline",Icon.WORLD,119+(i-page*perPage)*46,selected==i,true,()->{selected=index;layout();}).activateWith(()->minecraft.gui.setScreen(new HostManageScreen(this,world)));}
        pageButtons(page,worlds.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-77);int w=(contentWidth-8)/3,y=height-51;
        button("Create world",left,y,w,()->minecraft.gui.setScreen(new HostCreateScreen(this)));
        button("Manage world",left+w+4,y,w,()->{JsonObject world=selectedWorld();if(world!=null)minecraft.gui.setScreen(new HostManageScreen(this,world));}).active=selectedWorld()!=null&&!loading;
        button("Public connection",left+2*(w+4),y,w,()->minecraft.gui.setScreen(new TunnelScreen(this)));
    }
    private JsonObject selectedWorld(){return selected>=0&&selected<worlds.size()?worlds.get(selected).getAsJsonObject():null;}
    void refresh(){if(loading)return;nextPoll=System.currentTimeMillis()+4000;String id=selectedWorld()==null?"":text(selectedWorld(),"id");request("hosting.status",new JsonObject(),data->{status=data;worlds=array(data,"worlds");selected=-1;for(int i=0;i<worlds.size();i++)if(id.equals(text(worlds.get(i).getAsJsonObject(),"id")))selected=i;if(selected<0&&worlds.size()==1)selected=0;layout();});}
    @Override void onRequestError(){layout();}
    @Override public void tick(){if(!loading&&System.currentTimeMillis()>nextPoll)refresh();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(left,75,left+contentWidth,111,0xB00D100D);g.outline(left,75,contentWidth,36,0xFF454B41);
        super.extractRenderState(g,mx,my,delta);header(g,together(accountName(),"Hosting"),true);
        g.text(font,"Your hosted worlds",left+10,82,INK);
        String summary=bool(status,"ready")?text(status,"name")+" · "+number(status,"playerCount",0)+" players online":worlds.size()+" saved "+(worlds.size()==1?"world":"worlds")+" · Choose a world to manage";
        g.text(font,font.plainSubstrByWidth(summary,contentWidth-20),left+10,97,bool(status,"ready")?GREEN:MUTED);
        if(worlds.isEmpty())empty(g,loading?"Loading hosted worlds…":"Make a world for everyone","Create a world, add mods or datapacks, and invite Java and Bedrock friends.",138);
        pageText(g,page,worlds.size(),perPage,height-77);status(g,mx,my);
    }
}
