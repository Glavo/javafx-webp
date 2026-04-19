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
package org.glavo.javafx.webp.internal.codec;

import java.nio.charset.StandardCharsets;

/// Four-byte RIFF identifier stored as raw bytes.
///
/// WebP chunk types, the `RIFF` container marker and the `WEBP` signature are all encoded as
/// four ASCII bytes. Keeping them in a dedicated value type avoids repeated string allocation in
/// the parser and makes identifier comparisons explicit.
public record FourCC(byte b0, byte b1, byte b2, byte b3) {

    /// Creates a FourCC from a four-character ASCII string.
    ///
    /// @param fourCC the textual identifier
    /// @return the parsed FourCC value
    public static FourCC of(String fourCC) {
        if (fourCC.length() != 4) {
            throw new IllegalArgumentException("Invalid fourCC: " + fourCC);
        }
        var ch0 = fourCC.charAt(0);
        var ch1 = fourCC.charAt(1);
        var ch2 = fourCC.charAt(2);
        var ch3 = fourCC.charAt(3);

        if (ch0 > 0xFF || ch1 > 0xFF || ch2 > 0xFF || ch3 > 0xFF) {
            throw new IllegalArgumentException("Invalid fourCC: " + fourCC);
        }

        return new FourCC(
                (byte) fourCC.charAt(0),
                (byte) fourCC.charAt(1),
                (byte) fourCC.charAt(2),
                (byte) fourCC.charAt(3)
        );
    }

    /// Returns the canonical ASCII representation.
    @Override
    public String toString() {
        return new String(new byte[]{b0, b1, b2, b3}, StandardCharsets.US_ASCII);
    }
}
