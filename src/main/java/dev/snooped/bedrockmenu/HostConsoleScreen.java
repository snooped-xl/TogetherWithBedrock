// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

/** Bounded live chat and console; polling and wrapping happen only while this screen is visible. */
final class HostConsoleScreen extends MenuScreen {
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private record Line(FormattedCharSequence text,int color){}
    private final String id;
    private final HostedContext context;
    private final ArrayDeque<JsonObject> entries=new ArrayDeque<>();
    private final ArrayList<Line> lines=new ArrayList<>();
    private EditBox input;
    private Button sendButton,olderButton,newerButton,latestButton;
    private long cursor,generation=-1,nextPoll;
    private int offset;
    private boolean commandMode,chatOnly,canMessage,canCommand;
    private String draft="";

    HostConsoleScreen(Screen parent,String id){this(parent,id,null);}
    HostConsoleScreen(Screen parent,String id,HostedContext context){super(parent,"Server chat & console");this.id=id;this.context=context;}
    @Override protected void init(){layout();poll();}
    @Override public boolean isPauseScreen(){return false;}
    private boolean connectionValid(){return context==null||context.active();}
    private JsonObject parameters(){return context==null?object("id",id):context.parameters();}
    private String route(boolean send){return "hosting."+(context==null?"console":"leaderConsole")+(send?"Send":"");}
    private int visibleLines(){return Math.max(1,(height-153)/font.lineHeight);}
    private void layout(){
        if(input!=null)draft=input.getValue();clearWidgets();dimensions();
        button(chatOnly?"Chat only":"All output",left,47,90,()->{chatOnly=!chatOnly;offset=0;layout();});
        olderButton=button("Older",left+contentWidth-194,47,60,()->{offset+=visibleLines();updateButtons();});
        newerButton=button("Newer",left+contentWidth-130,47,60,()->{offset=Math.max(0,offset-visibleLines());updateButtons();});
        latestButton=button("Latest",left+contentWidth-66,47,66,()->{offset=0;updateButtons();});
        button(commandMode?"Command":"Message",left,height-59,92,()->{commandMode=!commandMode;layout();});
        String savedDraft=draft;input=field(commandMode?"Server command":"Server announcement","",left+96,height-59,contentWidth-166,value->draft=value);input.setMaxLength(commandMode?4096:512);input.setValue(savedDraft);
        input.setHint(Component.literal(commandMode?"/give Player minecraft:stone 64":"Message from the server"));
        sendButton=button("Send",left+contentWidth-66,height-59,66,this::send);
        sendButton.setTooltip(Tooltip.create(Component.literal("Messages appear as [Server]. Commands run with server console privileges.")));
        button("Back",left,height-26,70,this::onClose);wrapLines();updateButtons();setInitialFocus(input);
    }
    private void updateButtons(){
        offset=Math.clamp(offset,0,Math.max(0,lines.size()-visibleLines()));
        olderButton.active=offset<Math.max(0,lines.size()-visibleLines());newerButton.active=latestButton.active=offset>0;
        sendButton.active=!loading&&connectionValid()&&(commandMode?canCommand:canMessage)&&!draft.isBlank();
    }
    private void wrapLines(){
        lines.clear();for(JsonObject entry:entries){
            String kind=text(entry,"kind");if(chatOnly&&!kind.equals("chat")&&!(kind.equals("server")&&text(entry,"text").startsWith("[Server]")))continue;
            String time="";try{time=CLOCK.format(Instant.ofEpochMilli(entry.get("time").getAsLong()));}catch(Exception ignored){}
            String prefix=kind.equals("chat")?"<"+text(entry,"sender")+"> ":kind.equals("command")?"Host › ":"";
            int color=kind.equals("chat")?INK:kind.equals("command")?GREEN:kind.equals("warning")?AMBER:MUTED;
            for(FormattedCharSequence text:font.split(Component.literal(time+" "+prefix+text(entry,"text")),Math.max(20,contentWidth-16)))lines.add(new Line(text,color));
        }
    }
    private void poll(){
        if(loading||!connectionValid())return;nextPoll=System.currentTimeMillis()+1000;
        JsonObject params=parameters();params.addProperty("after",cursor);
        request(route(false),params,data->{
            if(!connectionValid())return;
            long newGeneration=data.get("hostGeneration").getAsLong();
            if(generation!=-1&&newGeneration!=generation){entries.clear();cursor=0;offset=0;generation=newGeneration;nextPoll=0;wrapLines();canMessage=canCommand=false;updateButtons();return;}
            generation=newGeneration;if(bool(data,"reset")){entries.clear();offset=0;}
            int previousLines=lines.size();boolean received=false;
            for(JsonElement item:array(data,"entries")){JsonObject entry=item.getAsJsonObject();if(entries.size()==512)entries.removeFirst();entries.addLast(entry);received=true;}
            cursor=data.get("cursor").getAsLong();canMessage=bool(data,"canMessage");canCommand=bool(data,"canCommand");
            if(received||bool(data,"reset")){wrapLines();if(offset>0)offset+=Math.max(0,lines.size()-previousLines);}
            if(bool(data,"more"))nextPoll=0;updateButtons();
        });updateButtons();
    }
    private void send(){
        if(loading||!connectionValid()||!(commandMode?canCommand:canMessage)||draft.isBlank())return;
        JsonObject params=parameters();params.addProperty("hostGeneration",generation);params.addProperty("kind",commandMode?"command":"message");params.addProperty("text",draft);
        String submitted=draft;request(route(true),params,data->{if(draft.equals(submitted)){draft="";input.setValue("");}nextPoll=0;offset=0;updateButtons();});updateButtons();
    }
    @Override void onRequestError(){nextPoll=System.currentTimeMillis()+3000;updateButtons();}
    @Override public boolean keyPressed(KeyEvent key){if((key.key()==257||key.key()==335)&&input.isFocused()){send();return true;}return super.keyPressed(key);}
    @Override public void tick(){if(minecraft.gui.screen()!=this)return;if(!connectionValid()){canMessage=canCommand=false;message="Reconnect to your hosted world to use its console.";}else if(System.currentTimeMillis()>=nextPoll)poll();updateButtons();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);header(g,"Live player chat, server messages and command output",false);
        g.fill(left,73,left+contentWidth,height-70,0xA00D100D);
        int end=Math.max(0,lines.size()-offset),start=Math.max(0,end-visibleLines());
        for(int i=start;i<end;i++){Line line=lines.get(i);g.text(font,line.text(),left+7,77+(i-start)*font.lineHeight,line.color());}
        if(lines.isEmpty())g.text(font,"Waiting for chat and server output…",left+8,80,MUTED);
        if(message.isBlank())g.text(font,commandMode?"Console commands · /gamemode creative Player":"Announcements are sent to everyone as [Server]",left+96,height-34,MUTED);
        status(g,mx,my);
    }
}
