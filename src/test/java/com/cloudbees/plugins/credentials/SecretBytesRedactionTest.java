package com.cloudbees.plugins.credentials;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Item;
import hudson.model.ModelObject;
import java.util.Base64;
import java.util.Iterator;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SecretBytesRedactionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testRedaction() throws Exception {
        final String usernamePasswordPassword = "thisisthe_theuserpassword";
        final SecretBytes secretBytes = SecretBytes.fromString("thisis_theTestData");

        Item.EXTENDED_READ.setEnabled(true);

        final Folder folder = j.jenkins.createProject(Folder.class, "F");
        final CredentialsStore store = lookupStore(folder);
        final UsernamePasswordCredentialsImpl usernamePasswordCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "passwordid", null, "theusername", usernamePasswordPassword);
        store.addCredentials(Domain.global(), usernamePasswordCredentials);
        store.addCredentials(Domain.global(), new SecretBytesCredential(CredentialsScope.GLOBAL, "certid", "thedesc", secretBytes));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("alice").grant(Item.READ, Item.EXTENDED_READ, Jenkins.READ).everywhere().to("bob"));

        try (JenkinsRule.WebClient webClient = j.createWebClient().login("alice")) {
            final Page page = webClient.goTo("job/F/config.xml", "application/xml");
            final String content = page.getWebResponse().getContentAsString();
            assertThat(content, containsString(usernamePasswordCredentials.getPassword().getEncryptedValue()));
            assertThat(content, containsString(Base64.getEncoder().encodeToString(secretBytes.getEncryptedData())));
        }
        try (JenkinsRule.WebClient webClient = j.createWebClient().login("bob")) {
            final Page page = webClient.goTo("job/F/config.xml", "application/xml");
            final String content = page.getWebResponse().getContentAsString();
            assertThat(content, not(containsString(usernamePasswordCredentials.getPassword().getEncryptedValue())));
            assertThat(content, not(containsString(Base64.getEncoder().encodeToString(secretBytes.getEncryptedData()))));
            assertThat(content, containsString("<password>********</password>"));
            assertThat(content, containsString("<mySecretBytes>********</mySecretBytes>"));
        }
    }

    // Stolen from BaseStandardCredentialsTest
    private static CredentialsStore lookupStore(ModelObject object) {
        Iterator<CredentialsStore> stores = CredentialsProvider.lookupStores(object).iterator();
        assertTrue(stores.hasNext());
        CredentialsStore store = stores.next();
        assertEquals("we got the expected store", object, store.getContext());
        return store;
    }

    // This would be nicer with a real credential like `FileCredentialsImpl` but another test falls over if we add `plain-credentials` to the test scope
    public static class SecretBytesCredential extends BaseStandardCredentials {
        private final SecretBytes mySecretBytes;

        public SecretBytesCredential(CredentialsScope scope, String id, String description, SecretBytes bytes) {
            super(scope, id, description);
            this.mySecretBytes = bytes;
        }
    }
}
