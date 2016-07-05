/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc..
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
/*
Portions Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to
You under the Apache License, Version 2.0.
 */
package com.cloudbees.plugins.credentials;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.util.Secret;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import jcifs.util.Base64;
import jenkins.security.ConfidentialStore;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;

/**
 * An analogue of {@link Secret} to be used for efficient storage of {@link byte[]}. The serialized form will embed the
 * salt and padding so no two invocations of {@link #getEncryptedData()} will return the same result, but all will
 * decrypt to the same {@link #getPlainData()}. XStream serialization and Stapler form-binding will assume that
 * the {@link #toString()} representation is used (i.e. the Base64 encoded secret bytes wrapped with <code>{</code>
 * and <code>}</code>. If the string representation fails to decrypt (and is not wrapped
 *
 * @since 2.1.5
 */
public class SecretBytes implements Serializable {
    /**
     * The chunk size.
     */
    private static final int CHUNK_SIZE = 16;
    /**
     * The salt size.
     */
    private static final int SALT_SIZE = 8;
    /**
     * Standardize serialization.
     */
    private static final long serialVersionUID = 1L;
    /**
     * The key that encrypts the data on disk.
     */
    private static final CredentialsConfidentialKey KEY = new CredentialsConfidentialKey(SecretBytes.class, "KEY");
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SecretBytes.class.getName());
    /**
     * The unencrypted bytes.
     */
    @NonNull
    private final byte[] value;

    /**
     * Internal constructor.
     *
     * @param encrypted {@code} true if the supplied data is already encrypted, {@code false} if the supplied data is plain text.
     * @param value the data to wrap.
     * @see #fromBytes(byte[])
     * @see #fromString(String)
     */
    private SecretBytes(boolean encrypted, @NonNull byte[] value) {
        if (encrypted) {
            this.value = value.clone();
        } else {
            try {
                // copied from https://github
                // .com/codehaus-plexus/plexus-cipher/blob/6ab0e38df80beed9ab3227ffab938b21dcdf5505/src
                // /main/java/org/sonatype/plexus/components/cipher/PBECipher.java
                byte[] salt = ConfidentialStore.get().randomBytes(SALT_SIZE);
                Cipher cipher = KEY.encrypt(salt);
                byte[] encryptedBytes = cipher.doFinal(value);
                int len = encryptedBytes.length;
                byte padLen = (byte) (CHUNK_SIZE
                        - (salt.length + len + 1) % CHUNK_SIZE);
                int totalLen = salt.length + len + padLen + 1;
                byte[] allEncryptedBytes = new byte[totalLen];
                byte[] padBytes = ConfidentialStore.get().randomBytes(padLen);
                System.arraycopy(salt, 0, allEncryptedBytes, 0, salt.length);
                allEncryptedBytes[salt.length] = padLen;
                System.arraycopy(encryptedBytes, 0, allEncryptedBytes, salt.length + 1, len);
                System.arraycopy(padBytes, 0, allEncryptedBytes, salt.length + 1 + len, padLen & 0xff);
                this.value = allEncryptedBytes;
            } catch (GeneralSecurityException e) {
                throw new Error(e); // impossible
            }
        }
    }

    /**
     * Returns the raw unencrypted data. The caller is responsible for zeroing out the returned {@link byte[]} after
     * use.
     *
     * @return the raw unencrypted data.
     */
    @NonNull
    public byte[] getPlainData() {
        try {
            int totalLen = value.length;
            byte[] salt = new byte[SALT_SIZE];
            System.arraycopy(value, 0, salt, 0, salt.length);
            byte padLen = value[salt.length];
            int len = totalLen - salt.length - 1 - (padLen & 0xff);
            byte[] encryptedBytes = new byte[len];
            System.arraycopy(value, salt.length + 1, encryptedBytes, 0, len);
            Cipher cipher = KEY.decrypt(salt);
            return cipher.doFinal(encryptedBytes);
        } catch (GeneralSecurityException e) {
            throw new Error(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SecretBytes that = (SecretBytes) o;

        return Arrays.equals(value, that.value);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    /**
     * Returns the encrypted data.
     *
     * @return the encrypted data.
     */
    @NonNull
    public byte[] getEncryptedData() {
        return value.clone();
    }

    /**
     * Pattern matching a possible output of {@link #toString()}.
     * Basically, any Base64-encoded value.
     * You must then call {@link #decrypt} to eliminate false positives.
     */
    @Restricted(NoExternalUse.class)
    public static final Pattern ENCRYPTED_VALUE_PATTERN = Pattern.compile("\\{[A-Za-z0-9+/]+={0,2}}");

    /**
     * Reverse operation of {@link #getEncryptedData()}. Returns null
     * if the given cipher text was invalid.
     *
     * @param data the bytes to decrypt.
     * @return the secret bytes or {@code null} if the data was not originally encrypted.
     */
    // copied from https://github.com/codehaus-plexus/plexus-cipher/blob/6ab0e38df80beed9ab3227ffab938b21dcdf5505/src
    // /main/java/org/sonatype/plexus/components/cipher/PBECipher.java
    @CheckForNull
    public static SecretBytes decrypt(byte[] data) {
        if (data == null || data.length <= SALT_SIZE + 1) {
            return null;
        }
        try {
            int totalLen = data.length;
            byte[] salt = new byte[SALT_SIZE];
            System.arraycopy(data, 0, salt, 0, salt.length);
            byte padLen = data[salt.length];
            int len = totalLen - salt.length - 1 - (padLen & 0xff);
            if (len < 0) {
                return null;
            }
            byte[] encryptedBytes = new byte[len];
            System.arraycopy(data, salt.length + 1, encryptedBytes, 0, len);
            Cipher cipher = KEY.decrypt(salt);
            cipher.doFinal(encryptedBytes);
            return new SecretBytes(true, data);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    /**
     * Works just like {@link SecretBytes#getPlainData()} but avoids NPE when the secret is null.
     * To be consistent with {@link #fromBytes(byte[])}, this method doesn't distinguish
     * empty password and null password.
     *
     * @param s the secret bytes.
     * @return the decrypted bytes.
     */
    @NonNull
    public static byte[] getPlainData(@CheckForNull SecretBytes s) {
        return s == null ? new byte[0] : s.getPlainData();
    }

    /**
     * Attempts to treat the given bytes first as a cipher encrypted bytes, and if it doesn't work,
     * treat the given bytes as the unencrypted secret value.
     *
     * <p>
     * Useful for recovering a value from a form field.
     * If the supplied bytes are known to be unencrypted then the caller is responsible for zeroing out the supplied
     * {@link byte[]} afterwards.
     *
     * @param data the data to wrap or decrypt.
     * @return never null
     */
    public static SecretBytes fromBytes(byte[] data) {
        data = data == null ? new byte[0] : data;
        SecretBytes s = decrypt(data);
        if (s == null) {
            s = new SecretBytes(false, data);
        }
        return s;
    }

    /**
     * Attempts to treat the given bytes first as a cipher text, and if it doesn't work,
     * treat the given string as the unencrypted BASE-64 encoded byte array.
     *
     * <p>
     * Useful for recovering a value from a form field.
     *
     * Note: the caller is responsible for evicting the data from memory in the event that the data is
     * the unencrypted BASE-64 encoded plain data.
     *
     * @param data the string representation to decrypt.
     * @return never null
     */
    @NonNull
    public static SecretBytes fromString(String data) {
        data = Util.fixNull(data);
        SecretBytes s;
        try {
            int len = data.length();
            if (len >= 2 && ENCRYPTED_VALUE_PATTERN.matcher(data).matches()) {
                byte[] decoded = Base64.decode(data.substring(1, len - 1));
                s = decrypt(decoded);
                if (s != null) {
                    return s;
                }
            }
            s = new SecretBytes(false, Base64.decode(data));
        } catch (StringIndexOutOfBoundsException e) {
            // wasn't valid Base64
            s = new SecretBytes(false, new byte[0]);
        }
        return s;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "{" + Base64.encode(getEncryptedData()) + "}";
    }

    /**
     * Works just like {@link SecretBytes#toString()} but avoids NPE when the secret is null.
     * To be consistent with {@link #fromString(String)}, this method doesn't distinguish
     * empty password and null password.
     *
     * @param s the secret bytes.
     * @return the string representation.
     */
    public static String toString(SecretBytes s) {
        return s == null ? "" : s.toString();
    }

    /**
     * Our XStream converter.
     */
    public static final class ConverterImpl implements Converter {
        /**
         * {@inheritDoc}
         */
        public boolean canConvert(Class type) {
            return type == SecretBytes.class;
        }

        /**
         * {@inheritDoc}
         */
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            SecretBytes src = (SecretBytes) source;
            writer.setValue(SecretBytes.toString(src));
        }

        /**
         * {@inheritDoc}
         */
        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
            return fromString(reader.getValue());
        }
    }

    /*
     * Register a converter for Stapler form binding.
     */
    static {
        Stapler.CONVERT_UTILS.register(new org.apache.commons.beanutils.Converter() {
            /**
             * {@inheritDoc}
             */
            public SecretBytes convert(Class type, Object value) {
                return SecretBytes.fromString(value.toString());
            }
        }, SecretBytes.class);
    }
}
