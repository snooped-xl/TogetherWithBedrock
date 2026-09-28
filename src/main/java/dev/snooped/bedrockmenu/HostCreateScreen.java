// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

/** Creates a separate server world; never exports or modifies an active integrated world. */
final class HostCreateScreen extends MenuScreen {
    private String name="Friends' world",seed="",players="10",memory="3072";private String mode="survival",difficulty="normal";private String visibility="public";private boolean eula;private Button create;
    HostCreateScreen(Screen parent){super(parent,"Host a world");}
    HostCreateScreen(Screen parent,String name,String seed){this(parent);if(!name.isBlank())this.name=name;this.seed=seed;}
    @Override protected void init(){dimensions();int x=left+8,w=contentWidth-16,half=(w-8)/2;field("World name",name,x,58,half,v->name=v);field("Seed (optional)",seed,x+half+8,58,half,v->seed=v);
        int third=(w-8)/3;button("Mode: "+mode,x,88,third,()->{mode=mode.equals("survival")?"creative":"survival";rebuildWidgets();});button("Difficulty: "+difficulty,x+third+4,88,third,()->{difficulty=switch(difficulty){case "peaceful"->"easy";case "easy"->"normal";case "normal"->"hard";default->"peaceful";};rebuildWidgets();});
        button(visibility.equals("public")?"Public":"Invite-only",x+2*(third+4),88,w-2*(third+4),()->{visibility=visibility.equals("public")?"invite-only":"public";rebuildWidgets();}).setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal("Public: anyone with the address can join. Invite-only: only you and invited players.")));
        field("Maximum players",players,x,128,half,v->players=v);field("Server memory (MiB)",memory,x+half+8,128,half,v->memory=v);
        button((eula?"[x] ":"[ ] ")+"I agree to Minecraft's EULA",x,160,w-72,()->{eula=!eula;rebuildWidgets();});button("Read",x+w-68,160,68,()->Util.getPlatform().openUri("https://www.minecraft.net/eula"));
        create=button("Create world",x,height-52,half,this::create);create.active=eula&&!loading;button("Cancel",x+half+8,height-52,half,this::onClose);
    }
    private void create(){if(loading||!eula)return;int p,m;try{p=Integer.parseInt(players);m=Integer.parseInt(memory);if(p<1||p>100||m<1024||m>16384)throw new NumberFormatException();}catch(NumberFormatException e){message="Use 1–100 players and 1024–16384 MiB of server memory.";return;}if(name.isBlank()){message="Give your world a name.";return;}JsonObject params=object("name",name.strip(),"seed",seed.strip(),"gamemode",mode,"difficulty",difficulty,"ownerJavaName",minecraft.getUser().getName(),"ownerJavaUuid",minecraft.getUser().getProfileId().toString(),"visibility",visibility);params.addProperty("maxPlayers",p);params.addProperty("memoryMb",m);params.addProperty("acceptEula",true);message="Creating your dedicated world…";create.active=false;request("hosting.create",params,data->{JsonObject world=data.has("world")?child(data,"world"):data;minecraft.gui.setScreen(new HostManageScreen(parent,world));});}
    @Override void onRequestError(){create.active=eula;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"A separate Fabric server with Geyser. No client mod conflicts.",false);int x=left+8,half=(contentWidth-24)/2;g.text(font,"World name",x,47,MUTED);g.text(font,"Seed (optional)",x+half+8,47,MUTED);g.text(font,"Maximum players",x,116,MUTED);g.text(font,"Server memory (MiB)",x+half+8,116,MUTED);status(g,mx,my);}
}
