package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.GlobalCredentialsConfiguration;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.Symbol;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

import javax.annotation.Nonnull;

import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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

        @Nonnull
        @Override
        public GlobalConfigurationCategory getCategory() {
            return GlobalConfigurationCategory.get(GlobalCredentialsConfiguration.Category.class);
        }

        @Nonnull
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
}
