package com.cloudbees.plugins.credentials;

import hudson.ExtensionPoint;

/**
 * Some credential types can store some of the credential details in a file outside of Jenkins. Taking a snapshot
 * of the credential ensures that all the details are captured within the credential. For example
 * {@link com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl} can use different keystores implementations
 * to hold the certificiate. Calling {@link #snapshot(Credentials)} resolve the actual source into
 * a source like {@link com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl.UploadedKeyStoreSource}
 * which is self contained.
 *
 * @since 1.14
 */
public abstract class CredentialsSnapshotTaker<C extends Credentials> implements ExtensionPoint {

    /**
     * The type of credentials that this {@link CredentialsSnapshotTaker} operates on.
     *
     * @return the type of credentials that this {@link CredentialsSnapshotTaker} operates on.
     */
    public abstract Class<C> type();

    /**
     * Create a self-contained version of this {@link Credentials} that does not require access to any external files
     * or resources.
     *
     * @param credentials the credentials
     * @return either the original credentials if the {@link Credentials} is already self-contained or a new identical
     * instance that is self-contained.
     */
    public abstract C snapshot(C credentials);

}
