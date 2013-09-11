/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc., Stephen Connolly.
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
import hudson.model.ModelObject;
import hudson.security.AccessDeniedException2;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A store of {@link Credentials}.
 *
 * @author Stephen Connolly
 * @since 2.0
 */
public abstract class CredentialsStore {

    /**
     * Returns the context within which this store operates. Credentials in this store will be available to
     * child contexts (unless {@link CredentialsScope#SYSTEM} is valid for the store) but will not be available to
     * parent contexts.
     *
     * @return the context within which this store operates.
     */
    public abstract ModelObject getContext();

    /**
     * Checks if the current security principal has this permission.
     * <p/>
     * <p/>
     * This is just a convenience function.
     *
     * @throws org.acegisecurity.AccessDeniedException
     *          if the user doesn't have the permission.
     */
    public final void checkPermission(@NonNull Permission p) {
        Authentication a = Jenkins.getAuthentication();
        if (!hasPermission(a, p)) {
            throw new AccessDeniedException2(a, p);
        }
    }

    /**
     * Checks if the current security principal has this permission.
     *
     * @return false
     *         if the user doesn't have the permission.
     */
    public final boolean hasPermission(@NonNull Permission p) {
        return hasPermission(Jenkins.getAuthentication(), p);
    }

    /**
     * Checks if the given principle has the given permission.
     */
    public abstract boolean hasPermission(@NonNull Authentication a, @NonNull Permission permission);

    /**
     * Returns all the {@link com.cloudbees.plugins.credentials.domains.Domain}s that this credential provider has.
     * Most implementers of {@link CredentialsStore} will probably want to override this method.
     *
     * @return the list of domains.
     */
    @NonNull
    public List<Domain> getDomains() {
        return Collections.singletonList(Domain.global());
    }

    /**
     * Identifies whether this {@link CredentialsStore} supports making changes to the list of domains or
     * whether it only supports a fixed set of domains (which may only be one domain).
     * <p/>
     * Note: Implementers that return {@code true} are expected to override the following methods:
     * <ul>
     * <li>{@link #getDomains}</li>
     * <li>{@link #addDomain(com.cloudbees.plugins.credentials.domains.Domain, java.util.List)}</li>
     * <li>{@link #addDomain(com.cloudbees.plugins.credentials.domains.Domain, Credentials...)}</li>
     * <li>{@link #removeDomain(com.cloudbees.plugins.credentials.domains.Domain)}</li>
     * </ul>
     *
     * @return {@code true} iff {@link #addDomain(com.cloudbees.plugins.credentials.domains.Domain, java.util.List)}
     *         {@link #addDomain(com.cloudbees.plugins.credentials.domains.Domain, Credentials...)}
     *         and {@link #removeDomain(com.cloudbees.plugins.credentials.domains.Domain)} are expected to work
     */
    public abstract boolean isDomainsModifiable();

    /**
     * Returns an unmodifiable list of credentials for the specified domain.
     *
     * @param domain the domain.
     * @return the possibly empty (e.g. for an unknown {@link Domain}) unmodifiable list of credentials for the
     *         specified domain.
     */
    @NonNull
    public abstract List<Credentials> getCredentials(@NonNull Domain domain);

    /**
     * Adds a new {@link Domain} with seed credentials.
     *
     * @param domain      the domain.
     * @param credentials the initial credentials with which to populate the domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws java.io.IOException if the change could not be persisted.
     */
    public final boolean addDomain(@NonNull Domain domain, Credentials... credentials) throws IOException {
        return addDomain(domain, Arrays.asList(credentials));
    }

    /**
     * Adds a new {@link Domain} with seed credentials.
     *
     * @param domain      the domain.
     * @param credentials the initial credentials with which to populate the domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    public boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException {
        throw new UnsupportedOperationException("Implementation does not support adding domains");
    }

    /**
     * Removes an existing {@link Domain} and all associated {@link Credentials}.
     *
     * @param domain the domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    public boolean removeDomain(@NonNull Domain domain) throws IOException {
        throw new UnsupportedOperationException("Implementation does not support removing domains");
    }

    /**
     * Adds the specified {@link Credentials} within the specified {@link Domain} for this {@link
     * CredentialsStore}.
     *
     * @param domain      the domain.
     * @param credentials the credentials
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    public abstract boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException;

    /**
     * Removes the specified {@link Credentials} from the specified {@link Domain} for this {@link
     * CredentialsStore}.
     *
     * @param domain      the domain.
     * @param credentials the credentials
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    public abstract boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials)
            throws IOException;

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
    public abstract boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                              @NonNull Credentials replacement)
            throws IOException;

}
