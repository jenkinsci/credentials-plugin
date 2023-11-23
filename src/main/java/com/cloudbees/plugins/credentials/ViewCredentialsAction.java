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

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.RootAction;
import hudson.model.TopLevelItem;
import hudson.model.TransientUserActionFactory;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.TransientActionFactory;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

/**
 * An {@link Action} that lets you view the available credentials for any {@link ModelObject}.
 */
@ExportedBean
public class ViewCredentialsAction implements Action, IconSpec, AccessControlled, ModelObjectWithContextMenu {

    /**
     * Expose {@link CredentialsProvider#VIEW} for Jelly.
     */
    public static final Permission VIEW = CredentialsProvider.VIEW;

    /**
     * Expose {@link CredentialsProvider#MANAGE_DOMAINS} for Jelly.
     */
    @Restricted(NoExternalUse.class)
    public static final Permission MANAGE_DOMAINS = CredentialsProvider.MANAGE_DOMAINS;

    /**
     * The context in which this {@link ViewCredentialsAction} was created.
     */
    private final ModelObject context;

    /**
     * Constructor.
     *
     * @param context the context.
     */
    public ViewCredentialsAction(ModelObject context) {
        this.context = context;
    }

    /**
     * Gets the context.
     *
     * @return the context.
     */
    public ModelObject getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return isVisible()
                ? "symbol-credentials plugin-credentials"
                : null;
    }

    /**
     * Exposes the {@link CredentialsStore} instances available to the {@link #getContext()}.
     *
     * @return the {@link CredentialsStore} instances available to the {@link #getContext()}.
     */
    @NonNull
    public List<CredentialsStore> getParentStores() {
        return streamStores()
                .filter(s -> context != s.getContext() && s.hasPermission(CredentialsProvider.VIEW))
                .collect(Collectors.toList());
    }

    /**
     * Exposes the {@link CredentialsStore} instances available to the {@link #getContext()}.
     *
     * @return the {@link CredentialsStore} instances available to the {@link #getContext()}.
     */
    @NonNull
    public List<CredentialsStore> getLocalStores() {
        return streamStores()
                .filter(s -> context == s.getContext() && s.hasPermission(CredentialsProvider.VIEW))
                .collect(Collectors.toList());
    }

    /**
     * Exposes the {@link #getLocalStores()} {@link CredentialsStore#getStoreAction()}.
     *
     * @return the {@link #getLocalStores()} {@link CredentialsStore#getStoreAction()}.
     */
    @NonNull
    @SuppressWarnings("unused") // Jelly EL
    public List<CredentialsStoreAction> getStoreActions() {
        return streamStores()
                .filter(s -> context == s.getContext() && s.hasPermission(CredentialsProvider.VIEW))
                .map(CredentialsStore::getStoreAction)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Stream<CredentialsStore> streamStores() {
        return StreamSupport.stream(CredentialsProvider.lookupStores(getContext()).spliterator(), false);
    }

    /**
     * Exposes the {@link #getLocalStores()} for the XML API.
     *
     * @return the {@link #getLocalStores()} for the XML API.
     * @since 2.1.0
     */
    @NonNull
    @SuppressWarnings("unused") // Stapler XML/JSON API
    @Exported(name = "stores")
    public Map<String,CredentialsStoreAction> getStoreActionsMap() {
        Map<String,CredentialsStoreAction> result = new TreeMap<>();
        for (CredentialsStoreAction a: getStoreActions()) {
            result.put(a.getUrlName(), a);
        }
        return result;
    }

    /**
     * Exposes the {@link #getStoreActions()} by {@link CredentialsStoreAction#getUrlName()} for Stapler.
     *
     * @param name the {@link CredentialsStoreAction#getUrlName()} to match.
     * @return the {@link CredentialsStoreAction} or {@code null}
     */
    @CheckForNull
    @SuppressWarnings("unused") // Stapler binding
    public CredentialsStoreAction getStore(String name) {
        return streamStores()
                .filter(s -> context == s.getContext() && s.getStoreAction() != null &&
                        name.equals(s.getStoreAction().getUrlName()))
                .findFirst()
                .filter(s -> s.hasPermission(CredentialsProvider.VIEW))
                .map(CredentialsStore::getStoreAction)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.CredentialsStoreAction_DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrlName() {
        return "credentials";
    }

    public String getStoreBaseUrl(String itUrl) {
        return itUrl.isEmpty() || itUrl.endsWith("/")
                ? itUrl + getUrlName() + "/store/"
                : itUrl + "/" + getUrlName() + "/store/";
    }

    /**
     * Tests if the {@link ViewCredentialsAction} should be visible.
     *
     * @return {@code true} if the action should be visible.
     */
    public boolean isVisible() {
        if (context instanceof AccessControlled) {
            AccessControlled accessControlled = (AccessControlled) this.context;
            if (isVisibleForAdministrator(accessControlled) || !accessControlled.hasPermission(CredentialsProvider.VIEW)) {
                // must have permission
                return false;
            }
        }

        for (CredentialsProvider p : CredentialsProvider.enabled(context)) {
            if (p.hasCredentialsDescriptors()) {
                // at least one provider must have the potential for at least one type
                return true;
            }
        }
        return false;
    }

    /**
     * Administrator's view credentials from 'Manage Jenkins'.
     * @param accessControlled an access controlled object.
     * @return whether the action should be visible or not if the user is an administrator.
     */
    private boolean isVisibleForAdministrator(AccessControlled accessControlled) {
        return accessControlled instanceof Jenkins && accessControlled.hasPermission(Jenkins.ADMINISTER);
    }

    /**
     * Expose a Jenkins {@link Api}.
     *
     * @return the {@link Api}.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the credential entries.
     *
     * @return the credential entries.
     */
    public List<TableEntry> getTableEntries() {
        List<TableEntry> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (CredentialsStore p : CredentialsProvider.lookupStores(context)) {
            if (p.hasPermission(CredentialsProvider.VIEW)) {
                for (Domain domain : p.getDomains()) {
                    for (Credentials c : p.getCredentials(domain)) {
                        CredentialsScope scope = c.getScope();
                        if (scope != null && !scope.isVisible(context)) {
                            continue;
                        }
                        boolean masked;
                        if (c instanceof IdCredentials) {
                            String id = ((IdCredentials) c).getId();
                            masked = ids.contains(id);
                            ids.add(id);
                        } else {
                            masked = false;
                        }
                        result.add(new TableEntry(p.getProvider(), p, domain, c, masked));
                    }
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        return isVisible()
                ? "symbol-credentials plugin-credentials"
                : null;
    }

    /**
     * Returns the full name of this action.
     *
     * @return the full name of this action.
     */
    public final String getFullName() {
        String n = getContextFullName();
        if (n.length() == 0) {
            return getUrlName();
        } else {
            return n + '/' + getUrlName();
        }
    }

    /**
     * Returns the full name of the {@link #getContext()}.
     *
     * @return the full name of the {@link #getContext()}.
     */
    public String getContextFullName() {
        String n;
        if (context instanceof Item) {
            n = ((Item) context).getFullName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullName();
        } else if (context instanceof User) {
            n = "user/" + ((User) context).getId();
        } else {
            n = "";
        }
        return n;
    }

    /**
     * Returns the full display name of this action.
     *
     * @return the full display name of this action.
     */
    public final String getFullDisplayName() {
        String n = getContextFullDisplayName();
        if (n.length() == 0) {
            return getDisplayName();
        } else {
            return n + " \u00BB " + getDisplayName();
        }
    }

    /**
     * Returns the full display name of the {@link #getContext()}.
     *
     * @return the full display name of the {@link #getContext()}.
     */
    public String getContextFullDisplayName() {
        String n;
        if (context instanceof Item) {
            n = ((Item) context).getFullDisplayName();
        } else if (context instanceof Jenkins) {
            n = context.getDisplayName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullDisplayName();
        } else if (context instanceof User) {
            n = Messages.CredentialsStoreAction_UserDisplayName(((User) context).getDisplayName());
        } else {
            n = Jenkins.get().getFullDisplayName();
        }
        return n;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ACL getACL() {
        final AccessControlled accessControlled =
                context instanceof AccessControlled ? (AccessControlled) context : Jenkins.get();
        return new ACL() {
            @Override
            public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                if (accessControlled.hasPermission2(a, permission)) {
                    for (CredentialsStore s : getLocalStores()) {
                        if (s.hasPermission2(a, permission)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    // In the general case we would implement ModelObjectWithChildren as the child actions could be viewed as children
    // but in this case we expose them in the sidebar, so they are more correctly part of the context menu.
    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) {
        ContextMenu menu = new ContextMenu();
        for (CredentialsStoreAction action : getStoreActions()) {
            ContextMenuIconUtils.addMenuItem(
                    menu,
                    "store",
                    action,
                    action.getContextMenu(ContextMenuIconUtils.buildUrl("store", action.getUrlName()))
            );
        }
        return menu;
    }

    /**
     * Add the {@link ViewCredentialsAction} to all {@link TopLevelItem} instances.
     */
    @Extension(ordinal = -1000)
    public static class TransientTopLevelItemActionFactoryImpl extends TransientActionFactory<TopLevelItem> {

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<TopLevelItem> type() {
            return TopLevelItem.class;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull TopLevelItem target) {
            return Collections.singleton(new ViewCredentialsAction(target));
        }
    }

    /**
     * Add the {@link ViewCredentialsAction} to all {@link User} instances.
     */
    @Extension(ordinal = -1000)
    public static class TransientUserActionFactoryImpl extends TransientUserActionFactory {
        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends Action> createFor(User target) {
            return Collections.singleton(new ViewCredentialsAction(target));
        }
    }

    /**
     * Add the {@link ViewCredentialsAction} to the {@link Jenkins} root.
     */
    @Extension(ordinal = -1000)
    public static class RootActionImpl extends ViewCredentialsAction implements RootAction {

        /**
         * Our constructor.
         */
        public RootActionImpl() {
            super(Jenkins.get());
        }
    }

    /**
     * Value class to simplify creating the table.
     */
    public static class TableEntry implements IconSpec {
        /**
         * The backing {@link Credentials}.
         */
        private final Credentials credentials;
        /**
         * The backing {@link CredentialsProvider}.
         */
        private final CredentialsProvider provider;
        /**
         * The backing {@link CredentialsStore}.
         */
        private final CredentialsStore store;
        /**
         * The backing {@link Domain}.
         */
        private final Domain domain;
        /**
         * Whether this entry's ID is being masked by another entry.
         */
        private final boolean masked;

        /**
         * Constructor.
         *
         * @param provider    the backing {@link CredentialsProvider}.
         * @param store       the backing {@link CredentialsStore}.
         * @param domain      the backing {@link Domain}.
         * @param credentials the backing {@link Credentials}.
         * @param masked      whether this entry is masked or not.
         */
        public TableEntry(CredentialsProvider provider, CredentialsStore store,
                          Domain domain, Credentials credentials, boolean masked) {
            this.provider = provider;
            this.store = store;
            this.domain = domain;
            this.credentials = credentials;
            this.masked = masked;
        }

        /**
         * Returns the {@link IdCredentials#getId()} of the {@link #credentials}.
         *
         * @return the {@link IdCredentials#getId()} of the {@link #credentials}.
         */
        public String getId() {
            return credentials instanceof IdCredentials ? ((IdCredentials) credentials).getId() : null;
        }

        /**
         * Returns the {@link Credentials#getScope()} of the {@link #credentials}.
         *
         * @return the {@link Credentials#getScope()} of the {@link #credentials}.
         */
        public CredentialsScope getScope() {
            return credentials.getScope();
        }

        /**
         * Returns the {@link CredentialsNameProvider#name(Credentials)} of the {@link #credentials}.
         *
         * @return the {@link CredentialsNameProvider#name(Credentials)} of the {@link #credentials}.
         */
        public String getName() {
            return CredentialsNameProvider.name(credentials);
        }

        /**
         * Returns the {@link StandardCredentials#getDescription()} of the {@link #credentials}.
         *
         * @return the {@link StandardCredentials#getDescription()} of the {@link #credentials}.
         * @throws IOException if there was an issue with formatting this using the markup formatter.
         */
        public String getDescription() throws IOException {
            return credentials instanceof StandardCredentials ? Jenkins.get().getMarkupFormatter()
                    .translate(((StandardCredentials) credentials).getDescription()) : null;
        }

        /**
         * Returns the {@link CredentialsDescriptor#getDisplayName()}.
         *
         * @return the {@link CredentialsDescriptor#getDisplayName()}.
         */
        public String getKind() {
            return credentials.getDescriptor().getDisplayName();
        }

        /**
         * Exposes the {@link CredentialsProvider}.
         *
         * @return the {@link CredentialsProvider}.
         */
        public CredentialsProvider getProvider() {
            return provider;
        }

        /**
         * Exposes the {@link Domain}.
         *
         * @return the {@link Domain}.
         */
        public Domain getDomain() {
            return domain;
        }

        /**
         * Exposes the {@link CredentialsStore}.
         *
         * @return the {@link CredentialsStore}.
         */
        public CredentialsStore getStore() {
            return store;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return credentials.getDescriptor().getIconClassName();
        }

        /**
         * Exposes if this {@link Credentials}'s ID is masked by another credential.
         *
         * @return {@code true} if there is a closer credential with the same ID.
         */
        public boolean isMasked() {
            return masked;
        }
    }

}
