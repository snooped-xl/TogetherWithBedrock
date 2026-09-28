// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.HashSet;
import java.util.Set;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

/** Invitations remain bound to the host session that opened the pause menu. */
final class HostInviteScreen extends MenuScreen {
    private final HostedContext context;
    private final long hostGeneration;
    private final String worldName;
    private final Set<String> invited=new HashSet<>();
    private JsonObject state;
    private JsonArray friends=new JsonArray();
    private String selectedXuid="",result="";
    private int page,perPage=1;
    private long nextPoll,nextFriendsPoll;
    private Button inviteButton;

    HostInviteScreen(Screen parent,HostedContext context,JsonObject state){
        super(parent,"Invite friends");this.context=context;this.state=state.deepCopy();
        this.hostGeneration=generation(state);this.worldName=text(state,"name");
    }
    @Override protected void init(){layout();refresh(true);}
    @Override public boolean isPauseScreen(){return false;}
    private static long generation(JsonObject state){try{return state.get("hostGeneration").getAsLong();}catch(Exception ignored){return -1;}}
    private boolean sameHost(){return context.active()&&bool(state,"authorized")&&bool(state,"ready")&&hostGeneration>=0&&generation(state)==hostGeneration;}
    private boolean canInvite(){return sameHost()&&bool(child(state,"broadcast"),"ready");}
    private JsonObject selected(){for(JsonElement row:friends){JsonObject friend=row.getAsJsonObject();if(selectedXuid.equals(text(friend,"xuid")))return friend;}return null;}

    private void layout(){
        clearWidgets();dimensions();perPage=Math.max(1,(height-167)/46);page=Math.clamp(page,0,Math.max(0,(friends.size()-1)/perPage));
        for(int i=page*perPage;i<Math.min(friends.size(),(page+1)*perPage);i++){
            JsonObject friend=friends.get(i).getAsJsonObject();String xuid=text(friend,"xuid");
            row(first(friend,"name","gamertag"),first(friend,"presence","status"),invited.contains(xuid)?"Invited":"Friend",Icon.PLAYER,82+(i-page*perPage)*46,selectedXuid.equals(xuid),!xuid.isBlank(),()->{selectedXuid=xuid;layout();});
        }
        pageButtons(page,friends.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-77);
        inviteButton=button(invited.contains(selectedXuid)?"Invitation sent":"Invite to this world",left,height-51,contentWidth,this::invite);
        inviteButton.active=selected()!=null&&!selectedXuid.isBlank()&&!invited.contains(selectedXuid)&&canInvite()&&!loading;
        inviteButton.setTooltip(Tooltip.create(Component.literal(canInvite()?"Send an Xbox invitation. This also grants access to an invite-only world.":availability())));
        button("Back",left,height-26,70,this::onClose);
        button("Refresh",left+contentWidth-80,height-26,80,()->refresh(true)).active=!loading&&context.active();
    }
    private String availability(){
        if(!context.active())return "Reconnect to your hosted world to invite friends.";
        if(generation(state)!=hostGeneration)return "The host restarted. Reopen Host leader before inviting.";
        if(!sameHost())return loading?"Checking your hosted world…":"Invitations require your active hosted world.";
        JsonObject broadcast=child(state,"broadcast");
        if(!bool(broadcast,"enabled"))return "Xbox sharing is off. Enable it in Hosting → Manage.";
        return switch(text(broadcast,"state")){
            case "ready"->"Select an Xbox friend to invite to this world.";
            case "signInRequired"->"Sign in from the Bedrock Account page to invite friends.";
            case "waitingForTunnel"->"Xbox invitations are waiting for a public Bedrock address.";
            case "error"->first(broadcast,"error").isBlank()?"Xbox sharing needs attention in Hosting → Manage.":text(broadcast,"error");
            default->"Connecting this world to Xbox…";
        };
    }
    private void refresh(boolean forceFriends){
        if(loading||!context.active())return;nextPoll=System.currentTimeMillis()+4000;
        request("hosting.leaderStatus",context.parameters(),data->{
            state=data;
            if(!sameHost()){layout();return;}
            if(forceFriends||System.currentTimeMillis()>=nextFriendsPoll){
                nextFriendsPoll=System.currentTimeMillis()+20000;
                request("friends",new JsonObject(),people->{friends=array(people,"friends");if(selected()==null)selectedXuid="";layout();});
            }
            layout();
        });layout();
    }
    private void invite(){
        JsonObject friend=selected();if(loading||friend==null||selectedXuid.isBlank()||invited.contains(selectedXuid)||!canInvite())return;
        String xuid=selectedXuid,name=first(friend,"name","gamertag");JsonObject params=context.parameters();
        params.addProperty("hostGeneration",hostGeneration);params.addProperty("xuid",xuid);params.addProperty("name",name);
        result="";request("hosting.leaderInvite",params,data->{invited.add(xuid);result="Invitation sent to "+name+".";nextPoll=0;layout();});layout();
    }
    @Override void onRequestError(){nextPoll=System.currentTimeMillis()+5000;layout();}
    @Override public void tick(){
        if(inviteButton!=null)inviteButton.active=selected()!=null&&!selectedXuid.isBlank()&&!invited.contains(selectedXuid)&&canInvite()&&!loading;
        if(!loading&&System.currentTimeMillis()>=nextPoll)refresh(false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);header(g,worldName,false);
        String availability=availability();g.text(font,font.plainSubstrByWidth(availability,contentWidth-16),left+8,51,canInvite()?GREEN:AMBER);
        if(my>=45&&my<65)g.setTooltipForNextFrame(font,Component.literal(availability),mx,my);
        g.text(font,"Xbox friends",left+8,68,MUTED);
        if(friends.isEmpty())empty(g,loading?"Loading friends…":"No friends to invite","Your Xbox friends appear here. Add friends from the Bedrock Friends page.",95);
        pageText(g,page,friends.size(),perPage,height-77);
        if(!result.isBlank()&&message.isBlank())g.centeredText(font,font.plainSubstrByWidth(result,Math.max(20,contentWidth-160)),width/2,height-20,GREEN);
        status(g,mx,my);
    }
}
