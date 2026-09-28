// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

/** Preserve invite-code enrollment and required Timeline opt-in before connecting. */
final class RealmCodeScreen extends MenuScreen {
    private final BedrockScreen owner;private String code="";
    RealmCodeScreen(BedrockScreen parent){super(parent,"Join a Realm");owner=parent;}
    @Override protected void init(){dimensions();int x=left+8,w=contentWidth-16;EditBox input=field("Invite code or link",code,x,65,w,v->code=v);int half=(w-8)/2;button("Join Realm",x,height-51,half,()->{String value=code.strip();if(value.isBlank()){message="Paste a Realm invite link or its code.";return;}INSTANCE.join("realmcode",value,owner);});button("Cancel",x+half+8,height-51,half,this::onClose);setInitialFocus(input);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"Bring your Bedrock Realm invitation into Java.",false);g.text(font,"Invite code or link",left+8,53,MUTED);paragraph(g,"Paste the full invitation link or its code. Joining enrolls your account in the Realm and accepts any required Timeline opt-in.",100,MUTED);status(g,mx,my);}
}
