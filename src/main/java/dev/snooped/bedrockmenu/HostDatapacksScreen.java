// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.nio.file.Path;
import java.util.List;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class HostDatapacksScreen extends MenuScreen {
    private final String id;
    private boolean browse,editable;
    private String query="",searchQuery="",folder="";
    private JsonArray packs=new JsonArray();
    private int selected=-1,page,perPage=1,total;
    HostDatapacksScreen(Screen parent,String id){super(parent,"Datapacks");this.id=id;}
    @Override protected void init(){layout();if(!browse)load();}
    private JsonObject selectedPack(){return selected>=0&&selected<packs.size()?packs.get(selected).getAsJsonObject():null;}
    private void changeTab(boolean browse){++requestRevision;loading=false;this.browse=browse;selected=-1;page=0;packs=new JsonArray();layout();if(!browse)load();}
    private void layout(){
        clearWidgets();dimensions();int third=(contentWidth-8)/3,half=(contentWidth-4)/2;
        button("Installed",left,46,third,()->changeTab(false)).active=browse&&!loading;
        button("Browse Modrinth",left+third+4,46,third,()->changeTab(true)).active=!browse&&!loading;
        button("Import ZIP",left+2*(third+4),46,contentWidth-2*(third+4),()->minecraft.gui.setScreen(new HostPackImportScreen(this,id,""))).active=editable&&!loading;
        int start=browse?116:92;
        if(browse){field("Search datapacks",query,left,90,contentWidth-78,v->query=v).setHint(Component.literal("Search datapacks for 26.2"));button("Search",left+contentWidth-74,90,74,this::search).active=!loading;}
        perPage=Math.max(1,(height-start-84)/46);page=Math.clamp(page,0,Math.max(0,(packs.size()-1)/perPage));
        for(int i=page*perPage;i<Math.min(packs.size(),(page+1)*perPage);i++){
            final int index=i;JsonObject pack=packs.get(i).getAsJsonObject();
            String badge=browse?"Modrinth":!bool(pack,"valid")?"Needs attention":bool(pack,"serverDisabled")?"Minecraft disabled":bool(pack,"enabled")?"Enabled":"Disabled";
            String detail=browse?text(pack,"description"):!bool(pack,"valid")?text(pack,"error"):bool(pack,"serverDisabled")?text(pack,"selectionNote"):together(first(pack,"description","filename"),text(pack,"version"));
            row(first(pack,"title","name","filename"),detail,badge,Icon.WORLD,start+(i-page*perPage)*46,selected==i,browse||bool(pack,"valid"),()->{selected=index;layout();});
        }
        pageButtons(page,packs.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-77);
        JsonObject pack=selectedPack();
        if(browse)button("Install datapack",left,height-51,contentWidth,this::install).active=editable&&pack!=null&&!loading;
        else{
            button(pack!=null&&bool(pack,"enabled")?"Disable":"Enable",left,height-51,half,this::toggle).active=editable&&pack!=null&&!loading&&(bool(pack,"enabled")||bool(pack,"valid"));
            button("Remove",left+half+4,height-51,half,this::remove).active=editable&&pack!=null&&!loading;
        }
        button("Back",left,height-26,70,this::onClose);
        if(browse&&packs.size()<total&&packs.size()<1000)button("More results",left+contentWidth-96,height-26,96,this::more).active=!loading;
        else if(!browse){button("Refresh",left+contentWidth-164,height-26,78,this::load).active=!loading;button("Open folder",left+contentWidth-82,height-26,82,()->{if(!folder.isBlank())Util.getPlatform().openPath(Path.of(folder));}).active=!folder.isBlank()&&java.nio.file.Files.isDirectory(Path.of(folder));}
    }
    private void load(){if(loading)return;request("hosting.datapacks",object("id",id),data->{packs=array(data,"datapacks");editable=bool(data,"editable");folder=text(data,"path");selected=-1;layout();});}
    private void search(){if(loading)return;searchQuery=query.strip();request("hosting.datapackSearch",object("query",searchQuery),data->{packs=array(data,"hits");total=number(data,"total",packs.size());selected=-1;page=0;layout();});layout();}
    private void more(){if(loading)return;int next=packs.size();JsonObject p=object("query",searchQuery);p.addProperty("offset",next);request("hosting.datapackSearch",p,data->{packs.addAll(array(data,"hits"));total=number(data,"total",packs.size());page=next/perPage;selected=-1;layout();});layout();}
    private void install(){
        JsonObject pack=selectedPack();if(pack==null||loading||!editable)return;
        String title=first(pack,"title","name");
        minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes){request("hosting.datapackInstall",object("id",id,"projectId",text(pack,"projectId")),r->{browse=false;page=0;load();});layout();}},Component.literal("Install "+title+"?"),Component.literal("Installs this datapack and required dependencies for Minecraft 26.2. Changes apply the next time this world starts.")));
    }
    private void toggle(){JsonObject pack=selectedPack();if(pack==null||loading||!editable)return;JsonObject p=object("id",id,"packId",text(pack,"id"));p.addProperty("enabled",!bool(pack,"enabled"));request("hosting.datapackToggle",p,r->load());layout();}
    private void remove(){JsonObject pack=selectedPack();if(pack==null||loading||!editable)return;minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes){request("hosting.datapackRemove",object("id",id,"packId",text(pack,"id")),r->load());layout();}},Component.literal("Remove "+first(pack,"name","filename")+"?"),Component.literal("Removing world-generation or gameplay packs can affect this world's saved content. A recovery copy is kept outside the world save.")));}
    @Override public void onFilesDrop(List<Path> paths){if(!editable||loading){message="Stop this world before importing datapacks.";return;}if(paths.size()!=1){message="Drop one datapack ZIP at a time.";return;}minecraft.gui.setScreen(new HostPackImportScreen(this,id,paths.getFirst().toAbsolutePath().toString()));}
    @Override void onRequestError(){layout();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);header(g,"World content · Minecraft 26.2",false);
        g.text(font,font.plainSubstrByWidth(editable?"Changes apply on the next world start.":"Stop this world to change datapacks.",contentWidth-16),left+8,75,editable?MUTED:AMBER);
        if(packs.isEmpty())empty(g,loading?"Loading…":browse?"Discover datapacks":"No datapacks installed",browse?"Search Modrinth for packs supporting Minecraft 26.2.":"Browse Modrinth, import a ZIP, or drop it onto this page.",browse?131:107);
        pageText(g,page,packs.size(),perPage,height-77);
        if(!message.isBlank()){
            g.text(font,font.plainSubstrByWidth(message,Math.max(10,contentWidth-(browse?182:250))),left+78,height-20,AMBER);
            if(my>=height-29)g.setTooltipForNextFrame(font,Component.literal(message),mx,my);
        }
    }
}
