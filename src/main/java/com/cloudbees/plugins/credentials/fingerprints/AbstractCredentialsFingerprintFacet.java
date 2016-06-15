/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.plugins.credentials.fingerprints;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.Fingerprint;
import jenkins.model.FingerprintFacet;

/**
 * Abstract class indicating {@link FingerprintFacet}s related to {@link Credentials}.
 * The facet may include the following optional resources (jelly, groovy, etc.):
 * <ul>
 * <li>{@literal summary} - short summary of the facet for tables</li>
 * </ul>
 *
 * @since 2.1.1
 */
public abstract class AbstractCredentialsFingerprintFacet extends FingerprintFacet {
    /**
     * Constructor.
     *
     * @param fingerprint {@link Fingerprint} object to which this facet is going to be added to.
     * @param timestamp   timestamp when the use happened (milliseconds since midnight Jan 1, 1970 UTC).
     */
    public AbstractCredentialsFingerprintFacet(Fingerprint fingerprint, long timestamp) {
        super(fingerprint, timestamp);
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

        AbstractCredentialsFingerprintFacet node = (AbstractCredentialsFingerprintFacet) o;

        return getTimestamp() == node.getTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        long value = getTimestamp();
        return (int) (value ^ (value >>> 32));
    }

}
