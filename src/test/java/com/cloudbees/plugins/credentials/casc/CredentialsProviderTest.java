package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.cloudbees.plugins.credentials.impl.DummyCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.jenkinsci.Symbol;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.StringContains.containsString;
import static org.jvnet.hudson.test.JenkinsMatchers.hasPlainText;

public class CredentialsProviderTest {

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("CredentialsProviderExtension.yaml")
    public void import_credentials_provider_extension_credentials() {
        List<DummyCredentials> dummyCred = CredentialsProvider.lookupCredentialsInItemGroup(
                DummyCredentials.class, j.jenkins, ACL.SYSTEM2,
                Collections.emptyList()
        );
        assertThat(dummyCred, hasSize(1));
        assertThat(dummyCred.get(0).getUsername(), equalTo("user1"));

        // the system provider works fine too
        List<UsernamePasswordCredentials> ups = CredentialsProvider.lookupCredentialsInItemGroup(
                UsernamePasswordCredentials.class, j.jenkins, ACL.SYSTEM2,
                Collections.singletonList(new HostnameRequirement("api.test.com"))
        );
        assertThat(ups, hasSize(1));
        final UsernamePasswordCredentials up = ups.get(0);
        assertThat(up.getPassword(), hasPlainText("password"));
    }

    @Test
    @ConfiguredWithCode("CredentialsProviderExtension.yaml")
    public void export_credentials_provider_extension_credentials() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);
        assertThat(out.toString(), containsString("username: \"user1\""));
        assertThat(out.toString(), containsString("id: \"sudo_password\""));
    }

    // Special test provider which only provides a single credential
    @TestExtension
    @Symbol("testProvider")
    public static final class TestCredentialsProvider extends CredentialsProvider {

        private DummyCredentials theCredential;

        public TestCredentialsProvider() {
            this.theCredential = new DummyCredentials(CredentialsScope.GLOBAL, "user1", "s3cr3t");
        }

        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type, @Nullable ItemGroup itemGroup, @Nullable Authentication authentication, @Nullable List<DomainRequirement> domainRequirements) {
            if (!type.equals(DummyCredentials.class)) {
                return Collections.emptyList();
            }
            List<C> l = new ArrayList<>();
            l.add((C) theCredential);
            return l;
        }

        public DummyCredentials getCredentials() {
            return theCredential;
        }

        public void setCredentials(DummyCredentials cred) {
            this.theCredential = cred;
        }
    }

    // Configurator for this specific CredentialsProvider extension
    @TestExtension
    public static final class TestCredentialsProviderConfigurator extends BaseConfigurator<TestCredentialsProvider> {

        @Override
        public Class<TestCredentialsProvider> getTarget() {
            return TestCredentialsProvider.class;
        }

        @Override
        protected TestCredentialsProvider instance(Mapping mapping, ConfigurationContext configurationContext) {
            return ExtensionList.lookupSingleton(TestCredentialsProvider.class);
        }

        @NonNull
        @Override
        public Set<Attribute<TestCredentialsProvider, ?>> describe() {
            return Collections.singleton(
                    new Attribute<TestCredentialsProvider, DummyCredentials>("unique", DummyCredentials.class)
                        .setter(TestCredentialsProvider::setCredentials)
                        .getter(TestCredentialsProvider::getCredentials));
        }

        @Override
        public CNode describe(TestCredentialsProvider instance, ConfigurationContext context) throws Exception {
            Mapping mapping = new Mapping();
            for (Attribute attribute : describe()) {
                mapping.put(attribute.getName(), attribute.describe(instance, context));
            }
            return mapping;
        }
    }
}
