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
import com.cloudbees.plugins.credentials.CredentialsSnapshotTaker;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.trilead.ssh2.crypto.Base64;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.remoting.Channel;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.IOUtils;
import hudson.util.Secret;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

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
        keyStoreSource.getClass();
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
     * When serializing over a {@link Channel} ensure that we send a self-contained version.
     *
     * @return the object instance to write to the stream.
     */
    private Object writeReplace() {
        if (/* XStream */Channel.current() == null || /* already safe to serialize */ keyStoreSource
                .isSnapshotSource()) {
            return this;
        }
        return CredentialsProvider.snapshot(this);
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
                keyStore = KeyStore.getInstance("PKCS12");
            } catch (KeyStoreException e) {
                throw new IllegalStateException("PKCS12 is a keystore type per the JLS spec", e);
            }
            try {
                keyStore.load(new ByteArrayInputStream(keyStoreSource.getKeyStoreBytes()), toCharArray(password));
            } catch (CertificateException e) {
                LOGGER.log(Level.WARNING, "Could not load keystore from " + keyStoreSource.toString(), e);
            } catch (NoSuchAlgorithmException e) {
                LOGGER.log(Level.WARNING, "Could not load keystore from " + keyStoreSource.toString(), e);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not load keystore from " + keyStoreSource.toString(), e);
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
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.CertificateCredentialsImpl_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "icon-credentials-certificate";
        }
    }

    /**
     * Represents a source of a {@link KeyStore}.
     */
    public static abstract class KeyStoreSource extends AbstractDescribableImpl<KeyStoreSource> {

        /**
         * Returns the {@link byte[]} content of the {@link KeyStore}.
         *
         * @return the {@link byte[]} content of the {@link KeyStore}.
         */
        @NonNull
        public abstract byte[] getKeyStoreBytes();

        /**
         * Returns a {@link System#currentTimeMillis()} comparable timestamp of when the content was last modified.
         * Used to track refreshing the {@link CertificateCredentialsImpl#keyStore} cache for sources that pull
         * from an external source.
         *
         * @return a {@link System#currentTimeMillis()} comparable timestamp of when the content was last modified.
         */
        public abstract long getKeyStoreLastModified();

        /**
         * Returns {@code true} if and only if the source is self contained.
         *
         * @return {@code true} if and only if the source is self contained.
         * @since 1.14
         */
        public boolean isSnapshotSource() {
            return false;
        }

    }

    /**
     * The base class for all {@link KeyStoreSource} {@link Descriptor} instances.
     */
    public static abstract class KeyStoreSourceDescriptor extends Descriptor<KeyStoreSource> {
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

        /**
         * Helper method that performs form validation on a {@link KeyStore}.
         *
         * @param type          the type of keystore to instantiate, see {@link KeyStore#getInstance(String)}.
         * @param keystoreBytes the {@link byte[]} content of the {@link KeyStore}.
         * @param password      the password to use when loading the {@link KeyStore} and recovering the key from the
         *                      {@link KeyStore}.
         * @return the validation results.
         */
        @NonNull
        protected static FormValidation validateCertificateKeystore(String type, byte[] keystoreBytes,
                                                                    String password) {

            char[] passwordChars = toCharArray(Secret.fromString(password));
            try {
                KeyStore keyStore = KeyStore.getInstance(type);
                keyStore.load(new ByteArrayInputStream(keystoreBytes), passwordChars);
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
            } catch (KeyStoreException e) {
                return FormValidation.warning(e, Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
            } catch (CertificateException e) {
                return FormValidation.warning(e, Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
            } catch (NoSuchAlgorithmException e) {
                return FormValidation.warning(e, Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
            } catch (IOException e) {
                return FormValidation.warning(e, Messages.CertificateCredentialsImpl_LoadKeystoreFailed());
            } finally {
                if (passwordChars != null) {
                    Arrays.fill(passwordChars, ' ');
                }
            }
        }
    }

    /**
     * Let the user reference a file on the disk.
     */
    public static class FileOnMasterKeyStoreSource extends KeyStoreSource {

        /**
         * Our logger.
         */
        private static final Logger LOGGER = Logger.getLogger(FileOnMasterKeyStoreSource.class.getName());

        /**
         * The path of the file on the master.
         */
        private final String keyStoreFile;

        /**
         * Our constructor.
         *
         * @param keyStoreFile the path of the file on the master.
         */
        @SuppressWarnings("unused") // by stapler
        @DataBoundConstructor
        public FileOnMasterKeyStoreSource(String keyStoreFile) {
            this.keyStoreFile = keyStoreFile;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public byte[] getKeyStoreBytes() {
            try {
                InputStream inputStream = new FileInputStream(new File(keyStoreFile));
                try {
                    return IOUtils.toByteArray(inputStream);
                } finally {
                    IOUtils.closeQuietly(inputStream);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not read private key file " + keyStoreFile, e);
                return new byte[0];
            }
        }

        /**
         * Returns the private key file name.
         *
         * @return the private key file name.
         */
        public String getKeyStoreFile() {
            return keyStoreFile;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getKeyStoreLastModified() {
            return new File(keyStoreFile).lastModified();
        }

        /**
         * {@inheritDoc}
         */
        @Extension
        public static class DescriptorImpl extends KeyStoreSourceDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.CertificateCredentialsImpl_FileOnMasterKeyStoreSourceDisplayName();
            }

            /**
             * Checks the keystore file path.
             *
             * @param value    the file path.
             * @param password the password.
             * @return the {@link FormValidation} results.
             */
            @SuppressWarnings("unused") // stapler form validation
            @Restricted(NoExternalUse.class)
            public FormValidation doCheckKeyStoreFile(@QueryParameter String value,
                                                      @QueryParameter String password) {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_KeyStoreFileUnspecified());
                }
                File file = new File(value);
                if (file.isFile()) {
                    try {
                        return validateCertificateKeystore("PKCS12", FileUtils.readFileToByteArray(file), password);
                    } catch (IOException e) {
                        return FormValidation
                                .error(Messages.CertificateCredentialsImpl_KeyStoreFileUnreadable(value), e);
                    }
                } else {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_KeyStoreFileDoesNotExist(value));
                }
            }
        }
    }

    /**
     * Let the user reference a file on the disk.
     */
    public static class UploadedKeyStoreSource extends KeyStoreSource implements Serializable {
        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Our logger.
         */
        private static final Logger LOGGER = Logger.getLogger(FileOnMasterKeyStoreSource.class.getName());

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
            this.uploadedKeystoreBytes = StringUtils.isBlank(uploadedKeystore)
                    ? null
                    : SecretBytes.fromBytes(DescriptorImpl.toByteArray(Secret.fromString(uploadedKeystore)));
        }

        /**
         * Our constructor.
         *
         * @param uploadedKeystore the keystore content.
         */
        @SuppressWarnings("unused") // by stapler
        @DataBoundConstructor
        public UploadedKeyStoreSource(SecretBytes uploadedKeystore) {
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

        /**
         * {@inheritDoc}
         */
        @Extension
        public static class DescriptorImpl extends KeyStoreSourceDescriptor {

            /**
             * Decode the {@link Base64} keystore wrapped in a {@link Secret}.
             *
             * @param secret the keystore as a secret.
             * @return the keystore bytes.
             * @see #toSecret(byte[])
             */
            public static byte[] toByteArray(Secret secret) {
                if (secret != null) {
                    try {
                        return Base64.decode(secret.getPlainText().toCharArray());
                    } catch (IOException e) {
                        // ignore
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
             */
            public static Secret toSecret(byte[] contents) {
                return contents == null || contents.length == 0
                        ? null
                        : Secret.fromString(new String(Base64.encode(contents)));
            }

            /**
             * {@inheritDoc}
             */
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
            public FormValidation doCheckUploadedKeystore(@QueryParameter String value,
                                                          @QueryParameter String password) {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_NoCertificateUploaded());
                }
                return validateCertificateKeystore("PKCS12", toByteArray(Secret.fromString(value)), password);
            }

            /**
             * Creates a new {@link Upload} for the specified {@literal <input id="..."/>}
             *
             * @param divId the id if the form input element to inject the uploaded content into.
             * @return the {@link Upload}
             */
            @SuppressWarnings("unused") // invoked by stapler binding
            @Restricted(NoExternalUse.class)
            public Upload getUpload(String divId) {
                return new Upload(divId, null);
            }

        }

        /**
         * Stapler binding object to handle a pop-up window for file upload.
         */
        public static class Upload {

            /**
             * The id of the {@literal <input>} element on the {@code window.opener} of the pop-up to inject the
             * uploaded content into.
             */
            @NonNull
            private final String divId;

            /**
             * The uploaded content.
             */
            @CheckForNull
            private final Secret uploadedKeystore;

            /**
             * Our constructor.
             *
             * @param divId            id of the {@literal <input>} element on the {@code window.opener} of the
             *                         pop-up to inject the uploaded content into.
             * @param uploadedKeystore the content.
             */
            public Upload(@NonNull String divId, @CheckForNull Secret uploadedKeystore) {
                this.divId = divId;
                this.uploadedKeystore = uploadedKeystore;
            }

            /**
             * Gets the id of the {@literal <input>} element on the {@code window.opener} of the pop-up to inject the
             * uploaded content into.
             *
             * @return the id of the {@literal <input>} element on the {@code window.opener} of the pop-up to inject the
             * uploaded content into.
             */
            @NonNull
            public String getDivId() {
                return divId;
            }

            /**
             * Returns the content.
             *
             * @return the content.
             */
            @SuppressWarnings("unused") // used by Jelly EL
            public Secret getUploadedKeystore() {
                return uploadedKeystore;
            }

            /**
             * Performs the actual upload.
             *
             * @param req the request.
             * @return the response.
             * @throws ServletException if something goes wrong.
             * @throws IOException      if something goes wrong.
             */
            @NonNull
            public HttpResponse doUpload(@NonNull StaplerRequest req) throws ServletException, IOException {
                FileItem file = req.getFileItem("certificate.file");
                if (file == null) {
                    throw new ServletException("no file upload");
                }
                // Here is the trick, if we have a successful upload we replace ourselves in the stapler view
                // with an instance that has the uploaded content and request stapler to render the "complete"
                // view for that instance. The "complete" view can then do the injection and close itself so that
                // the user experience is the pop-up then click upload and finally we inject back in the content to
                // the form.
                return HttpResponses.forwardToView(
                        new Upload(getDivId(), UploadedKeyStoreSource.DescriptorImpl.toSecret(file.get())), "complete");
            }
        }
    }

    /**
     * The {@link CredentialsSnapshotTaker} for {@link StandardCertificateCredentials}.
     *
     * @since 1.14
     */
    @Extension
    public static class CredentialsSnapshotTakerImpl extends CredentialsSnapshotTaker<StandardCertificateCredentials> {

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
                final KeyStoreSource keyStoreSource = ((CertificateCredentialsImpl) credentials).getKeyStoreSource();
                if (keyStoreSource.isSnapshotSource()) {
                    return credentials;
                }
                return new CertificateCredentialsImpl(credentials.getScope(), credentials.getId(),
                        credentials.getDescription(), credentials.getPassword().getEncryptedValue(),
                        new UploadedKeyStoreSource(SecretBytes.fromBytes(keyStoreSource.getKeyStoreBytes())));
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final char[] password = credentials.getPassword().getPlainText().toCharArray();
            try {
                credentials.getKeyStore().store(bos, password);
                bos.close();
            } catch (KeyStoreException e) {
                return credentials;
            } catch (IOException e) {
                return credentials;
            } catch (NoSuchAlgorithmException e) {
                return credentials;
            } catch (CertificateException e) {
                return credentials;
            } finally {
                Arrays.fill(password, (char) 0);
            }
            return new CertificateCredentialsImpl(credentials.getScope(), credentials.getId(),
                    credentials.getDescription(), credentials.getPassword().getEncryptedValue(),
                    new UploadedKeyStoreSource(SecretBytes.fromBytes(bos.toByteArray())));
        }
    }
}
