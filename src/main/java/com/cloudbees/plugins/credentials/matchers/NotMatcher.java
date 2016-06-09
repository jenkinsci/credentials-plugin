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
 * Inverts a matcher.
 *
 * @since 1.5
 */
public class NotMatcher implements CredentialsMatcher, CredentialsMatcher.CQL {
    /**
     * Standardize serialization.
     *
     * @since 2.1.0
     */
    private static final long serialVersionUID = 3301127941013284754L;
    /**
     * The matchers to match.
     */
    @NonNull
    private final CredentialsMatcher matcher;

    /**
     * Creates a new instance.
     *
     * @param matcher the matcher to invert the match of.
     */
    public NotMatcher(@NonNull CredentialsMatcher matcher) {
        matcher.getClass(); // throw NPE if null
        this.matcher = matcher;
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(@NonNull Credentials item) {
        return !matcher.matches(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        String description = matcher instanceof CQL ? ((CQL) matcher).describe() : null;
        return description == null ? null : description.startsWith("(") && description.endsWith(")") ? "!" + description : String.format("!(%s)", description);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return matcher.hashCode();
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

        NotMatcher that = (NotMatcher) o;

        return matcher.equals(that.matcher);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NotMatcher{");
        sb.append("matcher=").append(matcher);
        sb.append('}');
        return sb.toString();
    }
}
