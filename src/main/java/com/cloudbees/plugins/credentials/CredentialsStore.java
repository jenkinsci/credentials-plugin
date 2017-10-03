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
import com.cloudbees.plugins.credentials.store.BaseCredentialsStore;
import com.cloudbees.plugins.credentials.store.ModifiableCredentialsStore;
import com.cloudbees.plugins.credentials.store.ModifiableDomainsCredentialsStore;
import com.cloudbees.plugins.credentials.store.ModifiableItemsCredentialsStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.model.Saveable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A store of {@link Credentials}. Each {@link CredentialsStore} is associated with one and only one
 * {@link CredentialsProvider} though a {@link CredentialsProvider} may provide multiple {@link CredentialsStore}s
 * (for example a folder scoped {@link CredentialsProvider} may provide a {@link CredentialsStore} for each folder
 * or a user scoped {@link CredentialsProvider} may provide a {@link CredentialsStore} for each user).
 * This class has been deprecated. Please extend {@link BaseCredentialsStore} and implement on of the following
 * interfaces:
 * <ul>
 *     <li>{@link ModifiableCredentialsStore}</li>
 *     <li>{@link ModifiableDomainsCredentialsStore}</li>
 *     <li>{@link ModifiableItemsCredentialsStore}</li>
 * </ul>
 *
 * @author Stephen Connolly
 * @since 1.8
 */
public abstract class CredentialsStore extends BaseCredentialsStore implements ModifiableCredentialsStore {
    /**
     * Cache for {@link #isDomainsModifiable()}.
     */
    private transient Boolean domainsModifiable;

    /**
     * Constructor for use when the {@link CredentialsStore} is not an inner class of its {@link CredentialsProvider}.
     *
     * @param providerClass the {@link CredentialsProvider} class.
     * @since 2.0
     */
    public CredentialsStore(Class<? extends CredentialsProvider> providerClass) {
        super(providerClass);
    }

    /**
     * Constructor that auto-detects the {@link CredentialsProvider} that this {@link CredentialsStore} is associated
     * with by examining the outer classes until an outer class that implements {@link CredentialsProvider} is found.
     *
     * @since 2.0
     */
    @SuppressWarnings("unchecked")
    public CredentialsStore() {
        super();
    }

    /**
     * Identifies whether this {@link CredentialsStore} supports making changes to the list of domains or
     * whether it only supports a fixed set of domains (which may only be one domain).
     * <p>
     * Note: in order for implementations to return {@code true} all of the following methods must be overridden:
     * </p>
     * <ul>
     * <li>{@link #addDomain(Domain, java.util.List)}</li>
     * <li>{@link #removeDomain(Domain)}</li>
     * <li>{@link #updateDomain(Domain, Domain)} </li>
     * </ul>
     *
     * @return {@code true} iff {@link #addDomain(Domain, List)}
     * {@link #addDomain(Domain, Credentials...)}, {@link #removeDomain(Domain)}
     * and {@link #updateDomain(Domain, Domain)} are expected to work
     */
    @Deprecated
    public boolean isDomainsModifiable() {
        if (domainsModifiable == null) {
            try {
                domainsModifiable = isOverridden("addDomain", Domain.class, List.class)
                        && isOverridden("removeDomain", Domain.class)
                        && isOverridden("updateDomain", Domain.class, Domain.class);
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
        return domainsModifiable;
    }

    /**
     * Verifies if the specified method has been overridden by a subclass.
     *
     * @param name the name of the method.
     * @param args the arguments.
     * @return {@code true} if and only if the method is overridden by a subclass.
     * @throws NoSuchMethodException if something is seriously wrong.
     */
    @Deprecated
    private boolean isOverridden(String name, Class... args) throws NoSuchMethodException {
        return getClass().getMethod(name, args).getDeclaringClass() != CredentialsStore.class &&
                getClass().getMethod(name, args).getDeclaringClass() != BaseCredentialsStore.class;
    }

    /**
     * {@inheritDoc}
     */
    public final boolean addDomain(@NonNull Domain domain, Credentials... credentials) throws IOException {
        return addDomain(domain, Arrays.asList(credentials));
    }

    /**
     * {@inheritDoc}
     */
    public boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException {
        throw new UnsupportedOperationException("Implementation does not support adding domains");
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeDomain(@NonNull Domain domain) throws IOException {
        throw new UnsupportedOperationException("Implementation does not support removing domains");
    }

    /**
     * {@inheritDoc}
     */
    public boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
        throw new UnsupportedOperationException("Implementation does not support updating domains");
    }

    /**
     * Persists the state of this object into XML. Default implementation delegates to {@link #getContext()} if it
     * implements {@link Saveable} otherwise dropping back to a no-op.
     *
     * @see Saveable#save()
     * @since 2.1.9
     */
    @Override
    public void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        if (getContext() instanceof Saveable) {
            ((Saveable) getContext()).save();
        }
    }
}
