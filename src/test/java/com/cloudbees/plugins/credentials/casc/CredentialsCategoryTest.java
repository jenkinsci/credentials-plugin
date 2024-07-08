package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.GlobalCredentialsConfiguration;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import io.jenkins.plugins.casc.model.CNode;
import java.io.ByteArrayOutputStream;

import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import org.jenkinsci.Symbol;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

public class CredentialsCategoryTest {

    @ClassRule
    @ConfiguredWithCode("credentials-category.yaml")
    public static JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    public void importConfig() {
        TestGlobalConfiguration testGlobalConfiguration = ExtensionList.lookupSingleton(TestGlobalConfiguration.class);
        assertThat(testGlobalConfiguration.getConfig(), equalTo("hello"));
    }

    @Test
    public void export() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = ConfigurationAsCodeCategoryRoot.getConfiguration(context).get("test");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "credentials-category-export.yaml");

        assertThat(exported, is(expected));
    }

    @TestExtension
    @Symbol("test")
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
        assertThat(out.toString(), containsString("usernamePassword:"));
        assertThat(out.toString(), not(containsString("usernamePasswordCredentialsImpl:")));
    }

    @Test
    public void exportCertificateCredentialsImplConfiguration() throws Exception {
        byte[] p12Bytes = CertificateCredentialsImpl.class.getResourceAsStream("test.p12").readAllBytes();
        CertificateCredentialsImpl certificateCredentials =
                new CertificateCredentialsImpl(CredentialsScope.GLOBAL,
                                               "credential-certificate",
                                               "Credential with certificate",
                                               "password",
                                               new CertificateCredentialsImpl.UploadedKeyStoreSource(null, SecretBytes.fromBytes(p12Bytes)));

        SystemCredentialsProvider.getInstance().getCredentials().add(certificateCredentials);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);

        // CertificateCredentialsImpl should be jcasc exported as certificate
        assertThat(out.toString(), containsString("certificate:"));
        assertThat(out.toString(), not(containsString("certificateCredentialsImpl:")));
    }
}
