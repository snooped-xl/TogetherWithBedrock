// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class AccountScreen extends MenuScreen {
    private JsonObject auth=new JsonObject();private long nextPoll;private String snapshot="";
    AccountScreen(Screen parent){super(parent,"Bedrock account");}
    @Override protected void init(){layout();poll();}
    private void layout(){clearWidgets();dimensions();int x=left+8,w=contentWidth-16,y=Math.max(145,height-85);boolean signed=bool(auth,"signedIn"),pending=text(auth,"mode").equals("pending")||!text(auth,"deviceCode").isBlank();
        if(signed)button("Sign out",x,y,w,()->minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes)request("authSignOut",new JsonObject(),data->{auth=data;INSTANCE.status.remove("account");INSTANCE.status.add("auth",auth);INSTANCE.invitations=new JsonArray();INSTANCE.banners.clear();layout();poll();});},Component.literal("Sign out of Bedrock?"),Component.literal("Saved worlds and servers stay here. Stop your hosted world before switching accounts."))));
        else if(pending){if(!text(auth,"deviceCode").isBlank()){int half=(w-4)/2;button("Open Microsoft",x,y,half,()->openVerification());button("Copy code",x+half+4,y,half,()->{minecraft.keyboardHandler.setClipboard(text(auth,"deviceCode"));message="Code copied.";});}button("Cancel sign-in",x,y+25,w,()->request("authCancel",new JsonObject(),data->{auth=data;layout();}));}
        else button("Sign in with Microsoft",x,y,w,()->request("authStart",new JsonObject(),data->{auth=data;layout();}));
        button("Back",width/2-70,height-28,140,this::onClose);
    }
    private void openVerification(){String url=text(auth,"verificationUri");if(url.startsWith("https://login.microsoftonline.com/")||url.startsWith("https://microsoft.com/")||url.startsWith("https://www.microsoft.com/"))Util.getPlatform().openUri(url);else message="Use the Microsoft address shown above.";}
    private void poll(){if(loading)return;nextPoll=System.currentTimeMillis()+1500;request("authStatus",new JsonObject(),data->{auth=data;INSTANCE.status.add("auth",auth);String value=text(auth,"mode")+":"+bool(auth,"signedIn")+":"+text(auth,"deviceCode");if(!value.equals(snapshot)){snapshot=value;layout();}});}
    @Override public void tick(){if(System.currentTimeMillis()>nextPoll)poll();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"One Microsoft sign-in for worlds, friends, invites and hosting.",false);
        if(bool(auth,"signedIn")){paragraph(g,"Signed in as "+text(auth,"name"),59,GREEN);paragraph(g,"Your login stays in your private local profile. You can switch accounts by signing out here.",82,MUTED);}
        else if(!text(auth,"deviceCode").isBlank()){g.centeredText(font,text(auth,"deviceCode"),width/2,65,GREEN);paragraph(g,"Open "+text(auth,"verificationUri")+" and enter this code. This page completes sign-in automatically.",87,MUTED);}
        else if(text(auth,"mode").equals("pending"))paragraph(g,"Completing your Microsoft sign-in… This may take a moment.",65,GREEN);
        else paragraph(g,"Connect your own Microsoft account to use Bedrock's social features. Your browser handles the sign-in; your password is never entered into this mod.",59,MUTED);
        String state=first(auth,"error","status");if(!state.isBlank())paragraph(g,state,118,text(auth,"error").isBlank()?MUTED:AMBER);status(g,mx,my);
    }
}
