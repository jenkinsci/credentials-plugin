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
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.DummyCredentials;
import com.cloudbees.plugins.credentials.impl.DummyLegacyCredentials;
import com.cloudbees.plugins.credentials.fingerprints.NodeCredentialsFingerprintFacet;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.Node.Mode;
import hudson.slaves.NodeProperty;
import hudson.model.User;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.security.ACL;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.getInstance()).isEmpty());
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
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.getInstance()).isEmpty());
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
    
    @Test
    public void testNoCredentialsUntilWeAddSomeViaStore() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.getInstance()).isEmpty());
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
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Jenkins.getInstance()).isEmpty());
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

    @Test
    public void testManageUserCredentials() throws IOException {
        final User alice = User.get("alice");
        DummyCredentials aliceCred1 = new DummyCredentials(CredentialsScope.USER, "aliceCred1", "pwd");
        DummyCredentials aliceCred2 = new DummyCredentials(CredentialsScope.USER, "aliceCred2", "pwd");
        DummyCredentials aliceCred3 = new DummyCredentials(CredentialsScope.USER, "aliceCred3", "pwd");
        
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        
        CredentialsStore userStore;
        SecurityContext ctx = ACL.impersonate(alice.impersonate());
        userStore = CredentialsProvider.lookupStores(alice).iterator().next();
        userStore.addCredentials(Domain.global(), aliceCred1);
        userStore.addCredentials(Domain.global(), aliceCred2);
    
        assertEquals(2, CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null, alice.impersonate(), Collections.<DomainRequirement>emptyList()).size());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, Jenkins.ANONYMOUS, Collections.<DomainRequirement>emptyList()).isEmpty());

        // Remove credentials
        userStore.removeCredentials(Domain.global(), aliceCred2);
        
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null, alice.impersonate(), Collections.<DomainRequirement>emptyList()).size());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, Jenkins.ANONYMOUS, Collections.<DomainRequirement>emptyList()).isEmpty());
   
        // Update credentials
        userStore.updateCredentials(Domain.global(), aliceCred1, aliceCred3);
        
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null, alice.impersonate(), Collections.<DomainRequirement>emptyList()).size());
        assertEquals(aliceCred3.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null, alice.impersonate(), Collections.<DomainRequirement>emptyList()).get(0).getUsername());
        SecurityContextHolder.setContext(ctx);
    }
    
    @Test
    public void testUpdateAndDeleteCredentials() throws IOException {
        FreeStyleProject project = r.createFreeStyleProject();
        DummyCredentials systemCred = new DummyCredentials(CredentialsScope.SYSTEM, "systemCred", "pwd");
        DummyCredentials systemCred2 = new DummyCredentials(CredentialsScope.SYSTEM, "systemCred2", "pwd");
        DummyCredentials globalCred = new DummyCredentials(CredentialsScope.GLOBAL, "globalCred", "pwd");
        DummyCredentials modCredential = new DummyCredentials(CredentialsScope.GLOBAL, "modCredential", "pwd");

        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
        
        // Add credentials
        store.addCredentials(Domain.global(), systemCred);
        store.addCredentials(Domain.global(), systemCred2);
        store.addCredentials(Domain.global(), globalCred);
        
        assertEquals(3, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).size());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).size());
        assertEquals(globalCred.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).get(0).getUsername());
    
        // Update credentials
        store.updateCredentials(Domain.global(), globalCred, modCredential);
        
        assertEquals(3, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).size());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).size());
        assertEquals(modCredential.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).get(0).getUsername());
        
        // Remove credentials
        store.removeCredentials(Domain.global(), systemCred2);
        
        assertEquals(2, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).size());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()).size());
    }

    @Test
    public void testHaveDummyCredentialsType() throws Exception {
        assertTrue(!CredentialsProvider.allCredentialsDescriptors().isEmpty());
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

    @Test
    public void testNodeCredentialFingerprintsAreRemovedForNonExistantNodes() throws Exception {
        // Create dummy credentials to use
        DummyCredentials globalCred = new DummyCredentials(CredentialsScope.GLOBAL, "globalCred", "pwd");
        // Find how many times this credential has been currently tracked
        int initialFingerprintSize = CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size();

        // Create a DumbSlave, this time don't add it to the model,
        // it should not be recorded
        DumbSlave nonAddedSlave = new DumbSlave("non-added-slave",
                "dummy", "/home/test/slave", "1", Node.Mode.NORMAL, "remote",
                new JNLPLauncher(),
                RetentionStrategy.INSTANCE, Collections.<NodeProperty<?>>emptyList());


        CredentialsProvider.track(nonAddedSlave, globalCred);
        assertEquals(initialFingerprintSize, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());


        // Create a DumbSlave to use, and add it to the Jenkins model, this
        // one should be recorded
        DumbSlave addedSlave = new DumbSlave("added-slave",
                "dummy", "/home/test/slave", "1", Node.Mode.NORMAL, "remote",
                new JNLPLauncher(),
                RetentionStrategy.INSTANCE, Collections.<NodeProperty<?>>emptyList());

        Jenkins.getInstance().addNode(addedSlave);
        CredentialsProvider.track(addedSlave, globalCred);
        assertEquals(initialFingerprintSize+1, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

        // Track the usage of the credential for a second time, this should
        // not increase the number of fingerprints further
        CredentialsProvider.track(addedSlave, globalCred);
        assertEquals(initialFingerprintSize+1, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

        // Remove the added slave from Jenkins, and register the
        // slave again to flush any mapped credentials for nodes that no-longer
        // exist - including this one
        Jenkins.getInstance().removeNode(addedSlave);
        CredentialsProvider.track(addedSlave, globalCred);
        assertEquals(initialFingerprintSize, CredentialsProvider.getOrCreateFingerprintOf(globalCred).getFacets().size());

    }

}
