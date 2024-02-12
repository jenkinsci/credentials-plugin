/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.security.AccessDeniedException3;
import hudson.security.Permission;
import hudson.util.CopyOnWriteMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.MockFolder;
import org.springframework.security.core.Authentication;

/**
 * Analogue of <a href="https://github.com/jenkinsci/cloudbees-folder-plugin/blob/cloudbees-folder-4.2.3/src/main/java/com/cloudbees/hudson/plugins/folder/properties/FolderCredentialsProvider.java">{@code FolderCredentialsProvider}</a> for {@link MockFolder}.
 */
@Extension public class MockFolderCredentialsProvider extends CredentialsProvider {

    private static final Map<MockFolder,FolderCredentialsProperty> properties = new java.util.WeakHashMap<>();
    private static synchronized FolderCredentialsProperty getProperty(MockFolder folder) {
        FolderCredentialsProperty property = properties.get(folder);
        if (property == null) {
            property = new FolderCredentialsProperty(folder);
            properties.put(folder, property);
        }
        return property;
    }

    private static final Set<CredentialsScope> SCOPES =
            Collections.singleton(CredentialsScope.GLOBAL);

    @Override
    public Set<CredentialsScope> getScopes(ModelObject object) {
        if (object instanceof MockFolder) {
            return SCOPES;
        }
        return super.getScopes(object);
    }

    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type, @Nullable ItemGroup itemGroup,
                                                                     @Nullable Authentication authentication,
                                                                     @NonNull List<DomainRequirement> domainRequirements) {
        if (authentication == null) {
            authentication = ACL.SYSTEM2;
        }
        List<C> result = new ArrayList<>();
        if (ACL.SYSTEM2.equals(authentication)) {
            while (itemGroup != null) {
                if (itemGroup instanceof MockFolder) {
                    final MockFolder folder = (MockFolder) itemGroup;
                    FolderCredentialsProperty property = getProperty(folder);
                    result.addAll(DomainCredentials.getCredentials(
                            property.getDomainCredentialsMap(),
                            type,
                            domainRequirements,
                            CredentialsMatchers.always()));
                }
                if (itemGroup instanceof Item) {
                    itemGroup = ((Item) itemGroup).getParent();
                } else {
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public CredentialsStore getStore(@CheckForNull ModelObject object) {
        if (object instanceof MockFolder) {
            final MockFolder folder = (MockFolder) object;
            return getProperty(folder).getStore();
        }
        return null;
    }

    private static class FolderCredentialsProperty {

        private final MockFolder owner;

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
         * @since 3.10
         */
        private Map<Domain, List<Credentials>> domainCredentialsMap =
                new CopyOnWriteMap.Hash<>();

        /**
         * Our store.
         */
        private transient StoreImpl store = new StoreImpl();

        FolderCredentialsProperty(MockFolder owner) {
            this.owner = owner;
        }

        public <C extends Credentials> List<C> getCredentials(Class<C> type) {
            List<C> result = new ArrayList<>();
            for (Credentials credential : getCredentials()) {
                if (type.isInstance(credential)) {
                    result.add(type.cast(credential));
                }
            }
            return result;
        }

        /**
         * Gets all the folder's credentials.
         *
         * @return all the folder's credentials.
         */
        @SuppressWarnings("unused") // used by stapler
        public List<Credentials> getCredentials() {
            return getDomainCredentialsMap().get(Domain.global());
        }

        /**
         * The Map of domain credentials.
         *
         * @since 3.10
         */
        @NonNull
        public synchronized Map<Domain, List<Credentials>> getDomainCredentialsMap() {
            return domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
        }

        /**
         * Sets the map of domain credentials.
         *
         * @param domainCredentialsMap the map of domain credentials.
         * @since 3.10
         */
        public synchronized void setDomainCredentialsMap(Map<Domain, List<Credentials>> domainCredentialsMap) {
            this.domainCredentialsMap = DomainCredentials.toCopyOnWriteMap(domainCredentialsMap);
        }

        public synchronized CredentialsStore getStore() {
            if (store == null) {
                store = new StoreImpl();
            }
            return store;
        }

        /**
         * Short-cut method for checking {@link CredentialsStore#hasPermission(hudson.security.Permission)}
         *
         * @param p the permission to check.
         */
        private void checkPermission(Permission p) {
            if (!store.hasPermission(p)) {
                throw new AccessDeniedException3(Jenkins.getAuthentication2(), p);
            }
        }

        /**
         * Short-cut method that redundantly checks the specified permission (to catch any typos) and then escalates
         * authentication in order to save the {@link CredentialsStore}.
         *
         * @param p the permissions of the operation being performed.
         * @throws IOException if something goes wrong.
         */
        private void checkedSave(Permission p) throws IOException {
            checkPermission(p);
            try (var ignored = ACL.as2(ACL.SYSTEM2)) {
                owner.save();
            }
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
                    checkedSave(CredentialsProvider.MANAGE_DOMAINS);
                }
                return modified;
            } else {
                domainCredentialsMap.put(domain, new ArrayList<>(credentials));
                checkedSave(CredentialsProvider.MANAGE_DOMAINS);
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
                checkedSave(CredentialsProvider.MANAGE_DOMAINS);
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
                checkedSave(CredentialsProvider.MANAGE_DOMAINS);
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
                checkedSave(CredentialsProvider.CREATE);
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        @NonNull
        private synchronized List<Credentials> getCredentials(@NonNull Domain domain) {
            if (store.hasPermission(CredentialsProvider.VIEW)) {
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
                checkedSave(CredentialsProvider.DELETE);
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
                checkedSave(CredentialsProvider.UPDATE);
                return true;
            }
            return false;
        }

        private class StoreImpl extends CredentialsStore {

            @NonNull
            @Override
            public ModelObject getContext() {
                return owner;
            }

            @Override
            public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                return owner.getACL().hasPermission2(a, permission);
            }

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public List<Domain> getDomains() {
                return Collections.unmodifiableList(new ArrayList<>(
                        getDomainCredentialsMap().keySet()
                ));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException {
                return FolderCredentialsProperty.this.addDomain(domain, credentials);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeDomain(@NonNull Domain domain) throws IOException {
                return FolderCredentialsProperty.this.removeDomain(domain);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
                return FolderCredentialsProperty.this.updateDomain(current, replacement);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException {
                return FolderCredentialsProperty.this.addCredentials(domain, credentials);
            }

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public List<Credentials> getCredentials(@NonNull Domain domain) {
                return FolderCredentialsProperty.this.getCredentials(domain);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials)
                    throws IOException {
                return FolderCredentialsProperty.this.removeCredentials(domain, credentials);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                             @NonNull Credentials replacement) throws IOException {
                return FolderCredentialsProperty.this.updateCredentials(domain, current, replacement);
            }
        }

    }
}
