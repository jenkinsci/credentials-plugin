/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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


package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.htmlunit.html.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.markup.MarkupFormatter;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.tasks.Builder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.*;

@WithJenkins
class CredentialsParameterDefinitionTest {

    @Test
    void defaultValue(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        CredentialsStore store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, "id", "description", "username", "password"));

        List<ParameterDefinition> params = new ArrayList<>();
        params.add(new CredentialsParameterDefinition("name", "description", "id", "type", true));

        ParametersDefinitionProperty prop = new ParametersDefinitionProperty(params);
        p.addProperty(prop);
        p.getBuildersList().add(new ParamCheckBuilder());
        r.buildAndAssertSuccess(p);
    }

    @Test
    void onlyIncludeUserCredentialsWhenChecked(JenkinsRule r) throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());

        String globalCredentialId = UUID.randomUUID().toString();
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, globalCredentialId, "global credentials", "root", "qwerty"));

        User beth = User.getOrCreateByIdOrFullName("beth");
        String userCredentialId = UUID.randomUUID().toString();
        try (ACLContext ignored = ACL.as(beth)) {
            CredentialsProvider.lookupStores(beth).iterator().next().addCredentials(Domain.global(),
                    new UsernamePasswordCredentialsImpl(
                            CredentialsScope.USER, userCredentialId, "user credentials", "root", "a much better password than qwerty"));
        }

        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition("credential", "description",
                globalCredentialId, UsernamePasswordCredentialsImpl.class.getName(), true)));

        JenkinsRule.WebClient wc = r.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false) // build with parameters returns 405 success for fun reasons
                .login("beth");
        HtmlForm form = wc.getPage(p, "build?delay=0sec").getFormByName("parameters");

        HtmlCheckBoxInput checkbox = form.getInputByName("includeUser");
        assertFalse(checkbox.isChecked(), "List user credentials checkbox should not be checked by default");
        HtmlElement div = form.getOneHtmlElementByAttribute("div", "class", "warning user-credentials-caution");
        assertFalse(div.isDisplayed(), "Caution message about user credentials should not be displayed yet");
        form.getSelectByName("_.value").getOptions().forEach(option -> assertNotEquals(userCredentialId, option.getValueAttribute(), "No user credential should be an option yet"));

        HtmlElementUtil.click(checkbox);
        assertTrue(div.isDisplayed(), "Caution message about user credentials should be displayed after checking the box");
        form.getSelectByName("_.value").getOptions().stream().filter(option -> option.getValueAttribute().equals(userCredentialId)).findAny()
                .orElseThrow(() -> new AssertionError("No credential found matching user credential id " + userCredentialId));
    }

    @Issue("SECURITY-2690")
    @Test
    void escapeAndMarkupFormatAreDoneCorrectly(JenkinsRule r) throws Exception {
        r.jenkins.setMarkupFormatter(new MyMarkupFormatter());
        FreeStyleProject p = r.createFreeStyleProject("p");
        CredentialsParameterDefinition param = new CredentialsParameterDefinition("<param name>", "<param description>", "", "type", false);
        assertEquals("<b>[</b>param description<b>]</b>", param.getFormattedDescription());
        p.addProperty(new ParametersDefinitionProperty(param));
        JenkinsRule.WebClient wc = r.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        HtmlPage buildPage = wc.getPage(p, "build?delay=0sec");
        String buildPageText = buildPage.getWebResponse().getContentAsString();

        assertAll(
                () -> assertThat(buildPage.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_BAD_METHOD)), // 405 to dissuade scripts from thinking this triggered the build
                () -> assertThat("build page should escape param name", buildPageText, containsString("&lt;param name&gt;")),
                () -> assertThat("build page should not leave param name unescaped", buildPageText, not(containsString("<param name>"))),
                () -> assertThat("build page should mark up param description", buildPageText, containsString("<b>[</b>param description<b>]</b>")),
                () -> assertThat("build page should not leave param description unescaped", buildPageText, not(containsString("<param description>")))
        );
        HtmlForm form = buildPage.getFormByName("parameters");
        r.submit(form);
        r.waitUntilNoActivity();
        FreeStyleBuild b = p.getBuildByNumber(1);

        HtmlPage parameteresPage = r.createWebClient().getPage(b, "parameters/");
        String parametersPageText = parameteresPage.getWebResponse().getContentAsString();
        assertAll(
                () -> assertThat("parameters page should escape param name", parametersPageText, containsString("&lt;param name&gt;")),
                () -> assertThat("parameters page should not leave param name unescaped", parametersPageText, not(containsString("<param name>"))),
                () -> assertThat("parameters page should mark up param description", parametersPageText, containsString("<b>[</b>param description<b>]</b>")),
                () -> assertThat("parameters page should not leave param description unescaped", parametersPageText, not(containsString("<param description>")))
        );
    }

    private static class ParamCheckBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            ParametersAction pa = build.getAction(ParametersAction.class);
            ParameterValue pv = pa.getParameter("name");
            assertInstanceOf(CredentialsParameterValue.class, pv);
            assertTrue(((CredentialsParameterValue) pv).isDefaultValue());
            return true;
        }
    }

    static class MyMarkupFormatter extends MarkupFormatter {
        @Override
        public void translate(String markup, @NonNull Writer output) throws IOException {
            Matcher m = Pattern.compile("[<>]").matcher(markup);
            StringBuilder buf = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(buf, m.group().equals("<") ? "<b>[</b>" : "<b>]</b>");
            }
            m.appendTail(buf);
            output.write(buf.toString());
        }
    }
}
