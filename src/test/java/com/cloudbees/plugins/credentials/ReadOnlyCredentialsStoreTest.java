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

import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ModelObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReadOnlyCredentialsStoreTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private Domain testDomain = new Domain("some-domain", "some description", null);

    CredentialsStore credentialsStore = new ReadOnlyCredentialsStore() {
        @NonNull
        @Override
        public ModelObject getContext() {
            return j.getInstance();
        }

        @NonNull
        @Override
        public List<Credentials> getCredentials(@NonNull Domain domain) {
            ArrayList<Credentials> credentials = new ArrayList<>();
            credentials.add(createRandomCredentials("some-id"));
            return credentials;
        }
    };

    private Credentials createRandomCredentials(String id) {
        return new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                id,
                "some-desc",
                "username",
                "password"
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cannotAddCredentials() throws IOException {
        credentialsStore.addCredentials(testDomain, createRandomCredentials("new-id"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cannotUpdateCredentials() throws IOException {
        credentialsStore.updateCredentials(testDomain, createRandomCredentials("old-id"), createRandomCredentials("new-id"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cannotRemoveCredentials() throws IOException {
        credentialsStore.removeCredentials(testDomain, createRandomCredentials("old-id"));
    }
}
