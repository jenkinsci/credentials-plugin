/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CredentialsParameterDefinitionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test public void defaultValue() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
        store.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, "id", "description", "username", "password"));

        List<ParameterDefinition> params = new ArrayList<ParameterDefinition>();
        params.add(new CredentialsParameterDefinition("name", "description", "id", "type", true));

        ParametersDefinitionProperty prop = new ParametersDefinitionProperty(params);
        p.addProperty(prop);
        p.getBuildersList().add(new ParamCheckBuilder());
        r.buildAndAssertSuccess(p);
    }

    public static class ParamCheckBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            ParametersAction pa = build.getAction(ParametersAction.class);
            ParameterValue pv = pa.getParameter("name");
            assertTrue(pv instanceof CredentialsParameterValue);
            assertTrue(((CredentialsParameterValue) pv).isDefaultValue());
            return true;
        }
    }
}
