/*
 * The MIT License
 *
 * Copyright (c) 2011-2016, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.plugins.credentials.builds.CredentialsParameterBinding;
import com.cloudbees.plugins.credentials.builds.CredentialsParameterBinder;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.fingerprints.ItemCredentialsFingerprintFacet;
import com.cloudbees.plugins.credentials.fingerprints.NodeCredentialsFingerprintFacet;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Fingerprint;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.security.SecurityRealm;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static com.cloudbees.plugins.credentials.CredentialsStoreAction.FINGERPRINT_XML;

/**
 * An extension point for providing {@link Credentials}.
 */
public abstract class CredentialsProvider extends Descriptor<CredentialsProvider>
        implements ExtensionPoint, Describable<CredentialsProvider>, IconSpec {

    /**
     * A {@link CredentialsProvider} that does nothing for use as a marker
     *
     * @since 2.1.1
     */
    public static final CredentialsProvider NONE = new CredentialsProvider() {};

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
     * The system property name corresponding to {@link #FINGERPRINT_ENABLED}.
     */
    private static final String FINGERPRINT_ENABLED_NAME = CredentialsProvider.class.getSimpleName() + ".fingerprintEnabled";
    
    /**
     * Control if the fingerprints must be used or not. 
     * By default they are activated and thus allow the tracking of credentials usage.
     * In case of performance troubles in some weird situation, you can disable the behavior by setting it to {@code false}.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    @Restricted(NoExternalUse.class)
    /* package-protected */ static /* not final */ boolean FINGERPRINT_ENABLED = Boolean.parseBoolean(System.getProperty(FINGERPRINT_ENABLED_NAME, "true"));
    
    /**
     * Default constructor.
     */
    @SuppressWarnings("unchecked")
    public CredentialsProvider() {
        super(Descriptor.self());
    }

    /**
     * Returns all the registered {@link com.cloudbees.plugins.credentials.Credentials} descriptors.
     *
     * @return all the registered {@link com.cloudbees.plugins.credentials.Credentials} descriptors.
     */
    public static DescriptorExtensionList<Credentials, CredentialsDescriptor> allCredentialsDescriptors() {
        return Jenkins.get().getDescriptorList(Credentials.class);
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItem(Class, Item, Authentication, List)}
     * or {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication, List)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type) {
        return lookupCredentials(type, (Item) null, ACL.SYSTEM);
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItem(Class, Item, Authentication, List)},
     * {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication, List)}
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable org.acegisecurity.Authentication authentication) {
        return lookupCredentials(type, Jenkins.get(), authentication);
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItem(Class, Item, Authentication, List)} instead.
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item) {
        return item == null
                ? lookupCredentials(type, Jenkins.get(), ACL.SYSTEM)
                : lookupCredentials(type, item, ACL.SYSTEM);
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication, List)} instead.
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup) {
        return lookupCredentials(type, itemGroup, ACL.SYSTEM);
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication)} instead.
     */
    @Deprecated
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup,
                                                                    @Nullable org.acegisecurity.Authentication authentication) {
        return lookupCredentialsInItemGroup(type, itemGroup, authentication == null ? null : authentication.toSpring(), Collections.emptyList());
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItem(Class, Item, Authentication)} instead.
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item,
                                                                    @Nullable org.acegisecurity.Authentication authentication) {
        return lookupCredentialsInItem(type, item, authentication == null ? null : authentication.toSpring(), Collections.emptyList());
    }

    /**
     * @deprecated Use {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication)} or {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication, List)}.
     */
    @Deprecated
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup,
                                                                    @Nullable org.acegisecurity.Authentication authentication,
                                                                    @Nullable DomainRequirement... domainRequirements) {
        return lookupCredentialsInItemGroup(type, itemGroup, authentication == null ? null : authentication.toSpring(), Arrays.asList(domainRequirements == null ? new DomainRequirement[0] : domainRequirements));
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the {@link Item}s in the specified {@link ItemGroup}.
     *
     * @param type               the type of credentials to get.
     * @param itemGroup          the item group.
     * @param authentication     the authentication.
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since TODO
     */
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentialsInItemGroup(@NonNull Class<C> type,
                                                                               @Nullable ItemGroup itemGroup,
                                                                               @Nullable Authentication authentication) {
        return lookupCredentialsInItemGroup(type, itemGroup, authentication, List.of());
    }

    /**
     * @deprecated Use {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication, List)} instead.
     */
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    @Deprecated
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable ItemGroup itemGroup,
                                                                    @Nullable org.acegisecurity.Authentication authentication,
                                                                    @Nullable List<DomainRequirement> domainRequirements) {
        return lookupCredentialsInItemGroup(type, itemGroup, authentication == null ? null : authentication.toSpring(), domainRequirements);
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
     * @since TODO
     */
    @NonNull
    @SuppressWarnings({"unchecked", "unused"}) // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentialsInItemGroup(@NonNull Class<C> type,
                                                                               @Nullable ItemGroup itemGroup,
                                                                               @Nullable Authentication authentication,
                                                                               @Nullable List<DomainRequirement> domainRequirements) {
        Objects.requireNonNull(type);
        Jenkins jenkins = Jenkins.get();
        itemGroup = itemGroup == null ? jenkins : itemGroup;
        authentication = authentication == null ? ACL.SYSTEM2 : authentication;
        domainRequirements = domainRequirements
                == null ? Collections.emptyList() : domainRequirements;
        CredentialsResolver<Credentials, C> resolver = CredentialsResolver.getResolver(type);
        if (resolver != null) {
            LOGGER.log(Level.FINE, "Resolving legacy credentials of type {0} with resolver {1}",
                    new Object[]{type, resolver});
            final List<Credentials> originals =
                    lookupCredentialsInItemGroup(resolver.getFromClass(), itemGroup, authentication, domainRequirements);
            LOGGER.log(Level.FINE, "Original credentials for resolving: {0}", originals);
            return resolver.resolve(originals);
        }
        List<C> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (CredentialsProvider provider : all()) {
            if (provider.isEnabled(itemGroup) && provider.isApplicable(type)) {
                try {
                    for (C c : provider.getCredentialsInItemGroup(type, itemGroup, authentication, domainRequirements)) {
                        if (!(c instanceof IdCredentials) || ids.add(((IdCredentials) c).getId())) {
                            // if IdCredentials, only add if we haven't added already
                            // if not IdCredentials, always add
                            result.add(c);
                        }
                    }
                } catch (NoClassDefFoundError e) {
                    LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                            + " likely due to missing optional dependency", e);
                }
            }
        }
        return result;
    }

    /**
     * @deprecated Use {@link #listCredentialsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)} instead.
     */
    @Deprecated
    public static <C extends IdCredentials> ListBoxModel listCredentials(@NonNull Class<C> type,
                                                                         @Nullable ItemGroup itemGroup,
                                                                         @Nullable org.acegisecurity.Authentication authentication,
                                                                         @Nullable List<DomainRequirement>
                                                                                 domainRequirements,
                                                                         @Nullable CredentialsMatcher matcher) {
        return listCredentialsInItemGroup(type, itemGroup, authentication == null ? null : authentication.toSpring(), domainRequirements, matcher);
    }

    /**
     * Returns a {@link ListBoxModel} of all credentials which are available to the specified {@link Authentication}
     * for use by the {@link Item}s in the specified {@link ItemGroup}.
     *
     * @param type               the type of credentials to get.
     * @param authentication     the authentication.
     * @param itemGroup          the item group.
     * @param domainRequirements the credential domains to match.
     * @param matcher            the additional filtering to apply to the credentials
     * @param <C>                the credentials type.
     * @return the {@link ListBoxModel} of {@link IdCredentials#getId()} with the corresponding display names as
     * provided by {@link CredentialsNameProvider}.
     * @since TODO
     */
    public static <C extends IdCredentials> ListBoxModel listCredentialsInItemGroup(@NonNull Class<C> type,
                                                                                    @Nullable ItemGroup itemGroup,
                                                                                    @Nullable Authentication authentication,
                                                                                    @Nullable List<DomainRequirement>
                                                                                 domainRequirements,
                                                                                    @Nullable CredentialsMatcher matcher) {
        Objects.requireNonNull(type);
        Jenkins jenkins = Jenkins.get();
        itemGroup = itemGroup == null ? jenkins : itemGroup;
        authentication = authentication == null ? ACL.SYSTEM2 : authentication;
        domainRequirements =
                domainRequirements == null ? Collections.emptyList() : domainRequirements;
        matcher = matcher == null ? CredentialsMatchers.always() : matcher;
        CredentialsResolver<Credentials, C> resolver = CredentialsResolver.getResolver(type);
        if (resolver != null && IdCredentials.class.isAssignableFrom(resolver.getFromClass())) {
            LOGGER.log(Level.FINE, "Listing legacy credentials of type {0} identified by resolver {1}",
                    new Object[]{type, resolver});
            return listCredentialsInItemGroup((Class) resolver.getFromClass(), itemGroup, authentication, domainRequirements,
                    matcher);
        }
        ListBoxModel result = new ListBoxModel();
        Set<String> ids = new HashSet<>();
        for (CredentialsProvider provider : all()) {
            if (provider.isEnabled(itemGroup) && provider.isApplicable(type)) {
                try {
                    for (ListBoxModel.Option option : provider.getCredentialIdsInItemGroup(
                            type, itemGroup, authentication, domainRequirements, matcher)
                            ) {
                        if (ids.add(option.value)) {
                            result.add(option);
                        }
                    }
                } catch (NoClassDefFoundError e) {
                    LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                            + " likely due to missing optional dependency", e);
                }
            }
        }
        result.sort(new ListBoxModelOptionComparator());
        return result;
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication)} or {@link #lookupCredentialsInItemGroup(Class, ItemGroup, Authentication, List)}.
     */
    @Deprecated
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item,
                                                                    @Nullable org.acegisecurity.Authentication authentication,
                                                                    DomainRequirement... domainRequirements) {
        return lookupCredentialsInItem(type, item, authentication == null ? null : authentication.toSpring(), Arrays.asList(domainRequirements));
    }

    /**
     * Returns all credentials which are available to the specified {@link Authentication}
     * for use by the specified {@link Item}.
     *
     * @param type               the type of credentials to get.
     * @param authentication     the authentication.
     * @param item               the item.
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since TODO
     */
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentialsInItem(@NonNull Class<C> type,
                                                                          @Nullable Item item,
                                                                          @Nullable Authentication authentication) {
        return lookupCredentialsInItem(type, item, authentication, List.of());
    }

    /**
     * @deprecated use {@link #lookupCredentialsInItem(Class, Item, Authentication, List)}
     */
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    @Deprecated
    public static <C extends Credentials> List<C> lookupCredentials(@NonNull Class<C> type,
                                                                    @Nullable Item item,
                                                                    @Nullable org.acegisecurity.Authentication authentication,
                                                                    @Nullable List<DomainRequirement>
                                                                            domainRequirements) {
        return lookupCredentialsInItem(type, item, authentication == null ? null : authentication.toSpring(), domainRequirements);
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
     * @since TODO
     */
    @NonNull
    @SuppressWarnings("unused") // API entry point for consumers
    public static <C extends Credentials> List<C> lookupCredentialsInItem(@NonNull Class<C> type,
                                                                          @Nullable Item item,
                                                                          @Nullable Authentication authentication,
                                                                          @Nullable List<DomainRequirement>
                                                                            domainRequirements) {
        Objects.requireNonNull(type);
        if (item == null) {
            return lookupCredentialsInItemGroup(type, Jenkins.get(), authentication, domainRequirements);
        }
        if (item instanceof ItemGroup) {
            return lookupCredentialsInItemGroup(type, (ItemGroup)item, authentication, domainRequirements);
        }
        authentication = authentication == null ? ACL.SYSTEM2 : authentication;
        domainRequirements = domainRequirements
                == null ? Collections.emptyList() : domainRequirements;
        CredentialsResolver<Credentials, C> resolver = CredentialsResolver.getResolver(type);
        if (resolver != null) {
            LOGGER.log(Level.FINE, "Resolving legacy credentials of type {0} with resolver {1}",
                    new Object[]{type, resolver});
            final List<Credentials> originals =
                    lookupCredentialsInItem(resolver.getFromClass(), item, authentication, domainRequirements);
            LOGGER.log(Level.FINE, "Original credentials for resolving: {0}", originals);
            return resolver.resolve(originals);
        }
        List<C> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (CredentialsProvider provider : all()) {
            if (provider.isEnabled(item) && provider.isApplicable(type)) {
                try {
                    List<C> credentials = provider.getCredentialsInItem(type, item, authentication, domainRequirements);
                    // also lookup credentials as SYSTEM if granted for this item
                    if (authentication != ACL.SYSTEM2
                            && (item.getACL().hasPermission2(authentication, CredentialsProvider.USE_ITEM)
                            || item.getACL().hasPermission2(authentication, CredentialsProvider.USE_OWN))) {
                        credentials.addAll(provider.getCredentialsInItem(type, item, ACL.SYSTEM2, domainRequirements));
                    }

                    for (C c: credentials) {
                        if (!(c instanceof IdCredentials) || ids.add(((IdCredentials) c).getId())) {
                            // if IdCredentials, only add if we haven't added already
                            // if not IdCredentials, always add
                            result.add(c);
                        }
                    }
                } catch (NoClassDefFoundError e) {
                    LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                            + " likely due to missing optional dependency", e);
                }
            }
        }
        return result;
    }

    /**
     * @deprecated Use {@link #listCredentialsInItem(Class, Item, Authentication, List, CredentialsMatcher)} instead.
     */
    @NonNull
    @Deprecated
    public static <C extends IdCredentials> ListBoxModel listCredentials(@NonNull Class<C> type,
                                                                         @Nullable Item item,
                                                                         @Nullable org.acegisecurity.Authentication authentication,
                                                                         @Nullable List<DomainRequirement>
                                                                                 domainRequirements,
                                                                         @Nullable CredentialsMatcher matcher) {
        return listCredentialsInItem(type, item, authentication == null ? null : authentication.toSpring(), domainRequirements, matcher);
    }

    /**
     * Returns a {@link ListBoxModel} of all credentials which are available to the specified {@link Authentication}
     * for use by the specified {@link Item}.
     *
     * @param type               the type of credentials to get.
     * @param authentication     the authentication.
     * @param item               the item.
     * @param domainRequirements the credential domains to match.
     * @param matcher            the additional filtering to apply to the credentials
     * @param <C>                the credentials type.
     * @return the {@link ListBoxModel} of {@link IdCredentials#getId()} with the corresponding display names as
     * provided by {@link CredentialsNameProvider}.
     * @since TODO
     */
    @NonNull
    public static <C extends IdCredentials> ListBoxModel listCredentialsInItem(@NonNull Class<C> type,
                                                                               @Nullable Item item,
                                                                               @Nullable Authentication authentication,
                                                                               @Nullable List<DomainRequirement>
                                                                                 domainRequirements,
                                                                               @Nullable CredentialsMatcher matcher) {
        Objects.requireNonNull(type);
        if (item == null) {
            return listCredentialsInItemGroup(type, Jenkins.get(), authentication, domainRequirements, matcher);
        }
        if (item instanceof ItemGroup) {
            return listCredentialsInItemGroup(type, (ItemGroup) item, authentication, domainRequirements, matcher);
        }
        authentication = authentication == null ? ACL.SYSTEM2 : authentication;
        domainRequirements = domainRequirements
                == null ? Collections.emptyList() : domainRequirements;
        CredentialsResolver<Credentials, C> resolver = CredentialsResolver.getResolver(type);
        if (resolver != null && IdCredentials.class.isAssignableFrom(resolver.getFromClass())) {
            LOGGER.log(Level.FINE, "Listing legacy credentials of type {0} identified by resolver {1}",
                    new Object[]{type, resolver});
            return listCredentialsInItem((Class) resolver.getFromClass(), item, authentication,
                    domainRequirements, matcher);
        }
        ListBoxModel result = new ListBoxModel();
        Set<String> ids = new HashSet<>();
        for (CredentialsProvider provider : all()) {
            if (provider.isEnabled(item) && provider.isApplicable(type)) {
                try {
                    ListBoxModel credentialIds = provider.getCredentialIdsInItem(type, item, authentication, domainRequirements, matcher);
                    // also lookup credentials with scope SYSTEM when user has grants for this item
                    if (authentication != ACL.SYSTEM2
                            && (item.getACL().hasPermission2(authentication, CredentialsProvider.USE_ITEM)
                            || item.getACL().hasPermission2(authentication, CredentialsProvider.USE_OWN))) {
                        credentialIds.addAll(provider.getCredentialIdsInItem(type, item, ACL.SYSTEM2, domainRequirements, matcher));
                    }
                    for (ListBoxModel.Option option : credentialIds) {
                        if (ids.add(option.value)) {
                            result.add(option);
                        }
                    }
                } catch (NoClassDefFoundError e) {
                    LOGGER.log(Level.FINE, "Could not retrieve provider credentials from " + provider
                            + " likely due to missing optional dependency", e);
                }
            }
        }
        result.sort(new ListBoxModelOptionComparator());
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
        object = CredentialsDescriptor.unwrapContext(object);
        Set<CredentialsScope> result = null;
        for (CredentialsProvider provider : all()) {
            if (provider.isEnabled(object)) {
                try {
                    Set<CredentialsScope> scopes = provider.getScopes(object);
                    if (scopes != null) {
                        // if multiple providers for the same object, then combine scopes
                        if (result == null) {
                            result = new LinkedHashSet<>();
                        }
                        result.addAll(scopes);
                    }
                } catch (NoClassDefFoundError e) {
                    // ignore optional dependency
                }
            }
        }
        return result;
    }

    /**
     * Tests if the supplied context has any credentials stores associated with it.
     *
     * @param context the context object.
     * @return {@code true} if and only if the supplied context has at least one {@link CredentialsStore} associated
     * with it.
     * @since 2.1.5
     */
    public static boolean hasStores(final ModelObject context) {
        for (CredentialsProvider p : all()) {
            if (p.isEnabled(context) && p.getStore(context) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a lazy {@link Iterable} of all the {@link CredentialsStore} instances contributing credentials to the
     * supplied object.
     *
     * @param context the {@link Item} or {@link ItemGroup} or {@link User} to get the {@link CredentialsStore}s of.
     * @return a lazy {@link Iterable} of all {@link CredentialsStore} instances.
     * @since 1.8
     */
    public static Iterable<CredentialsStore> lookupStores(final ModelObject context) {
        final ExtensionList<CredentialsProvider> providers = all();
        return () -> new Iterator<CredentialsStore>() {
            private ModelObject current = context;
            private Iterator<CredentialsProvider> iterator = providers.iterator();
            private CredentialsStore next;

            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                while (current != null) {
                    while (iterator.hasNext()) {
                        CredentialsProvider p = iterator.next();
                        if (!p.isEnabled(context)) {
                            continue;
                        }
                        next = p.getStore(current);
                        if (next != null) {
                            return true;
                        }
                    }
                    // now walk up the model object tree
                    // TODO make this an extension point perhaps ContextResolver could help
                    if (current instanceof Item) {
                        current = ((Item) current).getParent();
                        iterator = providers.iterator();
                    } else if (current instanceof User) {
                        Jenkins jenkins = Jenkins.get();
                        Authentication a;
                        if (jenkins.hasPermission(USE_ITEM) && current == User.current()) {
                            // this is the fast path for the 99% of cases
                            a = Jenkins.getAuthentication2();
                        } else {
                            try {
                                a = ((User) current).impersonate2();
                            } catch (UsernameNotFoundException e) {
                                a = Jenkins.ANONYMOUS2;
                            }
                        }
                        if (current == User.current() && jenkins.getACL().hasPermission2(a, USE_ITEM)) {
                            current = jenkins;
                            iterator = providers.iterator();
                        } else {
                            current = null;
                        }
                    } else if (current instanceof Jenkins) {
                        // escape
                        current = null;
                    } else if (current instanceof ComputerSet) {
                        current = Jenkins.get();
                        iterator = providers.iterator();
                    } else if (current instanceof Computer) {
                        current = Jenkins.get();
                        iterator = providers.iterator();
                    } else if (current instanceof Node) {
                        current = Jenkins.get();
                        iterator = providers.iterator();
                    } else {
                        // fall back to Jenkins as the ultimate parent of everything else
                        current = Jenkins.get();
                        iterator = providers.iterator();
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
        };
    }

    /**
     * Make a best effort to ensure that the supplied credential is a snapshot credential (i.e. self-contained and
     * does not reference any external stores). <b>WARNING:</b> May produce unusual results if presented an exotic
     * credential that implements multiple distinct credential types at the same time, e.g. a credential that is
     * simultaneously a TLS certificate and a SSH key pair and a GPG key pair all at the same time... unless the
     * author of that credential type also provides a {@link CredentialsSnapshotTaker} that can handle such a
     * triple play.
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
    /*package*/ static Authentication getDefaultAuthenticationOf2(Item item) {
        if (item instanceof Queue.Task) {
            return Tasks.getAuthenticationOf2((Queue.Task) item);
        } else {
            return ACL.SYSTEM2;
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
     * @param <C>                the credentials type.
     * @return the credential or {@code null} if either the credential cannot be found or the user triggering the run
     * is not permitted to use the credential in the context of the run.
     * @since TODO
     */
    @CheckForNull
    public static <C extends IdCredentials> C findCredentialById(@NonNull String id, @NonNull Class<C> type,
                                                                 @NonNull Run<?, ?> run) {
        return findCredentialById(id, type, run, List.of());
    }

    /**
     * @deprecated Use {@link #findCredentialById(String, Class, Run, List)} instead.
     */
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
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(run);

        // first we need to find out if this id is pre-selected or a parameter
        id = id.trim();
        boolean isParameter = false;
        boolean isDefaultValue = false;
        String inputUserId = null;
        final String parameterName;
        if (id.startsWith("${") && id.endsWith("}")) {
            // denotes explicitly that this is a parameterized credential
            parameterName = id.substring(2, id.length() - 1);
        } else {
            // otherwise, we can check to see if there is a matching credential parameter name that shadows an
            // existing global credential id
            parameterName = id;
        }
        final CredentialsParameterBinder binder = CredentialsParameterBinder.getOrCreate(run);
        final CredentialsParameterBinding binding = binder.forParameterName(parameterName);
        if (binding != null) {
            isParameter = true;
            inputUserId = binding.getUserId();
            isDefaultValue = binding.isDefaultValue();
            id = Util.fixNull(binding.getCredentialsId());
        }
        // non parameters or default parameter values can only come from the job's context
        if (!isParameter || isDefaultValue) {
            // we use the default authentication of the job as those are the only ones that can be configured
            // if a different strategy is in play it doesn't make sense to consider the run-time authentication
            // as you would have no way to configure it
            Authentication runAuth = CredentialsProvider.getDefaultAuthenticationOf2(run.getParent());
            // we want the credentials available to the user the build is running as
            List<C> candidates = new ArrayList<>(
                    CredentialsProvider.lookupCredentialsInItem(type, run.getParent(), runAuth, domainRequirements)
            );
            // if that user can use the item's credentials, add those in too
            if (runAuth != ACL.SYSTEM2 && run.hasPermission2(runAuth, CredentialsProvider.USE_ITEM)) {
                candidates.addAll(
                        CredentialsProvider.lookupCredentialsInItem(type, run.getParent(), ACL.SYSTEM2, domainRequirements)
                );
            }
            // TODO should this be calling track?
            return contextualize(type, CredentialsMatchers.firstOrNull(candidates, CredentialsMatchers.withId(id)), run);
        }
        // this is a parameter and not the default value, we need to determine who triggered the build
        final Map.Entry<User, Run<?, ?>> triggeredBy = triggeredBy(run);
        final Authentication a = triggeredBy == null ? Jenkins.ANONYMOUS2 : triggeredBy.getKey().impersonate2();
        List<C> candidates = new ArrayList<>();
        if (triggeredBy != null && run == triggeredBy.getValue() && run.hasPermission2(a, CredentialsProvider.USE_OWN)) {
            // the user triggered this job directly and they are allowed to supply their own credentials, so
            // add those into the list. We do not want to follow the chain for the user's authentication
            // though, as there is no way to limit how far the passed-through parameters can be used
            candidates.addAll(CredentialsProvider.lookupCredentialsInItem(type, run.getParent(), a, domainRequirements));
        }
        if (inputUserId != null) {
            final User inputUser = User.getById(inputUserId, false);
            if (inputUser != null) {
                final Authentication inputAuth = inputUser.impersonate2();
                if (run.hasPermission2(inputAuth, CredentialsProvider.USE_OWN)) {
                    candidates.addAll(CredentialsProvider.lookupCredentialsInItem(type, run.getParent(), inputAuth, domainRequirements));
                }
            }
        }
        if (run.hasPermission2(a, CredentialsProvider.USE_ITEM)) {
            // the triggering user is allowed to use the item's credentials, so add those into the list
            // we use the default authentication of the job as those are the only ones that can be configured
            // if a different strategy is in play it doesn't make sense to consider the run-time authentication
            // as you would have no way to configure it
            Authentication runAuth = CredentialsProvider.getDefaultAuthenticationOf2(run.getParent());
            // we want the credentials available to the user the build is running as
            candidates.addAll(
                    CredentialsProvider.lookupCredentialsInItem(type, run.getParent(), runAuth, domainRequirements)
            );
            // if that user can use the item's credentials, add those in too
            if (runAuth != ACL.SYSTEM2 && run.hasPermission2(runAuth, CredentialsProvider.USE_ITEM)) {
                candidates.addAll(
                        CredentialsProvider.lookupCredentialsInItem(type, run.getParent(), ACL.SYSTEM2, domainRequirements)
                );
            }
        }
        C result = CredentialsMatchers.firstOrNull(candidates, CredentialsMatchers.withId(id));
        // if the run has not completed yet then we can safely assume that the credential is being used for this run
        // so we will track it's usage. We use isLogUpdated() as it could be used during post production
        if (run.isLogUpdated()) {
            track(run, result);
        }
        return contextualize(type, result, run);
    }

    @CheckForNull
    private static <C extends Credentials> C contextualize(@NonNull Class<C> type, @CheckForNull C credentials, @NonNull Run<?, ?> run) {
        if (credentials != null) {
            Credentials contextualized = credentials.forRun(run);
            if (type.isInstance(contextualized)) {
                return type.cast(contextualized);
            } else {
                LOGGER.warning(() -> "Ignoring " + contextualized.getClass().getName() + " return value of " + credentials.getClass().getName() + ".forRun since it is not assignable to " + type.getName());
            }
        }
        return credentials;
    }

    /**
     * Identifies the {@link User} and {@link Run} that triggered the supplied {@link Run}.
     *
     * @param run the {@link Run} to find the trigger of.
     * @return the trigger of the supplied run or {@code null} if this could not be determined.
     */
    @CheckForNull
    private static Map.Entry<User, Run<?, ?>> triggeredBy(Run<?, ?> run) {
        Cause.UserIdCause cause = run.getCause(Cause.UserIdCause.class);
        if (cause != null) {
            User u = User.get(cause.getUserId(), false, Collections.emptyMap());
            return u == null ? null : new AbstractMap.SimpleImmutableEntry<>(u, run);
        }
        Cause.UpstreamCause c = run.getCause(Cause.UpstreamCause.class);
        run = (c != null) ? c.getUpstreamRun() : null;
        while (run != null) {
            cause = run.getCause(Cause.UserIdCause.class);
            if (cause != null) {
                User u = User.get(cause.getUserId(), false, Collections.emptyMap());
                return u == null ? null : new AbstractMap.SimpleImmutableEntry<>(u, run);
            }
            c = run.getCause(Cause.UpstreamCause.class);

            run = (c != null) ? c.getUpstreamRun() : null;
        }
        return null;
    }

    /**
     * Returns the list of all {@link CredentialsProvider}.
     *
     * @return the list of all {@link CredentialsProvider}.
     */
    public static ExtensionList<CredentialsProvider> all() {
        return ExtensionList.lookup(CredentialsProvider.class);
    }

    /**
     * Returns only those {@link CredentialsProvider} that are {@link #isEnabled()}.
     *
     * @return a list of {@link CredentialsProvider} that are {@link #isEnabled()}.
     * @since 2.0
     */
    public static List<CredentialsProvider> enabled() {
        return ExtensionList.lookup(CredentialsProvider.class)
                .stream()
                .filter(CredentialsProvider::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * Returns only those {@link CredentialsProvider} that are {@link #isEnabled()} within a specific context.
     *
     * @param context the context in which to get the list.
     * @return a list of {@link CredentialsProvider} that are {@link #isEnabled()}.
     * @since 2.0
     */
    public static List<CredentialsProvider> enabled(Object context) {
        return ExtensionList.lookup(CredentialsProvider.class)
                .stream()
                .filter(p -> p.isEnabled(context))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Descriptor<CredentialsProvider> getDescriptor() {
        return this;
    }

    /**
     * Returns {@code true} if this {@link CredentialsProvider} is enabled.
     *
     * @return {@code true} if this {@link CredentialsProvider} is enabled.
     * @since 2.0
     */
    public final boolean isEnabled() {
        return CredentialsProviderManager.isEnabled(this);
    }

    /**
     * Returns {@code true} if this {@link CredentialsProvider} is enabled in the specified context.
     *
     * @param context the context.
     * @return {@code true} if this {@link CredentialsProvider} is enabled in the specified context.
     * @since 2.0
     */
    public boolean isEnabled(Object context) {
        if (!isEnabled()) {
            return false;
        }
        for (DescriptorVisibilityFilter filter : DescriptorVisibilityFilter.all()) {
            if (!filter.filter(context, this)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getDisplayName() {
        return StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(getClass().getSimpleName()), ' ');
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        return "symbol-credentials plugin-credentials";
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
     * @deprecated use {@link #getCredentialsInItem(Class, Item, Authentication, List)} instead.
     */
    @NonNull
    @Deprecated
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                                   @Nullable ItemGroup itemGroup,
                                                                   @Nullable org.acegisecurity.Authentication authentication) {
        return getCredentialsInItemGroup(type, itemGroup, authentication == null ? null : authentication.toSpring(), List.of());
    }

    /**
     * @deprecated use {@link #getCredentialsInItem(Class, Item, Authentication, List)} instead.
     */
    @Deprecated
    @NonNull
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                          @Nullable ItemGroup itemGroup,
                                                          @Nullable org.acegisecurity.Authentication authentication,
                                                          @NonNull List<DomainRequirement> domainRequirements) {
        return getCredentialsInItemGroup(type, itemGroup, authentication == null ? null : authentication.toSpring(), domainRequirements);
    }


    /**
     * Returns the credentials provided by this provider which are available to the specified {@link Authentication}
     * for items in the specified {@link ItemGroup} and are appropriate for the specified {@link com.cloudbees
     * .plugins.credentials.domains.DomainRequirement}s.
     *
     * @param type               the type of credentials to return.
     * @param itemGroup          the item group (if {@code null} assume {@link Jenkins#get()}.
     * @param authentication     the authentication (if {@code null} assume {@link ACL#SYSTEM2}.
     * @param domainRequirements the credential domains to match (if the {@link CredentialsProvider} does not support
     *                           {@link DomainRequirement}s then it should
     *                           assume the match is true).
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since TODO
     */
    @NonNull
    @SuppressWarnings("deprecation")
    public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type,
                                                                     @Nullable ItemGroup itemGroup,
                                                                     @Nullable Authentication authentication,
                                                                     @NonNull List<DomainRequirement> domainRequirements) {
        if (Util.isOverridden(CredentialsProvider.class, getClass(), "getCredentials", Class.class, ItemGroup.class, org.acegisecurity.Authentication.class, List.class)) {
            return getCredentials(type, itemGroup, authentication == null ? null : org.acegisecurity.Authentication.fromSpring(authentication), domainRequirements);
        }
        if (Util.isOverridden(CredentialsProvider.class, getClass(), "getCredentials", Class.class, ItemGroup.class, org.acegisecurity.Authentication.class)) {
            return getCredentials(type, itemGroup, authentication == null ? null : org.acegisecurity.Authentication.fromSpring(authentication));
        }
        throw new AbstractMethodError("Implement getCredentialsInItemGroup from " + getClass());
    }

    /**
     * @deprecated Use {@link #getCredentialIdsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)} instead.
     */
    @NonNull
    @Deprecated
    public <C extends IdCredentials> ListBoxModel getCredentialIds(@NonNull Class<C> type,
                                                                   @Nullable ItemGroup itemGroup,
                                                                   @Nullable org.acegisecurity.Authentication authentication,
                                                                   @NonNull
                                                                   List<DomainRequirement> domainRequirements,
                                                                   @NonNull CredentialsMatcher matcher) {
        return getCredentialIdsInItemGroup(type, itemGroup, authentication == null ? null : authentication.toSpring(), domainRequirements, matcher);
    }

    /**
     * Returns a {@link ListBoxModel} of the credentials provided by this provider which are available to the
     * specified {@link Authentication} for items in the specified {@link ItemGroup} and are appropriate for the
     * specified {@link DomainRequirement}s.
     * <strong>NOTE:</strong> implementations are recommended to override this method if the actual secret information
     * is being stored external from Jenkins and the non-secret information can be accessed with lesser traceability
     * requirements. The default implementation just uses {@link #getCredentialsInItem(Class, Item, Authentication, List)}
     * to build the {@link ListBoxModel}. Handling the {@link CredentialsMatcher} may require standing up a proxy
     * instance to apply the matcher against if {@link CredentialsMatchers#describe(CredentialsMatcher)} returns
     * {@code null}
     *
     * @param <C>                the credentials type.
     * @param type               the type of credentials to return.
     * @param itemGroup          the item group (if {@code null} assume {@link Jenkins#get()}.
     * @param authentication     the authentication (if {@code null} assume {@link ACL#SYSTEM2}.
     * @param domainRequirements the credential domain to match.
     * @param matcher            the additional filtering to apply to the credentials
     * @return the {@link ListBoxModel} of {@link IdCredentials#getId()} with names provided by
     * {@link CredentialsNameProvider}.
     * @since TODO
     */
    @NonNull
    public <C extends IdCredentials> ListBoxModel getCredentialIdsInItemGroup(@NonNull Class<C> type,
                                                                              @Nullable ItemGroup itemGroup,
                                                                              @Nullable Authentication authentication,
                                                                              @NonNull
                                                                           List<DomainRequirement> domainRequirements,
                                                                              @NonNull CredentialsMatcher matcher) {
        return getCredentialsInItemGroup(type, itemGroup, authentication, domainRequirements)
                .stream()
                .filter(matcher::matches)
                .sorted(new CredentialsNameComparator())
                .map(c -> new ListBoxModel.Option(CredentialsNameProvider.name(c), c.getId()))
                .collect(Collectors.toCollection(ListBoxModel::new));
    }

    /**
     * @deprecated Use {@link #getCredentialsInItem(Class, Item, Authentication, List)} instead.
     */
    @Deprecated
    @NonNull
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                          @NonNull Item item,
                                                          @Nullable org.acegisecurity.Authentication authentication) {
        Objects.requireNonNull(item);
        return getCredentialsInItemFallback(type, item, authentication == null ? null : authentication.toSpring(), List.of());
    }

    /**
     * @deprecated Use {@link #getCredentialsInItem(Class, Item, Authentication, List)} instead.
     */
    @Deprecated
    @NonNull
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                          @NonNull Item item,
                                                          @Nullable org.acegisecurity.Authentication authentication,
                                                          @NonNull List<DomainRequirement> domainRequirements) {
        return getCredentialsInItemFallback(type, item, authentication == null ? null : authentication.toSpring(), domainRequirements);
    }

    /**
     * Returns the credentials provided by this provider which are available to the specified {@link Authentication}
     * for the specified {@link Item} and are appropriate for the specified {@link DomainRequirement}s.
     *
     * @param type               the type of credentials to return.
     * @param item               the item.
     * @param authentication     the authentication (if {@code null} assume {@link ACL#SYSTEM2}.
     * @param domainRequirements the credential domain to match.
     * @param <C>                the credentials type.
     * @return the list of credentials.
     * @since TODO
     */
    @NonNull
    public <C extends Credentials> List<C> getCredentialsInItem(@NonNull Class<C> type,
                                                                @NonNull Item item,
                                                                @Nullable Authentication authentication,
                                                                @NonNull List<DomainRequirement> domainRequirements) {
        if (Util.isOverridden(CredentialsProvider.class, getClass(), "getCredentials", Class.class, Item.class, org.acegisecurity.Authentication.class, List.class)) {
            return getCredentials(type, item, authentication == null ? null : org.acegisecurity.Authentication.fromSpring(authentication), domainRequirements);
        }
        if (Util.isOverridden(CredentialsProvider.class, getClass(), "getCredentials", Class.class, Item.class, org.acegisecurity.Authentication.class)) {
            return getCredentials(type, item, authentication == null ? null : org.acegisecurity.Authentication.fromSpring(authentication));
        }
        return getCredentialsInItemFallback(type, item, authentication, domainRequirements);
    }

    @NonNull
    private <C extends Credentials> List<C> getCredentialsInItemFallback(@NonNull Class<C> type, @NonNull Item item, @Nullable Authentication authentication, @NonNull List<DomainRequirement> domainRequirements) {
        return getCredentialsInItemGroup(type, item instanceof ItemGroup ? (ItemGroup) item : item.getParent(),
                authentication, domainRequirements);
    }

    /**
     * @deprecated Use {@link #getCredentialIdsInItem(Class, Item, Authentication, List, CredentialsMatcher)} instead.
     */
    @NonNull
    @Deprecated
    public <C extends IdCredentials> ListBoxModel getCredentialIds(@NonNull Class<C> type,
                                                                   @NonNull Item item,
                                                                   @Nullable org.acegisecurity.Authentication authentication,
                                                                   @NonNull List<DomainRequirement> domainRequirements,
                                                                   @NonNull CredentialsMatcher matcher) {
        return getCredentialIdsInItem(type, item, authentication == null ? null : authentication.toSpring(), domainRequirements, matcher);
    }

    /**
     * Returns a {@link ListBoxModel} of the credentials provided by this provider which are available to the
     * specified {@link Authentication} for the specified {@link Item} and are appropriate for the
     * specified {@link DomainRequirement}s.
     * <strong>NOTE:</strong> implementations are recommended to override this method if the actual secret information
     * is being stored external from Jenkins and the non-secret information can be accessed with lesser traceability
     * requirements. The default implementation just uses {@link #getCredentialsInItem(Class, Item, Authentication, List)}
     * to build the {@link ListBoxModel}. Handling the {@link CredentialsMatcher} may require standing up a proxy
     * instance to apply the matcher against.
     *
     * @param type               the type of credentials to return.
     * @param item               the item.
     * @param authentication     the authentication (if {@code null} assume {@link ACL#SYSTEM2}.
     * @param domainRequirements the credential domain to match.
     * @param matcher            the additional filtering to apply to the credentials
     * @param <C>                the credentials type.
     * @return the {@link ListBoxModel} of {@link IdCredentials#getId()} with names provided by
     * {@link CredentialsNameProvider}.
     * @since TODO
     */
    @NonNull
    public <C extends IdCredentials> ListBoxModel getCredentialIdsInItem(@NonNull Class<C> type,
                                                                         @NonNull Item item,
                                                                         @Nullable Authentication authentication,
                                                                         @NonNull List<DomainRequirement> domainRequirements,
                                                                         @NonNull CredentialsMatcher matcher) {
        if (item instanceof ItemGroup) {
            return getCredentialIdsInItemGroup(type, (ItemGroup) item, authentication, domainRequirements, matcher);
        }
        return getCredentialsInItem(type, item, authentication, domainRequirements)
                .stream()
                .filter(matcher::matches)
                .sorted(new CredentialsNameComparator())
                .map(c -> new ListBoxModel.Option(CredentialsNameProvider.name(c), c.getId()))
                .collect(Collectors.toCollection(ListBoxModel::new));
    }

    /**
     * Returns {@code true} if this {@link CredentialsProvider} can provide credentials of the supplied type.
     *
     * @param clazz the base type of {@link Credentials} to check.
     * @return {@code true} if and only if there is at least one {@link CredentialsDescriptor} matching the required
     * {@link Credentials} interface that {@link #isApplicable(Descriptor)}.
     * @since 2.0
     */
    public final boolean isApplicable(Class<? extends Credentials> clazz) {
        if (!isEnabled()) {
            return false;
        }
        for (CredentialsDescriptor d : ExtensionList.lookup(CredentialsDescriptor.class)) {
            if (clazz.isAssignableFrom(d.clazz) && isApplicable(d)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the supplied {@link Descriptor} is applicable to this {@link CredentialsProvider}.
     *
     * @param descriptor the {@link Descriptor} to check.
     * @return {@code true} if and only if the supplied {@link Descriptor} is applicable in this
     * {@link CredentialsProvider}.
     * @since 2.0
     */
    public final boolean isApplicable(Descriptor<?> descriptor) {
        if (!isEnabled()) {
            return false;
        }
        if (descriptor instanceof CredentialsDescriptor) {
            if (!((CredentialsDescriptor) descriptor).isApplicable(this)) {
                return false;
            }
        }
        for (DescriptorVisibilityFilter filter : DescriptorVisibilityFilter.all()) {
            if (!filter.filter(this, descriptor)) {
                return false;
            }
        }
        return _isApplicable(descriptor);
    }

    /**
     * {@link CredentialsProvider} subtypes can override this method to veto some {@link Descriptor}s
     * from being available from their store. This is often useful when you are building
     * a custom store that holds a specific type of credentials or where you want to limit the
     * number of choices given to the users.
     *
     * @param descriptor the {@link Descriptor} to check.
     * @return {@code true} if the supplied {@link Descriptor} is applicable in this {@link CredentialsProvider}
     * @since 2.0
     */
    protected boolean _isApplicable(Descriptor<?> descriptor) {
        return true;
    }

    /**
     * Returns the list of {@link CredentialsDescriptor} instances that are applicable within this
     * {@link CredentialsProvider}.
     *
     * @return the list of {@link CredentialsDescriptor} instances that are applicable within this
     * {@link CredentialsProvider}.
     * @since 2.0
     */
    public final List<CredentialsDescriptor> getCredentialsDescriptors() {
        return DescriptorVisibilityFilter.apply(this, ExtensionList.lookup(CredentialsDescriptor.class))
                .stream()
                .filter(this::_isApplicable)
                .collect(Collectors.toList());
    }

    /**
     * Checks if there is at least one {@link CredentialsDescriptor} applicable within this {@link CredentialsProvider}.
     *
     * @return {@code true} if and ony if there is at least one {@link CredentialsDescriptor} applicable within this
     * {@link CredentialsProvider}.
     * @since 2.0
     */
    public final boolean hasCredentialsDescriptors() {
        ExtensionList<DescriptorVisibilityFilter> filters = DescriptorVisibilityFilter.all();
        OUTER:
        for (CredentialsDescriptor d : ExtensionList.lookup(CredentialsDescriptor.class)) {
            for (DescriptorVisibilityFilter f : filters) {
                if (!f.filter(this, d)) {
                    // not visible, let's try the next descriptor
                    continue OUTER;
                }
            }
            if (_isApplicable(d)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves the {@link Fingerprint} for a specific credential.
     *
     * @param c the credential.
     * @return the {@link Fingerprint} or {@code null} if the credential has no fingerprint associated with it.
     * @throws IOException if the credential's fingerprint hash could not be computed.
     * @since 2.1.1
     */
    @CheckForNull
    public static Fingerprint getFingerprintOf(@NonNull Credentials c) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            DigestOutputStream out = new DigestOutputStream(OutputStream.nullOutputStream(), md5);
            try {
                FINGERPRINT_XML.toXML(c, new OutputStreamWriter(out, StandardCharsets.UTF_8));
            } finally {
                IOUtils.closeQuietly(out);
            }
            return Jenkins.get().getFingerprintMap().get(Util.toHexString(md5.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JLS mandates MD5 as a supported digest algorithm");
        }
    }

    /**
     * Creates a fingerprint that can be used to track the usage of a specific credential.
     *
     * @param c the credential to fingerprint.
     * @return the Fingerprint.
     * @throws IOException if the credential's fingerprint hash could not be computed.
     * @since 2.1.1
     */
    @NonNull
    public static Fingerprint getOrCreateFingerprintOf(@NonNull Credentials c) throws IOException {
        String pseudoFilename = String.format("Credential id=%s name=%s",
                c instanceof IdCredentials ? ((IdCredentials) c).getId() : "unknown", CredentialsNameProvider.name(c));
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            DigestOutputStream out = new DigestOutputStream(OutputStream.nullOutputStream(), md5);
            try {
                FINGERPRINT_XML.toXML(c, new OutputStreamWriter(out, StandardCharsets.UTF_8));
            } finally {
                IOUtils.closeQuietly(out);
            }
            return Jenkins.get().getFingerprintMap().getOrCreate(null, pseudoFilename, md5.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JLS mandates MD5 as a supported digest algorithm");
        }
    }

    /**
     * Track the usage of credentials in a specific build.
     *
     * @param build       the run to tag the fingerprint
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @CheckForNull
    public static <C extends Credentials> C track(@NonNull Run build, @CheckForNull C credentials) {
        if (credentials != null) {
            trackAll(build, Collections.singletonList(credentials));
        }
        return credentials;
    }

    /**
     * Track the usage of credentials in a specific build.
     *
     * @param build       the run to tag the fingerprint
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @SafeVarargs
    @NonNull
    public static <C extends Credentials> List<C> trackAll(@NonNull Run build, C... credentials) {
        if (credentials != null) {
            return trackAll(build, Arrays.asList(credentials));
        }
        return Collections.emptyList();
    }

    /**
     * Track the usage of credentials in a specific build.
     *
     * @param build       the run to tag the fingerprint
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @NonNull
    public static <C extends Credentials> List<C> trackAll(@NonNull Run build, @NonNull List<C> credentials) {
        if (CredentialsProvider.FINGERPRINT_ENABLED) {
            for (Credentials c : credentials) {
                if (c != null) {
                    try {
                        getOrCreateFingerprintOf(c).addFor(build);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINEST, "Could not track usage of " + c, e);
                    }
                }
            }
        } else {
            LOGGER.log(Level.FINEST, "TrackAll method (Run variant) called but fingerprints disabled by {0}", FINGERPRINT_ENABLED_NAME);
        }
        for (Credentials c : credentials) {
            CredentialsUseListener.fireUse(c, build);
        }
        return credentials;
    }

    /**
     * Track the usage of credentials in a specific node.
     * Would be used for example when launching an agent.
     * @param node        the node to tag the fingerprint
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @CheckForNull
    public static <C extends Credentials> C track(@NonNull Node node, @CheckForNull C credentials) {
        if (credentials != null) {
            trackAll(node, Collections.singletonList(credentials));
        }
        return credentials;
    }

    /**
     * Track the usage of credentials in a specific node.
     * Would be used for example when launching an agent.
     * @param node        the node to tag the fingerprint
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @SafeVarargs
    @NonNull
    public static <C extends Credentials> List<C> trackAll(@NonNull Node node, C... credentials) {
        if (credentials != null) {
            return trackAll(node, Arrays.asList(credentials));
        }
        return Collections.emptyList();
    }

    /**
     * Track the usage of credentials in a specific node.
     * Would be used for example when launching an agent.
     * @param node        the node to tag the fingerprint
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @NonNull
    public static <C extends Credentials> List<C> trackAll(@NonNull Node node, @NonNull List<C> credentials) {
        if (CredentialsProvider.FINGERPRINT_ENABLED) {
            long timestamp = System.currentTimeMillis();
            String nodeName = node.getNodeName();
            // Create a list of all current node names. The credential will only be
            // fingerprinted if it is one of these.
            // TODO (JENKINS-51694): This breaks tracking for cloud agents. We should
            // track those agents against the cloud instead of the node itself.
            Set<String> jenkinsNodeNames = new HashSet<>();
            for (Node n: Jenkins.get().getNodes()) {
                jenkinsNodeNames.add(n.getNodeName());
            }
            for (Credentials c : credentials) {
                if (c != null) {
                    try {
                        Fingerprint fingerprint = getOrCreateFingerprintOf(c);
                        BulkChange change = new BulkChange(fingerprint);
                        try {
                            Collection<FingerprintFacet> facets = fingerprint.getFacets();
                            // purge any old facets
                            long start = timestamp;
                            for (Iterator<FingerprintFacet> iterator = facets.iterator(); iterator.hasNext(); ) {
                                FingerprintFacet f = iterator.next();
                                // For all the node-tracking credentials, check to see if we can remove
                                // older instances of these credential fingerprints, or from nodes which no longer exist
                                if (f instanceof NodeCredentialsFingerprintFacet) {
                                    // Remove older instances
                                    if (StringUtils.equals(nodeName, ((NodeCredentialsFingerprintFacet) f).getNodeName())) {
                                        start = Math.min(start, f.getTimestamp());
                                        iterator.remove();
                                    // Remove unneeded instances
                                    } else if (!jenkinsNodeNames.contains(((NodeCredentialsFingerprintFacet) f).getNodeName())) {
                                        iterator.remove();
                                    }
                                }
                            }
                            // add in the new one if it is a valid current node
                            if (jenkinsNodeNames.contains(node.getNodeName())) {
                                facets.add(new NodeCredentialsFingerprintFacet(node, fingerprint, start, timestamp));
                            }
                        } finally {
                            change.commit();
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.FINEST, "Could not track usage of " + c, e);
                    }
                }
            }
        } else {
            LOGGER.log(Level.FINEST, "TrackAll method (Node variant) called but fingerprints disabled by {0}", FINGERPRINT_ENABLED_NAME);
        }
        for (Credentials c : credentials) {
            CredentialsUseListener.fireUse(c, node);
        }
        return credentials;
    }

    /**
     * Track the usage of credentials in a specific item but not associated with a specific build, for example SCM
     * polling.
     *
     * @param item        the item to tag the fingerprint against
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @CheckForNull
    public static <C extends Credentials> C track(@NonNull Item item, @CheckForNull C credentials) {
        if (credentials != null) {
            trackAll(item, Collections.singletonList(credentials));
        }
        return credentials;
    }

    /**
     * Track the usage of credentials in a specific item but not associated with a specific build, for example SCM
     * polling.
     *
     * @param item        the item to tag the fingerprint against
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @SafeVarargs
    @NonNull
    public static <C extends Credentials> List<C> trackAll(@NonNull Item item, C... credentials) {
        if (credentials != null) {
            return trackAll(item, Arrays.asList(credentials));
        }
        return Collections.emptyList();
    }

    /**
     * Track the usage of credentials in a specific item but not associated with a specific build, for example SCM
     * polling.
     *
     * @param item        the item to tag the fingerprint against
     * @param credentials the credentials to fingerprint.
     * @param <C>         the credentials type.
     * @return the supplied credentials for method chaining.
     * @since 2.1.1
     */
    @NonNull
    public static <C extends Credentials> List<C> trackAll(@NonNull Item item, @NonNull List<C> credentials) {
        if (CredentialsProvider.FINGERPRINT_ENABLED) {
            long timestamp = System.currentTimeMillis();
            String fullName = item.getFullName();
            for (Credentials c : credentials) {
                if (c != null) {
                    try {
                        Fingerprint fingerprint = getOrCreateFingerprintOf(c);
                        BulkChange change = new BulkChange(fingerprint);
                        try {
                            Collection<FingerprintFacet> facets = fingerprint.getFacets();
                            // purge any old facets
                            long start = timestamp;
                            for (Iterator<FingerprintFacet> iterator = facets.iterator(); iterator.hasNext(); ) {
                                FingerprintFacet f = iterator.next();
                                if (f instanceof ItemCredentialsFingerprintFacet && StringUtils
                                        .equals(fullName, ((ItemCredentialsFingerprintFacet) f).getItemFullName())) {
                                    start = Math.min(start, f.getTimestamp());
                                    iterator.remove();
                                }
                            }
                            // add in the new one
                            facets.add(new ItemCredentialsFingerprintFacet(item, fingerprint, start, timestamp));
                        } finally {
                            change.commit();
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.FINEST, "Could not track usage of " + c, e);
                    }
                }
            }
        } else {
            LOGGER.log(Level.FINEST, "TrackAll method (Item variant) called but fingerprints disabled by {0}", FINGERPRINT_ENABLED_NAME);
        }
        for (Credentials c : credentials) {
            CredentialsUseListener.fireUse(c, item);
        }
        return credentials;
    }

    /**
     * A helper method for Groovy Scripting to address use cases such as JENKINS-39317 where all credential stores
     * need to be resaved. As this is a potentially very expensive operation the method has been marked
     * {@link DoNotUse} in order to ensure that no plugin attempts to call this method. If invoking this method
     * from an {@code init.d} Groovy script, ensure that the call is guarded by a marker file such that
     *
     */
    @Restricted(DoNotUse.class) // Do not use from plugins
    public static void saveAll() {
        LOGGER.entering(CredentialsProvider.class.getName(), "saveAll");
        try {
            Jenkins jenkins = Jenkins.get();
            jenkins.checkPermission(Jenkins.ADMINISTER);
            LOGGER.log(Level.INFO, "Forced save credentials stores: Requested by {0}",
                    StringUtils.defaultIfBlank(Jenkins.getAuthentication2().getName(), "anonymous"));
            Timer.get().execute(() -> {
                try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
                    if (jenkins.getInitLevel().compareTo(InitMilestone.JOB_LOADED) < 0) {
                        LOGGER.log(Level.INFO, "Forced save credentials stores: Initialization has not completed");
                        while (jenkins.getInitLevel().compareTo(InitMilestone.JOB_LOADED) < 0) {
                            LOGGER.log(Level.INFO, "Forced save credentials stores: Sleeping for 1 second");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                LOGGER.log(Level.WARNING, "Forced save credentials stores: Aborting due to interrupt",
                                        e);
                                return;
                            }
                        }
                        LOGGER.log(Level.INFO, "Forced save credentials stores: Initialization has completed");
                    }
                    LOGGER.log(Level.INFO, "Forced save credentials stores: Processing Jenkins");
                    for (CredentialsStore s : lookupStores(jenkins)) {
                        try {
                            s.save();
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Forced save credentials stores: Could not save " + s, e);
                        }
                    }
                    LOGGER.log(Level.INFO, "Forced save credentials stores: Processing Items...");
                    int count = 0;
                    for (Item item : jenkins.getAllItems(Item.class)) {
                        count++;
                        if (count % 100 == 0) {
                            LOGGER.log(Level.INFO, "Forced save credentials stores: Processing Items ({0} processed)",
                                    count);
                        }
                        for (CredentialsStore s : lookupStores(item)) {
                            if (item == s.getContext()) {
                                // only save if the store is associated with this context item as otherwise will
                                // have been saved already / later
                                try {
                                    s.save();
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "Forced save credentials stores: Could not save " + s, e);
                                }
                            }
                        }
                    }
                    LOGGER.log(Level.INFO, "Forced save credentials stores: Processing Users...");
                    count = 0;
                    for (User user : User.getAll()) {
                        count++;
                        if (count % 100 == 0) {
                            LOGGER.log(Level.INFO, "Forced save credentials stores: Processing Users ({0} processed)",
                                    count);
                        }
                        // HACK ALERT we just want to access the user's stores, so we do just enough impersonation
                        // to ensure that User.current() == user
                        // while we could use User.impersonate() that would force a query against the backing
                        // SecurityRealm to revalidate
                        ACL.impersonate2(new UsernamePasswordAuthenticationToken(user.getId(), "",
                                Set.of(SecurityRealm.AUTHENTICATED_AUTHORITY2)));
                        for (CredentialsStore s : lookupStores(user)) {
                            if (user == s.getContext()) {
                                // only save if the store is associated with this context item as otherwise will
                                // have been saved already / later
                                try {
                                    s.save();
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "Forced save credentials stores: Could not save " + s, e);
                                }
                            }
                        }
                    }
                } finally {
                    LOGGER.log(Level.INFO, "Forced save credentials stores: Completed");
                }
            });
        } finally {
            LOGGER.exiting(CredentialsProvider.class.getName(), "saveAll");
        }
    }

    /**
     * A {@link Comparator} for {@link ListBoxModel.Option} instances.
     *
     * @since 2.1.0
     */
    private static class ListBoxModelOptionComparator implements Comparator<ListBoxModel.Option> {
        /**
         * The locale to compare with.
         */
        private final Locale locale;
        /**
         * The {@link Collator}
         */
        private transient Collator collator;

        public ListBoxModelOptionComparator() {
            StaplerRequest req = Stapler.getCurrentRequest();
            if (req != null) {
                locale = req.getLocale();
            } else {
                locale = Locale.getDefault();
            }
            collator = Collator.getInstance(locale);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(ListBoxModel.Option o1, ListBoxModel.Option o2) {
            return collator.compare(o1.name.toLowerCase(locale), o2.name.toLowerCase(locale));
        }
    }
}

