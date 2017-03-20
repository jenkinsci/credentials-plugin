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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to indicate that a given class has the specified
 * {@link DomainRequirement}.  This is used to automatically discover the
 * extent of requirements by installed components, so that the user can specify
 * the necessary requirements from a sufficiently complete list presented in
 * the UI.
 *
 * For Example (URI):
 *  If a plugin only supports https/ssh URIs, we might annotate:
 *  <code>
 *    {@literal @}RequiresDomain(value = SecureURIRequirement.class)
 *  </code>
 *  Even though only one of the two may be necessary, it is sufficient to only
 *  surface the options HTTPS/SSH in the specification UI.
 *
 * For Example (OAuth):
 *  This is much more important for less constrained spaces than URI schemes,
 *  especially when the options are harder to type options, e.g. OAuth scopes.
 *  In this case, a plugin might annotate:
 *  <code>
 *    {@literal @}RequiresDomain(value = OAuthScopesABandC.class)
 *  </code>
 *  Even though the task they end up configuring may only require scopes
 *  "A" and "B".
 *
 * @see DomainRequirementProvider
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface RequiresDomain {
  /**
   * The class of {@link DomainRequirement} to which the annotated
   * class adheres.
   *
   * TODO(mattmoor): support a list option
   */
  Class<? extends DomainRequirement> value();
}