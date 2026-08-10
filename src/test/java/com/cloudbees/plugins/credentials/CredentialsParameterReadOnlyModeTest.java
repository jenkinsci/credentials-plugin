package com.cloudbees.plugins.credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ParametersDefinitionProperty;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verifies that the {@code <c:select>} control used by {@link CredentialsParameterDefinition}'s
 * "Default Value" field still shows the configured credential id (instead of silently rendering
 * as unset) and hides its "Add" affordance for a read-only ({@link Item#EXTENDED_READ}) viewer who
 * lacks {@link CredentialsProvider#USE_ITEM} and therefore cannot have the field's AJAX-populated
 * option list filled in.
 */
@WithJenkins
class CredentialsParameterReadOnlyModeTest {

    private JenkinsRule j;
    private String credentialId;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ).everywhere().to("viewer"));

        credentialId = "deploy-bot-creds";
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialId, "deployment credentials", "bob", "secret"));
    }

    @Issue("JENKINS-62143")
    @Test
    void readOnlyViewerSeesConfiguredCredentialIdNotHiddenAsUnset() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition(
                "DEPLOY_CREDENTIALS", "description", credentialId,
                UsernamePasswordCredentialsImpl.class.getName(), true)));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("viewer");
        HtmlPage page = wc.goTo(p.getUrl() + "configure");

        assertThat("the read-only viewer should still see which credential is configured",
                page.getWebResponse().getContentAsString(), containsString(credentialId));
        assertThat("a read-only viewer must not be able to add new credentials from this field",
                page.getByXPath("//*[contains(@class,'credentials-add')]"), empty());
    }

    @Issue("JENKINS-62143")
    @Test
    void adminStillSeesEditableSelectAndAddControl() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition(
                "DEPLOY_CREDENTIALS", "description", credentialId,
                UsernamePasswordCredentialsImpl.class.getName(), true)));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("admin");
        HtmlPage page = wc.goTo(p.getUrl() + "configure");

        assertThat("an administrator must still see the editable credentials select",
                page.getByXPath("//select[contains(@class,'credentials-select')]"), not(empty()));
        assertThat("an administrator must still be able to add new credentials from this field",
                page.getByXPath("//*[contains(@class,'credentials-add')]"), not(empty()));
    }
}
