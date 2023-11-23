/*
 * The MIT License
 *
 *  Copyright (c) 2018, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import hudson.security.ACL;
import hudson.util.Secret;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Sequence;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;

public class SystemCredentialsTest {

    @ClassRule
    @ConfiguredWithCode("SystemCredentialsTest.yaml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    public void import_system_credentials() {
        List<UsernamePasswordCredentials> ups = CredentialsProvider.lookupCredentialsInItemGroup(
            UsernamePasswordCredentials.class, j.jenkins, ACL.SYSTEM2,
            Collections.singletonList(new HostnameRequirement("api.test.com"))
        );
        assertThat(ups, hasSize(1));
        final UsernamePasswordCredentials up = ups.get(0);
        assertThat(up.getPassword().getPlainText(), equalTo("password"));
    }

    @Test
    public void export_system_credentials() throws Exception {
        CredentialsRootConfigurator root = Jenkins.get()
            .getExtensionList(CredentialsRootConfigurator.class).get(0);

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        Mapping configNode = Objects
            .requireNonNull(root.describe(root.getTargetComponent(context), context)).asMapping();
        Mapping domainCredentials = configNode
            .get("system").asMapping().get("domainCredentials")
            .asSequence()
            .get(0).asMapping();
        Mapping domain = domainCredentials.get("domain").asMapping();
        Sequence credentials = domainCredentials.get("credentials").asSequence();
        Mapping usernamePassword = credentials.get(0).asMapping().get("usernamePassword")
            .asMapping();
        Mapping hostnameSpecification = domain.get("specifications").asSequence().get(0).asMapping()
            .get("hostnameSpecification").asMapping();

        String password = usernamePassword.getScalarValue("password");
        Secret decryptedPassword = Secret.decrypt(password);
        assertNotNull(decryptedPassword);
        assertThat(usernamePassword.getScalarValue("scope"), is("SYSTEM"));
        assertThat(usernamePassword.getScalarValue("username"), is("root"));
        assertThat(password, is(not("password")));
        assertThat(decryptedPassword.getPlainText(), is("password"));
        assertThat(domain.getScalarValue("name"), is("test.com"));
        assertThat(domain.getScalarValue("description"), is("test.com domain"));
        assertThat(hostnameSpecification.getScalarValue("includes"), is("*.test.com"));
    }
}
