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
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CredentialsParameterBinderTest {

    @ClassRule public static JenkinsRule j = new JenkinsRule();
    private static final String GLOBAL_CREDENTIALS_ID = "cred-id";
    private static final String USER_CREDENTIALS_ID = "alpha-cred-id";
    private static final String USER_ID = "alpha";
    private static final String PARAMETER_NAME = "cred";

    @BeforeClass
    public static void setUpClass() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
                .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, GLOBAL_CREDENTIALS_ID, "global credential", "root", "correct horse battery staple"));
        final User alpha = User.getOrCreateByIdOrFullName(USER_ID);
        try (ACLContext ignored = ACL.as(alpha)) {
            CredentialsProvider.lookupStores(alpha).iterator().next()
                    .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(CredentialsScope.USER, USER_CREDENTIALS_ID, "user credentials", "root", "hello world"));
        }
    }

    private FreeStyleProject project;

    @Before
    public void setUp() throws Exception {
        project = j.createFreeStyleProject();
    }

    @Test
    public void getOrCreateIsEmptyWhenBuildHasNoParameters() throws Exception {
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0));
        final CredentialsParameterBinder binder = CredentialsParameterBinder.getOrCreate(build);
        assertTrue(binder.isEmpty());
    }

    @Test
    public void getOrCreateCopiesParametersWhenBuildIsParameterized() throws Exception {
        addCredentialsParameterDefinition();
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0,
                new Cause.UserIdCause(USER_ID), selectCredentialsById(GLOBAL_CREDENTIALS_ID)));
        final CredentialsParameterBinder binder = CredentialsParameterBinder.getOrCreate(build);
        final CredentialsParameterBinding cred = binder.forParameterName(PARAMETER_NAME);
        assertNotNull(cred);
        assertEquals(USER_ID, cred.getUserId());
        assertEquals(GLOBAL_CREDENTIALS_ID, cred.getCredentialsId());
    }

    @Test
    public void forParameterNameReturnsNullUserIdWhenBuildLacksUserIdCause() throws Exception {
        addCredentialsParameterDefinition();
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0,
                selectCredentialsById(USER_CREDENTIALS_ID)));
        final CredentialsParameterBinder binder = CredentialsParameterBinder.getOrCreate(build);
        final CredentialsParameterBinding cred = binder.forParameterName(PARAMETER_NAME);
        assertNotNull(cred);
        assertNull(cred.getUserId());
        // as a result:
        assertNull(CredentialsProvider.findCredentialById(cred.getParameterName(), IdCredentials.class, build));
    }

    @Test
    public void forParameterNameReturnsTriggeringUser() throws Exception {
        addCredentialsParameterDefinition();
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0,
                new Cause.UserIdCause(USER_ID), selectCredentialsById(USER_CREDENTIALS_ID)));
        final CredentialsParameterBinder binder = CredentialsParameterBinder.getOrCreate(build);
        final CredentialsParameterBinding cred = binder.forParameterName(PARAMETER_NAME);
        assertNotNull(cred);
        assertEquals(USER_ID, cred.getUserId());
        assertEquals(USER_CREDENTIALS_ID, cred.getCredentialsId());
        // as a result:
        assertNotNull(CredentialsProvider.findCredentialById(cred.getParameterName(), IdCredentials.class, build));
    }

    private void addCredentialsParameterDefinition() throws IOException {
        project.addProperty(new ParametersDefinitionProperty(new CredentialsParameterDefinition(PARAMETER_NAME, null, null, IdCredentials.class.getName(), true)));
    }

    private static ParametersAction selectCredentialsById(String credentialsId) {
        return new ParametersAction(new CredentialsParameterValue(PARAMETER_NAME, credentialsId, null, false));
    }
}