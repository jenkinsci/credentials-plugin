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

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DomainTest {

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
}
