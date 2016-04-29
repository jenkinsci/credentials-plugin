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
import java.util.LinkedHashSet;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link DomainSpecification} that matches {@link SchemeRequirement}s where the URI scheme is on a whitelist
 * of schemes.
 *
 * @see <a href="http://tools.ietf.org/rfc/rfc3986.txt">RFC-3986 Section 3.1</a>
 * @since 1.5
 */
public class SchemeSpecification extends DomainSpecification {

    /**
     * The schemes that this specification matches.
     */
    @NonNull
    private final Set<String> schemes;

    /**
     * Constructor for stapler.
     *
     * @param schemes A comma separated list of scheme names.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused")// by stapler
    public SchemeSpecification(@CheckForNull String schemes) {
        this.schemes = new LinkedHashSet<String>();
        schemes = Util.fixEmptyAndTrim(schemes);
        if (schemes != null) {
            for (String scheme : schemes.split("[,\\n ]")) {
                scheme = Util.fixEmptyAndTrim(scheme);
                if (scheme != null) {
                    this.schemes.add(toWellFormedScheme(scheme));
                }
            }
        }
    }

    /**
     * Tidy up the provided scheme.
     *
     * @param scheme the scheme.
     * @return tidy form of provided scheme.
     */
    @NonNull
    private static String toWellFormedScheme(@NonNull String scheme) {
        scheme = scheme.toLowerCase(); // RFC-3986 mandates that scheme's are compared as lowercase
        int index = scheme.indexOf(':'); // do not include the ':'
        if (index != -1) {
            scheme = scheme.substring(0, index);
        }
        return scheme;
    }

    /**
     * Returns the comma separated list of URI schemes that this specification matches.
     *
     * @return the comma separated list of URI schemes that this specification matches.
     */
    @CheckForNull
    @SuppressWarnings("unused")// by stapler
    public String getSchemes() {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (String scheme : schemes) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(scheme);
        }
        return Util.fixEmptyAndTrim(buf.toString());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Result test(@NonNull DomainRequirement requirement) {
        if (requirement instanceof SchemeRequirement) {
            String scheme = toWellFormedScheme(((SchemeRequirement) requirement).getScheme());
            if (!schemes.isEmpty() && schemes.contains(scheme)) {
                // we know the scheme is an exact test, no need to check this requirement any more
                return Result.POSITIVE;
            }
            return Result.NEGATIVE;
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
            return Messages.SchemeSpecification_DisplayName();
        }
    }
}
