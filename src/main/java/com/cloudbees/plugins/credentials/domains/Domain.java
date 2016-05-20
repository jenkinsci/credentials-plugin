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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A domain, within which credentials are common. For example a company may have a single sign-on domain where
 * a bunch of web applications, source control systems, issue trackers, etc all share the same username/password backing
 * database.
 */
public class Domain implements Serializable {

    /**
     * Set serialization version.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The name of the domain, or {@code null} if the special "global" domain.
     */
    @CheckForNull
    private final String name;

    /**
     * The description of the domain.
     */
    @CheckForNull
    private final String description;

    /**
     * The specifications that define the domain.
     */
    @NonNull
    private final List<DomainSpecification> specifications;

    @DataBoundConstructor
    public Domain(String name, String description, List<DomainSpecification> specifications) {
        this.name = Util.fixEmptyAndTrim(name);
        this.description = Util.fixEmptyAndTrim(description);
        this.specifications = specifications == null
                ? new ArrayList<DomainSpecification>()
                : new ArrayList<DomainSpecification>(specifications);
    }

    /**
     * Returns the special "global" domain.
     *
     * @return the special "global" domain.
     */
    @NonNull
    public static Domain global() {
        return ResourceHolder.GLOBAL;
    }

    /**
     * Resolve on deserialization.
     *
     * @return the instance.
     * @throws ObjectStreamException if something goes wrong.
     */
    private Object readResolve() throws ObjectStreamException {
        return resolve();
    }

    /**
     * Resolve the correct domain instance.
     *
     * @return the correct domain instance (i.e. replaces the global domain with {@link #global()}.
     */
    @SuppressWarnings("ConstantConditions")
    public Domain resolve() {
        if (Util.fixEmptyAndTrim(name) == null && Util.fixEmptyAndTrim(description) == null
                && (specifications == null || specifications.isEmpty())) {
            return global();
        }
        return this;
    }

    /**
     * Returns the description of this domain.
     *
     * @return the description of this domain.
     */
    @CheckForNull
    @SuppressWarnings("unused") // by stapler
    public String getDescription() {
        return description;
    }

    /**
     * Returns the {@link DomainSpecification}s for this {@link Domain}.
     *
     * @return the {@link DomainSpecification}s for this {@link Domain}.
     */
    @NonNull
    @SuppressWarnings("unused") // by stapler
    public List<DomainSpecification> getSpecifications() {
        return Collections.unmodifiableList(specifications);
    }

    /**
     * Returns the name of the domain.
     *
     * @return the name of the domain.
     */
    @CheckForNull
    @SuppressWarnings("unused") // by stapler
    public String getName() {
        return name;
    }

    /**
     * Return the store relative URL of this domain.
     *
     * @return the store relative URL of this domain.
     */
    @SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF",
                        justification = "isGlobal() check implies that domain.getName() is null")
    public String getUrl() {
        return isGlobal() ? "domain/_/" : "domain/" + Util.rawEncode(name) + "/";
    }

    /**
     * Tests if this is the {@link #global()} domain.
     *
     * @return {@code true} if and only if this is the {@link #global()} domain.
     * @since 2.0
     */
    public boolean isGlobal() {
        return equals(global());
    }

    /**
     * Returns {@code true} if and only if the supplied {@link DomainRequirement}s are a match for this {@link Domain}.
     *
     * @param requirements the {@link DomainRequirement}s  to test.
     * @return {@code true} if and only if the supplied {@link DomainRequirement}s are a match for this {@link Domain}.
     */
    public boolean test(DomainRequirement... requirements) {
        return test(Arrays.asList(requirements));
    }

    /**
     * Returns {@code true} if and only if the supplied {@link DomainRequirement}s are a match for this {@link Domain}.
     *
     * @param requirements the {@link DomainRequirement}s  to test.
     * @return {@code true} if and only if the supplied {@link DomainRequirement}s are a match for this {@link Domain}.
     */
    public boolean test(@NonNull List<DomainRequirement> requirements) {
        for (DomainRequirement scope : requirements) {
            if (scope == null) {
                continue;
            }
            for (DomainSpecification matcher : specifications) {
                DomainSpecification.Result result = matcher.test(scope);
                if (result.isDefinitive()) {
                    if (result.isMatch()) {
                        // we have matched this scope => continue with next scope
                        break;
                    } else {
                        // we have a non-test => done
                        return false;
                    }
                }
                // continue as nothing is definitive
            }
        }
        // must be a test
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Domain domain = (Domain) o;

        if (name != null ? !name.equals(domain.name) : domain.name != null) {
            return false;
        }

        return true;
    }

    /**
     * Lazy singleton thread-safe initialization.
     */
    private static final class ResourceHolder {
        private static final Domain GLOBAL = new Domain(null, null, Collections.<DomainSpecification>emptyList());
    }
}
