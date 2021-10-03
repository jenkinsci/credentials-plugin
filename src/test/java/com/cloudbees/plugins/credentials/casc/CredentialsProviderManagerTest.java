package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.CredentialsProviderFilter;
import com.cloudbees.plugins.credentials.CredentialsProviderManager;
import com.cloudbees.plugins.credentials.CredentialsProviderTypeRestriction;
import com.cloudbees.plugins.credentials.CredentialsTypeFilter;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import org.junit.ClassRule;
import org.junit.Test;

import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class CredentialsProviderManagerTest {

    @ClassRule
    @ConfiguredWithCode("credentialsProviderManager.yaml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    public void importConfig() {
        CredentialsProviderManager manager = CredentialsProviderManager.getInstance();

        assertThat(manager, notNullValue());
        assertThat(manager.getProviderFilter(), instanceOf(CredentialsProviderFilter.Excludes.class));
        CredentialsProviderFilter.Excludes excludesFilter = (CredentialsProviderFilter.Excludes) manager.getProviderFilter();
        assertThat(excludesFilter.getClassNames(), contains("com.cloudbees.plugins.credentials.UserCredentialsProvider"));

        assertThat(manager.getRestrictions(), hasSize(1));
        CredentialsProviderTypeRestriction restriction = manager.getRestrictions().get(0);
        assertThat(restriction, instanceOf(CredentialsProviderTypeRestriction.Includes.class));
        CredentialsProviderTypeRestriction.Includes restrictionIncludes = (CredentialsProviderTypeRestriction.Includes) restriction;
        assertThat(restrictionIncludes.getProvider(), equalTo("com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider"));
        assertThat(restrictionIncludes.getType(), equalTo("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl$DescriptorImpl"));

        assertThat(manager.getTypeFilter(), instanceOf(CredentialsTypeFilter.Excludes.class));
        CredentialsTypeFilter.Excludes excludesTypeFilter = (CredentialsTypeFilter.Excludes) manager.getTypeFilter();
        assertThat(excludesTypeFilter.getClassNames(), contains("com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl$DescriptorImpl"));
    }

    @Test
    public void exportConfig() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = ConfigurationAsCodeCategoryRoot.getConfiguration(context).get("configuration");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "credentialsProviderManagerExport.yaml");

        assertThat(exported, is(expected));
    }
}
