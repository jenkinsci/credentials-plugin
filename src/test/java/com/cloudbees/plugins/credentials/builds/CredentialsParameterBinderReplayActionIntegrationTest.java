/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package com.cloudbees.plugins.credentials.builds;

import com.cloudbees.plugins.credentials.CredentialsParameterDefinition;
import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.security.ACLContext;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@WithJenkins
class CredentialsParameterBinderReplayActionIntegrationTest {
    private static final String PARAMETER_NAME = "parameterName";

    private JenkinsRule j;

    private String credentialsId;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        final User alpha = User.getById("alpha", true);
        try (ACLContext ignored = ACL.as(alpha)) {
            credentialsId = UUID.randomUUID().toString();
            CredentialsProvider.lookupStores(alpha).iterator().next()
                    .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                            CredentialsScope.USER, credentialsId, null, "user", "pass"));
        }
    }

    @Test
    void replayActionShouldNotCopyCredentialsParameterBindingUserIds() throws Exception {
        final WorkflowJob job = j.createProject(WorkflowJob.class);
        job.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition(
                PARAMETER_NAME, null, null, IdCredentials.class.getName(), true)));
        job.setDefinition(new CpsFlowDefinition("echo 'hello, world'", true));
        final WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0,
                new CauseAction(new Cause.UserIdCause("alpha")),
                new ParametersAction(new CredentialsParameterValue(PARAMETER_NAME, credentialsId, null))
        ));

        final CredentialsParameterBinding original = CredentialsParameterBinder.getOrCreate(run).forParameterName(PARAMETER_NAME);
        assertNotNull(original);
        assertEquals("alpha", original.getUserId());
        assertNotNull(CredentialsProvider.findCredentialById(PARAMETER_NAME, IdCredentials.class, run));

        final User beta = User.getById("beta", true);
        final WorkflowRun replayedRun = replayRunAs(run, beta);
        final CredentialsParameterBinding replayed = CredentialsParameterBinder.getOrCreate(replayedRun).forParameterName(PARAMETER_NAME);
        assertNotNull(replayed);
        assertEquals("beta", replayed.getUserId());
        assertNull(CredentialsProvider.findCredentialById(PARAMETER_NAME, IdCredentials.class, replayedRun));
    }

    @SuppressWarnings("unchecked")
    private WorkflowRun replayRunAs(WorkflowRun run, User user) throws Exception {
        final ReplayAction replay = run.getAction(ReplayAction.class);
        assertNotNull(replay);
        final QueueTaskFuture<WorkflowRun> futureRun;
        try (ACLContext ignored = ACL.as(user)) {
            futureRun = replay.run(replay.getOriginalScript(), replay.getOriginalLoadedScripts());
        }
        return j.assertBuildStatusSuccess(futureRun);
    }
}
