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
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CredentialsProviderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    @Test
    public void testNoCredentialsUntilWeAddSome() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());

        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());
        assertEquals(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).size(), 1);
        assertEquals(
                CredentialsProvider.lookupCredentials(DummyCredentials.class, project).iterator().next().getUsername(),
                "manchu");

    }

    /**
     * Same test as {@link #testNoCredentialsUntilWeAddSome()} but using new APIs.
     */
    @Test
    public void testNoCredentialsUntilWeAddSome2() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, (ItemGroup) null, ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());

        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, (ItemGroup) null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());
        assertEquals(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).size(), 1);
        assertEquals(
                CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).iterator().next().getUsername(),
                "manchu");

    }
    
    @Test
    public void testNoCredentialsUntilWeAddSomeViaStore() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();
        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());

        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (org.acegisecurity.Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.get()).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());
        assertEquals(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).size(), 1);
        assertEquals(
                CredentialsProvider.lookupCredentials(DummyCredentials.class, project).iterator().next().getUsername(),
                "manchu");

    }

    /**
     * Same test as {@link #testNoCredentialsUntilWeAddSomeViaStore()} but using new APIs.
     */
    @Test
    public void testNoCredentialsUntilWeAddSomeViaStore2() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();
        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, (ItemGroup) null, ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());

        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), Jenkins.ANONYMOUS2).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, Jenkins.get(), ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, (Item) null, ACL.SYSTEM2).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentialsInItemGroup(DummyCredentials.class, (ItemGroup) null, ACL.SYSTEM2).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).isEmpty());
        assertEquals(CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).size(), 1);
        assertEquals(
                CredentialsProvider.lookupCredentialsInItem(DummyCredentials.class, project, ACL.SYSTEM2).iterator().next().getUsername(),
                "manchu");
    }

    @Test
    public void testManageUserCredentials() throws IOException {
        final User alice = User.getById("alice", true);
        DummyIdCredentials aliceCred1 = new DummyIdCredentials(null, CredentialsScope.USER, "aliceCred1", "pwd", "Cred 1");
        DummyIdCredentials aliceCred2 = new DummyIdCredentials(null, CredentialsScope.USER, "aliceCred2", "pwd", "Cred 2");
        DummyIdCredentials aliceCred3 = new DummyIdCredentials(aliceCred1.getId(), CredentialsScope.USER, "aliceCred3", "pwd", aliceCred1.getDescription());
        
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        
        try (ACLContext ctx = ACL.as(alice)) {
            CredentialsStore userStore = CredentialsProvider.lookupStores(alice).iterator().next();
            userStore.addCredentials(Domain.global(), aliceCred1);
            userStore.addCredentials(Domain.global(), aliceCred2);

            assertEquals(2, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, (Item) null, alice.impersonate2(), Collections.emptyList()).size());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, ACL.SYSTEM2, Collections.emptyList()).isEmpty());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, Jenkins.ANONYMOUS2, Collections.emptyList()).isEmpty());

            // Remove credentials
            userStore.removeCredentials(Domain.global(), aliceCred2);

            assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, (Item) null, alice.impersonate2(), Collections.emptyList()).size());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, ACL.SYSTEM2, Collections.emptyList()).isEmpty());
            assertTrue(CredentialsProvider.lookupCredentialsInItemGroup(DummyIdCredentials.class, r.jenkins, Jenkins.ANONYMOUS2, Collections.emptyList()).isEmpty());

            // Update credentials
            userStore.updateCredentials(Domain.global(), aliceCred1, aliceCred3);

            assertEquals(1, CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, (Item) null, alice.impersonate2(), Collections.emptyList()).size());
            assertEquals(aliceCred3.getUsername(), CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, (Item) null, alice.impersonate2(), Collections.emptyList()).get(0).getUsername());
        }
    }
    
    @Test
    public void testUpdateAndDeleteCredentials() throws IOException {
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
    public void testHaveDummyCredentialsType() {
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
    public void testLegacyCredentialMigration() throws Exception {
        DummyLegacyCredentials legacyCredentials = new DummyLegacyCredentials(CredentialsScope.GLOBAL, "foo", "bar");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(legacyCredentials);
        oos.close();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Credentials c = (Credentials) ois.readObject();
        assertTrue("Resolved credentials are UsernamePasswordCredentials", c instanceof UsernamePasswordCredentials);
        assertTrue("Resolved credentials are DummyCredentials", c instanceof DummyCredentials);
        assertFalse("Resolved credentials are not DummyLegacyCredentials", c instanceof DummyLegacyCredentials);

        assertTrue("No credentials currently", CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(c);

        final List<DummyLegacyCredentials> resolved =
                CredentialsProvider.lookupCredentials(DummyLegacyCredentials.class);
        assertFalse("Have resolved credentials", resolved.isEmpty());
        DummyLegacyCredentials r = resolved.iterator().next();
        assertEquals(legacyCredentials.getScope(), r.getScope());
        assertEquals(legacyCredentials.getUsername(), r.getUsername());
        assertEquals(legacyCredentials.getPassword(), r.getPassword());
    }

    @SuppressWarnings("deprecated")
    @Test
    public void testNodeCredentialFingerprintsAreRemovedForNonExistentNodes() throws Exception {
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
    public void trackingOfFingerprintDependsOnConfiguration() throws Exception {
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
    public void insertionOrderLookupCredentials() {
        assertThat(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2, Collections.emptyList()), hasSize(0));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("1", CredentialsScope.SYSTEM, "beta", "bar", "description 1"));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("2", CredentialsScope.SYSTEM, "alpha", "bar", "description 2"));
        List<DummyIdCredentials> credentials = CredentialsProvider.lookupCredentialsInItem(DummyIdCredentials.class, (Item) null, ACL.SYSTEM2, Collections.emptyList());
        assertThat(credentials, hasSize(2));
        // Insertion order
        assertThat(credentials.get(0).getUsername(), is("beta"));
        assertThat(credentials.get(1).getUsername(), is("alpha"));
    }

    @Test
    @Issue("JENKINS-65333")
    public void credentialsSortedByNameInUI() {
        assertThat(CredentialsProvider.lookupCredentialsInItem(Credentials.class, (Item) null, ACL.SYSTEM2, Collections.emptyList()), hasSize(0));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("1", CredentialsScope.SYSTEM, "beta", "bar", "description 1"));
        SystemCredentialsProvider.getInstance().getCredentials().add(new DummyIdCredentials("2", CredentialsScope.SYSTEM, "alpha", "bar", "description 2"));
        ListBoxModel options = CredentialsProvider.listCredentialsInItem(DummyIdCredentials.class, (Item) null, ACL.SYSTEM2, Collections.emptyList(), CredentialsMatchers.always());
        // Options are sorted by name
        assertThat(options, hasSize(2));
        assertThat(options.get(0).value, is("2"));
        assertThat(options.get(1).value, is("1"));
    }

    @Test
    @Issue("JENKINS-72611")
    public void credentialsIdCannotBeUpdated() {
        DummyIdCredentials cred1 = new DummyIdCredentials(null, CredentialsScope.GLOBAL, "cred1", "pwd", "Cred 1");
        DummyIdCredentials cred2 = new DummyIdCredentials(null, CredentialsScope.GLOBAL, "cred2", "pwd", "Cred 2");
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.get()).iterator().next();

        Assert.assertThrows(IllegalArgumentException.class, () -> store.updateCredentials(Domain.global(), cred1, cred2));
    }
}
