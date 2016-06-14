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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.model.Item;
import jenkins.model.Jenkins;

/**
 * A facet to track the usage of a credential for a specific item outside of the context of a build, for example SCM
 * polling.
 *
 * @since 2.1.1
 */
public class ItemCredentialsFingerprintFacet extends AbstractCredentialsFingerprintFacet {
    /**
     * The full name of the {@link Item}.
     */
    @NonNull
    private final String itemFullName;
    /**
     * The timestamp of first usage.
     */
    private final long timestamp0;

    /**
     * Constructor.
     *
     * @param item        the item
     * @param fingerprint {@link Fingerprint} object to which this facet is going to be added to.
     * @param timestamp   timestamp when the use happened (milliseconds since midnight Jan 1, 1970 UTC).
     */
    public ItemCredentialsFingerprintFacet(@NonNull Item item, @NonNull Fingerprint fingerprint, long timestamp) {
        this(item, fingerprint, timestamp, timestamp);
    }

    /**
     * Constructor.
     *
     * @param item        the item
     * @param fingerprint {@link Fingerprint} object to which this facet is going to be added to.
     * @param timestamp0  timestamp when the first usage happened.
     * @param timestamp   timestamp when the use happened (milliseconds since midnight Jan 1, 1970 UTC).
     */
    public ItemCredentialsFingerprintFacet(@NonNull Item item, @NonNull Fingerprint fingerprint, long timestamp0,
                                           long timestamp) {
        super(fingerprint, Math.max(timestamp, timestamp0));
        this.itemFullName = item.getFullName();
        this.timestamp0 = Math.min(timestamp, timestamp0);
    }

    /**
     * Returns the {@link Item#getFullName()}.
     *
     * @return the {@link Item#getFullName()}.
     */
    @NonNull
    public String getItemFullName() {
        return itemFullName;
    }

    /**
     * Returns the {@link Item}.
     *
     * @return the {@link Item} or {@code null} if either the item no longer exists or the current authentication
     * does not have permission to access the item.
     */
    @CheckForNull
    public Item getItem() {
        return Jenkins.getActiveInstance().getItemByFullName(itemFullName);
    }

    /**
     * Returns the timestamp of first usage.
     *
     * @return the timestamp of first usage.
     */
    public long getTimestamp0() {
        return timestamp0;
    }

    /**
     * Returns the timestamp range as a string.
     *
     * @return the timestamp range as a string.
     */
    public String getTimestampString() {
        long now = System.currentTimeMillis();
        return timestamp0 == getTimestamp()
                ? Messages.AbstractCredentialsFingerprintFacet_timestampSingle(
                Util.getPastTimeString(now - getTimestamp())
        )
                : Messages.AbstractCredentialsFingerprintFacet_timestampRange(
                        Util.getPastTimeString(now - timestamp0),
                        Util.getPastTimeString(now - getTimestamp())
                );
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
        if (!super.equals(o)) {
            return false;
        }

        ItemCredentialsFingerprintFacet node = (ItemCredentialsFingerprintFacet) o;

        if (getTimestamp0() != node.getTimestamp0()) {
            return false;
        }
        return getItemFullName().equals(node.getItemFullName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getItemFullName().hashCode();
        result = 31 * result + (int) (getTimestamp0() ^ (getTimestamp0() >>> 32));
        return result;
    }
}
