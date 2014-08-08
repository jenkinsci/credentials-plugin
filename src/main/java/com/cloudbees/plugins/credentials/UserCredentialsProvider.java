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

import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.TransientUserActionFactory;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @Nullable ItemGroup itemGroup,
                                                          @Nullable Authentication authentication) {
        return getCredentials(type, itemGroup, authentication, Collections.<DomainRequirement>emptyList());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                          @Nullable ItemGroup itemGroup,
                                                          @Nullable Authentication authentication,
                                                          @NonNull List<DomainRequirement> domainRequirements) {
        // ignore itemGroup, as per-user credentials are available on any object
        if (authentication == null) {
            // assume ACL#SYSTEM
            authentication = ACL.SYSTEM;
        }
        if (!ACL.SYSTEM.equals(authentication)) {
            User user;
            try {
                if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                    user = null;
                } else if (authentication == Jenkins.getAuthentication()) {
                    user = User.current();
                } else {
                    user = User.get(authentication.getName());
                }
            } catch (NullPointerException e) {
                LogRecord lr = new LogRecord(Level.FINE,
                        "Could not find user for specified authentication. User credentials lookup aborted");
                lr.setThrown(e);
                lr.setParameters(new Object[]{authentication});
                LOGGER.log(lr);
                user = null;
            }
            if (user != null) {
                UserCredentialsProperty property = user.getProperty(UserCredentialsProperty.class);
                if (property != null) {
                    return DomainCredentials
                            .getCredentials(property.getDomainCredentialsMap(), type, domainRequirements, always());
                }
            }
        }
        return new ArrayList<C>();
    }

    @Override
    public CredentialsStore getStore(@CheckForNull ModelObject object) {
        if (object instanceof User) {
            return new StoreImpl((User) object);
        }
        return null;
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
        private Map<Domain, List<Credentials>> domainCredentialsMap =
                new CopyOnWriteMap.Hash<Domain, List<Credentials>>();

        /**
         * Backwards compatibility.
         *
         * @param credentials the credentials.
         * @deprecated
         */
        @Deprecated
        public UserCredentialsProperty(List<Credentials> credentials) {
            domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
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
        private Object readResolve() throws ObjectStreamException {
            if (domainCredentialsMap == null) {
                domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
                credentials = null;
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
            List<C> result = new ArrayList<C>();
            for (Credentials credential : getCredentials()) {
                if (type.isInstance(credential)) {
                    result.add(type.cast(credential));
                }
            }
            return result;
        }

        /**
         * Gets all the user's credentials.
         *
         * @return all the user's credentials.
         */
        @SuppressWarnings("unused") // used by stapler
        public List<Credentials> getCredentials() {
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
            return DomainCredentials.asList(getDomainCredentialsMap());
        }

        /**
         * The Map of domain credentials.
         *
         * @since 1.5
         */
        @SuppressWarnings("deprecation")
        @NonNull
        public synchronized Map<Domain, List<Credentials>> getDomainCredentialsMap() {
            return domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
        }

        /**
         * Sets the map of domain credentials.
         *
         * @param domainCredentialsMap the map of domain credentials.
         * @since 1.5
         */
        public synchronized void setDomainCredentialsMap(Map<Domain, List<Credentials>> domainCredentialsMap) {
            this.domainCredentialsMap = DomainCredentials.toCopyOnWriteMap(domainCredentialsMap);
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException {
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
                domainCredentialsMap.put(domain, new ArrayList<Credentials>(credentials));
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
        private synchronized boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
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
            if (Jenkins.getInstance().hasPermission(CredentialsProvider.VIEW)) {
                List<Credentials> list = getDomainCredentialsMap().get(domain);
                if (list == null || list.isEmpty()) {
                    return Collections.emptyList();
                }
                return Collections.unmodifiableList(new ArrayList<Credentials>(list));
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

        private void checkPermission(Permission p) {
            user.checkPermission(p);
        }

        private void save() throws IOException {
            if (user.equals(User.current())) {
                user.save();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public UserProperty reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
            User selUser = req.findAncestorObject(User.class);
            User curUser = User.current();
            // only process changes to this property for the current user
            if (selUser != null && curUser != null && selUser.getId().equals(curUser.getId())) {
                return getDescriptor().newInstance(req, form);
            }
            return this;
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
            public String getDisplayName() {
                return Messages.UserCredentialsProvider_DisplayName();
            }

            /**
             * Whether the credentials should be visible on the user's configure screen.
             *
             * @return true if and only if the current request is the current user's configuration screen.
             */
            @SuppressWarnings("unused") // used by stapler
            public boolean isVisible() {
                if (!isEnabled()) {
                    // no point bothering the user if there are no credentials aware plugins installed.
                    return false;
                }
                StaplerRequest req = Stapler.getCurrentRequest();
                if (req == null) {
                    // does not make sense to pretend to be enabled outside of a stapler request
                    return false;
                }
                User selUser = req.findAncestorObject(User.class);
                User curUser = User.current();
                // only enable this property for the current user
                return selUser != null && curUser != null && selUser.equals(curUser);
            }

            /**
             * Gets all the credentials descriptors.
             *
             * @return all the credentials descriptors.
             * @since 1.5
             */
            @SuppressWarnings("unused") // used by stapler
            public DescriptorExtensionList<Credentials, Descriptor<Credentials>> getCredentialDescriptors() {
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
                return Jenkins.getInstance().getDescriptorList(DomainSpecification.class);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isEnabled() {
                return !all().isEmpty();
            }
        }
    }

    @Extension
    public static class TransientUserActionFactoryImpl extends TransientUserActionFactory {
        @Override
        public Collection<? extends Action> createFor(User target) {
            return Collections.singletonList(new UserFacingAction(target));
        }
    }

    @ExportedBean
    public static class UserFacingAction extends CredentialsStoreAction {

        private final User user;

        public UserFacingAction(User user) {
            this.user = user;
        }

        public Api getApi() {
            return new Api(this);
        }

        @Exported
        public CredentialsStore getStore() {
            return new StoreImpl(user);
        }

    }

    public static class StoreImpl extends CredentialsStore {

        private final User user;

        private StoreImpl(User user) {
            this.user = user;
        }

        private UserCredentialsProperty getInstance() {
            UserCredentialsProperty property = user.getProperty(UserCredentialsProperty.class);
            if (property == null) {
                BulkChange bc = new BulkChange(user);
                try {
                    user.addProperty(property = new UserCredentialsProperty(new DomainCredentials[0]));
                } catch (IOException e) {
                    // ignore as we are not actually saving
                } finally {
                    // we don't want to save the user record unless the credentials are modified.
                    bc.abort();
                }
            }
            return property;
        }

        @Override
        public ModelObject getContext() {
            return user;
        }

        @Override
        public boolean hasPermission(@NonNull Authentication a, @NonNull Permission permission) {
            return user.equals(User.get(a.getName()));
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        @Exported
        public List<Domain> getDomains() {
            return Collections.unmodifiableList(new ArrayList<Domain>(
                    getInstance().getDomainCredentialsMap().keySet()
            ));
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
    }

}
