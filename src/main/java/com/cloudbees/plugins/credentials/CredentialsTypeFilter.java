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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A filter of {@link CredentialsDescriptor} types used by {@link CredentialsProviderManager} to determine which
 * types are active.
 *
 * @since 2.0
 */
public abstract class CredentialsTypeFilter extends AbstractDescribableImpl<CredentialsTypeFilter>
        implements Serializable, ExtensionPoint {
    /**
     * Ensure consistent serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Returns {@code true} if and only if the supplied {@link CredentialsDescriptor} is permitted to be active.
     *
     * @param type the {@link CredentialsDescriptor} to check.
     * @return {@code true} if and only if the supplied {@link CredentialsDescriptor} is permitted to be active.
     */
    public abstract boolean filter(CredentialsDescriptor type);

    /**
     * {@inheritDoc}
     */
    @Override
    public CredentialsTypeFilterDescriptor getDescriptor() {
        return (CredentialsTypeFilterDescriptor) super.getDescriptor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract int hashCode();

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String toString();

    /**
     * A filter that does not filter anything.
     *
     * @since 2.0
     */
    public static class None extends CredentialsTypeFilter {
        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Our constructor.
         */
        @DataBoundConstructor
        public None() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filter(CredentialsDescriptor type) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o != null && getClass() == o.getClass();

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return None.class.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "None{}";
        }

        /**
         * Our descriptor.
         *
         * @since 2.0
         */
        @Extension
        public static class DescriptorImpl extends CredentialsTypeFilterDescriptor {

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.CredentialsTypeFilter_None_DisplayName();
            }
        }
    }

    /**
     * A filter that implements a allowlist policy, "if you are not on the list you can't come in".
     *
     * @since 2.0
     */
    public static class Includes extends CredentialsTypeFilter {
        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;
        /**
         * The set of classes that will be allowed.
         */
        private final Set<String> classNames;

        /**
         * Our constructor.
         *
         * @param classNames the list of included class names.
         */
        @DataBoundConstructor
        public Includes(@CheckForNull List<String> classNames) {
            this.classNames = new LinkedHashSet<>(Util.fixNull(classNames));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filter(CredentialsDescriptor type) {
            return classNames.contains(type.getId());
        }

        /**
         * Returns the list of allowed {@link Class#getName()}.
         *
         * @return the list of allowed {@link Class#getName()}.
         */
        @NonNull
        public List<String> getClassNames() {
            return new ArrayList<>(classNames);
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

            Includes includes = (Includes) o;

            return classNames.equals(includes.classNames);

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return classNames.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "Includes{" + "classes=" + getClassNames() +
                    '}';
        }

        /**
         * Our descriptor.
         *
         * @since 2.0
         */
        @Extension
        public static class DescriptorImpl extends CredentialsTypeFilterDescriptor {

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.CredentialsTypeFilter_Includes_DisplayName();
            }

            /**
             * Gets the full list of available types without any filtering.
             *
             * @return the full list of available types without any filtering.
             */
            @SuppressWarnings("unused")
            @Restricted(NoExternalUse.class) // stapler EL binding
            public List<CredentialsDescriptor> getTypeDescriptors() {
                return ExtensionList.lookup(CredentialsDescriptor.class);
            }

        }
    }

    /**
     * A filter that implements a denylist policy, "if you are not on the list you can come in".
     *
     * @since 2.0
     */
    public static class Excludes extends CredentialsTypeFilter {
        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;
        /**
         * The set of classes that will not be allowed.
         */
        private final Set<String> classNames;

        /**
         * Our constructor.
         *
         * @param classNames the list of excluded class names.
         */
        @DataBoundConstructor
        public Excludes(@CheckForNull List<String> classNames) {
            this.classNames = new LinkedHashSet<>(Util.fixNull(classNames));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filter(CredentialsDescriptor type) {
            return !classNames.contains(type.getId());
        }

        /**
         * Returns the list of banned {@link Class#getName()}.
         *
         * @return the list of banned {@link Class#getName()}.
         */
        @NonNull
        public List<String> getClassNames() {
            return new ArrayList<>(classNames);
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

            Excludes excludes = (Excludes) o;

            return classNames.equals(excludes.classNames);

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return classNames.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "Excludes{" + "classes=" + getClassNames() +
                    '}';
        }

        /**
         * Our descriptor.
         *
         * @since 2.0
         */
        @Extension
        public static class DescriptorImpl extends CredentialsTypeFilterDescriptor {

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.CredentialsTypeFilter_Excludes_DisplayName();
            }

            /**
             * Gets the full list of available types without any filtering.
             *
             * @return the full list of available types without any filtering.
             */
            @SuppressWarnings("unused")
            @Restricted(NoExternalUse.class) // stapler EL binding
            public List<CredentialsDescriptor> getTypeDescriptors() {
                return ExtensionList.lookup(CredentialsDescriptor.class);
            }

        }
    }
}
