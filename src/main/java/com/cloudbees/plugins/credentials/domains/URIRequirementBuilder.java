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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A builder to help creating requirements from URIs.
 *
 * @since 1.6
 */
public class URIRequirementBuilder {
    /**
     * The list of requirements.
     */
    @NonNull
    private final List<DomainRequirement> requirements;

    /**
     * Private constructor.
     *
     * @param requirements the list of requirements.
     */
    private URIRequirementBuilder(@NonNull List<DomainRequirement> requirements) {
        this.requirements = new ArrayList<DomainRequirement>(requirements);
    }

    /**
     * Creates an empty builder.
     *
     * @return a new empty builder.
     */
    @NonNull
    public static URIRequirementBuilder create() {
        return new URIRequirementBuilder(Collections.<DomainRequirement>emptyList());
    }

    /**
     * Creates a new builder using the supplied URI.
     *
     * @param uri the URI to create the requirements of.
     * @return a new builder with the requirements of the supplied URI.
     */
    @NonNull
    public static URIRequirementBuilder fromUri(@CheckForNull String uri) {
        return create().withUri(uri);
    }

    /**
     * Creates a new builder with the same requirements as this builder.
     *
     * @return a new builder with the same requirements as this builder.
     */
    @NonNull
    public URIRequirementBuilder duplicate() {
        return new URIRequirementBuilder(requirements);
    }

    /**
     * Replaces the requirements with those of the supplied URI.
     *
     * @param uri the URI.
     * @return {@code this}.
     */
    @NonNull
    public URIRequirementBuilder withUri(@CheckForNull String uri) {
        if (uri != null) {
            try {
                URI u = new URI(uri);
                withScheme(u.getScheme());
                withHostnamePort(u.getHost(), u.getPort());
                withPath(u.getRawPath());
            } catch (URISyntaxException e) {
                withoutScheme().withoutHostname().withoutHostnamePort();
            }
        }
        return this;
    }

    /**
     * Removes any scheme requirements.
     *
     * @return {@code this}.
     */
    @NonNull
    public URIRequirementBuilder withoutScheme() {
        for (Iterator<DomainRequirement> iterator = requirements.iterator(); iterator.hasNext(); ) {
            DomainRequirement r = iterator.next();
            if (r instanceof SchemeRequirement) {
                iterator.remove();
            }
        }
        return this;
    }

    /**
     * Removes any path requirements.
     *
     * @return {@code this}.
     * @since 1.12
     */
    @NonNull
    public URIRequirementBuilder withoutPath() {
        for (Iterator<DomainRequirement> iterator = requirements.iterator(); iterator.hasNext(); ) {
            DomainRequirement r = iterator.next();
            if (r instanceof PathRequirement) {
                iterator.remove();
            }
        }
        return this;
    }

    /**
     * Removes any hostname or hostname:port requirements.
     *
     * @return {@code this}.
     */
    @NonNull
    public URIRequirementBuilder withoutHostname() {
        for (Iterator<DomainRequirement> iterator = requirements.iterator(); iterator.hasNext(); ) {
            DomainRequirement r = iterator.next();
            if (r instanceof HostnameRequirement) {
                iterator.remove();
            }
        }
        return this;
    }

    /**
     * Removes any hostname:port requirements.
     *
     * @return {@code this}.
     */
    @NonNull
    public URIRequirementBuilder withoutHostnamePort() {
        for (Iterator<DomainRequirement> iterator = requirements.iterator(); iterator.hasNext(); ) {
            DomainRequirement r = iterator.next();
            if (r instanceof HostnamePortRequirement) {
                iterator.remove();
            }
        }
        return this;
    }

    /**
     * Replace any scheme requirements with the supplied scheme.
     *
     * @param scheme the scheme to use as a requirement
     * @return {@code this}.
     */
    @NonNull
    public URIRequirementBuilder withScheme(@CheckForNull String scheme) {
        withoutScheme();
        if (scheme != null) {
            requirements.add(new SchemeRequirement(scheme));
        }
        return this;
    }

    /**
     * Replace any path requirements with the supplied path.
     *
     * @param path the path to use as a requirement
     * @return {@code this}.
     * @since 1.12
     */
    @NonNull
    public URIRequirementBuilder withPath(@CheckForNull String path) {
        withoutPath();
        if (path != null) {
            requirements.add(new PathRequirement(path));
        }
        return this;
    }

    /**
     * Replace any hostname requirements with the supplied hostname.
     *
     * @param hostname the hostname to use as a requirement
     * @return {@code this}.
     */
    @NonNull
    public URIRequirementBuilder withHostname(@CheckForNull String hostname) {
        return withHostnamePort(hostname, -1);
    }

    /**
     * Replace any hostname or hostname:port requirements with the supplied hostname and port.
     *
     * @param hostname the hostname to use as a requirement or (@code null} to not add any requirement
     * @param port     the port or {@code -1} to not add {@link HostnamePortRequirement}s
     * @return {@code this}.
     */
    @NonNull
    public URIRequirementBuilder withHostnamePort(@CheckForNull String hostname, int port) {
        withoutHostname();
        withoutHostnamePort();
        if (hostname != null) {
            requirements.add(new HostnameRequirement(hostname));
            if (port != -1) {
                requirements.add(new HostnamePortRequirement(hostname, port));
            }
        }
        return this;
    }

    /**
     * Builds the list of requirements.
     *
     * @return the list of requirements.
     */
    @NonNull
    public List<DomainRequirement> build() {
        return new ArrayList<DomainRequirement>(requirements);
    }
}
