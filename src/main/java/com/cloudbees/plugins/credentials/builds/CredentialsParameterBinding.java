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
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Associates a user ID to a {@link CredentialsParameterValue}. This is effectively a flattened tuple of that.
 *
 * @since 2.3.0
 */
@Restricted(NoExternalUse.class)
public class CredentialsParameterBinding {

    static CredentialsParameterBinding fromParameter(@CheckForNull final String userId, @NonNull final CredentialsParameterValue parameterValue) {
        return new CredentialsParameterBinding(userId, parameterValue.getName(), parameterValue.getValue(),
                parameterValue.isDefaultValue());
    }

    private final String userId;
    private final String parameterName;
    private final String credentialsId;
    private final boolean isDefaultValue;

    private CredentialsParameterBinding(String userId, String parameterName, String credentialsId, boolean isDefaultValue) {
        this.userId = userId;
        this.parameterName = parameterName;
        this.credentialsId = credentialsId;
        this.isDefaultValue = isDefaultValue;
    }

    @CheckForNull
    public String getUserId() {
        return userId;
    }

    @NonNull
    public String getParameterName() {
        return parameterName;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    public boolean isDefaultValue() {
        return isDefaultValue;
    }
}
