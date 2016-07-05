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
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.xml.sax.SAXException;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class CredentialsUnavailableExceptionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    private CredentialsStore systemStore;

    @Before
    public void setUp() throws Exception {
        SystemCredentialsProvider.ProviderImpl system = ExtensionList.lookup(CredentialsProvider.class).get(
                SystemCredentialsProvider.ProviderImpl.class);

        systemStore = system.getStore(r.getInstance());

        List<Domain> domainList = new ArrayList<Domain>(systemStore.getDomains());
        domainList.remove(Domain.global());
        for (Domain d : domainList) {
            systemStore.removeDomain(d);
        }

        List<Credentials> credentialsList = new ArrayList<Credentials>(systemStore.getCredentials(Domain.global()));
        for (Credentials c : credentialsList) {
            systemStore.removeCredentials(Domain.global(), c);
        }
    }

    @Test
    public void buildFailure() throws Exception {
        systemStore.addCredentials(Domain.global(), new UsernameUnavailablePasswordImpl("buildFailure", "test", "foo"));
        FreeStyleProject project = r.createFreeStyleProject();
        project.getBuildersList().add(new PasswordBuildStep("buildFailure"));
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        this.r.assertBuildStatus(Result.FAILURE, build);
        this.r.assertLogContains("username: foo", build);
        this.r.assertLogContains("Property 'password' is currently unavailable", build);
        this.r.assertLogNotContains("Could not find", build);
        this.r.assertLogNotContains("Extracted secret", build);
    }

    @Test
    public void checkoutFailure() throws Exception {
        systemStore.addCredentials(Domain.global(), new UsernameUnavailablePasswordImpl("checkoutFailure", "test", "bar"));
        FreeStyleProject project = r.createFreeStyleProject();
        project.setScm(new PasswordSCM("checkoutFailure"));
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        this.r.assertBuildStatus(Result.FAILURE, build);
        this.r.assertLogContains("user: bar", build);
        this.r.assertLogContains("Property 'password' is currently unavailable", build);
        this.r.assertLogNotContains("Could not find", build);
        this.r.assertLogNotContains("Checking out with password", build);
    }

    @Test
    public void pollingFailure() throws Exception {
        UsernameUnavailablePasswordImpl credentials =
                new UsernameUnavailablePasswordImpl("pollingFailure", "test", "manchu");
        systemStore.addCredentials(Domain.global(),
                credentials);
        FreeStyleProject project = r.createFreeStyleProject();
        project.setQuietPeriod(0);
        // ensure we have a build so that polling doesn't trigger a build by accident
        r.buildAndAssertSuccess(project);
        project.setScm(new PasswordSCM("pollingFailure"));
        SCMTrigger trigger = new SCMTrigger("* * * * *");
        project.addTrigger(trigger);
        trigger.start(project, true);
        GregorianCalendar cal = new GregorianCalendar();
        cal.add(Calendar.MINUTE, 1);
        int number = project.getLastBuild().getNumber();
        // now we trigger polling the first time...
        Trigger.checkTriggers(cal);
        // we should get here without an exception being thrown or else core is handling the runtime exceptions poorly
        r.waitUntilNoActivity();
        assertThat(project.getAction(SCMTrigger.SCMAction.class).getLog(), allOf(
                containsString("Checking remote revision as user: manchu"),
                containsString("Property 'password' is currently unavailable")));
        assertThat("No new builds", project.getLastBuild().getNumber(), is(number));
        cal.add(Calendar.MINUTE, 1);
        // now we trigger polling the second time to verify that polling is not stuck
        Trigger.checkTriggers(cal);
        r.waitUntilNoActivity();
        assertThat(project.getAction(SCMTrigger.SCMAction.class).getLog(), allOf(
                containsString("Checking remote revision as user: manchu"),
                containsString("Property 'password' is currently unavailable")));
        assertThat("No new builds", project.getLastBuild().getNumber(), is(number));
        systemStore.updateCredentials(Domain.global(), credentials, new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "pollingFailure", "working", "manchu", "secret"));
        // now we trigger polling the third time... now with working credentials
        Trigger.checkTriggers(cal);
        r.waitUntilNoActivity();
        assertThat(project.getAction(SCMTrigger.SCMAction.class).getLog(), allOf(
                containsString("Checking remote revision as user: manchu"),
                not(containsString("Property 'password' is currently unavailable")),
                containsString("Checking remote revision with password: secret")));
        assertThat("New build", project.getLastBuild().getNumber(), greaterThan(number));
    }

    public static class PasswordSCM extends SCM {

        private final String id;

        public PasswordSCM(String id) {
            this.id = id;
        }

        @Override
        public boolean supportsPolling() {
            return true;
        }

        @Override
        public boolean requiresWorkspaceForPolling() {
            return false;
        }

        @Override
        public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace,
                             @Nonnull TaskListener listener, @javax.annotation.CheckForNull File changelogFile,
                             @javax.annotation.CheckForNull SCMRevisionState baseline)
                throws IOException, InterruptedException {
            StandardUsernamePasswordCredentials credentials =
                    CredentialsProvider.findCredentialById(this.id, StandardUsernamePasswordCredentials.class, build);
            if (credentials == null) {
                listener.getLogger().printf("Could not find credentials with id '%s'%n", id);
                build.setResult(Result.UNSTABLE);
            } else {
                listener.getLogger().printf("Checking out as user: %s%n", credentials.getUsername());
                Secret password = credentials.getPassword();
                listener.getLogger().printf("Checking out with password: %s%n", password.getPlainText());
            }
        }

        @Override
        public SCMRevisionState calcRevisionsFromBuild(@Nonnull Run<?, ?> build, @Nullable FilePath workspace,
                                                       @Nullable Launcher launcher, @Nonnull TaskListener listener)
                throws IOException, InterruptedException {
            return new SCMRevisionState() {
                @Override
                public String getIconFileName() {
                    return "mock.png";
                }

                @Override
                public String getDisplayName() {
                    return "mock";
                }

                @Override
                public String getUrlName() {
                    return "mock";
                }
            };
        }

        @Override
        public PollingResult compareRemoteRevisionWith(@Nonnull Job<?, ?> project, @Nullable Launcher launcher,
                                                       @Nullable FilePath workspace, @Nonnull TaskListener listener,
                                                       @Nonnull SCMRevisionState baseline)
                throws IOException, InterruptedException {
            StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project,
                            CredentialsProvider.getDefaultAuthenticationOf(project),
                            Collections.<DomainRequirement>emptyList()), CredentialsMatchers.withId(id));
            if (credentials == null) {
                throw new IOException(String.format("Could not find credentials with id '%s'", id));
            } else {
                listener.getLogger().printf("Checking remote revision as user: %s%n", credentials.getUsername());
                Secret password = credentials.getPassword();
                listener.getLogger()
                        .printf("Checking remote revision with password: %s%n", password.getPlainText());
            }
            return PollingResult.SIGNIFICANT;
        }

        @Override
        public ChangeLogParser createChangeLogParser() {
            return new ChangeLogParser() {
                @Override
                public ChangeLogSet<? extends ChangeLogSet.Entry> parse(Run build, RepositoryBrowser<?> browser,
                                                                        File changelogFile)
                        throws IOException, SAXException {
                    return ChangeLogSet.createEmpty(build);
                }
            };
        }

        @TestExtension
        public static class DescriptorImpl extends SCMDescriptor<PasswordSCM> {

            public DescriptorImpl() {
                super(RepositoryBrowser.class);
            }

            @Override
            public String getDisplayName() {
                return "Password SCM";
            }
        }
    }

    public static class PasswordBuildStep extends Builder {

        private final String id;

        public PasswordBuildStep(String id) {
            this.id = id;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            StandardUsernamePasswordCredentials credentials =
                    CredentialsProvider.findCredentialById(this.id, StandardUsernamePasswordCredentials.class, build);
            if (credentials == null) {
                listener.getLogger().printf("Could not find credentials with id '%s'%n", id);
                build.setResult(Result.UNSTABLE);
            } else {
                listener.getLogger().printf("Credentials with id '%s', username: %s%n", id, credentials.getUsername());
                Secret password = credentials.getPassword();
                listener.getLogger().printf("Extracted secret: %s%n", password.getPlainText());
            }
            return true;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Builder> {

            @Override
            public String getDisplayName() {
                return "Password buildstep";
            }
        }
    }

    public static class UsernameUnavailablePasswordImpl extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {

        private final String username;

        public UsernameUnavailablePasswordImpl(@CheckForNull String id, @CheckForNull String description,
                                               String username) {
            super(id, description);
            this.username = username;
        }

        public UsernameUnavailablePasswordImpl(@CheckForNull CredentialsScope scope, @CheckForNull String id,
                                               @CheckForNull String description, String username) {
            super(scope, id, description);
            this.username = username;
        }

        @NonNull
        @Override
        public Secret getPassword() {
            throw new CredentialsUnavailableException("password");
        }

        @NonNull
        @Override
        public String getUsername() {
            return username;
        }

        @TestExtension
        public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

            @Override
            public String getDisplayName() {
                return "Username and unavailable password";
            }
        }
    }
}
