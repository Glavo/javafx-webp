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
package org.glavo.webp;

import javafx.application.Platform;
import org.glavo.webp.javafx.WebPFXImage;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// Tests for the JavaFX WebP image adapter and its animation controls.
@NotNullByDefault
final class WebPFXImageTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    @BeforeAll
    static void initializeJavaFx() throws Exception {
        CompletableFuture<Void> startup = new CompletableFuture<>();
        try {
            Platform.startup(() -> startup.complete(null));
            startup.get(5, TimeUnit.SECONDS);
        } catch (IllegalStateException ignored) {
            // The JavaFX toolkit can only be started once per JVM.
        }
    }

    @Test
    void javaFxImageFromDecodedImageStartsPausedOnFirstFrame() throws Exception {
        WebPImage decoded = animatedImage(0, frame(RED, 40), frame(GREEN, 40), frame(BLUE, 40));

        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(decoded, false));

        assertFalse(callOnFxThread(image::isPlaying));
        assertEquals(0, callOnFxThread(image::getCurrentFrameIndex));
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void playPauseAndStopControlAnimation() throws Exception {
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
                animatedImage(0, frame(RED, 40), frame(GREEN, 40)),
                false
        ));

        callOnFxThread(() -> {
            image.play();
            return null;
        });
        waitForCondition(() -> callOnFxThread(image::getCurrentFrameIndex) == 1, 500);

        callOnFxThread(() -> {
            image.pause();
            return null;
        });
        assertFalse(callOnFxThread(image::isPlaying));

        Thread.sleep(120);
        assertEquals(1, callOnFxThread(image::getCurrentFrameIndex));
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));

        callOnFxThread(() -> {
            image.stop();
            return null;
        });
        assertEquals(0, callOnFxThread(image::getCurrentFrameIndex));
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void seekToFrameAndPlayFromStartUpdateDisplayedPixels() throws Exception {
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
                animatedImage(0, frame(RED, 40), frame(GREEN, 40), frame(BLUE, 40)),
                false
        ));

        callOnFxThread(() -> {
            image.seekToFrame(2);
            return null;
        });
        assertEquals(2, callOnFxThread(image::getCurrentFrameIndex));
        assertEquals(BLUE, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));

        callOnFxThread(() -> {
            image.playFromStart();
            return null;
        });
        assertTrue(callOnFxThread(image::isPlaying));
        assertEquals(0, callOnFxThread(image::getCurrentFrameIndex));
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));

        waitForCondition(() -> callOnFxThread(image::getCurrentFrameIndex) == 1, 500);
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void finiteLoopCountStopsOnLastFrame() throws Exception {
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
                animatedImage(1, frame(RED, 40), frame(GREEN, 40)),
                true
        ));

        waitForCondition(() -> !callOnFxThread(image::isPlaying), 500);
        assertEquals(1, callOnFxThread(image::getCurrentFrameIndex));
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void playbackRateReschedulesAnimationAndRejectsInvalidValues() throws Exception {
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
                animatedImage(0, frame(RED, 240), frame(GREEN, 240)),
                false
        ));

        callOnFxThread(() -> {
            image.setPlaybackRate(4.0);
            image.play();
            return null;
        });

        waitForCondition(() -> callOnFxThread(image::getCurrentFrameIndex) == 1, 180);
        assertEquals(4.0, callOnFxThread(image::getPlaybackRate), 0.0001);

        callOnFxThread(() -> {
            image.stop();
            return null;
        });
        callOnFxThread(() -> {
            try {
                image.setPlaybackRate(0.0);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException ignored) {
            }
            try {
                image.setPlaybackRate(Double.NaN);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException ignored) {
            }
            return null;
        });
    }

    private static WebPImage animatedImage(int loopCount, WebPFrame... frames) {
        long loopDurationMillis = 0L;
        for (WebPFrame frame : frames) {
            loopDurationMillis += frame.getDurationMillis();
        }

        return new WebPImage(
                1,
                1,
                1,
                1,
                false,
                true,
                false,
                loopCount,
                loopDurationMillis,
                WebPMetadata.empty(),
                List.of(frames)
        );
    }

    private static WebPFrame frame(int argb, int durationMillis) {
        return new WebPFrame(1, 1, durationMillis, new int[]{argb});
    }

    private static void waitForCondition(ThrowingBooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not satisfied within " + timeoutMillis + "ms");
    }

    private static <T> T callOnFxThread(ThrowingSupplier<T> supplier) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (TimeoutException ex) {
            throw new AssertionError("Timed out waiting for JavaFX task", ex);
        }
    }

    @NotNullByDefault
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @NotNullByDefault
    private interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
