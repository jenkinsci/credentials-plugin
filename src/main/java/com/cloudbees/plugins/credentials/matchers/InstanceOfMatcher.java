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
package com.cloudbees.plugins.credentials.matchers;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Matches credentials that implement a specific type.
 *
 * @since 1.5
 */
public class InstanceOfMatcher implements CredentialsMatcher, CredentialsMatcher.CQL {
    /**
     * Standardize serialization.
     *
     * @since 2.1.0
     */
    private static final long serialVersionUID = 7841840317353807524L;
    /**
     * The type that the credentials must implement
     */
    @NonNull
    private final Class clazz;

    /**
     * Constructs a new instance.
     *
     * @param clazz the type that credentials must implement.
     */
    public InstanceOfMatcher(@NonNull Class clazz) {
        clazz.getClass(); // throw NPE if null
        this.clazz = clazz;
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(@NonNull Credentials item) {
        return clazz.isInstance(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        return String.format("(instanceof %s)", clazz.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InstanceOfMatcher that = (InstanceOfMatcher) o;

        return clazz.equals(that.clazz);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InstanceOfMatcher{");
        sb.append("clazz=").append(clazz);
        sb.append('}');
        return sb.toString();
    }
}
