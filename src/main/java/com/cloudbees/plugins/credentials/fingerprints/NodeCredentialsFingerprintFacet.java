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
import hudson.model.Node;
import jenkins.model.Jenkins;

/**
 * Tracks usage of a credential by a {@link Node}.
 *
 * @since 2.1.1
 */
public class NodeCredentialsFingerprintFacet extends AbstractCredentialsFingerprintFacet {
    /**
     * The node name.
     */
    @NonNull
    private final String nodeName;
    /**
     * The timestamp of first usage.
     */
    private final long timestamp0;

    /**
     * Constructor.
     *
     * @param node        the node
     * @param fingerprint {@link Fingerprint} object to which this facet is going to be added to.
     * @param timestamp   timestamp when the use happened (milliseconds since midnight Jan 1, 1970 UTC).
     */
    public NodeCredentialsFingerprintFacet(@NonNull Node node, Fingerprint fingerprint, long timestamp) {
        this(node, fingerprint, timestamp, timestamp);
    }

    /**
     * Constructor.
     *
     * @param node        the node
     * @param fingerprint {@link Fingerprint} object to which this facet is going to be added to.
     * @param timestamp0  timestamp when the first usage happened.
     * @param timestamp   timestamp when the use happened (milliseconds since midnight Jan 1, 1970 UTC).
     */
    public NodeCredentialsFingerprintFacet(@NonNull Node node, Fingerprint fingerprint, long timestamp0,
                                           long timestamp) {
        super(fingerprint, Math.max(timestamp, timestamp0));
        this.nodeName = node.getNodeName();
        this.timestamp0 = Math.min(timestamp, timestamp0);
    }

    /**
     * Returns the {@link Node#getNodeName()}.
     *
     * @return the {@link Node#getNodeName()}.
     */
    @NonNull
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Returns the {@link Node}.
     *
     * @return the {@link Node} or {@code null} if either the node no longer exists or the current authentication
     * does not have permission to access the node.
     */
    @CheckForNull
    public Node getNode() {
        return nodeName.isEmpty() ? Jenkins.getActiveInstance() : Jenkins.getActiveInstance().getNode(nodeName);
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

        NodeCredentialsFingerprintFacet node = (NodeCredentialsFingerprintFacet) o;

        if (getTimestamp0() != node.getTimestamp0()) {
            return false;
        }
        return getNodeName().equals(node.getNodeName());

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getNodeName().hashCode();
        result = 31 * result + (int) (getTimestamp0() ^ (getTimestamp0() >>> 32));
        return result;
    }
}
