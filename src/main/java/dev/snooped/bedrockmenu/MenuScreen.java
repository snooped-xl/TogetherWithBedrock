// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.function.Consumer;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

/** Shared, responsive vanilla menu chrome. No networking on Minecraft's render thread. */
abstract class MenuScreen extends Screen {
    static final int INK=0xFFFFFFFF,MUTED=0xFFACB7AD,GREEN=0xFF9DDD89,AMBER=0xFFFFD28B;
    static final String[] TABS={"Servers","Worlds","Invites","Realms","Friends","Hosting"};
    // Main-menu screens have no world-bound item components in 26.2. GUI sprites
    // remain available before joining, after disconnecting, and during resize.
    enum Icon {
        SERVER("icon/link"), WORLD("icon/new_realm"), INVITE("icon/invite"),
        REALM("icon/trial_available"), PLAYER("pause_menu/social_interactions"), MOD("icon/info");
        final Identifier sprite;
        Icon(String path){sprite=Identifier.withDefaultNamespace(path);}
    }
    final Screen parent; int left,contentWidth; String message=""; boolean loading; int requestRevision;
    MenuScreen(Screen parent,String title){super(Component.literal(title));this.parent=parent;}
    void dimensions(){left=Math.max(10,(width-650)/2);contentWidth=width-left*2;}
    Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    EditBox field(String label,String value,int x,int y,int w,Consumer<String> changed){EditBox b=new EditBox(font,x,y,w,20,Component.literal(label));b.setMaxLength(256);b.setValue(value);b.setResponder(changed);return addRenderableWidget(b);}
    void chrome(int tab){
        dimensions();int tw=(contentWidth-20)/6;
        for(int i=0;i<6;i++){final int n=i;Button b=button(TABS[i],left+i*(tw+4),45,i==5?contentWidth-5*(tw+4):tw,()->openTab(parent,n));b.active=i!=tab;}
        String account=accountName();button(account.isBlank()?"Sign in":"Account",left+contentWidth-76,12,76,()->minecraft.gui.setScreen(new AccountScreen(this)));
        button("Back",left,height-26,70,this::onClose);
        button("Proxy",left+contentWidth-70,height-26,70,()->minecraft.gui.setScreen(new ProxyScreen(this)));
    }
    static void openTab(Screen parent,int tab){net.minecraft.client.Minecraft.getInstance().gui.setScreen(tab==4?new FriendsScreen(parent):tab==5?new HostingScreen(parent):new BedrockScreen(parent,tab));}
    static String accountName(){JsonObject state=INSTANCE.status;String name=text(state,"account");if(state.has("auth")&&state.get("auth").isJsonObject()){JsonObject auth=state.getAsJsonObject("auth");name=bool(auth,"signedIn")?text(auth,"name"):"";}return name;}
    void header(GuiGraphicsExtractor g,String subtitle,boolean root){
        int x=root?left:left+4;g.text(font,root?"TogetherWithBedrock":title.getString(),x,13,INK);
        g.text(font,font.plainSubstrByWidth(subtitle,Math.max(30,contentWidth-(root?88:8))),x,28,MUTED);
        g.fill(left,39,left+contentWidth,40,0xFF6D815F);
    }
    void status(GuiGraphicsExtractor g,int mx,int my){if(!message.isBlank()){g.centeredText(font,font.plainSubstrByWidth(message,Math.max(20,contentWidth-150)),width/2,height-20,AMBER);if(my>=height-28)g.setTooltipForNextFrame(font,Component.literal(message),mx,my);}}
    void paragraph(GuiGraphicsExtractor g,String s,int y,int color){g.textWithWordWrap(font,Component.literal(s),left+10,y,contentWidth-20,color);}
    void empty(GuiGraphicsExtractor g,String heading,String detail,int y){g.centeredText(font,heading,width/2,y,INK);g.textWithWordWrap(font,Component.literal(detail),left+Math.min(30,contentWidth/10),y+20,contentWidth-Math.min(60,contentWidth/5),MUTED);}
    void request(String method,JsonObject params,Consumer<JsonObject> done){loading=true;final int serial=++requestRevision;INSTANCE.companion.request(method,params).whenComplete((data,error)->minecraft.execute(()->{if(serial!=requestRevision)return;loading=false;if(error!=null){message=Companion.error(error);onRequestError();}else{message="";done.accept(data);}}));}
    void onRequestError(){}
    static boolean bool(JsonObject j,String key){try{return j.get(key).getAsBoolean();}catch(Exception e){return false;}}
    static int number(JsonObject j,String key,int fallback){try{return j.get(key).getAsInt();}catch(Exception e){return fallback;}}
    static JsonObject child(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonObject()?j.getAsJsonObject(key):new JsonObject();}
    static String first(JsonObject j,String... keys){for(String key:keys){String value=text(j,key);if(!value.isBlank())return value;}return "";}
    static String together(String... parts){return java.util.Arrays.stream(parts).filter(s->s!=null&&!s.isBlank()).collect(java.util.stream.Collectors.joining("  ·  "));}
    void pageButtons(int page,int total,int perPage,Runnable previous,Runnable next,int y){if(total<=perPage)return;button("‹",left,y,24,previous).active=page>0;button("›",left+contentWidth-24,y,24,next).active=(page+1)*perPage<total;}
    void pageText(GuiGraphicsExtractor g,int page,int total,int perPage,int y){if(total>perPage)g.centeredText(font,(page+1)+" / "+((total+perPage-1)/perPage),width/2,y+6,MUTED);}
    Row row(String title,String subtitle,String badge,Icon icon,int y,boolean selected,boolean available,Runnable click){return addRenderableWidget(new Row(left,y,contentWidth,42,title,subtitle,badge,icon,selected,available,click));}
    @Override public void onClose(){++requestRevision;minecraft.gui.setScreen(parent);}
    final class Row extends AbstractButton {
        private final String title,subtitle,badge;private final Icon icon;private final boolean selected,available;private final Runnable click;private Runnable activate;
        Row(int x,int y,int w,int h,String title,String subtitle,String badge,Icon icon,boolean selected,boolean available,Runnable click){super(x,y,w,h,Component.literal(together(title,subtitle,badge)));this.title=title;this.subtitle=subtitle;this.badge=badge;this.icon=icon;this.selected=selected;this.available=available;this.click=click;setTooltip(Tooltip.create(Component.literal(together(title,subtitle,badge))));}
        Row activateWith(Runnable action){activate=action;return this;}
        @Override public void onPress(InputWithModifiers input){if(selected&&activate!=null)activate.run();else click.run();}
        @Override public void onClick(MouseButtonEvent event,boolean doubleClick){if(doubleClick&&activate!=null)activate.run();else click.run();}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            int x=getX(),y=getY(),w=getWidth();g.fill(x,y,x+w,y+height,selected?0xB0384433:isHoveredOrFocused()?0xB0333732:0x900D100D);
            g.outline(x,y,w,height,selected?0xFF9DDD89:isHoveredOrFocused()?0xFFD0D0D0:0xFF454B41);
            if(selected)g.fill(x+1,y+1,x+3,y+height-1,GREEN);g.blitSprite(RenderPipelines.GUI_TEXTURED,icon.sprite,x+9,y+12,16,16);
            int badgeW=Math.min(font.width(badge),Math.max(0,w/3));int tx=x+34;g.text(font,font.plainSubstrByWidth(title,Math.max(20,w-46-badgeW)),tx,y+8,available?INK:MUTED);
            if(!badge.isBlank())g.text(font,font.plainSubstrByWidth(badge,badgeW),x+w-badgeW-9,y+8,available?GREEN:AMBER);
            g.text(font,font.plainSubstrByWidth(subtitle,Math.max(20,w-46)),tx,y+25,MUTED);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
}
