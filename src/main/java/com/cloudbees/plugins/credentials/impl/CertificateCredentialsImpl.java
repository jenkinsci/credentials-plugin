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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Items;
import hudson.remoting.Channel;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.crypto.interfaces.DHPrivateKey;
import javax.security.auth.DestroyFailedException;
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
import org.kohsuke.stapler.verb.POST;

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
        // ensure the keySore is valid
        // we check here as otherwise it will lead to hard to diagnose errors when used
        try {
            keyStoreSource.toKeyStore(toCharArray(this.password));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("KeyStore is not valid.", e);
        }
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
     * When serializing over a {@link Channel} ensure that we send a self-contained version.
     *
     * @return the object instance to write to the stream.
     */
    private Object writeReplace() {
        if (/* XStream */ Channel.current() == null
        ||  /* already safe to serialize */ keyStoreSource
                .isSnapshotSource()
        ) {
            return this;
        }
        return CredentialsProvider.snapshot(this);
    }

    /**
     * Returns the {@link KeyStore} containing the certificate.
     *
     * @return the {@link KeyStore} containing the certificate.
     */
    @Override
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
                // provide an empty uninitialised KeyStore for consumers
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
    @Override
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

        @Restricted(NoExternalUse.class)
        @POST
        public FormValidation doCheckPassword(@QueryParameter String value) {
            Secret s = Secret.fromString(value);
            String pw = s.getPlainText();
            if (FIPS140.useCompliantAlgorithms() && pw.length() < 14) {
                return FormValidation.error(Messages.CertificateCredentialsImpl_ShortPasswordFIPS());
            }
            if (pw.isEmpty()) {
                return FormValidation.warning(Messages.CertificateCredentialsImpl_NoPassword());
            }
            if (pw.length() < 14) {
                return FormValidation.warning(Messages.CertificateCredentialsImpl_ShortPassword());
            }
            return FormValidation.ok();
        }
    }

    /**
     * Represents a source of a {@link KeyStore}.
     */
    public static abstract class KeyStoreSource extends AbstractDescribableImpl<KeyStoreSource> {

        /**
         * @deprecated code should neither implement nor call this. 
         * This is an internal representation of a KeyStore and use of this internal representation would require knowledge of the keystore type.
         * @see #toKeyStore(char[])
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
        public abstract KeyStore toKeyStore(@Nullable char[] password) throws GeneralSecurityException, IOException;

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

        protected KeyStoreSourceDescriptor() {
            super();
        }

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
         * Still used for snapshot taking, with contents independent of Jenkins instance and JVM.
         */
        @CheckForNull
        @Deprecated
        private Secret uploadedKeystore;
        /**
         * The uploaded keystore.
         *
         * @since 2.1.5
         */
        @CheckForNull
        private SecretBytes uploadedKeystoreBytes;

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
         * Our constructor for serialization (e.g. to remote agents, whose SecretBytes
         * in another JVM use a different static KEY); would re-encode.
         *
         * @param uploadedKeystore the keystore content.
         * @deprecated
         */
        @SuppressWarnings("unused") // by stapler
        @Deprecated
        public UploadedKeyStoreSource(@CheckForNull Secret uploadedKeystore) {
            this.uploadedKeystore = uploadedKeystore;
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
         * Request that if the less-efficient but more-portable Secret
         * is involved (e.g. to cross the remoting gap to another JVM),
         * we use the more secure and efficient SecretBytes.
         */
        public void useSecretBytes() {
            if (this.uploadedKeystore != null && this.uploadedKeystoreBytes == null) {
                this.uploadedKeystoreBytes = SecretBytes.fromBytes(DescriptorImpl.toByteArray(this.uploadedKeystore));
                this.uploadedKeystore = null;
            }
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
         * Returns the private key + certificate file bytes.
         *
         * @return the private key + certificate file bytes.
         */
        public SecretBytes getUploadedKeystore() {
            if (uploadedKeystore != null && uploadedKeystoreBytes == null) {
                return SecretBytes.fromBytes(DescriptorImpl.toByteArray(uploadedKeystore));
            }
            return uploadedKeystoreBytes;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public byte[] getKeyStoreBytes() {
            if (uploadedKeystore != null && uploadedKeystoreBytes == null) {
                return DescriptorImpl.toByteArray(uploadedKeystore);
            }
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
            //return this.snapshotSecretBytes;
            // If context is local, clone SecretBytes directly (only
            // usable in same JVM). Otherwise use Secret for transport
            // (see {@link CertificateCredentialsSnapshotTaker}.
            return (/* XStream */ Channel.current() == null);
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
     * A user entered PEM encoded certificate chain and key.
     */
    public static class PEMEntryKeyStoreSource extends KeyStoreSource implements Serializable {

        private static final long serialVersionUID = 1L;

        /** The chain of certificates encoded as multiple PEM objects*/
        private final Secret certChain;
        /** The PEM encoded (and possibly encrypted) secret key */
        private final Secret privateKey;

        /**
         * Constructor able to receive file directly
         * 
         * @param certChain the PEM encoded certificate chain (possibly encrypted as a secret)
         * @param privateKey the PEM encoded and possibly encrypted key for the certificate (possibly encrypted as a secret)
         */
        @SuppressWarnings("unused") // by stapler
        @DataBoundConstructor
        public PEMEntryKeyStoreSource(String certChain, String privateKey) {
            this.certChain = Secret.fromString(certChain);
            this.privateKey = Secret.fromString(privateKey);
        }

        /**
         * Returns the PEM encoded certificate chain.
         */
        @Restricted(NoExternalUse.class) // for jelly only
        public Secret getCertChain() {
            return certChain;
        }

        /**
         * Returns the PEM encoded private key.
         */
        @Restricted(NoExternalUse.class) // for jelly only
        public Secret getPrivateKey() {
            return privateKey;
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
            return toKeyStore(certChain.getPlainText(), privateKey.getPlainText(), password);
        }

        protected static KeyStore toKeyStore(String pemEncodedCerts, String pemEncodedKey, char[] password) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, KeyStoreException, UnrecoverableKeyException, IOException {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, password); // initialise the keystore

            List<PEMEncodable> pemEncodeableCerts = PEMEncodable.decodeAll(pemEncodedCerts, password);
            List<Certificate> certs = pemEncodeableCerts.stream().map(PEMEncodable::toCertificate).filter(Objects::nonNull).collect(Collectors.toList());

            List<PEMEncodable> pemEncodeableKeys = PEMEncodable.decodeAll(pemEncodedKey, password);
            if (pemEncodeableKeys.size() != 1) {
                throw new IOException("expected one key but got " + pemEncodeableKeys.size());
            }

            PrivateKey privateKey = pemEncodeableKeys.get(0).toPrivateKey();

            keyStore.setKeyEntry("keychain", privateKey, password, certs.toArray(new Certificate[] {}));

            return keyStore;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "PEMEntryKeyStoreSource{pemCertChain=******,pemKey=******}";
        }

        @Extension
        public static class DescriptorImpl extends KeyStoreSourceDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.CertificateCredentialsImpl_PEMEntryKeyStoreSourceDisplayName();
            }

            @Restricted(NoExternalUse.class)
            @POST
            public FormValidation doCheckCertChain(@QueryParameter String value) {
                String pemCerts = Secret.fromString(value).getPlainText();
                try {
                    List<PEMEncodable> pemEncodables = PEMEncodable.decodeAll(pemCerts, null);
                    long count = pemEncodables.stream().map(PEMEncodable::toCertificate).filter(Objects::nonNull).count();
                    if (count < 1) {
                        return FormValidation.error(Messages.CertificateCredentialsImpl_PEMNoCertificates());
                    }
                    // ensure only certs are provided.
                    if (pemEncodables.size() != count) {
                        return FormValidation.error(Messages.CertificateCredentialsImpl_PEMNoCertificates());
                    }
                    Certificate cert = pemEncodables.get(0).toCertificate();
                    if (cert instanceof X509Certificate) {
                        X509Certificate x509 = (X509Certificate) cert;
                        return FormValidation.ok(x509.getSubjectDN().getName());
                    }
                    // no details
                    return FormValidation.ok();
                } catch (UnrecoverableKeyException | IOException e) {
                    String message = e.getMessage();
                    if (message != null) {
                        return FormValidation.error(e, Messages.CertificateCredentialsImpl_PEMCertificateParsingError(message));
                    }
                    return FormValidation.error(e, Messages.CertificateCredentialsImpl_PEMCertificateParsingError("unkown reason"));
                }
            }

            @Restricted(NoExternalUse.class)
            @POST
            public FormValidation doCheckPrivateKey(@QueryParameter String value,
                                                    @RelativePath("..")
                                                    @QueryParameter String password) {
                String key = Secret.fromString(value).getPlainText();
                try {
                    List<PEMEncodable> pemEncodables = PEMEncodable.decodeAll(key, toCharArray(Secret.fromString(password)));
                    long count = pemEncodables.stream().map(PEMEncodable::toPrivateKey).filter(Objects::nonNull).count();
                    if (count == 0) {
                        return FormValidation.error(Messages.CertificateCredentialsImpl_PEMNoKeys());
                    }
                    if (count > 1) {
                        return FormValidation.error(Messages.CertificateCredentialsImpl_PEMMultipleKeys());
                    }
                    // ensure only keys are provided.
                    if (pemEncodables.size() != 1) {
                        return FormValidation.error(Messages.CertificateCredentialsImpl_PEMNonKeys());
                    }
                    PrivateKey pk = pemEncodables.get(0).toPrivateKey();
                    String format;
                    String length;
                    if (pk instanceof RSAPrivateKey) {
                        format = "RSA";
                        length = ((RSAKey)pk).getModulus().bitLength() + " bit";
                    } else if (pk instanceof ECPrivateKey) {
                        format = "elliptic curve (EC)";
                        length =  ((ECPrivateKey)pk).getParams().getOrder().bitLength() + " bit";
                    } else if (pk instanceof DSAPrivateKey) {
                        format = "DSA";
                        length = ((DSAPrivateKey)pk).getParams().getP().bitLength() + " bit";
                    } else if (pk instanceof DHPrivateKey) {
                        format = "Diffie-Hellman";
                        length =  ((DHPrivateKey)pk).getParams().getP().bitLength() + " bit";
                    } else if (pk != null) {
                        // spotbugs things pk may be null, but we have already checked 
                        // the size of pemEncodables is one and contains a private key
                        // so it can not be
                        format = "unknown format (" + pk.getClass() +")";
                        length = "unknown strength";
                    } else { // pk == null can not happen
                        return FormValidation.error("there is a bug in the code, pk is null!");
                    }
                    try {
                        pk.destroy();
                    } catch (@SuppressWarnings("unused") DestroyFailedException ignored) {
                            // best effort
                    }
                    return FormValidation.ok(Messages.CertificateCredentialsImpl_PEMKeyInfo(length, format));
                } catch (UnrecoverableKeyException | IOException e) {
                    return FormValidation.error(e, Messages.CertificateCredentialsImpl_PEMKeyParseError(e.getLocalizedMessage()));
                }
            }

        }
    }

    static {
        // the critical field allow the permission check to make the XML read to fail completely in case of violation
        Items.XSTREAM2.addCriticalField(CertificateCredentialsImpl.class, "keyStoreSource");
    }
}
