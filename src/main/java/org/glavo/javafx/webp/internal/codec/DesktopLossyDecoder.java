package org.glavo.javafx.webp.internal.codec;

import org.glavo.javafx.webp.WebPException;
import org.glavo.javafx.webp.internal.BufferedImages;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Temporary lossy-frame decoder backed by Image I/O.
 *
 * <p>The long-term goal is to replace this adapter with a full pure-Java VP8 decoder ported from
 * {@code external/image-webp}. For now the class isolates the remaining desktop dependency behind
 * a narrow boundary so that container parsing, scaling, animation composition and VP8L decoding
 * already run through the in-project implementation.
 */
public final class DesktopLossyDecoder {

    static {
        ImageIO.scanForPlugins();
    }

    private DesktopLossyDecoder() {
    }

    /**
     * Decodes one still VP8-based frame to tightly packed RGBA pixels.
     *
     * <p>The input is the raw payload of a {@code VP8 } chunk, optionally accompanied by an
     * {@code ALPH} chunk. The method wraps those payloads in a minimal RIFF/WebP container so that
     * the legacy Image I/O backend can decode only the pixels for the requested frame rectangle.
     *
     * @param width the frame width
     * @param height the frame height
     * @param alphaChunk the optional ALPH chunk payload
     * @param vp8Chunk the raw VP8 chunk payload
     * @return tightly packed non-premultiplied RGBA pixels
     * @throws WebPException if the frame cannot be decoded
     */
    public static byte[] decodeRgba(int width, int height, byte[] alphaChunk, byte[] vp8Chunk) throws WebPException {
        byte[] container = buildStillContainer(width, height, alphaChunk, vp8Chunk);
        try (ByteArrayInputStream input = new ByteArrayInputStream(container)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new WebPException("ImageIO returned null for a VP8 frame");
            }
            if (image.getWidth() != width || image.getHeight() != height) {
                throw new WebPException("Decoded VP8 frame dimensions do not match the container");
            }
            return BufferedImages.toRgba(image);
        } catch (IOException ex) {
            throw new WebPException("Failed to decode VP8 frame", ex);
        }
    }

    private static byte[] buildStillContainer(int width, int height, byte[] alphaChunk, byte[] vp8Chunk) throws WebPException {
        if (width <= 0 || height <= 0) {
            throw new WebPException("Frame dimensions must be positive");
        }

        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (alphaChunk != null) {
                writeChunk(body, "VP8X", buildVp8x(width, height, true));
                writeChunk(body, "ALPH", alphaChunk);
            }
            writeChunk(body, "VP8 ", vp8Chunk);

            byte[] bodyBytes = body.toByteArray();
            ByteArrayOutputStream riff = new ByteArrayOutputStream();
            riff.write('R');
            riff.write('I');
            riff.write('F');
            riff.write('F');
            writeU32LE(riff, bodyBytes.length + 4L);
            riff.write('W');
            riff.write('E');
            riff.write('B');
            riff.write('P');
            riff.write(bodyBytes);
            return riff.toByteArray();
        } catch (IOException ex) {
            throw new WebPException("Failed to build a temporary WebP frame container", ex);
        }
    }

    private static byte[] buildVp8x(int width, int height, boolean alpha) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(10);
        out.write(alpha ? 1 << 4 : 0);
        out.write(0);
        out.write(0);
        out.write(0);
        writeU24LE(out, width - 1);
        writeU24LE(out, height - 1);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String fourCc, byte[] payload) throws IOException {
        out.write(fourCc.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeU32LE(out, payload.length);
        out.write(payload);
        if ((payload.length & 1) != 0) {
            out.write(0);
        }
    }

    private static void writeU24LE(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
    }

    private static void writeU32LE(ByteArrayOutputStream out, long value) throws IOException {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 24) & 0xFF));
    }
}
