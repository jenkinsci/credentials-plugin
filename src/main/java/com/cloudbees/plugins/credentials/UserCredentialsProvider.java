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

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.security.Permission;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.always;

/**
 * A store of credentials tied to a specific {@link User}.
 */
@Extension
public class UserCredentialsProvider extends CredentialsProvider {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(UserCredentialsProperty.class.getName());

    /**
     * We only care about {@link CredentialsScope#USER} scoped credentials.
     */
    private static final Set<CredentialsScope> SCOPES = Collections.singleton(CredentialsScope.USER);

    /**
     * The empty properties that have not been saved yet.
     */
    @GuardedBy("self")
    private static final WeakHashMap<User, UserCredentialsProperty> emptyProperties = new WeakHashMap<>();


    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CredentialsScope> getScopes(ModelObject object) {
        if (object instanceof User) {
            return SCOPES;
        }
        return super.getScopes(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CredentialsStore getStore(@CheckForNull ModelObject object) {
        if (object instanceof User) {
            return new StoreImpl((User) object);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type,
                                                                     @Nullable ItemGroup itemGroup,
                                                                     @Nullable Authentication authentication,
                                                                     @NonNull List<DomainRequirement> domainRequirements) {
        // ignore itemGroup, as per-user credentials are available on any object
        if (authentication == null) {
            // assume ACL#SYSTEM
            authentication = ACL.SYSTEM2;
        }
        if (!ACL.SYSTEM2.equals(authentication)) {
            User user = User.get2(authentication);
            if (user != null) {
                UserCredentialsProperty property = user.getProperty(UserCredentialsProperty.class);
                if (property != null) {
                    // we need to impersonate if the requesting authentication is not the current authentication.
                    boolean needImpersonation = !user.equals(User.current());
                    Supplier<List<C>> credentials = () -> DomainCredentials
                                .getCredentials(property.getDomainCredentialsMap(), type, domainRequirements, always());
                    if (needImpersonation) {
                        try (ACLContext ac = ACL.as(user)) {
                            return credentials.get();
                        }
                    } else {
                        return credentials.get();
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        return "symbol-person";
    }

    /**
     * Need a user property to hold the user's personal credentials.
     */
    public static class UserCredentialsProperty extends UserProperty {

        /**
         * Old store of credentials
         *
         * @deprecated
         */
        @Deprecated
        private transient List<Credentials> credentials;

        /**
         * Our credentials.
         *
         * @since 1.5
         */
        @SuppressFBWarnings("IS2_INCONSISTENT_SYNC")
        private Map<Domain, List<Credentials>> domainCredentialsMap;

        /**
         * Backwards compatibility.
         *
         * @param credentials the credentials.
         * @deprecated
         */
        @Deprecated
        public UserCredentialsProperty(List<Credentials> credentials) {
            domainCredentialsMap = DomainCredentials.migrateListToMap(null, credentials);
        }

        /**
         * Constructor for stapler.
         *
         * @param domainCredentials the credentials.
         * @since 1.5
         */
        @DataBoundConstructor
        public UserCredentialsProperty(DomainCredentials[] domainCredentials) {
            domainCredentialsMap = DomainCredentials.asMap(Arrays.asList(domainCredentials));
        }

        /**
         * Resolve old data store into new data store.
         *
         * @since 1.5
         */
        @SuppressWarnings("deprecation")
        private Object readResolve() {
            if (domainCredentialsMap == null) {
                return new UserCredentialsProperty(credentials);
            }
            return this;
        }

        /**
         * Helper method.
         *
         * @param type type of credentials to get.
         * @param <C>  type of credentials to get.
         * @return the subset of the user's credentials that are of the specified type.
         */
        public <C extends Credentials> List<C> getCredentials(Class<C> type) {
            checkPermission(CredentialsProvider.VIEW);
            return getCredentials()
                    .stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .collect(Collectors.toList());
        }

        /**
         * Gets all the user's credentials.
         *
         * @return all the user's credentials.
         */
        @SuppressWarnings("unused") // used by stapler
        public List<Credentials> getCredentials() {
            checkPermission(CredentialsProvider.VIEW);
            return domainCredentialsMap.get(Domain.global());
        }

        /**
         * Returns the {@link com.cloudbees.plugins.credentials.domains.DomainCredentials}
         *
         * @return the {@link com.cloudbees.plugins.credentials.domains.DomainCredentials}
         * @since 1.5
         */
        @SuppressWarnings("unused") // used by stapler
        public List<DomainCredentials> getDomainCredentials() {
            checkPermission(CredentialsProvider.VIEW);
            return DomainCredentials.asList(getDomainCredentialsMap());
        }

        /**
         * The map of domain credentials.
         *
         * @return The map of domain credentials.
         * @since 1.5
         */
        @SuppressWarnings("deprecation")
        @NonNull
        public synchronized Map<Domain, List<Credentials>> getDomainCredentialsMap() {
            checkPermission(CredentialsProvider.VIEW);
            return domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
        }

        /**
         * Sets the map of domain credentials.
         *
         * @param domainCredentialsMap the map of domain credentials.
         * @since 1.5
         */
        public synchronized void setDomainCredentialsMap(Map<Domain, List<Credentials>> domainCredentialsMap) {
            checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            this.domainCredentialsMap = DomainCredentials.toCopyOnWriteMap(domainCredentialsMap);
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean addDomain(@NonNull Domain domain, List<Credentials> credentials)
                throws IOException {
            checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                List<Credentials> list = domainCredentialsMap.get(domain);
                boolean modified = false;
                for (Credentials c : credentials) {
                    if (list.contains(c)) {
                        continue;
                    }
                    list.add(c);
                    modified = true;
                }
                if (modified) {
                    save();
                }
                return modified;
            } else {
                domainCredentialsMap.put(domain, new ArrayList<>(credentials));
                save();
                return true;
            }
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean removeDomain(@NonNull Domain domain) throws IOException {
            checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                domainCredentialsMap.remove(domain);
                save();
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement)
                throws IOException {
            checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(current)) {
                domainCredentialsMap.put(replacement, domainCredentialsMap.remove(current));
                save();
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials)
                throws IOException {
            checkPermission(CredentialsProvider.CREATE);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                List<Credentials> list = domainCredentialsMap.get(domain);
                if (list.contains(credentials)) {
                    return false;
                }
                list.add(credentials);
                save();
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        @NonNull
        private synchronized List<Credentials> getCredentials(@NonNull Domain domain) {
            if (user.equals(User.current())) {
                List<Credentials> list = getDomainCredentialsMap().get(domain);
                if (list == null || list.isEmpty()) {
                    return Collections.emptyList();
                }
                return Collections.unmodifiableList(new ArrayList<>(list));
            }
            return Collections.emptyList();
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials)
                throws IOException {
            checkPermission(CredentialsProvider.DELETE);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                List<Credentials> list = domainCredentialsMap.get(domain);
                if (!list.contains(credentials)) {
                    return false;
                }
                list.remove(credentials);
                save();
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                                       @NonNull Credentials replacement) throws IOException {
            checkPermission(CredentialsProvider.UPDATE);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                if (current instanceof IdCredentials || replacement instanceof IdCredentials) {
                    if (!current.equals(replacement)) {
                        throw new IllegalArgumentException("Credentials' IDs do not match, will not update.");
                    }
                }
                List<Credentials> list = domainCredentialsMap.get(domain);
                int index = list.indexOf(current);
                if (index == -1) {
                    return false;
                }
                list.set(index, replacement);
                save();
                return true;
            }
            return false;
        }

        /**
         * Helper method to check the specified permission.
         *
         * @param p the permission to check.
         */
        private void checkPermission(Permission p) {
            if (user.equals(User.current())) {
                user.checkPermission(p);
            } else {
                throw new AccessDeniedException3(Jenkins.getAuthentication2(), p);
            }
        }

        /**
         * Save all changes.
         *
         * @throws IOException if something goes wrong.
         */
        private void save() throws IOException {
            if (user.equals(User.current())) {
                UserCredentialsProperty property = user.getProperty(UserCredentialsProperty.class);
                if (property == null) {
                    Map<Domain, List<Credentials>> domainCredentialsMap;
                    synchronized (this) {
                        // peek to save manipulating the object further
                        domainCredentialsMap = this.domainCredentialsMap;
                    }
                    if (domainCredentialsMap == null || domainCredentialsMap.isEmpty()) {
                        // nothing to do here we do not want to persist the empty property and nobody
                        // has even called getDomainCredentialsMap so the global domain has not been populated
                        return;
                    } else if (domainCredentialsMap.size() == 1) {
                        List<Credentials> global = domainCredentialsMap.get(Domain.global());
                        if (global != null && global.isEmpty()) {
                            // nothing to do here we do not want to persist the empty property
                            return;
                        }
                    }
                    synchronized (emptyProperties) {
                        user.addProperty(this);
                        emptyProperties.remove(user);
                    }
                }
                user.save();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public UserProperty reconfigure(StaplerRequest req, JSONObject form) {
            return this;
        }

        /**
         * Allow setting the user.
         * @param user the user.
         */
        private void _setUser(User user) {
            this.user = user;
        }

        /**
         * Our user property descriptor.
         */
        @Extension
        @SuppressWarnings("unused") // used by Jenkins
        public static class DescriptorImpl extends UserPropertyDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public UserProperty newInstance(User user) {
                return new UserCredentialsProperty(new DomainCredentials[0]);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isEnabled() {
                return !all().isEmpty();
            }

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.UserCredentialsProvider_DisplayName();
            }

            /**
             * Gets all the credentials descriptors.
             *
             * @return all the credentials descriptors.
             * @since 1.5
             */
            @SuppressWarnings("unused") // used by stapler
            public DescriptorExtensionList<Credentials, CredentialsDescriptor> getCredentialDescriptors() {
                // TODO delete me
                return CredentialsProvider.allCredentialsDescriptors();
            }

            /**
             * Gets all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
             *
             * @return all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
             * @since 1.5
             */
            @SuppressWarnings("unused") // used by stapler
            public DescriptorExtensionList<DomainSpecification, Descriptor<DomainSpecification>>
            getSpecificationDescriptors() {
                return Jenkins.get().getDescriptorList(DomainSpecification.class);
            }
        }
    }

    @ExportedBean
    public static class UserFacingAction extends CredentialsStoreAction {

        /**
         * The user that this action belongs to.
         */
        private final StoreImpl store;

        /**
         * Constructor.
         *
         * @param store the {@link CredentialsStore} that is being exposed.
         */
        public UserFacingAction(StoreImpl store) {
            this.store = store;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Exported
        public CredentialsStore getStore() {
            return store;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconFileName() {
            return isVisible()
                    ? "symbol-person"
                    : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return isVisible()
                    ? "symbol-person"
                    : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.UserCredentialsProvider_UserFacingAction_DisplayName();
        }
    }

    /**
     * Our implementation
     */
    public static class StoreImpl extends CredentialsStore {

        /**
         * The user that this store belongs to.
         */
        private final User user;

        /**
         * Our store action.
         */
        private final UserFacingAction storeAction;

        /**
         * The property;
         */
        private transient UserCredentialsProperty property;

        /**
         * Constructor.
         *
         * @param user the user.
         */
        private StoreImpl(User user) {
            this.user = user;
            this.storeAction = new UserFacingAction(this);
        }

        /**
         * {@inheritDoc}
         */
        @Nullable
        @Override
        public CredentialsStoreAction getStoreAction() {
            return storeAction;
        }

        /**
         * Looks up the {@link UserCredentialsProperty} that we store the credentials in.
         *
         * @return the {@link UserCredentialsProperty} that we store the credentials in.
         */
        private UserCredentialsProperty getInstance() {
            if (property == null) {
                UserCredentialsProperty property = user.getProperty(UserCredentialsProperty.class);
                if (property == null) {
                    synchronized (emptyProperties) {
                        // need to recheck as UserCredentialsProperty#save() may have added while we waited for the lock
                        property = user.getProperty(UserCredentialsProperty.class);
                        if (property == null) {
                            property = emptyProperties.get(user);
                            if (property == null) {
                                property = new UserCredentialsProperty(new DomainCredentials[0]);
                                property._setUser(user);
                                emptyProperties.put(user, property);
                            }
                        }
                    }
                }
                this.property = property; // idempotent write
            }
            return property;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public ModelObject getContext() {
            return user;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
            return getACL().hasPermission2(a, permission);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public ACL getACL() {
            return new ACL() {
                @Override
                public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                    return user.equals(User.getById(a.getName(), true)) && user.getACL().hasPermission2(a, permission);
                }
            };
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        @Exported
        public List<Domain> getDomains() {
            return Collections.unmodifiableList(new ArrayList<>(
                    getInstance().getDomainCredentialsMap().keySet()
            ));
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        @Exported
        public List<Credentials> getCredentials(@NonNull Domain domain) {
            return getInstance().getCredentials(domain);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException {
            return getInstance().addDomain(domain, credentials);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean removeDomain(@NonNull Domain domain) throws IOException {
            return getInstance().removeDomain(domain);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
            return getInstance().updateDomain(current, replacement);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException {
            return getInstance().addCredentials(domain, credentials);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException {
            return getInstance().removeCredentials(domain, credentials);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                         @NonNull Credentials replacement) throws IOException {
            return getInstance().updateCredentials(domain, current, replacement);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getRelativeLinkToContext() {
            StaplerRequest request = Stapler.getCurrentRequest();
            return URI.create(request.getContextPath() + "/" + user.getUrl() + "/").normalize().toString() ;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void save() throws IOException {
            if (BulkChange.contains(this)) {
                return;
            }
            getInstance().save();
        }
    }

}
