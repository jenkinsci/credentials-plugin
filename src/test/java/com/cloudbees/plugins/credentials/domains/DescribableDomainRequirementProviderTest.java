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

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;

/**
 * Tests for {@link DescribableDomainRequirementProvider}.
 */
public class DescribableDomainRequirementProviderTest {
  // Allow for testing using JUnit4, instead of JUnit3.
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  /**
   */
  public static class TestRequirement
      extends OAuthScopeRequirement {
    @Override
    public Collection<String> getScopes() {
      return GOOD_SCOPES;
    }
  }

  /**
   * This is a trivial implementation of a {@link Builder} that
   * consumes {@link OAuthCredentials}.
   */
  @RequiresDomain(value = TestRequirement.class)
  public static class TestRobotBuilder extends Builder {
    public TestRobotBuilder() {
    }

    @Override
    public DescriptorImpl getDescriptor() {
      return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
        getClass());
    }

    /**
     * Descriptor for our trivial builder
     */
    @Extension
    public static final class DescriptorImpl
        extends Descriptor<Builder> {
      @Override
      public String getDisplayName() {
        return "Test Robot Builder";
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testDescribableRequirementDiscovery() throws Exception {
    List<TestRequirement> list =
        DomainRequirementProvider.lookupRequirements(TestRequirement.class);

    assertEquals(1, list.size());
  }

  @Test
  @WithoutJenkins
  public void testNoJenkinsInstance() throws Exception {
    // Make sure we don't crash if run on a slave, where no Jenkins
    // is present.
    List<TestRequirement> list =
        DomainRequirementProvider.lookupRequirements(TestRequirement.class);

    // However, we also shouldn't discover anything without Jenkins present.
    assertEquals(0, list.size());
  }

  /**
   */
  public static class BadTestRequirement
      extends OAuthScopeRequirement {
    public BadTestRequirement(String x) {
      // A trivial ctor is required, remove this
      // and things work fine.
    }

    @Override
    public Collection<String> getScopes() {
      return GOOD_SCOPES;
    }
  }

  /**
   */
  @RequiresDomain(value = BadTestRequirement.class)
  public static class TestRobotBuilderBad extends Builder {
    public TestRobotBuilderBad() {
    }

    @Override
    public DescriptorImpl getDescriptor() {
      return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
        getClass());
    }

    /**
     * Descriptor for our trivial builder
     */
    @Extension
    public static final class DescriptorImpl
        extends Descriptor<Builder> {
      @Override
      public String getDisplayName() {
        return "Test Robot Builder (Bad)";
      }
    }
  }

  @Test
  public void testBadRequirement() throws Exception {
    List<BadTestRequirement> list =
        DomainRequirementProvider.lookupRequirements(BadTestRequirement.class);

    assertEquals(0, list.size());
  }

  /**
   */
  public static class AnotherBadTestRequirement
      extends OAuthScopeRequirement {
    private AnotherBadTestRequirement() {
      // A visible ctor is required, make this public
      // or protected and things work.
    }

    @Override
    public Collection<String> getScopes() {
      return GOOD_SCOPES;
    }
  }

  /**
   */
  @RequiresDomain(value = AnotherBadTestRequirement.class)
  public static class TestRobotBuilderBadAgain extends Builder {
    public TestRobotBuilderBadAgain() {
    }

    @Override
    public DescriptorImpl getDescriptor() {
      return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
        getClass());
    }

    /**
     * Descriptor for our trivial builder
     */
    @Extension
    public static final class DescriptorImpl
        extends Descriptor<Builder> {
      @Override
      public String getDisplayName() {
        return "Test Robot Builder (Bad Again)";
      }
    }
  }

  @Test
  public void testBadRequirementProtection() throws Exception {
    List<AnotherBadTestRequirement> list =
        DomainRequirementProvider.lookupRequirements(
            AnotherBadTestRequirement.class);

    assertEquals(0, list.size());
  }

  private static String GOOD_SCOPE1 = "foo";
  private static String GOOD_SCOPE2 = "baz";
  private static String BAD_SCOPE = "bar";
  private static Collection<String> GOOD_SCOPES =
      ImmutableList.of(GOOD_SCOPE1, GOOD_SCOPE2);
  private static Collection<String> BAD_SCOPES =
      ImmutableList.of(GOOD_SCOPE1, BAD_SCOPE);
}