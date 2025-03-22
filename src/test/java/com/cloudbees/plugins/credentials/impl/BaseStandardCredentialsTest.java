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
import org.htmlunit.html.HtmlPage;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static hudson.util.FormValidation.Kind.ERROR;
import static hudson.util.FormValidation.Kind.OK;
import static hudson.util.FormValidation.Kind.WARNING;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class BaseStandardCredentialsTest {

    @Test
    void doCheckIdSyntax(JenkinsRule r) {
        assertDoCheckId(r, "", r.jenkins, OK);
        assertDoCheckId(r, /* random UUID */IdCredentials.Helpers.fixEmptyId(null), r.jenkins, OK);
        assertDoCheckId(r, "blah-blah", r.jenkins, OK);
        assertDoCheckId(r, "definitely\nscary", r.jenkins, ERROR);
    }

    @Test
    void doCheckIdDuplication(JenkinsRule r) throws Exception {
        // First set up two users, each of which has an existing credentials named ‘per-user’.
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        final User alice = User.getById("alice", true);
        try (ACLContext ctx = ACL.as(alice)) {
            CredentialsStore store = lookupStore(alice);
            addCreds(store, CredentialsScope.USER, "alice");
            addCreds(store, CredentialsScope.USER, "per-user");
        }
        User bob = User.getById("bob", true);
        try (ACLContext ctx = ACL.as(bob)) {
            CredentialsStore store = lookupStore(bob);
            addCreds(store, CredentialsScope.USER, "bob");
            addCreds(store, CredentialsScope.USER, "per-user");
        }

        // Now set up a folder tree with some masking of credentials.
        CredentialsStore store = lookupStore(r.jenkins);
        addCreds(store, CredentialsScope.GLOBAL, "masked");
        addCreds(store, CredentialsScope.GLOBAL, "root");
        addCreds(store, CredentialsScope.SYSTEM, "rootSystem");
        final MockFolder top = r.jenkins.createProject(MockFolder.class, "top");
        store = lookupStore(top);
        addCreds(store, CredentialsScope.GLOBAL, "masked");
        addCreds(store, CredentialsScope.GLOBAL, "top");
        final MockFolder bottom = top.createProject(MockFolder.class, "bottom");
        store = lookupStore(bottom);
        addCreds(store, CredentialsScope.GLOBAL, "masked");
        addCreds(store, CredentialsScope.GLOBAL, "bottom");

        // Now as Alice we expect that duplications are checked in the current and parent contexts, plus the user if distinct.
        try (ACLContext ctx = ACL.as(alice)) {
            assertDoCheckId(r, "root", r.jenkins, ERROR);
            assertDoCheckId(r, "rootSystem", r.jenkins, ERROR);
            assertDoCheckId(r, "masked", r.jenkins, ERROR);
            assertDoCheckId(r, "top", r.jenkins, OK);
            assertDoCheckId(r, "bottom", r.jenkins, OK);
            assertDoCheckId(r, "alice", r.jenkins, WARNING);
            assertDoCheckId(r, "bob", r.jenkins, OK);
            assertDoCheckId(r, "per-user", r.jenkins, WARNING);
            assertDoCheckId(r, "root", top, WARNING);
            assertDoCheckId(r, "rootSystem", top, OK); // not exported to child contexts, so not a duplicate
            assertDoCheckId(r, "masked", top, ERROR);
            assertDoCheckId(r, "top", top, ERROR);
            assertDoCheckId(r, "bottom", top, OK);
            assertDoCheckId(r, "alice", top, WARNING);
            assertDoCheckId(r, "bob", top, OK);
            assertDoCheckId(r, "per-user", top, WARNING);
            assertDoCheckId(r, "root", bottom, WARNING);
            assertDoCheckId(r, "rootSystem", bottom, OK); // not exported to child contexts, so not a duplicate
            assertDoCheckId(r, "masked", bottom, ERROR);
            assertDoCheckId(r, "top", bottom, WARNING);
            assertDoCheckId(r, "bottom", bottom, ERROR);
            assertDoCheckId(r, "alice", bottom, WARNING);
            assertDoCheckId(r, "bob", bottom, OK);
            assertDoCheckId(r, "per-user", bottom, WARNING);
            assertDoCheckId(r, "root", alice, WARNING);
            assertDoCheckId(r, "rootSystem", alice, OK); // not exported to child contexts, so not a duplicate
            assertDoCheckId(r, "masked", alice, WARNING);
            assertDoCheckId(r, "top", alice, OK);
            assertDoCheckId(r, "bottom", alice, OK);
            assertDoCheckId(r, "alice", alice, ERROR);
            assertDoCheckId(r, "bob", alice, OK);
            assertDoCheckId(r, "per-user", alice, ERROR);
        }

        // TODO could test the case that alice has Item.READ but not CredentialsProvider.VIEW on a folder, and mocks a web request passing that folder as context
    }

    @Test
    void noIDValidationMessageOnCredentialsUpdate(JenkinsRule r) throws Exception {
        // create credentials with ID test
        CredentialsStore store = lookupStore(r.jenkins);
        addCreds(store, CredentialsScope.GLOBAL, "test");
        // check there is no validation message about a duplicated ID when updating
        JenkinsRule.WebClient webClient = r.createWebClient();
        HtmlPage htmlPage = webClient.goTo("credentials/store/system/domain/_/credential/test/update");
        assertThat(htmlPage.asNormalizedText(), not(containsString("This ID is already in use")));
    }

    private static CredentialsStore lookupStore(ModelObject object) {
        Iterator<CredentialsStore> stores = CredentialsProvider.lookupStores(object).iterator();
        assertTrue(stores.hasNext());
        CredentialsStore store = stores.next();
        assertEquals(object, store.getContext(), "we got the expected store");
        return store;
    }

    private static void addCreds(CredentialsStore store, CredentialsScope scope, String id) throws Exception {
        // For purposes of this test we do not care about domains.
        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(scope, id, null, "x", "y"));
    }

    private static void assertDoCheckId(JenkinsRule r, String id, ModelObject context, FormValidation.Kind expectedResult) {
        assertEquals(expectedResult, r.jenkins.getDescriptorByType(UsernamePasswordCredentialsImpl.DescriptorImpl.class).doCheckId(

                context, id
        ).kind);
    }

}
