/*
 * The MIT License
 *
 * Copyright (c) 2011-2016, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.plugins.credentials.CredentialsSnapshotTaker;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import hudson.Extension;
import hudson.util.Secret;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

/**
 * The {@link CredentialsSnapshotTaker} for {@link StandardCertificateCredentials}.
 * Taking a snapshot of the credential ensures that all the details are captured
 * within the credential.
 *
 * @since 1.14
 *
 * Historic note: This code was dropped from {@link CertificateCredentialsImpl}
 * codebase along with most of FileOnMasterKeyStoreSource (deprecated and headed
 * towards eventual deletion) due to SECURITY-1322, see more details at
 * https://www.jenkins.io/security/advisory/2019-05-21/
 * In hind-sight, this snapshot taker was needed to let the
 * {@link CertificateCredentialsImpl.UploadedKeyStoreSource} be used
 * on remote agents.
 */
@Extension
public class CertificateCredentialsSnapshotTaker extends CredentialsSnapshotTaker<StandardCertificateCredentials> {

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<StandardCertificateCredentials> type() {
        return StandardCertificateCredentials.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandardCertificateCredentials snapshot(StandardCertificateCredentials credentials) {
        if (credentials instanceof CertificateCredentialsImpl) {
            final CertificateCredentialsImpl.KeyStoreSource keyStoreSource = ((CertificateCredentialsImpl) credentials).getKeyStoreSource();
            if (keyStoreSource.isSnapshotSource()) {
                return credentials;
            }
            return new CertificateCredentialsImpl(credentials.getScope(), credentials.getId(),
                    credentials.getDescription(), credentials.getPassword().getEncryptedValue(),
                    new CertificateCredentialsImpl.UploadedKeyStoreSource(CertificateCredentialsImpl.UploadedKeyStoreSource.DescriptorImpl.toSecret(keyStoreSource.getKeyStoreBytes())));
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final char[] password = credentials.getPassword().getPlainText().toCharArray();
        try {
            credentials.getKeyStore().store(bos, password);
            bos.close();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            return credentials; // as-is
        } finally {
            Arrays.fill(password, (char) 0);
        }

        return new CertificateCredentialsImpl(credentials.getScope(), credentials.getId(),
                credentials.getDescription(), credentials.getPassword().getEncryptedValue(),
                new CertificateCredentialsImpl.UploadedKeyStoreSource(CertificateCredentialsImpl.UploadedKeyStoreSource.DescriptorImpl.toSecret(bos.toByteArray())));
    }
}
