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

package org.apache.commons.codec.binary;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.codec.CodecPolicy;

/**
 * Provides Base64 encoding and decoding as defined by <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>.
 *
 * <p>
 * This class implements section <cite>6.8. Base64 Content-Transfer-Encoding</cite> from RFC 2045 <cite>Multipurpose
 * Internet Mail Extensions (MIME) Part One: Format of Internet Message Bodies</cite> by Freed and Borenstein.
 * </p>
 * <p>
 * The class can be parameterized in the following manner with various constructors:
 * </p>
 * <ul>
 * <li>URL-safe mode: Default off.</li>
 * <li>Line length: Default 76. Line length that aren't multiples of 4 will still essentially end up being multiples of
 * 4 in the encoded data.
 * <li>Line separator: Default is CRLF ("\r\n")</li>
 * </ul>
 * <p>
 * The URL-safe parameter is only applied to encode operations. Decoding seamlessly handles both modes.
 * </p>
 * <p>
 * Since this class operates directly on byte streams, and not character streams, it is hard-coded to only
 * encode/decode character encodings which are compatible with the lower 127 ASCII chart (ISO-8859-1, Windows-1252,
 * UTF-8, etc).
 * </p>
 * <p>
 * This class is thread-safe.
 * </p>
 * <p>
 * You can configure instances with the {@link Builder}.
 * </p>
 * <pre>
 * Base64 base64 = Base64.builder()
 *   .setDecodingPolicy(DecodingPolicy.LENIENT) // default is lenient, null resets to default
 *   .setEncodeTable(customEncodeTable)         // default is built in, null resets to default
 *   .setLineLength(0)                          // default is none
 *   .setLineSeparator('\r', '\n')              // default is CR LF, null resets to default
 *   .setPadding('=')                           // default is =
 *   .setUrlSafe(false)                         // default is false
 *   .get()
 * </pre>
 *
 * @see <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>
 * @since 1.0
 */
public class Base64 extends BaseNCodec {

    /**
     * Builds {@link Base64} instances.
     *
     * @since 1.17.0
     */
    public static class Builder extends AbstractBuilder<Base64, Builder> {

        /**
         * Constructs a new instance.
         */
        public Builder() {
            super(STANDARD_ENCODE_TABLE);
        }

        @Override
        public Base64 get() {
            return new Base64(getLineLength(), getLineSeparator(), getPadding(), getEncodeTable(), getDecodingPolicy());
        }

        /**
         * Sets the URL-safe encoding policy.
         *
         * @param urlSafe URL-safe encoding policy, null resets to the default.
         * @return {@code this} instance.
         */
        public Builder setUrlSafe(final boolean urlSafe) {
            return setEncodeTable(toUrlSafeEncodeTable(urlSafe));
        }

    }

    /**
     * BASE64 characters are 6 bits in length.
     * They are formed by taking a block of 3 octets to form a 24-bit string,
     * which is converted into 4 BASE64 characters.
     */
    private static final int BITS_PER_ENCODED_BYTE = 6;
    private static final int BYTES_PER_UNENCODED_BLOCK = 3;
    private static final int BYTES_PER_ENCODED_BLOCK = 4;
    private static final int DECODING_TABLE_LENGTH = 256;

    /**
     * This array is a lookup table that translates 6-bit positive integer index values into their "Base64 Alphabet" equivalents as specified in Table 1 of RFC
     * 2045.
     * <p>
     * Thanks to "commons" project in ws.apache.org for this code. https://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     * </p>
     */
    // @formatter:off
    private static final byte[] STANDARD_ENCODE_TABLE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };
    /**
     * This is a copy of the STANDARD_ENCODE_TABLE above, but with + and / changed to - and _ to make the encoded Base64 results more URL-SAFE. This table is
     * only used when the Base64's mode is set to URL-SAFE.
     */
    // @formatter:off
    private static final byte[] URL_SAFE_ENCODE_TABLE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
    };
    // @formatter:on
    /**
     * This array is a lookup table that translates Unicode characters drawn from the "Base64 Alphabet" (as specified
     * in Table 1 of RFC 2045) into their 6-bit positive integer equivalents. Characters that are not in the Base64
     * alphabet but fall within the bounds of the array are translated to -1.
     * <p>
     * Note: '+' and '-' both decode to 62. '/' and '_' both decode to 63. This means decoder seamlessly handles both
     * URL_SAFE and STANDARD base64. (The encoder, on the other hand, needs to know ahead of time what to emit).
     * </p>
     * <p>
     * Thanks to "commons" project in ws.apache.org for this code.
     * https://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     * </p>
     */
    private static final byte[] DECODE_TABLE = {
        //   0   1   2   3   4   5   6   7   8   9   A   B   C   D   E   F
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, // 00-0f
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, // 10-1f
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, 62, -1, 63, // 20-2f + - /
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, // 30-3f 0-9
            -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, // 40-4f A-O
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63, // 50-5f P-Z _
            -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, // 60-6f a-o
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51                      // 70-7a p-z
    };

    /**
     * Base64 uses 6-bit fields.
     */
    /** Mask used to extract 6 bits, used when encoding */
    private static final int MASK_6_BITS = 0x3f;

    // The static final fields above are used for the original static byte[] methods on Base64.
    // The private member fields below are used with the new streaming approach, which requires
    // some state be preserved between calls of encode() and decode().

    /** Mask used to extract 4 bits, used when decoding final trailing character. */
    private static final int MASK_4_BITS = 0xf;
    /** Mask used to extract 2 bits, used when decoding final trailing character. */
    private static final int MASK_2_BITS = 0x3;

    /**
     * Creates a new Builder.
     *
     * @return a new Builder.
     * @since 1.17.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Decodes Base64 data into octets.
     * <p>
     * <strong>Note:</strong> this method seamlessly handles data encoded in URL-safe or normal mode.
     * </p>
     *
     * @param base64Data
     *            Byte array containing Base64 data
     * @return Array containing decoded data.
     */
    public static byte[] decodeBase64(final byte[] base64Data) {
        return new Base64().decode(base64Data);
    }

    /**
     * Decodes a Base64 String into octets.
     * <p>
     * <strong>Note:</strong> this method seamlessly handles data encoded in URL-safe or normal mode.
     * </p>
     *
     * @param base64String
     *            String containing Base64 data
     * @return Array containing decoded data.
     * @since 1.4
     */
    public static byte[] decodeBase64(final String base64String) {
        return new Base64().decode(base64String);
    }

    /**
     * Decodes a byte64-encoded integer according to crypto standards such as W3C's XML-Signature.
     *
     * @param array
     *            a byte array containing base64 character data
     * @return A BigInteger
     * @since 1.4
     */
    public static BigInteger decodeInteger(final byte[] array) {
        return new BigInteger(1, decodeBase64(array));
    }

    /**
     * Encodes binary data using the base64 algorithm but does not chunk the output.
     *
     * @param binaryData
     *            binary data to encode
     * @return byte[] containing Base64 characters in their UTF-8 representation.
     */
    public static byte[] encodeBase64(final byte[] binaryData) {
        return encodeBase64(binaryData, false);
    }

    /**
     * Encodes binary data using the base64 algorithm, optionally chunking the output into 76 character blocks.
     *
     * @param binaryData
     *            Array containing binary data to encode.
     * @param isChunked
     *            if {@code true} this encoder will chunk the base64 output into 76 character blocks
     * @return Base64-encoded data.
     * @throws IllegalArgumentException
     *             Thrown when the input array needs an output array bigger than {@link Integer#MAX_VALUE}
     */
    public static byte[] encodeBase64(final byte[] binaryData, final boolean isChunked) {
        return encodeBase64(binaryData, isChunked, false);
    }

    /**
     * Encodes binary data using the base64 algorithm, optionally chunking the output into 76 character blocks.
     *
     * @param binaryData
     *            Array containing binary data to encode.
     * @param isChunked
     *            if {@code true} this encoder will chunk the base64 output into 76 character blocks
     * @param urlSafe
     *            if {@code true} this encoder will emit - and _ instead of the usual + and / characters.
     *            <strong>Note: No padding is added when encoding using the URL-safe alphabet.</strong>
     * @return Base64-encoded data.
     * @throws IllegalArgumentException
     *             Thrown when the input array needs an output array bigger than {@link Integer#MAX_VALUE}
     * @since 1.4
     */
    public static byte[] encodeBase64(final byte[] binaryData, final boolean isChunked, final boolean urlSafe) {
        return encodeBase64(binaryData, isChunked, urlSafe, Integer.MAX_VALUE);
    }

    /**
     * Encodes binary data using the base64 algorithm, optionally chunking the output into 76 character blocks.
     *
     * @param binaryData
     *            Array containing binary data to encode.
     * @param isChunked
     *            if {@code true} this encoder will chunk the base64 output into 76 character blocks
     * @param urlSafe
     *            if {@code true} this encoder will emit - and _ instead of the usual + and / characters.
     *            <strong>Note: No padding is added when encoding using the URL-safe alphabet.</strong>
     * @param maxResultSize
     *            The maximum result size to accept.
     * @return Base64-encoded data.
     * @throws IllegalArgumentException
     *             Thrown when the input array needs an output array bigger than maxResultSize
     * @since 1.4
     */
    public static byte[] encodeBase64(final byte[] binaryData, final boolean isChunked,
                                      final boolean urlSafe, final int maxResultSize) {
        if (BinaryCodec.isEmpty(binaryData)) {
            return binaryData;
        }
        // Create this so can use the super-class method
        // Also ensures that the same roundings are performed by the ctor and the code
        final Base64 b64 = isChunked ? new Base64(urlSafe) : new Base64(0, CHUNK_SEPARATOR, urlSafe);
        final long len = b64.getEncodedLength(binaryData);
        if (len > maxResultSize) {
            throw new IllegalArgumentException("Input array too big, the output array would be bigger (" +
                len +
                ") than the specified maximum size of " +
                maxResultSize);
        }
        return b64.encode(binaryData);
    }

    /**
     * Encodes binary data using the base64 algorithm and chunks the encoded output into 76 character blocks
     *
     * @param binaryData
     *            binary data to encode
     * @return Base64 characters chunked in 76 character blocks
     */
    public static byte[] encodeBase64Chunked(final byte[] binaryData) {
        return encodeBase64(binaryData, true);
    }

    /**
     * Encodes binary data using the base64 algorithm but does not chunk the output.
     *
     * NOTE:  We changed the behavior of this method from multi-line chunking (commons-codec-1.4) to
     * single-line non-chunking (commons-codec-1.5).
     *
     * @param binaryData
     *            binary data to encode
     * @return String containing Base64 characters.
     * @since 1.4 (NOTE:  1.4 chunked the output, whereas 1.5 does not).
     */
    public static String encodeBase64String(final byte[] binaryData) {
        return StringUtils.newStringUsAscii(encodeBase64(binaryData, false));
    }

    /**
     * Encodes binary data using a URL-safe variation of the base64 algorithm but does not chunk the output. The
     * url-safe variation emits - and _ instead of + and / characters.
     * <strong>Note: No padding is added.</strong>
     * @param binaryData
     *            binary data to encode
     * @return byte[] containing Base64 characters in their UTF-8 representation.
     * @since 1.4
     */
    public static byte[] encodeBase64URLSafe(final byte[] binaryData) {
        return encodeBase64(binaryData, false, true);
    }

    /**
     * Encodes binary data using a URL-safe variation of the base64 algorithm but does not chunk the output. The
     * url-safe variation emits - and _ instead of + and / characters.
     * <strong>Note: No padding is added.</strong>
     * @param binaryData
     *            binary data to encode
     * @return String containing Base64 characters
     * @since 1.4
     */
    public static String encodeBase64URLSafeString(final byte[] binaryData) {
        return StringUtils.newStringUsAscii(encodeBase64(binaryData, false, true));
    }

    /**
     * Encodes to a byte64-encoded integer according to crypto standards such as W3C's XML-Signature.
     *
     * @param bigInteger
     *            a BigInteger
     * @return A byte array containing base64 character data
     * @throws NullPointerException
     *             if null is passed in
     * @since 1.4
     */
    public static byte[] encodeInteger(final BigInteger bigInteger) {
        Objects.requireNonNull(bigInteger, "bigInteger");
        return encodeBase64(toIntegerBytes(bigInteger), false);
    }

    /**
     * Tests a given byte array to see if it contains only valid characters within the Base64 alphabet. Currently the
     * method treats whitespace as valid.
     *
     * @param arrayOctet
     *            byte array to test
     * @return {@code true} if all bytes are valid characters in the Base64 alphabet or if the byte array is empty;
     *         {@code false}, otherwise
     * @deprecated 1.5 Use {@link #isBase64(byte[])}, will be removed in 2.0.
     */
    @Deprecated
    public static boolean isArrayByteBase64(final byte[] arrayOctet) {
        return isBase64(arrayOctet);
    }

    /**
     * Returns whether or not the {@code octet} is in the base 64 alphabet.
     *
     * @param octet
     *            The value to test
     * @return {@code true} if the value is defined in the base 64 alphabet, {@code false} otherwise.
     * @since 1.4
     */
    public static boolean isBase64(final byte octet) {
        return octet == PAD_DEFAULT || octet >= 0 && octet < DECODE_TABLE.length && DECODE_TABLE[octet] != -1;
    }

    /**
     * Tests a given byte array to see if it contains only valid characters within the Base64 alphabet. Currently the
     * method treats whitespace as valid.
     *
     * @param arrayOctet
     *            byte array to test
     * @return {@code true} if all bytes are valid characters in the Base64 alphabet or if the byte array is empty;
     *         {@code false}, otherwise
     * @since 1.5
     */
    public static boolean isBase64(final byte[] arrayOctet) {
        for (final byte element : arrayOctet) {
            if (!isBase64(element) && !Character.isWhitespace(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tests a given String to see if it contains only valid characters within the Base64 alphabet. Currently the
     * method treats whitespace as valid.
     *
     * @param base64
     *            String to test
     * @return {@code true} if all characters in the String are valid characters in the Base64 alphabet or if
     *         the String is empty; {@code false}, otherwise
     *  @since 1.5
     */
    public static boolean isBase64(final String base64) {
        return isBase64(StringUtils.getBytesUtf8(base64));
    }

    /**
     * Returns a byte-array representation of a {@code BigInteger} without sign bit.
     *
     * @param bigInt
     *            {@code BigInteger} to be converted
     * @return a byte array representation of the BigInteger parameter
     */
    static byte[] toIntegerBytes(final BigInteger bigInt) {
        int bitlen = bigInt.bitLength();
        // round bitlen
        bitlen = bitlen + 7 >> 3 << 3;
        final byte[] bigBytes = bigInt.toByteArray();

        if (bigInt.bitLength() % 8 != 0 && bigInt.bitLength() / 8 + 1 == bitlen / 8) {
            return bigBytes;
        }
        // set up params for copying everything but sign bit
        int startSrc = 0;
        int len = bigBytes.length;

        // if bigInt is exactly byte-aligned, just skip signbit in copy
        if (bigInt.bitLength() % 8 == 0) {
            startSrc = 1;
            len--;
        }
        final int startDst = bitlen / 8 - len; // to pad w/ nulls as per spec
        final byte[] resizedBytes = new byte[bitlen / 8];
        System.arraycopy(bigBytes, startSrc, resizedBytes, startDst, len);
        return resizedBytes;
    }

    private static byte[] toUrlSafeEncodeTable(final boolean urlSafe) {
        return urlSafe ? URL_SAFE_ENCODE_TABLE : STANDARD_ENCODE_TABLE;
    }

    /**
     * Encode table to use: either STANDARD or URL_SAFE or custom.
     * Note: the DECODE_TABLE above remains static because it is able
     * to decode both STANDARD and URL_SAFE streams, but the encodeTable must be a member variable so we can switch
     * between the two modes.
     */
    private final byte[] encodeTable;

    /**
     * Decode table to use.
     */
    private final byte[] decodeTable;

    /**
     * Line separator for encoding. Not used when decoding. Only used if lineLength &gt; 0.
     */
    private final byte[] lineSeparator;

    /**
     * Convenience variable to help us determine when our buffer is going to run out of room and needs resizing.
     * {@code encodeSize = 4 + lineSeparator.length;}
     */
    private final int encodeSize;

    private final boolean isUrlSafe;

    /**
     * Constructs a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     * <p>
     * When encoding the line length is 0 (no chunking), and the encoding table is STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     */
    public Base64() {
        this(0);
    }

    /**
     * Constructs a Base64 codec used for decoding (all modes) and encoding in the given URL-safe mode.
     * <p>
     * When encoding the line length is 76, the line separator is CRLF, and the encoding table is STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param urlSafe
     *            if {@code true}, URL-safe encoding is used. In most cases this should be set to
     *            {@code false}.
     * @since 1.4
     */
    public Base64(final boolean urlSafe) {
        this(MIME_CHUNK_SIZE, CHUNK_SEPARATOR, urlSafe);
    }

    /**
     * Constructs a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     * <p>
     * When encoding the line length is given in the constructor, the line separator is CRLF, and the encoding table is
     * STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength
     *            Each line of encoded data will be at most of the given length (rounded down to the nearest multiple of
     *            4). If lineLength &lt;= 0, then the output will not be divided into lines (chunks). Ignored when
     *            decoding.
     * @since 1.4
     */
    public Base64(final int lineLength) {
        this(lineLength, CHUNK_SEPARATOR);
    }

    /**
     * Constructs a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     * <p>
     * When encoding the line length and line separator are given in the constructor, and the encoding table is
     * STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength
     *            Each line of encoded data will be at most of the given length (rounded down to the nearest multiple of
     *            4). If lineLength &lt;= 0, then the output will not be divided into lines (chunks). Ignored when
     *            decoding.
     * @param lineSeparator
     *            Each line of encoded data will end with this sequence of bytes.
     * @throws IllegalArgumentException
     *             Thrown when the provided lineSeparator included some base64 characters.
     * @since 1.4
     */
    public Base64(final int lineLength, final byte[] lineSeparator) {
        this(lineLength, lineSeparator, false);
    }

    /**
     * Constructs a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     * <p>
     * When encoding the line length and line separator are given in the constructor, and the encoding table is
     * STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength
     *            Each line of encoded data will be at most of the given length (rounded down to the nearest multiple of
     *            4). If lineLength &lt;= 0, then the output will not be divided into lines (chunks). Ignored when
     *            decoding.
     * @param lineSeparator
     *            Each line of encoded data will end with this sequence of bytes.
     * @param urlSafe
     *            Instead of emitting '+' and '/' we emit '-' and '_' respectively. urlSafe is only applied to encode
     *            operations. Decoding seamlessly handles both modes.
     *            <strong>Note: No padding is added when using the URL-safe alphabet.</strong>
     * @throws IllegalArgumentException
     *             Thrown when the {@code lineSeparator} contains Base64 characters.
     * @since 1.4
     */
    public Base64(final int lineLength, final byte[] lineSeparator, final boolean urlSafe) {
        this(lineLength, lineSeparator, PAD_DEFAULT, toUrlSafeEncodeTable(urlSafe), DECODING_POLICY_DEFAULT);
    }

    /**
     * Constructs a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     * <p>
     * When encoding the line length and line separator are given in the constructor, and the encoding table is
     * STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength
     *            Each line of encoded data will be at most of the given length (rounded down to the nearest multiple of
     *            4). If lineLength &lt;= 0, then the output will not be divided into lines (chunks). Ignored when
     *            decoding.
     * @param lineSeparator
     *            Each line of encoded data will end with this sequence of bytes.
     * @param urlSafe
     *            Instead of emitting '+' and '/' we emit '-' and '_' respectively. urlSafe is only applied to encode
     *            operations. Decoding seamlessly handles both modes.
     *            <strong>Note: No padding is added when using the URL-safe alphabet.</strong>
     * @param decodingPolicy The decoding policy.
     * @throws IllegalArgumentException
     *             Thrown when the {@code lineSeparator} contains Base64 characters.
     * @since 1.15
     */
    public Base64(final int lineLength, final byte[] lineSeparator, final boolean urlSafe, final CodecPolicy decodingPolicy) {
        this(lineLength, lineSeparator, PAD_DEFAULT, toUrlSafeEncodeTable(urlSafe), decodingPolicy);
    }

    /**
     * Constructs a Base64 codec used for decoding (all modes) and encoding in URL-unsafe mode.
     * <p>
     * When encoding the line length and line separator are given in the constructor, and the encoding table is STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * Line lengths that aren't multiples of 4 will still essentially end up being multiples of 4 in the encoded data.
     * </p>
     * <p>
     * When decoding all variants are supported.
     * </p>
     *
     * @param lineLength     Each line of encoded data will be at most of the given length (rounded down to the nearest multiple of 4). If lineLength &lt;= 0,
     *                       then the output will not be divided into lines (chunks). Ignored when decoding.
     * @param lineSeparator  Each line of encoded data will end with this sequence of bytes; the constructor makes a defensive copy. May be null.
     * @param padding        padding byte.
     * @param encodeTable    The manual encodeTable - a byte array of 64 chars.
     * @param decodingPolicy The decoding policy.
     * @throws IllegalArgumentException Thrown when the {@code lineSeparator} contains Base64 characters.
     */
    private Base64(final int lineLength, final byte[] lineSeparator, final byte padding, final byte[] encodeTable, final CodecPolicy decodingPolicy) {
        super(BYTES_PER_UNENCODED_BLOCK, BYTES_PER_ENCODED_BLOCK, lineLength, toLength(lineSeparator), padding, decodingPolicy);
        Objects.requireNonNull(encodeTable, "encodeTable");
        if (encodeTable.length != STANDARD_ENCODE_TABLE.length) {
            throw new IllegalArgumentException("encodeTable must have exactly 64 entries.");
        }
        // same array first or equal contents second
        this.isUrlSafe = encodeTable == URL_SAFE_ENCODE_TABLE || Arrays.equals(encodeTable, URL_SAFE_ENCODE_TABLE);
        if (encodeTable == STANDARD_ENCODE_TABLE || this.isUrlSafe) {
            decodeTable = DECODE_TABLE;
            // No need of a defensive copy of an internal table.
            this.encodeTable = encodeTable;
        } else {
            this.encodeTable = encodeTable.clone();
            this.decodeTable = calculateDecodeTable(this.encodeTable);
        }
        // TODO could be simplified if there is no requirement to reject invalid line sep when length <=0
        // @see test case Base64Test.testConstructors()
        if (lineSeparator != null) {
            final byte[] lineSeparatorCopy = lineSeparator.clone();
            if (containsAlphabetOrPad(lineSeparatorCopy)) {
                final String sep = StringUtils.newStringUtf8(lineSeparatorCopy);
                throw new IllegalArgumentException("lineSeparator must not contain base64 characters: [" + sep + "]");
            }
            if (lineLength > 0) { // null line-sep forces no chunking rather than throwing IAE
                this.encodeSize = BYTES_PER_ENCODED_BLOCK + lineSeparatorCopy.length;
                this.lineSeparator = lineSeparatorCopy;
            } else {
                this.encodeSize = BYTES_PER_ENCODED_BLOCK;
                this.lineSeparator = null;
            }
        } else {
            this.encodeSize = BYTES_PER_ENCODED_BLOCK;
            this.lineSeparator = null;
        }
    }

    /**
     * Calculates a decode table for a given encode table.
     *
     * @param encodeTable that is used to determine decode lookup table
     * @return decodeTable
     */
    private byte[] calculateDecodeTable(final byte[] encodeTable) {
        final byte[] decodeTable = new byte[DECODING_TABLE_LENGTH];
        Arrays.fill(decodeTable, (byte) -1);
        for (int i = 0; i < encodeTable.length; i++) {
            decodeTable[encodeTable[i]] = (byte) i;
        }
        return decodeTable;
    }

    /**
     * <p>
     * Decodes all of the provided data, starting at inPos, for inAvail bytes. Should be called at least twice: once
     * with the data to decode, and once with inAvail set to "-1" to alert decoder that EOF has been reached. The "-1"
     * call is not necessary when decoding, but it doesn't hurt, either.
     * </p>
     * <p>
     * Ignores all non-base64 characters. This is how chunked (for example 76 character) data is handled, since CR and LF are
     * silently ignored, but has implications for other bytes, too. This method subscribes to the garbage-in,
     * garbage-out philosophy: it will not check the provided data for validity.
     * </p>
     * <p>
     * Thanks to "commons" project in ws.apache.org for the bitwise operations, and general approach.
     * https://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     * </p>
     *
     * @param input
     *            byte[] array of ASCII data to base64 decode.
     * @param inPos
     *            Position to start reading data from.
     * @param inAvail
     *            Amount of bytes available from input for decoding.
     * @param context
     *            the context to be used
     */
    @Override
    void decode(final byte[] input, int inPos, final int inAvail, final Context context) {
        if (context.eof) {
            return;
        }
        if (inAvail < 0) {
            context.eof = true;
        }
        final int decodeSize = this.encodeSize - 1;
        for (int i = 0; i < inAvail; i++) {
            final byte[] buffer = ensureBufferSize(decodeSize, context);
            final byte b = input[inPos++];
            if (b == pad) {
                // We're done.
                context.eof = true;
                break;
            }
            if (b >= 0 && b < decodeTable.length) {
                final int result = decodeTable[b];
                if (result >= 0) {
                    context.modulus = (context.modulus + 1) % BYTES_PER_ENCODED_BLOCK;
                    context.ibitWorkArea = (context.ibitWorkArea << BITS_PER_ENCODED_BYTE) + result;
                    if (context.modulus == 0) {
                        buffer[context.pos++] = (byte) (context.ibitWorkArea >> 16 & MASK_8BITS);
                        buffer[context.pos++] = (byte) (context.ibitWorkArea >> 8 & MASK_8BITS);
                        buffer[context.pos++] = (byte) (context.ibitWorkArea & MASK_8BITS);
                    }
                }
            }
        }

        // Two forms of EOF as far as base64 decoder is concerned: actual
        // EOF (-1) and first time '=' character is encountered in stream.
        // This approach makes the '=' padding characters completely optional.
        if (context.eof && context.modulus != 0) {
            final byte[] buffer = ensureBufferSize(decodeSize, context);

            // We have some spare bits remaining
            // Output all whole multiples of 8 bits and ignore the rest
            switch (context.modulus) {
//              case 0 : // impossible, as excluded above
                case 1 : // 6 bits - either ignore entirely, or raise an exception
                    validateTrailingCharacter();
                    break;
                case 2 : // 12 bits = 8 + 4
                    validateCharacter(MASK_4_BITS, context);
                    context.ibitWorkArea = context.ibitWorkArea >> 4; // dump the extra 4 bits
                    buffer[context.pos++] = (byte) (context.ibitWorkArea & MASK_8BITS);
                    break;
                case 3 : // 18 bits = 8 + 8 + 2
                    validateCharacter(MASK_2_BITS, context);
                    context.ibitWorkArea = context.ibitWorkArea >> 2; // dump 2 bits
                    buffer[context.pos++] = (byte) (context.ibitWorkArea >> 8 & MASK_8BITS);
                    buffer[context.pos++] = (byte) (context.ibitWorkArea & MASK_8BITS);
                    break;
                default:
                    throw new IllegalStateException("Impossible modulus " + context.modulus);
            }
        }
    }

    /**
     * <p>
     * Encodes all of the provided data, starting at inPos, for inAvail bytes. Must be called at least twice: once with
     * the data to encode, and once with inAvail set to "-1" to alert encoder that EOF has been reached, to flush last
     * remaining bytes (if not multiple of 3).
     * </p>
     * <p><strong>Note: No padding is added when encoding using the URL-safe alphabet.</strong></p>
     * <p>
     * Thanks to "commons" project in ws.apache.org for the bitwise operations, and general approach.
     * https://svn.apache.org/repos/asf/webservices/commons/trunk/modules/util/
     * </p>
     *
     * @param in
     *            byte[] array of binary data to base64 encode.
     * @param inPos
     *            Position to start reading data from.
     * @param inAvail
     *            Amount of bytes available from input for encoding.
     * @param context
     *            the context to be used
     */
    @Override
    void encode(final byte[] in, int inPos, final int inAvail, final Context context) {
        if (context.eof) {
            return;
        }
        // inAvail < 0 is how we're informed of EOF in the underlying data we're
        // encoding.
        if (inAvail < 0) {
            context.eof = true;
            if (0 == context.modulus && lineLength == 0) {
                return; // no leftovers to process and not using chunking
            }
            final byte[] buffer = ensureBufferSize(encodeSize, context);
            final int savedPos = context.pos;
            switch (context.modulus) { // 0-2
                case 0 : // nothing to do here
                    break;
                case 1 : // 8 bits = 6 + 2
                    // top 6 bits:
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea >> 2 & MASK_6_BITS];
                    // remaining 2:
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea << 4 & MASK_6_BITS];
                    // URL-SAFE skips the padding to further reduce size.
                    if (encodeTable == STANDARD_ENCODE_TABLE) {
                        buffer[context.pos++] = pad;
                        buffer[context.pos++] = pad;
                    }
                    break;

                case 2 : // 16 bits = 6 + 6 + 4
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea >> 10 & MASK_6_BITS];
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea >> 4 & MASK_6_BITS];
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea << 2 & MASK_6_BITS];
                    // URL-SAFE skips the padding to further reduce size.
                    if (encodeTable == STANDARD_ENCODE_TABLE) {
                        buffer[context.pos++] = pad;
                    }
                    break;
                default:
                    throw new IllegalStateException("Impossible modulus " + context.modulus);
            }
            context.currentLinePos += context.pos - savedPos; // keep track of current line position
            // if currentPos == 0 we are at the start of a line, so don't add CRLF
            if (lineLength > 0 && context.currentLinePos > 0) {
                System.arraycopy(lineSeparator, 0, buffer, context.pos, lineSeparator.length);
                context.pos += lineSeparator.length;
            }
        } else {
            for (int i = 0; i < inAvail; i++) {
                final byte[] buffer = ensureBufferSize(encodeSize, context);
                context.modulus = (context.modulus + 1) % BYTES_PER_UNENCODED_BLOCK;
                int b = in[inPos++];
                if (b < 0) {
                    b += 256;
                }
                context.ibitWorkArea = (context.ibitWorkArea << 8) + b; // BITS_PER_BYTE
                if (0 == context.modulus) { // 3 bytes = 24 bits = 4 * 6 bits to extract
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea >> 18 & MASK_6_BITS];
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea >> 12 & MASK_6_BITS];
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea >> 6 & MASK_6_BITS];
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea & MASK_6_BITS];
                    context.currentLinePos += BYTES_PER_ENCODED_BLOCK;
                    if (lineLength > 0 && lineLength <= context.currentLinePos) {
                        System.arraycopy(lineSeparator, 0, buffer, context.pos, lineSeparator.length);
                        context.pos += lineSeparator.length;
                        context.currentLinePos = 0;
                    }
                }
            }
        }
    }

    /**
     * Gets the line separator (for testing only).
     *
     * @return the line separator.
     */
    byte[] getLineSeparator() {
        return lineSeparator;
    }

    /**
     * Returns whether or not the {@code octet} is in the Base64 alphabet.
     *
     * @param octet
     *            The value to test
     * @return {@code true} if the value is defined in the Base64 alphabet {@code false} otherwise.
     */
    @Override
    protected boolean isInAlphabet(final byte octet) {
        return octet >= 0 && octet < decodeTable.length && decodeTable[octet] != -1;
    }

    /**
     * Returns our current encode mode. True if we're URL-safe, false otherwise.
     *
     * @return true if we're in URL-safe mode, false otherwise.
     * @since 1.4
     */
    public boolean isUrlSafe() {
        return isUrlSafe;
    }

    /**
     * Validates whether decoding the final trailing character is possible in the context
     * of the set of possible base 64 values.
     * <p>
     * The character is valid if the lower bits within the provided mask are zero. This
     * is used to test the final trailing base-64 digit is zero in the bits that will be discarded.
     * </p>
     *
     * @param emptyBitsMask The mask of the lower bits that should be empty
     * @param context the context to be used
     * @throws IllegalArgumentException if the bits being checked contain any non-zero value
     */
    private void validateCharacter(final int emptyBitsMask, final Context context) {
        if (isStrictDecoding() && (context.ibitWorkArea & emptyBitsMask) != 0) {
            throw new IllegalArgumentException(
                "Strict decoding: Last encoded character (before the paddings if any) is a valid " +
                "base 64 alphabet but not a possible encoding. " +
                "Expected the discarded bits from the character to be zero.");
        }
    }

    /**
     * Validates whether decoding allows an entire final trailing character that cannot be
     * used for a complete byte.
     *
     * @throws IllegalArgumentException if strict decoding is enabled
     */
    private void validateTrailingCharacter() {
        if (isStrictDecoding()) {
            throw new IllegalArgumentException(
                "Strict decoding: Last encoded character (before the paddings if any) is a valid " +
                "base 64 alphabet but not a possible encoding. " +
                "Decoding requires at least two trailing 6-bit characters to create bytes.");
        }
    }

}
