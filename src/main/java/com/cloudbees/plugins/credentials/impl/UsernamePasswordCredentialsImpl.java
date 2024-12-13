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
package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;

import jenkins.security.FIPS140;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Concrete implementation of {@link StandardUsernamePasswordCredentials}.
 *
 * @since 1.6
 */
@SuppressWarnings("unused") // read resolved by extension plugins
public class UsernamePasswordCredentialsImpl extends BaseStandardCredentials implements
        StandardUsernamePasswordCredentials {

    /**
     * The username.
     */
    @NonNull
    private final String username;

    /**
     * The password.
     */
    @NonNull
    private final Secret password;

    @Nullable
    private Boolean usernameSecret = false;

    /**
     * Constructor.
     *
     * @param scope       the credentials scope
     * @param id          the ID or {@code null} to generate a new one.
     * @param description the description.
     * @param username    the username.
     * @param password    the password.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused") // by stapler
    public UsernamePasswordCredentialsImpl(@CheckForNull CredentialsScope scope,
                                           @CheckForNull String id, @CheckForNull String description,
                                           @CheckForNull String username, @CheckForNull String password)
            throws Descriptor.FormException {
        super(scope, id, description);
        this.username = Util.fixNull(username);
        if(FIPS140.useCompliantAlgorithms() && StringUtils.length(password) < 14) {
            throw new Descriptor.FormException(Messages.passwordTooShortFIPS(), "password");
        }
        this.password = Secret.fromString(password);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public Secret getPassword() {
        return password;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isUsernameSecret() {
        return usernameSecret != null ? usernameSecret : true;
    }

    @DataBoundSetter
    public void setUsernameSecret(boolean usernameSecret) {
        this.usernameSecret = usernameSecret;
    }

    /**
     * {@inheritDoc}
     */
    @Extension(ordinal = 1)
    @Symbol("usernamePassword")
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.UsernamePasswordCredentialsImpl_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "symbol-id-card";
        }

        @RequirePOST
        public FormValidation doCheckPassword(@QueryParameter String password) {
            if(FIPS140.useCompliantAlgorithms() && StringUtils.length(password) < 14) {
                return FormValidation.error(Messages.passwordTooShortFIPS());
            }
            return FormValidation.ok();
        }
    }
}
