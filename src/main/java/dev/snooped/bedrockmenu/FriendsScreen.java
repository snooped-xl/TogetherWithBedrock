// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class FriendsScreen extends MenuScreen {
    private JsonObject data=new JsonObject();private JsonArray rows=new JsonArray();private int section,page,selected=-1,perPage=1;private String query="",requestedSelection="";private long nextPoll;private Button inviteButton;
    FriendsScreen(Screen parent){this(parent,0,"");}
    FriendsScreen(Screen parent,int section,String xuid){super(parent,"Friends");this.section=Math.clamp(section,0,2);requestedSelection=xuid;}
    @Override protected void init(){layout();refresh();}
    private void layout(){clearWidgets();inviteButton=null;chrome(4);int thirds=(contentWidth-8)/3;String[] labels={"Friends","Requests","Find people"};for(int i=0;i<3;i++){final int n=i;button(labels[i],left+i*(thirds+4),72,thirds,()->{++requestRevision;loading=false;section=n;page=0;selected=-1;setRows();layout();if(section!=2)refresh();}).active=i!=section;}
        int start=section==2?124:98;if(section==2){field("Xbox gamertag",query,left,98,contentWidth-78,v->query=v);button("Search",left+contentWidth-74,98,74,this::search).active=!loading;}
        perPage=Math.max(1,(height-start-83)/46);page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/perPage));
        for(int i=page*perPage;i<Math.min(rows.size(),(page+1)*perPage);i++){final int index=i;JsonObject person=rows.get(i).getAsJsonObject();String badge=section==1?(bool(person,"incomingRequest")?"Received":"Sent"):bool(person,"isFriend")?"Friend":first(person,"status");row(first(person,"name","gamertag"),first(person,"presence","status"),badge,Icon.PLAYER,start+(i-page*perPage)*46,selected==i,true,()->{selected=index;layout();});}
        pageButtons(page,rows.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-73);
        JsonObject person=selectedRow();int w=(contentWidth-8)/3;int y=height-51;
        if(section==0){inviteButton=button("Invite to hosted world",left,y,w,()->action("inviteFriend"));inviteButton.active=person!=null&&!loading&&bool(child(INSTANCE.status,"broadcast"),"ready");inviteButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Share a hosted world on Xbox before inviting friends.")));button("Remove friend",left+w+4,y,w,()->confirmRemove()).active=person!=null&&!loading;button("Joinable worlds",left+2*(w+4),y,w,()->openTab(parent,1));}
        else if(section==1){boolean incoming=person!=null&&bool(person,"incomingRequest");button(incoming?"Accept":"Cancel request",left,y,w,()->action(incoming?"friendAccept":"friendCancel")).active=person!=null&&!loading;button("Decline",left+w+4,y,w,()->action("friendDecline")).active=incoming&&!loading;button("Refresh",left+2*(w+4),y,w,this::refresh).active=!loading;}
        else {button("Send friend request",left,y,(contentWidth-4)/2,()->action("friendRequest")).active=person!=null&&!bool(person,"isFriend")&&(!person.has("canBeFriended")||bool(person,"canBeFriended"))&&!loading;button("Joinable worlds",left+(contentWidth-4)/2+4,y,(contentWidth-4)/2,()->openTab(parent,1));}
    }
    private JsonObject selectedRow(){return selected>=0&&selected<rows.size()?rows.get(selected).getAsJsonObject():null;}
    private void setRows(){if(section==0)rows=array(data,"friends");else if(section==1){rows=new JsonArray();for(JsonElement e:array(data,"incoming")){JsonObject row=e.getAsJsonObject().deepCopy();row.addProperty("incomingRequest",true);rows.add(row);}for(JsonElement e:array(data,"outgoing")){JsonObject row=e.getAsJsonObject().deepCopy();row.addProperty("incomingRequest",false);rows.add(row);}}else rows=new JsonArray();selected=-1;}
    private void refresh(){if(loading)return;nextPoll=System.currentTimeMillis()+20000;String id=requestedSelection.isBlank()?(selectedRow()==null?"":text(selectedRow(),"xuid")):requestedSelection;request("friends",new JsonObject(),result->{
        String account=text(child(INSTANCE.status,"auth"),"accountXuid");
        if(!account.equals(text(result,"accountXuid"))){data=new JsonObject();rows=new JsonArray();selected=-1;message="Your account changed. Refresh Friends.";nextPoll=System.currentTimeMillis()+3000;layout();return;}
        if(!INSTANCE.observeFriendRequests(result)){nextPoll=System.currentTimeMillis()+3000;layout();return;}
        data=result;
        if(section!=2){setRows();for(int i=0;i<rows.size();i++)if(id.equals(text(rows.get(i).getAsJsonObject(),"xuid")))selected=i;if(!requestedSelection.isBlank()){if(selected>=0)page=selected/perPage;requestedSelection="";}}
        if(section==1&&minecraft.gui.screen()==this)INSTANCE.friendRequestsViewed();layout();
    });}
    private void search(){if(loading||query.isBlank())return;request("friendSearch",object("query",query.strip()),result->{rows=array(result,"entries");selected=-1;page=0;layout();});layout();}
    private void action(String method){JsonObject p=selectedRow();if(p==null||loading)return;String name=first(p,"name","gamertag");request(method,object("xuid",text(p,"xuid"),"name",name),result->{message=switch(method){case "friendRequest"->"Friend request sent to "+name+".";case "inviteFriend"->"World invitation sent to "+name+".";case "friendAccept"->"Friend request accepted.";default->"Updated.";};if(section==2){p.addProperty("isFriend",method.equals("friendAccept"));p.addProperty("status","Request sent");p.addProperty("canBeFriended",false);layout();}else refresh();});layout();}
    private void confirmRemove(){JsonObject p=selectedRow();if(p==null)return;minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)request("friendRemove",object("xuid",text(p,"xuid")),r->refresh());},Component.literal("Remove friend?"),Component.literal(first(p,"name","gamertag"))));}
    @Override void onRequestError(){layout();}
    @Override public void tick(){if(inviteButton!=null)inviteButton.active=selectedRow()!=null&&!loading&&bool(child(INSTANCE.status,"broadcast"),"ready");if(section!=2&&!loading&&System.currentTimeMillis()>nextPoll)refresh();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,together(accountName(),"Your Xbox friends, together in Java."),true);if(rows.isEmpty())empty(g,loading?"Loading…":section==2?"Find your friends":"All caught up",section==2?"Search an Xbox gamertag to send a friend request.":section==1?"Incoming and outgoing requests appear here.":"Friends appear here after you sign in. Use Find people to add someone.",section==2?151:118);pageText(g,page,rows.size(),perPage,height-73);status(g,mx,my);}
}
