// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class HostPlayersScreen extends MenuScreen {
    private final String id,self;private JsonArray players=new JsonArray();private int selected=-1,page,perPage=1;private long nextPoll,hostGeneration=-1;
    HostPlayersScreen(Screen parent,String id,String self){super(parent,"Players");this.id=id;this.self=self;}
    @Override protected void init(){layout();poll();}
    private void layout(){clearWidgets();dimensions();perPage=Math.max(1,(height-150)/46);page=Math.clamp(page,0,Math.max(0,(players.size()-1)/perPage));for(int i=page*perPage;i<Math.min(players.size(),(page+1)*perPage);i++){final int index=i;JsonObject p=players.get(i).getAsJsonObject();row(text(p,"name"),text(p,"name").equals(self)?"You":"Connected player",bool(p,"op")?"Operator":"Player",Icon.PLAYER,48+(i-page*perPage)*46,selected==i,true,()->{selected=index;layout();});}
        pageButtons(page,players.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-103);int w=(contentWidth-12)/4;JsonObject p=selectedPlayer();
        String[] modes={"survival","creative","adventure","spectator"};for(int i=0;i<modes.length;i++){String mode=modes[i];button(Character.toUpperCase(mode.charAt(0))+mode.substring(1),left+i*(w+4),height-76,w,()->gamemode(mode)).active=p!=null&&!loading;}
        button("Kick",left,height-51,w,()->confirm("kick")).active=p!=null&&!loading;button("Ban",left+w+4,height-51,w,()->confirm("ban")).active=p!=null&&!loading;button("Give OP",left+2*(w+4),height-51,w,()->confirm("op")).active=p!=null&&!bool(p,"op")&&!loading;button("Remove OP",left+3*(w+4),height-51,w,()->confirm("deop")).active=p!=null&&bool(p,"op")&&!loading;button("Back",left,height-26,70,this::onClose);
        button("Manage my OP",left+contentWidth-130,height-26,130,()->minecraft.gui.setScreen(new SelfOperatorScreen(this,id,self)));
    }
    private JsonObject selectedPlayer(){return selected>=0&&selected<players.size()?players.get(selected).getAsJsonObject():null;}
    private void gamemode(String mode){JsonObject p=selectedPlayer();if(p==null||loading)return;request("hosting.playerAction",object("id",id,"hostGeneration",Long.toString(hostGeneration),"name",text(p,"name"),"action","gamemode","value",mode),data->poll());}
    private void confirm(String action){JsonObject p=selectedPlayer();if(p==null)return;String name=text(p,"name");long run=hostGeneration;String question=switch(action){case "kick"->"Kick "+name+"?";case "ban"->"Ban "+name+"?";case "op"->"Give "+name+" operator access?";default->"Remove operator access from "+name+"?";};minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)request("hosting.playerAction",object("id",id,"hostGeneration",Long.toString(run),"name",name,"action",action),d->poll());},Component.literal(question),Component.literal(action.equals("op")?"Operators can change world settings and run server commands.":action.equals("ban")?"They cannot rejoin until unbanned. Use /pardon in Chat & console to undo this.":"This only affects this hosted server.")));}
    private void poll(){if(loading)return;nextPoll=System.currentTimeMillis()+3000;String selectedName=selectedPlayer()==null?"":text(selectedPlayer(),"name");request("hosting.players",object("id",id),data->{players=array(data,"players");hostGeneration=data.get("hostGeneration").getAsLong();selected=-1;for(int i=0;i<players.size();i++)if(text(players.get(i).getAsJsonObject(),"name").equals(selectedName))selected=i;layout();});}
    @Override void onRequestError(){layout();}
    @Override public void tick(){if(System.currentTimeMillis()>nextPoll)poll();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"Manage everyone on your hosted world, including yourself.",false);if(players.isEmpty())empty(g,"No players connected","You can still change your own operator status below.",75);pageText(g,page,players.size(),perPage,height-103);status(g,mx,my);}
    private static final class SelfOperatorScreen extends MenuScreen {
        final String id,name;SelfOperatorScreen(Screen p,String id,String name){super(p,"Your operator access");this.id=id;this.name=name;}
        @Override protected void init(){dimensions();button("Give myself OP",left+8,92,contentWidth-16,()->change("op"));button("Remove my OP",left+8,120,contentWidth-16,()->change("deop"));button("Back",width/2-70,height-30,140,this::onClose);}
        void change(String action){if(loading)return;request("hosting.playerAction",object("id",id,"name",name,"action",action),r->message="Operator access updated for "+name+".");}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,name,false);paragraph(g,"Your hosting controls remain available even when you remove your own OP.",52,MUTED);status(g,mx,my);}
    }
}
