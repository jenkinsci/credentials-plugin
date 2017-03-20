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

import static java.util.logging.Level.SEVERE;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;

/**
 * This {@link ExtensionPoint} serves as a means for plugins to augment the
 * domain-requirement discovery process.  The intended usage is:
 * <code>
 *   List&lt;T&gt; list = DomainRequirementProvider.lookupRequirements(
 *      FooRequirement.class);
 * </code>
 * This will delegate to the various extension implementations to
 * {@link #provide(Class)} a {@link List} of requirements from things it
 * understands how to discover.  The expectation is that it will call:
 * <code>
 *   of(discoveredClass, type /* parameter to provide *{@literal /});
 * </code>
 * in order to perform the {@link RequiresDomain} resolution.
 *
 * @see RequiresDomain
 * @see DescribableDomainRequirementProvider
 */
public abstract class DomainRequirementProvider implements ExtensionPoint {
  private static final Logger logger =
      Logger.getLogger(DomainRequirementProvider.class.getName());

  /**
   * This hook is intended for providers to implement such that they can
   * surface custom class-discovery logic, on which they will call {@code of()}
   * to instantiate the elements returned.
   */
  protected abstract <T extends DomainRequirement> List<T> provide(
      Class<T> type);

  /**
   * The the entrypoint for requirement gathering, this static method delegates
   * to any registered providers to provide their set of discoverable
   * requirements.
   */
  public static <T extends DomainRequirement> List<T> lookupRequirements(
      Class<T> type) {
    ExtensionList<DomainRequirementProvider> providers;
    try {
      providers = Hudson.getInstance().getExtensionList(
          DomainRequirementProvider.class);
    } catch (Exception e) {
      logger.log(SEVERE, e.getMessage(), e);
      return ImmutableList.of();
    }

    List<T> result = Lists.newArrayList();
    for (DomainRequirementProvider provider : providers) {
      result.addAll(provider.provide(type));
    }
    return result;
  }

  /**
   * This is called by implementations of {@code provide()} to instantiate
   * the class specified by an actual attribute, if present.  It returns null
   * otherwise.
   */
  @Nullable
  public static <T extends DomainRequirement> T of(Class<?> type,
      Class<T> requirementType) {
    RequiresDomain requiresDomain =
        (RequiresDomain) type.getAnnotation(RequiresDomain.class);
    if (requiresDomain == null) {
      // Not annotated with @RequiresDomain
      return null;
    }

    if (!requirementType.isAssignableFrom(requiresDomain.value())) {
      // The "type" filter excludes the annotation
      return null;
    }

    try {
      // Add an instance of the annotated requirement
      return (T) requiresDomain.value().newInstance();
    } catch (InstantiationException e) {
      logger.log(SEVERE, e.getMessage(), e);
      return null;
    } catch (IllegalAccessException e) {
      logger.log(SEVERE, e.getMessage(), e);
      return null;
    }
  }
}
