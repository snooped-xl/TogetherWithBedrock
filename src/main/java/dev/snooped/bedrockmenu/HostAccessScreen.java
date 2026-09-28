// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class HostAccessScreen extends MenuScreen {
    private final JsonObject world;
    private JsonObject access=new JsonObject();
    private JsonArray guests=new JsonArray();
    private int page,selected=-1,perPage=1;
    private long nextPoll;
    HostAccessScreen(Screen parent,JsonObject world){super(parent,"World access");this.world=world;}
    private JsonObject params(){return object("id",text(world,"id"),"ownerJavaUuid",minecraft.getUser().getProfileId().toString());}
    private boolean privateWorld(){return "invite-only".equals(text(access,"visibility"));}
    @Override protected void init(){layout();refresh();}
    private void layout(){
        clearWidgets();dimensions();int half=(contentWidth-4)/2;
        boolean editable=bool(access,"editable")&&!loading;
        button(privateWorld()?"Public":"[ Public ]",left,48,half,()->setMode("public")).active=editable&&privateWorld();
        button(privateWorld()?"[ Invite-only ]":"Invite-only",left+half+4,48,half,()->setMode("invite-only")).active=editable&&!privateWorld();
        perPage=Math.max(1,(height-201)/46);page=Math.clamp(page,0,Math.max(0,(guests.size()-1)/perPage));
        for(int i=page*perPage;i<Math.min(guests.size(),(page+1)*perPage);i++){
            final int index=i;JsonObject guest=guests.get(i).getAsJsonObject();
            row(text(guest,"name"),"Can join this world until removed","Invited",Icon.PLAYER,119+(i-page*perPage)*46,selected==i,true,()->{selected=index;layout();});
        }
        pageButtons(page,guests.size(),perPage,()->{page--;layout();},()->{page++;layout();},height-76);
        button("Invite friends",left,height-51,half,()->minecraft.gui.setScreen(new FriendsScreen(this))).active=!loading&&text(world,"id").equals(text(child(INSTANCE.status,"hosting"),"worldId"))&&bool(child(INSTANCE.status,"broadcast"),"ready");
        button("Remove access",left+half+4,height-51,half,this::remove).active=editable&&selected>=0&&selected<guests.size();
        button("Back",left,height-26,70,this::onClose);button("Refresh",left+contentWidth-80,height-26,80,this::refresh).active=!loading;
    }
    private void update(JsonObject result){
        String chosen=selected>=0&&selected<guests.size()?text(guests.get(selected).getAsJsonObject(),"xuid"):"";
        access=result;guests=array(result,"guests");selected=-1;
        for(int i=0;i<guests.size();i++)if(chosen.equals(text(guests.get(i).getAsJsonObject(),"xuid")))selected=i;
        world.addProperty("visibility",text(result,"visibility"));layout();
    }
    private void setMode(String mode){
        Runnable apply=()->{JsonObject p=params();p.addProperty("visibility",mode);request("hosting.setAccess",p,this::update);layout();};
        if(mode.equals("public"))minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)apply.run();},Component.literal("Make this world public?"),Component.literal("Anyone with its Java or Bedrock address can join while it is running. Xbox friends can also join when sharing is on.")));
        else apply.run();
    }
    private void remove(){
        if(selected<0||selected>=guests.size())return;JsonObject guest=guests.get(selected).getAsJsonObject();
        minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes){JsonObject p=params();p.addProperty("xuid",text(guest,"xuid"));request("hosting.revokeGuest",p,this::update);layout();}},Component.literal("Remove "+text(guest,"name")+"?"),Component.literal("They will need a new invitation to join an invite-only world. Any OP bypass is removed for private hosting.")));
    }
    private void refresh(){if(loading)return;nextPoll=System.currentTimeMillis()+5000;request("hosting.access",params(),this::update);}
    @Override void onRequestError(){layout();}
    @Override public void tick(){if(!loading&&System.currentTimeMillis()>nextPoll)refresh();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);header(g,text(world,"name"),false);
        paragraph(g,privateWorld()?"Only you and invited players can join, including through a shared address.":"Anyone with the address can join. Xbox friends can join when sharing is on.",75,MUTED);
        g.text(font,bool(access,"editable")?"Invited players":"Stop this world to change access or remove players.",left+8,105,bool(access,"editable")?INK:AMBER);
        if(guests.isEmpty())g.text(font,loading?"Loading…":"No invited players yet.",left+10,134,MUTED);
        pageText(g,page,guests.size(),perPage,height-76);status(g,mx,my);
    }
}
