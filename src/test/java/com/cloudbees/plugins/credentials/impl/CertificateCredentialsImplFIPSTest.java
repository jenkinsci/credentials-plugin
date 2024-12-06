package com.cloudbees.plugins.credentials.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.RealJenkinsRule;

import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import com.cloudbees.plugins.credentials.CredentialsScope;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

public class CertificateCredentialsImplFIPSTest {

    @Rule
    public RealJenkinsRule rule = new RealJenkinsRule().javaOptions("-Djenkins.security.FIPS140.COMPLIANCE=true");

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    String pemCert;
    String pemKey;

    @Before
    public void setup() throws IOException {
        pemCert = IOUtils.toString(CertificateCredentialsImplFIPSTest.class.getResource("FIPSCerts.pem"),
                                   StandardCharsets.UTF_8);
        pemKey = IOUtils.toString(CertificateCredentialsImplFIPSTest.class.getResource("FIPSKey.pem"),
                                  StandardCharsets.UTF_8);
    }

    @Test
    public void doCheckPasswordTest() throws Throwable {
        rule.then(r -> {
            CertificateCredentialsImpl.DescriptorImpl descriptor = ExtensionList.lookupSingleton(
                    CertificateCredentialsImpl.DescriptorImpl.class);
            FormValidation result = descriptor.doCheckPassword("passwordFipsCheck");
            assertThat(result.kind, is(FormValidation.Kind.OK));
            result = descriptor.doCheckPassword("foo");
            assertThat(result.kind, is(FormValidation.Kind.ERROR));
            assertThat(result.getMessage(),
                       is(StringEscapeUtils.escapeHtml4(Messages.CertificateCredentialsImpl_ShortPasswordFIPS())));
        });
    }

    @Test
    public void invalidPEMKeyStoreTest() throws Throwable {
        CertificateCredentialsImpl.PEMEntryKeyStoreSource storeSource = new CertificateCredentialsImpl.PEMEntryKeyStoreSource(
                pemCert, pemKey);
        rule.then(r -> {
            new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "certificate-validation",
                                           "Validate the certificate credentials", "passwordFipsCheck", storeSource);

            assertThrows(Descriptor.FormException.class,
                         () -> new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "certificate-validation",
                                                              "Validate the certificate password", "foo", storeSource));
            assertThrows(Descriptor.FormException.class,
                         () -> new CertificateCredentialsImpl(CredentialsScope.GLOBAL, "certificate-validation",
                                                              "Validate the certificate keyStore", "passwordFipsCheck",
                                                              new CertificateCredentialsImpl.PEMEntryKeyStoreSource(
                                                                      null, null)));
        });
    }
}
