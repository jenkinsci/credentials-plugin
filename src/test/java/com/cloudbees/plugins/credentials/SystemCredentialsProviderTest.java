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

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.impl.DummyCredentials;
import com.cloudbees.plugins.credentials.impl.DummyIdCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.User;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SystemCredentialsProviderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void saveAndLoad() throws Exception {
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        SystemCredentialsProvider.getInstance().save();
        assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
        SystemCredentialsProvider.getInstance().save();
        assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());
    }

    @Test
    public void malformedInput() throws Exception {
        assertTrue(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyCredentials(CredentialsScope.SYSTEM, "foo", "bar"));
        assertFalse(CredentialsProvider.lookupCredentials(Credentials.class).isEmpty());
        assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
        SystemCredentialsProvider.getInstance().save();
        assertFalse(new SystemCredentialsProvider().getCredentials().isEmpty());
        FileUtils.writeStringToFile(SystemCredentialsProvider.getConfigFile().getFile(), "<<barf>>", StandardCharsets.UTF_8);
        assertTrue(new SystemCredentialsProvider().getCredentials().isEmpty());
    }

    @Test
    public void smokes() {
        assertFalse(CredentialsProvider.allCredentialsDescriptors().isEmpty());
        assertNotNull(SystemCredentialsProvider.getInstance().getDescriptor());
        assertNotNull(SystemCredentialsProvider.getInstance().getCredentials());
    }

    @Test
    public void given_globalScopeCredential_when_builtAsSystem_then_credentialFound() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyIdCredentials("foo-manchu", CredentialsScope.GLOBAL, "foo", "manchu", "Dr. Fu Manchu")
        );
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu"));
        r.buildAndAssertSuccess(prj);
    }

    @Test
    public void given_systemScopeCredential_when_builtAsSystem_then_credentialNotFound() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyIdCredentials("foo-manchu", CredentialsScope.SYSTEM, "foo", "manchu", "Dr. Fu Manchu")
        );
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu"));
        r.assertBuildStatus(Result.FAILURE, prj.scheduleBuild2(0).get());
    }

    @Test
    public void given_globalScopeCredential_when_builtAsUserWithUseItem_then_credentialFound() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyIdCredentials("foo-manchu", CredentialsScope.GLOBAL, "foo", "manchu", "Dr. Fu Manchu")
        );
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu"));

        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);

        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        strategy.grant(CredentialsProvider.USE_ITEM).everywhere().to("bob");
        strategy.grant(Item.BUILD).everywhere().to("bob");
        strategy.grant(Computer.BUILD).everywhere().to("bob");

        r.jenkins.setAuthorizationStrategy(strategy);
        HashMap<String, Authentication> jobsToUsers = new HashMap<>();
        jobsToUsers.put(prj.getFullName(), User.getById("bob", true).impersonate());
        MockQueueItemAuthenticator authenticator = new MockQueueItemAuthenticator(jobsToUsers);

        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(authenticator);
        r.buildAndAssertSuccess(prj);
    }

    @Test
    public void given_globalScopeCredential_when_builtAsUserWithoutUseItem_then_credentialNotFound() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DummyIdCredentials("foo-manchu", CredentialsScope.GLOBAL, "foo", "manchu", "Dr. Fu Manchu")
        );
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildersList().add(new HasCredentialBuilder("foo-manchu"));

        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);

        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        strategy.grant(Item.BUILD).everywhere().to("bob");
        strategy.grant(Computer.BUILD).everywhere().to("bob");

        r.jenkins.setAuthorizationStrategy(strategy);
        HashMap<String, Authentication> jobsToUsers = new HashMap<>();
        jobsToUsers.put(prj.getFullName(), User.getById("bob", true).impersonate());
        MockQueueItemAuthenticator authenticator = new MockQueueItemAuthenticator(jobsToUsers);

        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(authenticator);
        r.assertBuildStatus(Result.FAILURE, prj.scheduleBuild2(0).get());
    }

    public static class HasCredentialBuilder extends Builder {

        private final String id;

        @DataBoundConstructor
        public HasCredentialBuilder(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            IdCredentials credentials = CredentialsProvider.findCredentialById(id, IdCredentials.class, build);
            if (credentials == null) {
                listener.getLogger().printf("Could not find any credentials with id %s%n", id);
                build.setResult(Result.FAILURE);
                return false;
            } else {
                listener.getLogger().printf("Found %s credentials with id %s%n", CredentialsNameProvider.name(credentials), id);
                return true;
            }
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return "Probe credentials exist";
            }
        }
    }

}
