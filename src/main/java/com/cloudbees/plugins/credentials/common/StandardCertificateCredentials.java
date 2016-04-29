/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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
package com.cloudbees.plugins.credentials.common;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Credentials that have an ID, description, keystore and password, for example client certificates for SSL.
 *
 * @since 1.7
 */
@Recommended(since = "1.7")
@NameWith(value = StandardCertificateCredentials.NameProvider.class, priority = 8)
public interface StandardCertificateCredentials extends StandardCredentials, CertificateCredentials {
    /**
     * Our name provider.
     *
     * @since 1.7
     */
    class NameProvider extends CredentialsNameProvider<StandardCertificateCredentials> {

        /**
         * Returns the Subject DN of the first key with an x509 certificate in its certificate chain.
         *
         * @param keyStore the keystore.
         * @return the subject DN or {@code null}
         */
        @CheckForNull
        public static String getSubjectDN(@NonNull KeyStore keyStore) {
            keyStore.getClass(); // throw NPE if null
            try {
                for (Enumeration<String> enumeration = keyStore.aliases(); enumeration.hasMoreElements(); ) {
                    String alias = enumeration.nextElement();
                    if (keyStore.isKeyEntry(alias)) {
                        Certificate[] certificateChain = keyStore.getCertificateChain(alias);
                        if (certificateChain != null && certificateChain.length > 0) {
                            Certificate certificate = certificateChain[0];
                            if (certificate instanceof X509Certificate) {
                                X509Certificate x509 = (X509Certificate) certificate;
                                return x509.getSubjectDN().getName();
                            }
                        }
                    }
                }
            } catch (KeyStoreException e) {
                // ignore
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getName(@NonNull StandardCertificateCredentials c) {
            String description = Util.fixEmptyAndTrim(c.getDescription());
            String subjectDN = getSubjectDN(c.getKeyStore());
            return (subjectDN == null ? c.getDescriptor().getDisplayName() : subjectDN)
                    + (description != null ? " (" + description + ")" : "");
        }
    }
}
