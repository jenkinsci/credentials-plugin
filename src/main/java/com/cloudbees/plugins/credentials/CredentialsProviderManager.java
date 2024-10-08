/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc., Stephen Connolly.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Saveable;
import hudson.security.Permission;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Manages the various {@link CredentialsProvider} implementations in a {@link Jenkins}
 *
 * @since 2.0
 */
@Extension
public class CredentialsProviderManager extends DescriptorVisibilityFilter implements Serializable, Saveable {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CredentialsProviderManager.class.getName());
    /**
     * Ensure standardized serialization.
     */
    private static final long serialVersionUID = 1L;
    /**
     * Our {@link CredentialsProvider} filter.
     */
    private CredentialsProviderFilter providerFilter;
    /**
     * Our {@link CredentialsDescriptor} filter.
     */
    private CredentialsTypeFilter typeFilter;
    /**
     * Any additional restrictions to apply.
     */
    private List<CredentialsProviderTypeRestriction> restrictions;
    /**
     * A cache of {@link #restrictions} grouped by {@link CredentialsProviderTypeRestriction#getDescriptor()}.
     */
    private transient Map<CredentialsProviderTypeRestrictionDescriptor, List<CredentialsProviderTypeRestriction>>
            restrictionGroups;

    /**
     * Our constructor.
     */
    public CredentialsProviderManager() {
        try {
            XmlFile xml = getConfigFile();
            if (xml.exists()) {
                xml.unmarshal(this);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read the existing credentials", e);
        }
    }

    /**
     * Returns {@code true} if and only if the specified {@link CredentialsProvider} is enabled.
     *
     * @param provider the specified {@link CredentialsProvider} to check.
     * @return {@code true} if and only if the specified {@link CredentialsProvider} is enabled.
     */
    public static boolean isEnabled(CredentialsProvider provider) {
        CredentialsProviderManager manager = getInstance();
        return manager == null || manager.providerFilter == null || manager.providerFilter.filter(provider);
    }

    /**
     * Returns our {@link CredentialsProviderManager} singleton.
     *
     * @return {@link CredentialsProviderManager} singleton or {@code null}
     */
    @Nullable // should never be null under normal code paths
    public static CredentialsProviderManager getInstance() {
        return ExtensionList.lookup(DescriptorVisibilityFilter.class).get(CredentialsProviderManager.class);
    }

    /**
     * Returns our {@link CredentialsProviderManager} singleton.
     *
     * @return {@link CredentialsProviderManager} singleton
     */
    @NonNull
    public static CredentialsProviderManager getInstanceOrDie() {
        CredentialsProviderManager instance = getInstance();
        if (instance == null) {
            throw new IllegalStateException("CredentialsProviderManager is not registered with Jenkins");
        }
        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean filter(Object context, @NonNull Descriptor descriptor) {
        Map<CredentialsProviderTypeRestrictionDescriptor, List<CredentialsProviderTypeRestriction>> restrictions =
                restrictions();
        if (restrictions != null && descriptor instanceof CredentialsDescriptor) {
            CredentialsProvider provider = null;
            if (context instanceof CredentialsProvider) {
                provider = (CredentialsProvider) context;
            } else if (context instanceof CredentialsStore) {
                provider = ((CredentialsStore) context).getProvider();
            } else if (context instanceof CredentialsStoreAction.DomainWrapper) {
                provider = ((CredentialsStoreAction.DomainWrapper) context).getStore().getProvider();
            } else if (context instanceof CredentialsStoreAction.CredentialsWrapper) {
                provider = ((CredentialsStoreAction.CredentialsWrapper) context).getStore().getProvider();
            }

            if (provider != null) {
                CredentialsDescriptor type = (CredentialsDescriptor) descriptor;
                for (Map.Entry<CredentialsProviderTypeRestrictionDescriptor, List<CredentialsProviderTypeRestriction>>
                        group : restrictions.entrySet()) {
                    if (!group.getKey().filter(group.getValue(), provider, type)) {
                        return false;
                    }
                }
            }
        }
        if (descriptor instanceof CredentialsDescriptor) {
            return typeFilter == null || typeFilter.filter((CredentialsDescriptor) descriptor);
        }
        if (descriptor instanceof CredentialsProvider && context instanceof Jenkins) {
            return providerFilter == null || providerFilter.filter((CredentialsProvider) descriptor);
        }
        return true;
    }

    /**
     * Gets the configuration file that {@link CredentialsProviderManager} uses to store its credentials.
     *
     * @return the configuration file that {@link CredentialsProviderManager} uses to store its credentials.
     */
    public static XmlFile getConfigFile() {
        return new XmlFile(Jenkins.XSTREAM2, new File(Jenkins.get().getRootDir(), "credentials-configuration.xml"));
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
     * {@inheritDoc}
     */
    @Override
    public void save() throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        getConfigFile().write(this);
    }

    /**
     * Gets the current {@link CredentialsProviderFilter}.
     *
     * @return the current {@link CredentialsProviderFilter}.
     */
    @NonNull
    public CredentialsProviderFilter getProviderFilter() {
        return providerFilter == null ? new CredentialsProviderFilter.None() : providerFilter;
    }

    /**
     * Sets the {@link CredentialsProviderFilter}.
     *
     * @param providerFilter the new {@link CredentialsProviderFilter}.
     */
    public void setProviderFilter(@CheckForNull CredentialsProviderFilter providerFilter) {
        if (providerFilter == null) {
            providerFilter = new CredentialsProviderFilter.None();
        }
        if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            if (!providerFilter.equals(this.providerFilter)) {
                this.providerFilter = providerFilter;
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Gets the current {@link CredentialsTypeFilter}.
     *
     * @return the current {@link CredentialsTypeFilter}.
     */
    @NonNull
    public CredentialsTypeFilter getTypeFilter() {
        return typeFilter == null ? new CredentialsTypeFilter.None() : typeFilter;
    }

    /**
     * Sets the {@link CredentialsTypeFilter}.
     *
     * @param typeFilter the new {@link CredentialsTypeFilter}.
     */
    public void setTypeFilter(@CheckForNull CredentialsTypeFilter typeFilter) {
        if (typeFilter == null) {
            typeFilter = new CredentialsTypeFilter.None();
        }
        if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            if (!typeFilter.equals(this.typeFilter)) {
                this.typeFilter = typeFilter;
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Gets the current list of {@link CredentialsProviderTypeRestriction} instances.
     *
     * @return the current list of {@link CredentialsProviderTypeRestriction} instances.
     */
    @NonNull
    public List<CredentialsProviderTypeRestriction> getRestrictions() {
        return restrictions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(restrictions);
    }

    /**
     * Sets the list of {@link CredentialsProviderTypeRestriction} instances.
     *
     * @param restrictions the new list of {@link CredentialsProviderTypeRestriction} instances.
     */
    public void setRestrictions(List<CredentialsProviderTypeRestriction> restrictions) {
        if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            if (restrictions != null) {
                // ensure they are sorted grouped so that it is easy to infer
                restrictions.sort(new Comparator<CredentialsProviderTypeRestriction>() {
                    final ExtensionList<CredentialsProviderTypeRestrictionDescriptor> list =
                            ExtensionList.lookup(CredentialsProviderTypeRestrictionDescriptor.class);

                    /** {@inheritDoc} */
                    @Override
                    public int compare(CredentialsProviderTypeRestriction o1, CredentialsProviderTypeRestriction o2) {
                        int index1 = list.indexOf(o1.getDescriptor());
                        int index2 = list.indexOf(o2.getDescriptor());
                        if (index1 == -1) {
                            return index2 == -1 ? 0 : 1;
                        }
                        if (index2 == -1) {
                            return -1;
                        }
                        return Integer.compare(index1, index2);
                    }
                });
            }
            if (!Objects.equals(restrictions, this.restrictions)) {
                this.restrictions = restrictions;
                this.restrictionGroups = null;
                try {
                    save();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * A repopulation-safe access for the {@link #restrictionGroups}
     *
     * @return the {@link #restrictionGroups} or {@code null} if empty.
     */
    @CheckForNull
    private Map<CredentialsProviderTypeRestrictionDescriptor, List<CredentialsProviderTypeRestriction>> restrictions() {
        if (restrictionGroups == null && restrictions != null) {
            Map<CredentialsProviderTypeRestrictionDescriptor, List<CredentialsProviderTypeRestriction>>
                    restrictionGroups = new HashMap<>();
            for (CredentialsProviderTypeRestriction restriction : restrictions) {
                List<CredentialsProviderTypeRestriction> group = restrictionGroups.computeIfAbsent(restriction.getDescriptor(), k -> new ArrayList<>(1));
                group.add(restriction);
            }
            this.restrictionGroups = restrictionGroups; // idempotent
        }
        return restrictionGroups;
    }

    /**
     * Our global configuration.
     *
     * @since 2.0
     */
    @Extension
    public static class Configuration extends GlobalConfiguration {

        /**
         * A Jelly EL short-cut for {@link CredentialsProviderManager#getProviderFilter()}.
         *
         * @return the {@link CredentialsProviderFilter}.
         */
        @SuppressWarnings("unused") // jelly EL helper
        public CredentialsProviderFilter getProviderFilter() {
            CredentialsProviderManager manager = getInstance();
            return manager == null ? new CredentialsProviderFilter.None() : manager.getProviderFilter();
        }

        /**
         * A Jelly form-binding short-cut for
         * {@link CredentialsProviderManager#setProviderFilter(CredentialsProviderFilter)}.
         *
         * @param providerFilter the {@link CredentialsProviderFilter}.
         */
        @SuppressWarnings("unused") // jelly form binding
        public void setProviderFilter(CredentialsProviderFilter providerFilter) {
            CredentialsProviderManager manager = getInstance();
            if (manager != null) {
                manager.setProviderFilter(providerFilter);
            }
        }

        /**
         * A Jelly EL short-cut for {@link CredentialsProviderManager#getTypeFilter()}.
         *
         * @return the {@link CredentialsTypeFilter}.
         */
        @SuppressWarnings("unused") // jelly EL helper
        public CredentialsTypeFilter getTypeFilter() {
            CredentialsProviderManager manager = getInstance();
            return manager == null ? new CredentialsTypeFilter.None() : manager.getTypeFilter();
        }

        /**
         * A Jelly form-binding short-cut for
         * {@link CredentialsProviderManager#setTypeFilter(CredentialsTypeFilter)}.
         *
         * @param typeFilter the {@link CredentialsTypeFilter}.
         */
        @SuppressWarnings("unused") // jelly form binding
        public void setTypeFilter(CredentialsTypeFilter typeFilter) {
            CredentialsProviderManager manager = getInstance();
            if (manager != null) {
                manager.setTypeFilter(typeFilter);
            }
        }

        /**
         * A Jelly EL short-cut for {@link CredentialsProviderManager#getRestrictions()}.
         *
         * @return the {@link CredentialsProviderTypeRestriction} instances.
         */
        @SuppressWarnings("unused") // jelly EL helper
        public List<CredentialsProviderTypeRestriction> getRestrictions() {
            CredentialsProviderManager manager = getInstance();
            return manager == null
                    ? Collections.emptyList()
                    : manager.getRestrictions();
        }

        /**
         * A Jelly form-binding short-cut for
         * {@link CredentialsProviderManager#setRestrictions(List)}.
         *
         * @param restrictions the {@link CredentialsProviderTypeRestriction} instances.
         */
        @SuppressWarnings("unused") // jelly form binding
        public void setRestrictions(List<CredentialsProviderTypeRestriction> restrictions) {
            CredentialsProviderManager manager = getInstance();
            if (manager != null) {
                manager.setRestrictions(restrictions);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                if (!json.has("restrictions")) {
                    // JENKINS-36090 stapler "helpfully" does not submit the restrictions if there are none
                    // and hence you can never delete the last one
                    json.put("restrictions", new JSONArray());
                }
                req.bindJSON(this, json);
                return super.configure(req, json);
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public GlobalConfigurationCategory getCategory() {
            return GlobalConfigurationCategory.get(GlobalCredentialsConfiguration.Category.class);
        }
    }
}
