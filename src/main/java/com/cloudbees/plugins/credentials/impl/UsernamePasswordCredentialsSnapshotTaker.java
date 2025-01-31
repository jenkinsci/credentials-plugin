package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsSnapshotTaker;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.Secret;

@Extension
public class UsernamePasswordCredentialsSnapshotTaker extends CredentialsSnapshotTaker<StandardUsernamePasswordCredentials> {

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<StandardUsernamePasswordCredentials> type() {
        return StandardUsernamePasswordCredentials.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandardUsernamePasswordCredentials snapshot(StandardUsernamePasswordCredentials credentials) {
        if (credentials instanceof UsernamePasswordCredentialsImpl) {
            return credentials;
        }
        try {
            UsernamePasswordCredentialsImpl snapshot =
                    new UsernamePasswordCredentialsImpl(credentials.getScope(), credentials.getId(), credentials.getDescription(), credentials.getUsername(), Secret.toString(credentials.getPassword()));
            snapshot.setUsernameSecret(credentials.isUsernameSecret());
            return snapshot;
        } catch (Descriptor.FormException e) {
            throw new RuntimeException(e);
        }
    }
}