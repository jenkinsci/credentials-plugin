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
package com.cloudbees.plugins.credentials;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class SecretBytesTest {

    @Test
    public void encrypt() {
        SecretBytes secret = SecretBytes.fromBytes("abc".getBytes());
        assertThat(secret.getPlainData(), is("abc".getBytes()));

        //System.out.println(Base64.encode(secret.getEncryptedData()));
        assertThat(secret.getEncryptedData(), not(is("abc".getBytes())));

        assertThat(SecretBytes.fromBytes(secret.getEncryptedData()), is(secret));
    }

    @Test
    public void encryptedValuePattern() {
        Random entropy = new Random();
        for (int i = 1; i < 100; i++) {
            String plaintext = Base64.encodeBase64String(RandomStringUtils.random(entropy.nextInt(i)).getBytes());
            SecretBytes secretBytes = SecretBytes.fromString(plaintext);
            String ciphertext = secretBytes.toString();
            // System.out.printf("%s%n → %s%n → %s%n", plaintext, ciphertext, secretBytes);

            assertThat(SecretBytes.ENCRYPTED_VALUE_PATTERN.matcher(ciphertext).matches(), is(true));
        }
    }

    @Test
    public void decrypt() {
        assertThat(SecretBytes.fromBytes("abc".getBytes()).getPlainData(), is("abc".getBytes()));
    }

    @Test
    public void isSecretBytes() {
        assertThat(SecretBytes.isSecretBytes(SecretBytes.fromBytes("abc".getBytes()).toString()), is(true));
        assertThat(SecretBytes.isSecretBytes(""), is(false));
        assertThat(SecretBytes.isSecretBytes("{}"), is(false));
        assertThat(SecretBytes.isSecretBytes("{ABCDEFG===}"), is(false));
        String text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod"
                + " tempor incididunt ut labore et dolore magna aliqua";
        byte[] data = text.getBytes();
        String valid = SecretBytes.fromBytes(data).toString();
        assertThat(SecretBytes.isSecretBytes(valid), is(true));
        String broken = valid.substring(0, 20) + valid.substring(22);
        assertThat(SecretBytes.isSecretBytes(broken), is(false));
        String invalid =
                "Fu".equals(valid.substring(20,22))
                        ? valid.substring(0, 20) + "fU" + valid.substring(22)
                        : valid.substring(0, 20) + "Fu" + valid.substring(22);
        if (SecretBytes.isSecretBytes(invalid)) {
            assertThat(new String(SecretBytes.fromString(invalid).getPlainData()), not(is(text)));
        }
        assertThat(new String(SecretBytes.fromString(valid).getPlainData()), is(text));
        assertThat(new String(SecretBytes.fromString(broken).getPlainData()), not(is(text)));
    }

    @Test
    public void noAccidentalDecrypt() {
        // if this fails then you have magically picked up the secret key that this was generated from
        // running the test again should pass... but it is highly unlikely that you will ever get the
        // same key as I had wen I generated this value
        String luckyMatch = "{0P+rPrwSBIkHVqPUF8kPXt8QlBJzW7lzysQSL+XBikQ=}";
        assertThat(SecretBytes.fromBytes(luckyMatch.getBytes()).getPlainData(), is(luckyMatch.getBytes()));
    }

    @Test
    public void serialization() {
        SecretBytes s = SecretBytes.fromBytes("Mr.Jenkins".getBytes());
        String xml = Jenkins.XSTREAM.toXML(s);
        assertThat(xml, not(containsString(Base64.encodeBase64String("Mr.Jenkins".getBytes()))));
        Object o = Jenkins.XSTREAM.fromXML(xml);
        assertThat(o, is(s));
    }

    public static class Foo {
        SecretBytes password;
    }

    /**
     * Makes sure the serialization form is backward compatible with String.
     */
    @Test
    public void testCompatibilityFromString() {
        String tagName = Foo.class.getName().replace("$", "_-");
        String xml = String.format("<%s><password>%s</password></%s>", tagName, Base64.encodeBase64String("secret".getBytes()), tagName);
        Foo foo = new Foo();
        Jenkins.XSTREAM.fromXML(xml, foo);
        assertThat(SecretBytes.getPlainData(foo.password), is("secret".getBytes()));
    }

    @Test
    public void largeRawString__noChunking__noUrlSafe() {
        byte[] data = new byte[2048];
        new Random().nextBytes(data);
        assertThat(SecretBytes.fromString(new String(org.apache.commons.codec.binary.Base64.encodeBase64(data, false, false), StandardCharsets.US_ASCII)).getPlainData(), is(data));
    }

    @Test
    public void largeRawString__chunking__noUrlSafe() {
        byte[] data = new byte[2048];
        new Random().nextBytes(data);
        assertThat(SecretBytes.fromString(new String(org.apache.commons.codec.binary.Base64.encodeBase64(data, true, false), StandardCharsets.US_ASCII)).getPlainData(), is(data));
    }

    @Test
    public void largeRawString__noChunking__urlSafe() {
        byte[] data = new byte[2048];
        new Random().nextBytes(data);
        assertThat(SecretBytes.fromString(new String(org.apache.commons.codec.binary.Base64.encodeBase64(data, false, true), StandardCharsets.US_ASCII)).getPlainData(), is(data));
    }

    @Test
    public void largeRawString__chunking__urlSafe() {
        byte[] data = new byte[2048];
        new Random().nextBytes(data);
        assertThat(SecretBytes.fromString(new String(org.apache.commons.codec.binary.Base64.encodeBase64(data, true, true), StandardCharsets.US_ASCII)).getPlainData(), is(data));
    }


}
