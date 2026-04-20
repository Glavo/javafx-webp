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
package org.glavo.webp.swing;

import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Objects;

/// Swing helpers for converting decoded WebP content into `BufferedImage` instances.
@NotNullByDefault
public final class WebPSwingUtils {
    private WebPSwingUtils() {
    }

    /// Creates a `BufferedImage` from the first frame of a decoded WebP image.
    ///
    /// Animated WebP images are converted using their first presentation frame.
    ///
    /// @param img the decoded WebP image
    /// @return a `TYPE_INT_ARGB` buffered image
    public static BufferedImage fromWebPImage(WebPImage img) {
        return fromWebPImage(img, null);
    }

    /// Writes the first frame of a decoded WebP image into a `BufferedImage`.
    ///
    /// Animated WebP images are converted using their first presentation frame. If `bimg`
    /// is `null`, has a different size, or is not `TYPE_INT_ARGB`, a replacement image is
    /// allocated and returned.
    ///
    /// @param img the decoded WebP image
    /// @param bimg the optional destination image to reuse
    /// @return the destination image containing the converted pixels
    public static BufferedImage fromWebPImage(WebPImage img, @Nullable BufferedImage bimg) {
        Objects.requireNonNull(img, "img");
        return fromWebPFrame(img.getFirstFrame(), bimg);
    }

    /// Creates a `BufferedImage` from one decoded WebP frame.
    ///
    /// @param frame the decoded frame
    /// @return a `TYPE_INT_ARGB` buffered image
    public static BufferedImage fromWebPFrame(WebPFrame frame) {
        return fromWebPFrame(frame, null);
    }

    /// Writes one decoded WebP frame into a `BufferedImage`.
    ///
    /// If `bimg` is `null`, has a different size, or is not `TYPE_INT_ARGB`, a replacement
    /// image is allocated and returned.
    ///
    /// @param frame the decoded frame
    /// @param bimg the optional destination image to reuse
    /// @return the destination image containing the converted pixels
    public static BufferedImage fromWebPFrame(WebPFrame frame, @Nullable BufferedImage bimg) {
        Objects.requireNonNull(frame, "frame");

        int width = frame.getWidth();
        int height = frame.getHeight();

        BufferedImage output = bimg;
        if (output == null
                || output.getWidth() != width
                || output.getHeight() != height
                || output.getType() != BufferedImage.TYPE_INT_ARGB) {
            output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        DataBufferInt dataBuffer = (DataBufferInt) output.getRaster().getDataBuffer();
        frame.getArgbPixels().get(dataBuffer.getData(), 0, width * height);
        return output;
    }
}
