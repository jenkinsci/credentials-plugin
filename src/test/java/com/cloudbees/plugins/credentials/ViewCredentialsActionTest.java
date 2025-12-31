package com.cloudbees.plugins.credentials;

import static com.cloudbees.plugins.credentials.XmlMatchers.isSimilarToIgnoringPrivateAttrs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.htmlunit.WebResponse;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.ExtensionList;
import hudson.model.FreeStyleProject;

import org.jvnet.hudson.test.MockFolder;

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
                + "Credentials that should be available irrespective of domain specification to requirements "
                + "matching."
                + "</description>"
                + "<displayName>Global credentials (unrestricted)</displayName>"
                + "<fullDisplayName>System » Global credentials (unrestricted)</fullDisplayName>"
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
                + "Credentials that should be available irrespective of domain specification to requirements "
                + "matching."
                + "</description>"
                + "<displayName>Global credentials (unrestricted)</displayName>"
                + "<fullDisplayName>System » Global credentials (unrestricted)</fullDisplayName>"
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

}
