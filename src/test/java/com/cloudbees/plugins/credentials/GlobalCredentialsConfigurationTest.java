package com.cloudbees.plugins.credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.HttpURLConnection;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verifies that the "Manage Jenkins &gt; Configure Credentials" page
 * ({@link GlobalCredentialsConfiguration}, {@code configureCredentials}) is viewable
 * (but not editable) by a user with {@link Jenkins#SYSTEM_READ} but not
 * {@link Jenkins#ADMINISTER}, matching the read-only rendering convention already used
 * by {@code hudson.security.GlobalSecurityConfiguration}.
 */
@WithJenkins
class GlobalCredentialsConfigurationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("viewer"));
    }

    @Issue("JENKINS-62429")
    @Test
    void systemReadViewerCanViewButNotSaveConfigureCredentials() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("viewer");

        HtmlPage page = wc.goTo("configureCredentials");
        assertThat("a Jenkins.SYSTEM_READ viewer should not see Save/Apply controls on a page they cannot save",
                page.getByXPath("//button[@type='submit' or contains(@class,'apply-button')]"), empty());

        // Attach a valid crumb so the 403 we assert on below can only come from the
        // Jenkins.ADMINISTER permission check inside doConfigure, not from crumb validation.
        WebRequest request = wc.addCrumb(
                new WebRequest(new URL(j.getURL(), "configureCredentials/configure"), HttpMethod.POST));
        FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class,
                () -> wc.getPage(request),
                "submitting the configuration should still require Jenkins.ADMINISTER");
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ex.getStatusCode());
    }

    @Issue("JENKINS-62429")
    @Test
    void administerUserStillSeesSaveApplyControls() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("admin");

        HtmlPage page = wc.goTo("configureCredentials");
        assertThat("an administrator must still be able to save this page",
                page.getByXPath("//button[@type='submit' or contains(@class,'apply-button')]"), not(empty()));
    }
}
