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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Matches any of the supplied matchers.
 *
 * @since 1.5
 */
public class AnyOfMatcher implements CredentialsMatcher, CredentialsMatcher.CQL {
    /**
     * Standardize serialization.
     *
     * @since 2.1.0
     */
    private static final long serialVersionUID = 8214348092732916263L;
    /**
     * The matchers to match.
     */
    @NonNull
    private final List<CredentialsMatcher> matchers;

    /**
     * Creates a new instance.
     *
     * @param matchers the matchers to match.
     */
    public AnyOfMatcher(@CheckForNull List<CredentialsMatcher> matchers) {
        this.matchers = new ArrayList<CredentialsMatcher>(
                matchers == null ? Collections.<CredentialsMatcher>emptyList() : matchers);
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(@NonNull Credentials item) {
        for (CredentialsMatcher matcher : matchers) {
            if (matcher.matches(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public String describe() {
        if (matchers.isEmpty()) {
            return "true";
        }
        final StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (CredentialsMatcher m : matchers) {
            String description = m instanceof CQL ? ((CQL) m).describe() : null;
            if (description == null) {
                return null;
            }
            if (first) {
                first = false;
            } else {
                sb.append(" || ");
            }
            sb.append(description);
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return matchers.hashCode();
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

        AnyOfMatcher that = (AnyOfMatcher) o;

        return matchers.equals(that.matchers);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AnyMatcher{");
        sb.append("matchers=").append(matchers);
        sb.append('}');
        return sb.toString();
    }
}
