/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc., Stephen Connolly.
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
package com.cloudbees.plugins.credentials.store;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.store.CredentialsStoreInterface;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Saveable;

import java.io.IOException;
import java.util.List;

/**
 * A store of {@link Credentials} that allows for modifying domains.
 *
 * @author Hosh Sadiq
 * @since 2.1.17
 */
public interface ModifiableDomainsCredentialsStore extends CredentialsStoreInterface, Saveable {
    /**
     * Adds a new {@link Domain} with seed credentials.
     *
     * @param domain      the domain.
     * @param credentials the initial credentials with which to populate the domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws java.io.IOException if the change could not be persisted.
     */
    boolean addDomain(@NonNull Domain domain, Credentials... credentials) throws IOException;

    /**
     * Adds a new {@link Domain} with seed credentials.
     *
     * @param domain      the domain.
     * @param credentials the initial credentials with which to populate the domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException;

    /**
     * Removes an existing {@link Domain} and all associated {@link Credentials}.
     *
     * @param domain the domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    boolean removeDomain(@NonNull Domain domain) throws IOException;

    /**
     * Updates an existing {@link Domain} keeping the existing associated {@link Credentials}.
     *
     * @param current     the domain to update.
     * @param replacement the new replacement domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException;
}
