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
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
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

    @Test
    public void createSmokes() {
        CLICommand cmd = new CreateCredentialsDomainByXmlCommand();
        CLICommandInvoker invoker = new CLICommandInvoker(r, cmd);
        assertThat(SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet(),
                (Matcher) not(hasItem(hasProperty("name",is("smokes")))));
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
        for (Domain d: SystemCredentialsProvider.getInstance().getDomainCredentialsMap().keySet()) {
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

    private static ByteArrayInputStream asStream(String text) {
        return new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8")));
    }
}
