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
package com.cloudbees.plugins.credentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Serializable;

/**
 * Something that matches credentials. Best practice is to
 * <ul>
 * <li>Also implement {@link CredentialsMatcher.CQL} and return a description of the matcher logic using the CQL
 * syntax detailed in {@link CredentialsMatchers#describe(CredentialsMatcher)}</li>
 * <li>Implement {@link Object#toString()}</li>
 * <li>Implement {@link Object#equals(Object)} and {@link Object#hashCode()}</li>
 * <li>Define a {@code serialVersionUID} field to ensure consistent serialization</li>
 * </ul>
 *
 * @since 1.5
 */
public interface CredentialsMatcher extends Serializable {
    /**
     * Evaluates the matcher for the specified credentials.
     *
     * @param item the specified credentials.
     * @return {@code true} if and only if the specified credentials match.
     */
    boolean matches(@NonNull Credentials item);

    /**
     * A mix-in interface to allow describing a credentials matcher.
     *
     * @since 2.1.0
     */
    interface CQL extends CredentialsMatcher {
        /**
         * Describes this matcher in terms of a java-bean style query language
         *
         * @return the description of the credentials matcher query or {@code null} if the matcher cannot be expressed
         * in CQL.
         */
        @CheckForNull
        String describe();
    }
}
