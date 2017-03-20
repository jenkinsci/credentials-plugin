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

import java.util.List;

import com.google.common.collect.Lists;

import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * This implementation of {@link DomainRequirementProvider} implements
 * support for discovering {@link DomainRequirement}s annotated on
 * {@link hudson.model.Describable} classes by walking the {@link Descriptor}s
 * registered with {@link Jenkins}.
 *
 * TODO(mattmoor): should we allow the annotation on the descriptor itself?
 */
@Extension
public class DescribableDomainRequirementProvider
    extends DomainRequirementProvider {
  /**
   * {@inheritDoc}
   */
  @Override
  protected <T extends DomainRequirement> List<T> provide(Class<T> type) {
    ExtensionList<Descriptor> extensions =
        Jenkins.getInstance().getExtensionList(Descriptor.class);

    List<T> result = Lists.newArrayList();
    for (ExtensionComponent<Descriptor> component :
             extensions.getComponents()) {
      Descriptor descriptor = component.getInstance();

      T element = of(descriptor.clazz, type);
      if (element != null) {
        // Add an instance of the annotated requirement
        result.add(element);
      }
    }
    return result;
  }
}
