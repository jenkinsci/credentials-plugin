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

import com.cloudbees.plugins.credentials.store.ModifiableCredentialsStore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.impl.DummyCredentials;

import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DomainTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    @Test
    public void smokes() throws Exception {
        Domain instance =
                new Domain("test federation", "the instance under test", Arrays.<DomainSpecification>asList(
                        new SchemeSpecification("http, https, svn, git, pop3, imap, spdy"),
                        new HostnameSpecification("*.jenkins-ci.org", null)));

        assertThat(instance.test(), is(true));
        assertThat(instance.test(new HostnamePortRequirement("www.jenkins-ci.org", 80), new SchemeRequirement("http")), is(true));
    }

    @Test
    public void pathRequirements() throws Exception {
        Domain instance =
                new Domain("test federation", "the instance under test", Arrays.<DomainSpecification>asList(
                        new SchemeSpecification("https"),
                        new HostnameSpecification("*.jenkins-ci.org", null),
                        new PathSpecification("/download/**/jenkins.war", null, false)));

        assertThat(instance.test(), is(true));
        assertThat(instance.test(URIRequirementBuilder.fromUri("https://updates.jenkins-ci.org/download/1.532/jenkins.war").build()), is(true));
        assertThat(instance.test(URIRequirementBuilder.fromUri("https://updates.jenkins-ci.org/download/jenkins.war").build()), is(true));
        assertThat(instance.test(URIRequirementBuilder.fromUri("https://updates.jenkins-ci.org/download/1/2/3/jenkins.war").build()), is(true));
        assertThat(instance.test(URIRequirementBuilder.fromUri("http://updates.jenkins-ci.org/download/1/2/3/jenkins.war").build()), is(false));

    }
    
    @Test
    public void testCredentialsInCustomDomains() throws IOException {
        Domain domainFoo = new Domain("domainFoo", "Hostname domain", Arrays.asList(new DomainSpecification[] { new HostnameSpecification("foo.com", "") }));
        Domain domainBar = new Domain("domainBar", "Path domain", Arrays.asList(new DomainSpecification[] { new HostnameSpecification("bar.com", "") }));
        DummyCredentials systemCred = new DummyCredentials(CredentialsScope.SYSTEM, "systemCred", "pwd");
        DummyCredentials systemCred1 = new DummyCredentials(CredentialsScope.SYSTEM, "systemCred1", "pwd");
        DummyCredentials systemCredMod = new DummyCredentials(CredentialsScope.SYSTEM, "systemCredMod", "pwd");

        ModifiableCredentialsStore store = (ModifiableCredentialsStore) CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
        
        // Add domains with credentials
        store.addDomain(domainFoo, Collections.<Credentials>emptyList());
        store.addDomain(domainBar, Collections.<Credentials>emptyList());
        
        // Domain requirements for credential queries
        List<DomainRequirement> reqFoo = Arrays.asList(new DomainRequirement[] { new HostnameRequirement("foo.com") });
        List<DomainRequirement> reqBar = Arrays.asList(new DomainRequirement[] { new HostnameRequirement("bar.com") });
        
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqFoo).isEmpty());
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqBar).isEmpty());
    
        // Add credentials to domains
        store.addCredentials(domainFoo, systemCred);
        store.addCredentials(domainBar, systemCred1);
    
        // Search creadentials with specific domain restrictions
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqFoo).size());
        assertEquals(systemCred.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqFoo).get(0).getUsername());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqBar).size());
        assertEquals(systemCred1.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqBar).get(0).getUsername());
    
        // Update credential from domain
        store.updateCredentials(domainFoo, systemCred, systemCredMod);
        
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqFoo).size());
        assertEquals(systemCredMod.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqFoo).get(0).getUsername());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqBar).size());
        assertEquals(systemCred1.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqBar).get(0).getUsername());
  
        // Remove credential from domain
        store.removeCredentials(domainFoo, systemCredMod);
        
        assertTrue(CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqFoo).isEmpty());
        assertEquals(1, CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqBar).size());
        assertEquals(systemCred1.getUsername(), CredentialsProvider.lookupCredentials(DummyCredentials.class, r.jenkins, ACL.SYSTEM, reqBar).get(0).getUsername());
    }
}
