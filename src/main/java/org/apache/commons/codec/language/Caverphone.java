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

package org.apache.commons.codec.language;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.StringEncoder;

/**
 * Encodes a string into a Caverphone 2.0 value. Delegate to a {@link Caverphone2} instance.
 *
 * This is an algorithm created by the Caversham Project at the University of Otago. It implements the Caverphone 2.0
 * algorithm:
 *
 * @see <a href="https://en.wikipedia.org/wiki/Caverphone">Wikipedia - Caverphone</a>
 * @see <a href="https://caversham.otago.ac.nz/files/working/ctp150804.pdf">Caverphone 2.0 specification</a>
 * @since 1.4
 * @deprecated 1.5 Replaced by {@link Caverphone2}, will be removed in 2.0.
 */
@Deprecated
public class Caverphone implements StringEncoder {

    /**
     * Delegate to a {@link Caverphone2} instance to avoid code duplication.
     */
    private final Caverphone2 encoder = new Caverphone2();

    /**
     * Constructs a new instance.
     */
    public Caverphone() {
        // empty
    }

    /**
     * Encodes the given String into a Caverphone value.
     *
     * @param source
     *            String the source string.
     * @return A Caverphone code for the given String.
     */
    public String caverphone(final String source) {
        return this.encoder.encode(source);
    }

    /**
     * Encodes an Object using the Caverphone algorithm. This method is provided in order to satisfy the requirements of
     * the Encoder interface, and will throw an EncoderException if the supplied object is not of type {@link String}.
     *
     * @param obj
     *            Object to encode.
     * @return An object (or type {@link String}) containing the Caverphone code which corresponds to the String
     *         supplied.
     * @throws EncoderException
     *             if the parameter supplied is not of type {@link String}.
     */
    @Override
    public Object encode(final Object obj) throws EncoderException {
        if (!(obj instanceof String)) {
            throw new EncoderException("Parameter supplied to Caverphone encode is not of type java.lang.String");
        }
        return caverphone((String) obj);
    }

    /**
     * Encodes a String using the Caverphone algorithm.
     *
     * @param str
     *            String object to encode.
     * @return The Caverphone code corresponding to the String supplied.
     */
    @Override
    public String encode(final String str) {
        return caverphone(str);
    }

    /**
     * Tests if the Caverphones of two strings are identical.
     *
     * @param str1
     *            First of two strings to compare.
     * @param str2
     *            Second of two strings to compare.
     * @return {@code true} if the Caverphones of these strings are identical, {@code false} otherwise.
     */
    public boolean isCaverphoneEqual(final String str1, final String str2) {
        return caverphone(str1).equals(caverphone(str2));
    }

}
