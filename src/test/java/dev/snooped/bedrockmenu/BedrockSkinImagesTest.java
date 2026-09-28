package dev.snooped.bedrockmenu;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class BedrockSkinImagesTest {
    private static int color(int x, int y) { return 0xFF000000 | (x << 16) | (y << 8) | ((x * 17 + y * 31) & 255); }

    private static BufferedImage image(int width, int height, boolean translucent) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = color(x, y);
                if (translucent) pixel = (pixel & 0x00FFFFFF) | (((x + y) & 255) << 24);
                image.setRGB(x, y, pixel);
            }
        }
        return image;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", output));
        return output.toByteArray();
    }

    private static void headerChecksum(byte[] png) {
        CRC32 checksum = new CRC32(); checksum.update(png, 12, 17);
        ByteBuffer.wrap(png).putInt(29, (int) checksum.getValue());
    }

    @Test void validatesHeadersDimensionsAndChunksBeforeNativeDecode() throws Exception {
        byte[] good = png(image(64, 64, true));
        assertArrayEquals(new int[]{64, 64}, BedrockSkinImages.validate(good));
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(null));
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(new byte[600_001]));
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(Arrays.copyOf(good, 32)));
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(Arrays.copyOf(good, good.length - 1)));
        byte[] signature = good.clone(); signature[0] = 0;
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(signature));
        byte[] checksum = good.clone(); checksum[29] ^= 1;
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(checksum));
        byte[] chunkLength = good.clone(); ByteBuffer.wrap(chunkLength).putInt(33, Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(chunkLength));
        for (int width : new int[]{0, -1, 257, Integer.MAX_VALUE}) {
            byte[] dimensions = good.clone(); ByteBuffer.wrap(dimensions).putInt(16, width); headerChecksum(dimensions);
            assertThrows(IOException.class, () -> BedrockSkinImages.validate(dimensions));
        }
        byte[] depth = good.clone(); depth[24] = 3; headerChecksum(depth);
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(depth));
        byte[] duplicate = new byte[good.length + 25];
        System.arraycopy(good, 0, duplicate, 0, 33);
        System.arraycopy(good, 8, duplicate, 33, 25);
        System.arraycopy(good, 33, duplicate, 58, good.length - 33);
        assertThrows(IOException.class, () -> BedrockSkinImages.validate(duplicate));
    }

    @Test void rejectsNoncanonicalAtlasesWithoutDecoding() throws Exception {
        for (int[] size : new int[][]{{32, 32}, {96, 96}, {64, 16}, {128, 32}}) {
            byte[] data = png(image(size[0], size[1], false));
            assertThrows(IOException.class, () -> BedrockSkinImages.decodeSkin(data));
        }
        for (int[] size : new int[][]{{32, 32}, {64, 64}, {128, 128}, {128, 32}}) {
            byte[] data = png(image(size[0], size[1], false));
            assertThrows(IOException.class, () -> BedrockSkinImages.decodeCape(data));
        }
    }

    @Test void preservesEveryModernSkinAndCapePixelIncludingAlphaAtEachScale() throws Exception {
        for (int width : new int[]{64, 128, 256}) {
            BufferedImage skin = image(width, width, true);
            try (NativeImage actual = BedrockSkinImages.decodeSkin(png(skin))) { assertPixels(skin, actual); }
            BufferedImage cape = image(width, width / 2, true);
            try (NativeImage actual = BedrockSkinImages.decodeCape(png(cape))) { assertPixels(cape, actual); }
        }
    }

    @Test void legacy64ConversionMatchesNativeMinecraftIncludingBothHatCases() throws Exception {
        var nativeConversion = SkinTextureDownloader.class.getDeclaredMethod("processLegacySkin", NativeImage.class, String.class);
        nativeConversion.setAccessible(true);
        for (boolean transparentHat : new boolean[]{false, true}) {
            BufferedImage source = image(64, 32, false);
            source.setRGB(1, 1, 0x10345678); // Native legacy base layers are opaque.
            if (transparentHat) source.setRGB(40, 5, 0x7F123456);
            byte[] png = png(source);
            try (NativeImage input = NativeImage.read(png);
                 NativeImage expected = (NativeImage) nativeConversion.invoke(null, input, "legacy fixture");
                 NativeImage actual = BedrockSkinImages.decodeSkin(png)) {
                assertEquals(64, actual.getHeight());
                assertArrayEquals(expected.getPixels(), actual.getPixels());
                assertEquals(transparentHat ? 127 : 0, actual.getPixel(40, 5) >>> 24);
                assertEquals(255, actual.getPixel(1, 1) >>> 24);
            }
        }
    }

    @Test void hdLegacyMirrorsDetailedLimbFacesWithoutDownsampling() throws Exception {
        // Explicit source and destination face origins, independently of copyRect's offset API.
        int[][] faces = {
                {4, 16, 20, 48, 4}, {8, 16, 24, 48, 4},
                {0, 20, 24, 52, 12}, {4, 20, 20, 52, 12}, {8, 20, 16, 52, 12}, {12, 20, 28, 52, 12},
                {44, 16, 36, 48, 4}, {48, 16, 40, 48, 4},
                {40, 20, 40, 52, 12}, {44, 20, 36, 52, 12}, {48, 20, 32, 52, 12}, {52, 20, 44, 52, 12}
        };
        for (int scale : new int[]{2, 4}) {
            BufferedImage source = image(64 * scale, 32 * scale, false);
            try (NativeImage actual = BedrockSkinImages.decodeSkin(png(source))) {
                assertEquals(64 * scale, actual.getWidth()); assertEquals(64 * scale, actual.getHeight());
                for (int[] face : faces) {
                    for (int y = 0; y < face[4] * scale; y++) {
                        for (int x = 0; x < 4 * scale; x++) {
                            int expected = source.getRGB(face[0] * scale + 4 * scale - 1 - x, face[1] * scale + y);
                            assertEquals(expected, actual.getPixel(face[2] * scale + x, face[3] * scale + y));
                        }
                    }
                }
                assertEquals(0, actual.getPixel(1, 40 * scale)); // Newly added outer layers stay clear.
                assertEquals(0, actual.getPixel(40 * scale, 5 * scale) >>> 24); // Legacy opaque-hat workaround scales.
                assertEquals(source.getRGB(9, 9), actual.getPixel(9, 9)); // Fine detail in original head survives.
            }
            source.setRGB(40 * scale + 1, 5 * scale + 1, 0x7F123456);
            try (NativeImage actual = BedrockSkinImages.decodeSkin(png(source))) {
                assertEquals(0x7F123456, actual.getPixel(40 * scale + 1, 5 * scale + 1));
                assertEquals(255, actual.getPixel(40 * scale, 5 * scale) >>> 24);
            }
        }
    }

    private static void assertPixels(BufferedImage expected, NativeImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth()); assertEquals(expected.getHeight(), actual.getHeight());
        assertArrayEquals(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()), actual.getPixels());
    }
}
