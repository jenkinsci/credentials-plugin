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

/**
 * A requirement for a set of OAuth scopes
 *
 * NOTE: The intended usage pattern for this is:
 * <pre>
 *   SpecificOAuthScopeRequirement
 *     extends ProviderOAuthScopeRequirement
 *     extends OAuthScopeRequirement
 * </pre>
 *
 * A ~concrete example:
 * <pre>
 *   GoogleDriveOAuthScopeRequirement
 *     extends GoogleOAuthScopeRequirement
 *     extends OAuthScopeRequirement
 * </pre>
 *
 * This is so that client code can type-filter on provider-specific requirements
 * e.g. {@code GoogleOAuthScopeRequirement.class} to gather all the scopes that
 * may be asked of a given credential.
 *
 * @see OAuthScopeSpecification
 * @see com.cloudbees.plugins.credentials.common.OAuthCredentials
 */
public abstract class OAuthScopeRequirement extends DomainRequirement {
  /**
   * The OAuth scopes required for authenticating with a service provider.
   */
  public abstract Collection<String> getScopes();
}