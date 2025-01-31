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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ManagementLink;
import hudson.security.GlobalSecurityConfiguration;
import hudson.util.FormApply;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link ManagementLink} to expose the global credentials configuration screen.
 *
 * @see CredentialsProviderManager.Configuration
 * @see GlobalCredentialsConfiguration.Category
 * @since 2.0
 */
@Extension(ordinal = Integer.MAX_VALUE - 212)
public class GlobalCredentialsConfiguration extends ManagementLink
        implements Describable<GlobalCredentialsConfiguration> // TODO once context menu is Icon spec aware //, IconSpec
        {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(GlobalSecurityConfiguration.class.getName());

    /**
     * Our filter.
     */
    @SuppressWarnings("rawtypes")
    public static final Predicate<Descriptor> FILTER = d -> d.getCategory() instanceof Category;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return ExtensionList.lookup(CredentialsDescriptor.class).isEmpty()
                ? null
                : "symbol-credential-providers plugin-credentials";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return Messages.GlobalCredentialsConfiguration_Description();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrlName() {
        return "configureCredentials";
    }

    public String getCategoryName() {
        return "SECURITY";
    }

// TODO uncomment once ContextMenu is IconSpec aware
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public String getIconClassName() {
//        return ExtensionList.lookup(CredentialsDescriptor.class).isEmpty()
//                ? null
//                : "icon-credentials-credentials";
//    }

    /**
     * Handles the form submission
     *
     * @param req the request.
     * @return the response.
     * @throws IOException if something goes wrong.
     * @throws ServletException if something goes wrong.
     * @throws FormException if something goes wrong.
     */
    @RequirePOST
    @NonNull
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // stapler web method binding
    public synchronized HttpResponse doConfigure(@NonNull StaplerRequest2 req) throws IOException, ServletException,
            FormException {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        // logically this change starts from Jenkins
        BulkChange bc = new BulkChange(jenkins);
        try {
            boolean result = configure(req, req.getSubmittedForm());
            LOGGER.log(Level.FINE, "credentials configuration saved: " + result);
            jenkins.save();
            return FormApply
                    .success(result ? req.getContextPath() + "/manage" : req.getContextPath() + "/" + getUrlName());
        } finally {
            bc.commit();
        }
    }

    /**
     * Performs the configuration.
     *
     * @param req  the request.
     * @param json the JSON object.
     * @return {@code false} to keep the client in the same config page.
     * @throws FormException if something goes wrong.
     */
    private boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.ADMINISTER);

        // persist all the provider configs
        boolean result = true;
        for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER)) {
            result &= configureDescriptor(req, json, d);
        }

        return result;
    }

    /**
     * Performs the configuration of a specific {@link Descriptor}.
     *
     * @param req  the request.
     * @param json the JSON object.
     * @param d    the {@link Descriptor}.
     * @return {@code false} to keep the client in the same config page.
     * @throws FormException if something goes wrong.
     */
    private boolean configureDescriptor(StaplerRequest2 req, JSONObject json, Descriptor<?> d) throws
            FormException {
        // collapse the structure to remain backward compatible with the JSON structure before 1.
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name)
                ? json.getJSONObject(name)
                : new JSONObject(); // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<GlobalCredentialsConfiguration> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Our {@link Descriptor}.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<GlobalCredentialsConfiguration> {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.GlobalCredentialsConfiguration_DisplayName();
        }
    }

    /**
     * Security related configurations.
     */
    @Extension
    @Symbol("globalCredentialsConfiguration")
    public static class Category extends GlobalConfigurationCategory {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getShortDescription() {
            return Messages.GlobalCredentialsConfiguration_Description();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.GlobalCredentialsConfiguration_DisplayName();
        }
    }
}
