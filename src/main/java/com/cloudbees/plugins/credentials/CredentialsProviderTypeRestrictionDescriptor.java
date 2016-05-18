/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc., Stephen Connolly.
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

import hudson.model.Descriptor;
import java.util.List;

/**
 * The {@link Descriptor} base class for {@link CredentialsProviderTypeRestriction} implementations.
 *
 * @since 2.0
 */
public abstract class CredentialsProviderTypeRestrictionDescriptor
        extends Descriptor<CredentialsProviderTypeRestriction> {

    /**
     * Computes the effective
     * {@link CredentialsProviderTypeRestriction#filter(CredentialsProvider, CredentialsDescriptor)} result from the
     * sublist of all {@link CredentialsProviderTypeRestriction} instances that use this
     * {@link CredentialsProviderTypeRestrictionDescriptor instance}. Each implementation can determine the policy to
     * follow, typically implementations will from two styles:
     * <ul>
     * <li>Require at least one
     * {@link CredentialsProviderTypeRestriction#filter(CredentialsProvider, CredentialsDescriptor)} returning
     * {@code true}</li>
     * <li>Require no
     * {@link CredentialsProviderTypeRestriction#filter(CredentialsProvider, CredentialsDescriptor)} returning
     * {@code false}</li>
     * </ul>
     * But whether to check against the whole list or only a subset is a choice of the implementation.
     *
     * @param restrictions the sublist of {@link CredentialsProviderManager#getRestrictions()} that return {@code this}
     *                     from {@link CredentialsProviderTypeRestriction#getDescriptor()}
     * @param provider     the {@link CredentialsProvider} to check.
     * @param type         the {@link CredentialsDescriptor} to check.
     * @return {@code true} if and only if the supplied {@link CredentialsDescriptor} is permitted in the scope of
     * the supplied {@link CredentialsProvider}
     */
    public abstract boolean filter(List<CredentialsProviderTypeRestriction> restrictions, CredentialsProvider provider,
                                   CredentialsDescriptor type);
}
