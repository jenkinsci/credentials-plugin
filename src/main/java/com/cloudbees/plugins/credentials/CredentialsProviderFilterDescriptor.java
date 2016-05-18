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

/**
 * The base {@link Descriptor} class for all {@link CredentialsProviderFilter} instances.
 *
 * @since 2.0
 */
public abstract class CredentialsProviderFilterDescriptor extends Descriptor<CredentialsProviderFilter> {

    /**
     * Helper method that returns the current {@link CredentialsProviderFilter} effective state for the supplied
     * {@link CredentialsProvider}. Used to ensure that when changing implementation the initial config is equivalent to
     * the current.
     *
     * @param provider the {@link CredentialsProvider} to check.
     * @return {@code true} if and only if the current {@link CredentialsProviderFilter} returns {@code true} for the
     * supplied {@link CredentialsProvider}.
     */
    public boolean filter(CredentialsProvider provider) {
        CredentialsProviderManager manager = CredentialsProviderManager.getInstance();
        return manager == null || manager.getProviderFilter().filter(provider);
    }

}
