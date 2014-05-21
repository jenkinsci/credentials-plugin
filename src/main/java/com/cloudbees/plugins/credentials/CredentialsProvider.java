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
package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An extension point for providing {@link Credentials}.
 */
public abstract class CredentialsProvider implements ExtensionPoint {

    /**
     * Our logger.
     *
     * @since 1.6
     */
    private static final Logger LOGGER = Logger.getLogger(CredentialsProvider.class.getName());

    /**
     * The permission group for credentials.
     *
     * @since 1.8
     */
    public static final PermissionGroup GROUP = new PermissionGroup(CredentialsProvider.class,
            Messages._CredentialsProvider_PermissionGroupTitle());

    /**
     * The permission for adding credentials to a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission CREATE = new Permission(GROUP, "Create",
            Messages._CredentialsProvider_CreatePermissionDescription(), Permission.CREATE, PermissionScope.ITEM);

    /**
     * The permission for updating credentials in a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission UPDATE = new Permission(GROUP, "Update",
            Messages._CredentialsProvider_UpdatePermissionDescription(), Permission.UPDATE, PermissionScope.ITEM);

    /**
     * The permission for viewing credentials in a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission VIEW = new Permission(GROUP, "View",
            Messages._CredentialsProvider_ViewPermissionDescription(), Permission.READ, PermissionScope.ITEM);

    /**
     * The permission for removing credentials from a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission DELETE = new Permission(GROUP, "Delete",
            Messages._CredentialsProvider_DeletePermissionDescription(), Permission.DELETE, PermissionScope.ITEM);

    /**
     * The permission for managing credential domains in a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission MANAGE_DOMAINS = new Permission(GROUP, "ManageDomains",
            Messages._CredentialsProvider_ManageDomainsPermissionDescription(), Permission.CONFIGURE, PermissionScope.ITEM);

    /**
     * Returns all the registered {@link com.cloudbees.plugins.credentials.Credentials} descriptors.
     *
     * @return all the registered {@link com.cloudbees.plugins.credentials.Credentials} descriptors.
     */
    public static DescriptorExtensionList<Credentials, Descriptor<Credentials>> allCredentialsDescriptors() {
        return Hudson.getInstance().getDescriptorList(Credentials.class);
    }

    /**
     * Returns the scopes allowed for credentials stored within the specified object or {@code null} if the
     * object is not relevant for scopes and the object's container should be considered instead.
     *
     * @param object the object.
     * @return the set of scopes that are relevant for the object or {@code null} if the object is not a credentials
     *         container.
     */
    public Set<CredentialsScope> getScopes(ModelObject object) {
        return null;
    }

    /**
     * Returns the {@link CredentialsStore} that this {@link CredentialsProvider} maintains specifically for this
     * {@link ModelObject} or {@code null} if either the object is not a credentials container or this
     * {@link CredentialsProvider} does not maintain a store specifically bound to this {@link ModelObject}.
     *
     * @param object the {@link Item} or {@link ItemGroup} that the store is being requested of.
     * @return either {@code null} or a scoped {@link CredentialsStore} where
     *         {@link com.cloudbees.plugins.credentials.CredentialsStore#getContext()} {@code == object}.
     * @since 1.8
     */
    @CheckForNull
    public CredentialsStore getStore(@CheckForNull ModelObject object) {
        return null;
    }

    /**
     * Returns the credentials provided by this provider which are available to the specified {@link Authentication}
     * for items in the specified {@link ItemGroup}
     *
     * @param type           the type of credentials to return.
     * @param itemGroup      the item group (if {@code null} assume {@link hudson.model.Hudson#getInstance()}.
     * @param authentication the authentication (if {@code null} assume {@link hudson.security.ACL#SYSTEM}.
     * @return the list of credentials.
     */
    @NonNull
    public abstract <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                                   @Nullable ItemGroup itemGroup,
                                                                   @Nullable Authentication authentication);

    /**
     * Returns the credentials provided by this provider which are available to the specified {@link Authentication}
     * for items in the specified {@link ItemGroup} and are appropriate for the specified {@link com.cloudbees
     * .plugins.credentials.domains.DomainRequirement}s.
     *
     * @param type               the type of credentials to return.
     * @param itemGroup          the item group (if {@code null} assume {@link hudson.model.Hudson#getInstance()}.
     * @param authentication     the authentication (if {@code null} assume {@link hudson.security.ACL#SYSTEM}.
     * @param domainRequirements the credential domains to match (if the {@link CredentialsProvider} does not support
     *                           {@link com.cloudbees.plugins.credentials.domains.DomainRequirement}s then it should
     *                           assume the match is true).
     * @return the list of credentials.
     * @since 1.5
     */
    @NonNull
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                          @Nullable ItemGroup itemGroup,
                                                          @Nullable Authentication authentication,
                                                          @NonNull List<DomainRequirement> domainRequirements) {
        return getCredentials(type, itemGroup, authentication);
    }

    /**
     * Returns the credentials provided by this provider which are available to the specified {@link Authentication}
     * for the specified {@link Item}
     *
     * @param type           the type of credentials to return.
     * @param item           the item.
     * @param authentication the authentication (if {@code null} assume {@link hudson.security.ACL#SYSTEM}.
     * @return the list of credentials.
     */
    @NonNull
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                          @NonNull Item item,
                                                          @Nullable Authentication authentication) {
        item.getClass();
        return getCredentials(type, item.getParent(), authentication);
    }

    /**
     * Returns the credentials provided by this provider which are available to the specified {@link Authentication}
     * for items in the specified {@link Item} and are appropriate for the specified {@link com.cloudbees.plugins
     * .credentials.domains.DomainRequirement}s.
     *
     * @param type               the type of credentials to return.
     * @param item               the item.
     * @param authentication     the authentication (if {@code null} assume {@link hudson.security.ACL#SYSTEM}.
     * @param domainRequirements the credential domain to match.
     * @return the list of credentials.
     * @since 1.5
     */
    @NonNull
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                          @NonNull Item item,
                                                          @Nullable Authentication authentication,
                                                          @NonNull List<DomainRequirement> domainRequirements) {
        return getCredentials(type, item.getParent(), authentication, domainRequirements);
    }

    /**
     * Returns all credentials which are available to the {@link ACL#SYSTEM} {@link Authentication}
     * within the {@link hudson.model.Hudson#getInstance()}.
     *
     * @param type the type of credentials to get.
     * @param <C>  the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, Item, Authentication, List)},
     *             {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)},
     *             {@link #lookupCredentials(Class, ItemGroup, Authentication, List)}
     *             or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type) {
        return lookupCredentials(type, (Item) null, ACL.SYSTEM);
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * within the {@link hudson.model.Hudson#getInstance()}.
     *
     * @param type           the type of credentials to get.
     * @param authentication the authentication.
     * @param <C>            the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, Item, Authentication, List)},
     *             {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)},
     *             {@link #lookupCredentials(Class, ItemGroup, Authentication, List)}
     *             or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Authentication authentication) {
        return lookupCredentials(type, Hudson.getInstance(), authentication);
    }

    /**
     * Returns all credentials which are available to the {@link ACL#SYSTEM} {@link Authentication}
     * for use by the specified {@link Item}.
     *
     * @param type the type of credentials to get.
     * @param item the item.
     * @param <C>  the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, Item, Authentication, List)}
     *             or {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item) {
        return item == null
                ? lookupCredentials(type, Hudson.getInstance(), ACL.SYSTEM)
                : lookupCredentials(type, item, ACL.SYSTEM);
    }

    /**
     * Returns all credentials which are available to the {@link ACL#SYSTEM} {@link Authentication}
     * for use by the {@link Item}s in the specified {@link ItemGroup}.
     *
     * @param type      the type of credentials to get.
     * @param itemGroup the item group.
     * @param <C>       the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, ItemGroup, Authentication, List)}
     *             or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup) {
        return lookupCredentials(type, itemGroup, ACL.SYSTEM);
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the {@link Item}s in the specified {@link ItemGroup}.
     *
     * @param type           the type of credentials to get.
     * @param itemGroup      the item group.
     * @param authentication the authentication.
     * @param <C>            the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, ItemGroup, Authentication, List)}
     *             or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup,
                                                                    @Nullable Authentication authentication) {
        return lookupCredentials(type, itemGroup, authentication, Collections.<DomainRequirement>emptyList());
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the specified {@link Item}.
     *
     * @param type           the type of credentials to get.
     * @param authentication the authentication.
     * @param item           the item.
     * @param <C>            the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, Item, Authentication, List)}
     *             or {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item,
                                                                    @Nullable Authentication authentication) {
        return lookupCredentials(type, item, authentication, Collections.<DomainRequirement>emptyList());
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the {@link Item}s in the specified {@link ItemGroup}.
     *
     * @param type               the type of credentials to get.
     * @param itemGroup          the item group.
     * @param authentication     the authentication.
     * @param domainRequirements the credential domains to match.
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since 1.5
     */
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup,
                                                                    @Nullable Authentication authentication,
                                                                    @Nullable DomainRequirement... domainRequirements) {
        return lookupCredentials(type, itemGroup, authentication, Arrays.asList(domainRequirements));
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the {@link Item}s in the specified {@link ItemGroup}.
     *
     * @param type               the type of credentials to get.
     * @param itemGroup          the item group.
     * @param authentication     the authentication.
     * @param domainRequirements the credential domains to match.
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since 1.5
     */
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup,
                                                                    @Nullable Authentication authentication,
                                                                    @Nullable List<DomainRequirement>
                                                                            domainRequirements) {
        type.getClass(); // throw NPE if null
        itemGroup = itemGroup == null ? Hudson.getInstance() : itemGroup;
        authentication = authentication == null ? ACL.SYSTEM : authentication;
        domainRequirements = domainRequirements
                == null ? Collections.<DomainRequirement>emptyList() : domainRequirements;
        CredentialsResolver<Credentials, C> resolver = CredentialsResolver.getResolver(type);
        if (resolver != null) {
            LOGGER.log(Level.FINE, "Resolving legacy credentials of type {0} with resolver {1}",
                    new Object[]{type, resolver});
            final List<Credentials> originals =
                    lookupCredentials(resolver.getFromClass(), itemGroup, authentication, domainRequirements);
            LOGGER.log(Level.FINE, "Original credentials for resolving: {0}", originals);
            return resolver.resolve(originals);
        }
        ExtensionList<CredentialsProvider> providers;
        try {
            providers = Hudson.getInstance().getExtensionList(CredentialsProvider.class);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not retrieve list of CredentialsProvider instances", e);
            return Collections.emptyList();
        }
        List<C> result = new ArrayList<C>();
        for (CredentialsProvider provider : providers) {
            try {
                result.addAll(provider.getCredentials(type, itemGroup, authentication, domainRequirements));
            } catch (NoClassDefFoundError e) {
                LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                        + " likely due to missing optional dependency", e);
            }
        }
        return result;
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the specified {@link Item}.
     *
     * @param type               the type of credentials to get.
     * @param authentication     the authentication.
     * @param item               the item.
     * @param domainRequirements the credential domains to match.
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since 1.5
     */
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item,
                                                                    @Nullable Authentication authentication,
                                                                    DomainRequirement... domainRequirements) {
        return lookupCredentials(type, item, authentication, Arrays.asList(domainRequirements));
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the specified {@link Item}.
     *
     * @param type               the type of credentials to get.
     * @param authentication     the authentication.
     * @param item               the item.
     * @param domainRequirements the credential domains to match.
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since 1.5
     */
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item,
                                                                    @Nullable Authentication authentication,
                                                                    @Nullable List<DomainRequirement>
                                                                            domainRequirements) {
        type.getClass(); // throw NPE if null
        if (item == null) {
            return lookupCredentials(type, Hudson.getInstance(), authentication);
        }
        authentication = authentication == null ? ACL.SYSTEM : authentication;
        domainRequirements = domainRequirements
                == null ? Collections.<DomainRequirement>emptyList() : domainRequirements;
        CredentialsResolver<Credentials, C> resolver = CredentialsResolver.getResolver(type);
        if (resolver != null) {
            LOGGER.log(Level.FINE, "Resolving legacy credentials of type {0} with resolver {1}",
                    new Object[]{type, resolver});
            final List<Credentials> originals =
                    lookupCredentials(resolver.getFromClass(), item, authentication, domainRequirements);
            LOGGER.log(Level.FINE, "Original credentials for resolving: {0}", originals);
            return resolver.resolve(originals);
        }
        ExtensionList<CredentialsProvider> providers;
        try {
            providers = Hudson.getInstance().getExtensionList(CredentialsProvider.class);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not retrieve list of CredentialsProvider instances", e);
            return Collections.emptyList();
        }
        List<C> result = new ArrayList<C>();
        for (CredentialsProvider provider : providers) {
            try {
                result.addAll(provider.getCredentials(type, item, authentication, domainRequirements));
            } catch (NoClassDefFoundError e) {
                LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                        + " likely due to missing optional dependency", e);
            }
        }
        return result;
    }

    /**
     * Returns the scopes allowed for credentials stored within the specified object or {@code null} if the
     * object is not relevant for scopes and the object's container should be considered instead.
     *
     * @param object the object.
     * @return the set of scopes that are relevant for the object or {@code null} if the object is not a credentials
     *         container.
     */
    @CheckForNull
    public static Set<CredentialsScope> lookupScopes(ModelObject object) {
        ExtensionList<CredentialsProvider> providers;
        try {
            providers = Hudson.getInstance().getExtensionList(CredentialsProvider.class);
        } catch (Exception e) {
            return Collections.emptySet();
        }
        Set<CredentialsScope> result = null;
        for (CredentialsProvider provider : providers) {
            try {
                Set<CredentialsScope> scopes = provider.getScopes(object);
                if (scopes != null) {
                    // if multiple providers for the same object, then combine scopes
                    if (result == null) {
                        result = new LinkedHashSet<CredentialsScope>();
                    }
                    result.addAll(scopes);
                }
            } catch (NoClassDefFoundError e) {
                // ignore optional dependency
            }
        }
        return result;
    }

    /**
     * Returns a lazy {@link Iterable} of all the {@link CredentialsStore} instances contributing credentials to the
     * supplied
     * object.
     *
     * @param object the {@link Item} or {@link ItemGroup} to get the {@link CredentialsStore}s of.
     * @return a lazy {@link Iterable} of all {@link CredentialsStore} instances.
     * @since 1.8
     */
    public static Iterable<CredentialsStore> lookupStores(final ModelObject object) {
        final ExtensionList<CredentialsProvider> providers;
        try {
            providers = Hudson.getInstance().getExtensionList(CredentialsProvider.class);
        } catch (Exception e) {
            return Collections.emptySet();
        }
        return new Iterable<CredentialsStore>() {
            public Iterator<CredentialsStore> iterator() {
                return new Iterator<CredentialsStore>() {
                    private ModelObject current = object;
                    private Iterator<CredentialsProvider> iterator= providers.iterator();
                    private CredentialsStore next;

                    public boolean hasNext() {
                        if (next != null) {
                            return true;
                        }
                        while (current != null) {
                            while (iterator.hasNext()) {
                                CredentialsProvider p = iterator.next();
                                next = p.getStore(current);
                                if (next != null) {
                                    return true;
                                }
                            }
                            if (current instanceof Item) {
                                current = ((Item) current).getParent();
                                iterator = providers.iterator();
                            } else if (current instanceof Jenkins) {
                                current = null;
                            }
                        }
                        return false;
                    }

                    public CredentialsStore next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        try {
                            return next;
                        } finally {
                            next = null;
                        }
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }
}
