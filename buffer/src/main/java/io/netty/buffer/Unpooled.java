/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import io.netty.util.CharsetUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;


/**
 * Creates a new {@link ByteBuf} or a new {@link MessageBuf} by allocating new space or by wrapping
 * or copying existing byte arrays, byte buffers and a string.
 *
 * <h3>Use static import</h3>
 * This classes is intended to be used with Java 5 static import statement:
 *
 * <pre>
 * import static io.netty.buffer.{@link Unpooled}.*;
 *
 * {@link ByteBuf} heapBuffer    = buffer(128);
 * {@link ByteBuf} directBuffer  = directBuffer(256);
 * {@link ByteBuf} dynamicBuffer = dynamicBuffer(512);
 * {@link ByteBuf} wrappedBuffer = wrappedBuffer(new byte[128], new byte[256]);
 * {@link ByteBuf} copiedBuffe r = copiedBuffer({@link ByteBuffer}.allocate(128));
 * </pre>
 *
 * <h3>Allocating a new buffer</h3>
 *
 * Three buffer types are provided out of the box.
 *
 * <ul>
 * <li>{@link #buffer(int)} allocates a new fixed-capacity heap buffer.</li>
 * <li>{@link #directBuffer(int)} allocates a new fixed-capacity direct buffer.</li>
 * <li>{@link #dynamicBuffer(int)} allocates a new dynamic-capacity heap
 *     buffer, whose capacity increases automatically as needed by a write
 *     operation.</li>
 * </ul>
 *
 * <h3>Creating a wrapped buffer</h3>
 *
 * Wrapped buffer is a buffer which is a view of one or more existing
 * byte arrays and byte buffers.  Any changes in the content of the original
 * array or buffer will be visible in the wrapped buffer.  Various wrapper
 * methods are provided and their name is all {@code wrappedBuffer()}.
 * You might want to take a look at the methods that accept varargs closely if
 * you want to create a buffer which is composed of more than one array to
 * reduce the number of memory copy.
 *
 * <h3>Creating a copied buffer</h3>
 *
 * Copied buffer is a deep copy of one or more existing byte arrays, byte
 * buffers or a string.  Unlike a wrapped buffer, there's no shared data
 * between the original data and the copied buffer.  Various copy methods are
 * provided and their name is all {@code copiedBuffer()}.  It is also convenient
 * to use this operation to merge multiple buffers into one buffer.
 *
 * <h3>Miscellaneous utility methods</h3>
 *
 * This class also provides various utility methods to help implementation
 * of a new buffer type, generation of hex dump and swapping an integer's
 * byte order.
 * @apiviz.landmark
 * @apiviz.has io.netty.buffer.ChannelBuffer oneway - - creates
 */
public final class Unpooled {

    /**
     * Big endian byte order.
     */
    public static final ByteOrder BIG_ENDIAN = ByteOrder.BIG_ENDIAN;

    /**
     * Little endian byte order.
     */
    public static final ByteOrder LITTLE_ENDIAN = ByteOrder.LITTLE_ENDIAN;

    /**
     * A buffer whose capacity is {@code 0}.
     */
    public static final ByteBuf EMPTY_BUFFER = new HeapByteBuf(0) {
        @Override
        public ByteBuf order(ByteOrder endianness) {
            if (endianness == null) {
                throw new NullPointerException("endianness");
            }
            return this;
        }
    };

    private static final char[] HEXDUMP_TABLE = new char[256 * 4];

    static {
        final char[] DIGITS = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 256; i ++) {
            HEXDUMP_TABLE[(i << 1) + 0] = DIGITS[i >>> 4 & 0x0F];
            HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i >>> 0 & 0x0F];
        }
    }

    public static <T> MessageBuf<T> messageBuffer() {
        return new DefaultMessageBuf<T>();
    }

    public static <T> MessageBuf<T> messageBuffer(int initialCapacity) {
        return new DefaultMessageBuf<T>(initialCapacity);
    }

    public static <T> MessageBuf<T> wrappedBuffer(Queue<T> queue) {
        return new QueueBackedMessageBuf<T>(queue);
    }

    /**
     * Creates a new big-endian Java heap buffer with the specified
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf buffer(int capacity) {
        if (capacity == 0) {
            return EMPTY_BUFFER;
        }
        return new HeapByteBuf(capacity);
    }

    /**
     * Creates a new big-endian direct buffer with the specified
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf directBuffer(int capacity) {
        if (capacity == 0) {
            return EMPTY_BUFFER;
        }

        ByteBuf buffer = new NioBufferBackedByteBuf(ByteBuffer.allocateDirect(capacity));
        buffer.clear();
        return buffer;
    }

    /**
     * Creates a new big-endian dynamic buffer whose estimated data length is
     * {@code 256} bytes.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf dynamicBuffer() {
        return dynamicBuffer(256);
    }

    /**
     * Creates a new big-endian dynamic buffer whose estimated data length is
     * {@code 256} bytes.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf dynamicBuffer(ByteBufFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }

        return new DynamicByteBuf(256, factory);
    }

    /**
     * Creates a new big-endian dynamic buffer with the specified estimated
     * data length.  More accurate estimation yields less unexpected
     * reallocation overhead.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf dynamicBuffer(int estimatedLength) {
        return new DynamicByteBuf(estimatedLength);
    }

    /**
     * Creates a new big-endian dynamic buffer with the specified estimated
     * data length using the specified factory.  More accurate estimation yields
     * less unexpected reallocation overhead.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0}.
     */
    public static ByteBuf dynamicBuffer(int estimatedLength, ByteBufFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }

        return new DynamicByteBuf(estimatedLength, factory);
    }

    /**
     * Creates a new big-endian buffer which wraps the specified {@code array}.
     * A modification on the specified array's content will be visible to the
     * returned buffer.
     */
    public static ByteBuf wrappedBuffer(byte[] array) {
        if (array.length == 0) {
            return EMPTY_BUFFER;
        }
        return new HeapByteBuf(array);
    }

    /**
     * Creates a new big-endian buffer which wraps the sub-region of the
     * specified {@code array}.  A modification on the specified array's
     * content will be visible to the returned buffer.
     */
    public static ByteBuf wrappedBuffer(byte[] array, int offset, int length) {
        if (offset == 0) {
            if (length == array.length) {
                return wrappedBuffer(array);
            } else {
                if (length == 0) {
                    return EMPTY_BUFFER;
                } else {
                    return new TruncatedByteBuf(wrappedBuffer(array), length);
                }
            }
        } else {
            if (length == 0) {
                return EMPTY_BUFFER;
            } else {
                return new SlicedByteBuf(wrappedBuffer(array), offset, length);
            }
        }
    }

    /**
     * Creates a new buffer which wraps the specified NIO buffer's current
     * slice.  A modification on the specified buffer's content will be
     * visible to the returned buffer.
     */
    public static ByteBuf wrappedBuffer(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return EMPTY_BUFFER;
        }
        if (buffer.hasArray()) {
            return wrappedBuffer(
                    buffer.array(),
                    buffer.arrayOffset() + buffer.position(),
                    buffer.remaining()).order(buffer.order());
        } else {
            return new NioBufferBackedByteBuf(buffer);
        }
    }

    /**
     * Creates a new buffer which wraps the specified buffer's readable bytes.
     * A modification on the specified buffer's content will be visible to the
     * returned buffer.
     */
    public static ByteBuf wrappedBuffer(ByteBuf buffer) {
        if (buffer.readable()) {
            return buffer.slice();
        } else {
            return EMPTY_BUFFER;
        }
    }

    /**
     * Creates a new big-endian composite buffer which wraps the specified
     * arrays without copying them.  A modification on the specified arrays'
     * content will be visible to the returned buffer.
     */
    public static ByteBuf wrappedBuffer(byte[]... arrays) {
        switch (arrays.length) {
        case 0:
            break;
        case 1:
            if (arrays[0].length != 0) {
                return wrappedBuffer(arrays[0]);
            }
            break;
        default:
            // Get the list of the component, while guessing the byte order.
            final List<ByteBuf> components = new ArrayList<ByteBuf>(arrays.length);
            for (byte[] a: arrays) {
                if (a == null) {
                    break;
                }
                if (a.length > 0) {
                    components.add(wrappedBuffer(a));
                }
            }
            return compositeBuffer(BIG_ENDIAN, components);
        }

        return EMPTY_BUFFER;
    }

    /**
     * Creates a new composite buffer which wraps the specified
     * components without copying them.  A modification on the specified components'
     * content will be visible to the returned buffer.
     */
    private static ByteBuf compositeBuffer(ByteOrder endianness, List<ByteBuf> components) {
        switch (components.size()) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            return components.get(0);
        default:
            return new CompositeByteBuf(endianness, components);
        }
    }

    /**
     * Creates a new composite buffer which wraps the readable bytes of the
     * specified buffers without copying them.  A modification on the content
     * of the specified buffers will be visible to the returned buffer.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ByteBuf wrappedBuffer(ByteBuf... buffers) {
        switch (buffers.length) {
        case 0:
            break;
        case 1:
            if (buffers[0].readable()) {
                return wrappedBuffer(buffers[0]);
            }
            break;
        default:
            ByteOrder order = null;
            final List<ByteBuf> components = new ArrayList<ByteBuf>(buffers.length);
            for (ByteBuf c: buffers) {
                if (c == null) {
                    break;
                }
                if (c.readable()) {
                    if (order != null) {
                        if (!order.equals(c.order())) {
                            throw new IllegalArgumentException("inconsistent byte order");
                        }
                    } else {
                        order = c.order();
                    }
                    if (c instanceof CompositeByteBuf) {
                        // Expand nested composition.
                        components.addAll(
                                ((CompositeByteBuf) c).decompose(
                                        c.readerIndex(), c.readableBytes()));
                    } else {
                        // An ordinary buffer (non-composite)
                        components.add(c.slice());
                    }
                }
            }
            return compositeBuffer(order, components);
        }
        return EMPTY_BUFFER;
    }

    /**
     * Creates a new composite buffer which wraps the slices of the specified
     * NIO buffers without copying them.  A modification on the content of the
     * specified buffers will be visible to the returned buffer.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ByteBuf wrappedBuffer(ByteBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            break;
        case 1:
            if (buffers[0].hasRemaining()) {
                return wrappedBuffer(buffers[0]);
            }
            break;
        default:
            ByteOrder order = null;
            final List<ByteBuf> components = new ArrayList<ByteBuf>(buffers.length);
            for (ByteBuffer b: buffers) {
                if (b == null) {
                    break;
                }
                if (b.hasRemaining()) {
                    if (order != null) {
                        if (!order.equals(b.order())) {
                            throw new IllegalArgumentException("inconsistent byte order");
                        }
                    } else {
                        order = b.order();
                    }
                    components.add(wrappedBuffer(b));
                }
            }
            return compositeBuffer(order, components);
        }

        return EMPTY_BUFFER;
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified {@code array}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and {@code array.length} respectively.
     */
    public static ByteBuf copiedBuffer(byte[] array) {
        if (array.length == 0) {
            return EMPTY_BUFFER;
        }
        return new HeapByteBuf(array.clone());
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified {@code array}'s sub-region.  The new buffer's
     * {@code readerIndex} and {@code writerIndex} are {@code 0} and
     * the specified {@code length} respectively.
     */
    public static ByteBuf copiedBuffer(byte[] array, int offset, int length) {
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        byte[] copy = new byte[length];
        System.arraycopy(array, offset, copy, 0, length);
        return wrappedBuffer(copy);
    }

    /**
     * Creates a new buffer whose content is a copy of the specified
     * {@code buffer}'s current slice.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and {@code buffer.remaining}
     * respectively.
     */
    public static ByteBuf copiedBuffer(ByteBuffer buffer) {
        int length = buffer.remaining();
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        byte[] copy = new byte[length];
        int position = buffer.position();
        try {
            buffer.get(copy);
        } finally {
            buffer.position(position);
        }
        return wrappedBuffer(copy).order(buffer.order());
    }

    /**
     * Creates a new buffer whose content is a copy of the specified
     * {@code buffer}'s readable bytes.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and {@code buffer.readableBytes}
     * respectively.
     */
    public static ByteBuf copiedBuffer(ByteBuf buffer) {
        if (buffer.readable()) {
            return buffer.copy();
        } else {
            return EMPTY_BUFFER;
        }
    }

    /**
     * Creates a new big-endian buffer whose content is a merged copy of
     * the specified {@code arrays}.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and the sum of all arrays'
     * {@code length} respectively.
     */
    public static ByteBuf copiedBuffer(byte[]... arrays) {
        switch (arrays.length) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            if (arrays[0].length == 0) {
                return EMPTY_BUFFER;
            } else {
                return copiedBuffer(arrays[0]);
            }
        }

        // Merge the specified arrays into one array.
        int length = 0;
        for (byte[] a: arrays) {
            if (Integer.MAX_VALUE - length < a.length) {
                throw new IllegalArgumentException(
                        "The total length of the specified arrays is too big.");
            }
            length += a.length;
        }

        if (length == 0) {
            return EMPTY_BUFFER;
        }

        byte[] mergedArray = new byte[length];
        for (int i = 0, j = 0; i < arrays.length; i ++) {
            byte[] a = arrays[i];
            System.arraycopy(a, 0, mergedArray, j, a.length);
            j += a.length;
        }

        return wrappedBuffer(mergedArray);
    }

    /**
     * Creates a new buffer whose content is a merged copy of the specified
     * {@code buffers}' readable bytes.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and the sum of all buffers'
     * {@code readableBytes} respectively.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ByteBuf copiedBuffer(ByteBuf... buffers) {
        switch (buffers.length) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            return copiedBuffer(buffers[0]);
        }

        // Merge the specified buffers into one buffer.
        ByteOrder order = null;
        int length = 0;
        for (ByteBuf b: buffers) {
            int bLen = b.readableBytes();
            if (bLen <= 0) {
                continue;
            }
            if (Integer.MAX_VALUE - length < bLen) {
                throw new IllegalArgumentException(
                        "The total length of the specified buffers is too big.");
            }
            length += bLen;
            if (order != null) {
                if (!order.equals(b.order())) {
                    throw new IllegalArgumentException("inconsistent byte order");
                }
            } else {
                order = b.order();
            }
        }

        if (length == 0) {
            return EMPTY_BUFFER;
        }

        byte[] mergedArray = new byte[length];
        for (int i = 0, j = 0; i < buffers.length; i ++) {
            ByteBuf b = buffers[i];
            int bLen = b.readableBytes();
            b.getBytes(b.readerIndex(), mergedArray, j, bLen);
            j += bLen;
        }

        return wrappedBuffer(mergedArray).order(order);
    }

    /**
     * Creates a new buffer whose content is a merged copy of the specified
     * {@code buffers}' slices.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and the sum of all buffers'
     * {@code remaining} respectively.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ByteBuf copiedBuffer(ByteBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            return copiedBuffer(buffers[0]);
        }

        // Merge the specified buffers into one buffer.
        ByteOrder order = null;
        int length = 0;
        for (ByteBuffer b: buffers) {
            int bLen = b.remaining();
            if (bLen <= 0) {
                continue;
            }
            if (Integer.MAX_VALUE - length < bLen) {
                throw new IllegalArgumentException(
                        "The total length of the specified buffers is too big.");
            }
            length += bLen;
            if (order != null) {
                if (!order.equals(b.order())) {
                    throw new IllegalArgumentException("inconsistent byte order");
                }
            } else {
                order = b.order();
            }
        }

        if (length == 0) {
            return EMPTY_BUFFER;
        }

        byte[] mergedArray = new byte[length];
        for (int i = 0, j = 0; i < buffers.length; i ++) {
            ByteBuffer b = buffers[i];
            int bLen = b.remaining();
            int oldPos = b.position();
            b.get(mergedArray, j, bLen);
            b.position(oldPos);
            j += bLen;
        }

        return wrappedBuffer(mergedArray).order(order);
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(CharSequence string, Charset charset) {
        if (string == null) {
            throw new NullPointerException("string");
        }

        if (string instanceof CharBuffer) {
            return copiedBuffer((CharBuffer) string, charset);
        }

        return copiedBuffer(CharBuffer.wrap(string), charset);
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(
            CharSequence string, int offset, int length, Charset charset) {
        if (string == null) {
            throw new NullPointerException("string");
        }
        if (length == 0) {
            return EMPTY_BUFFER;
        }

        if (string instanceof CharBuffer) {
            CharBuffer buf = (CharBuffer) string;
            if (buf.hasArray()) {
                return copiedBuffer(
                        buf.array(),
                        buf.arrayOffset() + buf.position() + offset,
                        length, charset);
            }

            buf = buf.slice();
            buf.limit(length);
            buf.position(offset);
            return copiedBuffer(buf, charset);
        }

        return copiedBuffer(CharBuffer.wrap(string, offset, offset + length), charset);
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(char[] array, Charset charset) {
        return copiedBuffer(array, 0, array.length, charset);
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(char[] array, int offset, int length, Charset charset) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        return copiedBuffer(CharBuffer.wrap(array, offset, length), charset);
    }

    private static ByteBuf copiedBuffer(CharBuffer buffer, Charset charset) {
        ByteBuffer dst = Unpooled.encodeString(buffer, charset);
        ByteBuf result = wrappedBuffer(dst.array());
        result.writerIndex(dst.remaining());
        return result;
    }

    /**
     * Creates a read-only buffer which disallows any modification operations
     * on the specified {@code buffer}.  The new buffer has the same
     * {@code readerIndex} and {@code writerIndex} with the specified
     * {@code buffer}.
     */
    public static ByteBuf unmodifiableBuffer(ByteBuf buffer) {
        if (buffer instanceof ReadOnlyByteBuf) {
            buffer = ((ReadOnlyByteBuf) buffer).unwrap();
        }
        return new ReadOnlyByteBuf(buffer);
    }

    /**
     * Creates a new 4-byte big-endian buffer that holds the specified 32-bit integer.
     */
    public static ByteBuf copyInt(int value) {
        ByteBuf buf = buffer(4);
        buf.writeInt(value);
        return buf;
    }

    /**
     * Create a big-endian buffer that holds a sequence of the specified 32-bit integers.
     */
    public static ByteBuf copyInt(int... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 4);
        for (int v: values) {
            buffer.writeInt(v);
        }
        return buffer;
    }

    /**
     * Creates a new 2-byte big-endian buffer that holds the specified 16-bit integer.
     */
    public static ByteBuf copyShort(int value) {
        ByteBuf buf = buffer(2);
        buf.writeShort(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 16-bit integers.
     */
    public static ByteBuf copyShort(short... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 2);
        for (int v: values) {
            buffer.writeShort(v);
        }
        return buffer;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 16-bit integers.
     */
    public static ByteBuf copyShort(int... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 2);
        for (int v: values) {
            buffer.writeShort(v);
        }
        return buffer;
    }

    /**
     * Creates a new 3-byte big-endian buffer that holds the specified 24-bit integer.
     */
    public static ByteBuf copyMedium(int value) {
        ByteBuf buf = buffer(3);
        buf.writeMedium(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 24-bit integers.
     */
    public static ByteBuf copyMedium(int... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 3);
        for (int v: values) {
            buffer.writeMedium(v);
        }
        return buffer;
    }

    /**
     * Creates a new 8-byte big-endian buffer that holds the specified 64-bit integer.
     */
    public static ByteBuf copyLong(long value) {
        ByteBuf buf = buffer(8);
        buf.writeLong(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 64-bit integers.
     */
    public static ByteBuf copyLong(long... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 8);
        for (long v: values) {
            buffer.writeLong(v);
        }
        return buffer;
    }

    /**
     * Creates a new single-byte big-endian buffer that holds the specified boolean value.
     */
    public static ByteBuf copyBoolean(boolean value) {
        ByteBuf buf = buffer(1);
        buf.writeBoolean(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified boolean values.
     */
    public static ByteBuf copyBoolean(boolean... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length);
        for (boolean v: values) {
            buffer.writeBoolean(v);
        }
        return buffer;
    }

    /**
     * Creates a new 4-byte big-endian buffer that holds the specified 32-bit floating point number.
     */
    public static ByteBuf copyFloat(float value) {
        ByteBuf buf = buffer(4);
        buf.writeFloat(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 32-bit floating point numbers.
     */
    public static ByteBuf copyFloat(float... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 4);
        for (float v: values) {
            buffer.writeFloat(v);
        }
        return buffer;
    }

    /**
     * Creates a new 8-byte big-endian buffer that holds the specified 64-bit floating point number.
     */
    public static ByteBuf copyDouble(double value) {
        ByteBuf buf = buffer(8);
        buf.writeDouble(value);
        return buf;
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 64-bit floating point numbers.
     */
    public static ByteBuf copyDouble(double... values) {
        if (values == null || values.length == 0) {
            return EMPTY_BUFFER;
        }
        ByteBuf buffer = buffer(values.length * 8);
        for (double v: values) {
            buffer.writeDouble(v);
        }
        return buffer;
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's readable bytes.
     */
    public static String hexDump(ByteBuf buffer) {
        return hexDump(buffer, buffer.readerIndex(), buffer.readableBytes());
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's sub-region.
     */
    public static String hexDump(ByteBuf buffer, int fromIndex, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        if (length == 0) {
            return "";
        }

        int endIndex = fromIndex + length;
        char[] buf = new char[length << 1];

        int srcIdx = fromIndex;
        int dstIdx = 0;
        for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
            System.arraycopy(
                    HEXDUMP_TABLE, buffer.getUnsignedByte(srcIdx) << 1,
                    buf, dstIdx, 2);
        }

        return new String(buf);
    }

    /**
     * Calculates the hash code of the specified buffer.  This method is
     * useful when implementing a new buffer type.
     */
    public static int hashCode(ByteBuf buffer) {
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2;
        final int byteCount = aLen & 3;

        int hashCode = 1;
        int arrayIndex = buffer.readerIndex();
        if (buffer.order() == BIG_ENDIAN) {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + buffer.getInt(arrayIndex);
                arrayIndex += 4;
            }
        } else {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + swapInt(buffer.getInt(arrayIndex));
                arrayIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex ++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    /**
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other as described in {@code ChannelBuffer#equals(Object)}.
     * This method is useful when implementing a new buffer type.
     */
    public static boolean equals(ByteBuf bufferA, ByteBuf bufferB) {
        final int aLen = bufferA.readableBytes();
        if (aLen != bufferB.readableBytes()) {
            return false;
        }

        final int longCount = aLen >>> 3;
        final int byteCount = aLen & 7;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        if (bufferA.order() == bufferB.order()) {
            for (int i = longCount; i > 0; i --) {
                if (bufferA.getLong(aIndex) != bufferB.getLong(bIndex)) {
                    return false;
                }
                aIndex += 8;
                bIndex += 8;
            }
        } else {
            for (int i = longCount; i > 0; i --) {
                if (bufferA.getLong(aIndex) != swapLong(bufferB.getLong(bIndex))) {
                    return false;
                }
                aIndex += 8;
                bIndex += 8;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            if (bufferA.getByte(aIndex) != bufferB.getByte(bIndex)) {
                return false;
            }
            aIndex ++;
            bIndex ++;
        }

        return true;
    }

    /**
     * Compares the two specified buffers as described in {@link ByteBuf#compareTo(ByteBuf)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int compare(ByteBuf bufferA, ByteBuf bufferB) {
        final int aLen = bufferA.readableBytes();
        final int bLen = bufferB.readableBytes();
        final int minLength = Math.min(aLen, bLen);
        final int uintCount = minLength >>> 2;
        final int byteCount = minLength & 3;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        if (bufferA.order() == bufferB.order()) {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = bufferB.getUnsignedInt(bIndex);
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        } else {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = swapInt(bufferB.getInt(bIndex)) & 0xFFFFFFFFL;
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            short va = bufferA.getUnsignedByte(aIndex);
            short vb = bufferB.getUnsignedByte(bIndex);
            if (va > vb) {
                return 1;
            } else if (va < vb) {
                return -1;
            }
            aIndex ++;
            bIndex ++;
        }

        return aLen - bLen;
    }

    /**
     * The default implementation of {@link ByteBuf#indexOf(int, int, byte)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int indexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, value);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, value);
        }
    }

    /**
     * The default implementation of {@link ByteBuf#indexOf(int, int, ByteBufIndexFinder)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int indexOf(ByteBuf buffer, int fromIndex, int toIndex, ByteBufIndexFinder indexFinder) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, indexFinder);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, indexFinder);
        }
    }

    /**
     * Toggles the endianness of the specified 16-bit short integer.
     */
    public static short swapShort(short value) {
        return (short) (value << 8 | value >>> 8 & 0xff);
    }

    /**
     * Toggles the endianness of the specified 24-bit medium integer.
     */
    public static int swapMedium(int value) {
        int swapped = value << 16 & 0xff0000 | value & 0xff00 | value >>> 16 & 0xff;
        if ((swapped & 0x800000) != 0) {
            swapped |= 0xff000000;
        }
        return swapped;
    }

    /**
     * Toggles the endianness of the specified 32-bit integer.
     */
    public static int swapInt(int value) {
        return swapShort((short) value) <<  16 |
               swapShort((short) (value >>> 16)) & 0xffff;
    }

    /**
     * Toggles the endianness of the specified 64-bit long integer.
     */
    public static long swapLong(long value) {
        return (long) swapInt((int) value) <<  32 |
                      swapInt((int) (value >>> 32)) & 0xffffffffL;
    }

    private static int firstIndexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.max(fromIndex, 0);
        if (fromIndex >= toIndex || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    private static int lastIndexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.min(fromIndex, buffer.capacity());
        if (fromIndex < 0 || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    private static int firstIndexOf(
            ByteBuf buffer, int fromIndex, int toIndex, ByteBufIndexFinder indexFinder) {
        fromIndex = Math.max(fromIndex, 0);
        if (fromIndex >= toIndex || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (indexFinder.find(buffer, i)) {
                return i;
            }
        }

        return -1;
    }

    private static int lastIndexOf(
            ByteBuf buffer, int fromIndex, int toIndex, ByteBufIndexFinder indexFinder) {
        fromIndex = Math.min(fromIndex, buffer.capacity());
        if (fromIndex < 0 || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (indexFinder.find(buffer, i)) {
                return i;
            }
        }

        return -1;
    }

    static ByteBuffer encodeString(CharBuffer src, Charset charset) {
        final CharsetEncoder encoder = CharsetUtil.getEncoder(charset);
        final ByteBuffer dst = ByteBuffer.allocate(
                (int) ((double) src.remaining() * encoder.maxBytesPerChar()));
        try {
            CoderResult cr = encoder.encode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = encoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        dst.flip();
        return dst;
    }

    static String decodeString(ByteBuffer src, Charset charset) {
        final CharsetDecoder decoder = CharsetUtil.getDecoder(charset);
        final CharBuffer dst = CharBuffer.allocate(
                (int) ((double) src.remaining() * decoder.maxCharsPerByte()));
        try {
            CoderResult cr = decoder.decode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = decoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        return dst.flip().toString();
    }

    private Unpooled() {
        // Unused
    }
}