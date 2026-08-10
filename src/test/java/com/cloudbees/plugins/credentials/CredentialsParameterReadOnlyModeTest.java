package com.cloudbees.plugins.credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import com.cloudbees.plugins.credentials.common.Messages;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ParametersDefinitionProperty;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verifies that the {@code <c:select>} control used by {@link CredentialsParameterDefinition}'s
 * "Default Value" field still indicates that a credential is configured (instead of silently
 * rendering as unset) and hides its "Add" affordance for a read-only ({@link Item#EXTENDED_READ})
 * viewer who lacks {@link CredentialsProvider#USE_ITEM} and therefore cannot have the field's
 * AJAX-populated option list filled in with the credential's actual id/description.
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
    void readOnlyViewerSeesCredentialIsConfiguredNotHiddenAsUnset() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition(
                "DEPLOY_CREDENTIALS", "description", credentialId,
                UsernamePasswordCredentialsImpl.class.getName(), true)));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("viewer");
        HtmlPage page = wc.goTo(p.getUrl() + "configure");
        wc.waitForBackgroundJavaScript(4000);

        List<DomElement> readOnlyElements = page.getByXPath("//pre[contains(@class,'jenkins-readonly')]");
        List<String> readOnlyTexts =
                readOnlyElements.stream().map(DomElement::getTextContent).collect(Collectors.toList());
        // A viewer without CredentialsProvider.USE_ITEM can't have the real id/description resolved
        // (that would leak credential metadata to someone who can't otherwise see it), so per the
        // documented doFillXYZItems pattern (docs/consumer.adoc) the field falls back to
        // StandardListBoxModel#includeCurrentValue's generic placeholder rather than "- none -".
        assertThat("the read-only viewer should see an indication that a credential is configured, "
                        + "not have it collapse to an empty/unset field",
                readOnlyTexts, hasItem(Messages.AbstractIdCredentialsListBoxModel_CurrentSelection()));
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
