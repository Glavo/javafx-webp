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

/// WebP decoding library for JavaFX.
///
/// The module exposes a public API in `org.glavo.javafx.webp` for reading static and
/// animated WebP images, extracting metadata, and converting decoded frames to JavaFX images.
/// Decoded frame pixels are exposed as packed non-premultiplied `ARGB` integers.
/// Two entry points are provided:
///
///   - [org.glavo.javafx.webp.WebPDecoder] for eager convenience methods;
///   - [org.glavo.javafx.webp.WebPImageReader] for forward-only frame-by-frame decode.
///
/// The decoder is implemented in pure Java. It does not depend on `java.desktop` or any
/// external WebP codec, and supports decode-time scaling with the same
/// `requestedWidth/requestedHeight/preserveRatio/smooth` semantics used by
/// [javafx.scene.image.Image].
///
/// `javafx.controls` is only required for the bundled
/// [org.glavo.javafx.webp.WebPViewerApp] demo application, so the dependency remains
/// optional at compile time.
module org.glavo.javafx.webp {
    requires javafx.graphics;

    // For WebPViewerApp
    requires static javafx.controls;

    exports org.glavo.javafx.webp;
}
