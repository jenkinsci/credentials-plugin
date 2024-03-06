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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.util.CopyOnWriteMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.always;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.not;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withScope;
import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static com.cloudbees.plugins.credentials.CredentialsScope.SYSTEM;

/**
 * The root store of credentials.
 */
@Extension
public class SystemCredentialsProvider extends AbstractDescribableImpl<SystemCredentialsProvider>
        implements Saveable {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SystemCredentialsProvider.class.getName());

    /**
     * Old store of credentials
     *
     * @deprecated migrate to {@link #domainCredentialsMap}.
     */
    @Deprecated
    private transient List<Credentials> credentials = new CopyOnWriteArrayList<>();

    /**
     * Our credentials.
     *
     * @since 1.5
     */
    private Map<Domain, List<Credentials>> domainCredentialsMap = new CopyOnWriteMap.Hash<>();

    /**
     * Our backing store.
     */
    private transient StoreImpl store = new StoreImpl();

    /**
     * Constructor.
     */
    @SuppressWarnings("deprecation")
    public SystemCredentialsProvider() {
        try {
            XmlFile xml = getConfigFile();
            if (xml.exists()) {
                xml.unmarshal(this);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read the existing credentials", e);
        }
        domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
        credentials = null;
    }
    
    /**
     * Ensure the credentials are loaded using SYSTEM during the startup and migration occurs as expected
     */
    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void forceLoadDuringStartup() {
        getInstance();
    }
    
    /**
     * Gets the configuration file that this {@link CredentialsProvider} uses to store its credentials.
     *
     * @return the configuration file that this {@link CredentialsProvider} uses to store its credentials.
     */
    public static XmlFile getConfigFile() {
        return new XmlFile(Jenkins.XSTREAM2, new File(Jenkins.get().getRootDir(), "credentials.xml"));
    }

    /**
     * Gets the singleton instance.
     *
     * @return the singleton instance.
     */
    public static SystemCredentialsProvider getInstance() {
        return ExtensionList.lookup(SystemCredentialsProvider.class).get(SystemCredentialsProvider.class);
    }

    /**
     * Get all the ({@link Domain#global()}) credentials.
     *
     * @return all the ({@link Domain#global()}) credentials.
     */
    @SuppressWarnings("unused") // used by stapler
    public List<Credentials> getCredentials() {
        return domainCredentialsMap.get(Domain.global());
    }

    /**
     * Get all the credentials.
     *
     * @return all the credentials.
     * @since 1.5
     */
    @SuppressWarnings("unused") // used by stapler
    public List<DomainCredentials> getDomainCredentials() {
        return DomainCredentials.asList(getDomainCredentialsMap());
    }

    /**
     * Get all the credentials.
     *
     * @return all the credentials.
     * @since 1.5
     */
    @NonNull
    public synchronized Map<Domain, List<Credentials>> getDomainCredentialsMap() {
        return domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
    }

    /**
     * Set all the credentials.
     *
     * @param domainCredentialsMap all the credentials.
     * @since 1.5
     */
    public synchronized void setDomainCredentialsMap(Map<Domain, List<Credentials>> domainCredentialsMap) {
        this.domainCredentialsMap = DomainCredentials.toCopyOnWriteMap(domainCredentialsMap);
    }

    /**
     * Short-cut method for {@link Jenkins#checkPermission(hudson.security.Permission)}
     *
     * @param p the permission to check.
     */
    private void checkPermission(Permission p) {
        Jenkins.get().checkPermission(p);
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
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            save();
        }
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
    private synchronized boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
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
        if (Jenkins.get().hasPermission(CredentialsProvider.VIEW)) {
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

    /**
     * Implementation for {@link ProviderImpl} to delegate to while keeping the lock synchronization simple.
     */
    private synchronized StoreImpl getStore() {
        if (store == null) {
            store = new StoreImpl();
        }
        return store;
    }

    /**
     * {@inheritDoc}
     */
    public void save() throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        if (BulkChange.contains(this)) {
            return;
        }
        XmlFile configFile = getConfigFile();
        configFile.write(this);
        SaveableListener.fireOnChange(this, configFile);
    }

    /**
     * Our management link descriptor.
     */
    @Extension
    @SuppressWarnings("unused") // used by Jenkins
    public static final class DescriptorImpl extends Descriptor<SystemCredentialsProvider> {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "";
        }

    }

    @Extension
    @SuppressWarnings("unused") // used by Jenkins
    public static class ProviderImpl extends CredentialsProvider {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SystemCredentialsProvider_ProviderImpl_DisplayName();
        }

        /**
         * The scopes that are relevant to the store.
         */
        private static final Set<CredentialsScope> SCOPES =
                Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(GLOBAL, SYSTEM)));

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<CredentialsScope> getScopes(ModelObject object) {
            if (object instanceof Jenkins || object instanceof SystemCredentialsProvider) {
                return SCOPES;
            }
            return super.getScopes(object);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public CredentialsStore getStore(@CheckForNull ModelObject object) {
            if (object == Jenkins.get()) {
                return SystemCredentialsProvider.getInstance().getStore();
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type, @Nullable ItemGroup itemGroup,
                                                                         @Nullable Authentication authentication,
                                                                         @NonNull List<DomainRequirement> domainRequirements) {
            if (ACL.SYSTEM2.equals(authentication)) {
                CredentialsMatcher matcher = Jenkins.get() == itemGroup ? always() : not(withScope(SYSTEM));
                return DomainCredentials.getCredentials(SystemCredentialsProvider.getInstance()
                        .getDomainCredentialsMap(), type, domainRequirements, matcher);
            }
            return new ArrayList<>();
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentialsInItem(@NonNull Class<C> type, @NonNull Item item,
                                                                    @Nullable Authentication authentication,
                                                                    @NonNull List<DomainRequirement> domainRequirements) {
            if (ACL.SYSTEM2.equals(authentication)) {
                return DomainCredentials.getCredentials(SystemCredentialsProvider.getInstance()
                        .getDomainCredentialsMap(), type, domainRequirements, not(withScope(SYSTEM)));
            }
            return new ArrayList<>();
        }

        @Override
        public String getIconClassName() {
            return "symbol-jenkins";
        }
    }

    /**
     * Our {@link CredentialsStore}.
     */
    @ExportedBean
    public static class StoreImpl extends CredentialsStore {

        /**
         * Our store action.
         */
        private final UserFacingAction storeAction = new UserFacingAction();

        /**
         * Default constructor.
         */
        public StoreImpl() {
            super(ProviderImpl.class);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public ModelObject getContext() {
            return Jenkins.get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
            // we follow the permissions of Jenkins itself
            return getACL().hasPermission2(a, permission);
        }

        @NonNull
        public ACL getACL() {
            return Jenkins.get().getACL();
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        @Exported
        public List<Domain> getDomains() {
            return Collections.unmodifiableList(new ArrayList<>(
                    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet()
            ));
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        @Exported
        public List<Credentials> getCredentials(@NonNull Domain domain) {
            return SystemCredentialsProvider.getInstance().getCredentials(domain);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException {
            return SystemCredentialsProvider.getInstance().addDomain(domain, credentials);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean removeDomain(@NonNull Domain domain) throws IOException {
            return SystemCredentialsProvider.getInstance().removeDomain(domain);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
            return SystemCredentialsProvider.getInstance().updateDomain(current, replacement);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException {
            return SystemCredentialsProvider.getInstance().addCredentials(domain, credentials);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException {
            return SystemCredentialsProvider.getInstance().removeCredentials(domain, credentials);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                         @NonNull Credentials replacement) throws IOException {
            return SystemCredentialsProvider.getInstance().updateCredentials(domain, current, replacement);
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
         * {@inheritDoc}
         */
        @Override
        public void save() throws IOException {
            if (BulkChange.contains(this)) {
                return;
            }
            SystemCredentialsProvider.getInstance().save();
        }
    }

    /**
     * Expose the store.
     */
    @ExportedBean
    public static class UserFacingAction extends CredentialsStoreAction {

        /**
         * {@inheritDoc}
         */
        @NonNull
        public CredentialsStore getStore() {
            return SystemCredentialsProvider.getInstance().getStore();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconFileName() {
            return isVisible()
                    ? "/plugin/credentials/images/system-store.svg"
                    : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return isVisible()
                    ? "icon-credentials-system-store icon-sm"
                    : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.SystemCredentialsProvider_UserFacingAction_DisplayName();
        }
    }
}
