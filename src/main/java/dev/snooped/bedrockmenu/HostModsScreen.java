// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class HostModsScreen extends MenuScreen {
    private final String id;private String query="",searchQuery="";private boolean browse;private int selected=-1,page,perPage=1,total;private JsonArray mods=new JsonArray();
    HostModsScreen(Screen parent,String id){super(parent,"Server mods");this.id=id;}
    @Override protected void init(){layout();if(!browse)load();}
    private void layout(){clearWidgets();dimensions();int half=(contentWidth-4)/2;button("Installed",left,46,half,()->{++requestRevision;loading=false;browse=false;selected=-1;page=0;mods=new JsonArray();layout();load();}).active=browse;button("Browse Modrinth",left+half+4,46,half,()->{++requestRevision;loading=false;browse=true;selected=-1;page=0;mods=new JsonArray();layout();}).active=!browse;
        int start=browse?98:72;if(browse){field("Search server-side Fabric mods",query,left,72,contentWidth-78,v->query=v);button("Search",left+contentWidth-74,72,74,this::search).active=!loading;}
        perPage=Math.max(1,(height-start-83)/46);page=Math.clamp(page,0,Math.max(0,(mods.size()-1)/perPage));for(int i=page*perPage;i<Math.min(mods.size(),(page+1)*perPage);i++){final int index=i;JsonObject mod=mods.get(i).getAsJsonObject();String detail=first(mod,"description","version","file","filename");if(!browse&&bool(mod,"important"))detail+=" · "+(bool(mod,"enabled")?"Enabled":"Disabled")+" · Optional performance mod";row(first(mod,"title","name","projectId","file"),detail,browse?"Modrinth":bool(mod,"required")?"Required":bool(mod,"important")?"Important":bool(mod,"enabled")?"Enabled":"Disabled",Icon.MOD,start+(i-page*perPage)*46,selected==i,true,()->{selected=index;layout();});}
        pageButtons(page,mods.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-78);JsonObject mod=selectedMod();
        if(browse)button("Install on server",left,height-51,contentWidth,this::install).active=mod!=null&&!loading;
        else{button(mod!=null&&bool(mod,"enabled")?"Disable":"Enable",left,height-51,half,this::toggle).active=mod!=null&&!loading&&!bool(mod,"required");button("Remove",left+half+4,height-51,half,this::remove).active=mod!=null&&!loading&&!bool(mod,"required");}
        button("Back",left,height-26,70,this::onClose);if(browse&&mods.size()<total&&mods.size()<1000)button("More results",left+contentWidth-96,height-26,96,this::more).active=!loading;
    }
    private JsonObject selectedMod(){return selected>=0&&selected<mods.size()?mods.get(selected).getAsJsonObject():null;}
    private void load(){if(loading)return;request("hosting.mods",object("id",id),r->{mods=array(r,"mods");layout();});}
    private void search(){if(loading)return;searchQuery=query.strip();message="Searching Modrinth…";request("hosting.modSearch",object("id",id,"query",searchQuery),r->{mods=array(r,"hits");total=number(r,"total",mods.size());selected=-1;page=0;layout();});layout();}
    private void more(){if(loading)return;int firstNew=mods.size();JsonObject p=object("id",id,"query",searchQuery);p.addProperty("offset",firstNew);request("hosting.modSearch",p,r->{mods.addAll(array(r,"hits"));total=number(r,"total",mods.size());page=firstNew/perPage;selected=-1;layout();});layout();}
    private void install(){JsonObject mod=selectedMod();if(mod==null||loading)return;String title=first(mod,"title","name");minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes){message="Installing "+title+"…";request("hosting.modInstall",object("id",id,"projectId",text(mod,"projectId")),r->{browse=false;selected=-1;mods=new JsonArray();load();});layout();}},Component.literal("Install "+title+"?"),Component.literal("This changes the dedicated server only. Stop your hosted world before changing mods.")));}
    private void toggle(){JsonObject mod=selectedMod();if(mod==null||loading)return;JsonObject params=object("id",id,"projectId",text(mod,"projectId"));params.addProperty("enabled",!bool(mod,"enabled"));request("hosting.modToggle",params,r->load());layout();}
    private void remove(){JsonObject mod=selectedMod();if(mod==null||loading)return;minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)request("hosting.modRemove",object("id",id,"projectId",text(mod,"projectId")),r->{selected=-1;load();});},Component.literal("Remove server mod?"),Component.literal(first(mod,"title","name","file"))));}
    @Override void onRequestError(){layout();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"Server-side Fabric 26.2 mods. Geyser support depends on the mod.",false);if(mods.isEmpty())empty(g,loading?"Loading…":browse?"Find a server mod":"No additional server mods",browse?"Search Modrinth for server-side Fabric mods. Your Minecraft client is never changed.":"Geyser and the server runtime are managed automatically.",browse?125:98);pageText(g,page,mods.size(),perPage,height-78);status(g,mx,my);}
}
