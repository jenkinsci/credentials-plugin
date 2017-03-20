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

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.OAuthCredentials;
import com.cloudbees.plugins.credentials.domains.DomainSpecification.Result;
import com.google.common.collect.ImmutableList;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.ACL;
import jenkins.model.Jenkins;

/**
 * Tests for {@link OAuthScopeSpecification}.
 */
public class OAuthScopeSpecificationTest {
  // Allow for testing using JUnit4, instead of JUnit3.
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  /**
   */
  public static class TestRequirement
      extends OAuthScopeRequirement {
    public TestRequirement(Collection<String> scopes) {
      this.scopes = scopes;
    }

    @Override
    public Collection<String> getScopes() {
      return scopes;
    }
    private final Collection<String> scopes;
  }

  /**
   */
  public static class TestGoodRequirement
      extends TestRequirement {
    public TestGoodRequirement() {
      super(GOOD_SCOPES);
    }
  }

  /**
   */
  public static class TestBadRequirement
      extends TestRequirement {
    public TestBadRequirement() {
      super(BAD_SCOPES);
    }
  }

  /**
   */
  public static class TestSpec
      extends OAuthScopeSpecification<TestRequirement> {
    public TestSpec(Collection<String> scopes) {
      super(scopes);
    }

    /**
     */
    @Extension
    public static class DescriptorImpl
        extends OAuthScopeSpecification.Descriptor<TestRequirement> {
      public DescriptorImpl() {
        super(TestRequirement.class);
      }

      @Override
      public String getDisplayName() {
        return "blah";
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @WithoutJenkins
  public void testBasics() throws Exception {
    TestSpec spec = new TestSpec(GOOD_SCOPES);

    assertThat(spec.getSpecifiedScopes(),
        hasItems(GOOD_SCOPE1, GOOD_SCOPE2));
  }

  @Test
  public void testUnknownRequirement() throws Exception {
    TestSpec spec = new TestSpec(GOOD_SCOPES);

    OAuthScopeRequirement requirement = new OAuthScopeRequirement() {
        @Override
        public Collection<String> getScopes() {
          return GOOD_SCOPES;
        }
      };

    // Verify that even with the right scopes the type kind excludes
    // the specification from matching this requirement
    assertEquals(Result.UNKNOWN, spec.test(requirement));
  }

  @Test
  public void testKnownRequirements() throws Exception {
    TestSpec spec = new TestSpec(GOOD_SCOPES);

    TestRequirement goodReq = new TestGoodRequirement();
    TestRequirement badReq = new TestBadRequirement();

    // Verify that with the right type of requirement that
    // good scopes match POSITIVEly and bad scopes match NEGATIVEly
    assertEquals(Result.POSITIVE, spec.test(goodReq));
    assertEquals(Result.NEGATIVE, spec.test(badReq));
  }

  @Test
  public void testWithCustom_test() throws Exception {
    TestRequirement badReq = new TestBadRequirement();

    final TestSpec forDescriptor = new TestSpec(GOOD_SCOPES);

    TestSpec spec = new TestSpec(GOOD_SCOPES) {
        @Override
        protected Result _test(TestRequirement foo) {
          return Result.POSITIVE;
        }

        @Override
        public Descriptor<TestRequirement> getDescriptor() {
          return forDescriptor.getDescriptor();
        }
      };

    // Verify that if we *override* the '_test' method that we can
    // even let bad scopes through.
    assertEquals(Result.POSITIVE, spec.test(badReq));
  }

  /**
   */
  @Extension
  public static class CustomProvider extends DomainRequirementProvider {
    @Override
    protected <T extends DomainRequirement> List<T> provide(Class<T> type) {
      if (type.isAssignableFrom(TestRequirement.class)) {
        return ImmutableList.<T>of((T) new TestGoodRequirement());
      }
      return ImmutableList.of();
    }
  }

  @Test
  public void testGetScopeItems() throws Exception {
    TestSpec forDescriptor = new TestSpec(ImmutableList.<String>of());

    Collection<String> discovered =
        forDescriptor.getDescriptor().getScopeItems();

    assertEquals(2, discovered.size());
    assertThat(discovered, hasItems(GOOD_SCOPE1, GOOD_SCOPE2));
    assertThat(discovered, not(hasItems(BAD_SCOPE)));
  }

  @Mock
  private OAuthCredentials mockCredentials;

  /**
   * Verify that credentials that appear outside of a domain with
   * an OAuth specification can be matched
   */
  @Test
  public void testUnscopedLookup() throws Exception {
    SystemCredentialsProvider.getInstance().getCredentials()
        .add(mockCredentials);

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestGoodRequirement());

    assertThat(matchingCredentials, hasItems(mockCredentials));
  }

  /**
   * Verify that credentials that appear inside of a domain where the oauth
   * specification provides no scopes is not matched.
   */
  @Test
  public void testBadDomainScopedLookupEmptySpec() throws Exception {
    TestSpec spec = new TestSpec(ImmutableList.<String>of());
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of(spec));

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestGoodRequirement());

    assertThat(matchingCredentials, not(hasItems(mockCredentials)));
  }

  /**
   * Verify that credentials that appear inside of a domain without any
   * specification are found and returned.
   */
  @Test
  public void testGoodDomainScopedLookupUnspecifiedDomain() throws Exception {
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of());

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestGoodRequirement());

    assertThat(matchingCredentials, hasItems(mockCredentials));
  }

  /**
   * Verify that credentials that appear inside of a domain with a single
   * unrelated specification (inapplicable) are found and returned.
   */
  @Test
  public void testGoodDomainScopedLookupUnrelatedSpecification()
      throws Exception {
    SchemeSpecification spec = new SchemeSpecification("http");
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of(spec));

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestGoodRequirement());

    assertThat(matchingCredentials, hasItems(mockCredentials));
  }

  /**
   * Verify that credentials that appear inside of a domain with a matching
   * specification are found and returned.
   */
  @Test
  public void testGoodDomainScopedLookup() throws Exception {
    TestSpec spec = new TestSpec(GOOD_SCOPES);
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of(spec));

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestGoodRequirement());

    assertThat(matchingCredentials, hasItems(mockCredentials));
  }

  /**
   * Verify that credentials that appear inside of a domain with a matching
   * superset specification are found and returned.
   */
  @Test
  public void testGoodDomainScopedLookupSubset() throws Exception {
    TestSpec spec = new TestSpec(GOOD_SCOPES);
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of(spec));

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestRequirement(
                ImmutableList.of(GOOD_SCOPE1)));

    assertThat(matchingCredentials, hasItems(mockCredentials));
  }

  /**
   * Verify that credentials that appear inside of a domain with a mismatched
   * specification are NOT returned
   */
  @Test
  public void testBadDomainScopedLookup() throws Exception {
    TestSpec spec = new TestSpec(GOOD_SCOPES);
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of(spec));

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestBadRequirement());

    assertThat(matchingCredentials, not(hasItems(mockCredentials)));
  }

  /**
   * Verify that credentials that appear inside of a domain with a subset of the
   * scopes required are NOT returned
   */
  @Test
  public void testBadDomainScopedLookupSuperset() throws Exception {
    TestSpec spec = new TestSpec(ImmutableList.of(GOOD_SCOPE1));
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of(spec));

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestGoodRequirement());

    assertThat(matchingCredentials, not(hasItems(mockCredentials)));
  }

  /**
   * Verify that credentials that appear inside of a domain with a partially
   * overlapping set of scopes are NOT matched.
   */
  @Test
  public void testBadDomainScopedLookupOverlap() throws Exception {
    TestSpec spec = new TestSpec(GOOD_SCOPES);
    Domain domain = new Domain("domain name", "domain description",
        ImmutableList.<DomainSpecification>of(spec));

    SystemCredentialsProvider.getInstance().getDomainCredentialsMap()
        .put(domain, ImmutableList.<Credentials>of(mockCredentials));

    List<OAuthCredentials> matchingCredentials =
        CredentialsProvider.lookupCredentials(OAuthCredentials.class,
            Jenkins.getInstance(), ACL.SYSTEM, new TestBadRequirement());

    assertThat(matchingCredentials, not(hasItems(mockCredentials)));
  }


  private static String GOOD_SCOPE1 = "foo";
  private static String GOOD_SCOPE2 = "baz";
  private static String BAD_SCOPE = "bar";
  private static Collection<String> GOOD_SCOPES =
      ImmutableList.of(GOOD_SCOPE1, GOOD_SCOPE2);
  private static Collection<String> BAD_SCOPES =
      ImmutableList.of(GOOD_SCOPE1, BAD_SCOPE);
}