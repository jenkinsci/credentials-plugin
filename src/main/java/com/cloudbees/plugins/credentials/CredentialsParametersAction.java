/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

import hudson.model.Cause;
import hudson.model.InvisibleAction;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import jenkins.model.RunAction2;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Invisible run action for tracking which user supplied which {@link CredentialsParameterValue} to a {@link Run}.
 * This class exposes {@link #add(String, CredentialsParameterValue)} and {@link #addFromParameterValues(String, Collection)}
 * for adding these parameter values to an existing action. When this action is attached to a run, this action imports
 * any CredentialsParameterValues contained in the run's {@link ParametersAction} and associates it to the optionally
 * present {@link hudson.model.Cause.UserIdCause}. Parameters without a user id cannot reference
 * {@linkplain CredentialsScope#USER user-scoped credentials}.
 *
 * @since TODO
 */
public class CredentialsParametersAction extends InvisibleAction implements RunAction2 {

    /**
     * Maps a user id to a CredentialsParameterValue to indicate the user performing the credential action.
     */
    static class AuthenticatedCredentials {
        private final String userId;
        private final CredentialsParameterValue parameterValue;

        private AuthenticatedCredentials(@CheckForNull String userId, @Nonnull CredentialsParameterValue parameterValue) {
            this.userId = userId;
            this.parameterValue = parameterValue;
        }

        /**
         * @return the effective user id of who supplied this credentials parameter value
         */
        String getUserId() {
            return userId;
        }

        /**
         * @return the credentials parameter value supplied
         */
        CredentialsParameterValue getParameterValue() {
            return parameterValue;
        }
    }

    private final Map<String, AuthenticatedCredentials> values = new ConcurrentHashMap<>();

    /**
     * Looks up an existing CredentialsParameterAction for a Run or adapts and attaches an existing ParametersAction to
     * a new CredentialsParametersAction. Returns {@code null} if the run has no parameters.
     */
    static @CheckForNull CredentialsParametersAction forRun(@Nonnull Run<?, ?> run) {
        final CredentialsParametersAction existingAction = run.getAction(CredentialsParametersAction.class);
        if (existingAction != null) {
            return existingAction;
        }
        if (run.getAction(ParametersAction.class) == null) {
            return null;
        }
        run.addAction(new CredentialsParametersAction());
        // onAttached copies existing ParametersAction
        return run.getAction(CredentialsParametersAction.class);
    }

    /**
     * Adds a CredentialsParameterValue to this action if it does not already exist.
     */
    public void add(@CheckForNull String userId, @Nonnull CredentialsParameterValue parameterValue) {
        values.put(parameterValue.getName(), new AuthenticatedCredentials(userId, parameterValue));
    }

    /**
     * Bulk add of CredentialsParameterValues from a heterogeneous collection of parameter values.
     */
    public void addFromParameterValues(@CheckForNull String userId, @Nonnull Collection<ParameterValue> parameterValues) {
        parameterValues.stream()
                .filter(CredentialsParameterValue.class::isInstance)
                .map(CredentialsParameterValue.class::cast)
                .forEach(parameterValue -> add(userId, parameterValue));
    }

    @CheckForNull AuthenticatedCredentials findCredentialsByParameterName(@Nonnull String name) {
        return values.get(name);
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        final ParametersAction action = r.getAction(ParametersAction.class);
        if (action != null) {
            final Cause.UserIdCause cause = r.getCause(Cause.UserIdCause.class);
            addFromParameterValues(cause == null ? null : cause.getUserId(), action.getParameters());
        }
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        // this space intentionally left blank
    }

}
