/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc..
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

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.Builder;
import hudson.util.Secret;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Test to check if user scoped credentials can be retrieved from credential store.
 * @author Peter Skopek <pskopek@redhat.com>
 */
public class FindUserScopeCredentialsByIdTest {

    private static String testedCredentialId = "my_db_pwd";
    private static String username = "my_user_name";
    private static String password = "my_secret";
    private static final Logger logger = LoggerFactory.getLogger(FindUserScopeCredentialsByIdTest.class);

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        final User alice = User.get("alice");
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        CredentialsStore userStore;
        SecurityContext ctx = ACL.impersonate(alice.impersonate());
        userStore = CredentialsProvider.lookupStores(alice).iterator().next();
        userStore.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(CredentialsScope.USER, testedCredentialId,"Test user credential", username, password));
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    public void findUserCredentialsById() throws Exception {
        final User alice = User.get("alice");
        SecurityContext ctx = ACL.impersonate(alice.impersonate());
        FreeStyleProject project = r.createFreeStyleProject();
        project.getBuildersList().add(new PasswordBuildStep(testedCredentialId));
        FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause(), new Action[0]).get();
        logger.debug(JenkinsRule.getLog(build));
        r.assertBuildStatus(Result.SUCCESS, build);
        r.assertLogContains("username: " + username, build);
        r.assertLogContains("Credentials with credentialId '" + testedCredentialId + "', username: " + username, build);
        r.assertLogContains("Extracted secret: " + password, build);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    public void userCredentialsByIdOverride() throws Exception {
        // create credential store entry with the same Id as but different context
        SystemCredentialsProvider.ProviderImpl system = ExtensionList.lookup(CredentialsProvider.class).get(
                SystemCredentialsProvider.ProviderImpl.class);
        assert system != null;
        CredentialsStore systemStore = system.getStore(r.getInstance());
        assert systemStore != null;
        systemStore.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, testedCredentialId,"Test user credential", "glob-" +  username, "glob-" + password));
        findUserCredentialsById();
    }

    public static class PasswordBuildStep extends Builder {

        private final String credentialId;

        PasswordBuildStep(String credentialId) {
            this.credentialId = credentialId;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            StandardUsernamePasswordCredentials credentials =
                    CredentialsProvider.findCredentialById(this.credentialId, StandardUsernamePasswordCredentials.class, build);
            if (credentials == null) {
                listener.getLogger().printf("Could not find credentials with credentialId '%s'%n", credentialId);
                build.setResult(Result.FAILURE);
            } else {
                listener.getLogger().printf("Credentials with credentialId '%s', username: %s%n", credentialId, credentials.getUsername());
                Secret password = credentials.getPassword();
                listener.getLogger().printf("Extracted secret: %s%n", password.getPlainText());
            }
            return true;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Builder> {

            @Nonnull
            @Override
            public String getDisplayName() {
                return "Password build step";
            }
        }
    }

}
