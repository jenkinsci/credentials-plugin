package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsSnapshotTaker;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.trilead.ssh2.crypto.Base64;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.remoting.Channel;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.IOUtils;
import hudson.util.Secret;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

/**
 * @author stephenc
 * @since 09/08/2013 16:39
 */
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

    @GuardedBy("this")
    private transient long keyStoreLastModified;

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

    private Object writeReplace() {
        if (/* XStream */Channel.current() == null || /* already safe to serialize */ keyStoreSource.isSnapshotSource()) {
            return this;
        }
        return CredentialsProvider.snapshot(this);
    }

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

    @NonNull
    public Secret getPassword() {
        return password;
    }

    public boolean isPasswordEmpty() {
        return StringUtils.isEmpty(password.getPlainText());
    }

    public KeyStoreSource getKeyStoreSource() {
        return keyStoreSource;
    }

    private static char[] toCharArray(Secret password) {
        String plainText = Util.fixEmpty(password.getPlainText());
        return plainText == null ? null : plainText.toCharArray();
    }

    @Extension(ordinal = -1)
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.CertificateCredentialsImpl_DisplayName();
        }

        public DescriptorExtensionList<KeyStoreSource, Descriptor<KeyStoreSource>> getKeyStoreSources() {
            return Hudson.getInstance().getDescriptorList(KeyStoreSource.class);
        }

    }

    public static abstract class KeyStoreSource extends AbstractDescribableImpl<KeyStoreSource> {

        @NonNull
        public abstract byte[] getKeyStoreBytes();

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

    public static abstract class KeyStoreSourceDescriptor extends Descriptor<KeyStoreSource> {
        protected KeyStoreSourceDescriptor() {
            super();
        }

        protected KeyStoreSourceDescriptor(Class<? extends KeyStoreSource> clazz) {
            super(clazz);
        }

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
                            return FormValidation.warning(Messages.CertificateCredentialsImpl_LoadKeyFailedQueryEmptyPassword(alias));
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
         * {@inheritDoc}
         */
        @Override
        public long getKeyStoreLastModified() {
            return new File(keyStoreFile).lastModified();
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
        @Extension
        public static class DescriptorImpl extends KeyStoreSourceDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.CertificateCredentialsImpl_FileOnMasterKeyStoreSourceDisplayName();
            }

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
                        return FormValidation.error(Messages.CertificateCredentialsImpl_KeyStoreFileUnreadable(value), e);
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
         * The uploaded keystore.
         */
        @CheckForNull
        private final Secret uploadedKeystore;

        @SuppressWarnings("unused") // by stapler
        @DataBoundConstructor
        public UploadedKeyStoreSource(String uploadedKeystore) {
            this.uploadedKeystore = StringUtils.isBlank(uploadedKeystore) ? null : Secret.fromString(uploadedKeystore);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public byte[] getKeyStoreBytes() {
            return DescriptorImpl.toByteArray(uploadedKeystore);
        }

        @Override
        public long getKeyStoreLastModified() {
            return 0L;
        }

        /**
         * Returns the private key file name.
         *
         * @return the private key file name.
         */
        public String getUploadedKeystore() {
            return uploadedKeystore == null ? "" : uploadedKeystore.getEncryptedValue();
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
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.CertificateCredentialsImpl_UploadedKeyStoreSourceDisplayName();
            }

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

            public static Secret toSecret(byte[] contents) {
                return contents == null || contents.length == 0
                        ? null
                        : Secret.fromString(new String(Base64.encode(contents)));
            }

            public FormValidation doCheckUploadedKeystore(@QueryParameter String value,
                                                          @QueryParameter String password) {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(Messages.CertificateCredentialsImpl_NoCertificateUploaded());
                }
                return validateCertificateKeystore("PKCS12", toByteArray(Secret.fromString(value)), password);
            }

            public Upload getUpload(String divId) {
                return new Upload(divId, null);
            }


        }

        public static class Upload {

            private final String divId;

            private final Secret uploadedKeystore;

            public Upload(String divId, Secret uploadedKeystore) {
                this.divId = divId;
                this.uploadedKeystore = uploadedKeystore;
            }

            public String getDivId() {
                return divId;
            }

            public Secret getUploadedKeystore() {
                return uploadedKeystore;
            }

            public HttpResponse doUpload(StaplerRequest req) throws ServletException, IOException {
                FileItem file = req.getFileItem("certificate.file");
                if (file == null) {
                    throw new ServletException("no file upload");
                }
                return HttpResponses.forwardToView(
                        new Upload(getDivId(), UploadedKeyStoreSource.DescriptorImpl.toSecret(file.get())), "complete");
            }
        }
    }

    /**
     * @since 1.14
     */
    @Extension
    public static class CredentialsSnapshotTakerImpl extends CredentialsSnapshotTaker<StandardCertificateCredentials> {

        @Override
        public Class<StandardCertificateCredentials> type() {
            return StandardCertificateCredentials.class;
        }

        @Override
        public StandardCertificateCredentials snapshot(StandardCertificateCredentials credentials) {
            if (credentials instanceof CertificateCredentialsImpl) {
                final KeyStoreSource keyStoreSource = ((CertificateCredentialsImpl) credentials).getKeyStoreSource();
                if (keyStoreSource.isSnapshotSource()) {
                    return credentials;
                }
                return new CertificateCredentialsImpl(credentials.getScope(), credentials.getId(),
                        credentials.getDescription(), credentials.getPassword().getEncryptedValue(),
                        new UploadedKeyStoreSource(
                                UploadedKeyStoreSource.DescriptorImpl.toSecret(keyStoreSource.getKeyStoreBytes())
                                        .getEncryptedValue()));
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
                    new UploadedKeyStoreSource(
                            UploadedKeyStoreSource.DescriptorImpl.toSecret(bos.toByteArray())
                                    .getEncryptedValue()));
        }
    }
}
