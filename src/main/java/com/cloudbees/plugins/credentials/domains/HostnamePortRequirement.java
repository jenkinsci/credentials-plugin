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

import javax.annotation.CheckForNull;

/**
 * A requirement for a {@link Domain} that includes {@link com.cloudbees.plugins.credentials.Credentials} for a
 * specific hostname and port combination.
 *
 * @since 1.5
 */
public class HostnamePortRequirement extends HostnameRequirement {
    /**
     * Standardize serialization format.
     *
     * @since 1.9
     */
    private static final long serialVersionUID = 1L;

    /**
     * The port.
     */
    private final int port;

    /**
     * Creates a new requirement.
     *
     * @param hostname the host.
     * @param port     the port.
     */
    public HostnamePortRequirement(@CheckForNull String hostname, int port) {
        super(hostname);
        this.port = port;
    }

    /**
     * Returns the required port.
     *
     * @return the required port.
     */
    public int getPort() {
        return port;
    }
}
