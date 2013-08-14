package com.cloudbees.plugins.credentials.common;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;

/**
 * @author stephenc
 * @since 14/08/2013 14:35
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
