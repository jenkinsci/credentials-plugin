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

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.PluginManager;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Items;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jenkins.bouncycastle.api.PEMEncodable;
import jenkins.model.Jenkins;
import jenkins.security.FIPS140;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class CertificateCredentialsImpl extends BaseStandardCredentials implements StandardCertificateCredentials {

    /**
     * Ensure consistent serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CertificateCredentialsImpl.class.getName());

    /**
     * The source of the keystore.
     */
    private final KeyStoreSource keyStoreSource;

    /**
     * The password.
     */
    private final Secret password;

    /**
     * The keystore.
     */
    @GuardedBy("this")
    @CheckForNull
    private transient KeyStore keyStore;

    /**
     * Timestamp of the last time the keystore was modified so that we can track if need to refresh {@link #keyStore}.
     */
    @GuardedBy("this")
    private transient long keyStoreLastModified;

    /**
     * Our constructor.
     *
     * @param scope          the scope.
     * @param id             the id.
     * @param description    the description.
     * @param password       the password.
     * @param keyStoreSource the source of the keystore that holds the certificate.
     */
    @DataBoundConstructor
    public CertificateCredentialsImpl(@CheckForNull CredentialsScope scope,
                                      @CheckForNull String id, @CheckForNull String description,
                                      @CheckForNull String password,
                                      @NonNull KeyStoreSource keyStoreSource) {
        super(scope, id, description);
        Objects.requireNonNull(keyStoreSource);
        this.password = Secret.fromString(password);
        this.keyStoreSource = keyStoreSource;
    }

    /**
     * Helper to convert a {@link Secret} password into a {@code char[]}
     *
     * @param password the password.
     * @return a {@code char[]} containing the password or {@code null}
     */
    @CheckForNull
    private static char[] toCharArray(@NonNull Secret password) {
        String plainText = Util.fixEmpty(password.getPlainText());
        return plainText == null ? null : plainText.toCharArray();
    }

    /**
     * Returns the {@link KeyStore} containing the certificate.
     *
     * @return the {@link KeyStore} containing the certificate.
     */
    @NonNull
    public synchronized KeyStore getKeyStore() {
        long lastModified = keyStoreSource.getKeyStoreLastModified();
        if (keyStore == null || keyStoreLastModified < lastModified) {
            KeyStore keyStore;
            try {
                keyStore = keyStoreSource.toKeyStore(toCharArray(password));
            } catch (GeneralSecurityException | IOException e) {
                LogRecord lr = new LogRecord(Level.WARNING, "Credentials ID {0}: Could not load keystore from {1}");
                lr.setParameters(new Object[]{getId(), keyStoreSource});
                lr.setThrown(e);
                LOGGER.log(lr);
                // provide an empty KeyStore for consumers
                try {
                    keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                } catch (KeyStoreException e2) {
                    throw new IllegalStateException("JVM can not create a KeyStore of the JVM Default Type ("+ KeyStore.getDefaultType() +")", e2);
                }
            }
            this.keyStore = keyStore;
            this.keyStoreLastModified = lastModified;
        }
        return keyStore;
    }

    /**
     * Returns the password used to protect the certificate's private key in {@link #getKeyStore()}.
     *
     * @return the password used to protect the certificate's private key in {@link #getKeyStore()}.
     */
    @NonNull
    public Secret getPassword() {
        return password;
    }

    /**
     * Whether there is actually a password protecting the certificate's private key in {@link #getKeyStore()}.
     *
     * @return {@code true} if there is a password protecting the certificate's private key in {@link #getKeyStore()}.
     */
    public boolean isPasswordEmpty() {
        return StringUtils.isEmpty(password.getPlainText());
    }

    /**
     * Returns the source of the {@link #getKeyStore()}.
     *
     * @return the source of the {@link #getKeyStore()}.
     */
    public KeyStoreSource getKeyStoreSource() {
        return keyStoreSource;
    }

    /**
     * Our descriptor.
     */
    @Extension(ordinal = -1)
    @Symbol("certificate")
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.CertificateCredentialsImpl_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "icon-application-certificate";
        }
    }

    /**
     * Represents a source of a {@link KeyStore}.
     */
    public static abstract class KeyStoreSource extends AbstractDescribableImpl<KeyStoreSource> {

        /**
         * @deprecated code should neither implement nor call this. 
         * This is an internal representation of a KeyStore and use of this internal representation would require knowledge of the keystore type.
         * @throws IllegalStateException always
         */
        @NonNull
        @Deprecated(forRemoval = true)
        public byte[] getKeyStoreBytes() {
            throw new IllegalStateException("Callers should use toKeyStore");
        }

        /**
         * Returns a {@link System#currentTimeMillis()} comparable timestamp of when the content was last modified.
         * Used to track refreshing the {@link CertificateCredentialsImpl#keyStore} cache for sources that pull
         * from an external source.
         *
         * @return a {@link System#currentTimeMillis()} comparable timestamp of when the content was last modified.
         */
        public abstract long getKeyStoreLastModified();

        /**
         * Returns an in memory {@link KeyStore} created from the source.
         *
         * @return The KeyStore content of the {@link KeyStore}.
         * @throws GeneralSecurityException if there was an issue creating the KeyStore
         * @throws IOException if there was an IOException whilst creating the KeyStore
         */
        @NonNull
        public abstract KeyStore toKeyStore(char[] password) throws GeneralSecurityException, IOException;

        /**
         * Returns {@code true} if and only if the source is self contained.
         *
         * @return {@code true} if and only if the source is self contained.
         * @since 1.14
         * @deprecated No longer need to distinguish snapshot sources.
         */
        @Deprecated
        public boolean isSnapshotSource() {
            return false;
        }

    }

    /**
     * The base class for all {@link KeyStoreSource} {@link Descriptor} instances.
     */
    public static abstract class KeyStoreSourceDescriptor extends Descriptor<KeyStoreSource> {
        protected static FormValidation validateCertificateKeystore(KeyStore keyStore, char[] passwordChars)
                throws KeyStoreException, NoSuchAlgorithmException {
                    int size = keyStore.size();
                    if (size == 0) {
                        return FormValidation.warning(Messages.CertificateCredentialsImpl_EmptyKeystore());
                    }
                    StringBuilder buf = new StringBuilder();
                    boolean first = true;
                    for (Enumeration<String> enumeration = keyStore.aliases(); enumeration.hasMoreElements(); ) {
                        String alias = enumeration.nextElement();
                        if (first) {
                            first = false;
                        } else {
                            buf.append(", ");
                        }
                        buf.append(alias);
                        if (keyStore.isCertificateEntry(alias)) {
                            keyStore.getCertificate(alias);
                        } else if (keyStore.isKeyEntry(alias)) {
                            if (passwordChars == null) {
                                return FormValidation.warning(
                                        Messages.CertificateCredentialsImpl_LoadKeyFailedQueryEmptyPassword(alias));
                            }
                            try {
                                keyStore.getKey(alias, passwordChars);
                            } catch (UnrecoverableEntryException e) {
                                return FormValidation.warning(e,
                                        Messages.CertificateCredentialsImpl_LoadKeyFailed(alias));
                            }
                        }
                    }
                    return FormValidation.ok(StringUtils
                            .defaultIfEmpty(StandardCertificateCredentials.NameProvider.getSubjectDN(keyStore),
                                    buf.toString()));
                }

        /**
         * {@inheritDoc}
         */
        protected KeyStoreSourceDescriptor() {
            super();
        }

        /**
         * {@inheritDoc}
         */
        protected KeyStoreSourceDescriptor(Class<? extends KeyStoreSource> clazz) {
            super(clazz);
        }

    }

    /**
     * Let the user reference an uploaded PKCS12 file.
     */
    public static class UploadedKeyStoreSource extends KeyStoreSource implements Serializable {
        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * The old uploaded keystore.
         */
        @CheckForNull
        @Deprecated
        private transient Secret uploadedKeystore;
        /**
         * The uploaded keystore.
         *
         * @since 2.1.5
         */
        @CheckForNull
        private final SecretBytes uploadedKeystoreBytes;

        /**
         * Our constructor.
         *
         * @param uploadedKeystore the keystore content.
         * @deprecated
         */
        @SuppressWarnings("unused") // by stapler
        @Deprecated
        public UploadedKeyStoreSource(String uploadedKeystore) {
            ensureNotRunningInFIPSMode();
            this.uploadedKeystoreBytes = StringUtils.isBlank(uploadedKeystore)
                    ? null
                    : SecretBytes.fromBytes(DescriptorImpl.toByteArray(Secret.fromString(uploadedKeystore)));
        }

        /**
         * Our constructor.
         *
         * @param uploadedKeystore the keystore content.
         * @deprecated
         */
        @SuppressWarnings("unused") // by stapler
        @Deprecated
        public UploadedKeyStoreSource(@CheckForNull SecretBytes uploadedKeystore) {
            ensureNotRunningInFIPSMode();
            this.uploadedKeystoreBytes = uploadedKeystore;
        }

        /**
         * Constructor able to receive file directly
         * 
         * @param uploadedCertFile the keystore content from the file upload
         * @param uploadedKeystore the keystore encrypted data, in case the file is not uploaded (e.g. update of the password / description)
         */
        @SuppressWarnings("unused") // by stapler
        @DataBoundConstructor
        public UploadedKeyStoreSource(FileItem uploadedCertFile, @CheckForNull SecretBytes uploadedKeystore) {
            ensureNotRunningInFIPSMode();
            if (uploadedCertFile != null) {
                byte[] fileBytes = uploadedCertFile.get();
                if (fileBytes.length != 0) {
                    uploadedKeystore = SecretBytes.fromBytes(fileBytes);
                }
            }
            this.uploadedKeystoreBytes = uploadedKeystore;
        }

        /**
         * Migrate to the new field.
         *
         * @return the deserialized object.
         * @throws ObjectStreamException if something didn't work.
         * @since 2.1.5
         */
        private Object readResolve() throws ObjectStreamException {
            ensureNotRunningInFIPSMode();
            if (uploadedKeystore != null && uploadedKeystoreBytes == null) {
                return new UploadedKeyStoreSource(SecretBytes.fromBytes(DescriptorImpl.toByteArray(uploadedKeystore)));
            }
            return this;
        }

        /**
         * Returns the private key file name.
         *
         * @return the private key file name.
         */
        public SecretBytes getUploadedKeystore() {
            return uploadedKeystoreBytes;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public byte[] getKeyStoreBytes() {
            return SecretBytes.getPlainData(uploadedKeystoreBytes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getKeyStoreLastModified() {
            return 0L; // our content is final so it will never change
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSnapshotSource() {
            return true;
        }

        @Override
        public KeyStore toKeyStore(char[] password) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyStoreException, IOException {
            if (FIPS140.useCompliantAlgorithms()) {
                Class<? extends KeyStoreSource> self = this.getClass();
                String className = self.getName();
                String pluginName = Jenkins.get().getPluginManager().whichPlugin(self).getShortName();
                throw new IllegalStateException(className + " is not FIPS compliant and can not be used when Jenkins is in FIPS mode. " +
                                                "An issue should be filed against the plugin " + pluginName + " to ensure it is adapted to be able to work in this mode");
            }
            // legacy behaviour that assumed all KeyStoreSources where in the non compliant PKCS12 format
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(getKeyStoreBytes()), password);
            return keyStore;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "UploadedKeyStoreSource{uploadedKeystoreBytes=******}";
        }

        /*
         * Prevents the use of any direct usage of the class when running in FIPS mode as PKCS12 is not compliant.
         */
        private static void ensureNotRunningInFIPSMode() {
            if (FIPS140.useCompliantAlgorithms()) {
                throw new IllegalStateException("UploadedKeyStoreSource is not compliant with FIPS-140 and can not be used when Jenkins is in FIPS mode. " +
                                                "This is an error in the calling code and an issue should be filed against the plugin that is calling to adapt to become FIPS compliant.");
            }
        }

        /**
         * {@inheritDoc}
         */
        public static class DescriptorImpl extends KeyStoreSourceDescriptor {
            public static final String DEFAULT_VALUE = UploadedKeyStoreSource.class.getName() + ".default-value";

            /**
             * Creates the extension if we are not in FIPS mode, do <em>NOT</em> call this directly!
             */
            @Restricted(NoExternalUse.class)
            @Extension
            public static KeyStoreSourceDescriptor extension() {
                return FIPS140.useCompliantAlgorithms() ? null : new DescriptorImpl();
            }

            /**
             * Decode the {@link Base64} keystore wrapped in a {@link Secret}.
             *
             * @param secret the keystore as a secret.
             * @return the keystore bytes.
             * @see #toSecret(byte[])
             */
            @NonNull
            public static byte[] toByteArray(@Nullable Secret secret) {
                if (secret != null) {
                    byte[] decoded = Base64.getDecoder().decode(secret.getPlainText());
                    if (null != decoded) {
                        return decoded;
                    }
                }
                return new byte[0];
            }

            /**
             * Encodes the keystore bytes into {@link Base64} and wraps in a {@link Secret}
             *
             * @param contents the keystore bytes.
             * @return the keystore as a secret.
             * @see #toByteArray(Secret)
             * @deprecated use {@link SecretBytes#fromBytes(byte[])}
             */
            @Deprecated
            @CheckForNull
            public static Secret toSecret(@Nullable byte[] contents) {
                return contents == null || contents.length == 0
                        ? null
                        : Secret.fromString(Base64.getEncoder().encodeToString(contents));
            }

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.CertificateCredentialsImpl_UploadedKeyStoreSourceDisplayName();
            }

            /**
             * Checks the keystore content.
             *
             * @param value    the keystore content.
             * @param password the password.
             * @return the {@link FormValidation} results.
             */
            @SuppressWarnings("unused") // stapler form validation
            @Restricted(NoExternalUse.class)
            @RequirePOST
            public FormValidation doCheckUploadedKeystore(@QueryParameter String value,
                                                          @QueryParameter String uploadedCertFile,
                                                          @QueryParameter String password) {
                // Priority for the file, to cover the (re-)upload cases
                if (StringUtils.isNotEmpty(uploadedCertFile)) {
                    byte[] uploadedCertFileBytes = Base64.getDecoder().decode(uploadedCertFile.getBytes(StandardCharsets.UTF_8));
                    return validateCertificateKeystore(uploadedCertFileBytes, password);
                }

                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_NoCertificateUploaded());
                }
                if (DEFAULT_VALUE.equals(value)) {
                    return FormValidation.ok();
                }

                // If no file, we rely on the previous value, stored as SecretBytes in an hidden input
                SecretBytes secretBytes = SecretBytes.fromString(value);
                byte[] keystoreBytes = secretBytes.getPlainData();
                if (keystoreBytes == null || keystoreBytes.length == 0) {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
                }
                return validateCertificateKeystore(keystoreBytes, password);
            }

            /**
             * Helper method that performs form validation on a {@link KeyStore}.
             *
             * @param type          the type of keystore to instantiate, see {@link KeyStore#getInstance(String)}.
             * @param keystoreBytes the {@code byte[]} content of the {@link KeyStore}.
             * @param password      the password to use when loading the {@link KeyStore} and recovering the key from the
             *                      {@link KeyStore}.
             * @return the validation results.
             */
            @NonNull
            protected static FormValidation validateCertificateKeystore(byte[] keystoreBytes,
                                                                        String password) {

                ensureNotRunningInFIPSMode();
                if (keystoreBytes == null || keystoreBytes.length == 0) {
                    return FormValidation.warning(Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
                }

                char[] passwordChars = toCharArray(Secret.fromString(password));
                try {
                    KeyStore keyStore = KeyStore.getInstance("PKCS12");
                    keyStore.load(new ByteArrayInputStream(keystoreBytes), passwordChars);
                    return validateCertificateKeystore(keyStore, passwordChars);
                } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                    return FormValidation.warning(e, Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
                } finally {
                    if (passwordChars != null) {
                        Arrays.fill(passwordChars, ' ');
                    }
                }
            }

        }
    }

    /**
     * A user uploaded file containing a set of PEM encoded certificates and a key.
     */
    public static class PEMUploadedKeyStoreSource extends KeyStoreSource implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * The uploaded PEM certs and key.
         */
        private final SecretBytes pemBytes;

        /**
         * Constructor able to receive file directly
         * 
         * @param uploadedCertFile the file containing PEM encoded certs and key.
         * @param uploadedKeystore the PEM data, in case the file is not uploaded (e.g. update of the password / description)
         */
        @SuppressWarnings("unused") // by stapler
        @DataBoundConstructor
        public PEMUploadedKeyStoreSource(FileItem uploadedPemFile, @CheckForNull SecretBytes pemBytes) {
            if (uploadedPemFile != null) {
                byte[] fileBytes = uploadedPemFile.get();
                if (fileBytes.length != 0) {
                    pemBytes = SecretBytes.fromBytes(fileBytes);
                }
            }
            this.pemBytes = pemBytes;
        }

        /**
         * Returns the private key file name.
         *
         * @return the private key file name.
         */
        public SecretBytes getPemBytes() {
            return pemBytes;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getKeyStoreLastModified() {
            return 0L; // our content is final so it will never change
        }
    
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSnapshotSource() {
            return true;
        }
    
        @Override
        public KeyStore toKeyStore(char[] password) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyStoreException, UnrecoverableKeyException, IOException {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            // PEM (rfc7468) only defined the textual encoding
            // the data is always encapsulated in base64
            // the pre-emble defined to be labelchar ( %x21-2C / %x2E-7E any printable character; except hyphen-minus)
            // As far as text is concerned this is all just ascii, however this is the text representation not what may be stored on disk
            // mostly but not always this would only affect us if we where dealing with some esoteric single byte encoding or multibyte encoding
            // for most purposes this is just going to be ASCII but lets assume UTF-8 (to match the bouncycastle plugin read methods)
            String pem = new String(pemBytes.getPlainData(), StandardCharsets.UTF_8);

            return toKeyStore(pem, password);
        }

        protected static KeyStore toKeyStore(String pem, char[] password) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyStoreException, UnrecoverableKeyException, IOException {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            List<PEMEncodable> pemEncodeables = PEMEncodable.decodeAll(pem, password);

            // add the certs first
            int i = 0;
            for (PEMEncodable pe : pemEncodeables) {
                Certificate cert = pe.toCertificate();
                if (cert != null) {
                    keyStore.setCertificateEntry("cert-"+ i++, cert);
                }
            }
            // then the private keys so we already have the cert entries
            i = 0;
            for (PEMEncodable pe : pemEncodeables) {
                PrivateKey pk = pe.toPrivateKey();
                if (pk != null) {
                    keyStore.setKeyEntry("key-" + i++, pk, password, null);
                }
            }
            // XXX if something else (like a public key) was provided we should error...
            return keyStore;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "PEMUploadedKeyStoreSource{pemBytes=******}";
        }

        @Extension
        public static class DescriptorImpl extends KeyStoreSourceDescriptor {

            public static final String DEFAULT_VALUE = UploadedKeyStoreSource.class.getName() + ".default-value";

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.CertificateCredentialsImpl_PEMUploadedKeyStoreSourceDisplayName();
            }

            /**
             * Checks the keystore content.
             *
             * @param value    the keystore content.
             * @param password the password.
             * @return the {@link FormValidation} results.
             */
            @SuppressWarnings("unused") // stapler form validation
            @Restricted(NoExternalUse.class)
            @RequirePOST
            public FormValidation doCheckUploadedPemFile(@QueryParameter String value,
                                                         @QueryParameter String uploadedPemFile,
                                                         @QueryParameter String password) {
                // Priority for the file, to cover the (re-)upload cases
                if (StringUtils.isNotEmpty(uploadedPemFile)) {
                    byte[] uploadedCertFileBytes = Base64.getDecoder().decode(uploadedPemFile.getBytes(StandardCharsets.UTF_8));
                    return validateCertificateKeystore(uploadedCertFileBytes, password);
                }

                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_NoCertificateUploaded());
                }
                if (DEFAULT_VALUE.equals(value)) {
                    return FormValidation.ok();
                }

                // If no file, we rely on the previous value, stored as SecretBytes in an hidden input
                SecretBytes secretBytes = SecretBytes.fromString(value);
                byte[] keystoreBytes = secretBytes.getPlainData();
                if (keystoreBytes == null || keystoreBytes.length == 0) {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
                }
                return validateCertificateKeystore(keystoreBytes, password);
            }

            private FormValidation validateCertificateKeystore(byte[] keystoreBytes, String password) {
                char[] passwordChars = toCharArray(Secret.fromString(password));
                String pem = new String(keystoreBytes, StandardCharsets.UTF_8);
                try {
                    KeyStore ks = PEMUploadedKeyStoreSource.toKeyStore(pem, passwordChars);
                    return validateCertificateKeystore(ks, passwordChars);
                } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | IOException e) {
                    return FormValidation.warning(e, Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
                } finally {
                    if (passwordChars != null) {
                        Arrays.fill(passwordChars, ' ');
                    }
                }
            }
        }
    }

    static {
        // the critical field allow the permission check to make the XML read to fail completely in case of violation
        Items.XSTREAM2.addCriticalField(CertificateCredentialsImpl.class, "keyStoreSource");
    }
}
