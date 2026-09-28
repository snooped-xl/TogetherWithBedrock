package dev.snooped.bedrockmenu;

import java.lang.reflect.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Runs only in the isolated GL smoke harness, never in the shipped client. */
final class BedrockSkinSmoke {
    static void run(Minecraft client) throws Exception {
        Class.forName("net.minecraft.client.multiplayer.PlayerInfo");
        Class.forName("net.minecraft.client.player.AbstractClientPlayer");
        Class<?> slotType=Class.forName("dev.snooped.bedrockmenu.BedrockSkins$Slot");
        var ctor=slotType.getDeclaredConstructor();ctor.setAccessible(true);Object slot=ctor.newInstance();
        var decode=BedrockSkins.class.getDeclaredMethod("decode",BedrockSkinPayload.class);decode.setAccessible(true);
        var upload=BedrockSkins.class.getDeclaredMethod("upload",UUID.class,slotType,BedrockSkinPayload.class,Class.forName("dev.snooped.bedrockmenu.BedrockSkins$Decoded"));upload.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,Object> cache=(Map<UUID,Object>)field(BedrockSkins.class,"SKINS").get(null);
        UUID uuid=UUID.randomUUID();cache.put(uuid,slot);
        try {
            var first=new BedrockSkinPayload(uuid,1,png(128,128,0xB1234567),png(64,32,0xFFAABBCC));
            Object images=decode.invoke(null,first);
            try { upload.invoke(null,uuid,slot,first,images); } finally { ((AutoCloseable)images).close(); }
            PlayerSkin skin=(PlayerSkin)field(slotType,"skin").get(slot);
            if(skin==null || skin.model()!=PlayerModelType.SLIM || skin.cape()==null) throw new AssertionError("Slim skin/cape upload failed");
            var body=(DynamicTexture)client.getTextureManager().getTexture(skin.body().texturePath());
            var cape=(DynamicTexture)client.getTextureManager().getTexture(skin.cape().texturePath());
            if(body.getPixels().getPixel(50,50)!=0xB1234567 || body.getPixels().getWidth()!=128 || cape.getPixels().getPixel(10,10)!=0xFFAABBCC) throw new AssertionError("Texture pixel/alpha lost");
            if(BedrockSkins.lookup(uuid)!=null) throw new AssertionError("Unnegotiated/disconnected skin leaked");
            var replacement=new BedrockSkinPayload(uuid,0,png(64,32,0xFF654321),new byte[0]);
            images=decode.invoke(null,replacement);
            try { upload.invoke(null,uuid,slot,replacement,images); } finally { ((AutoCloseable)images).close(); }
            skin=(PlayerSkin)field(slotType,"skin").get(slot);
            if(skin.model()!=PlayerModelType.WIDE || skin.cape()!=null || !body.getPixels().isClosed() || !cape.getPixels().isClosed()) throw new AssertionError("Replaced texture/cape not released");
            body=(DynamicTexture)client.getTextureManager().getTexture(skin.body().texturePath());
            if(body.getPixels().getHeight()!=64) throw new AssertionError("Legacy texture did not expand");
            BedrockSkins.reset();
            if(!body.getPixels().isClosed() || !cache.isEmpty() || field(BedrockSkins.class,"textureBytes").getInt(null)!=0) throw new AssertionError("Disconnect texture cleanup failed");
            System.out.println("BEDROCK_SKIN_RENDER_SMOKE_PASS");
        } finally { BedrockSkins.reset(); }
    }
    private static Field field(Class<?> owner,String name) throws Exception { var f=owner.getDeclaredField(name);f.setAccessible(true);return f; }
    private static byte[] png(int width,int height,int argb) throws Exception {
        var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,argb);
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",out);return out.toByteArray();
    }
}
