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
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
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
    public final boolean equals(Object o) {
        return IdCredentials.Helpers.equals(this, o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return IdCredentials.Helpers.hashCode(this);
    }

    /**
     * Descriptor to use for subclasses of {@link BaseStandardCredentials}.
     * <p>{@code <st:include page="id-and-description" class="${descriptor.clazz}"/>} in {@code credentials.jelly} to pick up standard controls for {@link #getId} and {@link #getDescription}.
     */
    protected static abstract class BaseStandardCredentialsDescriptor extends CredentialsDescriptor {

        protected BaseStandardCredentialsDescriptor() {
            clazz.asSubclass(BaseStandardCredentials.class);
        }

        protected BaseStandardCredentialsDescriptor(Class<? extends BaseStandardCredentials> clazz) {
            super(clazz);
        }

        public final FormValidation doCheckId(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.ok();
            }
            if (!value.matches("[a-zA-Z0-9_.-]+")) { // anything else considered kosher?
                return FormValidation.error("Unacceptable characters");
            }
            return FormValidation.ok();
        }

    }

}
