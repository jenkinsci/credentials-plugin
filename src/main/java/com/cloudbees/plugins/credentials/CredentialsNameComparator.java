/*
 * The MIT License
 *
 * Copyright (c) 2016, Andrae Ray, Stephen Connolly, CloudBees, Inc..
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
import java.io.Serializable;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Comparator to alphabetically sort credentials drop down list in ascending order by Credential Name.
 *
 * @since 1.28
 */
public class CredentialsNameComparator implements Comparator<Credentials>, Serializable {
    /**
     * Standardize serialization format.
     */
    private static final long serialVersionUID = 1;
    /**
     * The locale to compare with.
     */
    private final Locale locale;
    /**
     * Whether to ignore case when comparing names.
     */
    private final boolean ignoreCase;
    /**
     * The {@link Collator}
     */
    private transient Collator collator;

    /**
     * Creates a new instance that case insensitively sorts names using the the web request locale (if invoked from a
     * web request
     * handling thread) or the default locale (if invoked outside of a web request thread.
     */
    public CredentialsNameComparator() {
        this(null, true);
    }

    /**
     * Creates a new instance that case insensitively sorts names using the specified locale.
     *
     * @param locale the locale to use or {@code null} to use the web request locale (if invoked from a web request
     *               handling thread) or the default locale (if invoked outside of a web request thread.
     */
    public CredentialsNameComparator(@CheckForNull Locale locale) {
        this(locale, true);
    }

    /**
     * Creates a new instance that sorts names using the specified locale.
     *
     * @param locale     the locale to use or {@code null} to use the web request locale (if invoked from a web request
     *                   handling thread) or the default locale (if invoked outside of a web request thread.
     * @param ignoreCase if {@code true} then the names will be compared ignoring case.
     */
    public CredentialsNameComparator(@CheckForNull Locale locale, boolean ignoreCase) {
        if (locale == null) {
            StaplerRequest2 req = Stapler.getCurrentRequest2();
            if (req != null) {
                locale = req.getLocale();
            }
            if (locale == null) {
                locale = Locale.getDefault();
            }
        }
        this.locale = locale;
        this.ignoreCase = ignoreCase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compare(Credentials c1, Credentials c2) {
        final String n1 = StringUtils.defaultString(CredentialsNameProvider.name(c1));
        final String n2 = StringUtils.defaultString(CredentialsNameProvider.name(c2));
        if (collator == null) {
            // in the event of a race condition this will be effectively idempotent so no need for synchronization.
            collator = Collator.getInstance(locale);
        }
        return ignoreCase ? collator.compare(n1.toLowerCase(locale), n2.toLowerCase(locale)) : collator.compare(n1, n2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = locale.hashCode();
        result = 31 * result + (ignoreCase ? 1 : 0);
        return result;
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

        CredentialsNameComparator that = (CredentialsNameComparator) o;

        if (ignoreCase != that.ignoreCase) {
            return false;
        }
        return locale.equals(that.locale);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "CredentialsNameComparator{" + "locale=" + locale +
                ", ignoreCase=" + ignoreCase +
                '}';
    }
}
