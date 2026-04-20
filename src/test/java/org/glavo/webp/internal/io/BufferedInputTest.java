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
package org.glavo.webp.internal.io;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NotNullByDefault
final class BufferedInputTest {

    @Test
    void readsAcrossMultipleInputStreamRefills() throws Exception {
        byte[] payload = payload(9000);
        byte[] data = testData(payload);

        try (BufferedInput input = new BufferedInput.OfInputStream(new FragmentedInputStream(data, 127))) {
            assertReadsHeaderAndPayload(input, payload);
        }
    }

    @Test
    void readsAcrossMultipleChannelRefills() throws Exception {
        byte[] payload = payload(9000);
        byte[] data = testData(payload);

        try (BufferedInput input = new BufferedInput.OfByteChannel(Channels.newChannel(new FragmentedInputStream(data, 211)))) {
            assertReadsHeaderAndPayload(input, payload);
        }
    }

    @Test
    void byteBufferWrapperUsesSliceWithoutMutatingCallerState() throws Exception {
        byte[] data = ByteBuffer.allocate(6)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0x1234)
                .putInt(0x89ABCDEF)
                .array();

        ByteBuffer source = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        source.position(2);

        try (BufferedInput input = new BufferedInput.OfByteBuffer(source)) {
            assertEquals(0x89ABCDEF, input.readInt());
            assertEquals(2, source.position());
            assertEquals(ByteOrder.BIG_ENDIAN, source.order());
        }
    }

    @Test
    void truncatedInputThrowsWebPException() throws Exception {
        try (BufferedInput input = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(new byte[]{1, 2, 3}))) {
            assertThrows(WebPException.class, input::readInt);
        }
    }

    @Test
    void closeClosesUnderlyingStreamAndRejectsFurtherReads() throws Exception {
        TrackingInputStream source = new TrackingInputStream(new byte[]{1, 2, 3}, 2);
        BufferedInput input = new BufferedInput.OfInputStream(source);

        input.close();

        assertTrue(source.wasClosed);
        assertThrows(IOException.class, input::readByte);
    }

    private static void assertReadsHeaderAndPayload(BufferedInput input, byte[] payload) throws Exception {
        assertEquals(0xAB, input.readUnsignedByte());
        assertEquals(0x1234, input.readUnsignedShort());
        assertEquals(0x89ABCDEF, input.readInt());
        assertEquals(0x0123456789ABCDEFL, input.readLong());
        assertArrayEquals(payload, input.readByteArray(payload.length));
    }

    private static byte[] testData(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 2 + 4 + 8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0xAB);
        buffer.putShort((short) 0x1234);
        buffer.putInt(0x89ABCDEF);
        buffer.putLong(0x0123456789ABCDEFL);
        buffer.put(payload);
        return buffer.array();
    }

    private static byte[] payload(int length) {
        byte[] payload = new byte[length];
        for (int i = 0; i < length; i++) {
            payload[i] = (byte) i;
        }
        return payload;
    }

    private static class FragmentedInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int maxChunkSize;

        private FragmentedInputStream(byte[] data, int maxChunkSize) {
            this.delegate = new ByteArrayInputStream(data);
            this.maxChunkSize = maxChunkSize;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, Math.min(len, maxChunkSize));
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class TrackingInputStream extends FragmentedInputStream {
        private boolean wasClosed = false;

        private TrackingInputStream(byte[] data, int maxChunkSize) {
            super(data, maxChunkSize);
        }

        @Override
        public void close() throws IOException {
            wasClosed = true;
            super.close();
        }
    }
}
