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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ManagementLink;
import hudson.model.ModelObject;
import hudson.model.Saveable;
import hudson.security.ACL;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The root store of credentials.
 */
@Extension
public class SystemCredentialsProvider extends ManagementLink
        implements Describable<SystemCredentialsProvider>, Saveable, StaplerProxy {

    /**
     * Our logger
     */
    private static final Logger LOGGER = Logger.getLogger(SystemCredentialsProvider.class.getName());

    /**
     * Our credentials.
     */
    private List<Credentials> credentials = new ArrayList<Credentials>();

    /**
     * Constructor.
     */
    public SystemCredentialsProvider() {
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
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return CredentialsProvider.allCredentialsDescriptors().isEmpty() ? null : "/plugin/credentials/images/48x48/credentials.png";
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
     * Get all the credentials.
     *
     * @return all the credentials.
     */
    @SuppressWarnings("unused") // used by stapler
    public List<Credentials> getCredentials() {
        return credentials;
    }

    /**
     * Gets all the credentials descriptors.
     *
     * @return all the credentials descriptors.
     */
    @SuppressWarnings("unused") // used by stapler
    public DescriptorExtensionList<Credentials, Descriptor<Credentials>> getCredentialDescriptors() {
        return CredentialsProvider.allCredentialsDescriptors();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Descriptor<SystemCredentialsProvider> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Only sysadmin can access this page.
     */
    public Object getTarget() {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
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
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        JSONObject data = req.getSubmittedForm();
        credentials = req.bindJSONToList(Credentials.class, data.get("credentials"));
        save();
        return HttpResponses.redirectToContextRoot(); // send the user back to the top page
    }

    /**
     * {@inheritDoc}
     */
    public void save() throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        getConfigFile().write(this);
    }

    /**
     * Gets the configuration file that this {@link CredentialsProvider} uses to store its credentials.
     *
     * @return the configuration file that this {@link CredentialsProvider} uses to store its credentials.
     */
    public static XmlFile getConfigFile() {
        return new XmlFile(new File(Hudson.getInstance().getRootDir(), "credentials.xml"));
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

    /**
     * The {@link CredentialsProvider} that exposes credentials stored in
     * {@link com.cloudbees.plugins.credentials.SystemCredentialsProvider#getCredentials()}
     */
    @Extension
    @SuppressWarnings("unused") // used by Jenkins
    public static class ProviderImpl extends CredentialsProvider {

        /**
         * The scopes that are relevant to the store.
         */
        private static final Set<CredentialsScope> SCOPES =
                Collections.unmodifiableSet(new LinkedHashSet<CredentialsScope>(
                        Arrays.asList(CredentialsScope.GLOBAL, CredentialsScope.SYSTEM)));

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<CredentialsScope> getScopes(ModelObject object) {
            if (object instanceof Hudson || object instanceof SystemCredentialsProvider) {
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
            List<C> result = new ArrayList<C>();
            boolean includeSystemScope = Hudson.getInstance() == itemGroup;
            if (ACL.SYSTEM.equals(authentication)) {
                for (Credentials credential : SystemCredentialsProvider.getInstance().getCredentials()) {
                    if (type.isInstance(credential) && (includeSystemScope || !CredentialsScope.SYSTEM
                            .equals(credential.getScope()))) {
                        result.add(type.cast(credential));
                    }
                }
            }
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @NonNull Item item,
                                                              @Nullable Authentication authentication) {
            List<C> result = new ArrayList<C>();
            if (ACL.SYSTEM.equals(authentication)) {
                for (Credentials credential : SystemCredentialsProvider.getInstance().getCredentials()) {
                    if (type.isInstance(credential) && !CredentialsScope.SYSTEM.equals(credential.getScope())) {
                        result.add(type.cast(credential));
                    }
                }
            }
            return result;
        }
    }
}
