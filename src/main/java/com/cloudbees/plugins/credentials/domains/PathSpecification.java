/*
 * The MIT License
 *
 * Copyright (c) 2011-2014, CloudBees, Inc., Stephen Connolly.
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
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.util.AntPathMatcher;

/**
 * A {@link DomainSpecification} that matches {@link PathRequirement}s where the URI path is on a whitelist
 * of paths.
 *
 * @see <a href="http://tools.ietf.org/rfc/rfc3986.txt">RFC-3986 Section 3.1</a>
 * @since 1.12
 */
public class PathSpecification extends DomainSpecification {

    /**
     * Paths to match. A comma separated set of
     * <var>path</var> with <code>*</code> wildcards supported.
     * {@code null} signifies include everything.
     */
    @CheckForNull
    private final String includes;
    /**
     * Paths to explicitly not match. A comma separated set of
     * <var>path</var> with <code>*</code> wildcards supported.
     * {@code null} signifies exclude nothing.
     */
    @CheckForNull
    private final String excludes;
    /**
     * Flag to indicate that the patch specification is case sensitive.
     */
    private final boolean caseSensitive;

    /**
     * Constructor for stapler.
     *
     * @param includes      Paths to match. A comma separated set of
     *                      <var>path</var> with <code>*</code> wildcards supported.
     *                      {@code null} signifies include everything.
     * @param excludes      Paths to explicitly not match. A comma separated set of
     *                      <var>path</var> with <code>*</code> wildcards supported.
     *                      {@code null} signifies exclude nothing.
     * @param caseSensitive {@code true} if the path match should be case sensitive.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused")// by stapler
    public PathSpecification(String includes, String excludes, boolean caseSensitive) {
        this.includes = includes;
        this.excludes = excludes;
        this.caseSensitive = caseSensitive;
    }

    /**
     * Returns the paths to match. A whitespace separated set of
     * <var>path</var> with <code>*</code> wildcards supported.
     * {@code null} signifies include everything.
     *
     * @return the paths to match.
     */
    @CheckForNull
    @SuppressWarnings("unused")// by stapler
    public String getIncludes() {
        return includes;
    }

    /**
     * Returns the paths to explicitly not match. A comma separated set of
     * <var>path</var> with <code>*</code> wildcards supported.
     * {@code null} signifies include everything.
     *
     * @return the paths to explicitly not match.
     */
    @CheckForNull
    @SuppressWarnings("unused")// by stapler
    public String getExcludes() {
        return excludes;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Result test(@NonNull DomainRequirement requirement) {
        if (requirement instanceof PathRequirement) {
            final AntPathMatcher matcher = new AntPathMatcher();
            String path = ((PathRequirement) requirement).getPath();
            if (!caseSensitive) {
                path = path.toLowerCase();
            }
            if (includes != null) {
                boolean isInclude = false;
                for (String include : includes.split(",")) {
                    include = Util.fixEmptyAndTrim(include);
                    if (include == null) {
                        continue;
                    }
                    if (!caseSensitive) {
                        include = include.toLowerCase();
                    }
                    if (matcher.isPattern(include) ? matcher.match(include, path) : StringUtils.equals(include, path)) {
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
                for (String exclude : excludes.split(",")) {
                    exclude = Util.fixEmptyAndTrim(exclude);
                    if (exclude == null) {
                        continue;
                    }
                    if (!caseSensitive) {
                        exclude = exclude.toLowerCase();
                    }
                    if (matcher.isPattern(exclude) ? matcher.match(exclude, path) : StringUtils.equals(exclude, path)) {
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
            return Messages.PathSpecification_DisplayName();
        }
    }
}
