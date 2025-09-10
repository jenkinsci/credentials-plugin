/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.DummyCredentials;
import com.cloudbees.plugins.credentials.impl.DummyIdCredentials;
import com.cloudbees.plugins.credentials.impl.DummyLegacyCredentials;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.User;
import hudson.security.ACLContext;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@WithJenkins
class CredentialsProviderTest {

    @Test
    void testNoCredentialsUntilWeAddSome(JenkinsRule r) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty(),
                "null item -> Root");
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());

        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, project).size());
        assertEquals(
		        "manchu",
		        CredentialsProvider.lookupCredentials(DummyCredentials.class, project).iterator().next().getUsername());

    }

    /**
     * Same test as {@link #testNoCredentialsUntilWeAddSome(JenkinsRule)} but using new APIs.
     */
    @Test
    void testNoCredentialsUntilWeAddSome2(JenkinsRule r) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2).isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertTrue(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());

        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());
        assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).size());
        assertEquals(
		        "manchu",
		        CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).iterator().next().getUsername());

    }

    @Test
    void testNoCredentialsUntilWeAddSomeViaStore(JenkinsRule r) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();
        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty(),
                "null item -> Root");
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());

        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, project).size());
        assertEquals(
		        "manchu",
		        CredentialsProvider.lookupCredentials(DummyCredentials.class, project).iterator().next().getUsername());

    }

    /**
     * Same test as {@link #testNoCredentialsUntilWeAddSomeViaStore(JenkinsRule)} but using new APIs.
     */
    @Test
    void testNoCredentialsUntilWeAddSomeViaStore2(JenkinsRule r) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2).isEmpty());
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();
        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertTrue(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());

        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty(),
                "null auth -> ACL.SYSTEM");

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, null, ACL.SYSTEM2).isEmpty(),
                "null item -> Root");
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());
        assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).size());
        assertEquals(
		        "manchu",
		        CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).iterator().next().getUsername());
    }

    @Test
    void testManageUserCredentials(JenkinsRule r) throws IOException {
        final User alice = User.getById("alice", true);
        DummyIdCredentials aliceCred1 = new DummyIdCredentials(null, CredentialsScope.USER, "aliceCred1", "pwd", "Cred 1");
        DummyIdCredentials aliceCred2 = new DummyIdCredentials(null, CredentialsScope.USER, "aliceCred2", "pwd", "Cred 2");
        DummyIdCredentials aliceCred3 = new DummyIdCredentials(aliceCred1.getId(), CredentialsScope.USER, "aliceCred3", "pwd", aliceCred1.getDescription());

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());

        try (ACLContext ctx = ACL.as(alice)) {
            CredentialsStore userStore = CredentialsProvider.lookupStores(alice).iterator().next();
            userStore.addCredentials(Domain.global(), aliceCred1);
            userStore.addCredentials(Domain.global(), aliceCred2);

            assertEquals(2, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, null, alice.impersonate2(), Collections.emptyList()).size());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, ACL.SYSTEM2, Collections.emptyList()).isEmpty());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, Jenkins.ANONYMOUS2, Collections.emptyList()).isEmpty());

            // Remove credentials
            userStore.removeCredentials(Domain.global(), aliceCred2);

            assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, null, alice.impersonate2(), Collections.emptyList()).size());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, ACL.SYSTEM2, Collections.emptyList()).isEmpty());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, Jenkins.ANONYMOUS2, Collections.emptyList()).isEmpty());

            // Update credentials
            userStore.updateCredentials(Domain.global(), aliceCred1, aliceCred3);

            assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, null, alice.impersonate2(), Collections.emptyList()).size());
            assertEquals(aliceCred3.getUsername(), CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, null, alice.impersonate2(), Collections.emptyList()).get(0).getUsername());
        }
    }

    @Test
    void testUpdateAndDeleteCredentials(JenkinsRule r) throws IOException {
        FreeStyleProject project = r.createFreeStyleProject();
        DummyIdCredentials systemCred = new DummyIdCredentials(null, CredentialsScope.SYSTEM, "systemCred", "pwd", "System 1");
        DummyIdCredentials systemCred2 = new DummyIdCredentials(null, CredentialsScope.SYSTEM, "systemCred2", "pwd", "System 2");
        DummyIdCredentials globalCred = new DummyIdCredentials(null, CredentialsScope.GLOBAL, "globalCred", "pwd", "Global 1");
        DummyIdCredentials modCredential = new DummyIdCredentials(globalCred.getId(), CredentialsScope.GLOBAL, "modCredential", "pwd", globalCred.getDescription());

        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();

        // Add credentials
        store.addCredentials(Domain.global(), systemCred);
        store.addCredentials(Domain.global(), systemCred2);
        store.addCredentials(Domain.global(), globalCred);

        assertEquals(3, CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, ACL.SYSTEM2, Collections.emptyList()).size());
        assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, project, ACL.SYSTEM2, Collections.emptyList()).size());
        assertEquals(globalCred.getUsername(), CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, project, ACL.SYSTEM2, Collections.emptyList()).get(0).getUsername());

        // Update credentials
        store.updateCredentials(Domain.global(), globalCred, modCredential);

        assertEquals(3, CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, ACL.SYSTEM2, Collections.emptyList()).size());
        assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, project, ACL.SYSTEM2, Collections.emptyList()).size());
        assertEquals(modCredential.getUsername(), CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, project, ACL.SYSTEM2, Collections.emptyList()).get(0).getUsername());

        // Remove credentials
        store.removeCredentials(Domain.global(), systemCred2);

        assertEquals(2, CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, ACL.SYSTEM2, Collections.emptyList()).size());
        assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, project, ACL.SYSTEM2, Collections.emptyList()).size());
    }

    @Test
    void testHaveDummyCredentialsType(JenkinsRule r) {
        assertFalse(CredentialsProvider.allCredentialsDescriptors().isEmpty());
        DummyCredentials.DescriptorImpl descriptor = null;
        for (Descriptor<Credentials> d : CredentialsProvider.allCredentialsDescriptors()) {
            if (d instanceof DummyCredentials.DescriptorImpl) {
                descriptor = (DummyCredentials.DescriptorImpl) d;
                break;
            }
        }
        assertNotNull(descriptor);
        assertNotNull(new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar").getDescriptor());
    }

    @Test
    void testLegacyCredentialMigration(JenkinsRule r) throws Exception {
        DummyLegacyCredentials legacyCredentials = new DummyLegacyCredentials(CredentialsScope.GLOBAL, "foo", "bar");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(legacyCredentials);
        oos.close();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Credentials c = (Credentials) ois.readObject();
	    assertInstanceOf(UsernamePasswordCredentials.class, c, "Resolved credentials are UsernamePasswordCredentials");
	    assertInstanceOf(DummyCredentials.class, c, "Resolved credentials are DummyCredentials");
        assertFalse(c instanceof DummyLegacyCredentials, "Resolved credentials are not DummyLegacyCredentials");

        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty(), "No credentials currently");
        SystemCredentialsProvider.getInstance().getCredentials().add(c);

        final List<DummyLegacyCredentials> resolved =
                CredentialsProvider.lookupCredentials(DummyLegacyCredentials.class);
        assertFalse(resolved.isEmpty(), "Have resolved credentials");
        DummyLegacyCredentials credentials = resolved.iterator().next();
        assertEquals(legacyCredentials.getScope(), credentials.getScope());
        assertEquals(legacyCredentials.getUsername(), credentials.getUsername());
        assertEquals(legacyCredentials.getPassword(), credentials.getPassword());
    }

    @SuppressWarnings("deprecated")
    @Test
    void testNodeCredentialFingerprintsAreRemovedForNonExistentNodes(JenkinsRule r) throws Exception {
        // Create dummy credentials to use
        DummyCredentials globalCred = new DummyCredentials(CredentialsScope.GLOBAL, "globalCred", "pwd");
        // Find how many times this credential has been currently tracked
        int initialFingerprintSize = CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size();

        // Create a DumbSlave, this time don't add it to the model,
        // it should not be recorded
        DumbSlave nonAddedSlave = new DumbSlave("non-added-slave",
                "dummy", "/home/test/agent", "1", Node.Mode.NORMAL, "remote",
                new JNLPLauncher(), // Use noarg ctor, see PR#228
                RetentionStrategy.INSTANCE, Collections.emptyList());


        CredentialsProvider.track(nonAddedSlave, globalCred);
        assertEquals(initialFingerprintSize, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());


        // Create a DumbSlave to use, and add it to the Jenkins model, this
        // one should be recorded
        DumbSlave addedSlave = new DumbSlave("added-agent",
                "dummy", "/home/test/agent", "1", Node.Mode.NORMAL, "remote",
                new JNLPLauncher(), // Use noarg ctor, see PR#228
                RetentionStrategy.INSTANCE, Collections.emptyList());

        Jenkins.get().addNode(addedSlave);
        CredentialsProvider.track(addedSlave, globalCred);
        assertEquals(initialFingerprintSize+1, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

        // Track the usage of the credential for a second time, this should
        // not increase the number of fingerprints further
        CredentialsProvider.track(addedSlave, globalCred);
        assertEquals(initialFingerprintSize+1, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

        // Remove the added agent from Jenkins, and track the non-added agent
        // to flush any mapped credentials for nodes that no longer exist.
        Jenkins.get().removeNode(addedSlave);
        CredentialsProvider.track(nonAddedSlave, globalCred);
        assertEquals(initialFingerprintSize, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

    }

    @Test
    void trackingOfFingerprintDependsOnConfiguration(JenkinsRule r) throws Exception {
        try {
            // Create dummy credentials to use
            DummyCredentials globalCred = new DummyCredentials(CredentialsScope.GLOBAL, "globalCred", "pwd");
            FreeStyleProject p1 = r.createFreeStyleProject();
            FreeStyleProject p2 = r.createFreeStyleProject();

            // Find how many times this credential has been currently tracked
            int initialFingerprintJobSize = CredentialsProvider.getOrCreateFingerprintOf(globalCred).getJobs().size();

            CredentialsProvider.track(p1, globalCred);
            assertEquals(initialFingerprintJobSize + 1, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

            CredentialsProviderManager manager = CredentialsProviderManager.getInstance();
            CredentialsProvider.FINGERPRINT_ENABLED = false;
            CredentialsProvider.track(p2, globalCred);
            // no effect
            assertEquals(initialFingerprintJobSize + 1, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

            CredentialsProvider.FINGERPRINT_ENABLED = true;

            CredentialsProvider.track(p2, globalCred);
            assertEquals(initialFingerprintJobSize + 2, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());
        }
        finally {
            // not necessary in default configuration but could be useful if someone runs the test with custom policy
            // and this test is failing at the middle
            CredentialsProvider.FINGERPRINT_ENABLED = true;
        }
    }

    @Test
    @Issue("JENKINS-65333")
    void insertionOrderLookupCredentials(JenkinsRule r) {
        assertThat(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2, Collections.emptyList()), hasSize(0));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("1", CredentialsScope.SYSTEM, "beta", "bar", "description 1"));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("2", CredentialsScope.SYSTEM, "alpha", "bar", "description 2"));
        List<DummyIdCredentials> credentials = CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, null, ACL.SYSTEM2, Collections.emptyList());
        assertThat(credentials, hasSize(2));
        // Insertion order
        assertThat(credentials.get(0).getUsername(), is("beta"));
        assertThat(credentials.get(1).getUsername(), is("alpha"));
    }

    @Test
    @Issue("JENKINS-65333")
    void credentialsSortedByNameInUI(JenkinsRule r) {
        assertThat(CredentialsProvider.lookupCredentialsInItem(Credentials.class, null, ACL.SYSTEM2, Collections.emptyList()), hasSize(0));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("1", CredentialsScope.SYSTEM, "beta", "bar", "description 1"));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("2", CredentialsScope.SYSTEM, "alpha", "bar", "description 2"));
        ListBoxModel options = CredentialsProvider.listCredentialsInItem(DummyIdCredentials.class, null, ACL.SYSTEM2, Collections.emptyList(), CredentialsMatchers.always());
        // Options are sorted by name
        assertThat(options, hasSize(2));
        assertThat(options.get(0).value, is("2"));
        assertThat(options.get(1).value, is("1"));
    }

    @Test
    @Issue("JENKINS-72611")
    void credentialsIdCannotBeUpdated(JenkinsRule r) {
        DummyIdCredentials cred1 = new DummyIdCredentials(null, CredentialsScope.GLOBAL, "cred1", "pwd", "Cred 1");
        DummyIdCredentials cred2 = new DummyIdCredentials(null, CredentialsScope.GLOBAL, "cred2", "pwd", "Cred 2");
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();

        assertThrows(IllegalArgumentException.class, () -> store.updateCredentials(Domain.global(), cred1, cred2));
    }
}
