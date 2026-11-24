package com.cloudbees.plugins.credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.RootAction;
import hudson.model.Run;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verifies how {@link CredentialsParameterValue} finds the build it is shown for.
 */
@WithJenkins
class CredentialsParameterValueTest {

    private static final String CREDENTIALS_ID = "credential-name";

    private JenkinsRule j;
    private UsernamePasswordCredentialsImpl credentials;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, CREDENTIALS_ID, "credential-description", "user", "password");
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), credentials);
    }

    @Test
    void usesTheBuildItIsGiven() throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(j.createFreeStyleProject());
        CredentialsParameterValue value =
                new CredentialsParameterValue("parameter-name", CREDENTIALS_ID, "does not matter", true);

        // this thread is not handling a request, so there is no build to find in it
        assertThrows(IllegalStateException.class, value::describe);
        assertThrows(IllegalStateException.class, value::iconClassName);
        assertThrows(IllegalStateException.class, value::url);

        assertThat(value.describe(build), containsString("credential-description"));
        assertThat(value.iconClassName(build), is(credentials.getDescriptor().getIconClassName()));
        // the link is made relative to the current request, so what it is does not matter here
        assertDoesNotThrow(() -> value.url(build));
    }

    /**
     * The parameters of a build can be shown by a request that is not for the build, for example when Jenkins renders
     * a dialog on demand. Then {@code value.jelly} has to use the build that the page gives it.
     */
    @Test
    void valueJellyUsesTheBuildVariable() throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(j.createFreeStyleProject());
        ExtensionList.lookupSingleton(ValuePage.class)
                .show(build, new CredentialsParameterValue("parameter-name", CREDENTIALS_ID, "does not matter", true));

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.goTo(ValuePage.URL_NAME);
            DomElement link = page.querySelector("a[href$='/credential/" + CREDENTIALS_ID + "']");
            assertNotNull(link, "the credentials are not linked");
            assertThat(link.asNormalizedText(), containsString("credential-description"));
            assertNotNull(link.querySelector("svg"), "the credentials have no icon");
        }
    }

    @Test
    void parametersPageShowsTheCredentials() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition(
                "parameter-name",
                "does not matter",
                CREDENTIALS_ID,
                UsernamePasswordCredentialsImpl.class.getName(),
                true)));
        FreeStyleBuild build = j.buildAndAssertSuccess(project);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(build, "parameters");
            DomElement link = page.querySelector("a[href$='/credential/" + CREDENTIALS_ID + "']");
            assertNotNull(link, "the credentials are not linked");
            assertThat(link.asNormalizedText(), containsString("credential-description"));
            assertNotNull(link.querySelector("svg"), "the credentials have no icon");
        }
    }

    /**
     * A page that shows a parameter value the way the parameters of a build are shown, but is not below the build.
     */
    @TestExtension("valueJellyUsesTheBuildVariable")
    public static final class ValuePage implements RootAction {

        private static final String URL_NAME = "credentials-parameter-value";

        private Run<?, ?> run;
        private CredentialsParameterValue value;

        void show(Run<?, ?> run, CredentialsParameterValue value) {
            this.run = run;
            this.value = value;
        }

        public Run<?, ?> getRun() {
            return run;
        }

        public CredentialsParameterValue getValue() {
            return value;
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return URL_NAME;
        }
    }
}
