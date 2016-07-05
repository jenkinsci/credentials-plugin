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
 * createCipher routine was adopted from http://juliusdavies.ca/svn/not-yet-commons-ssl/tags/commons-ssl-0.3
 * .10/src/java/org/apache/commons/ssl/OpenSSL.java
 * which is distributed under APL-2.0 license: http://www.apache.org/licenses/LICENSE-2.0
 */
/*
Portions Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to
You under the Apache License, Version 2.0.
 */
package com.cloudbees.plugins.credentials;

import hudson.util.Secret;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jenkins.security.ConfidentialKey;
import jenkins.security.ConfidentialStore;

/**
 * {@link ConfidentialKey} that stores a {@link SecretKey} for shared-secret cryptography (AES).
 *
 * @since 2.1.5
 */
public class CredentialsConfidentialKey extends ConfidentialKey {
    /**
     * The secret key.
     */
    private volatile SecretKey secret;

    /**
     * The spice size used to seed the IV.
     */
    private static final int SPICE_SIZE = 16;

    /**
     * The digest algorithm to use.
     */
    private static final String DIGEST_ALG = "SHA-256";

    /**
     * The key algorithm to use.
     */
    private static final String KEY_ALG = "AES";

    /**
     * The Cipher algorithm to use.
     */
    private static final String CIPHER_ALG = "AES/CBC/PKCS5Padding";

    /**
     * {@inheritDoc}
     */
    public CredentialsConfidentialKey(String id) {
        super(id);
    }

    /**
     * Constructor.
     *
     * @param owner     the owning class name.
     * @param shortName the name.
     */
    public CredentialsConfidentialKey(Class owner, String shortName) {
        this(owner.getName() + '.' + shortName);
    }

    /**
     * Gets the key used for encryption.
     *
     * @return the key used for encryption.
     */
    private SecretKey getKey() {
        try {
            if (secret == null) {
                synchronized (this) {
                    if (secret == null) {
                        byte[] payload = load();
                        if (payload == null) {
                            payload = ConfidentialStore.get().randomBytes(256);
                            store(payload);
                        }
                        // Due to the stupid US export restriction JDK only ships 128bit version.
                        secret = new SecretKeySpec(payload, 0, 128 / 8, KEY_ALG);
                    }
                }
            }
            return secret;
        } catch (IOException e) {
            throw new Error("Failed to load the key: " + getId(), e);
        }
    }

    /**
     * Returns a {@link Cipher} object for encrypting with this key.
     *
     * @param salt the salt to use for the {@link Cipher}
     * @return the {@link Cipher}
     */
    public Cipher encrypt(byte[] salt) {
        try {
            return createCipher(getKey().getEncoded(), salt, Cipher.ENCRYPT_MODE);
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a {@link Cipher} object for decrypting with this key.
     *
     * @param salt the salt to use for the {@link Cipher}
     * @return the {@link Cipher}
     */
    public Cipher decrypt(byte[] salt) {
        try {
            return createCipher(getKey().getEncoded(), salt, Cipher.DECRYPT_MODE);
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    // copied from https://github.com/codehaus-plexus/plexus-cipher/blob/6ab0e38df80beed9ab3227ffab938b21dcdf5505/src
    // /main/java/org/sonatype/plexus/components/cipher/PBECipher.java
    private Cipher createCipher(final byte[] pwdAsBytes, byte[] salt, final int mode)
            throws GeneralSecurityException {
        MessageDigest _digester = MessageDigest.getInstance(DIGEST_ALG);
        _digester.reset();

        byte[] keyAndIv = new byte[SPICE_SIZE * 2];

        if (salt == null || salt.length == 0) {
            // Unsalted!  Bad idea!
            salt = null;
        }

        byte[] result;

        int currentPos = 0;

        while (currentPos < keyAndIv.length) {
            _digester.update(pwdAsBytes);

            if (salt != null) {
                // First 8 bytes of salt ONLY!  That wasn't obvious to me
                // when using AES encrypted private keys in "Traditional
                // SSLeay Format".
                //
                // Example:
                // DEK-Info: AES-128-CBC,8DA91D5A71988E3D4431D9C2C009F249
                //
                // Only the first 8 bytes are salt, but the whole thing is
                // re-used again later as the IV.  MUCH gnashing of teeth!
                _digester.update(salt, 0, 8);
            }
            result = _digester.digest();

            int stillNeed = keyAndIv.length - currentPos;

            // Digest gave us more than we need.  Let's truncate it.
            if (result.length > stillNeed) {
                byte[] b = new byte[stillNeed];

                System.arraycopy(result, 0, b, 0, b.length);

                result = b;
            }

            System.arraycopy(result, 0, keyAndIv, currentPos, result.length);

            currentPos += result.length;

            if (currentPos < keyAndIv.length) {
                // Next round starts with a hash of the hash.
                _digester.reset();
                _digester.update(result);
            }
        }

        byte[] key = new byte[SPICE_SIZE];

        byte[] iv = new byte[SPICE_SIZE];

        System.arraycopy(keyAndIv, 0, key, 0, key.length);

        System.arraycopy(keyAndIv, key.length, iv, 0, iv.length);

        Cipher cipher = Secret.getCipher(CIPHER_ALG);

        cipher.init(mode, new SecretKeySpec(key, KEY_ALG), new IvParameterSpec(iv));

        return cipher;
    }
}
