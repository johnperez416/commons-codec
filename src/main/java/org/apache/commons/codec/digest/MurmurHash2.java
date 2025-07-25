/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.codec.digest;

import org.apache.commons.codec.binary.StringUtils;

/**
 * Implements the MurmurHash2 32-bit and 64-bit hash functions.
 *
 * <p>MurmurHash is a non-cryptographic hash function suitable for general
 * hash-based lookup. The name comes from two basic operations, multiply (MU)
 * and rotate (R), used in its inner loop. Unlike cryptographic hash functions,
 * it is not specifically designed to be difficult to reverse by an adversary,
 * making it unsuitable for cryptographic purposes.</p>
 *
 * <p>This contains a Java port of the 32-bit hash function {@code MurmurHash2}
 * and the 64-bit hash function {@code MurmurHash64A} from Austin Appleby's
 * original {@code c++} code in SMHasher.</p>
 *
 * <p>This is a re-implementation of the original C code plus some additional
 * features.</p>
 *
 * <p>This is public domain code with no copyrights. From home page of
 * <a href="https://github.com/aappleby/smhasher">SMHasher</a>:</p>
 *
 * <blockquote>
 * "All MurmurHash versions are public domain software, and the author
 * disclaims all copyright to their code."
 * </blockquote>
 *
 * @see <a href="https://en.wikipedia.org/wiki/MurmurHash">MurmurHash</a>
 * @see <a href="https://github.com/aappleby/smhasher/blob/master/src/MurmurHash2.cpp">
 *   Original MurmurHash2 c++ code</a>
 * @since 1.13
 */
public final class MurmurHash2 {

    // Constants for 32-bit variant
    private static final int M32 = 0x5bd1e995;
    private static final int R32 = 24;

    // Constants for 64-bit variant
    private static final long M64 = 0xc6a4a7935bd1e995L;
    private static final int R64 = 47;

    /**
     * Generates a 32-bit hash from byte array with the given length and a default seed value.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0x9747b28c;
     * int hash = MurmurHash2.hash32(data, length, seed);
     * </pre>
     *
     * @param data The input byte array
     * @param length The length of the array
     * @return The 32-bit hash
     * @see #hash32(byte[], int, int)
     */
    public static int hash32(final byte[] data, final int length) {
        return hash32(data, length, 0x9747b28c);
    }

    /**
     * Generates a 32-bit hash from byte array with the given length and seed.
     *
     * @param data The input byte array
     * @param length The length of the array
     * @param seed The initial seed value
     * @return The 32-bit hash
     */
    public static int hash32(final byte[] data, final int length, final int seed) {
        // Initialize the hash to a random value
        int h = seed ^ length;
        // Mix 4 bytes at a time into the hash
        final int nblocks = length >> 2;
        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = i << 2;
            int k = MurmurHash.getLittleEndianInt(data, index);
            k *= M32;
            k ^= k >>> R32;
            k *= M32;
            h *= M32;
            h ^= k;
        }
        // Handle the last few bytes of the input array
        final int index = nblocks << 2;
        switch (length - index) {
        case 3:
            h ^= (data[index + 2] & 0xff) << 16;
            // falls-through
        case 2:
            h ^= (data[index + 1] & 0xff) << 8;
            // falls-through
        case 1:
            h ^= data[index] & 0xff;
            h *= M32;
        }
        // Do a few final mixes of the hash to ensure the last few
        // bytes are well-incorporated.
        h ^= h >>> 13;
        h *= M32;
        h ^= h >>> 15;
        return h;
    }

    /**
     * Generates a 32-bit hash from a string with a default seed.
     * <p>
     * Before 1.14 the string was converted using default encoding.
     * Since 1.14 the string is converted to bytes using UTF-8 encoding.
     * </p>
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0x9747b28c;
     * byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash32(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text The input string
     * @return The 32-bit hash
     * @see #hash32(byte[], int, int)
     */
    public static int hash32(final String text) {
        final byte[] bytes = StringUtils.getBytesUtf8(text);
        return hash32(bytes, bytes.length);
    }

    /**
     * Generates a 32-bit hash from a substring with a default seed value.
     * The string is converted to bytes using the default encoding.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0x9747b28c;
     * byte[] bytes = text.substring(from, from + length).getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash32(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text The input string
     * @param from The starting index
     * @param length The length of the substring
     * @return The 32-bit hash
     * @see #hash32(byte[], int, int)
     */
    public static int hash32(final String text, final int from, final int length) {
        return hash32(text.substring(from, from + length));
    }

    /**
     * Generates a 64-bit hash from byte array with given length and a default seed value.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0xe17a1465;
     * int hash = MurmurHash2.hash64(data, length, seed);
     * </pre>
     *
     * @param data The input byte array
     * @param length The length of the array
     * @return The 64-bit hash
     * @see #hash64(byte[], int, int)
     */
    public static long hash64(final byte[] data, final int length) {
        return hash64(data, length, 0xe17a1465);
    }

    /**
     * Generates a 64-bit hash from byte array of the given length and seed.
     *
     * @param data The input byte array
     * @param length The length of the array
     * @param seed The initial seed value
     * @return The 64-bit hash of the given array
     */
    public static long hash64(final byte[] data, final int length, final int seed) {
        long h = seed & 0xffffffffL ^ length * M64;
        final int nblocks = length >> 3;
        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = i << 3;
            long k = MurmurHash.getLittleEndianLong(data, index);

            k *= M64;
            k ^= k >>> R64;
            k *= M64;

            h ^= k;
            h *= M64;
        }
        final int index = nblocks << 3;
        switch (length - index) {
        case 7:
            h ^= ((long) data[index + 6] & 0xff) << 48;
            // falls-through
        case 6:
            h ^= ((long) data[index + 5] & 0xff) << 40;
            // falls-through
        case 5:
            h ^= ((long) data[index + 4] & 0xff) << 32;
            // falls-through
        case 4:
            h ^= ((long) data[index + 3] & 0xff) << 24;
            // falls-through
        case 3:
            h ^= ((long) data[index + 2] & 0xff) << 16;
            // falls-through
        case 2:
            h ^= ((long) data[index + 1] & 0xff) << 8;
            // falls-through
        case 1:
            h ^= (long) data[index] & 0xff;
            h *= M64;
        }
        h ^= h >>> R64;
        h *= M64;
        h ^= h >>> R64;
        return h;
    }

    /**
     * Generates a 64-bit hash from a string with a default seed.
     * <p>
     * Before 1.14 the string was converted using default encoding.
     * Since 1.14 the string is converted to bytes using UTF-8 encoding.
     * </p>
     * <p>
     * This is a helper method that will produce the same result as:
     * </p>
     *
     * <pre>
     * int seed = 0xe17a1465;
     * byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash64(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text The input string
     * @return The 64-bit hash
     * @see #hash64(byte[], int, int)
     */
    public static long hash64(final String text) {
        final byte[] bytes = StringUtils.getBytesUtf8(text);
        return hash64(bytes, bytes.length);
    }

    /**
     * Generates a 64-bit hash from a substring with a default seed value.
     * The string is converted to bytes using the default encoding.
     * This is a helper method that will produce the same result as:
     *
     * <pre>
     * int seed = 0xe17a1465;
     * byte[] bytes = text.substring(from, from + length).getBytes(StandardCharsets.UTF_8);
     * int hash = MurmurHash2.hash64(bytes, bytes.length, seed);
     * </pre>
     *
     * @param text The input string
     * @param from The starting index
     * @param length The length of the substring
     * @return The 64-bit hash
     * @see #hash64(byte[], int, int)
     */
    public static long hash64(final String text, final int from, final int length) {
        return hash64(text.substring(from, from + length));
    }

    /** No instance methods. */
    private MurmurHash2() {
    }
}
