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

package com.cloudbees.plugins.credentials.builds;

import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.InvisibleAction;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks credentials being bound and unbound to a build. An instance is created and attached to a build when it is
 * first looked up via {@link #getOrCreate(Run)}. This binds any existing {@link CredentialsParameterValue}s using the
 * {@link hudson.model.Cause.UserIdCause} if available. Other plugins may
 * {@linkplain #bindCredentialsParameter(String, CredentialsParameterValue) bind} and
 * {@linkplain #unbindCredentialsParameter(String) unbind} parameters during a build.
 *
 * @since 2.3.0
 */
public final class CredentialsParameterBinder extends InvisibleAction {

    /**
     * Gets or creates a CredentialsParameterBinder for the given run.
     * This automatically imports credentials parameters provided in a {@link ParametersAction}.
     */
    @NonNull
    public static CredentialsParameterBinder getOrCreate(@NonNull final Run<?, ?> run) {
        CredentialsParameterBinder resolver = run.getAction(CredentialsParameterBinder.class);
        if (resolver == null) {
            resolver = new CredentialsParameterBinder();
            final ParametersAction action = run.getAction(ParametersAction.class);
            if (action != null) {
                final Cause.UserIdCause cause = run.getCause(Cause.UserIdCause.class);
                final String userId = cause == null ? null : cause.getUserId();
                for (final ParameterValue parameterValue : action) {
                    if (parameterValue instanceof CredentialsParameterValue) {
                        resolver.bindCredentialsParameter(userId, (CredentialsParameterValue) parameterValue);
                    }
                }
            }
            run.addAction(resolver);
        }
        return resolver;
    }

    private final Map<String, CredentialsParameterBinding> boundCredentials = new ConcurrentHashMap<>();

    /**
     * Binds a credentials parameter with an optional user ID. User credentials require a user ID.
     */
    public void bindCredentialsParameter(@CheckForNull final String userId, @NonNull final CredentialsParameterValue parameterValue) {
        boundCredentials.put(parameterValue.getName(), CredentialsParameterBinding.fromParameter(userId, parameterValue));
    }

    /**
     * Unbinds a credentials parameter.
     */
    public void unbindCredentialsParameter(@NonNull final String parameterName) {
        boundCredentials.remove(parameterName);
    }

    @CheckForNull
    @Restricted(NoExternalUse.class)
    public CredentialsParameterBinding forParameterName(@NonNull final String parameterName) {
        return boundCredentials.get(parameterName);
    }

    boolean isEmpty() {
        return boundCredentials.isEmpty();
    }
}
