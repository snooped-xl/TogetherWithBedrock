// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static dev.snooped.bedrockmenu.BedrockMenu.*;

final class HostPackImportScreen extends MenuScreen {
    private final String id;private String path;private boolean choosing;private EditBox field;private Button install,browse;
    HostPackImportScreen(Screen parent,String id,String path){super(parent,"Import datapack");this.id=id;this.path=path;}
    @Override protected void init(){
        dimensions();field=field("Datapack ZIP","",left+8,86,contentWidth-108,v->path=v);field.setMaxLength(4096);field.setValue(path);field.setHint(Component.literal("Choose or drop a .zip file"));
        browse=button("Choose file…",left+contentWidth-96,86,88,this::choose);
        install=button("Import",left,height-51,contentWidth,this::install);button("Back",left,height-26,70,this::onClose);setInitialFocus(field);buttons();
    }
    private void buttons(){install.active=!loading&&!choosing&&!path.isBlank();browse.active=!loading&&!choosing;}
    private void choose(){
        if(loading||choosing)return;choosing=true;buttons();
        CompletableFuture.supplyAsync(()->{try(var stack=MemoryStack.stackPush()){return TinyFileDialogs.tinyfd_openFileDialog("Choose a datapack ZIP",null,stack.pointers(stack.UTF8("*.zip")),"Datapack ZIP",false);}}).whenComplete((file,error)->minecraft.execute(()->{
            choosing=false;if(minecraft.gui.screen()!=this)return;if(error!=null)message="File chooser unavailable. Paste the ZIP path or drag the file here.";else if(file!=null)field.setValue(file);buttons();
        }));
    }
    private void install(){if(loading||choosing||path.isBlank())return;request("hosting.datapackImport",object("id",id,"path",path.strip()),data->minecraft.gui.setScreen(parent));buttons();}
    @Override public void onFilesDrop(List<Path> files){if(!loading&&!choosing&&files.size()==1)field.setValue(files.getFirst().toAbsolutePath().toString());else message="Choose one ZIP at a time.";}
    @Override void onRequestError(){buttons();}
    @Override public void tick(){buttons();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);header(g,"Add content to this hosted world",false);g.text(font,"Datapack ZIP",left+8,72,MUTED);paragraph(g,"The ZIP must contain pack.mcmeta at its root and support Minecraft 26.2. Your original file stays unchanged.",120,MUTED);status(g,mx,my);}
}
