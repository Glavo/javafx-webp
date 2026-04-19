/*
 * Copyright 2026 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.javafx.webp;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import org.glavo.javafx.webp.internal.Argb;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Integration tests for the public WebP decoding API.
final class WebPDecoderTest {

    @Test
    void decodesStaticLossyImage() throws Exception {
        WebPImage image = WebPDecoder.decodeAll(resource("images/gallery1-1.webp"));
        assertEquals(1, image.getFrames().size());
        assertFalse(image.isAnimated());
        assertTrue(image.isLossy());
        assertFalse(image.hasAlpha());
        assertEmptyMetadata(image.getMetadata());
        assertFrameSamplesClose(image.getFrames().get(0), "reference/gallery1-1.png", 24);
    }

    @Test
    void decodesStaticLosslessImageWithAlpha() throws Exception {
        WebPImage image = WebPDecoder.decodeAll(resource("images/gallery2-1_webp_a.webp"));
        assertEquals(1, image.getFrames().size());
        assertFalse(image.isAnimated());
        assertTrue(image.hasAlpha());
        assertFrameSamplesClose(image.getFrames().get(0), "reference/gallery2-1_webp_a.png", 16);
    }

    @Test
    void decodesTinyRegressionImage() throws Exception {
        WebPImage image = WebPDecoder.decodeAll(resource("images/regression-tiny.webp"));
        assertEquals(1, image.getFrames().size());
        assertFrameEquals(image.getFrames().get(0), "reference/regression-tiny.png");
    }

    @Test
    void streamsAnimatedLosslessFrames() throws Exception {
        try (WebPImageReader reader = WebPDecoder.open(resource("images/animated-random_lossless.webp"))) {
            assertTrue(reader.isAnimated());
            assertEquals(3, reader.getFrameCount());
            assertTrue(reader.getLoopDurationMillis() > 0);

            List<WebPFrame> frames = new ArrayList<>();
            while (true) {
                var frame = reader.readNextFrame();
                if (frame.isEmpty()) {
                    break;
                }
                frames.add(frame.get());
            }

            assertEquals(3, frames.size());
            assertTrue(reader.isComplete());

            assertFrameClose(frames.get(0), "reference/animated/random_lossless-1.png", 1);
            assertFrameClose(frames.get(1), "reference/animated/random_lossless-2.png", 1);
            assertFrameClose(frames.get(2), "reference/animated/random_lossless-3.png", 1);

            long summedDuration = frames.stream().mapToLong(WebPFrame::getDurationMillis).sum();
            assertEquals(reader.getLoopDurationMillis(), summedDuration);
        }
    }

    @Test
    void streamsAnimatedLossyFramesWithinTolerance() throws Exception {
        try (WebPImageReader reader = WebPDecoder.open(resource("images/animated-random_lossy.webp"))) {
            assertTrue(reader.isAnimated());
            assertTrue(reader.isLossy());
            assertEquals(4, reader.getFrameCount());

            List<WebPFrame> frames = new ArrayList<>();
            while (true) {
                var frame = reader.readNextFrame();
                if (frame.isEmpty()) {
                    break;
                }
                frames.add(frame.get());
            }

            assertEquals(4, frames.size());
            assertFrameSamplesClose(frames.get(0), "reference/animated/random_lossy-1.png", 64);
            assertFrameSamplesClose(frames.get(1), "reference/animated/random_lossy-2.png", 64);
            assertFrameSamplesClose(frames.get(2), "reference/animated/random_lossy-3.png", 64);
            assertFrameSamplesClose(frames.get(3), "reference/animated/random_lossy-4.png", 64);
        }
    }

    @Test
    void scalingDimensionsMatchJavaFxImageSemantics() throws Exception {
        assertScaledDimensions("images/gallery1-1.webp", "reference/gallery1-1.png", 180, 0, true, true);
        assertScaledDimensions("images/gallery1-1.webp", "reference/gallery1-1.png", 0, 96, true, true);
        assertScaledDimensions("images/gallery1-1.webp", "reference/gallery1-1.png", 180, 120, true, true);
        assertScaledDimensions("images/gallery1-1.webp", "reference/gallery1-1.png", 180, 120, false, false);
    }

    @Test
    void decodeFirstFrameImageProducesJavaFxImage() throws Exception {
        var options = WebPImageLoadOptions.builder()
                .requestedWidth(96)
                .requestedHeight(96)
                .preserveRatio(true)
                .smooth(true)
                .build();

        var image = WebPDecoder.decodeFirstFrameImage(resource("images/gallery2-1_webp_ll.webp"), options);
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    @Test
    void writableImageRoundTripsArgbPixels() throws Exception {
        WebPFrame frame = WebPDecoder.decodeAll(resource("images/gallery2-1_webp_a.webp")).getFrames().get(0);
        var image = frame.toWritableImage();
        var reader = image.getPixelReader();
        assertNotNull(reader);

        int[] expected = frame.getArgbArray();
        for (int y : sampleCoordinates(frame.getHeight())) {
            for (int x : sampleCoordinates(frame.getWidth())) {
                int expectedPixel = expected[y * frame.getWidth() + x];
                int actualPixel = reader.getArgb(x, y);
                for (int channel = 0; channel < 4; channel++) {
                    assertEquals(
                            visibleChannelValue(expectedPixel, channel),
                            visibleChannelValue(actualPixel, channel),
                            "pixel at (" + x + ", " + y + "), channel " + channel
                    );
                }
            }
        }
    }

    @Test
    void rejectsInvalidContainer() {
        byte[] invalid = "not a webp".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThrows(WebPException.class, () -> WebPDecoder.decodeAll(new ByteArrayInputStream(invalid)));
    }

    private static void assertScaledDimensions(
            String webpResource,
            String pngReferenceResource,
            double requestedWidth,
            double requestedHeight,
            boolean preserveRatio,
            boolean smooth
    ) throws Exception {
        WebPImageLoadOptions options = WebPImageLoadOptions.builder()
                .requestedWidth(requestedWidth)
                .requestedHeight(requestedHeight)
                .preserveRatio(preserveRatio)
                .smooth(smooth)
                .build();

        WebPImage image = WebPDecoder.decodeAll(resource(webpResource), options);
        try (InputStream reference = resource(pngReferenceResource)) {
            Image expected = new Image(reference, requestedWidth, requestedHeight, preserveRatio, smooth);
            assertEquals(expected.getWidth(), image.getWidth(), 0.0001);
            assertEquals(expected.getHeight(), image.getHeight(), 0.0001);
        }
    }

    private static void assertFrameEquals(WebPFrame frame, String referenceResource) throws Exception {
        assertFrameClose(frame, referenceResource, 0);
    }

    private static void assertFrameSamplesClose(WebPFrame frame, String referenceResource, int maxChannelDelta) throws Exception {
        int[] expected = readPngAsArgb(referenceResource);
        int[] actual = readPixels(frame);
        assertEquals(expected.length, actual.length, "pixel buffer size");

        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] xs = sampleCoordinates(width);
        int[] ys = sampleCoordinates(height);

        for (int y : ys) {
            for (int x : xs) {
                int pixelIndex = y * width + x;
                for (int channel = 0; channel < 4; channel++) {
                    int expectedValue = visibleChannelValue(expected[pixelIndex], channel);
                    int actualValue = visibleChannelValue(actual[pixelIndex], channel);
                    int delta = Math.abs(expectedValue - actualValue);
                    if (delta > maxChannelDelta) {
                        fail("sample pixel mismatch at (" + x + ", " + y + "), channel " + channel
                                + ": expected=" + expectedValue + ", actual=" + actualValue + ", delta=" + delta);
                    }
                }
            }
        }
    }

    private static void assertFrameClose(WebPFrame frame, String referenceResource, int maxChannelDelta) throws Exception {
        int[] expected = readPngAsArgb(referenceResource);
        int[] actual = readPixels(frame);
        assertEquals(expected.length, actual.length, "pixel buffer size");

        int maxObservedDelta = 0;
        for (int i = 0; i < expected.length; i++) {
            for (int channel = 0; channel < 4; channel++) {
                int expectedValue = visibleChannelValue(expected[i], channel);
                int actualValue = visibleChannelValue(actual[i], channel);
                int delta = Math.abs(expectedValue - actualValue);
                maxObservedDelta = Math.max(maxObservedDelta, delta);
                if (delta > maxChannelDelta) {
                    fail("pixel mismatch at pixel " + i + ", channel " + channel + ": expected=" + expectedValue
                            + ", actual=" + actualValue + ", maxObservedDelta=" + maxObservedDelta);
                }
            }
        }
    }

    private static int[] readPngAsArgb(String resource) throws Exception {
        try (InputStream input = resource(resource)) {
            Image image = new Image(input);
            assertFalse(image.isError(), "reference PNG must decode");
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            PixelReader pixelReader = image.getPixelReader();
            assertNotNull(pixelReader, "reference PNG must expose pixels");
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    argb[y * width + x] = pixelReader.getArgb(x, y);
                }
            }
            return argb;
        }
    }

    private static int[] readPixels(WebPFrame frame) {
        return frame.getArgbArray();
    }

    private static int visibleChannelValue(int argb, int channel) {
        int alpha = argb >>> 24;
        if (channel == 3) {
            return alpha;
        }
        int value = switch (channel) {
            case 0 -> Argb.red(argb);
            case 1 -> Argb.green(argb);
            case 2 -> Argb.blue(argb);
            default -> throw new IllegalArgumentException("Invalid channel: " + channel);
        };
        return value * alpha / 255;
    }

    private static int[] sampleCoordinates(int size) {
        if (size <= 1) {
            return new int[]{0};
        }
        if (size == 2) {
            return new int[]{0, 1};
        }
        return new int[]{0, size / 4, size / 2, (size * 3) / 4, size - 1};
    }

    private static void assertEmptyMetadata(WebPMetadata metadata) {
        assertTrue(metadata.getIccProfile().isEmpty());
        assertTrue(metadata.getExifMetadata().isEmpty());
        assertTrue(metadata.getXmpMetadata().isEmpty());
    }

    private static InputStream resource(String path) {
        InputStream input = WebPDecoderTest.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalArgumentException("Missing test resource: " + path);
        }
        return input;
    }
}
