/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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

package com.cloudbees.plugins.credentials.domains;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import java.io.Serializable;

/**
 * Base class for a specification against which {@link DomainRequirement}s can be tested.
 *
 * @since 1.5
 */
public abstract class DomainSpecification extends AbstractDescribableImpl<DomainSpecification>
        implements ExtensionPoint, Serializable {

    /**
     * Tests the scope against this specification.
     *
     * @param scope the scope to test.
     * @return the result of the test.
     */
    @NonNull
    public abstract Result test(@NonNull DomainRequirement scope);

    /**
     * The results of any {@link DomainRequirement} test against a {@link DomainSpecification}.
     *
     * @since 1.5
     */
    public static enum Result {

        /**
         * The specification does not know this kind of scope, keep checking other specifications for this scope.
         */
        UNKNOWN(false, false),

        /**
         * The specification knows this kind of scope and it is not a match.
         */
        NEGATIVE(true, false),

        /**
         * The specification knows this kind of scope and it is a match, keep checking other specifications.
         */
        PARTIAL(false, true),

        /**
         * The specification knows this kind of scope and it is a definitive match.
         */
        POSITIVE(true, true);

        /**
         * Is the result definitive, in other words can processing be short-cut.
         */
        private final boolean definitive;

        /**
         * Is the result a match.
         */
        private final boolean match;

        /**
         * Internal constructor.
         *
         * @param definitive Is the result definitive, in other words can processing be short-cut.
         * @param matches    Is the result a match.
         */
        Result(boolean definitive, boolean matches) {
            this.definitive = definitive;
            this.match = matches;
        }

        /**
         * Returns {@code true} if the result definitive, in other words can processing be short-cut.
         *
         * @return {@code true} if the result definitive, in other words can processing be short-cut.
         */
        public boolean isDefinitive() {
            return definitive;
        }

        /**
         * Returns {@code true} if the result a match.
         *
         * @return {@code true} if the result a match.
         */
        public boolean isMatch() {
            return match;
        }
    }
}
