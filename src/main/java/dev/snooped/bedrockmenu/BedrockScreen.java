// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

public final class BedrockScreen extends MenuScreen {
    private static final String[] METHODS={"servers","worlds","invites","realms"};
    private final int tab;private int page,selected=-1,perPage=1;private long nextRefresh;
    private JsonArray entries=new JsonArray();private boolean discoveringWorlds,leavingRealm,skipNextRefresh;private Button viaBedrock,leaveRealm;private final List<Button> actions=new ArrayList<>(),realmTools=new ArrayList<>();
    public BedrockScreen(Screen parent){this(parent,0);}
    public BedrockScreen(Screen parent,int tab){super(parent,"Bedrock");this.tab=Math.clamp(tab,0,3);}
    @Override protected void init(){layout();if(skipNextRefresh)skipNextRefresh=false;else refresh();}
    private void layout(){
        clearWidgets();actions.clear();realmTools.clear();viaBedrock=null;leaveRealm=null;chrome(tab);perPage=Math.max(1,(height-152)/46);page=Math.clamp(page,0,Math.max(0,(entries.size()-1)/perPage));
        Icon icon=switch(tab){case 0->Icon.SERVER;case 1->Icon.WORLD;case 2->Icon.INVITE;default->Icon.REALM;};
        for(int i=page*perPage;i<Math.min(entries.size(),(page+1)*perPage);i++){final int index=i;JsonObject entry=entries.get(i).getAsJsonObject();Row widget=row(first(entry,"name","host"),details(entry),badge(entry),icon,75+(i-page*perPage)*46,selected==i,available(entry),()->{selected=index;layout();}).activateWith(()->{selected=index;joinSelected();});if(selected==i)setInitialFocus(widget);}
        pageButtons(page,entries.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-77);
        int slots=tab==0||tab==3?4:3,w=(contentWidth-(slots-1)*4)/slots;int y=height-51;
        Button join=button("Join",left,y,w,this::joinSelected);actions.add(join);if(selectedRow()!=null&&bool(selectedRow(),"localHosted"))join.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Connect directly to your hosted Java server.")));
        if(tab==3&&selectedRow()!=null&&bool(selectedRow(),"full"))join.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("This Realm is full. You can still try joining; auto-reconnect can retry until a slot opens.")));
        if(tab==0){button("Add server",left+w+4,y,w,()->minecraft.gui.setScreen(new EditServerScreen(this,null)));actions.add(button("Edit",left+2*(w+4),y,w,()->{if(selectedRow()!=null)minecraft.gui.setScreen(new EditServerScreen(this,selectedRow()));}));actions.add(button("Remove",left+3*(w+4),y,w,()->{JsonObject row=selectedRow();if(row!=null)minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)remove(row);},Component.literal("Remove server?"),Component.literal(text(row,"name"))));}));}
        else if(tab==2){actions.add(button("Dismiss",left+w+4,y,w,()->{JsonObject r=selectedRow();if(r!=null)request("dismissInvite",object("id",text(r,"id")),data->refresh());}));button("Refresh",left+2*(w+4),y,w,this::refresh);}
        else if(tab==3){
            realmTools.add(button("Use invite code",left+w+4,y,w,()->minecraft.gui.setScreen(new RealmCodeScreen(this))));
            leaveRealm=button("Leave Realm",left+2*(w+4),y,w,this::confirmLeaveRealm);actions.add(leaveRealm);
            JsonObject realm=selectedRow();String hint=realm==null?"Select a Realm to leave.":bool(realm,"owned")?"You own this Realm. Leaving is only available to members; this does not cancel a subscription.":canLeaveRealm(realm)?"Leave this Realm. You will need a new invitation or an invite code to rejoin.":"Refresh to verify Realm ownership before leaving.";
            leaveRealm.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(hint)));
            realmTools.add(button("Refresh",left+3*(w+4),y,w,this::refresh));
        }
        else{if(selectedRow()!=null&&bool(selectedRow(),"localHosted")){viaBedrock=button("Join Via ViaBedrock",left+w+4,y,w,()->{JsonObject row=selectedRow();if(row!=null&&available(row))INSTANCE.join("hosted",text(row,"id"),this);});viaBedrock.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Test both translators using your Bedrock account.")));actions.add(viaBedrock);}else button("Friends",left+w+4,y,w,()->openTab(parent,4));button("Refresh",left+2*(w+4),y,w,this::refresh);}
        updateActions();
    }
    void refresh(){if(loading)return;nextRefresh=System.currentTimeMillis()+(tab==0?4000:15000);String id=selectedRow()==null?"":text(selectedRow(),"id");request(METHODS[tab],new JsonObject(),data->{entries=array(data,"entries");discoveringWorlds=bool(data,"refreshing");if(tab==1)nextRefresh=System.currentTimeMillis()+(discoveringWorlds?1500:3000);message=text(data,"warning");selected=-1;for(int i=0;i<entries.size();i++)if(text(entries.get(i).getAsJsonObject(),"id").equals(id))selected=i;layout();});updateActions();}
    @Override void onRequestError(){if(leavingRealm){leavingRealm=false;nextRefresh=System.currentTimeMillis()+30000;}layout();}
    private JsonObject selectedRow(){return selected>=0&&selected<entries.size()?entries.get(selected).getAsJsonObject():null;}
    private boolean available(JsonObject entry){return (!entry.has("available")||bool(entry,"available"))&&!(tab==2&&InviteBanners.expired(entry));}
    private void updateActions(){
        JsonObject selected=selectedRow();for(Button action:actions)action.active=!loading&&!INSTANCE.busy()&&selected!=null;
        if(!actions.isEmpty()&&selected!=null)actions.getFirst().active&=available(selected);if(viaBedrock!=null&&selected!=null)viaBedrock.active&=available(selected);
        if(leaveRealm!=null)leaveRealm.active&=canLeaveRealm(selected);
        for(Button tool:realmTools)tool.active=!leavingRealm;
    }
    private boolean canLeaveRealm(JsonObject realm){return realm!=null&&!bool(realm,"owned")&&bool(realm,"canLeave");}
    private void joinSelected(){JsonObject entry=selectedRow();if(loading||INSTANCE.busy()||entry==null||!available(entry))return;if(bool(entry,"localHosted"))INSTANCE.reconnectHosted(text(entry,"id"),this);else INSTANCE.join(tab==0?"server":tab==3?"realm":"world",text(entry,"id"),this);}
    private void confirmLeaveRealm(){
        JsonObject realm=selectedRow();if(loading||INSTANCE.busy()||!canLeaveRealm(realm))return;
        final String id=text(realm,"id"),name=first(realm,"name","id");
        minecraft.gui.setScreen(new ConfirmScreen(yes->{
            // Returning from confirmation must not start a list refresh that races the mutation.
            skipNextRefresh=true;minecraft.gui.setScreen(this);
            if(yes)leaveRealm(id,name);
        },Component.literal("Leave "+name+"?"),Component.literal("You will lose access to this Realm. To rejoin, you will need a new invitation from its owner or a valid invite code.")));
    }
    private void leaveRealm(String id,String name){
        if(loading||leavingRealm)return;
        leavingRealm=true;message="Leaving "+name+"…";
        request("leaveRealm",object("id",id),data->{
            leavingRealm=false;nextRefresh=System.currentTimeMillis()+15000;
            if(!bool(data,"left")||!id.equals(text(data,"id"))){message="Leaving could not be confirmed. Refresh the Realm list to check.";layout();return;}
            JsonArray remaining=new JsonArray();for(JsonElement entry:entries)if(!id.equals(text(entry.getAsJsonObject(),"id")))remaining.add(entry);
            entries=remaining;selected=-1;message="Left "+name+".";layout();
        });
        updateActions();
    }
    private String badge(JsonObject r){if(tab==2&&InviteBanners.expired(r))return "Expired";if(!available(r))return "Unavailable";int count=number(r,"players",-1),capacity=number(r,"capacity",-1);if(count>=0)return count+(capacity>0?" / "+capacity:"")+" players"+(tab==3&&bool(r,"full")?" · Full":"");return tab==2?"Invited":bool(r,"online")?"Online":tab==3?"?"+(capacity>0?" / "+capacity:"")+" players":"";}
    private String details(JsonObject r){
        if(!text(r,"unavailable").isBlank())return text(r,"unavailable");
        String host=tab==0?text(r,"host")+(r.has("port")?":"+text(r,"port"):""):first(r,"owner","host");
        String version=text(r,"version");if(version.isBlank()&&r.has("protocol"))version=number(r,"protocol",2193)==2193?"26.50 / 26.51":"26.40 / 26.45";
        int ping=number(r,"ping",number(r,"pingMs",-1));return together(host,first(r,"description","motd"),version,ping>=0?ping+" ms":"",bool(r,"invited")&&tab==1?"Invited":"");
    }
    @Override public void tick(){updateActions();if(!loading&&System.currentTimeMillis()>nextRefresh)refresh();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,together(accountName().isBlank()?"One account. Your Bedrock community.":accountName(),TABS[tab]+" · "+entries.size()),true);
        if(entries.isEmpty())empty(g,(loading||discoveringWorlds)?"Finding your "+TABS[tab].toLowerCase()+"…":"No "+TABS[tab].toLowerCase()+" yet",loading?"":switch(tab){case 0->"Save an address with Add server. Player counts and connection details appear here when the server responds.";case 1->"Your running hosted world and friends' joinable worlds appear here. Start a world from Hosting or ask a friend to invite you.";case 2->"World invitations appear here and on the main menu. Expired invitations stay visible until dismissed.";default->"Your Bedrock Realms appear here. You can also join with a Realm invite link.";},95);
        pageText(g,page,entries.size(),perPage,height-77);if((loading||discoveringWorlds)&&entries.size()>0)g.centeredText(font,leavingRealm?"Leaving Realm…":discoveringWorlds?"Finding friends’ worlds…":"Refreshing…",width/2,height-70,MUTED);status(g,mx,my);
    }
    void saveServer(JsonObject server,java.util.function.Consumer<String> errorHandler){INSTANCE.companion.request("servers").thenCompose(data->{JsonArray rows=array(data,"entries").deepCopy();boolean replaced=false;for(int i=0;i<rows.size();i++)if(text(rows.get(i).getAsJsonObject(),"id").equals(text(server,"id"))){rows.set(i,server);replaced=true;break;}if(!replaced)rows.add(server);JsonObject body=new JsonObject();body.add("entries",rows);return INSTANCE.companion.request("saveServers",body);}).whenComplete((data,error)->minecraft.execute(()->{if(error!=null)errorHandler.accept(Companion.error(error));else minecraft.gui.setScreen(this);}));}
    private void remove(JsonObject row){JsonArray rows=new JsonArray();for(JsonElement e:entries)if(!text(e.getAsJsonObject(),"id").equals(text(row,"id")))rows.add(e);JsonObject body=new JsonObject();body.add("entries",rows);request("saveServers",body,data->{selected=-1;refresh();});}
}
