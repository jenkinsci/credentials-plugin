/*
 * The MIT License
 *
 * Copyright (c) 2015 Jacob Keller
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
package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.Secret;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents a set of {@link Credentials}, each meant to be used for a
 * specific set of {@link DomainSpecification}s
 *
 */
public class CredentialsSet extends BaseStandardCredentials {
    /**
     * The map.
     */
    @NonNull
    private final Map<Set<DomainSpecification>,String> set;

    /**
     * Stapler's constructor.
     *
     * @param map         the map of domain specifications to credential Id's
     */
    @DataBoundConstructor
    public CredentialsSet(@CheckForNull CredentialsScope scope,
                          @CheckForNull String id, @CheckForNull String description,
                          @CheckForNull Map<Set<DomainSpecification>, String> map) {
        super(scope, id, description);
        this.set = map == null ? new HashMap<Set<DomainSpecification>, String>() : map;
    }
}
