package dev.snooped.bedrockmenu;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/** Bounded PNG skin/cape decoding. The caller owns each successfully returned image. */
public final class BedrockSkinImages {
    private static final int MAX_PNG_BYTES = 600_000;
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int IHDR = 0x49484452;
    private static final int IDAT = 0x49444154;
    private static final int IEND = 0x49454E44;

    private BedrockSkinImages() { }

    /** Validates the bounded PNG container before native decoding; returns {width, height}. */
    public static int[] validate(byte[] png) throws IOException {
        if (png == null || png.length < 33 || png.length > MAX_PNG_BYTES) {
            throw new IOException("Invalid skin PNG payload length");
        }
        ByteBuffer header = ByteBuffer.wrap(png);
        if (header.getLong() != PNG_SIGNATURE || header.getInt() != 13 || header.getInt() != IHDR) {
            throw new IOException("Expected PNG signature and IHDR");
        }
        int width = header.getInt(), height = header.getInt();
        if (width < 1 || height < 1 || width > 256 || height > 256) {
            throw new IOException("PNG dimensions must be between 1 and 256");
        }
        int depth = Byte.toUnsignedInt(header.get());
        int color = Byte.toUnsignedInt(header.get());
        boolean validDepth = switch (color) {
            case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
            case 2, 4, 6 -> depth == 8 || depth == 16;
            case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
            default -> false;
        };
        if (!validDepth || header.get() != 0 || header.get() != 0 || Byte.toUnsignedInt(header.get()) > 1) {
            throw new IOException("Invalid PNG IHDR encoding");
        }

        boolean hasPixels = false;
        for (int offset = 8; offset < png.length;) {
            if (png.length - offset < 12) throw new IOException("Truncated PNG chunk");
            int length = header.getInt(offset);
            int type = header.getInt(offset + 4);
            if (length < 0 || length > png.length - offset - 12) throw new IOException("Invalid PNG chunk length");
            if (type == IHDR && offset != 8) throw new IOException("Duplicate PNG IHDR");
            CRC32 crc = new CRC32();
            crc.update(png, offset + 4, length + 4);
            if ((int) crc.getValue() != header.getInt(offset + 8 + length)) throw new IOException("Invalid PNG chunk checksum");
            offset += length + 12;
            if (type == IDAT) hasPixels = true;
            if (type == IEND) {
                if (length != 0 || !hasPixels || offset != png.length) throw new IOException("Invalid PNG end chunk");
                return new int[]{width, height};
            }
        }
        throw new IOException("Missing PNG end chunk");
    }

    public static NativeImage decodeSkin(byte[] png) throws IOException {
        int[] size = validate(png);
        if (!skinWidth(size[0]) || (size[1] != size[0] && size[1] != size[0] / 2)) {
            throw new IOException("Unsupported skin atlas dimensions");
        }
        NativeImage image = read(png, size);
        if (size[0] == size[1]) return image; // Modern Bedrock/persona atlases retain their original alpha.
        try (image) {
            return expandLegacy(image);
        } catch (RuntimeException failure) {
            throw new IOException("Could not expand legacy skin", failure);
        }
    }

    public static NativeImage decodeCape(byte[] png) throws IOException {
        int[] size = validate(png);
        // A square 32x32 Bedrock cape has no unambiguous Java UV conversion here.
        // The receiver may discard that cape while retaining the independently decoded skin.
        if (!skinWidth(size[0]) || size[1] != size[0] / 2) {
            throw new IOException("Unsupported cape atlas dimensions");
        }
        return read(png, size);
    }

    private static boolean skinWidth(int width) {
        return width == 64 || width == 128 || width == 256;
    }

    private static NativeImage read(byte[] png, int[] size) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(png)) {
            NativeImage image = NativeImage.read(NativeImage.Format.RGBA, input);
            if (image.getWidth() != size[0] || image.getHeight() != size[1]) {
                image.close();
                throw new IOException("Decoded PNG dimensions differ from IHDR");
            }
            return image;
        } catch (RuntimeException failure) {
            throw new IOException("Could not decode skin PNG", failure);
        }
    }

    private static NativeImage expandLegacy(NativeImage source) {
        int scale = source.getWidth() / 64;
        NativeImage modern = new NativeImage(source.getWidth(), source.getWidth(), true);
        boolean completed = false;
        try {
            modern.copyFrom(source);
            modern.fillRect(0, 32 * scale, 64 * scale, 32 * scale, 0);
            // Native SkinTextureDownloader.processLegacySkin: source x/y, destination offsets, size.
            mirror(modern, scale, 4, 16, 16, 32, 4, 4);
            mirror(modern, scale, 8, 16, 16, 32, 4, 4);
            mirror(modern, scale, 0, 20, 24, 32, 4, 12);
            mirror(modern, scale, 4, 20, 16, 32, 4, 12);
            mirror(modern, scale, 8, 20, 8, 32, 4, 12);
            mirror(modern, scale, 12, 20, 16, 32, 4, 12);
            mirror(modern, scale, 44, 16, -8, 32, 4, 4);
            mirror(modern, scale, 48, 16, -8, 32, 4, 4);
            mirror(modern, scale, 40, 20, 0, 32, 4, 12);
            mirror(modern, scale, 44, 20, -8, 32, 4, 12);
            mirror(modern, scale, 48, 20, -16, 32, 4, 12);
            mirror(modern, scale, 52, 20, -8, 32, 4, 12);
            opaque(modern, 0, 0, 32 * scale, 16 * scale);
            legacyHatAlpha(modern, scale);
            opaque(modern, 0, 16 * scale, 64 * scale, 32 * scale);
            opaque(modern, 16 * scale, 48 * scale, 48 * scale, 64 * scale);
            completed = true;
            return modern;
        } finally {
            if (!completed) modern.close();
        }
    }

    private static void mirror(NativeImage image, int scale, int x, int y, int dx, int dy, int width, int height) {
        image.copyRect(x * scale, y * scale, dx * scale, dy * scale, width * scale, height * scale, true, false);
    }

    private static void opaque(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
        }
    }

    private static void legacyHatAlpha(NativeImage image, int scale) {
        for (int y = 0; y < 32 * scale; y++) {
            for (int x = 32 * scale; x < 64 * scale; x++) {
                if ((image.getPixel(x, y) >>> 24) < 128) return;
            }
        }
        for (int y = 0; y < 32 * scale; y++) {
            for (int x = 32 * scale; x < 64 * scale; x++) {
                image.setPixel(x, y, image.getPixel(x, y) & 0x00FFFFFF);
            }
        }
    }
}
