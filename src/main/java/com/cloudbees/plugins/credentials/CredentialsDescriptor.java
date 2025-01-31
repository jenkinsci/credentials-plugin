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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.RestrictedSince;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Descriptor for credentials.
 */
public abstract class CredentialsDescriptor extends Descriptor<Credentials> implements IconSpec {

    private transient final Map<String, FormValidation.CheckMethod>
            enhancedCheckMethods = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param clazz The concrete credentials class.
     * @since 1.2
     */
    protected CredentialsDescriptor(Class<? extends Credentials> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link Credentials} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.3
     */
    protected CredentialsDescriptor() {
    }

    /**
     * Fills in the scopes for a scope list-box.
     *
     * @param context list-box context
     * @return the scopes for the nearest request object that acts as a container for credentials.
     */
    @SuppressWarnings("unused") // used by stapler
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.1.5")
    public ListBoxModel doFillScopeItems(@ContextInPath ModelObject context) {
        Set<CredentialsScope> scopes = CredentialsProvider.lookupScopes(context);
        if (scopes != null) {
            return scopes.stream()
                    .map(scope -> new ListBoxModel.Option(scope.getDisplayName(), scope.toString()))
                    .collect(Collectors.toCollection(ListBoxModel::new));
        }
        return new ListBoxModel();
    }

    /**
     * Checks if asking for a credentials scope is relevant. For example, when a scope will be stored in
     * {@link UserCredentialsProvider}, there is no need to specify the scope,
     * as it can only be {@link CredentialsScope#USER}, but where the credential will be stored in
     * {@link SystemCredentialsProvider}, there are multiple scopes relevant for that container, so the scope
     * field is relevant.
     *
     * @return {@code true} if the nearest request object that acts as a container for credentials needs a scope
     * to be specified.
     */
    @SuppressWarnings("unused") // used by stapler
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.1.5")
    public boolean isScopeRelevant() {
        Ancestor ancestor = Stapler.getCurrentRequest2().findAncestor(Object.class);
        while (ancestor != null) {
            if (ancestor.getObject() instanceof ModelObject) {
                ModelObject context = unwrapContext((ModelObject) ancestor.getObject());
                Set<CredentialsScope> scopes = CredentialsProvider.lookupScopes(context);
                if (scopes != null) {
                    return scopes.size() > 1;
                }
            }
            ancestor = ancestor.getPrev();
        }
        return false;
    }

    /**
     * Similar to {@link #isScopeRelevant()} but operating on a specific {@link ModelObject} rather than trying to
     * infer from the stapler request.
     *
     * @param object the object that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     * object.
     */
    @SuppressWarnings("unused") // used by stapler
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.1.5")
    public boolean isScopeRelevant(ModelObject object) {
        Set<CredentialsScope> scopes = CredentialsProvider.lookupScopes(object);
        return scopes != null && scopes.size() > 1;
    }

    /**
     * Similar to {@link #isScopeRelevant()} but operating on a specific {@link CredentialsStore} rather than trying to
     * infer from the stapler request.
     *
     * @param store the object that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     * object.
     * @since 2.1.5
     */
    @SuppressWarnings("unused") // used by stapler
    @Restricted(NoExternalUse.class)
    public boolean isScopeRelevant(@CheckForNull CredentialsStore store) {
        Set<CredentialsScope> scopes = store == null ? null : store.getScopes();
        return scopes != null && scopes.size() > 1;
    }

    /**
     * Similar to {@link #isScopeRelevant()} but used by {@link CredentialsStoreAction}.
     *
     * @param wrapper the wrapper for the domain that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     * object.
     */
    @SuppressWarnings("unused") // used by stapler
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.1.5")
    public boolean isScopeRelevant(CredentialsStoreAction.DomainWrapper wrapper) {
        if (wrapper != null) {
            return isScopeRelevant(wrapper.getStore().getContext());
        }
        CredentialsStoreAction action =
                Stapler.getCurrentRequest2().findAncestorObject(CredentialsStoreAction.class);
        if (action != null) {
            return isScopeRelevant(action.getStore().getContext());
        }
        return isScopeRelevant();
    }

    /**
     * Similar to {@link #isScopeRelevant()} but used by {@link CredentialsStoreAction}.
     *
     * @param wrapper the wrapper for the domain that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     * object.
     */
    @SuppressWarnings("unused") // used by stapler
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.1.5")
    public boolean isScopeRelevant(CredentialsStoreAction.CredentialsWrapper wrapper) {
        if (wrapper != null) {
            return isScopeRelevant(wrapper.getStore().getContext());
        }
        CredentialsStoreAction action =
                Stapler.getCurrentRequest2().findAncestorObject(CredentialsStoreAction.class);
        if (action != null) {
            return isScopeRelevant(action.getStore().getContext());
        }
        return isScopeRelevant();
    }

    /**
     * Similar to {@link #isScopeRelevant()} but used by {@link CredentialsSelectHelper}.
     *
     * @param wrapper the wrapper for the domain that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     * object.
     * @since 2.1.5
     */
    @SuppressWarnings("unused") // used by stapler
    @Restricted(NoExternalUse.class)
    public boolean isScopeRelevant(CredentialsSelectHelper.WrappedCredentialsStore wrapper) {
        if (wrapper != null) {
            return isScopeRelevant(wrapper.getStore().getContext());
        }
        CredentialsStoreAction action =
                Stapler.getCurrentRequest2().findAncestorObject(CredentialsStoreAction.class);
        if (action != null) {
            return isScopeRelevant(action.getStore().getContext());
        }
        return isScopeRelevant();
    }

    /**
     * Returns the config page for the credentials.
     *
     * @return the config page for the credentials.
     */
    @SuppressWarnings("unused") // used by stapler
    public String getCredentialsPage() {
        return getViewPage(clazz, "credentials.jelly");
    }

    /**
     * {@inheritDoc}
     * @since 1.25
     */
    public String getIconClassName() {
        return "symbol-credentials plugin-credentials";
    }

    /**
     * Determines if this {@link CredentialsDescriptor} is applicable to the specified {@link CredentialsProvider}.
     * <p>
     * This method will be called by {@link CredentialsProvider#isApplicable(Descriptor)}
     *
     * @param provider the {@link CredentialsProvider} to check.
     * @return {@code true} if this {@link CredentialsDescriptor} is applicable in the specified {@link CredentialsProvider}
     * @since 2.0
     */
    public boolean isApplicable(CredentialsProvider provider) {
        return true;
    }

    /**
     * In some cases the nearest {@link AncestorInPath} {@link ModelObject} is one of the Credentials plugins wrapper
     * classes.
     * This helper method unwraps those to return the correct context.
     *
     * @param context the context (wrapped or unwrapped).
     * @return the unwrapped context.
     * @since 2.1.5
     */
    @NonNull
    public static ModelObject unwrapContext(@NonNull ModelObject context) {
        if (context instanceof CredentialsSelectHelper.WrappedCredentialsStore) {
            return ((CredentialsSelectHelper.WrappedCredentialsStore) context).getStore().getContext();
        }
        if (context instanceof CredentialsStoreAction.CredentialsWrapper) {
            return ((CredentialsStoreAction.CredentialsWrapper) context).getStore().getContext();
        }
        if (context instanceof CredentialsStoreAction.DomainWrapper) {
            return ((CredentialsStoreAction.DomainWrapper) context).getStore().getContext();
        }
        return context;
    }

    /**
     * Looks up the context given the provider and token.
     * @param provider the provider.
     * @param token the token.
     * @return the context.
     *
     * @since 2.1.5
     */
    @CheckForNull
    public static ModelObject lookupContext(String provider, String token) {
        return ExtensionList.lookup(CredentialsSelectHelper.ContextResolver.class)
                .stream()
                .filter(r -> r.getClass().getName().equals(provider))
                .findFirst()
                .map(r -> r.getContext(token))
                .orElse(null);
    }

    /**
     * Attempts to resolve the credentials context from the {@link Stapler#getCurrentRequest2()} (includes special
     * handling of the HTTP Referer to enable resolution from AJAX requests).
     *
     * @param type the type of context.
     * @param <T> the type of context.
     * @return the context from the request
     * @since 2.1.5
     */
    @CheckForNull
    public static <T extends ModelObject> T findContextInPath(@NonNull Class<T> type) {
        return findContextInPath(Stapler.getCurrentRequest2(), type);
    }

    /**
     * Attempts to resolve the credentials context from the {@link StaplerRequest2} (includes special
     * handling of the HTTP Referer to enable resolution from AJAX requests).
     *
     * @param request the {@link StaplerRequest2}.
     * @param type the type of context.
     * @param <T>  the type of context.
     * @return the context from the request
     * @since 2.1.5
     */
    @CheckForNull
    public static <T extends ModelObject> T findContextInPath(@NonNull StaplerRequest2 request, @NonNull Class<T> type) {
        List<Ancestor> ancestors = request.getAncestors();
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            Ancestor a = ancestors.get(i);
            Object o = a.getObject();
            // special case of unwrapping our internal wrapper classes.
            if (o instanceof CredentialsSelectHelper.WrappedCredentialsStore) {
                o = ((CredentialsSelectHelper.WrappedCredentialsStore) o).getStore().getContext();
            } else if (o instanceof CredentialsStoreAction.CredentialsWrapper) {
                o = ((CredentialsStoreAction.CredentialsWrapper) o).getStore().getContext();
            } else if (o instanceof CredentialsStoreAction.DomainWrapper) {
                o = ((CredentialsStoreAction.DomainWrapper) o).getStore().getContext();
            } else if (o instanceof Descriptor && i == 1) { // URL is /descriptorByName/...
                // TODO this is a https://issues.jenkins-ci.org/browse/JENKINS-19413 workaround

                // we need to try an infer from the Referer as this is likely a doCheck or a doFill method
                String referer = request.getReferer();
                String rootPath = request.getRootPath();
                if (referer != null && rootPath != null && referer.startsWith(rootPath)) {
                    // strip out any query portion of the referer URL.
                    String path = URI.create(referer.substring(rootPath.length())).getPath().substring(1);

                    // TODO have Stapler expose a method that can walk a path and produce the ancestors and use that

                    // what now follows is an example of a really evil hack, consequently this means...
                    //
                    //                         7..       ,
                    //                      MMM.          MMM.
                    //                     MMMMM        .MMMMMM
                    //                    MMMM.           MMMMM.
                    //                  OMMM                 MMZ
                    //                  MMM                    MM
                    //                .MMMM    $.     .       .MM,
                    //                MMMMM    MMM   MM        MMM
                    //               .MMMMM. MMMMD  8MMM.     MMMM
                    //               MMMMMMM.MMMM    MMMMM.  MMMMMM
                    //               MMMMMMMM.M .     MMM.  MMMMMMM
                    //               MMMMMMMMM.            MMMMMMMM
                    //               MMMMMMMMM . ..     MMMMMMMMMMM
                    //               MMMMMMMM  IMMMM  Z.MMMMMMMMMM ,
                    //               .MMMMMMM   .M:M   MMMMMMMMMMM M
                    //              I MMMMMMM.         MMMMMMMMMO  M
                    //           MMMM  MMMMMMM       .MMMMMMMMM.   .
                    //         :MMMMMM.MMMMMMM.      MMMMMMMM   .MMMM
                    //         MMMMMMMMMMMMMMMM      MMMMMMM   MMMMMMMM
                    //        MMMMMMMMM.MMMMMMM      MMMMMMM MMMMMMMMMM.
                    //        MMMMMMMMMMMMMMMM?      MMMMMM MMMMMMMMMMM
                    //        MMMMMMMMMM  .  .       MMMMMIMMMMMMMMMMMM.
                    //        MMMMMMMMMM               .. :MMMMMMMMMMMM.
                    //        DMMMMMMMMMM                 MMMMMMMMMMMMM.
                    //         MMMMMMMMMM.M.              MMMMMMMMMMMM.
                    //           MMMMMM,                    ....
                    //
                    //                   I AM A SAD PANDA

                    List<String> pathSegments = new ArrayList<>(Arrays.asList(StringUtils.split(path, "/")));
                    // strip out any leading junk
                    while (!pathSegments.isEmpty() && StringUtils.isBlank(pathSegments.get(0))) {
                        pathSegments.remove(0);
                    }
                    if (pathSegments.size() >= 2) {
                        String firstSegment = pathSegments.get(0);
                        if ("user".equals(firstSegment)) {
                            User user = User.getById(pathSegments.get(1), true);
                            if (type.isInstance(user) && CredentialsProvider.hasStores(user)) {
                                // we have a winner
                                return type.cast(user);
                            }
                        } else if ("job".equals(firstSegment) || "item".equals(firstSegment) || "view"
                                .equals(firstSegment)) {
                            int index = 0;
                            while (index < pathSegments.size()) {
                                String segment = pathSegments.get(index);
                                if ("view".equals(segment)) {
                                    // remove the /view/
                                    pathSegments.remove(index);
                                    if (index < pathSegments.size()) {
                                        // remove the /view/{name}
                                        pathSegments.remove(index);
                                    }
                                } else if ("job".equals(segment) || "item".equals(segment)) {
                                    // remove the /job/
                                    pathSegments.remove(index);
                                    // skip the name
                                    index++;
                                } else {
                                    // we have gone as far as we can parse the item path structure
                                    while (index < pathSegments.size()) {
                                        // remove the remainder
                                        pathSegments.remove(index);
                                    }
                                }
                            }
                            Jenkins jenkins = Jenkins.get();
                            while (!pathSegments.isEmpty()) {
                                String fullName = StringUtils.join(pathSegments, "/");
                                Item item = jenkins.getItemByFullName(fullName);
                                if (item != null) {
                                    if (type.isInstance(item) && CredentialsProvider.hasStores(item)) {
                                        // we have a winner
                                        return type.cast(item);
                                    }
                                }
                                // walk back up and try one level less deep
                                pathSegments.remove(pathSegments.size() - 1);
                            }
                        }
                    }
                    // ok we give up, we are not thirsty for more, we'll let "normal" ancestor in path logic continue
                }
            }
            if (type.isInstance(o) && o instanceof ModelObject && CredentialsProvider.hasStores((ModelObject) o)) {
                return type.cast(o);
            }
        }
        return null;

    }

    /**
     * {@inheritDoc}
     */
    public FormValidation.CheckMethod getCheckMethod(String fieldName) {
        // this is an ugly hack to make the @ContextInPath annotation more failsafe
        // requires that you explicitly call out the checkUrl: checkUrl="${descriptor.getCheckUrl('fieldName')}"
        FormValidation.CheckMethod method = enhancedCheckMethods.get(fieldName);
        if (method == null) {
            method = new EnhancedCheckMethod(this, fieldName);
            enhancedCheckMethods.put(fieldName, method);
        }
        return method;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void calcFillSettings(String field, Map<String, Object> attributes) {
        if (attributes.containsKey("fillUrl")) {
            // the user already provided a custom one, get out of the way
            super.calcFillSettings(field, attributes);
            return;
        }
        // this is an ugly hack to make the @ContextInPath annotation more failsafe
        super.calcFillSettings(field, attributes);
        if (attributes.containsKey("fillUrl")) {
            try {
                JellyContext jelly = Functions.getCurrentJellyContext();
                Object it = jelly.findVariable("it");
                if (it instanceof CredentialsStore) {
                    ModelObject context = ((CredentialsStore) it).getContext();
                    for (CredentialsSelectHelper.ContextResolver r : ExtensionList
                            .lookup(CredentialsSelectHelper.ContextResolver.class)) {
                        String token = r.getToken(context);
                        if (token != null) {
                            String fillUrl = (String) attributes.get("fillUrl");
                            if (fillUrl != null) {
                                if (fillUrl.indexOf('?') != -1) {
                                    fillUrl = fillUrl + '&';
                                } else {
                                    fillUrl = fillUrl + '?';
                                }
                                attributes.put("fillUrl", fillUrl + "$provider=" +
                                        URLEncoder.encode(r.getClass().getName(), "UTF-8")
                                        + "&$token=" + URLEncoder.encode(token, "UTF-8"));
                            }
                        }
                    }

                }
            } catch (AssertionError | UnsupportedEncodingException e) {
                // ignore, we did the best we could
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void calcAutoCompleteSettings(String field, Map<String, Object> attributes) {
        if (attributes.containsKey("autoCompleteUrl")) {
            // the user already provided a custom one, get out of the way
            super.calcAutoCompleteSettings(field, attributes);
            return;
        }
        // this is an ugly hack to make the @ContextInPath annotation more failsafe
        super.calcAutoCompleteSettings(field, attributes);
        if (attributes.containsKey("autoCompleteUrl")) {
            try {
                JellyContext jelly = Functions.getCurrentJellyContext();
                Object it = jelly.findVariable("it");
                if (it instanceof CredentialsStore) {
                    ModelObject context = ((CredentialsStore) it).getContext();
                    for (CredentialsSelectHelper.ContextResolver r : ExtensionList
                            .lookup(CredentialsSelectHelper.ContextResolver.class)) {
                        String token = r.getToken(context);
                        if (token != null) {
                            String autoCompleteUrl = (String) attributes.get("autoCompleteUrl");
                            if (autoCompleteUrl != null) {
                                if (autoCompleteUrl.indexOf('?') != -1) {
                                    autoCompleteUrl = autoCompleteUrl + '&';
                                } else {
                                    autoCompleteUrl = autoCompleteUrl + '?';
                                }
                                attributes.put("autoCompleteUrl",
                                        autoCompleteUrl + "$provider=" +
                                                URLEncoder.encode(r.getClass().getName(), "UTF-8")
                                                + "&$token=" + URLEncoder.encode(token, "UTF-8"));
                            }
                        }
                    }

                }
            } catch (AssertionError | UnsupportedEncodingException e) {
                // ignore, we did the best we could
            }
        }
    }

    /**
     * An enhanced {@link FormValidation.CheckMethod} that can add assistance for resolving the context from the
     * request path.
     *
     * @since 2.1.5
     */
    @Restricted(NoExternalUse.class)
    public static class EnhancedCheckMethod extends FormValidation.CheckMethod {

        /**
         * {@inheritDoc}
         */
        public EnhancedCheckMethod(Descriptor descriptor, String fieldName) {
            super(descriptor, fieldName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toCheckUrl() {
            String checkUrl = super.toCheckUrl();
            if (checkUrl == null) {
                return null;
            }
            try {
                JellyContext jelly = Functions.getCurrentJellyContext();
                Object it = jelly.findVariable("it");
                if (it instanceof CredentialsStore) {
                    ModelObject context = ((CredentialsStore) it).getContext();
                    for (CredentialsSelectHelper.ContextResolver r : ExtensionList
                            .lookup(CredentialsSelectHelper.ContextResolver.class)) {
                        String token = r.getToken(context);
                        if (token != null) {
                            if (checkUrl.endsWith(".toString()")) {
                                checkUrl = StringUtils.removeEnd(checkUrl, ".toString()");
                            } else {
                                checkUrl = checkUrl + "+qs(this).addThis()";
                            }
                            return checkUrl
                                    + ".append('$provider=" +
                                    Functions.jsStringEscape(URLEncoder.encode(r.getClass().getName(), "UTF-8"))
                                    + "')"
                                    + ".append('$token="
                                    + Functions.jsStringEscape(URLEncoder.encode(token, "UTF-8"))
                                    + "')"
                                    + ".toString()";
                        }
                    }

                }
            } catch (AssertionError | UnsupportedEncodingException e) {
                // ignore, we did the best we could
            }
            return checkUrl;
        }
    }

}
