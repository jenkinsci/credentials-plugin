package com.cloudbees.plugins.credentials;

import static com.cloudbees.plugins.credentials.CredentialsSelectHelperTest.selectOption;
import static com.cloudbees.plugins.credentials.XmlMatchers.isSimilarToIgnoringPrivateAttrs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import hudson.security.ACL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.htmlunit.WebResponse;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlRadioButtonInput;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ViewCredentialsActionTest {

    @Test
    void smokes(JenkinsRule j) throws Exception {
        SystemCredentialsProvider.ProviderImpl system = ExtensionList.lookup(CredentialsProvider.class).get(
                SystemCredentialsProvider.ProviderImpl.class);

        CredentialsStore systemStore = system.getStore(j.getInstance());

        List<Domain> domainList = new ArrayList<>(systemStore.getDomains());
        domainList.remove(Domain.global());
        for (Domain d: domainList) {
            systemStore.removeDomain(d);
        }

        List<Credentials> credentialsList = new ArrayList<>(systemStore.getCredentials(Domain.global()));
        for (Credentials c: credentialsList) {
            systemStore.removeCredentials(Domain.global(), c);
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        WebResponse response = wc.goTo("credentials/api/xml?depth=5", "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), isSimilarToIgnoringPrivateAttrs("<rootActionImpl>"
                + "<stores>"
                + "<system>"
                + "<domains>"
                + "<_>"
                + "<description>"
                + "Credentials that should be available everywhere."
                + "</description>"
                + "<displayName>Global</displayName>"
                + "<fullDisplayName>System » Global</fullDisplayName>"
                + "<fullName>system/_</fullName>"
                + "<global>true</global>"
                + "<urlName>_</urlName>"
                + "</_>"
                + "</domains>"
                + "</system>"
                + "</stores>"
                + "</rootActionImpl>"));

        Random entropy = new Random();
        String domainName = "test"+entropy.nextInt();
        String domainDescription = "test description " + entropy.nextLong();
        String credentialId = "test-id-" + entropy.nextInt();
        String credentialDescription = "test-account-" + entropy.nextInt();
        String credentialUsername = "test-user-" + entropy.nextInt();
        systemStore.addDomain(new Domain(domainName, domainDescription, Collections.emptyList()),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialId,
                        credentialDescription, credentialUsername, "test-secret"));
        response = wc.goTo("credentials/api/xml?depth=5", "application/xml").getWebResponse();
        assertThat(response.getContentAsString(), isSimilarToIgnoringPrivateAttrs("<rootActionImpl>"
                + "<stores>"
                + "<system>"
                + "<domains>"
                + "<_>"
                + "<description>"
                + "Credentials that should be available everywhere."
                + "</description>"
                + "<displayName>Global</displayName>"
                + "<fullDisplayName>System » Global</fullDisplayName>"
                + "<fullName>system/_</fullName>"
                + "<global>true</global>"
                + "<urlName>_</urlName>"
                + "</_>"
                + "<" + domainName + ">"
                + "<credential>"
                + "<description>"+ credentialDescription +"</description>"
                + "<displayName>" + credentialUsername + "/****** (" + credentialDescription + ")</displayName>"
                + "<fullName>system/"+ domainName + "/" + credentialId + "</fullName>"
                + "<id>" + credentialId + "</id>"
                + "<typeName>Username with password</typeName>"
                + "</credential>"
                + "<description>"
                + domainDescription
                + "</description>"
                + "<displayName>" + domainName + "</displayName>"
                + "<fullDisplayName>System » " + domainName + "</fullDisplayName>"
                + "<fullName>system/" + domainName + "</fullName>"
                + "<global>false</global>"
                + "<urlName>" + domainName + "</urlName>"
                + "</" + domainName + ">"
                + "</domains>"
                + "</system>"
                + "</stores>"
                + "</rootActionImpl>"));
    }

    @Test
    void isVisibleShouldReturnTrueForFolders(JenkinsRule j) throws Exception {
        // For a folder (with store)
        MockFolder folder = j.createFolder("test-folder");
        // And a ViewCredentialsAction attached to it
        ViewCredentialsAction action = new ViewCredentialsAction(folder);
        assertTrue(CredentialsProvider.enabled(folder).stream().anyMatch(CredentialsProvider::hasCredentialsDescriptors),
            "Verify assumption of 'hasCredentialsDescriptors'");
        assertTrue(CredentialsProvider.enabled(folder).stream().anyMatch(p -> p.getStore(folder) != null),
            "Verify assumption of 'getStore != null' (MockFolderCredentialsProvider)");

        assertTrue(action.isVisible(), "ViewCredentialsAction.isVisible() should return for folders");
        assertNotNull(action.getIconFileName(),
            "getIconFileName() should return non-null when action is visible");
    }

    @Test
    void isVisibleShouldReturnFalseForRegularJobs(JenkinsRule j) throws Exception {
        // For a FreeStyleProject (or any other regular job that is a TopLevelItem but not a folder)
        FreeStyleProject job = j.createFreeStyleProject("test-job");
        // And a ViewCredentialsAction attached to it
        ViewCredentialsAction action = new ViewCredentialsAction(job);
        assertTrue(CredentialsProvider.enabled(job).stream().anyMatch(CredentialsProvider::hasCredentialsDescriptors),
            "Verify assumption of 'hasCredentialsDescriptors'");
        assertTrue(CredentialsProvider.enabled(job).stream().noneMatch(p -> p.getStore(job) != null),
            "Verify assumption of 'getStore == null' (jobs don't manage credentials)");

        assertFalse(action.isVisible(),
            "ViewCredentialsAction.isVisible() should return false for regular jobs (no credentials store)");
        assertNull(action.getIconFileName(),
            "getIconFileName() should return null when action is not visible");
    }
    
    @Test
    void createUsernamePasswordCredentials(JenkinsRule r) throws Exception {
        createUsernamePasswordCredentials(r, false);
    }

    @Test
    void createUsernamePasswordCredentialsWithMultipleDomains(JenkinsRule r) throws Exception {
        createTestDomain(r);
        createUsernamePasswordCredentials(r, true);
    }

    private static void createTestDomain(JenkinsRule r) throws IOException {
        // Create a test domain here, so there are at least 2 domains to cause the
        // Add credentials button to render a domain selector.
        SystemCredentialsProvider.ProviderImpl system = ExtensionList.lookup(CredentialsProvider.class).get(
                SystemCredentialsProvider.ProviderImpl.class);
        CredentialsStore systemStore = system.getStore(r.getInstance());
        String domainName = "test-domain";
        String domainDescription = "test description";
        systemStore.addDomain(new Domain(domainName, domainDescription, Collections.emptyList()));
    }

    private void createUsernamePasswordCredentials(JenkinsRule r, boolean clickGlobalDomain) throws Exception {
        String displayName = r.jenkins.getDescriptor(UsernamePasswordCredentialsImpl.class).getDisplayName();

        if (clickGlobalDomain) {
            // Ensure there is more than one domain so the UI exposes a domain selector.
            SystemCredentialsProvider.ProviderImpl system = ExtensionList.lookup(CredentialsProvider.class)
                .get(SystemCredentialsProvider.ProviderImpl.class);
            CredentialsStore systemStore = system.getStore(r.jenkins);
            systemStore.addDomain(new Domain("extra-domain", "Extra domain for UI test", Collections.emptyList()),
                Collections.emptyList());
        }

        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            HtmlPage htmlPage = wc.goTo("credentials/");

            HtmlButton button = htmlPage.querySelector(".jenkins-button--primary");
            HtmlElementUtil.click(button);

            if (clickGlobalDomain) {
                button = htmlPage.querySelector("button[data-type='credentials-add-store-item']");
                HtmlElementUtil.click(button);
            }

            HtmlForm form = htmlPage.getFormByName("dialog");

            DomNodeList<DomNode> allOptions = form.querySelectorAll(".jenkins-choice-list__item");
            boolean optionFound = selectOption(allOptions, displayName);
            assertTrue(optionFound, "The username password option was not found in the credentials type select");

            HtmlButton formSubmitButton = htmlPage.querySelector("#cr-dialog-next");
            HtmlElementUtil.click(formSubmitButton);

            HtmlForm newCredentialsForm = htmlPage.getFormByName("newCredentials");

            if (clickGlobalDomain) {
                // Best-effort: if the domain radio list is present, pick the global domain.
                // (When only one domain exists, Jenkins often omits the selector entirely.)
                DomNodeList<DomNode> radios = newCredentialsForm.querySelectorAll("input[type='radio'][name$='domain']");
                for (DomNode n : radios) {
                    if (n instanceof HtmlRadioButtonInput rbi && rbi.getValueAttribute() != null
                            && ("_".equals(rbi.getValueAttribute()) || "global".equalsIgnoreCase(rbi.getValueAttribute()))) {
                        rbi.setChecked(true);
                        break;
                    }
                }
            }

            newCredentialsForm.getInputByName("_.username").setValue("username");
            newCredentialsForm.getInputByName("_.password").setValue("password");

            List<UsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentialsInItemGroup(UsernamePasswordCredentials.class, null, ACL.SYSTEM2);
            assertThat(credentials, hasSize(0));

            button = newCredentialsForm.querySelector("#cr-dialog-submit");
            HtmlElementUtil.click(button);

            credentials = CredentialsProvider.lookupCredentialsInItemGroup(UsernamePasswordCredentials.class, null, ACL.SYSTEM2);
            assertThat(credentials, hasSize(1));

            UsernamePasswordCredentials passwordCredentials = credentials.get(0);
            String username = passwordCredentials.getUsername();
            assertEquals("username", username);
        }
    }

}
