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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link DomainSpecification} that matches {@link HostnameRequirement} where
 * the <var>hostname</var> is on an {@link #includes} list but not on an {@link #excludes}
 * list. The {@link #includes} and {@link #excludes} lists are comma separated
 * <var>hostname</var> with <code>*</code> wildcards supported.
 *
 * @since 1.5
 */
public class HostnameSpecification extends DomainSpecification {

    /**
     * Hostnames to match. A comma separated set of
     * <var>hostname</var> with <code>*</code> wildcards supported.
     * {@code null} signifies include everything.
     */
    @CheckForNull
    private final String includes;

    /**
     * Hostnames to explicitly not match. A comma separated set of
     * <var>hostname</var> with <code>*</code> wildcards supported.
     * {@code null} signifies exclude nothing.
     */
    @CheckForNull
    private final String excludes;

    /**
     * Constructor for stapler.
     *
     * @param includes Hostnames to match. A comma separated set of
     *                 <var>hostname</var> with <code>*</code> wildcards supported.
     *                 {@code null} signifies include everything.
     * @param excludes Hostnames to explicitly not match. A comma separated set of
     *                 <var>hostname</var> with <code>*</code> wildcards supported.
     *                 {@code null} signifies exclude nothing.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused")// by stapler
    public HostnameSpecification(String includes, String excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    /**
     * Returns the hostnames to match. A comma separated set of
     * <var>hostname</var> with <code>*</code> wildcards supported.
     * {@code null} signifies include everything.
     *
     * @return the hostnames to match.
     */
    @CheckForNull
    @SuppressWarnings("unused")// by stapler
    public String getIncludes() {
        return includes;
    }

    /**
     * Returns the hostnames to explicitly not match. A comma separated set of
     * <var>hostname</var> with <code>*</code> wildcards supported.
     * {@code null} signifies include everything.
     *
     * @return the hostnames to explicitly not match.
     */
    @CheckForNull
    @SuppressWarnings("unused")// by stapler
    public String getExcludes() {
        return excludes;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Result test(@NonNull DomainRequirement requirement) {
        if (requirement instanceof HostnameRequirement) {
            String hostname = ((HostnameRequirement) requirement).getHostname();
            if (includes != null) {
                boolean isInclude = false;
                for (String include : includes.split("[,\\n ]")) {
                    include = Util.fixEmptyAndTrim(include);
                    if (include == null) {
                        continue;
                    }
                    if (FilenameUtils.wildcardMatch(hostname, include, IOCase.INSENSITIVE)) {
                        isInclude = true;
                        break;
                    }
                }
                if (!isInclude) {
                    return Result.NEGATIVE;
                }
            }
            if (excludes != null) {
                boolean isExclude = false;
                for (String exclude : excludes.split("[,\\n ]")) {
                    exclude = Util.fixEmptyAndTrim(exclude);
                    if (exclude == null) {
                        continue;
                    }
                    if (FilenameUtils.wildcardMatch(hostname, exclude, IOCase.INSENSITIVE)) {
                        isExclude = true;
                        break;
                    }
                }
                if (isExclude) {
                    return Result.NEGATIVE;
                }
            }
            return Result.PARTIAL;
        }
        return Result.UNKNOWN;
    }

    /**
     * Our {@link hudson.model.Descriptor}.
     */
    @Extension
    @SuppressWarnings("unused")// by stapler
    public static class DescriptorImpl extends DomainSpecificationDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.HostnameSpecification_DisplayName();
        }
    }
}
