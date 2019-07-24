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

package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CredentialsParametersActionTest {

    @ClassRule public static JenkinsRule j = new JenkinsRule();

    @BeforeClass
    public static void setUpClass() throws IOException {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
                .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "cred-id", "global credential", "root", "correct horse battery staple"));
        final User alpha = User.getOrCreateByIdOrFullName("alpha");
        try (ACLContext ignored = ACL.as(alpha)) {
            CredentialsProvider.lookupStores(alpha).iterator().next()
                    .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(CredentialsScope.USER, "alpha-cred-id", "user credentials", "root", "hello world"));
        }
    }

    @Test
    public void forRunReturnsNullWhenRunHasNoParameters() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0));
        assertNull(CredentialsParametersAction.forRun(build));
    }

    @Test
    public void forRunCopiesParametersWhenRunIsParameterized() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition("cred", "", null, IdCredentials.class.getName(), true)));
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause("alpha"), forParameter("cred-id")));
        final CredentialsParametersAction action = CredentialsParametersAction.forRun(build);
        assertNotNull(action);
        final CredentialsParametersAction.AuthenticatedCredentials creds = action.findCredentialsByParameterName("cred");
        assertNotNull(creds);
        assertEquals("alpha", creds.getUserId());
        assertEquals("cred-id", creds.getParameterValue().getValue());
    }

    @Test
    public void forRunUsesUserIdCauseForInitialParametersOwnership() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition("cred", "", null, IdCredentials.class.getName(), true)));

        // no cause means no user id
        {
            final CredentialsParametersAction action = CredentialsParametersAction.forRun(
                    j.assertBuildStatusSuccess(
                            project.scheduleBuild2(0,
                                    forParameter("cred-id"))));
            assertNotNull(action);
            final CredentialsParametersAction.AuthenticatedCredentials creds = action.findCredentialsByParameterName("cred");
            assertNotNull(creds);
            assertNull(creds.getUserId());
            assertEquals("cred-id", creds.getParameterValue().getValue());
        }

        // cause of type UserIdCause means that user id
        {
            final CredentialsParametersAction action = CredentialsParametersAction.forRun(
                    j.assertBuildStatusSuccess(
                            project.scheduleBuild2(0,
                                    new Cause.UserIdCause("alpha"), forParameter("alpha-cred-id"))));
            assertNotNull(action);
            final CredentialsParametersAction.AuthenticatedCredentials creds = action.findCredentialsByParameterName("cred");
            assertNotNull(creds);
            assertEquals("alpha", creds.getUserId());
            assertEquals("alpha-cred-id", creds.getParameterValue().getValue());
        }
    }

    private static ParametersAction forParameter(String credentialsId) {
        return new ParametersAction(new CredentialsParameterValue("cred", credentialsId, null, false));
    }
}