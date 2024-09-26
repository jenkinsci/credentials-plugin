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
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.Items;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xmlunit.matchers.CompareMatcher;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;

public class CLICommandsTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();
    private CredentialsStore store = null;

    @Before
    public void clearCredentials() {
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
    public void createSmokes() {
        CLICommand cmd = new CreateCredentialsDomainByXmlCommand();
        CLICommandInvoker invoker = new CLICommandInvoker(r, cmd);
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                not(hasItem(hasProperty("name", is("smokes")))));
        assertThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>"))
                .invokeWithArgs("system::system::jenkins"), succeededSilently());
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                hasItem(hasProperty("name", is("smokes"))));
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
    public void createNonHappy() {
        CLICommand cmd = new CreateCredentialsDomainByXmlCommand();
        CLICommandInvoker invoker = new CLICommandInvoker(r, cmd);
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                not(hasItem(hasProperty("name", is("smokes")))));
        assumeThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>"))
                .invokeWithArgs("system::system::jenkins"), succeededSilently());
        assertThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "  <name>smokes</name>\n"
                        + "</com.cloudbees.plugins.credentials.domains.Domain>"))
                .invokeWithArgs("system::system::jenkins"), failedWith(1));

        cmd = new CreateCredentialsByXmlCommand();
        invoker = new CLICommandInvoker(r, cmd);
        assumeThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>GLOBAL</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>created from xml</description>\n"
                        + "  <username>example-com-deployer</username>\n"
                        + "  <password>super-secret</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>"))
                        .invokeWithArgs("system::system::jenkins", "smokes"),
                succeededSilently());
        assertThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>GLOBAL</scope>\n"
                        + "  <id>smokey-id</id>\n"
                        + "  <description>created from xml</description>\n"
                        + "  <username>example-com-deployer</username>\n"
                        + "  <password>super-secret</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>"))
                        .invokeWithArgs("system::system::jenkins", "smokes"),
                failedWith(1));
    }

    @Test
    public void readSmokes() throws Exception {
        Domain smokes = new Domain("smokes", "smoke test domain",
                Collections.singletonList(new HostnameSpecification("smokes.example.com", null)));
        UsernamePasswordCredentialsImpl smokey =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "smokes-id", "smoke testing", "smoke",
                        "smoke text");
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
                Collections.singletonList(new HostnameSpecification("smokes.example.com", null)));
        UsernamePasswordCredentialsImpl smokey =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "smokes-id", "smoke testing", "smoke",
                        "smoke text");

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

    @Test
    public void updateSmokes() throws Exception {
        Domain smokes = new Domain("smokes", "smoke test domain",
                Collections.singletonList(new HostnameSpecification("smokes.example.com", null)));
        UsernamePasswordCredentialsImpl smokey =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "smokes-id", "smoke testing", "smoke",
                        "smoke text");
        store.addDomain(smokes, smokey);
        CLICommandInvoker invoker = new CLICommandInvoker(r, new UpdateCredentialsDomainByXmlCommand());
        Domain replacement = new Domain("smokes", "smoke test domain updated",
                Collections.singletonList(new HostnameSpecification("smokes.example.com", "update.example.com")));
        assertThat(invoker.withStdin(asStream(Items.XSTREAM2.toXML(replacement)))
                .invokeWithArgs("system::system::jenkins", "smokes"), succeededSilently());
        Domain updated = store.getDomainByName("smokes");
        assertThat(Items.XSTREAM2.toXML(updated), CompareMatcher.isIdenticalTo(Items.XSTREAM2.toXML(replacement))
                .ignoreComments()
                .ignoreWhitespace()
        );
        assertThat(Items.XSTREAM2.toXML(updated), not(CompareMatcher.isIdenticalTo(Items.XSTREAM2.toXML(smokes))
                .ignoreComments()
                .ignoreWhitespace()
        ));
        invoker = new CLICommandInvoker(r, new UpdateCredentialsByXmlCommand());
        assertThat(invoker.withStdin(asStream(
                "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "  <scope>SYSTEM</scope>\n"
                        + "  <id>smokes-id</id>\n"
                        + "  <description>updated by xml</description>\n"
                        + "  <username>soot</username>\n"
                        + "  <password>vapour text</password>\n"
                        + "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>"))
                        .invokeWithArgs("system::system::jenkins", "smokes", "smokes-id"),
                succeededSilently());
        assertThat(store.getCredentials(smokes).size(), is(1));
        Credentials cred = store.getCredentials(smokes).get(0);
        assertThat(cred, instanceOf(UsernamePasswordCredentialsImpl.class));
        UsernamePasswordCredentialsImpl c = (UsernamePasswordCredentialsImpl) cred;
        assertThat(c.getScope(), is(CredentialsScope.SYSTEM));
        assertThat(c.getId(), is("smokes-id"));
        assertThat(c.getDescription(), is("updated by xml"));
        assertThat(c.getUsername(), is("soot"));
        assertThat(c.getPassword().getPlainText(), is("vapour text"));
    }

    @Test
    public void deleteSmokes() throws Exception {
        Domain smokes = new Domain("smokes", "smoke test domain",
                Collections.singletonList(new HostnameSpecification("smokes.example.com", null)));
        UsernamePasswordCredentialsImpl smokey =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "smokes-id", "smoke testing", "smoke",
                        "smoke text");
        store.addDomain(smokes, smokey);
        CLICommandInvoker invoker = new CLICommandInvoker(r, new DeleteCredentialsCommand());
        assertThat(store.getCredentials(smokes), not(is(Collections.<Credentials>emptyList())));
        assertThat(invoker.invokeWithArgs("system::system::jenkins", "smokes", "smokes-id"), succeededSilently());
        assertThat(store.getCredentials(smokes), is(Collections.<Credentials>emptyList()));

        invoker = new CLICommandInvoker(r, new DeleteCredentialsDomainCommand());
        assertThat(invoker.invokeWithArgs("system::system::jenkins", "smokes"), succeededSilently());
        assertThat(store.getDomainByName("smokes"), nullValue());
    }

    @Test
    public void listCredentialsAsXML() throws Exception {
        Domain smokes = new Domain("smokes", "smoke test domain",
                Collections.singletonList(new HostnameSpecification("smokes.example.com", null)));
        UsernamePasswordCredentialsImpl smokey =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "smokes-id", "smoke testing", "smoke",
                        "smoke text");
        UsernamePasswordCredentialsImpl global =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "global-cred-id", "Global Credentials", "john",
                        "john");
        UsernamePasswordCredentialsImpl globalDomainSystemScope =
                new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "global-cred-id-system", "Global Credentials", "john",
                        "john");
        CLICommandInvoker invoker = new CLICommandInvoker(r, new ListCredentialsAsXmlCommand());
        CLICommandInvoker.Result result = invoker.invokeWithArgs("system::system::jenkins");
        assertThat(result, succeeded());
        assertThat(result.stdout(), not(containsString("<id>smokes-id</id>")));

        store.addDomain(smokes, smokey);
        store.addCredentials(Domain.global(), global);
        store.addCredentials(Domain.global(), globalDomainSystemScope);

        invoker = new CLICommandInvoker(r, new ListCredentialsAsXmlCommand());
        result = invoker.invokeWithArgs("system::system::jenkins");
        assertThat(result, succeeded());
        assertThat(result.stdout(), allOf(
                containsString("<id>smokes-id</id>"),
                containsString("<id>global-cred-id</id>"),
                containsString("<id>global-cred-id-system</id>"),
                containsString("<name>smokes</name>")
        ));
    }

    @Test
    public void importCredentialsAsXML() {
        InputStream input = this.getClass().getResourceAsStream("credentials-input.xml");
        CLICommandInvoker invoker = new CLICommandInvoker(r, new ImportCredentialsAsXmlCommand());

        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                not(hasItem(hasProperty("name", is("smokes")))));

        invoker.withStdin(input).invokeWithArgs("system::system::jenkins");
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                hasItem(hasProperty("name", is("smokes"))));
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()),
                hasItem(hasProperty("id", is("global-cred-id"))));
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(new Domain("smokes", null, null)),
                hasItem(hasProperty("id", is("smokes-id"))));

    }


    private static ByteArrayInputStream asStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
