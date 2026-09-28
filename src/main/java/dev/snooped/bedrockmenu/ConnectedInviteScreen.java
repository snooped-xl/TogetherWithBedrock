// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import java.util.HashSet;
import java.util.Set;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class ConnectedInviteScreen extends MenuScreen {
    private final ConnectedInvites.Context context;
    private JsonObject state;
    private JsonArray friends=new JsonArray();
    private final Set<String> sent=new HashSet<>();
    private String selected="",result="";
    private int page,perPage=1;
    private long nextPoll;
    private boolean prepared;
    private Button invite;
    ConnectedInviteScreen(Screen parent,ConnectedInvites.Context context,JsonObject state){super(parent,"Invite Xbox friends");this.context=context;this.state=state.deepCopy();}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){layout();if(!prepared){prepared=true;refresh(true);} }
    private void layout(){
        clearWidgets();dimensions();perPage=Math.max(1,(height-160)/46);page=Math.clamp(page,0,Math.max(0,(friends.size()-1)/perPage));
        for(int i=page*perPage;i<Math.min(friends.size(),(page+1)*perPage);i++){
            JsonObject friend=friends.get(i).getAsJsonObject();String xuid=text(friend,"xuid");
            row(first(friend,"name","gamertag"),first(friend,"presence","status"),sent.contains(xuid)?"Invited":"Friend",Icon.PLAYER,76+(i-page*perPage)*46,selected.equals(xuid),!xuid.isBlank(),()->{selected=xuid;layout();});
        }
        pageButtons(page,friends.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-77);
        invite=button(sent.contains(selected)?"Invitation sent":"Invite to this server / world",left,height-51,contentWidth,this::send);
        invite.active=canSend();button("Back",left,height-26,70,this::onClose);
        button("Refresh",left+contentWidth-80,height-26,80,()->refresh(true)).active=!loading&&context.active();
    }
    private boolean canSend(){return context.active()&&!loading&&bool(state,"ready")&&!selected.isBlank()&&!sent.contains(selected);}
    private void refresh(boolean loadFriends){
        if(loading||!context.active())return;nextPoll=System.currentTimeMillis()+3000;
        request("connectedInvites.prepare",context.parameters(),data->{
            state=data;layout();if(loadFriends)request("friends",new JsonObject(),people->{friends=array(people,"friends");layout();});
        });
    }
    private void send(){
        if(!canSend())return;String xuid=selected;JsonObject p=context.parameters();p.addProperty("xuid",xuid);
        request("connectedInvites.send",p,data->{sent.add(xuid);result="Invitation sent. The server's normal access rules still apply.";layout();});layout();
    }
    @Override void onRequestError(){nextPoll=System.currentTimeMillis()+6000;layout();}
    @Override public void tick(){if(invite!=null)invite.active=canSend();if(!loading&&context.active()&&System.currentTimeMillis()>=nextPoll)refresh(false);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);header(g,text(state,"name"),false);
        String hint=!context.active()?"This connection has ended.":bool(state,"ready")?"Select a friend to invite.":text(state,"state").equals("error")?text(state,"error"):"Connecting invitations to Xbox…";
        g.text(font,font.plainSubstrByWidth(hint,contentWidth-16),left+8,53,bool(state,"ready")?GREEN:AMBER);
        if(friends.isEmpty())empty(g,loading?"Loading friends…":"No friends to invite","Add Xbox friends from the Bedrock Friends page.",92);
        pageText(g,page,friends.size(),perPage,height-77);
        if(message.isBlank()&&!result.isBlank())g.centeredText(font,font.plainSubstrByWidth(result,Math.max(20,contentWidth-170)),width/2,height-20,GREEN);
        status(g,mx,my);
    }
}
