/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.security.ACL;
import hudson.util.FormValidation;
import static hudson.util.FormValidation.Kind.*;
import java.io.IOException;
import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

public class BaseStandardCredentialsTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void doCheckIdSyntax() throws Exception {
        assertDoCheckId("", r.jenkins, OK);
        assertDoCheckId(/* random UUID */IdCredentials.Helpers.fixEmptyId(null), r.jenkins, OK);
        assertDoCheckId("blah-blah", r.jenkins, OK);
        assertDoCheckId("definitely\nscary", r.jenkins, ERROR);
    }

    @Test public void doCheckIdDuplication() throws Exception {
        // First set up two users, each of which has an existing credentials named ‘per-user’.
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        final User alice = User.get("alice");
        CredentialsStore store = lookupStore(alice);
        addCreds(store, CredentialsScope.USER, "alice");
        addCreds(store, CredentialsScope.USER, "per-user");
        User bob = User.get("bob");
        store = lookupStore(bob);
        addCreds(store, CredentialsScope.USER, "bob");
        addCreds(store, CredentialsScope.USER, "per-user");

        // Now set up a folder tree with some masking of credentials.
        store = lookupStore(r.jenkins);
        addCreds(store, CredentialsScope.GLOBAL, "masked");
        addCreds(store, CredentialsScope.GLOBAL, "root");
        // TODO not currently testing SYSTEM; should this make any difference to behavior here?
        final MockFolder top = r.jenkins.createProject(MockFolder.class, "top");
        store = lookupStore(top);
        addCreds(store, CredentialsScope.GLOBAL, "masked");
        addCreds(store, CredentialsScope.GLOBAL, "top");
        final MockFolder bottom = top.createProject(MockFolder.class, "bottom");
        store = lookupStore(bottom);
        addCreds(store, CredentialsScope.GLOBAL, "masked");
        addCreds(store, CredentialsScope.GLOBAL, "bottom");

        // Now as Alice we expect that duplications are checked in the current and parent contexts, plus the user if distinct.
        ACL.impersonate(alice.impersonate(), new Runnable() {
            public void run() {
                assertDoCheckId("root", r.jenkins, ERROR);
                assertDoCheckId("masked", r.jenkins, ERROR);
                assertDoCheckId("top", r.jenkins, OK);
                assertDoCheckId("bottom", r.jenkins, OK);
                assertDoCheckId("alice", r.jenkins, WARNING);
                assertDoCheckId("bob", r.jenkins, OK);
                assertDoCheckId("per-user", r.jenkins, WARNING);
                assertDoCheckId("root", top, WARNING);
                assertDoCheckId("masked", top, ERROR);
                assertDoCheckId("top", top, ERROR);
                assertDoCheckId("bottom", top, OK);
                assertDoCheckId("alice", top, WARNING);
                assertDoCheckId("bob", top, OK);
                assertDoCheckId("per-user", top, WARNING);
                assertDoCheckId("root", bottom, WARNING);
                assertDoCheckId("masked", bottom, ERROR);
                assertDoCheckId("top", bottom, WARNING);
                assertDoCheckId("bottom", bottom, ERROR);
                assertDoCheckId("alice", bottom, WARNING);
                assertDoCheckId("bob", bottom, OK);
                assertDoCheckId("per-user", bottom, WARNING);
                assertDoCheckId("root", alice, WARNING);
                assertDoCheckId("masked", alice, WARNING);
                assertDoCheckId("top", alice, OK);
                assertDoCheckId("bottom", alice, OK);
                assertDoCheckId("alice", alice, ERROR);
                assertDoCheckId("bob", alice, OK);
                assertDoCheckId("per-user", alice, ERROR);
            }
        });

        // TODO could test the case that alice has Item.READ but not CredentialsProvider.VIEW on a folder, and mocks a web request passing that folder as context
    }
    
    private static CredentialsStore lookupStore(ModelObject object) {
        Iterator<CredentialsStore> stores = CredentialsProvider.lookupStores(object).iterator();
        assertTrue(stores.hasNext());
        CredentialsStore store = stores.next();
        assertEquals("we got the expected store", object, store.getContext());
        return store;
    }

    private static void addCreds(CredentialsStore store, CredentialsScope scope, String id) throws IOException {
        // For purposes of this test we do not care about domains.
        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(scope, id, null, "x", "y"));
    }

    private void assertDoCheckId(String id, ModelObject context, FormValidation.Kind expectedResult) {
        assertEquals(expectedResult, r.jenkins.getDescriptorByType(UsernamePasswordCredentialsImpl.DescriptorImpl.class).doCheckId(id, context).kind);
    }

}
