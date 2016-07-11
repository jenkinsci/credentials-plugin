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
package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.ContextInPath;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsSelectHelper;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.util.FormValidation;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.EnumSet;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import static com.cloudbees.plugins.credentials.CredentialsSelectHelper.*;

/**
 * Base class for {@link StandardCredentials}.
 */
@ExportedBean
public abstract class BaseStandardCredentials extends BaseCredentials implements StandardCredentials {
    /**
     * Our ID.
     */
    @NonNull
    private final String id;

    /**
     * Our description.
     */
    @NonNull
    private final String description;

    /**
     * Constructor.
     *
     * @param id          the id.
     * @param description the description.
     */
    public BaseStandardCredentials(@CheckForNull String id, @CheckForNull String description) {
        super();
        this.id = IdCredentials.Helpers.fixEmptyId(id);
        this.description = Util.fixNull(description);
    }

    /**
     * Constructor.
     *
     * @param scope       the scope.
     * @param id          the id.
     * @param description the description.
     */
    public BaseStandardCredentials(@CheckForNull CredentialsScope scope, @CheckForNull String id,
                                   @CheckForNull String description) {
        super(scope);
        this.id = IdCredentials.Helpers.fixEmptyId(id);
        this.description = Util.fixNull(description);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Exported
    public String getDescription() {
        return description;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Exported
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return IdCredentials.Helpers.hashCode(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        return IdCredentials.Helpers.equals(this, o);
    }

    /**
     * Descriptor to use for subclasses of {@link BaseStandardCredentials}.
     * <p>{@code <st:include page="id-and-description" class="${descriptor.clazz}"/>} in {@code credentials.jelly} to
     * pick up standard controls for {@link #getId} and {@link #getDescription}.
     */
    protected static abstract class BaseStandardCredentialsDescriptor extends CredentialsDescriptor {

        protected BaseStandardCredentialsDescriptor() {
            clazz.asSubclass(BaseStandardCredentials.class);
        }

        protected BaseStandardCredentialsDescriptor(Class<? extends BaseStandardCredentials> clazz) {
            super(clazz);
        }

        @CheckForNull
        private static FormValidation checkForDuplicates(String value, ModelObject context, ModelObject object) {
            CredentialsMatcher withId = CredentialsMatchers.withId(value);
            for (CredentialsStore store : CredentialsProvider.lookupStores(object)) {
                if (!store.hasPermission(CredentialsProvider.VIEW)) {
                    continue;
                }
                ModelObject storeContext = store.getContext();
                for (Domain domain : store.getDomains()) {
                    for (Credentials match : CredentialsMatchers.filter(store.getCredentials(domain), withId)) {
                        if (storeContext == context) {
                            return FormValidation.error("This ID is already in use");
                        } else {
                            CredentialsScope scope = match.getScope();
                            if (scope != null && !scope.isVisible(context)) {
                                // scope is not exported to child contexts
                                continue;
                            }
                            return FormValidation.warning("The ID ‘%s’ is already in use in %s", value,
                                    storeContext instanceof Item
                                            ? ((Item) storeContext).getFullDisplayName()
                                            : storeContext.getDisplayName());
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Gets the check id url for the specified store.
         *
         * @param store the store.
         * @return the url of the id check endpoint.
         * @throws UnsupportedEncodingException if the JVM does not implement the JLS.
         */
        public String getCheckIdUrl(CredentialsStore store) throws UnsupportedEncodingException {
            ModelObject context = store.getContext();
            for (ContextResolver r : ExtensionList.lookup(ContextResolver.class)) {
                String token = r.getToken(context);
                if (token != null) {
                    return Jenkins.getActiveInstance().getRootUrlFromRequest() + "/" + getDescriptorUrl()
                            + "/checkId?provider=" + r.getClass().getName() + "&token="
                            + URLEncoder.encode(token, "UTF-8");
                }
            }
            return Jenkins.getActiveInstance().getRootUrlFromRequest() + "/" + getDescriptorUrl()
                    + "/checkId?provider=null&token=null";
        }

        public final FormValidation doCheckId(@ContextInPath ModelObject context, @QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.ok();
            }
            if (!value.matches("[a-zA-Z0-9_.-]+")) { // anything else considered kosher?
                return FormValidation.error("Unacceptable characters");
            }
            FormValidation problem = checkForDuplicates(value, context, context);
            if (problem != null) {
                return problem;
            }
            if (!(context instanceof User)) {
                User me = User.current();
                if (me != null) {
                    problem = checkForDuplicates(value, context, me);
                    if (problem != null) {
                        return problem;
                    }
                }
            }
            if (!(context instanceof Jenkins)) {
                // CredentialsProvider.lookupStores(User) does not return SystemCredentialsProvider.
                Jenkins j = Jenkins.getInstance();
                if (j != null) {
                    problem = checkForDuplicates(value, context, j);
                    if (problem != null) {
                        return problem;
                    }
                }
            }
            return FormValidation.ok();
        }

    }

}
