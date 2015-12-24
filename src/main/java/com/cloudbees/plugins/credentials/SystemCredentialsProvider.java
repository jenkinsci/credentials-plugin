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
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Api;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ManagementLink;
import hudson.model.ModelObject;
import hudson.model.RootAction;
import hudson.model.Saveable;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
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

import static com.cloudbees.plugins.credentials.CredentialsMatchers.always;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.not;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withScope;
import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static com.cloudbees.plugins.credentials.CredentialsScope.SYSTEM;

/**
 * The root store of credentials.
 */
@Extension
public class SystemCredentialsProvider extends ManagementLink
        implements Describable<SystemCredentialsProvider>, Saveable, StaplerProxy, IconSpec {

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
    private transient List<Credentials> credentials = new CopyOnWriteArrayList<Credentials>();

    /**
     * Our credentials.
     *
     * @since 1.5
     */
    private Map<Domain, List<Credentials>> domainCredentialsMap = new CopyOnWriteMap.Hash<Domain, List<Credentials>>();

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
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return CredentialsProvider.allCredentialsDescriptors().isEmpty()
                ? null
                : "/plugin/credentials/images/48x48/credentials.png";
    }

    /**
     * {@inheritDoc}
     */
    public String getIconClassName() {
        return CredentialsProvider.allCredentialsDescriptors().isEmpty()
                ? null
                : "icon-credentials-credentials";
    }

    /**
     * {@inheritDoc}
     */
    public String getDisplayName() {
        return Messages.SystemCredentialsProvider_DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return Messages.SystemCredentialsProvider_Description();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrlName() {
        return "credentials";
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
    @SuppressWarnings("deprecation")
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
                // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins has not been started, or was already shut down");
        }
        jenkins.checkPermission(p);
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
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            save();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
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
            domainCredentialsMap.put(domain, new ArrayList<Credentials>(credentials));
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
        // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins has not been started, or was already shut down");
        }
        if (jenkins.hasPermission(CredentialsProvider.VIEW)) {
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
     * Gets all the credentials descriptors.
     *
     * @return all the credentials descriptors.
     */
    @SuppressWarnings("unused") // used by stapler
    public DescriptorExtensionList<Credentials, CredentialsDescriptor> getCredentialDescriptors() {
        return CredentialsProvider.allCredentialsDescriptors();
    }

    /**
     * Gets all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
     *
     * @return all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
     * @since 1.5
     */
    @SuppressWarnings("unused") // used by stapler
    public DescriptorExtensionList<DomainSpecification, Descriptor<DomainSpecification>> getSpecificationDescriptors() {
        // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins has not been started, or was already shut down");
        }
        return jenkins.getDescriptorList(DomainSpecification.class);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Descriptor<SystemCredentialsProvider> getDescriptor() {
        // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins has not been started, or was already shut down");
        }
        return jenkins.getDescriptorOrDie(getClass());
    }

    /**
     * Only sysadmin can access this page.
     */
    public Object getTarget() {
        checkPermission(Jenkins.ADMINISTER);
        return this;
    }

    /**
     * Handles form submission.
     *
     * @param req the request.
     * @return the response.
     * @throws ServletException if something goes wrong.
     * @throws IOException      if something goes wrong.
     */
    @SuppressWarnings("unused") // by stapler
    public HttpResponse doConfigSubmit(StaplerRequest req) throws ServletException, IOException {
        checkPermission(Jenkins.ADMINISTER);
        JSONObject data = req.getSubmittedForm();
        setDomainCredentialsMap(DomainCredentials.asMap(
                req.bindJSONToList(DomainCredentials.class, data.get("domainCredentials"))));
        save();
        return HttpResponses.redirectToContextRoot(); // send the user back to the top page
    }

    /**
     * {@inheritDoc}
     */
    public void save() throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        getConfigFile().write(this);
    }

    /**
     * Gets the configuration file that this {@link CredentialsProvider} uses to store its credentials.
     *
     * @return the configuration file that this {@link CredentialsProvider} uses to store its credentials.
     */
    public static XmlFile getConfigFile() {
        // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins has not been started, or was already shut down");
        }
        return new XmlFile(new File(jenkins.getRootDir(), "credentials.xml"));
    }

    /**
     * Gets the singleton instance.
     *
     * @return the singleton instance.
     */
    public static SystemCredentialsProvider getInstance() {
        return all().get(SystemCredentialsProvider.class);
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
        @Override
        public String getDisplayName() {
            return "";
        }

    }

    @Extension
    @SuppressWarnings("unused") // used by Jenkins
    public static class ProviderImpl extends CredentialsProvider {

        /**
         * The scopes that are relevant to the store.
         */
        private static final Set<CredentialsScope> SCOPES =
                Collections.unmodifiableSet(new LinkedHashSet<CredentialsScope>(Arrays.asList(GLOBAL, SYSTEM)));

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
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type,
                                                              @Nullable ItemGroup itemGroup,
                                                              @Nullable Authentication authentication) {
            return getCredentials(type, itemGroup, authentication, Collections.<DomainRequirement>emptyList());
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @NonNull Item item,
                                                              @Nullable Authentication authentication) {
            return getCredentials(type, item, authentication, Collections.<DomainRequirement>emptyList());
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @NonNull Item item,
                                                              @Nullable Authentication authentication,
                                                              @NonNull List<DomainRequirement> domainRequirements) {
            if (ACL.SYSTEM.equals(authentication)) {
                return DomainCredentials.getCredentials(SystemCredentialsProvider.getInstance()
                        .getDomainCredentialsMap(), type, domainRequirements, not(withScope(SYSTEM)));
            }
            return new ArrayList<C>();
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @Nullable ItemGroup itemGroup,
                                                              @Nullable Authentication authentication,
                                                              @NonNull List<DomainRequirement> domainRequirements) {
            if (ACL.SYSTEM.equals(authentication)) {
                CredentialsMatcher matcher = Jenkins.getInstance() == itemGroup ? always() : not(withScope(SYSTEM));
                return DomainCredentials.getCredentials(SystemCredentialsProvider.getInstance()
                        .getDomainCredentialsMap(), type, domainRequirements, matcher);
            }
            return new ArrayList<C>();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public CredentialsStore getStore(@CheckForNull ModelObject object) {
            if (object == Jenkins.getInstance()) {
                return SystemCredentialsProvider.getInstance().getStore();
            }
            return null;
        }
    }

    /**
     * Our {@link CredentialsStore}.
     */
    @ExportedBean
    public static class StoreImpl extends CredentialsStore {

        /**
         * {@inheritDoc}
         */
        @Override
        public ModelObject getContext() {
            // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                throw new IllegalStateException("Jenkins has not been started, or was already shut down");
            }
            return jenkins;
        }

        public ACL getACL() {
            // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                throw new IllegalStateException("Jenkins has not been started, or was already shut down");
            }
            return jenkins.getACL();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasPermission(@NonNull Authentication a, @NonNull Permission permission) {
            // we follow the permissions of Jenkins itself
            return getACL().hasPermission(a, permission);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        @Exported
        public List<Domain> getDomains() {
            return Collections.unmodifiableList(new ArrayList<Domain>(
                    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet()
            ));
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

    }

    @ExportedBean
    @Extension
    public static class UserFacingAction extends CredentialsStoreAction implements RootAction {

        public Api getApi() {
            return new Api(this);
        }

        @Exported
        public CredentialsStore getStore() {
            return SystemCredentialsProvider.getInstance().getStore();
        }
    }
}
