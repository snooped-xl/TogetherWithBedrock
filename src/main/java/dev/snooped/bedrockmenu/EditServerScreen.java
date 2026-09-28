// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import java.util.UUID;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class EditServerScreen extends MenuScreen {
    private final BedrockScreen owner;private final String id;private String name="",host="",port="19132";private int protocol=2193;private Button save;
    EditServerScreen(BedrockScreen parent,JsonObject entry){super(parent,entry==null?"Add Bedrock server":"Edit Bedrock server");owner=parent;id=entry==null?UUID.randomUUID().toString():text(entry,"id");if(entry!=null){name=text(entry,"name");host=text(entry,"host");port=text(entry,"port");protocol=number(entry,"protocol",2193);}}
    @Override protected void init(){dimensions();int x=left+8,w=contentWidth-16;EditBox first=field("Server name",name,x,59,w,v->name=v);field("Address",host,x,101,w-80,v->host=v);field("Port",port,x+w-72,101,72,v->port=v);button(version(),x,143,w,()->{protocol=protocol==2193?2169:2193;rebuildWidgets();});int half=(w-8)/2;save=button("Save server",x,height-51,half,this::save);button("Cancel",x+half+8,height-51,half,this::onClose);setInitialFocus(first);}
    private String version(){return "Bedrock "+(protocol==2193?"26.50 / 26.51":"26.40 / 26.45");}
    private void save(){int number;try{number=Integer.parseInt(port);if(number<1||number>65535)throw new NumberFormatException();}catch(NumberFormatException e){message="Enter a port between 1 and 65535.";return;}if(name.isBlank()||host.isBlank()){message="Enter a server name and address.";return;}JsonObject row=object("id",id,"name",name.strip(),"host",host.strip());row.addProperty("port",number);row.addProperty("protocol",protocol);save.active=false;owner.saveServer(row,error->{message=error;save.active=true;});}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"A saved connection, with its own map and settings cache.",false);int x=left+8,w=contentWidth-16;g.text(font,"Server name",x,47,MUTED);g.text(font,"Address",x,89,MUTED);g.text(font,"Port",x+w-72,89,MUTED);g.text(font,"Server version",x,131,MUTED);status(g,mx,my);}
}
