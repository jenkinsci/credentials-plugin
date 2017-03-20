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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * The base class for provider-specific specifications, instantiated with the
 * provider-specific requirement type to which the specification should apply.
 *
 * NOTE: The reason for provider-specific paired implementations of scope /
 * requirement is due to the fact that an OAuth credential is per-provider
 * (e.g. Google, Facebook, GitHub).
 *
 * This base implementation, returns {@code UNKNOWN} from {@link #test}
 * if the passed requirement doesn't match our descriptor's provider
 * requirement.  It then delegates to {@link #_test}, a hook that by default
 * returns {@code POSITIVE}/{@code NEGATIVE} depending on whether
 * {@code specifiedScopes} is a superset of the required scopes.
 *
 * @see OAuthScopeRequirement
 * @see com.cloudbees.plugins.credentials.common.OAuthCredentials
 * @param <T> The type of requirements to which this specification may apply
 */
public abstract class OAuthScopeSpecification<T extends OAuthScopeRequirement>
    extends DomainSpecification {
  protected OAuthScopeSpecification(Collection<String> specifiedScopes) {
    this.specifiedScopes = checkNotNull(specifiedScopes);
  }

  /**
   * Tests the scope against this specification.
   *
   * @param requirement The set of requirements to validate against this
   * specification
   * @return the result of the test.
   */
  @Override
  @edu.umd.cs.findbugs.annotations.SuppressWarnings("BC_UNCONFIRMED_CAST")
  public final Result test(DomainRequirement requirement) {
    Class<T> providerRequirement = getDescriptor().getProviderRequirement();

    if (!providerRequirement.isInstance(requirement)) {
      return Result.UNKNOWN;
    }

    // NOTE: This cast is checked by the isInstance above
    return _test((T) requirement);
  }

  /**
   * Surfaces a hook for implementations to override or extend the
   * default functionality of simply matching the set of scopes.
   */
  protected Result _test(T requirement) {
    for (String scope : requirement.getScopes()) {
      if (!specifiedScopes.contains(scope)) {
        // We are missing this required scope
        return Result.NEGATIVE;
      }
    }
    // We matched all the scopes
    return Result.POSITIVE;
  }

  /**
   * Surfaces the set of scopes specified by this requirement for
   * jelly roundtripping.
   */
  public Collection<String> getSpecifiedScopes() {
    return Collections.unmodifiableCollection(specifiedScopes);
  }
  private final Collection<String> specifiedScopes;

  /**
   * {@inheritDoc}
   */
  @Override
  public Descriptor<T> getDescriptor() {
    return (Descriptor<T>) super.getDescriptor();
  }

  /**
   * The base descriptor for specification extensions.  This carries the
   * class of requirements to which this specification should apply.
   */
  public abstract static class Descriptor<T extends OAuthScopeRequirement>
      extends DomainSpecificationDescriptor {
    public Descriptor(Class<T> providerRequirement) {
      this.providerRequirement = checkNotNull(providerRequirement);
    }

    /**
     * Fetches the names and values of the set of scopes consumed by clients of
     * this plugin.
     */
    public Collection<String> getScopeItems() {
      List<T> requirements = DomainRequirementProvider.lookupRequirements(
          getProviderRequirement());

      Set<String> result = Sets.newHashSet();
      for (T required : requirements) {
        Iterables.addAll(result, required.getScopes());
      }
      return result;
    }

    /**
     * Retrieve the class of {@link DomainRequirement}s to which our associated
     * specifications should apply.
     */
    public Class<T> getProviderRequirement() {
      return providerRequirement;
    }
    private final Class<T> providerRequirement;
  }
}