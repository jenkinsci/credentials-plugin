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
package com.cloudbees.plugins.credentials.cli;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsSelectHelper;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.Items;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.hamcrest.Matchers;
import org.xmlunit.matchers.CompareMatcher;

import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class CLICommandsTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void clearCredentials() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.<Credentials>emptyList()));
    }

    @Test
    public void createSmokes() {
        CLICommand cmd = new CreateCredentialsDomainByXmlCommand();
        CLICommandInvoker invoker = new CLICommandInvoker(r, cmd);
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                (Matcher) not(hasItem(hasProperty("name", is("smokes")))));
        assertThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>"))
                .invokeWithArgs("system::system::jenkins"), succeededSilently());
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                (Matcher) hasItem(hasProperty("name", is("smokes"))));
        cmd = new CreateCredentialsByXmlCommand();
        invoker = new CLICommandInvoker(r, cmd);
        assertThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>GLOBAL</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>created from xml</description>\n"
                        + "  <username>example-com-deployer</username>\n"
                        + "  <password>super-secret</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>"))
                        .invokeWithArgs("system::system::jenkins", "smokes"),
                succeededSilently());
        Domain domain = null;
        for (Domain d : SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet()) {
            if ("smokes".equals(d.getName())) {
                domain = d;
                break;
            }
        }
        List<Credentials> credentials = SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(domain);
        assertThat(credentials, notNullValue());
        Credentials cred = credentials.isEmpty() ? null : credentials.get(0);
        assertThat(cred, instanceOf(UsernamePasswordCredentialsImpl.class));
        UsernamePasswordCredentialsImpl c = (UsernamePasswordCredentialsImpl) cred;
        assertThat(c.getScope(), is(CredentialsScope.GLOBAL));
        assertThat(c.getId(), is("smokey-id"));
        assertThat(c.getDescription(), is("created from xml"));
        assertThat(c.getUsername(), is("example-com-deployer"));
        assertThat(c.getPassword().getPlainText(), is("super-secret"));
    }

    @Test
    public void readSmokes() throws Exception {
        Domain smokes = new Domain("smokes", "smoke test domain",
                Collections.<DomainSpecification>singletonList(new HostnameSpecification("smokes.example.com", null)));
        UsernamePasswordCredentialsImpl smokey =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "smokes-id", "smoke testing", "smoke",
                        "smoke text");
        CredentialsStore store = null;
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.getInstance())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
        store.addDomain(smokes, smokey);
        CLICommandInvoker invoker = new CLICommandInvoker(r, new GetCredentialsDomainAsXmlCommand());
        CLICommandInvoker.Result result = invoker.invokeWithArgs("system::system::jenkins", "smokes");
        assertThat(result, succeeded());
        assertThat(result.stdout(), CompareMatcher.isIdenticalTo(Items.XSTREAM2.toXML(smokes))
                .ignoreComments()
                .ignoreWhitespace()
        );
        invoker = new CLICommandInvoker(r, new GetCredentialsAsXmlCommand());
        result = invoker.invokeWithArgs("system::system::jenkins", "smokes", "smokes-id");
        assertThat(result, succeeded());
        assertThat("secrets are redacted", result.stdout(),
                not(CompareMatcher.isIdenticalTo(Items.XSTREAM2.toXML(smokey))
                        .ignoreComments()
                        .ignoreWhitespace()
                ));
        assertThat("secrets are redacted", result.stdout(),
                not(containsString(smokey.getPassword().getEncryptedValue())));
        assertThat("secrets are redacted", result.stdout(),
                not(containsString(smokey.getPassword().getPlainText())));
        assertThat("secrets are redacted", result.stdout(), CompareMatcher.isIdenticalTo(
                Items.XSTREAM2.toXML(smokey).replace(smokey.getPassword().getEncryptedValue(), "<secret-redacted/>"))
                .ignoreComments()
                .ignoreWhitespace()
        );
    }

    @Test
    public void listSmokes() throws Exception {
        Domain smokes = new Domain("smokes", "smoke test domain",
                Collections.<DomainSpecification>singletonList(new HostnameSpecification("smokes.example.com", null)));
        UsernamePasswordCredentialsImpl smokey =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "smokes-id", "smoke testing", "smoke",
                        "smoke text");
        CredentialsStore store = null;
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.getInstance())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());

        // check getting the providers
        CLICommandInvoker invoker = new CLICommandInvoker(r, new ListCredentialsProvidersCommand());
        CLICommandInvoker.Result result = invoker.invoke();
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("\nsystem "));
        assertThat(result.stdout(), containsString("\nSystemCredentialsProvider "));
        assertThat(result.stdout(), containsString("\n"+SystemCredentialsProvider.ProviderImpl.class.getName()+" "));

        // now check getting the resolvers
        invoker = new CLICommandInvoker(r, new ListCredentialsContextResolversCommand());
        result = invoker.invoke();
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("\nsystem "));
        assertThat(result.stdout(), containsString("\nSystemContextResolver "));
        assertThat(result.stdout(), containsString("\n" + CredentialsSelectHelper.SystemContextResolver.class.getName()+" "));

        // now check listing credentials (expect empty)
        invoker = new CLICommandInvoker(r, new ListCredentialsCommand());
        result = invoker.invokeWithArgs("system::system::jenkins");
        assertThat(result, succeeded());
        assertThat(result.stdout().replaceAll("\\s+", " "), allOf(
                containsString(" Domain (global) Description # of Credentials 0 "),
                not(containsString(" Domain smokes "))
        ));

        store.addDomain(smokes, smokey);
        invoker = new CLICommandInvoker(r, new ListCredentialsCommand());
        result = invoker.invokeWithArgs("system::system::jenkins");
        assertThat(result, succeeded());
        assertThat(result.stdout().replaceAll("\\s+", " "), allOf(
                containsString(" Domain (global) Description # of Credentials 0 "),
                containsString(" Domain smokes Description smoke test domain # of Credentials 1 "),
                containsString(" smokes-id smoke/****** (smoke testing) ")
        ));
    }

    private static ByteArrayInputStream asStream(String text) {
        return new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8")));
    }
}
