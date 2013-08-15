package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.remoting.Channel;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.Secret;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.ByteArrayInputStream;
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

    public KeyStoreSource getKeyStoreSource() {
        return keyStoreSource;
    }

    private static char[] toCharArray(Secret password) {
        String plainText = Util.fixEmpty(password.getPlainText());
        return plainText == null ? null : plainText.toCharArray();
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.CertificateCredentialsImpl_DisplayName();
        }

        public DescriptorExtensionList<KeyStoreSource, Descriptor<KeyStoreSource>> getKeyStoreSources() {
            return Hudson.getInstance().getDescriptorList(KeyStoreSource.class);
        }


        public CertificateCredentialsImpl fixInstance(CertificateCredentialsImpl instance) {
            if (instance == null) {
                return new CertificateCredentialsImpl(CredentialsScope.GLOBAL, null, "", "",
                        new FileOnMasterKeyStoreSource(""));
            } else {
                return instance;
            }
        }


    }

    public static abstract class KeyStoreSource extends AbstractDescribableImpl<KeyStoreSource>
            implements Serializable {

        @NonNull
        public abstract byte[] getKeyStoreBytes();

        public abstract long getKeyStoreLastModified();

    }

    public static abstract class KeyStoreSourceDescriptor extends Descriptor<KeyStoreSource> {
        protected KeyStoreSourceDescriptor() {
            super();
        }

        protected KeyStoreSourceDescriptor(Class<? extends KeyStoreSource> clazz) {
            super(clazz);
        }

        protected static FormValidation validateCertificateKeystore(String type, byte[] keystoreBytes, String password) {

            char[] passwordChars = toCharArray(Secret.fromString(password));
            try {
                KeyStore keyStore = KeyStore.getInstance(type);
                keyStore.load(new ByteArrayInputStream(keystoreBytes), passwordChars);
                int size = keyStore.size();
                if (size == 0) {
                    return FormValidation.warning("Empty keystore");
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
                        try {
                            keyStore.getKey(alias, passwordChars);
                        } catch (UnrecoverableEntryException e) {
                            if (passwordChars == null || passwordChars.length == 0) {
                                return FormValidation.warning(e,
                                        "Could retrieve key '" + alias + "'. You may need to provide a password");
                            }
                            return FormValidation.warning(e,
                                    "Could retrieve key '" + alias + "'");
                        }
                    }
                }
                return FormValidation.ok(StringUtils
                        .defaultIfEmpty(StandardCertificateCredentials.NameProvider.getSubjectDN(keyStore),
                                buf.toString()));
            } catch (KeyStoreException e) {
                return FormValidation.warning(e, "Could not load keystore");
            } catch (CertificateException e) {
                return FormValidation.warning(e, "Could not load keystore");
            } catch (NoSuchAlgorithmException e) {
                return FormValidation.warning(e, "Could not load keystore");
            } catch (IOException e) {
                return FormValidation.warning(e, "Could not load keystore");
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
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;

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
            InputStream inputStream;
            try {
                if (Channel.current() == null) {
                    inputStream = new FileInputStream(new File(keyStoreFile));
                } else {
                    // we are not on the master
                    inputStream = new FilePath(Channel.current(), keyStoreFile).read();
                }
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
            if (Channel.current() == null) {
                return new File(keyStoreFile).lastModified();
            } else {
                // we are not on the master
                try {
                    return new FilePath(Channel.current(), keyStoreFile).lastModified();
                } catch (IOException e) {
                    return 0L;
                } catch (InterruptedException e) {
                    return 0L;
                }
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
                                                      @QueryParameter @RelativePath("..") String password) {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error("You must specify the file path");
                }
                File file = new File(value);
                if (file.isFile()) {
                    try {
                        return validateCertificateKeystore("PKCS12", FileUtils.readFileToByteArray(file), password);
                    } catch (IOException e) {
                        return FormValidation.error("Could not read file '" + value + "'", e);
                    }
                } else {
                    return FormValidation.error("The file '" + value + "' does not exist");
                }
            }

        }
    }

}
