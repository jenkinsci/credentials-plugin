/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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

package com.cloudbees.plugins.credentials.domains;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

public class DomainRestrictedCredentialsTest {
    // Allow for testing using JUnit4, instead of JUnit3.
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    public static class TestRestrictedCredentials extends BaseCredentials
            implements DomainRestrictedCredentials {
        public TestRestrictedCredentials(final boolean answer) {
            this.answer = answer;
        }

        public boolean matches(@NonNull List<DomainRequirement> requirements) {
            return answer;
        }

        private final boolean answer;
    }

    @Test
    public void testGetRestrictedCredentials() {
        Credentials trueCredentials = new TestRestrictedCredentials(true);
        Credentials falseCredentials = new TestRestrictedCredentials(false);

        SystemCredentialsProvider.getInstance().getCredentials()
                .add(trueCredentials);
        SystemCredentialsProvider.getInstance().getCredentials()
                .add(falseCredentials);

        Collection<Credentials> matchingCredentials =
                CredentialsProvider.lookupCredentialsInItemGroup(Credentials.class,
                        Jenkins.get(), ACL.SYSTEM2);

        assertThat(matchingCredentials, hasItems(trueCredentials));
        assertThat(matchingCredentials, not(hasItems(falseCredentials)));
    }
}
