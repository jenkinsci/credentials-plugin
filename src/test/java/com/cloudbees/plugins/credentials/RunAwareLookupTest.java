/*
 * The MIT License
 *
 * Copyright (c) 2026, Jim Klimov, PROVYS Technologies a.s.
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

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end demonstration that the {@link Run}-aware overloads added to
 * {@link CredentialsProvider} (see {@link CredentialsProvider#getCredentialsInItemGroup(Class, ItemGroup, Authentication, List, Run)})
 * really do carry the specific {@link Run} that triggered the lookup all the
 * way to a provider, for both a classic {@link FreeStyleProject} build and a
 * Pipeline build.
 *
 * <p>{@link ObservingProvider} is a throwaway {@link CredentialsProvider} that
 * records every {@link Run} it is asked about into a static list (the "global
 * variable" approach) and additionally bakes the observed run's id into the
 * looked-up credential's own value, which the calling build step/Pipeline step
 * logs (the "log something" approach) - so the test can assert on the actual
 * build log, not just on the provider's internal state, proving the value
 * really flowed through {@code findCredentialById} and back out again.</p>
 */
@WithJenkins
class RunAwareLookupTest {

    private static final String CREDENTIALS_ID = "run-observing";

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule r) {
        this.r = r;
        ObservingProvider.OBSERVED_RUN_IDS.clear();
    }

    @Test
    void freeStyleBuildIsIdentifiedToTheProvider() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        project.getBuildersList().add(new ObservingBuildStep());
        FreeStyleBuild build = r.buildAndAssertSuccess(project);
        String runId = build.getExternalizableId();
        r.assertLogContains("observed run: " + runId, build);
        assertTrue(ObservingProvider.OBSERVED_RUN_IDS.contains(runId),
                "provider should have recorded the build's own externalizable id, got: "
                        + ObservingProvider.OBSERVED_RUN_IDS);
    }

    @Test
    void pipelineBuildIsIdentifiedToTheProvider() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("observeRunCredential()", true));
        WorkflowRun run = r.buildAndAssertSuccess(job);
        String runId = run.getExternalizableId();
        r.assertLogContains("observed run: " + runId, run);
        assertTrue(ObservingProvider.OBSERVED_RUN_IDS.contains(runId),
                "provider should have recorded the pipeline run's own externalizable id, got: "
                        + ObservingProvider.OBSERVED_RUN_IDS);
    }

    /** Records every {@link Run} (or {@code "<null>"} if none) it is asked to look up credentials for. */
    @TestExtension
    public static class ObservingProvider extends CredentialsProvider {

        static final List<String> OBSERVED_RUN_IDS = Collections.synchronizedList(new ArrayList<>());

        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type,
                @Nullable ItemGroup itemGroup, @Nullable Authentication authentication,
                @NonNull List<DomainRequirement> domainRequirements) {
            return getCredentialsInItemGroup(type, itemGroup, authentication, domainRequirements, null);
        }

        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type,
                @Nullable ItemGroup itemGroup, @Nullable Authentication authentication,
                @NonNull List<DomainRequirement> domainRequirements, @CheckForNull Run<?, ?> run) {
            OBSERVED_RUN_IDS.add(run == null ? "<null>" : run.getExternalizableId());
            if (!type.isAssignableFrom(ObservingCredentials.class)) {
                return Collections.emptyList();
            }
            String observed = run == null ? "<null>" : run.getExternalizableId();
            return Collections.singletonList(type.cast(new ObservingCredentials(CREDENTIALS_ID, observed)));
        }
    }

    /** A trivial credential whose value is fixed, at construction time, to whichever {@link Run} the provider last saw. */
    public static class ObservingCredentials extends BaseStandardCredentials implements StandardCredentials {

        private final String observedRunExternalizableId;

        ObservingCredentials(String id, String observedRunExternalizableId) {
            super(id, "records which Run last looked it up");
            this.observedRunExternalizableId = observedRunExternalizableId;
        }

        String getObservedRunExternalizableId() {
            return observedRunExternalizableId;
        }

        @TestExtension
        public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Run-observing credentials (test only)";
            }
        }
    }

    /** Classic-job equivalent of {@link ObserveRunCredentialStep}: looks up and logs, from within a real build. */
    private static class ObservingBuildStep extends Builder {

        @Override
        public boolean perform(AbstractBuild<?, ?> build, hudson.Launcher launcher, BuildListener listener) {
            ObservingCredentials credentials =
                    CredentialsProvider.findCredentialById(CREDENTIALS_ID, ObservingCredentials.class, build);
            listener.getLogger().println("observed run: "
                    + (credentials == null ? "<none>" : credentials.getObservedRunExternalizableId()));
            return true;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Builder> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Observing build step (test only)";
            }
        }
    }

    /** Pipeline step {@code observeRunCredential()}: looks up and logs, from within a real running script. */
    public static class ObserveRunCredentialStep extends Step {

        @DataBoundConstructor
        public ObserveRunCredentialStep() {
        }

        @Override
        public StepExecution start(StepContext context) {
            return new Execution(context);
        }

        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {

            @NonNull
            @Override
            public String getFunctionName() {
                return "observeRunCredential";
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return "Observe run credential (test only)";
            }

            @NonNull
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                Set<Class<?>> context = new java.util.HashSet<>();
                context.add(Run.class);
                context.add(TaskListener.class);
                return context;
            }
        }

        private static class Execution extends SynchronousStepExecution<Void> {
            private static final long serialVersionUID = 1L;

            Execution(StepContext context) {
                super(context);
            }

            @Override
            protected Void run() throws Exception {
                Run<?, ?> run = getContext().get(Run.class);
                ObservingCredentials credentials =
                        CredentialsProvider.findCredentialById(CREDENTIALS_ID, ObservingCredentials.class, run);
                getContext().get(TaskListener.class).getLogger().println("observed run: "
                        + (credentials == null ? "<none>" : credentials.getObservedRunExternalizableId()));
                return null;
            }
        }
    }
}
