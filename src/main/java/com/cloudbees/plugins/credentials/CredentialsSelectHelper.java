/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc., Stephen Connolly.
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
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.util.FormApply;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A descriptor used to assist the c:select tag with allowing in-place addition of credentials.
 *
 * @author Stephen Connolly
 */
@Extension
public class CredentialsSelectHelper extends Descriptor<CredentialsSelectHelper> implements
        Describable<CredentialsSelectHelper> {

    /**
     * Expose the {@link CredentialsProvider#CREATE} permission for Jelly.
     */
    public static final Permission CREATE = CredentialsProvider.CREATE;

    /**
     * {@inheritDoc}
     */
    public CredentialsSelectHelper() {
        super(CredentialsSelectHelper.class);
    }

    /**
     * {@inheritDoc}
     */
    public CredentialsSelectHelper getDescriptor() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.CredentialsSelectHelper_DisplayName();
    }

   /**
     * Returns the {@link StoreItem} instances for the current Stapler request.
     *
     * @param includeUser {@code true} to also include any User scoped stores.
     * @return the {@link StoreItem} instances for the current Stapler request.
     * @since 2.0.5
     */
    @Restricted(NoExternalUse.class)
    public List<StoreItem> getStoreItems(ModelObject context, boolean includeUser) {
        List<StoreItem> result = new ArrayList<StoreItem>();
        if (context == null) {
            StaplerRequest request = Stapler.getCurrentRequest();
            if (request != null) {
                context = request.findAncestorObject(ModelObject.class);
            }
        }
        if (context != null) {
            for (CredentialsStore store : CredentialsProvider.lookupStores(context)) {
                result.add(new StoreItem(store));
            }
        }
        if (includeUser) {
            boolean hasPermission = false;
            ModelObject current = context;
            while (current != null) {
                if (current instanceof AccessControlled) {
                    hasPermission = ((AccessControlled) current).hasPermission(CredentialsProvider.USE_OWN);
                    break;
                } else if (current instanceof ComputerSet) {
                    current = Jenkins.getActiveInstance();
                } else {
                    // fall back to Jenkins as the ultimate parent of everything else
                    current = Jenkins.getActiveInstance();
                }
            }
            if (hasPermission) {
                for (CredentialsStore store : CredentialsProvider.lookupStores(User.current())) {
                    result.add(new StoreItem(store));
                }
            }
        }
        return result;
    }

    /**
     * Stapler binding for the resolver URL segment.
     *
     * @param className the class name of the resolver.
     * @return the wrapped resolver.
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public WrappedContextResolver getResolver(String className) {
        for (ContextResolver r : ExtensionList.lookup(ContextResolver.class)) {
            if (r.getClass().getName().equals(className)) {
                return new WrappedContextResolver(r);
            }
        }
        return null;
    }

    /**
     * Value class to hold the details of a {@link CredentialsStore}.
     *
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public static final class StoreItem implements IconSpec, ModelObject {
        /**
         * The store.
         */
        private final CredentialsStore store;
        /**
         * The URL we will expose the store at.
         */
        private final String url;

        /**
         * Constructor.
         *
         * @param store the store.
         */
        public StoreItem(CredentialsStore store) {
            this.store = store;
            String provider = store.getProvider().getClass().getName();
            String resolver = null;
            String token = null;
            ModelObject storeContext = store.getContext();
            // we only support the cases where the
            for (ContextResolver r : ExtensionList.lookup(ContextResolver.class)) {
                String t = r.getToken(storeContext);
                if (t != null) {
                    resolver = r.getClass().getName();
                    token = t;
                    break;
                }
            }
            this.url = token == null
                    ? null
                    : String.format(
                            "%sdescriptor/%s/resolver/%s/provider/%s/context/%s",
                            Jenkins.getActiveInstance().getRootUrlFromRequest(),
                            CredentialsSelectHelper.class.getName(),
                            Util.rawEncode(resolver),
                            Util.rawEncode(provider),
                            Util.rawEncode(token)
                    );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return store.getProvider().getIconClassName();
        }

        /**
         * Exposes if this store is enabled for the current user.
         *
         * @return {@code true} if the current user can add credentials to this store.
         */
        public boolean isEnabled() {
            return url != null && store.hasPermission(Permission.CREATE) && !store.getCredentialsDescriptors()
                    .isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return store.getContextDisplayName();
        }

        /**
         * Exposes the description of this store (i.e. the {@link CredentialsProvider#getDisplayName()}.
         *
         * @return the description of this store (i.e. the {@link CredentialsProvider#getDisplayName()}.
         */
        public String getDescription() {
            return store.getProvider().getDisplayName();
        }

        /**
         * Exposes the URL of this store's {@link WrappedCredentialsStore}.
         *
         * @return the URL of this store's {@link WrappedCredentialsStore}
         */
        public String getUrl() {
            return url;
        }
    }

    /**
     * Stapler binding for {@link ContextResolver}.
     *
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public static final class WrappedContextResolver {

        /**
         * Our {@link ContextResolver}
         */
        @NonNull
        private final ContextResolver resolver;

        /**
         * Our constructor.
         *
         * @param resolver the {@link ContextResolver}
         */
        public WrappedContextResolver(@NonNull ContextResolver resolver) {
            this.resolver = resolver;
        }

        /**
         * Stapler web binding for the {@link CredentialsProvider}.
         *
         * @param className the class name of the {@link CredentialsProvider}.
         * @return the {@link WrappedContextResolverCredentialsProvider} or {@code null}
         */
        @CheckForNull
        public WrappedContextResolverCredentialsProvider getProvider(String className) {
            for (CredentialsProvider p : CredentialsProvider.enabled()) {
                if (p.getClass().getName().equals(className)) {
                    return new WrappedContextResolverCredentialsProvider(resolver, p);
                }
            }
            return null;
        }
    }

    /**
     * Stapler binding for a {@link ContextResolver} and {@link CredentialsProvider}.
     *
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public static final class WrappedContextResolverCredentialsProvider {

        /**
         * Our {@link ContextResolver}
         */
        @NonNull
        private final ContextResolver resolver;
        /**
         * Our {@link CredentialsProvider}
         */
        @NonNull
        private final CredentialsProvider provider;

        /**
         * Our constructor.
         *
         * @param resolver the {@link ContextResolver}
         * @param provider the {@link CredentialsProvider}
         */
        public WrappedContextResolverCredentialsProvider(@NonNull ContextResolver resolver,
                                                         @NonNull CredentialsProvider provider) {
            this.resolver = resolver;
            this.provider = provider;
        }

        /**
         * Stapler web binding for the {@link ModelObject} representing the context of the store.
         *
         * @param token the {@link ContextResolver#getToken(ModelObject)} of the context of the store.
         * @return the {@link WrappedContextResolverCredentialsProvider} or {@code null}
         */
        public WrappedCredentialsStore getContext(String token) {
            ModelObject context = resolver.getContext(token);
            if (context != null) {
                CredentialsStore store = provider.getStore(context);
                if (store != null) {
                    return new WrappedCredentialsStore(resolver, provider, token, store);
                }
            }
            return null;
        }
    }

    /**
     * Stapler binding for a {@link CredentialsStore}.
     *
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public static final class WrappedCredentialsStore implements IconSpec, ModelObject {
        /**
         * Our {@link ContextResolver}
         */
        @NonNull
        private final ContextResolver resolver;
        /**
         * Our {@link CredentialsProvider}
         */
        @NonNull
        private final CredentialsProvider provider;
        /**
         * Our context's {@link ContextResolver#getToken(ModelObject)}.
         */
        @NonNull
        private final String token;
        /**
         * Our {@link CredentialsStore}.
         */
        private final CredentialsStore store;

        /**
         * Our constructor.
         *
         * @param resolver the {@link ContextResolver}
         * @param provider the {@link CredentialsProvider}
         * @param token    the context's {@link ContextResolver#getToken(ModelObject)}.
         * @param store    the {@link CredentialsStore}
         */
        public WrappedCredentialsStore(ContextResolver resolver, CredentialsProvider provider,
                                       String token, CredentialsStore store) {
            this.store = store;
            this.resolver = resolver;
            this.provider = provider;
            this.token = token;
        }

        /**
         * Stapler web binding for adding credentials to the domain.
         *
         * @param req the request.
         * @param rsp the response.
         * @throws IOException      if something goes wrong.
         * @throws ServletException if something goes wrong.
         */
        @RequirePOST
        public void doAddCredentials(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            if (!store.isDomainsModifiable()) {
                hudson.util.HttpResponses.status(400).generateResponse(req, rsp, null);
                FormApply.applyResponse("window.alert('Domain is read-only')").generateResponse(req, rsp, null);
            }
            store.checkPermission(CredentialsStoreAction.CREATE);
            JSONObject data = req.getSubmittedForm();
            String domainName = data.getString("domain");
            CredentialsStoreAction.DomainWrapper wrapper = getWrappers().get(domainName);
            if (!store.getDomains().contains(wrapper.getDomain())) {
                hudson.util.HttpResponses.status(400).generateResponse(req, rsp, null);
                FormApply.applyResponse("window.alert('Store does not have selected domain')")
                        .generateResponse(req, rsp, null);
            }
            store.checkPermission(CredentialsStoreAction.CREATE);
            Credentials credentials = req.bindJSON(Credentials.class, data.getJSONObject("credentials"));
            store.addCredentials(wrapper.getDomain(), credentials);
            FormApply.applyResponse("window.credentials.refreshAll();").generateResponse(req, rsp, null);
        }

        /**
         * Returns a {@link CredentialsStoreAction.DomainWrapper} to use for contextualizing the credentials form.
         *
         * @return a {@link CredentialsStoreAction.DomainWrapper} to use for contextualizing the credentials form.
         */
        public CredentialsStoreAction.DomainWrapper getWrapper() {
            Collection<CredentialsStoreAction.DomainWrapper> values = getWrappers().values();
            return values.isEmpty() ? null : values.iterator().next();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return store.getProvider().getIconClassName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return store.getContextDisplayName();
        }

        /**
         * Exposes the description of this store (i.e. the {@link CredentialsProvider#getDisplayName()}.
         *
         * @return the description of this store (i.e. the {@link CredentialsProvider#getDisplayName()}.
         */
        public String getDescription() {
            return store.getProvider().getDisplayName();
        }

        /**
         * Exposes our URL (as we will be invoked from an unknown page so we need an absolute URL).
         *
         * @return our URL.
         */
        public String getUrl() {
            return String.format(
                    "%sdescriptor/%s/resolver/%s/provider/%s/context/%s",
                    Jenkins.getActiveInstance().getRootUrlFromRequest(),
                    CredentialsSelectHelper.class.getName(),
                    Util.rawEncode(resolver.getClass().getName()),
                    Util.rawEncode(provider.getClass().getName()),
                    Util.rawEncode(token)
            );
        }

        /**
         * Exposes the {@link CredentialsDescriptor} instances appropriate for this {@link CredentialsStore}.
         *
         * @return the {@link CredentialsDescriptor} instances appropriate for this {@link CredentialsStore}.
         */
        public List<CredentialsDescriptor> getCredentialsDescriptors() {
            return store.getCredentialsDescriptors();
        }

        /**
         * The {@link CredentialsStoreAction.DomainWrapper} instances.
         *
         * @return the {@link CredentialsStoreAction.DomainWrapper} instances.
         */
        public Map<String, CredentialsStoreAction.DomainWrapper> getWrappers() {
            CredentialsStoreAction action = store.getStoreAction();
            return action != null ? action.getDomains() : new CredentialsStoreAction() {
                /**
                 * {@inheritDoc}
                 */
                @NonNull
                @Override
                public CredentialsStore getStore() {
                    return store;
                }
            }.getDomains();
        }

    }

    /**
     * An extension point to allow plugging in additional resolution of {@link ModelObject} instances.
     *
     * @since 2.0
     */
    public static abstract class ContextResolver implements ExtensionPoint {

        /**
         * Maps a context object (a {@link ModelObject}) into a token that can be used to recover the context.
         *
         * @param context the {@link ModelObject}.
         * @return a token if this {@link ContextResolver} can recover the object or {@code null} if the
         * {@link ModelObject} type is not supported by this {@link ContextResolver}.
         */
        @CheckForNull
        public abstract String getToken(ModelObject context);

        /**
         * Maps a token from {@link #getToken(ModelObject)} back to its original {@link ModelObject}.
         *
         * @param token the token.
         * @return the corresponding {@link ModelObject} or {@code null} if the object no longer exists or if the
         * user does not have permission to access that object.
         */
        @CheckForNull
        public abstract ModelObject getContext(String token);
    }

    /**
     * A {@link ContextResolver} for {@link Jenkins}.
     *
     * @since 2.0
     */
    @Extension(ordinal = 1000)
    public static class SystemContextResolver extends ContextResolver {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getToken(ModelObject context) {
            return context instanceof Jenkins ? "jenkins" : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ModelObject getContext(String token) {
            return "jenkins".equals(token) ? Jenkins.getActiveInstance() : null;
        }
    }

    /**
     * A {@link ContextResolver} for {@link Item} instances resolvable by {@link Jenkins#getItemByFullName(String)}.
     *
     * @since 2.0
     */
    @Extension
    public static class ItemContextResolver extends ContextResolver {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getToken(ModelObject context) {
            return context instanceof Item ? ((Item) context).getFullName() : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ModelObject getContext(String token) {
            return Jenkins.getActiveInstance().getItemByFullName(token);
        }
    }

    /**
     * A {@link ContextResolver} for {@link User} instances.
     *
     * @since 2.0
     */
    @Extension
    public static class UserContextResolver extends ContextResolver {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getToken(ModelObject context) {
            return context instanceof User ? ((User) context).getId() : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ModelObject getContext(String token) {
            // TODO invoke User.getById directly once Jenkins 2.3+
            try {
                Method getById = User.class.getMethod("getById", String.class, boolean.class);
                return (ModelObject) getById.invoke(null, token, false);
            } catch (NoSuchMethodException e) {
                // old Jenkins pre SECURITY-243
                return User.get(token, false, Collections.emptyMap());
            } catch (InvocationTargetException e) {
                return null;
            } catch (IllegalAccessException e) {
                return null;
            }
        }
    }
}
