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
package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Saveable;
import hudson.security.AccessControlled;

import java.io.IOException;

/**
 * A store of {@link Credentials}. Each {@link ModifiableItemsCredentialsStore} is associated with one and only one
 * {@link CredentialsProvider} though a {@link CredentialsProvider} may provide multiple {@link ModifiableItemsCredentialsStore}s
 * (for example a folder scoped {@link CredentialsProvider} may provide a {@link ModifiableItemsCredentialsStore} for each folder
 * or a user scoped {@link CredentialsProvider} may provide a {@link ModifiableItemsCredentialsStore} for each user).
 *
 * @author Stephen Connolly
 * @since 1.8
 */
interface ModifiableItemsCredentialsStore extends AccessControlled, Saveable {

    /**
     * Adds the specified {@link Credentials} within the specified {@link Domain} for this {@link
     * CredentialsStore}.
     *
     * @param domain      the domain.
     * @param credentials the credentials
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException;

    /**
     * Removes the specified {@link Credentials} from the specified {@link Domain} for this {@link
     * CredentialsStore}.
     *
     * @param domain      the domain.
     * @param credentials the credentials
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException;

    /**
     * Updates the specified {@link Credentials} from the specified {@link Domain} for this {@link
     * CredentialsStore} with the supplied replacement.
     *
     * @param domain      the domain.
     * @param current     the credentials to update.
     * @param replacement the new replacement credentials.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current, @NonNull Credentials replacement) throws IOException;
}
