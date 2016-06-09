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
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Matches credentials that are {@link IdCredentials} and have the specified {@link CredentialsScope}(s).
 *
 * @since 1.5
 */
public class ScopeMatcher implements CredentialsMatcher, CredentialsMatcher.CQL {
    /**
     * Standardize serialization.
     *
     * @since 2.1.0
     */
    private static final long serialVersionUID = -7786779595366393177L;
    /**
     * The scopes to match.
     */
    @NonNull
    private final Set<CredentialsScope> scopes;

    /**
     * Constructs a new instance.
     *
     * @param scope the scope to match.
     */
    public ScopeMatcher(@NonNull CredentialsScope scope) {
        scope.getClass(); // throw NPE if null
        this.scopes = Collections.singleton(scope);
    }

    /**
     * Constructs a new instance.
     *
     * @param scopes the scopes to match.
     */
    public ScopeMatcher(@NonNull CredentialsScope... scopes) {
        this.scopes = EnumSet.copyOf(Arrays.asList(scopes));
    }

    /**
     * Constructs a new instance.
     *
     * @param scopes the scopes to match.
     */
    public ScopeMatcher(@NonNull Collection<CredentialsScope> scopes) {
        this.scopes = EnumSet.copyOf(scopes);
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(@NonNull Credentials item) {
        return scopes.contains(item.getScope());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        if (scopes.isEmpty()) {
            return "false";
        }
        StringBuilder sb = new StringBuilder("(");
        if (scopes.size() == 1) {
            sb.append("scope == ");
            sb.append(CredentialsScope.class.getName());
            sb.append('.');
            sb.append(scopes.iterator().next().name());
        } else {
            boolean first = true;
            for (CredentialsScope s : scopes) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" || ");
                }
                sb.append("(scope == ");
                sb.append(CredentialsScope.class.getName());
                sb.append('.');
                sb.append(s.name());
                sb.append(')');
            }
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return scopes.hashCode();
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

        ScopeMatcher that = (ScopeMatcher) o;

        return scopes.equals(that.scopes);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ScopeMatcher{");
        sb.append("scopes=").append(scopes);
        sb.append('}');
        return sb.toString();
    }
}
