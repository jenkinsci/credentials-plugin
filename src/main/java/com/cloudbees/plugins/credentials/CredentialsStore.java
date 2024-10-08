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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.BulkChange;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Util;
import hudson.model.Actionable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AccessDeniedException3;
import hudson.security.Permission;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.core.Authentication;

/**
 * A store of {@link Credentials}. Each {@link CredentialsStore} is associated with one and only one
 * {@link CredentialsProvider} though a {@link CredentialsProvider} may provide multiple {@link CredentialsStore}s
 * (for example a folder scoped {@link CredentialsProvider} may provide a {@link CredentialsStore} for each folder
 * or a user scoped {@link CredentialsProvider} may provide a {@link CredentialsStore} for each user).
 *
 * @author Stephen Connolly
 * @since 1.8
 */
public abstract class CredentialsStore implements AccessControlled, Saveable {

    /**
     * The {@link CredentialsProvider} class.
     *
     * @since 2.0
     */
    private final Class<? extends CredentialsProvider> providerClass;

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
        this.providerClass = providerClass;
    }

    /**
     * Constructor that auto-detects the {@link CredentialsProvider} that this {@link CredentialsStore} is associated
     * with by examining the outer classes until an outer class that implements {@link CredentialsProvider} is found.
     *
     * @since 2.0
     */
    @SuppressWarnings("unchecked")
    public CredentialsStore() {
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
     * Returns the {@link CredentialsProvider} or dies trying.
     *
     * @return the {@link CredentialsProvider}
     * @since 2.0
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
     * Returns the {@link CredentialsProvider}.
     *
     * @return the {@link CredentialsProvider} (may be {@code null} if the admin has removed the provider from
     * the {@link ExtensionList})
     * @since 2.0
     */
    @Nullable
    public final CredentialsProvider getProvider() {
        return ExtensionList.lookup(CredentialsProvider.class).get(providerClass);
    }

    /**
     * Returns the {@link CredentialsScope} instances that are applicable to this store.
     * @return the {@link CredentialsScope} instances that are applicable to this store or {@code null} if the store
     * instance is no longer enabled.
     *
     * @since 2.1.5
     */
    @Nullable
    public final Set<CredentialsScope> getScopes() {
        CredentialsProvider provider = getProvider();
        return provider == null ? null : provider.getScopes(getContext());
    }

    /**
     * Returns the context within which this store operates. Credentials in this store will be available to
     * child contexts (unless {@link CredentialsScope#SYSTEM} is valid for the store) but will not be available to
     * parent contexts.
     *
     * @return the context within which this store operates.
     */
    @NonNull
    public abstract ModelObject getContext();


    /**
     * @deprecated Use {@link #hasPermission2(Authentication, Permission)} instead.
     */
    @Deprecated
    public boolean hasPermission(@NonNull org.acegisecurity.Authentication a, @NonNull Permission permission) {
        return hasPermission2(a.toSpring(), permission);
    }

    /**
     * Checks if the given principle has the given permission.
     *
     * @param a          the principle.
     * @param permission the permission.
     * @return {@code false} if the user doesn't have the permission.
     * @since TODO
     */
    public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
        if (Util.isOverridden(CredentialsStore.class, getClass(), "hasPermission", org.acegisecurity.Authentication.class,
                Permission.class)) {
            return hasPermission(org.acegisecurity.Authentication.fromSpring(a), permission);
        }
        throw new AbstractMethodError("Implement hasPermission2 from " + getClass());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public ACL getACL() {
        // we really want people to implement this one, but in case of legacy implementations we need to provide
        // an effective ACL implementation.
        return new ACL() {
            @Override
            public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                return CredentialsStore.this.hasPermission2(a, permission);
            }
        };
    }

    /**
     * Checks if the current security principal has this permission.
     * <p>
     * Note: This is just a convenience function.
     * </p>
     *
     * @throws AccessDeniedException3 if the user doesn't have the permission.
     */
    public final void checkPermission(@NonNull Permission p) {
        Authentication a = Jenkins.getAuthentication2();
        if (!hasPermission2(a, p)) {
            throw new AccessDeniedException3(a, p);
        }
    }

    /**
     * Checks if the current security principal has this permission.
     *
     * @return {@code false} if the user doesn't have the permission.
     */
    public final boolean hasPermission(@NonNull Permission p) {
        return hasPermission2(Jenkins.getAuthentication2(), p);
    }

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
     * Retrieves the domain with the matching name.
     *
     * @param name the name (or {@code null} to match {@link Domain#global()} as that is the domain with a null name)
     * @return the domain or {@code null} if there is no domain with the supplied name.
     * @since 2.1.1
     */
    @CheckForNull
    public Domain getDomainByName(@CheckForNull String name) {
        return getDomains()
                .stream()
                .filter(d -> StringUtils.equals(name, d.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Identifies whether this {@link CredentialsStore} supports making changes to the list of domains or
     * whether it only supports a fixed set of domains (which may only be one domain).
     * <p>
     * Note: in order for implementations to return {@code true} all of the following methods must be overridden:
     * </p>
     * <ul>
     * <li>{@link #getDomains}</li>
     * <li>{@link #addDomain(Domain, java.util.List)}</li>
     * <li>{@link #removeDomain(Domain)}</li>
     * <li>{@link #updateDomain(Domain, Domain)} </li>
     * </ul>
     *
     * @return {@code true} iff {@link #addDomain(Domain, List)}
     * {@link #addDomain(Domain, Credentials...)}, {@link #removeDomain(Domain)}
     * and {@link #updateDomain(Domain, Domain)} are expected to work
     */
    public final boolean isDomainsModifiable() {
        if (domainsModifiable == null) {
            try {
                domainsModifiable = isOverridden("getDomains")
                        && isOverridden("addDomain", Domain.class, List.class)
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
    private boolean isOverridden(String name, Class... args) throws NoSuchMethodException {
        return getClass().getMethod(name, args).getDeclaringClass() != CredentialsStore.class;
    }

    /**
     * Returns an unmodifiable list of credentials for the specified domain.
     *
     * @param domain the domain.
     * @return the possibly empty (e.g. for an unknown {@link Domain}) unmodifiable list of credentials for the
     * specified domain.
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
     * Updates an existing {@link Domain} keeping the existing associated {@link Credentials}.
     *
     * @param current     the domain to update.
     * @param replacement the new replacement domain.
     * @return {@code true} if the {@link CredentialsStore} was modified.
     * @throws IOException if the change could not be persisted.
     */
    public boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
        throw new UnsupportedOperationException("Implementation does not support updating domains");
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

    /**
     * Determines if the specified {@link Descriptor} is applicable to this {@link CredentialsStore}.
     * <p>
     * The default implementation consults the {@link DescriptorVisibilityFilter}s, {@link #_isApplicable(Descriptor)}
     * and the {@link #getProviderOrDie()}.
     *
     * @param descriptor the {@link Descriptor} to check.
     * @return {@code true} if the supplied {@link Descriptor} is applicable in this {@link CredentialsStore}
     * @since 2.0
     */
    public final boolean isApplicable(Descriptor<?> descriptor) {
        boolean allFiltersMatch = DescriptorVisibilityFilter.all()
                .stream()
                .allMatch(filter -> filter.filter(this, descriptor));
        if (!allFiltersMatch) {
            return false;
        }
        CredentialsProvider provider = getProvider();
        return _isApplicable(descriptor) && (provider == null || provider.isApplicable(descriptor));
    }

    /**
     * {@link CredentialsStore} subtypes can override this method to veto some  {@link Descriptor}s
     * from being available from their store. This is often useful when you are building
     * a custom store that holds a specific type of credentials or where you want to limit the
     * number of choices given to the users.
     *
     * @param descriptor the {@link Descriptor} to check.
     * @return {@code true} if the supplied {@link Descriptor} is applicable in this {@link CredentialsStore}
     * @since 2.0
     */
    protected boolean _isApplicable(Descriptor<?> descriptor) {
        return true;
    }

    /**
     * Returns the list of {@link CredentialsDescriptor} instances that are applicable within this
     * {@link CredentialsStore}.
     *
     * @return the list of {@link CredentialsDescriptor} instances that are applicable within this
     * {@link CredentialsStore}.
     * @since 2.0
     */
    public final List<CredentialsDescriptor> getCredentialsDescriptors() {
        CredentialsProvider provider = getProvider();
        List<CredentialsDescriptor> result =
                DescriptorVisibilityFilter.apply(this, ExtensionList.lookup(CredentialsDescriptor.class));
        if (provider != null && provider.isEnabled()) {
            result = result.stream()
                    .filter(d -> _isApplicable(d) && provider._isApplicable(d) && d.isApplicable(provider))
                    .collect(Collectors.toList());
        }
        return result;
    }

    /**
     * Computes the relative path from the current page to this store.
     *
     * @return the relative path from the current page or {@code null}
     * @since 2.0
     */
    @CheckForNull
    public String getRelativeLinkToContext() {
        ModelObject context = getContext();
        if (context instanceof Item) {
            return Functions.getRelativeLinkTo((Item) context);
        }
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null) {
            return null;
        }
        if (context instanceof Jenkins) {
            String suffix = Jenkins.get().hasPermission(Jenkins.ADMINISTER) ? "/manage/" : "/";
            return URI.create(request.getContextPath() + suffix).normalize().toString();
        }
        if (context instanceof User) {
            return URI.create(request.getContextPath() + "/" + ((User) context).getUrl()+"/")
                    .normalize().toString();
        }
        return null;
    }

    /**
     * Computes the relative path from the current page to this store.
     *
     * @return the relative path from the current page or {@code null}
     * @since 2.0
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
            if (action.getStore() == this) {
                return relativeLink + action.getUrlName() + "/";
            }
        }
        return null;
    }

    /**
     * Computes the relative path from the current page to the specified domain.
     *
     * @param domain the domain
     * @return the relative path from the current page or {@code null}
     * @since 2.0
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
     * Returns the display name of the {@link #getContext()} of this {@link CredentialsStore}. The default
     * implementation can handle both {@link Item} and {@link ItemGroup} as long as these are accessible from
     * {@link Jenkins}, and {@link User}. If the {@link CredentialsStore} provides an alternative
     * {@link #getContext()} that is outside of the normal tree then that implementation is responsible for
     * overriding this method to produce the correct display name.
     *
     * @return the display name.
     * @since 2.0
     */
    public final String getContextDisplayName() {
        ModelObject context = getContext();
        if (context instanceof Item) {
            return ((Item) context).getFullDisplayName();
        } else if (context instanceof Jenkins) {
            return context.getDisplayName();
        } else if (context instanceof ItemGroup) {
            return ((ItemGroup) context).getFullDisplayName();
        } else if (context instanceof User) {
            return Messages.CredentialsStoreAction_UserDisplayName(context.getDisplayName());
        } else {
            return context.getDisplayName();
        }
    }

    /**
     * Resolves a display name from the Store
     * @return the display name
     */
    public String getDisplayName() {
        Class<?> c = this.getClass();
        while (c.getEnclosingClass() != null) {
            c = c.getEnclosingClass();
        }
        String name = c.getSimpleName().replaceAll("(?i)(Impl|Credentials|Provider|Store)+", "");
        if (StringUtils.isBlank(name)) {
            name = c.getSimpleName();
        }
        return StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(name), ' ');
    }

    /**
     * Return the {@link CredentialsStoreAction} for this store. The action will be displayed as a sub-item of the
     * {@link ViewCredentialsAction}. Return {@code null} if this store will take control of displaying its action
     * (which will be the case for legacy implementations)
     *
     * @return the {@link CredentialsStoreAction} for this store to be rendered in {@link ViewCredentialsAction} or
     * {@code null} for old implementations compiled against pre 2.0 versions of credentials plugin.
     * @since 2.0
     */
    @Nullable
    public CredentialsStoreAction getStoreAction() {
        return null;
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
