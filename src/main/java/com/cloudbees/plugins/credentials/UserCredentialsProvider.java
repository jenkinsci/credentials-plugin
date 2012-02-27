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
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.security.ACL;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A store of credentials tied to a specific {@link User}.
 */
@Extension
public class UserCredentialsProvider extends CredentialsProvider {

    /**
     * We only care about {@link CredentialsScope#USER} scoped credentials.
     */
    private static final Set<CredentialsScope> SCOPES = Collections.singleton(CredentialsScope.USER);

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CredentialsScope> getScopes(ModelObject object) {
        if (object instanceof User) {
            return SCOPES;
        }
        return super.getScopes(object);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @Nullable ItemGroup itemGroup,
                                                          @Nullable Authentication authentication) {
        // ignore itemGroup, as per-user credentials are available on any object
        if (authentication == null) {
            // assume ACL#SYSTEM
            authentication = ACL.SYSTEM;
        }
        List<C> result = new ArrayList<C>();
        if (!ACL.SYSTEM.equals(authentication)) {
            User user = authentication == null || authentication instanceof AnonymousAuthenticationToken
                    ? null
                    : User.get(authentication.getName());
            if (user != null) {
                UserCredentialsProperty property = user.getProperty(UserCredentialsProperty.class);
                if (property != null) {
                    result.addAll(property.getCredentials(type));
                }
            }
        }
        return result;
    }

    /**
     * Need a user property to hold the user's personal credentials.
     */
    public static class UserCredentialsProperty extends UserProperty {

        /**
         * The user's credentials.
         */
        private final List<Credentials> credentials;

        /**
         * Constructor for stapler.
         *
         * @param credentials the credentials.
         */
        @DataBoundConstructor
        public UserCredentialsProperty(List<Credentials> credentials) {
            this.credentials = new ArrayList<Credentials>(credentials);
        }

        /**
         * Helper method.
         *
         * @param type type of credentials to get.
         * @param <C>  type of credentials to get.
         * @return the subset of the user's credentials that are of the specified type.
         */
        public <C extends Credentials> List<C> getCredentials(Class<C> type) {
            List<C> result = new ArrayList<C>();
            for (Credentials credential : credentials) {
                if (type.isInstance(credential)) {
                    result.add(type.cast(credential));
                }
            }
            return result;
        }

        /**
         * Gets all the user's credentials.
         *
         * @return all the user's credentials.
         */
        @SuppressWarnings("unused") // used by stapler
        public List<Credentials> getCredentials() {
            return credentials;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public UserProperty reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
            User selUser = req.findAncestorObject(User.class);
            User curUser = User.current();
            // only process changes to this property for the current user
            if (selUser != null && curUser != null && selUser.getId().equals(curUser.getId())) {
                return getDescriptor().newInstance(req, form);
            }
            return this;
        }

        /**
         * Our user property descriptor.
         */
        @Extension
        @SuppressWarnings("unused") // used by Jenkins
        public static class DescriptorImpl extends UserPropertyDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public UserProperty newInstance(User user) {
                return new UserCredentialsProperty(Collections.<Credentials>emptyList());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.UserCredentialsProvider_DisplayName();
            }

            /**
             * Whether the credentials should be visible on the user's configure screen.
             *
             * @return true if and only if the current request is the current user's configuration screen.
             */
            @SuppressWarnings("unused") // used by stapler
            public boolean isVisible() {
                if (!isEnabled()) {
                    // no point bothering the user if there are no credentials aware plugins installed.
                    return false;
                }
                StaplerRequest req = Stapler.getCurrentRequest();
                if (req == null) {
                    // does not make sense to pretend to be enabled outside of a stapler request
                    return false;
                }
                User selUser = req.findAncestorObject(User.class);
                User curUser = User.current();
                // only enable this property for the current user
                return selUser != null && curUser != null && selUser.equals(curUser);

            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isEnabled() {
                return !all().isEmpty();
            }
        }
    }
}
