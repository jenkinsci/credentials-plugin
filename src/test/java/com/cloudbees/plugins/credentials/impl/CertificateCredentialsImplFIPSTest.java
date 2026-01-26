package com.cloudbees.plugins.credentials.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import hudson.ExtensionList;
import hudson.util.FormValidation;

import com.cloudbees.plugins.credentials.CredentialsScope;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertificateCredentialsImplFIPSTest {

    @RegisterExtension
    private final RealJenkinsExtension extension = new RealJenkinsExtension().withFIPSEnabled().javaOptions("-Xmx512m");

    private String pemCert;
    private String pemKey;
    private static final String VALID_PASSWORD = "passwordFipsCheck";
    private static final String INVALID_PASSWORD = "invalidPasswordFipsCheck";
    private static final String SHORT_PASSWORD = "password";

    @BeforeEach
    void setup() throws IOException {
        pemCert = IOUtils.toString(CertificateCredentialsImplFIPSTest.class.getResource("validCerts.pem"),
                                   StandardCharsets.UTF_8);
        pemKey = IOUtils.toString(CertificateCredentialsImplFIPSTest.class.getResource("validKey.pem"),
                                  StandardCharsets.UTF_8);
    }

    @Test
    void doCheckPasswordTest() throws Throwable {
        extension.then(r -> {
            CertificateCredentialsImpl.DescriptorImpl descriptor = ExtensionList.lookupSingleton(
                    CertificateCredentialsImpl.DescriptorImpl.class);
            FormValidation result = descriptor.doCheckPassword(VALID_PASSWORD);
            assertThat(result.kind, is(FormValidation.Kind.OK));
            result = descriptor.doCheckPassword(SHORT_PASSWORD);
            assertThat(result.kind, is(FormValidation.Kind.ERROR));
            assertThat(result.getMessage(),
                       is(StringEscapeUtils.escapeHtml4(Messages.CertificateCredentialsImpl_ShortPasswordFIPS())));
        });
    }

    @Test
    void invalidPEMKeyStoreAndPasswordTest() throws Throwable {
        CertificateCredentialsImpl.PEMEntryKeyStoreSource storeSource = new CertificateCredentialsImpl.PEMEntryKeyStoreSource(
                pemCert, pemKey);
        extension.then(r -> {
            new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "valid-certificate-and-password-validation",
                                           "Validate the certificate credentials", VALID_PASSWORD, storeSource);
            assertThrows(IllegalArgumentException.class,
                         () -> new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "empty-password-validation",
                                                              "Validate the certificate empty password", "",
                                                              storeSource));
            assertThrows(IllegalArgumentException.class,
                         () -> new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "password-length-validation",
                                                              "Validate the certificate password length",
                                                              SHORT_PASSWORD, storeSource));
            assertThrows(IllegalArgumentException.class,
                         () -> new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "invalid-password-validation",
                                                              "Validate the certificate password", INVALID_PASSWORD,
                                                              storeSource));
            assertThrows(IllegalArgumentException.class,
                         () -> new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "empty-keystore-validation",
                                                              "Validate the invalid certificate keyStore",
                                                              VALID_PASSWORD,
                                                              new CertificateCredentialsImpl.PEMEntryKeyStoreSource(
                                                                      null, null)));
        });
    }
}
