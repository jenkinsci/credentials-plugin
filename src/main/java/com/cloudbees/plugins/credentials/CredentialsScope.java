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

import hudson.model.Computer;
import hudson.model.ModelObject;
import hudson.model.Node;
import java.io.Serializable;
import jenkins.model.Jenkins;

/**
 * The scope of credentials.
 */
public enum CredentialsScope implements Serializable {

    /**
     * This credential is only available to the object on which the credential is associated. Typically you would
     * use SYSTEM scoped credentials for things like email auth, slave connection, etc, i.e. where the
     * Jenkins instance itself is using the credential.
     */
    SYSTEM {
        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return Messages.CredentialsScope_SystemDisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isVisible(ModelObject context) {
            // we future-proof for the event that somebody defines a per-node credentials store
            // in which case a ViewCredentialsAction for the Node/Computer should also show the
            // SYSTEM scoped credentials
            return context instanceof Jenkins || context instanceof Node || context instanceof Computer;
        }
    },
    /**
     * This credential is available to the object on which the credential is associated and all objects that are
     * children of that object. Typically you would use GLOBAL scoped credentials for things that are needed by
     * {@link hudson.model.Job}s.
     */
    GLOBAL {
        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return Messages.CredentialsScope_GlobalDisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isVisible(ModelObject context) {
            return true;
        }
    },
    /**
     * This credential is available to the user which which the credential is associated. Typically you would use
     * USER scoped credentials where credentials are required for immediate actions. Some examples could include:
     * <ul>
     * <li>Tag this build</li>
     * <li>Deploy artifacts to some container NOW</li>
     * <li>etc</li>
     * </ul>
     * The key point is that the user is making a request and instructing Jenkins to use one of their own credentials
     * in the scope of the request. Where the request is something that happens automatically, e.g. a build triggered
     * by SCM polling, then USER scoped credentials are not usually appropriate (unless the action is one that can fail
     * gracefully)
     * <p>
     * Another way of looking at this is, if the action is one that is configured from the {@link hudson.model.Job}'s
     * Configure page, then don't use USER scope, as that prevents another user from modifying the job configuration
     * (because they will only be able to see their own credentials)
     */
    USER {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.CredentialsScope_UserDisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isVisible(ModelObject context) {
            // technically the context doesn't matter here as there are currently no child context objects of User
            // but in the event that some plugin were to - say - permit defining user specific jobs
            // that were attached to the user object then those jobs should have the user's credentials
            // available.
            return true;
        }
    };

    /**
     * Gets the display name for the credentials.
     *
     * @return The display name for the credentials.
     */
    public abstract String getDisplayName();

    /**
     * Tests if credentials with this scope are visible in the supplied context.
     *
     * @param context the context.
     * @return {@code true} if credentials with this scope are visible in the supplied context.
     * @since 2.1.5
     */
    public abstract boolean isVisible(ModelObject context);
}
