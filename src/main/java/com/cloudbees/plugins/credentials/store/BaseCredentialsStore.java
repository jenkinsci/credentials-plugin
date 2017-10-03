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

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Util;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AccessDeniedException2;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.net.URI;
import java.util.*;

/**
 * A store of {@link Credentials}. Each {@link BaseCredentialsStore} is associated with one and only one
 * {@link CredentialsProvider} though a {@link CredentialsProvider} may provide multiple {@link BaseCredentialsStore}s
 * (for example a folder scoped {@link CredentialsProvider} may provide a {@link BaseCredentialsStore} for each folder
 * or a user scoped {@link CredentialsProvider} may provide a {@link BaseCredentialsStore} for each user).
 *
 * @author Stephen Connolly
 * @since 1.8
 */
public abstract class BaseCredentialsStore implements CredentialsStoreInterface, AccessControlled {

    /**
     * The {@link CredentialsProvider} class.
     *
     * @since 2.0
     */
    private final Class<? extends CredentialsProvider> providerClass;

    /**
     * Constructor for use when the {@link BaseCredentialsStore} is not an inner class of its {@link CredentialsProvider}.
     *
     * @param providerClass the {@link CredentialsProvider} class.
     * @since 2.0
     */
    public BaseCredentialsStore(Class<? extends CredentialsProvider> providerClass) {
        this.providerClass = providerClass;
    }

    /**
     * Constructor that auto-detects the {@link CredentialsProvider} that this {@link BaseCredentialsStore} is associated
     * with by examining the outer classes until an outer class that implements {@link CredentialsProvider} is found.
     *
     * @since 2.0
     */
    @SuppressWarnings("unchecked")
    public BaseCredentialsStore() {
        // now let's infer our provider, Jesse will not like this evil
        Class<?> clazz = getClass().getEnclosingClass();
        while (clazz != null && !CredentialsProvider.class.isAssignableFrom(clazz)) {
            clazz = clazz.getEnclosingClass();
        }
        if (clazz == null) {
            throw new AssertionError(getClass() + " doesn't have an outer class. "
                    + "Use the constructor that takes the Class object explicitly.");
        }
        if (!CredentialsProvider.class.isAssignableFrom(clazz)) {
            throw new AssertionError(getClass() + " doesn't have an outer class implementing CredentialsProvider. "
                    + "Use the constructor that takes the Class object explicitly");
        }
        providerClass = (Class<? extends CredentialsProvider>) clazz;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public final CredentialsProvider getProviderOrDie() {
        CredentialsProvider provider = getProvider();
        if (provider == null) {
            // we can only construct an instance if we were given the providerClass or we successfully inferred it
            // thus if the provider is missing it must have been removed from the extension list, e.g. by an admin
            // that wanted to block that provider from users before the addition of provider visibility controls
            throw new IllegalStateException("The credentials provider " + providerClass
                    + " has been removed from the list of active extension points");
        }
        return provider;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    public final CredentialsProvider getProvider() {
        return ExtensionList.lookup(CredentialsProvider.class).get(providerClass);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    public final Set<CredentialsScope> getScopes() {
        CredentialsProvider provider = getProvider();
        return provider == null ? null : provider.getScopes(getContext());
    }

    /**
     * {@inheritDoc}
     */
    public ACL getACL() {
        // we really want people to implement this one, but in case of legacy implementations we need to provide
        // an effective ACL implementation.
        return new ACL() {
            @Override
            public boolean hasPermission(Authentication a, Permission permission) {
                return BaseCredentialsStore.this.hasPermission(a, permission);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    public final void checkPermission(@NonNull Permission p) {
        Authentication a = Jenkins.getAuthentication();
        if (!hasPermission(a, p)) {
            throw new AccessDeniedException2(a, p);
        }
    }

    /**
     * {@inheritDoc}
     */
    public final boolean hasPermission(@NonNull Permission p) {
        return hasPermission(Jenkins.getAuthentication(), p);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public List<Domain> getDomains() {
        return Collections.singletonList(Domain.global());
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public Domain getDomainByName(@CheckForNull String name) {
        for (Domain d : getDomains()) {
            if (StringUtils.equals(name, d.getName())) {
                return d;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDomainsModifiable() {
        return this instanceof ModifiableDomainsCredentialsStore;
    }

    // todo this should become final once BaseCredentialsStore replaces CredentialsStore
    /**
     * {@inheritDoc}
     */
    public boolean isCredentialsModifiable() {
        return this instanceof ModifiableItemsCredentialsStore;
    }

    /**
     * Determines if the specified {@link Descriptor} is applicable to this {@link BaseCredentialsStore}.
     * <p>
     * The default implementation consults the {@link DescriptorVisibilityFilter}s, {@link #_isApplicable(Descriptor)}
     * and the {@link #getProviderOrDie()}.
     *
     * {@inheritDoc}
     */
    public final boolean isApplicable(Descriptor<?> descriptor) {
        for (DescriptorVisibilityFilter filter : DescriptorVisibilityFilter.all()) {
            if (!filter.filter(this, descriptor)) {
                return false;
            }
        }
        CredentialsProvider provider = getProvider();
        return _isApplicable(descriptor) && (provider == null || provider.isApplicable(descriptor));
    }

    /**
     * {@inheritDoc}
     */
    protected boolean _isApplicable(Descriptor<?> descriptor) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public final List<CredentialsDescriptor> getCredentialsDescriptors() {
        CredentialsProvider provider = getProvider();
        List<CredentialsDescriptor> result =
                DescriptorVisibilityFilter.apply(this, ExtensionList.lookup(CredentialsDescriptor.class));
        if (provider != null && provider.isEnabled()) {
            if (!(result instanceof ArrayList)) {
                // should never happen, but let's be defensive in case the DescriptorVisibilityFilter contract changes
                result = new ArrayList<CredentialsDescriptor>(result);
            }
            for (Iterator<CredentialsDescriptor> iterator = result.iterator(); iterator.hasNext(); ) {
                CredentialsDescriptor d = iterator.next();
                if (!_isApplicable(d) || !provider._isApplicable(d) || !d.isApplicable(provider)) {
                    iterator.remove();
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public String getRelativeLinkToContext() {
        ModelObject context = getContext();
        if (context instanceof Item) {
            return Functions.getRelativeLinkTo((Item) context);
        }
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request == null) {
            return null;
        }
        if (context instanceof Jenkins) {
            return URI.create(request.getContextPath() + "/").normalize().toString();
        }
        if (context instanceof User) {
            return URI.create(request.getContextPath() + "/" + ((User) context).getUrl() + "/")
                    .normalize().toString();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public String getRelativeLinkToAction() {
        ModelObject context = getContext();
        String relativeLink = getRelativeLinkToContext();
        if (relativeLink == null) {
            return null;
        }
        CredentialsStoreAction a = getStoreAction();
        if (a != null) {
            return relativeLink + "credentials/store/" + a.getUrlName() + "/";
        }
        List<CredentialsStoreAction> actions;
        if (context instanceof Actionable) {
            actions = ((Actionable) context).getActions(CredentialsStoreAction.class);
        } else if (context instanceof Jenkins) {
            actions = Util.filter(((Jenkins) context).getActions(), CredentialsStoreAction.class);
        } else if (context instanceof User) {
            actions = Util.filter(((User) context).getTransientActions(), CredentialsStoreAction.class);
        } else {
            return null;
        }
        for (CredentialsStoreAction action : actions) {
            if (action.getStoreImpl() == this) {
                return relativeLink + action.getUrlName() + "/";
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public String getRelativeLinkTo(Domain domain) {
        String relativeLink = getRelativeLinkToAction();
        if (relativeLink == null) {
            return null;
        }
        return relativeLink + domain.getUrl();
    }

    /**
     * {@inheritDoc}
     */
    public final String getContextDisplayName() {
        ModelObject context = getContext();
        if (context instanceof Item) {
            return ((Item) context).getFullDisplayName();
        } else if (context instanceof Jenkins) {
            return ((Jenkins) context).getDisplayName();
        } else if (context instanceof ItemGroup) {
            return ((ItemGroup) context).getFullDisplayName();
        } else if (context instanceof User) {
            return com.cloudbees.plugins.credentials.Messages.CredentialsStoreAction_UserDisplayName(context.getDisplayName());
        } else {
            return context.getDisplayName();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    public CredentialsStoreAction getStoreAction() {
        return null;
    }
}
