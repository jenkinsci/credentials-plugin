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
import com.cloudbees.plugins.credentials.impl.DummyLegacyCredentials;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

public class CredentialsProviderTest extends HudsonTestCase {

    public void testNoCredentialsUntilWeAddSome() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.getInstance()).isEmpty());
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
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.getInstance()).isEmpty());
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

    public void testNoCredentialsUntilWeAddSomeViaStore() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.getInstance()).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Item) null).isEmpty());
        assertFalse("null item -> Root",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (ItemGroup) null).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, project).isEmpty());

        store.addCredentials(Domain.global(), new DummyCredentials(CredentialsScope.GLOBAL, "manchu", "bar"));

        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, ACL.SYSTEM).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.ANONYMOUS).isEmpty());
        assertFalse("null auth -> ACL.SYSTEM",
                CredentialsProvider.lookupCredentials(DummyCredentials.class, (Authentication) null).isEmpty());

        assertFalse(CredentialsProvider.lookupCredentials(DummyCredentials.class, Hudson.getInstance()).isEmpty());
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

}
