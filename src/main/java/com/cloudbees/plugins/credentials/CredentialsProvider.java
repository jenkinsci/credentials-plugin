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

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

/**
 * An extension point for providing {@link Credentials}.
 */
public abstract class CredentialsProvider implements ExtensionPoint {

    /**
     * The permission group for credentials.
     *
     * @since 1.8
     */
    public static final PermissionGroup GROUP = new PermissionGroup(CredentialsProvider.class,
            Messages._CredentialsProvider_PermissionGroupTitle());
    /**
     * Where an immediate action against a job requires that a credential be selected by the user triggering the
     * action, this permission allows the user to select a credential from their private credential store. Immediate
     * actions could include: building with parameters, tagging a build, deploying artifacts, etc.
     *
     * @since 1.16
     */
    public static final Permission USE_OWN = new Permission(GROUP, "UseOwn",
            Messages._CredentialsProvider_UseOwnPermissionDescription(),
            Boolean.getBoolean("com.cloudbees.plugins.credentials.UseOwnPermission") ? Jenkins.ADMINISTER : Job.BUILD,
            Boolean.getBoolean("com.cloudbees.plugins.credentials.UseOwnPermission"),
            new PermissionScope[]{PermissionScope.ITEM});
    /**
     * Where an immediate action against a job requires that a credential be selected by the user triggering the
     * action, this permission allows the user to select a credential from those credentials available within the
     * scope of the job. Immediate actions could include: building with parameters, tagging a build,
     * deploying artifacts, etc.
     *
     * This permission is implied by {@link Job#CONFIGURE} as anyone who can configure the job can configure the
     * job to use credentials within the item scope anyway.
     *
     * @since 1.16
     */
    public static final Permission USE_ITEM = new Permission(GROUP, "UseItem",
            Messages._CredentialsProvider_UseItemPermissionDescription(), Job.CONFIGURE,
            Boolean.getBoolean("com.cloudbees.plugins.credentials.UseItemPermission"),
            new PermissionScope[]{PermissionScope.ITEM});
    /**
     * Our logger.
     *
     * @since 1.6
     */
    private static final Logger LOGGER = Logger.getLogger(CredentialsProvider.class.getName());
    /**
     * The scopes that we allow credential permissions on.
     *
     * @since 1.12.
     */
    private static final PermissionScope[] SCOPES =
            new PermissionScope[]{PermissionScope.ITEM, PermissionScope.ITEM_GROUP, PermissionScope.JENKINS};
    /**
     * The permission for adding credentials to a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission CREATE = new Permission(GROUP, "Create",
            Messages._CredentialsProvider_CreatePermissionDescription(), Permission.CREATE, true, SCOPES);
    /**
     * The permission for updating credentials in a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission UPDATE = new Permission(GROUP, "Update",
            Messages._CredentialsProvider_UpdatePermissionDescription(), Permission.UPDATE, true, SCOPES);
    /**
     * The permission for viewing credentials in a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission VIEW = new Permission(GROUP, "View",
            Messages._CredentialsProvider_ViewPermissionDescription(), Permission.READ, true, SCOPES);
    /**
     * The permission for removing credentials from a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission DELETE = new Permission(GROUP, "Delete",
            Messages._CredentialsProvider_DeletePermissionDescription(), Permission.DELETE, true, SCOPES);
    /**
     * The permission for managing credential domains in a {@link CredentialsStore}.
     *
     * @since 1.8
     */
    public static final Permission MANAGE_DOMAINS = new Permission(GROUP, "ManageDomains",
            Messages._CredentialsProvider_ManageDomainsPermissionDescription(), Permission.CONFIGURE, true, SCOPES);

    /**
     * Returns all the registered {@link com.cloudbees.plugins.credentials.Credentials} descriptors.
     *
     * @return all the registered {@link com.cloudbees.plugins.credentials.Credentials} descriptors.
     */
    public static DescriptorExtensionList<Credentials, CredentialsDescriptor> allCredentialsDescriptors() {
        // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
        return Jenkins.getActiveInstance().getDescriptorList(Credentials.class);
    }

    /**
     * Returns all credentials which are available to the {@link ACL#SYSTEM} {@link Authentication}
     * within the {@link jenkins.model.Jenkins#getInstance()}.
     *
     * @param type the type of credentials to get.
     * @param <C>  the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, Item, Authentication, List)},
     * {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)},
     * {@link #lookupCredentials(Class, ItemGroup, Authentication, List)}
     * or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type) {
        return lookupCredentials(type, (Item) null, ACL.SYSTEM);
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * within the {@link jenkins.model.Jenkins#getInstance()}.
     *
     * @param type           the type of credentials to get.
     * @param authentication the authentication.
     * @param <C>            the credentials type.
     * @return the list of credentials.
     * @deprecated use {@link #lookupCredentials(Class, Item, Authentication, List)},
     * {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)},
     * {@link #lookupCredentials(Class, ItemGroup, Authentication, List)}
     * or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Authentication authentication) {
        return lookupCredentials(type, Jenkins.getInstance(), authentication);
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
     * or {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item) {
        return item == null
                ? lookupCredentials(type, Jenkins.getInstance(), ACL.SYSTEM)
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
     * or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
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
     * or {@link #lookupCredentials(Class, ItemGroup, Authentication, DomainRequirement...)}
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
     * or {@link #lookupCredentials(Class, Item, Authentication, DomainRequirement...)}
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
        // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
        Jenkins jenkins = Jenkins.getActiveInstance();
        itemGroup = itemGroup == null ? jenkins : itemGroup;
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
        List<C> result = new ArrayList<C>();
        for (CredentialsProvider provider : all()) {
            try {
                result.addAll(provider.getCredentials(type, itemGroup, authentication, domainRequirements));
            } catch (NoClassDefFoundError e) {
                LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                        + " likely due to missing optional dependency", e);
            }
        }
        Collections.sort(result, new CredentialsNameComparator());
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
            return lookupCredentials(type, Jenkins.getInstance(), authentication);
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
        List<C> result = new ArrayList<C>();
        for (CredentialsProvider provider : all()) {
            try {
                result.addAll(provider.getCredentials(type, item, authentication, domainRequirements));
            } catch (NoClassDefFoundError e) {
                LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                        + " likely due to missing optional dependency", e);
            }
        }

        Collections.sort(result, new CredentialsNameComparator());
        return result;
    }

    /**
     * Returns the scopes allowed for credentials stored within the specified object or {@code null} if the
     * object is not relevant for scopes and the object's container should be considered instead.
     *
     * @param object the object.
     * @return the set of scopes that are relevant for the object or {@code null} if the object is not a credentials
     * container.
     */
    @CheckForNull
    public static Set<CredentialsScope> lookupScopes(ModelObject object) {
        if (object instanceof CredentialsStoreAction.CredentialsWrapper) {
            object = ((CredentialsStoreAction.CredentialsWrapper) object).getStore().getContext();
        }
        if (object instanceof CredentialsStoreAction.DomainWrapper) {
            object = ((CredentialsStoreAction.DomainWrapper) object).getStore().getContext();
        }
        Set<CredentialsScope> result = null;
        for (CredentialsProvider provider : all()) {
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
     * @param object the {@link Item} or {@link ItemGroup} or {@link User} to get the {@link CredentialsStore}s of.
     * @return a lazy {@link Iterable} of all {@link CredentialsStore} instances.
     * @since 1.8
     */
    public static Iterable<CredentialsStore> lookupStores(final ModelObject object) {
        final ExtensionList<CredentialsProvider> providers = all();
        return new Iterable<CredentialsStore>() {
            public Iterator<CredentialsStore> iterator() {
                return new Iterator<CredentialsStore>() {
                    private ModelObject current = object;
                    private Iterator<CredentialsProvider> iterator = providers.iterator();
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
                            } else if (current instanceof User) {
                                current = null;
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

    /**
     * Make a best effort to ensure that the supplied credential is a snapshot credential (i.e. self-contained and
     * does not reference any external stores). <b>WARNING:</b> May produce unusual results if presented an exotic
     * credential that implements multiple distinct credential types at the same time, e.g. a credential that is
     * simultaneously a TLS certificate and a SSH key pair and a GPG key pair all at the same time... unless the
     * author of that credential type also provides a {@link CredentialsSnapshotTaker} that can handle such a
     * tripple play.
     *
     * @param credential the credential.
     * @param <C>        the type of credential.
     * @return the credential or a snapshot of the credential.
     * @since 1.14
     */
    @SuppressWarnings("unchecked")
    public static <C extends Credentials> C snapshot(C credential) {
        return (C) snapshot(Credentials.class, credential);
    }

    /**
     * Make a best effort to ensure that the supplied credential is a snapshot credential (i.e. self-contained and
     * does not reference any external stores)
     *
     * @param clazz      the type of credential that we are trying to snapshot (specified so that if there is more than
     *                   one type of snapshot able credential interface implemented by the credentials,
     *                   then they can be separated out.
     * @param credential the credential.
     * @param <C>        the type of credential.
     * @return the credential or a snapshot of the credential.
     * @since 1.14
     */
    @SuppressWarnings("unchecked")
    public static <C extends Credentials> C snapshot(Class<C> clazz, C credential) {
        Class bestType = null;
        CredentialsSnapshotTaker bestTaker = null;
        for (CredentialsSnapshotTaker taker : ExtensionList.lookup(CredentialsSnapshotTaker.class)) {
            if (clazz.isAssignableFrom(taker.type()) && taker.type().isInstance(credential)) {
                if (bestTaker == null || bestType.isAssignableFrom(taker.type())) {
                    bestTaker = taker;
                    bestType = taker.type();
                }
            }
        }
        if (bestTaker == null) {
            return credential;
        }
        return clazz.cast(bestTaker.snapshot(credential));
    }

    /**
     * Helper method to get the default authentication to use for an {@link Item}.
     */
    @NonNull
    /*package*/ static Authentication getDefaultAuthenticationOf(Item item) {
        if (item instanceof Queue.Task) {
            return Tasks.getAuthenticationOf((Queue.Task) item);
        } else {
            return ACL.SYSTEM;
        }
    }

    /**
     * A common requirement for plugins is to resolve a specific credential by id in the context of a specific run.
     * Given that the credential itself could be resulting from a build parameter expression and the complexities of
     * determining the scope of items from which the credential should be resolved in a chain of builds, this method
     * provides the correct answer.
     *
     * @param id                 either the id of the credential to find or a parameter expression for the id.
     * @param type               the type of credential to find.
     * @param run                the {@link Run} defining the context within which to find the credential.
     * @param domainRequirements the domain requirements of the credential.
     * @param <C>                the credentials type.
     * @return the credential or {@code null} if either the credential cannot be found or the user triggering the run
     * is not permitted to use the credential in the context of the run.
     * @since 1.16
     */
    @CheckForNull
    public static <C extends IdCredentials> C findCredentialById(@NonNull String id, @NonNull Class<C> type,
                                                                 @NonNull Run<?, ?> run,
                                                                 DomainRequirement... domainRequirements) {
        return findCredentialById(id, type, run, Arrays.asList(domainRequirements));
    }

    /**
     * A common requirement for plugins is to resolve a specific credential by id in the context of a specific run.
     * Given that the credential itself could be resulting from a build parameter expression and the complexities of
     * determining the scope of items from which the credential should be resolved in a chain of builds, this method
     * provides the correct answer.
     *
     * @param id                 either the id of the credential to find or a parameter expression for the id.
     * @param type               the type of credential to find.
     * @param run                the {@link Run} defining the context within which to find the credential.
     * @param domainRequirements the domain requirements of the credential.
     * @param <C>                the credentials type.
     * @return the credential or {@code null} if either the credential cannot be found or the user triggering the run
     * is not permitted to use the credential in the context of the run.
     * @since 1.16
     */
    @CheckForNull
    public static <C extends IdCredentials> C findCredentialById(@NonNull String id, @NonNull Class<C> type,
                                                                 @NonNull Run<?, ?> run,
                                                                 @Nullable List<DomainRequirement> domainRequirements) {
        id.getClass(); // throw NPE if null;
        type.getClass(); // throw NPE if null;
        run.getClass(); // throw NPE if null;

        // first we need to find out if this id is pre-selected or a parameter
        id = id.trim();
        boolean isParameter = false;
        boolean isDefaultValue = false;
        if (id.startsWith("${") && id.endsWith("}")) {
            final ParametersAction action = run.getAction(ParametersAction.class);
            if (action != null) {
                final ParameterValue parameter = action.getParameter(id.substring(2, id.length() - 1));
                if (parameter instanceof CredentialsParameterValue) {
                    isParameter = true;
                    isDefaultValue = ((CredentialsParameterValue) parameter).isDefaultValue();
                    id = ((CredentialsParameterValue) parameter).getValue();
                }
            }
        }
        // non parameters or default parameter values can only come from the job's context
        if (!isParameter || isDefaultValue) {
            // we use the default authentication of the job as those are the only ones that can be configured
            // if a different strategy is in play it doesn't make sense to consider the run-time authentication
            // as you would have no way to configure it
            Authentication runAuth = CredentialsProvider.getDefaultAuthenticationOf(run.getParent());
            List<C> candidates = new ArrayList<C>();
            // we want the credentials available to the user the build is running as
            candidates.addAll(
                    CredentialsProvider.lookupCredentials(type, run.getParent(), runAuth, domainRequirements)
            );
            // if that user can use the item's credentials, add those in too
            if (runAuth != ACL.SYSTEM && run.getACL().hasPermission(runAuth, CredentialsProvider.USE_ITEM)) {
                candidates.addAll(
                        CredentialsProvider.lookupCredentials(type, run.getParent(), ACL.SYSTEM, domainRequirements)
                );
            }
            return CredentialsMatchers.firstOrNull(candidates, CredentialsMatchers.withId(id));
        }
        // this is a parameter and not the default value, we need to determine who triggered the build
        final Map.Entry<User, Run<?, ?>> triggeredBy = triggeredBy(run);
        final Authentication a = triggeredBy == null ? Jenkins.ANONYMOUS : triggeredBy.getKey().impersonate();
        List<C> candidates = new ArrayList<C>();
        if (triggeredBy != null && run == triggeredBy.getValue()
                && run.getACL().hasPermission(a, CredentialsProvider.USE_OWN)) {
            // the user triggered this job directly and they are allowed to supply their own credentials, so
            // add those into the list. We do not want to follow the chain for the user's authentication
            // though, as there is no way to limit how far the passed-through parameters can be used
            candidates.addAll(CredentialsProvider.lookupCredentials(type, run.getParent(), a, domainRequirements));
        }
        if (run.getACL().hasPermission(a, CredentialsProvider.USE_ITEM)) {
            // the triggering user is allowed to use the item's credentials, so add those into the list
            // we use the default authentication of the job as those are the only ones that can be configured
            // if a different strategy is in play it doesn't make sense to consider the run-time authentication
            // as you would have no way to configure it
            Authentication runAuth = CredentialsProvider.getDefaultAuthenticationOf(run.getParent());
            // we want the credentials available to the user the build is running as
            candidates.addAll(
                    CredentialsProvider.lookupCredentials(type, run.getParent(), runAuth, domainRequirements)
            );
            // if that user can use the item's credentials, add those in too
            if (runAuth != ACL.SYSTEM && run.getACL().hasPermission(runAuth, CredentialsProvider.USE_ITEM)) {
                candidates.addAll(
                        CredentialsProvider.lookupCredentials(type, run.getParent(), ACL.SYSTEM, domainRequirements)
                );
            }
        }
        return CredentialsMatchers.firstOrNull(candidates, CredentialsMatchers.withId(id));
    }

    private static Map.Entry<User, Run<?, ?>> triggeredBy(Run<?, ?> run) {
        Cause.UserIdCause cause = run.getCause(Cause.UserIdCause.class);
        if (cause != null) {
            User u = User.get(cause.getUserId(), false, Collections.emptyMap());
            return u == null ? null : new AbstractMap.SimpleImmutableEntry<User, Run<?, ?>>(u, run);
        }
        Cause.UpstreamCause c = run.getCause(Cause.UpstreamCause.class);
        run = (c != null) ? c.getUpstreamRun() : null;
        while (run != null) {
            cause = run.getCause(Cause.UserIdCause.class);
            if (cause != null) {
                User u = User.get(cause.getUserId(), false, Collections.emptyMap());
                return u == null ? null : new AbstractMap.SimpleImmutableEntry<User, Run<?, ?>>(u, run);
            }
            c = run.getCause(Cause.UpstreamCause.class);

            run = (c != null) ? c.getUpstreamRun() : null;
        }
        return null;
    }

    public static ExtensionList<CredentialsProvider> all() {
        return ExtensionList.lookup(CredentialsProvider.class);
    }

    /**
     * Returns the scopes allowed for credentials stored within the specified object or {@code null} if the
     * object is not relevant for scopes and the object's container should be considered instead.
     *
     * @param object the object.
     * @return the set of scopes that are relevant for the object or {@code null} if the object is not a credentials
     * container.
     */
    public Set<CredentialsScope> getScopes(ModelObject object) {
        return null;
    }

    /**
     * Returns the {@link CredentialsStore} that this {@link CredentialsProvider} maintains specifically for this
     * {@link ModelObject} or {@code null} if either the object is not a credentials container or this
     * {@link CredentialsProvider} does not maintain a store specifically bound to this {@link ModelObject}.
     *
     * @param object the {@link Item} or {@link ItemGroup} or {@link User} that the store is being requested of.
     * @return either {@code null} or a scoped {@link CredentialsStore} where
     * {@link com.cloudbees.plugins.credentials.CredentialsStore#getContext()} {@code == object}.
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
     * @param <C>            the credentials type.
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
     * @param <C>                the credentials type.
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
     * @param <C>            the credentials type.
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
     * @param <C>                the credentials type.
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

}

