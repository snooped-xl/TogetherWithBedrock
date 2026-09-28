// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class ProxyScreen extends MenuScreen {
    private Button restart;
    ProxyScreen(Screen parent){super(parent,"Connection service");}
    @Override protected void init(){dimensions();restart=button("Restart service / load update",left+8,height-86,contentWidth-16,()->INSTANCE.companion.request("hosting.status").whenComplete((state,error)->minecraft.execute(()->{if(error!=null){message=Companion.error(error);return;}String phase=text(state,"state");if(bool(state,"running")||phase.equals("installing")||phase.equals("starting")||phase.equals("stopping")){message="Stop your hosted world from Hosting before restarting the service.";return;}restart();})));button("Open logs folder",left+8,height-60,contentWidth-16,()->{var path=INSTANCE.companion.profile();if(path!=null)Util.getPlatform().openPath(path);});button("Back",width/2-70,height-30,140,this::onClose);}
    private void restart(){restart.active=false;message="Restarting…";INSTANCE.restart().whenComplete((ignored,error)->minecraft.execute(()->{restart.active=true;message=error==null?"Service reloaded. Minecraft can stay open.":Companion.error(error);}));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"External build "+text(INSTANCE.status,"version"),false);paragraph(g,"Proxy updates load here without restarting Minecraft. Leave your Bedrock server before reloading.",55,MUTED);paragraph(g,text(INSTANCE.status,"invitations"),88,MUTED);status(g,mx,my);}
}
