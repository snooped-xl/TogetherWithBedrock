// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class TunnelScreen extends MenuScreen {
    private JsonObject state=new JsonObject();private String claim="";private long nextPoll;
    TunnelScreen(Screen parent){super(parent,"Public connection");}
    @Override protected void init(){layout();poll();}
    private boolean hosting(){return bool(state,"running")||!java.util.Set.of("","stopped","error").contains(text(state,"state"));}
    private void layout(){
        clearWidgets();dimensions();JsonObject playit=child(state,"playit");claim=text(playit,"claimUrl");int y=height-104,w=(contentWidth-4)/2;
        boolean linked=bool(playit,"configured"),pending=!claim.isBlank();
        button(pending?"Check account approval":linked?"Refresh connection":"Connect playit.gg",left,y,w,()->action(pending||linked?"hosting.playitRefresh":"hosting.playitSetup")).active=!loading&&(pending||linked||!hosting());
        button("Reconnect playit",left+w+4,y,w,()->action("hosting.playitReconnect")).active=!loading&&!hosting();
        button("Open account setup",left,y+25,w,this::openClaim).active=pending;
        button("Copy setup link",left+w+4,y+25,w,()->{minecraft.keyboardHandler.setClipboard(claim);message="Setup link copied.";}).active=pending;
        button("Copy Java address",left,y+50,w,()->copy("publicJava")).active=!text(state,"publicJava").isBlank();
        button("Copy Bedrock address",left+w+4,y+50,w,()->copy("publicBedrock")).active=!text(state,"publicBedrock").isBlank();
        button("Back",left,height-26,70,this::onClose);
        button("Disconnect playit",left+contentWidth-130,height-26,130,()->minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)action("hosting.playitDisconnect");},Component.literal("Disconnect playit?"),Component.literal("Public Java and Bedrock connections will stop working until you reconnect.")))).active=(bool(playit,"hasAccount")||pending)&&!loading&&!hosting();
    }
    private void action(String method){request(method,new JsonObject(),r->{state.add("playit",r);layout();nextPoll=0;});layout();}
    private void openClaim(){try{java.net.URI url=java.net.URI.create(claim);String host=url.getHost();if("https".equals(url.getScheme())&&host!=null&&(host.equals("playit.gg")||host.endsWith(".playit.gg")))Util.getPlatform().openUri(url);else message="The service returned an invalid playit setup address.";}catch(Exception e){message="Invalid playit setup address.";}}
    private void copy(String key){minecraft.keyboardHandler.setClipboard(text(state,key));message="Public address copied.";}
    private void poll(){if(loading)return;nextPoll=System.currentTimeMillis()+3000;request("hosting.status",new JsonObject(),r->{state=r;layout();});}
    @Override void onRequestError(){layout();}
    @Override public void tick(){if(System.currentTimeMillis()>nextPoll)poll();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);header(g,"One account link for your Java and Bedrock addresses.",false);
        JsonObject playit=child(state,"playit");String error=first(playit,"error","message");
        g.text(font,font.plainSubstrByWidth("playit: "+first(playit,"status","state"),contentWidth-16),left+8,50,bool(playit,"running")?GREEN:bool(playit,"requiresRelink")?AMBER:MUTED);
        g.text(font,font.plainSubstrByWidth("Java: "+(text(state,"publicJava").isBlank()?"Not public yet":text(state,"publicJava")),contentWidth-16),left+8,67,MUTED);
        g.text(font,font.plainSubstrByWidth("Bedrock: "+(text(state,"publicBedrock").isBlank()?"Not public yet":text(state,"publicBedrock")),contentWidth-16),left+8,81,MUTED);
        String hint=!error.isBlank()?error:!claim.isBlank()?"Open account setup, approve the link, then return here.":hosting()?"Stop the hosted world before reconnecting your account.":"Connect once, approve in your browser, then start a world.";
        int lines=Math.max(1,(height-226)/font.lineHeight);
        var wrapped=font.split(Component.literal(hint),contentWidth-16);
        for(int i=0;i<Math.min(lines,wrapped.size());i++)g.text(font,wrapped.get(i),left+8,100+i*font.lineHeight,AMBER);
        if(my>=98&&my<height-126)g.setTooltipForNextFrame(font,Component.literal(hint),mx,my);
        String version=first(state,"serviceVersion");if(version.isBlank())version=first(INSTANCE.status,"version");
        g.text(font,font.plainSubstrByWidth("Companion: "+(version.isBlank()?"Checking…":version),contentWidth-16),left+8,height-119,MUTED);
        status(g,mx,my);
    }
}
