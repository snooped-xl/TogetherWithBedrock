// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

final class ConnectingBedrockScreen extends MenuScreen {
    private String error="";private final String progressMessage;
    ConnectingBedrockScreen(Screen parent){this(parent,"Connecting to Bedrock","Starting your Bedrock proxy…");}
    ConnectingBedrockScreen(Screen parent,String title,String progressMessage){super(parent,title);this.progressMessage=progressMessage;}
    @Override protected void init(){dimensions();button(error.isBlank()?"Cancel":"Back",width/2-90,height-40,180,this::onClose);}
    void failed(String message){error=message;rebuildWidgets();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,error.isBlank()?"Preparing a secure connection to your world.":"The connection could not be completed.",false);paragraph(g,error.isBlank()?progressMessage:error,72,error.isBlank()?MUTED:AMBER);if(error.isBlank()){int w=Math.min(180,contentWidth-40),x=(width-w)/2,y=Math.min(125,height-80);g.fill(x,y,x+w,y+3,0xFF404A39);int position=(int)(System.currentTimeMillis()/25%(w-24));g.fill(x+position,y,x+position+24,y+3,GREEN);}}
    @Override public void onClose(){BedrockMenu.INSTANCE.stop();minecraft.gui.setScreen(parent);}
}
