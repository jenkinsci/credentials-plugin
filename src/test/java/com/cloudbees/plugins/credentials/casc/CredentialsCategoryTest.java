package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.GlobalCredentialsConfiguration;
import edu.umd.cs.findbugs.annotations.NonNull;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;

import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jenkinsci.Symbol;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class CredentialsCategoryTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("credentials-category.yaml")
    public void globalCredentialsConfigurationCategory() throws Exception {
        assertThat(ExtensionList.lookupSingleton(TestGlobalConfiguration.class).getConfig(), equalTo("hello"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);
        assertThat(out.toString(), containsString("globalCredentialsConfiguration:\n" +
                "  testGlobalConfiguration:\n" +
                "    config: \"hello\""));
    }

    @TestExtension
    @Symbol("testGlobalConfiguration")
    public static class TestGlobalConfiguration extends GlobalConfiguration {

        private String config;

        @NonNull
        @Override
        public GlobalConfigurationCategory getCategory() {
            return GlobalConfigurationCategory.get(GlobalCredentialsConfiguration.Category.class);
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Test Global Configuration";
        }

        public String getConfig() {
            return config;
        }

        public void setConfig(String config) {
            this.config = config;
        }
    }

    @Test
    public void exportUsernamePasswordCredentialsImplConfiguration() throws Exception {
        UsernamePasswordCredentialsImpl usernamePasswordCredentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                                                    "username-pass",
                                                    "Username / Password credential for testing",
                                                    "my-user",
                                                    "wonderfulPassword");

        SystemCredentialsProvider.getInstance().getCredentials().add(usernamePasswordCredentials);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);

        // UsernamePasswordCredentialsImpl should be jcasc exported as usernamePassword
        assertThat(out.toString(), Matchers.containsString("usernamePassword:"));
        assertThat(out.toString(), not(Matchers.containsString("usernamePasswordCredentialsImpl:")));
    }

    @Test
    public void exportCertificateCredentialsImplConfiguration() throws Exception {
        CertificateCredentialsImpl certificateCredentials =
                new CertificateCredentialsImpl(CredentialsScope.GLOBAL,
                                               "credential-certificate",
                                               "Credential with certificate",
                                               "password",
                                               new CertificateCredentialsImpl.UploadedKeyStoreSource(SecretBytes.fromBytes("Testing not real certificate".getBytes())));

        SystemCredentialsProvider.getInstance().getCredentials().add(certificateCredentials);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);

        // CertificateCredentialsImpl should be jcasc exported as certificate
        assertThat(out.toString(), Matchers.containsString("certificate:"));
        assertThat(out.toString(), not(Matchers.containsString("certificateCredentialsImpl:")));
    }
}
