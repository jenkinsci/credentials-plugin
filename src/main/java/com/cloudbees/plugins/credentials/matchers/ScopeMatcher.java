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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Matches credentials that have the specified {@link CredentialsScope}(s).
 *
 * @since 1.5
 */
public record ScopeMatcher(@NonNull Set<CredentialsScope> scopes) implements CredentialsMatcher {
    /**
     * Constructs a new instance.
     *
     * @param scopes the scopes to match.
     */
    public ScopeMatcher {
        Objects.requireNonNull(scopes);
    }

    /**
     * Constructs a new instance.
     *
     * @param scope the scope to match.
     */
    public ScopeMatcher(@NonNull CredentialsScope scope) {
        this(Collections.singleton(Objects.requireNonNull(scope)));
    }

    /**
     * Constructs a new instance.
     *
     * @param scopes the scopes to match.
     */
    public ScopeMatcher(@NonNull CredentialsScope... scopes) {
        this(EnumSet.copyOf(Arrays.asList(scopes)));
    }

    /**
     * Constructs a new instance.
     *
     * @param scopes the scopes to match.
     */
    public ScopeMatcher(@NonNull Collection<CredentialsScope> scopes) {
        this(EnumSet.copyOf(scopes));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull Credentials item) {
        return scopes.contains(item.getScope());
    }
}
