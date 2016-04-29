/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.plugins.credentials.common;

import com.cloudbees.plugins.credentials.Credentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;

/**
 * Credentials that have an unique ID that assists in retrieving the specific credential from a collection of
 * {@link Credentials}.
 * <p>
 * Note: This credential interface is a marker interface
 *
 * @since 1.5
 */
@LegacyMixIn(preferred = StandardCredentials.class)
public interface IdCredentials extends Credentials {
    /**
     * Returns the ID.
     *
     * @return the ID.
     */
    @NonNull
    String getId();

    /**
     * The contract implementations of {@link #equals(Object)} and {@link #hashCode()} that implementations of
     * this class must use as the basis for equality tests and as the hash code.
     *
     * @since 1.6
     */
    public static final class Helpers {
        /**
         * Standard {@link #equals(Object)} implementation.
         *
         * @param self the {@code this} reference.
         * @param o    the other object.
         * @return true if equal.
         */
        public static boolean equals(IdCredentials self, Object o) {
            if (self == o) {
                return true;
            }
            if (!(o instanceof IdCredentials)) {
                return false;
            }

            IdCredentials that = (IdCredentials) o;

            if (!self.getId().equals(that.getId())) {
                return false;
            }

            return true;
        }

        /**
         * The standard {@link #hashCode()} implementation.
         *
         * @param self the {@code this} reference.
         * @return the hash code.
         */
        public static int hashCode(IdCredentials self) {
            return self.getId().hashCode();
        }

        /**
         * Returns either the id or a generated new id if the supplied id is missing.
         *
         * @param id the supplied id.
         * @return either the id or a generated new id if the supplied id is missing.
         */
        @NonNull
        public static String fixEmptyId(@CheckForNull String id) {
            return StringUtils.isEmpty(id) ? UUID.randomUUID().toString() : id;
        }
    }
}
