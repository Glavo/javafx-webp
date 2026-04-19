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
package org.glavo.javafx.webp.internal;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/// JavaFX image conversion helpers.
public final class FxImages {

    private FxImages() {
    }

    /// Converts non-premultiplied packed `ARGB` ints to a JavaFX [WritableImage].
    ///
    /// JavaFX exposes a writable non-premultiplied `ARGB` pixel format, so the method can pass the
    /// packed integers through directly without allocating an intermediate swizzled buffer.
    ///
    /// @param width the image width
    /// @param height the image height
    /// @param argb tightly packed non-premultiplied `ARGB` pixels stored as `0xAARRGGBB`
    /// @return a new writable JavaFX image
    public static WritableImage toWritableImage(int width, int height, int[] argb) {
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        writer.setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return image;
    }
}
