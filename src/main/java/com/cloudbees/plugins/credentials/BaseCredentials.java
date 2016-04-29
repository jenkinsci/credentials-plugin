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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Base class for Credentials.
 */
@ExportedBean
public class BaseCredentials implements Credentials {

    /**
     * Set serialization version.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The scope of the credential.
     */
    @CheckForNull
    private final CredentialsScope scope;

    @SuppressWarnings("unused")
    protected BaseCredentials() {
        this.scope = null;
    }

    /**
     * Creates an instance with specific scope.
     *
     * @param scope the scope.
     */
    public BaseCredentials(@CheckForNull CredentialsScope scope) {
        this.scope = scope;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public CredentialsScope getScope() {
        return scope;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public CredentialsDescriptor getDescriptor() {
        // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
        return (CredentialsDescriptor) Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }
}
