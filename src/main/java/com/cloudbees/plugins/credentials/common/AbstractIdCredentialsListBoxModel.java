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
package com.cloudbees.plugins.credentials.common;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.ListBoxModel;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Base class for {@link ListBoxModel} types that present a selection of credentials where the selection is keyed
 * by the credential's {@link com.cloudbees.plugins.credentials.common.IdCredentials#getId()}.
 *
 * @since 1.6
 */
public abstract class AbstractIdCredentialsListBoxModel<T extends AbstractIdCredentialsListBoxModel<T, C>,
        C extends IdCredentials>
        extends ListBoxModel {

    /**
     * Generate a description of the supplied credential.
     *
     * @param c the credential.
     * @return the description.
     */
    @NonNull
    protected abstract String describe(@NonNull C c);

    /**
     * Adds a single credential.
     *
     * @param u the credential to add.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> with(@CheckForNull C u) {
        if (u != null) {
            add(describe(u), u.getId());
        }
        return this;
    }

    /**
     * Adds an "empty" credential to signify selection of no credential.
     *
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withEmptySelection() {
        add(Messages.AbstractIdCredentialsListBoxModel_EmptySelection(), "");
        return this;
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull C... credentials) {
        return withMatching(CredentialsMatchers.always(), Arrays.asList(credentials));
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull Iterable<? extends C> credentials) {
        return withMatching(CredentialsMatchers.always(), credentials.iterator());
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull Iterator<? extends C> credentials) {
        return withMatching(CredentialsMatchers.always(), credentials);
    }

    /**
     * Adds the matching subset of suppled credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull C... credentials) {
        return withMatching(matcher, Arrays.asList(credentials));
    }

    /**
     * Adds the matching subset of suppled credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull Iterable<? extends C> credentials) {
        return withMatching(matcher, credentials.iterator());
    }

    /**
     * Adds the matching subset of suppled credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull Iterator<? extends C> credentials) {
        while (credentials.hasNext()) {
            C c = credentials.next();
            if (matcher.matches(c)) {
                with(c);
            }
        }
        return this;
    }

}
