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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A filter of {@link CredentialsDescriptor} types scoped to specific {@link CredentialsProvider} instances used by
 * {@link CredentialsProviderManager} to determine which types are applicable to each provider.
 *
 * @see CredentialsProviderFilter
 * @see CredentialsTypeFilter
 * @since 2.0
 */
public abstract class CredentialsProviderTypeRestriction
        extends AbstractDescribableImpl<CredentialsProviderTypeRestriction>
        implements Serializable, ExtensionPoint {

    /**
     * Ensure consistent serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Returns {@code true} if the supplied {@link CredentialsDescriptor} is permitted to be active for the supplied
     * {@link CredentialsProvider}.
     *
     * @param provider the {@link CredentialsProvider} to check.
     * @param type the {@link CredentialsDescriptor} to check.
     * @return {@code true} if and only if the supplied {@link CredentialsProvider} is permitted to be active.
     * @see CredentialsProviderTypeRestrictionDescriptor#filter(List, CredentialsProvider, CredentialsDescriptor) for
     * how multiple instances are combined.
     */
    public abstract boolean filter(CredentialsProvider provider, CredentialsDescriptor type);

    /**
     * {@inheritDoc}
     */
    @Override
    public CredentialsProviderTypeRestrictionDescriptor getDescriptor() {
        return (CredentialsProviderTypeRestrictionDescriptor) super.getDescriptor();
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
     * A whitelist of types for each provider. If you specify any {@link Includes} for any specific
     * {@link #getProvider()} then at least one {@link Includes} for that {@link #getProvider()} must match for a
     * {@link #getType()} to be permitted.
     *
     * @since 2.0
     */
    public static class Includes extends CredentialsProviderTypeRestriction {
        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;
        /**
         * The {@link CredentialsProvider} {@link Class#getName()}.
         */
        private final String provider;
        /**
         * The {@link CredentialsDescriptor} {@link Class#getName()}.
         */
        private final String type;

        /**
         * Our constructor.
         *
         * @param provider the {@link CredentialsProvider} {@link Class#getName()}.
         * @param type     the {@link CredentialsDescriptor} {@link Class#getName()}.
         */
        @DataBoundConstructor
        public Includes(String provider, String type) {
            this.provider = provider;
            this.type = type;
        }

        /**
         * Returns the {@link CredentialsProvider} {@link Class#getName()}.
         *
         * @return the {@link CredentialsProvider} {@link Class#getName()}.
         */
        public String getProvider() {
            return provider;
        }

        /**
         * Returns the {@link CredentialsDescriptor} {@link Class#getName()}.
         *
         * @return the {@link CredentialsDescriptor} {@link Class#getName()}.
         */
        public String getType() {
            return type;
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

            if (!provider.equals(includes.provider)) {
                return false;
            }
            return type.equals(includes.type);

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int result = provider.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Includes{");
            sb.append("provider='").append(provider).append('\'');
            sb.append(", type='").append(type).append('\'');
            sb.append('}');
            return sb.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filter(CredentialsProvider provider, CredentialsDescriptor type) {
            return provider.getClass().getName().equals(this.provider)
                    && type.getClass().getName().equals(this.type);
        }

        /**
         * Our descriptor
         *
         * @since 2.0
         */
        @Extension
        public static class DescriptorImpl extends CredentialsProviderTypeRestrictionDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean filter(List<CredentialsProviderTypeRestriction> restrictions, CredentialsProvider provider,
                                  CredentialsDescriptor type) {
                // includes is must match one if there are any for this provider
                // logically equivalent to
                //
                // filtered = restrictions.filter({x -> x.provider == provider})
                // return filtered.isEmpty() ? true : filtered.find({x-> x.type == type});
                //
                // but we do it in a single pass with this code
                boolean haveProviderMatch = false;
                for (CredentialsProviderTypeRestriction r : restrictions) {
                    if (!haveProviderMatch && r instanceof Includes && provider.clazz.getName()
                            .equals(((Includes) r).getProvider())) {
                        haveProviderMatch = true;
                    }
                    if (r.filter(provider, type)) {
                        return true;
                    }
                }
                return !haveProviderMatch;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.CredentialsProviderTypeRestriction_Includes_DisplayName();
            }

            /**
             * Returns the list box for provider selection.
             *
             * @return the list box for provider selection.
             */
            @SuppressWarnings("unused") // stapler form completion
            @Restricted(NoExternalUse.class)
            public ListBoxModel doFillProviderItems() {
                ListBoxModel result = new ListBoxModel();
                for (CredentialsProvider p : ExtensionList.lookup(CredentialsProvider.class)) {
                    result.add(p.getDisplayName(), p.getClass().getName());
                }
                return result;
            }

            /**
             * Returns the list box for type selection.
             *
             * @return the list box for type selection.
             */
            @SuppressWarnings("unused") // stapler form completion
            @Restricted(NoExternalUse.class)
            public ListBoxModel doFillTypeItems() {
                ListBoxModel result = new ListBoxModel();
                for (CredentialsDescriptor d : ExtensionList.lookup(CredentialsDescriptor.class)) {
                    result.add(d.getDisplayName(), d.getClass().getName());
                }
                return result;
            }
        }
    }

    /**
     * A blacklist of types for each provider.
     *
     * @since 2.0
     */
    public static class Excludes extends CredentialsProviderTypeRestriction {
        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;
        /**
         * The {@link CredentialsProvider} {@link Class#getName()}.
         */
        private final String provider;
        /**
         * The {@link CredentialsDescriptor} {@link Class#getName()}.
         */
        private final String type;

        /**
         * Our constructor.
         *
         * @param provider the {@link CredentialsProvider} {@link Class#getName()}.
         * @param type     the {@link CredentialsDescriptor} {@link Class#getName()}.
         */
        @DataBoundConstructor
        public Excludes(String provider, String type) {
            this.provider = provider;
            this.type = type;
        }

        /**
         * Returns the {@link CredentialsProvider} {@link Class#getName()}.
         *
         * @return the {@link CredentialsProvider} {@link Class#getName()}.
         */
        public String getProvider() {
            return provider;
        }

        /**
         * Returns the {@link CredentialsDescriptor} {@link Class#getName()}.
         *
         * @return the {@link CredentialsDescriptor} {@link Class#getName()}.
         */
        public String getType() {
            return type;
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

            if (!provider.equals(excludes.provider)) {
                return false;
            }
            return type.equals(excludes.type);

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int result = provider.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Excludes{");
            sb.append("provider='").append(provider).append('\'');
            sb.append(", type='").append(type).append('\'');
            sb.append('}');
            return sb.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filter(CredentialsProvider provider, CredentialsDescriptor type) {
            return !(provider.getClass().getName().equals(this.provider)
                    && type.getClass().getName().equals(this.type));
        }

        /**
         * Our descriptor
         *
         * @since 2.0
         */
        @Extension
        public static class DescriptorImpl extends CredentialsProviderTypeRestrictionDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean filter(List<CredentialsProviderTypeRestriction> restrictions, CredentialsProvider provider,
                                  CredentialsDescriptor type) {
                // excludes is must not match any
                for (CredentialsProviderTypeRestriction r : restrictions) {
                    if (!r.filter(provider, type)) {
                        return false;
                    }
                }
                return true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.CredentialsProviderTypeRestriction_Excludes_DisplayName();
            }

            /**
             * Returns the list box for provider selection.
             *
             * @return the list box for provider selection.
             */
            @SuppressWarnings("unused") // stapler form completion
            @Restricted(NoExternalUse.class)
            public ListBoxModel doFillProviderItems() {
                ListBoxModel result = new ListBoxModel();
                for (CredentialsProvider p : ExtensionList.lookup(CredentialsProvider.class)) {
                    result.add(p.getDisplayName(), p.getClass().getName());
                }
                return result;
            }

            /**
             * Returns the list box for type selection.
             *
             * @return the list box for type selection.
             */
            @SuppressWarnings("unused") // stapler form completion
            @Restricted(NoExternalUse.class)
            public ListBoxModel doFillTypeItems() {
                ListBoxModel result = new ListBoxModel();
                for (CredentialsDescriptor d : ExtensionList.lookup(CredentialsDescriptor.class)) {
                    result.add(d.getDisplayName(), d.getClass().getName());
                }
                return result;
            }
        }
    }
}
