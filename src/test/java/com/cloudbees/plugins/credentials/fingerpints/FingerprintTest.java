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
package com.cloudbees.plugins.credentials.fingerpints;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsParameterDefinition;
import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.xmlunit.matchers.CompareMatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class FingerprintTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private CredentialsStore store = null;

    @Before
    public void setUp() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;

            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    @Test
    public void parameterizedBuildUsageTracked() throws Exception {
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "secret-id", "test credentials", "bob",
                        "secret");
        store.addCredentials(Domain.global(), credentials);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until first use", fingerprint, nullValue());

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("credentials/store/system/domain/_/credentials/secret-id");
        assertThat("Have usage tracking reported", page.getElementById("usage"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-missing"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-present"), nullValue());

        FreeStyleProject job = j.createFreeStyleProject();
        // add a parameter
        job.addProperty(new ParametersDefinitionProperty(
                new CredentialsParameterDefinition(
                        "SECRET",
                        "The secret",
                        "secret-id",
                        Credentials.class.getName(),
                        false
                )));

        j.assertBuildStatusSuccess(job.scheduleBuild2(0,
                new ParametersAction(new CredentialsParameterValue("SECRET", "secret-id", "The secret", true))));

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("A job that does nothing does not use parameterized credentials", fingerprint, nullValue());

        page = wc.goTo("credentials/store/system/domain/_/credentials/secret-id");
        assertThat("Have usage tracking reported", page.getElementById("usage"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-missing"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-present"), nullValue());

        // need to have the job do something otherwise the parameter will be unused
        job.getBuildersList().add(new CaptureEnvironmentBuilder());

        j.assertBuildStatusSuccess(job.scheduleBuild2(0,
                new ParametersAction(new CredentialsParameterValue("SECRET", "secret-id", "The secret", true))));

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
        assertThat(fingerprint.getJobs(), hasItem(is(job.getFullName())));
        Fingerprint.RangeSet rangeSet = fingerprint.getRangeSet(job);
        assertThat(rangeSet, notNullValue());
        assertThat(rangeSet.includes(job.getLastBuild().getNumber()), is(true));

        page = wc.goTo("credentials/store/system/domain/_/credentials/secret-id");
        assertThat(page.getElementById("usage-missing"), nullValue());
        assertThat(page.getElementById("usage-present"), notNullValue());
        assertThat(page.getAnchorByText(job.getFullDisplayName()), notNullValue());

        // check the API
        WebResponse response = wc.goTo(
                "credentials/store/system/domain/_/credentials/secret-id/api/xml?depth=1&xpath=*/fingerprint/usage",
                "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), CompareMatcher.isSimilarTo("<usage>"
                + "<name>"+ Util.xmlEscape(job.getFullName())+"</name>"
                + "<ranges>"
                + "<range>"
                + "<end>"+(job.getLastBuild().getNumber()+1)+"</end>"
                + "<start>" + job.getLastBuild().getNumber()+"</start>"
                + "</range>"
                + "</ranges>"
                + "</usage>").ignoreWhitespace().ignoreComments());
    }
}
