// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

/** In-game controls are bound to one live connection and one owner-verified world. */
final class HostLeaderScreen extends MenuScreen {
    private final HostedContext context;
    private JsonObject state=new JsonObject();
    private String selectedUuid="",result="";
    private int tab,page,perPage=1;
    private long nextPoll;
    private boolean operation;

    HostLeaderScreen(Screen parent,HostedContext context){super(parent,"Host leader");this.context=context;}
    @Override protected void init(){layout();poll();}
    @Override public boolean isPauseScreen(){return false;}
    private JsonObject self(){return child(state,"self");}
    private boolean available(){return context.active()&&bool(state,"authorized")&&bool(state,"ready");}
    private int permission(){return number(self(),"opLevel",0);}
    private JsonArray entries(){return array(state,tab==2?"bans":"players");}
    private JsonObject selected(){for(JsonElement entry:entries()){JsonObject player=entry.getAsJsonObject();if(selectedUuid.equals(text(player,"uuid")))return player;}return null;}
    private boolean can(int level){return available()&&!loading&&!operation&&permission()>=level;}
    private void selectTab(int value){tab=value;page=0;selectedUuid="";layout();}

    private void layout(){
        clearWidgets();dimensions();int tw=(contentWidth-16)/5;
        String[] tabs={"Players","World","Bans"};for(int i=0;i<tabs.length;i++){int target=i;button(tabs[i],left+i*(tw+4),45,tw,()->selectTab(target)).active=tab!=i;}
        button("Console",left+3*(tw+4),45,tw,()->minecraft.gui.setScreen(new HostConsoleScreen(this,context.id(),context))).active=available();
        Button invite=button("Invite",left+4*(tw+4),45,contentWidth-4*(tw+4),()->{
            if(available()&&state.has("hostGeneration"))minecraft.gui.setScreen(new HostInviteScreen(this,context,state));
        });
        invite.active=available()&&!loading&&!operation&&state.has("hostGeneration");
        invite.setTooltip(Tooltip.create(Component.literal("Invite Xbox friends to this hosted world.")));
        if(tab==1)worldControls();else playerControls();
        button("Back",left,height-26,70,this::onClose);
        boolean op=bool(self(),"op");Button own=button(op?"Remove my OP":"Give myself OP",left+contentWidth-130,height-26,130,()->confirm(op?"selfDeop":"selfOp",null,""));
        own.active=available()&&!loading&&!operation;
        own.setTooltip(Tooltip.create(Component.literal("As the world owner, you can restore your own OP here. Other controls follow your current operator permissions.")));
    }
    private void playerControls(){
        JsonArray players=entries();perPage=Math.max(1,(height-202)/46);page=Math.clamp(page,0,Math.max(0,(players.size()-1)/perPage));
        for(int i=page*perPage;i<Math.min(players.size(),(page+1)*perPage);i++){
            JsonObject player=players.get(i).getAsJsonObject();String uuid=text(player,"uuid");
            String name=text(player,"name"),detail=tab==2?text(player,"reason"):together(uuid.equals(text(self(),"uuid"))?"You":"Connected player",label(text(player,"gamemode")));
            String badge=tab==2?"Banned":bool(player,"op")?"OP "+number(player,"opLevel",0):"Player";
            row(name.isBlank()?uuid:name,detail,badge,Icon.PLAYER,85+(i-page*perPage)*46,uuid.equals(selectedUuid),true,()->{selectedUuid=uuid;layout();});
        }
        pageButtons(page,players.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-109);
        JsonObject player=selected();
        if(tab==2){Button pardon=button("Unban selected player",left,height-59,contentWidth,()->confirm("pardon",selected(),""));pardon.active=player!=null&&can(3);permissionTooltip(pardon,3);return;}
        String[] modes={"survival","creative","adventure","spectator"};int w=(contentWidth-12)/4;
        for(int i=0;i<modes.length;i++){
            String mode=modes[i];Button b=button(label(mode),left+i*(w+4),height-84,w,()->act("gamemode",selected(),mode));
            b.active=player!=null&&can(2)&&!mode.equals(text(player,"gamemode"));permissionTooltip(b,2);
        }
        int mw=(contentWidth-8)/3;
        Button kick=button("Kick",left,height-59,mw,()->confirm("kick",selected(),""));kick.active=player!=null&&can(3);permissionTooltip(kick,3);
        Button ban=button("Ban",left+mw+4,height-59,mw,()->confirm("ban",selected(),""));ban.active=player!=null&&can(3);permissionTooltip(ban,3);
        boolean op=player!=null&&bool(player,"op");Button changeOp=button(op?"Remove OP":"Give OP",left+2*(mw+4),height-59,mw,()->confirm(op?"deop":"op",selected(),""));changeOp.active=player!=null&&can(3);permissionTooltip(changeOp,3);
    }
    private void worldControls(){
        int w=(contentWidth-12)/4,y=91;
        String[] times={"day","noon","night","midnight"};for(int i=0;i<times.length;i++){String value=times[i];worldButton(label(value),left+i*(w+4),y,w,"time",value);}
        int ww=(contentWidth-8)/3;y+=32;
        String[] weather={"clear","rain","thunder"};for(int i=0;i<weather.length;i++){String value=weather[i];worldButton(label(value),left+i*(ww+4),y,ww,"weather",value);}
        String[] levels={"peaceful","easy","normal","hard"};y+=32;for(int i=0;i<levels.length;i++){String value=levels[i];Button b=worldButton(label(value),left+i*(w+4),y,w,"difficulty",value);if(value.equals(text(state,"difficulty")))b.active=false;}
        Button save=button("Save world now",left,height-51,contentWidth,()->act("save",null,""));save.active=can(2);permissionTooltip(save,2);
    }
    private Button worldButton(String name,int x,int y,int w,String action,String value){Button b=button(name,x,y,w,()->act(action,null,value));b.active=can(2);permissionTooltip(b,2);return b;}
    private void permissionTooltip(Button button,int level){button.setTooltip(Tooltip.create(Component.literal("Requires operator level "+level+" on this world.")));}
    private static String label(String value){return value.isBlank()?"":Character.toUpperCase(value.charAt(0))+value.substring(1);}

    private void confirm(String action,JsonObject selected,String value){
        if(!available()||operation||loading)return;
        JsonObject target=selected==null?null:selected.deepCopy();
        String name=target==null?"yourself":text(target,"name");
        String heading=switch(action){case "kick"->"Kick "+name+"?";case "ban"->"Ban "+name+"?";case "pardon"->"Unban "+name+"?";case "op"->"Give "+name+" operator access?";case "deop"->"Remove operator access from "+name+"?";case "selfOp"->"Restore your operator access?";default->"Remove your operator access?";};
        String detail=switch(action){case "ban"->"They will disconnect and cannot rejoin this world until unbanned.";case "kick"->"They will disconnect, but can rejoin this world.";case "op","selfOp"->"Operators can change world settings and run server commands.";case "selfDeop"->"Your game permissions will be removed. As the host, you can restore your OP here.";case "pardon"->"This player may rejoin, subject to the world's access settings.";default->"This changes permissions only on your hosted world.";};
        minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)act(action,target,value);},Component.literal(heading),Component.literal(detail)));
    }
    private void act(String action,JsonObject target,String value){
        // Recheck after confirmation: disconnects and permission changes may happen meanwhile.
        boolean own=action.equals("selfOp")||action.equals("selfDeop");
        int level=switch(action){case "gamemode","save","time","weather","difficulty"->2;default->3;};
        if(!available()||operation||(!own&&permission()<level)){message="These controls are no longer available for this connection.";return;}
        if((action.equals("gamemode")||action.equals("kick")||action.equals("ban")||action.equals("pardon")||action.equals("op")||action.equals("deop"))&&target==null)return;
        JsonObject params=context.parameters();params.addProperty("action",action);
        if(target!=null)params.addProperty("targetUuid",text(target,"uuid"));if(!value.isBlank())params.addProperty("value",value);
        operation=true;result="";
        request("hosting.leaderAction",params,data->{operation=false;if(!context.active()){state=new JsonObject();layout();return;}result=first(data,"message");if(result.isBlank())result="Change sent to your hosted server.";nextPoll=0;layout();});layout();
    }
    private void poll(){
        if(loading||operation||!context.active())return;nextPoll=System.currentTimeMillis()+2500;
        request("hosting.leaderStatus",context.parameters(),data->{if(!context.active()){state=new JsonObject();layout();return;}state=data;if(selected()==null)selectedUuid="";if(!bool(data,"authorized"))message=first(data,"message","error");layout();});
    }
    @Override void onRequestError(){operation=false;nextPoll=System.currentTimeMillis()+5000;layout();}
    @Override public void tick(){
        if(!context.active()){if(!state.isEmpty()){state=new JsonObject();layout();}message="Host controls require an active connection to your running world.";return;}
        if(System.currentTimeMillis()>=nextPoll)poll();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);header(g,together(text(state,"name"),context.route().equals("java")?"Direct Java":"ViaBedrock test connection"),false);
        String access=available()?together(text(self(),"name"),bool(self(),"op")?"Operator level "+permission():"Player · restore OP to use game controls"):loading?"Checking host permissions…":"Host controls unavailable";
        g.text(font,font.plainSubstrByWidth(access,contentWidth-16),left+8,72,available()?GREEN:AMBER);
        if(tab==1){g.text(font,"Time",left+2,81,MUTED);g.text(font,"Weather",left+2,113,MUTED);g.text(font,"Difficulty",left+2,145,MUTED);}
        else{
            if(entries().isEmpty())empty(g,tab==2?"No banned players":"No connected players",tab==2?"Players banned from this world appear here.":"The server refreshes this list automatically.",91);
            pageText(g,page,entries().size(),perPage,height-109);
            if(tab==2&&selected()!=null)g.text(font,font.plainSubstrByWidth("Selected: "+text(selected(),"name"),contentWidth-16),left+8,height-79,MUTED);
        }
        if(!result.isBlank()&&message.isBlank())g.centeredText(font,font.plainSubstrByWidth(result,Math.max(30,contentWidth-210)),width/2,height-20,GREEN);
        if(!message.isBlank()){g.centeredText(font,font.plainSubstrByWidth(message,Math.max(30,contentWidth-210)),width/2,height-20,AMBER);if(my>=height-28)g.setTooltipForNextFrame(font,Component.literal(message),mx,my);}
    }
}
