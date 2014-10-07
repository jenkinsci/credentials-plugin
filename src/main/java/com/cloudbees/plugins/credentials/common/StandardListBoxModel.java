package com.cloudbees.plugins.credentials.common;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.ListBoxModel;

/**
 * {@link ListBoxModel} with {@link StandardCredentials} support.
 * See {@link AbstractIdCredentialsListBoxModel} for usage.
 */
public class StandardListBoxModel
        extends AbstractIdCredentialsListBoxModel<StandardListBoxModel, StandardCredentials> {

    /**
     * {@inheritDoc}
     */
    @NonNull
    protected String describe(@NonNull StandardCredentials c) {
        return CredentialsNameProvider.name(c);
    }
}
