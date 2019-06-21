package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
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
        final CredentialsParameterValue parameterValue = new CredentialsParameterValue("cred", "cred-id", "", false, "alpha");
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause("alpha"), new ParametersAction(parameterValue)));
        final CredentialsParametersAction action = CredentialsParametersAction.forRun(build);
        assertNotNull(action);
        assertEquals(parameterValue, action.findParameterByName("cred"));
    }
}