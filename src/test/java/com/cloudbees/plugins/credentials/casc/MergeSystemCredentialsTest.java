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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.security.ACL;
import hudson.util.Secret;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Sequence;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;

public class MergeSystemCredentialsTest {

    @ClassRule
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Before
    public void setup() {
        System.setProperty("casc.credentials.merge.strategy", "merge");
    }

    @After
    public void teardown() {
        System.clearProperty("casc.credentials.merge.strategy");
    }

    @Test
    public void merge_system_credentials() throws ConfiguratorException {
        UsernamePasswordCredentials foo = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "foo", "", "Foo", "Bar");
        UsernamePasswordCredentials bar = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "bar", "", "Bar", "Foo");
        Domain testCom = new Domain("test.com", "test dot com", Collections.emptyList());
        SystemCredentialsProvider.getInstance().getCredentials().add(foo);
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(testCom, new CopyOnWriteArrayList<>(Collections.singletonList(bar)));
        ConfigurationAsCode.get().configure(getClass().getResource("MergeSystemCredentialsTest.yaml").toExternalForm());
        System.out.println(SystemCredentialsProvider.getInstance().getDomainCredentialsMap());
        List<UsernamePasswordCredentials> ups = CredentialsProvider.lookupCredentials(
            UsernamePasswordCredentials.class, j.jenkins, ACL.SYSTEM,
            Collections.singletonList(new HostnameRequirement("api.test.com"))
        );
        assertThat(ups, hasSize(3));
        bar = CredentialsMatchers.firstOrNull(ups, CredentialsMatchers.withId("bar"));
        assertThat(bar, not(nullValue()));
        assertThat(bar.getUsername(), equalTo("bar_usr"));
    }

}
